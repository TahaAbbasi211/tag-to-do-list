package com.tag.routine.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tag.routine.MainActivity
import com.tag.routine.R
import com.tag.routine.TagApp
import com.tag.routine.data.ActiveFocusRun
import com.tag.routine.data.AppConstants
import com.tag.routine.data.FocusRunStorage
import com.tag.routine.data.FocusTimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Pure coroutine state machine behind [FocusTimerRuntime].
 *
 * Serializes all run transitions (`start`, `switchTo`, `pause`, `resume`, `advance`,
 * `finish`, `discardWithoutRecording`) under [settleLock] so switching routines or racing
 * with service completion never loses or double-records a [FocusSessionEntity].
 */
internal class FocusTimerCoordinator(
    private val storageProvider: () -> FocusRunStorage?,
) {
    private val _run = MutableStateFlow<ActiveFocusRun?>(null)

    /** Serialises settlement so the service, receiver, and UI cannot race. */
    private val settleLock = Mutex()

    /** The run in progress, or null. Ticks are not pushed here; elapsed time is derived. */
    val run: StateFlow<ActiveFocusRun?> = _run.asStateFlow()

    suspend fun restore(run: ActiveFocusRun?) = settleLock.withLock {
        if (_run.value != null) return@withLock
        _run.value = run
    }

    suspend fun start(
        routineId: String?,
        routineTitle: String?,
        minutes: Int,
        now: Long = System.currentTimeMillis(),
        onBeforeSettle: () -> Unit = {},
    ): ActiveFocusRun = switchTo(
        routineId = routineId,
        routineTitle = routineTitle,
        minutes = minutes,
        now = now,
        onBeforeSettle = onBeforeSettle,
    )

    /**
     * Atomically settles any currently active run (recording elapsed time if >= minimum)
     * and starts and persists a fresh run for the new target.
     */
    suspend fun switchTo(
        routineId: String?,
        routineTitle: String?,
        minutes: Int,
        now: Long = System.currentTimeMillis(),
        onBeforeSettle: () -> Unit = {},
    ): ActiveFocusRun = settleLock.withLock {
        onBeforeSettle()
        val storage = storageProvider()
        val previous = _run.value ?: storage?.currentActiveFocusRun()
        if (previous != null) {
            storage?.clearActiveFocusRun()
            val session = FocusTimerEngine.toSession(previous, id = previous.id, now = now)
            if (session != null) {
                storage?.recordFocusSession(
                    id = session.id,
                    routineId = session.routineId,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    plannedMinutes = session.plannedMinutes,
                    completed = session.completed,
                )
            }
        }
        val next = FocusTimerEngine.start(
            id = UUID.randomUUID().toString(),
            routineId = routineId,
            routineTitle = routineTitle,
            minutes = minutes,
            now = now,
        )
        _run.value = next
        storage?.setActiveFocusRun(next)
        next
    }

    suspend fun pause(now: Long = System.currentTimeMillis()) = settleLock.withLock {
        val current = _run.value ?: return@withLock
        val updated = FocusTimerEngine.pause(current, now)
        _run.value = updated
        storageProvider()?.setActiveFocusRun(updated)
    }

    suspend fun resume(now: Long = System.currentTimeMillis()) = settleLock.withLock {
        val current = _run.value ?: return@withLock
        val updated = FocusTimerEngine.resume(current, now)
        _run.value = updated
        storageProvider()?.setActiveFocusRun(updated)
    }

    suspend fun advance(now: Long = System.currentTimeMillis()): Boolean = settleLock.withLock {
        val pending = _run.value ?: return@withLock false
        val ticked = FocusTimerEngine.tick(pending, now)
        if (!ticked.completed) return@withLock false
        _run.value = ticked.run
        settleLocked(now)
        true
    }

    suspend fun finish(now: Long = System.currentTimeMillis()) = settleLock.withLock {
        settleLocked(now)
    }

    suspend fun discardWithoutRecording() = settleLock.withLock {
        _run.value = null
        storageProvider()?.clearActiveFocusRun()
    }

    suspend fun adopt(run: ActiveFocusRun?) = settleLock.withLock {
        if (_run.value != null) return@withLock
        _run.value = run
    }

    private suspend fun settleLocked(now: Long) {
        val storage = storageProvider() ?: return
        val current = _run.value ?: storage.currentActiveFocusRun() ?: return
        _run.value = null
        storage.clearActiveFocusRun()
        val session = FocusTimerEngine.toSession(current, id = current.id, now = now) ?: return
        storage.recordFocusSession(
            id = session.id,
            routineId = session.routineId,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            plannedMinutes = session.plannedMinutes,
            completed = session.completed,
        )
    }
}

