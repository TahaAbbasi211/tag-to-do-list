package com.tag.routine.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tag.routine.TagApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Completion alarm for the focus timer, and the receiver that settles the run.
 *
 * The foreground service normally finishes a run on its own. This is the backstop for
 * the case where the platform kills the process anyway (aggressive battery managers on
 * some devices do), so a completed run is still recorded instead of lingering on disk
 * until the user happens to reopen the app.
 */
object FocusTimerAlarms {

    private const val REQUEST_CODE = 7301

    /** Arms an alarm for the moment [run] reaches its planned length. */
    fun arm(context: Context, run: com.tag.routine.data.ActiveFocusRun) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val remaining = run.remainingSeconds()
        if (remaining <= 0) return
        val triggerAt = System.currentTimeMillis() + remaining * 1000L
        val pending = pendingIntent(context)
        runCatching {
            alarmManager.scheduleRtcWakeup(triggerAt, pending)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FocusTimerReceiver::class.java).setAction(FocusTimerReceiver.ACTION_COMPLETE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Settles a focus run when the user stops it from the notification, or when its completion alarm fires. */
class FocusTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_COMPLETE && action != ACTION_STOP) return
        val container = TagApp.containerOrNull ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_STOP -> {
                        // The user stopped the timer from its notification. Settling
                        // through the shared path records the run if it earned a place in
                        // the history, and is a no-op if the service already settled it.
                        FocusTimerAlarms.cancel(context)
                        FocusTimerRuntime.adopt(container.repository.currentActiveFocusRun())
                        FocusTimerRuntime.finish()
                        runCatching {
                            context.stopService(Intent(context, FocusTimerService::class.java))
                        }
                    }

                    else -> {
                        val run = container.repository.currentActiveFocusRun() ?: return@launch
                        // Adopt the stored run, then let the shared settle path decide. It
                        // only closes the run when it genuinely reached its planned
                        // length, and its lock plus the run-id-keyed write make this safe
                        // even if the service is finishing the same run at the same moment.
                        FocusTimerRuntime.adopt(run)
                        val transition = FocusTimerRuntime.advanceWithTransition()
                        val next = transition.next
                        if (next != null && next.running) {
                            // Break mode auto-started the next phase; keep the foreground
                            // service and its backstop alarm alive from this receiver.
                            FocusTimerAlarms.arm(context, next)
                            runCatching { FocusTimerService.start(context) }
                        } else {
                            // No next phase (or it is a paused focus run): tear the service
                            // down. `stopService` is always permitted, unlike `startService`.
                            runCatching {
                                context.stopService(Intent(context, FocusTimerService::class.java))
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.tag.routine.focus.COMPLETE"
        const val ACTION_STOP = "com.tag.routine.focus.STOP_FROM_NOTIFICATION"
    }
}