/**
 * Process-wide view of the focus timer.
 *
 * The timer itself is owned by [FocusTimerService]; this object is the single place the
 * UI and the service agree on. It holds the last known run in memory so the Focus screen
 * can render without touching disk on every frame, and mirrors every mutation to
 * [com.tag.routine.data.SettingsStore] so the run survives the process being killed.
 *
 * All mutations funnel through here, which is what keeps the service notification and
 * the on-screen countdown from ever disagreeing.
 */
object FocusTimerRuntime {

    private val coordinator = FocusTimerCoordinator { TagApp.containerOrNull?.repository }

    /** The run in progress, or null. Ticks are not pushed here; elapsed time is derived. */
    val run: StateFlow<ActiveFocusRun?> = coordinator.run

    /**
     * Warms the in-memory copy from disk when nothing is loaded yet.
     *
     * The in-memory run is always at least as fresh as the stored one — while a run is
     * counting, the stored copy keeps its original start timestamp and elapsed time is
     * derived from it — so an existing run is deliberately left untouched.
     */
    suspend fun restore(run: ActiveFocusRun?) = coordinator.restore(run)

    /** Starts a new run and makes it durable, settling any prior active run first. */
    suspend fun start(
        routineId: String?,
        routineTitle: String?,
        minutes: Int,
        now: Long = System.currentTimeMillis(),
        onBeforeSettle: () -> Unit = {},
    ): ActiveFocusRun = coordinator.start(
        routineId = routineId,
        routineTitle = routineTitle,
        minutes = minutes,
        now = now,
        onBeforeSettle = onBeforeSettle,
    )

    /** Settles any active run cleanly and starts a replacement run under the settle lock. */
    suspend fun switchTo(
        routineId: String?,
        routineTitle: String?,
        minutes: Int,
        now: Long = System.currentTimeMillis(),
        onBeforeSettle: () -> Unit = {},
    ): ActiveFocusRun = coordinator.switchTo(
        routineId = routineId,
        routineTitle = routineTitle,
        minutes = minutes,
        now = now,
        onBeforeSettle = onBeforeSettle,
    )

    suspend fun pause() = coordinator.pause()

    suspend fun resume() = coordinator.resume()

    /**
     * Advances the run against the wall clock and settles it when it has reached its
     * planned length.
     *
     * @return true when this call completed and recorded the run.
     */
    suspend fun advance(): Boolean = coordinator.advance()

    /**
     * Clears the run, recording it when it earned a place in the history.
     */
    suspend fun finish() = coordinator.finish()

    /**
     * Drops the active run from memory and disk without recording a history row.
     * Used when a full backup replace removes the linked routine.
     */
    suspend fun discardWithoutRecording() = coordinator.discardWithoutRecording()

    /**
     * Applies a run loaded from disk without writing it back.
     */
    suspend fun adopt(run: ActiveFocusRun?) = coordinator.adopt(run)
}

/**
 * Keeps the focus timer alive while the app is closed.
 *
 * Android tears down the app process when the user swipes the app out of Recents, which
 * is what used to reset the timer to zero. A foreground service is the platform's
 * supported way to keep working with the app off screen: it is not killed with the task,
 * and its notification is the visible evidence of the running timer.
 *
 * The service deliberately does not count ticks as its source of truth. It wakes once a
 * second to refresh the countdown, but the elapsed time it reports always comes from the
 * persisted wall-clock start, so a frozen or throttled process still shows the right time
 * the moment it runs again.
 */
class FocusTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    /** Identifies the run+segment the completion alarm is currently armed for. */
    private var armedAlarmKey: String? = null

    /** Fingerprint of the last published notification, used to skip redundant posts. */
    private var publishedFingerprint: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        FocusTimerNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // A stop request delivered straight to the service. The user-facing stop
            // action goes through FocusTimerReceiver; this path covers a stale
            // PendingIntent that was created before that indirection existed.
            tickJob?.cancel()
            tickJob = null
            scope.launch {
                FocusTimerAlarms.cancel(this@FocusTimerService)
                FocusTimerRuntime.finish()
                stopTimer()
            }
            return START_NOT_STICKY
        }

        // The platform requires startForeground within a few seconds, so the service
        // shows something immediately and resolves the real run afterwards. This is the
        // path taken when the process was killed and the system restarted the service,
        // where the in-memory copy is gone but the run is still on disk.
        val known = FocusTimerRuntime.run.value
        if (known != null) {
            promoteToForeground(known)
            startTicking()
            return START_STICKY
        }

        promoteToForeground(null)
        scope.launch {
            val stored = TagApp.containerOrNull?.repository?.currentActiveFocusRun()
            if (stored == null) {
                stopTimer()
                return@launch
            }
            FocusTimerRuntime.adopt(stored)
            promoteToForeground(stored)
            startTicking()
        }
        return START_STICKY
    }

    /**
     * Watches the run and stops once the planned length is reached.
     *
     * The service does not drive the clock — elapsed time is always recomputed from the
     * persisted timestamps — so this loop is a supervisor, not a stopwatch. It re-arms the
     * backstop alarm when the run starts or resumes, re-posts the notification when the
     * displayed state actually changes, and settles the run at completion.
     */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val current = FocusTimerRuntime.run.value
                if (current == null) {
                    stopTimer()
                    return@launch
                }
                armCompletionAlarm(current)
                publishIfChanged(current)

                val completed = FocusTimerRuntime.advance()
                if (completed) {
                    FocusTimerAlarms.cancel(this@FocusTimerService)
                    notifyFinished(current)
                    stopTimer()
                    return@launch
                }
                delay(TICK_MILLIS)
            }
        }
    }

    /**
     * Re-posts the notification only when the text it shows would actually differ.
     *
     * The countdown itself is drawn by the platform's chronometer, so a running run only
     * needs its static text refreshed occasionally (rounded to [TEXT_REFRESH_SECONDS]);
     * a paused run has no chronometer, so its exact value matters. Rebuilding every
     * second would be wasted work and a visible source of notification churn.
     */
    private fun publishIfChanged(run: ActiveFocusRun) {
        val displayedSeconds = run.remainingSeconds().let { remaining ->
            if (run.running) remaining / TEXT_REFRESH_SECONDS else remaining
        }
        val fingerprint = listOf(
            run.id,
            run.running,
            displayedSeconds,
            run.plannedMinutes,
            run.routineTitle,
        ).joinToString("|")
        if (fingerprint == publishedFingerprint) return
        publishedFingerprint = fingerprint
        updateNotification(run)
    }

    /**
     * Arms (or re-arms) the backstop alarm for the current run+segment.
     *
     * The key changes only when a new segment begins, so pausing and resuming re-arms
     * once rather than on every tick.
     */
    private fun armCompletionAlarm(run: ActiveFocusRun) {
        val key = "${run.id}:${run.segmentStartedAt ?: 0L}"
        if (key == armedAlarmKey) return
        armedAlarmKey = key
        if (run.running) {
            FocusTimerAlarms.arm(this, run)
        } else {
            FocusTimerAlarms.cancel(this)
        }
    }

    /**
     * Shows (or refreshes) the ongoing notification and promotes the service to the
     * foreground. Must be called promptly after `startForegroundService`, before any
     * slow work, or the platform raises `ForegroundServiceDidNotStartInTimeException`.
     *
     * A null [run] renders a neutral "starting" notification; it is only used for the
     * brief window while the run is being read back from disk.
     */
    private fun promoteToForeground(run: ActiveFocusRun?) {
        val notification = if (run == null) {
            NotificationCompat.Builder(this, FocusTimerNotifications.CHANNEL_FOCUS)
                .setSmallIcon(R.drawable.ic_stat_tag)
                .setContentTitle(getString(R.string.focus_title))
                .setContentText(getString(R.string.notif_focus_starting))
                .setColor(0xFF7C6CFF.toInt())
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } else {
            buildNotification(run)
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    /**
     * Rebuilds the notification so the countdown tracks the run.
     *
     * The remaining time is rendered by the platform with `setChronometerCountDown`, which
     * counts down from the run's real deadline. That keeps the notification accurate even
     * while the process is frozen, and costs one rebuild instead of one per second.
     */
    private fun updateNotification(run: ActiveFocusRun) {
        if (!Notifications.canPost(this)) return
        runCatching {
            Notifications.post(this, NOTIFICATION_ID, buildNotification(run))
        }
    }

    private fun buildNotification(run: ActiveFocusRun): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // The stop action is a broadcast rather than a service start: a receiver is
        // always deliverable from a notification action, whereas starting a background
        // service is subject to background-execution limits.
        val stop = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, FocusTimerReceiver::class.java).setAction(FocusTimerReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = run.routineTitle?.takeIf { it.isNotBlank() }
            ?: getString(R.string.focus_history_untitled)

        val builder = NotificationCompat.Builder(this, FocusTimerNotifications.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(title)
            .setContentIntent(openApp)
            .setColor(0xFF7C6CFF.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, getString(R.string.focus_stop), stop)

        if (run.running) {
            val remaining = run.remainingSeconds()
            builder
                .setContentText(getString(R.string.notif_focus_running, FocusTimerEngine.formatClock(remaining)))
                .setWhen(remainingDeadline(run))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setContentText(
                getString(
                    R.string.notif_focus_paused,
                    FocusTimerEngine.formatClock(run.remainingSeconds()),
                ),
            )
        }
        return builder.build()
    }

    /**
     * The wall-clock instant the run reaches its planned length, expressed in the
     * `SystemClock.elapsedRealtime` timeline that `setWhen` and the chronometer use.
     */
    private fun remainingDeadline(run: ActiveFocusRun): Long =
        SystemClock.elapsedRealtime() + run.remainingSeconds() * 1000L

    /** Tells the user the run finished, then removes the ongoing notification. */
    private fun notifyFinished(run: ActiveFocusRun) {
        if (!Notifications.canPost(this)) return
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = run.routineTitle?.takeIf { it.isNotBlank() }
            ?: getString(R.string.focus_history_untitled)
        val done = NotificationCompat.Builder(this, FocusTimerNotifications.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(getString(R.string.focus_session_done))
            .setContentText(title)
            .setColor(0xFF4FD9C0.toInt())
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        runCatching {
            Notifications.post(this, FINISHED_NOTIFICATION_ID, done)
        }
    }

    private fun stopTimer() {
        tickJob?.cancel()
        tickJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.tag.routine.focus.START"
        const val ACTION_STOP = "com.tag.routine.focus.STOP"

        private const val NOTIFICATION_ID = 4201
        private const val FINISHED_NOTIFICATION_ID = 4202
        private const val TICK_MILLIS = 1_000L

        /** How often the notification's text is re-rendered while the run is counting. */
        private const val TEXT_REFRESH_SECONDS = 30

        /** Brings the service up for a run that already exists in [FocusTimerRuntime]. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FocusTimerService::class.java).setAction(ACTION_START),
            )
        }

        /**
         * Tears the timer service down from the app.
         *
         * `stopService` is used rather than sending [ACTION_STOP]: it is permitted from a
         * background context, works whether or not the service is running, and removes the
         * ongoing notification as a side effect. The caller settles the run itself.
         */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FocusTimerService::class.java)) }
        }
    }
}

/** Dedicated channel so the ongoing timer can be silenced independently of reminders. */
object FocusTimerNotifications {
    const val CHANNEL_FOCUS = "tag_focus"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_FOCUS,
            context.getString(R.string.notif_channel_focus),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_focus_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
