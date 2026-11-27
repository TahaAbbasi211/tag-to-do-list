package com.tag.routine.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.tag.routine.R
import com.tag.routine.TagApp
import com.tag.routine.data.AppConstants
import com.tag.routine.data.Dates
import com.tag.routine.data.RemindAtCalculator
import com.tag.routine.widget.WidgetLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Pure decision logic for notification action dismissal, kept Android-free for unit tests. */
internal object NotificationActionPolicy {

    fun notificationIdFor(routineId: String): Int = routineId.hashCode()

    fun shouldCancelNotification(action: String?, routineId: String?): Boolean =
        !routineId.isNullOrBlank() &&
            (action == NotificationReceiver.ACTION_MARK_DONE || action == NotificationReceiver.ACTION_SNOOZE)

    fun notificationIdToCancel(action: String?, routineId: String?): Int? =
        if (shouldCancelNotification(action, routineId)) notificationIdFor(routineId!!) else null
}

/** Delivers a reminder notification, handles its actions, and re-arms the next occurrence. */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Notifications.ensureChannels(context)

        // Notification actions carry an action string rather than a kind extra.
        when (intent.action) {
            ACTION_MARK_DONE -> {
                if (intent.isTaskIntent()) handleTaskDone(context, intent) else handleMarkDone(context, intent)
                return
            }
            ACTION_SNOOZE -> {
                if (intent.isTaskIntent()) handleTaskSnooze(context, intent) else handleSnooze(context, intent)
                return
            }
        }

        when (val kind = intent.getStringExtra(Notifications.EXTRA_KIND)) {
            Notifications.KIND_ROUTINE -> {
                val routineId = intent.getStringExtra(Notifications.EXTRA_ROUTINE_ID) ?: return
                val title = intent.getStringExtra(Notifications.EXTRA_ROUTINE_TITLE)
                    ?: context.getString(R.string.focus_title)
                Notifications.notifyRoutine(context, routineId, title)
                rearm(context) { container ->
                    val routine = container.repository.routine(routineId) ?: return@rearm
                    if (routine.reminder && routine.timeMinutes != null && !routine.archived) {
                        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return@rearm
                        ReminderScheduler.scheduleRoutine(context, alarmManager, routine)
                    }
                }
            }

            Notifications.KIND_TASK -> {
                val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
                rearm(context) { container ->
                    val snapshot = container.repository.snapshot.first()
                    val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return@rearm
                    if (task.done) return@rearm
                    val dueDate = task.dueDate?.let(Dates::parse) ?: return@rearm
                    val settings = snapshot.settings
                    val locale = WidgetLocale.localeFor(settings)
                    val zone = ZoneId.systemDefault()
                    val dueLabel = if (task.dueTimeMinutes != null) {
                        Dates.shortDateTime(
                            epochMillis = RemindAtCalculator.dueInstantMillis(
                                dueDate,
                                task.dueTimeMinutes,
                                zone,
                            ),
                            zone = zone,
                            locale = locale,
                            calendarType = settings.calendarType,
                        )
                    } else {
                        Dates.dayMonth(dueDate, locale, settings.calendarType)
                    }
                    Notifications.notifyTask(
                        context = WidgetLocale.localizedContext(context, settings),
                        taskId = task.id,
                        title = task.title,
                        dueLabel = dueLabel,
                    )
                }
            }

            Notifications.KIND_DIGEST -> {
                rearm(context) { container ->
                    val snapshot = container.repository.snapshot.first()
                    val today = Dates.today()
                    val iso = Dates.iso(today)
                    val done = snapshot.routineLogs.filter { it.date == iso }.map { it.routineId }.toSet()
                    val scheduled = com.tag.routine.data.StatsEngine.scheduledOn(today, snapshot.routines)
                    val remaining = scheduled.count { it.id !in done }
                    val text = when {
                        scheduled.isEmpty() -> context.getString(R.string.notif_digest_nothing)
                        remaining == 0 ->
                            context.getString(R.string.notif_digest_all_done, scheduled.size)
                        else ->
                            context.getString(
                                R.string.notif_digest_remaining,
                                remaining,
                                scheduled.size,
                            )
                    }
                    Notifications.notifyDigest(context, text)
                    if (snapshot.settings.dailyDigestEnabled) {
                        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return@rearm
                        ReminderScheduler.scheduleDigest(
                            context,
                            alarmManager,
                            snapshot.settings.dailyDigestMinutes,
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    /** Marks the routine done for today straight from the notification and dismisses it. */
    private fun handleMarkDone(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(Notifications.EXTRA_ROUTINE_ID) ?: return
        cancelRoutineNotification(context, intent.action, routineId)
        rearm(context) { container ->
            container.repository.toggleRoutine(routineId, Dates.today(), true)
        }
    }

    /** Re-arms the routine 15 minutes from now and dismisses the notification. */
    private fun handleSnooze(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(Notifications.EXTRA_ROUTINE_ID) ?: return
        cancelRoutineNotification(context, intent.action, routineId)
        val title = intent.getStringExtra(Notifications.EXTRA_ROUTINE_TITLE)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        ReminderScheduler.snoozeRoutine(
            context,
            alarmManager,
            routineId,
            title,
            minutes = AppConstants.SNOOZE_MINUTES,
        )
    }

    private fun handleTaskDone(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
        cancelTaskNotification(context, taskId)
        rearm(context) { container ->
            container.repository.setTaskDone(taskId, true)
        }
    }

    private fun handleTaskSnooze(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
        cancelTaskNotification(context, taskId)
        val title = intent.getStringExtra(Notifications.EXTRA_TASK_TITLE)
        val dueLabel = intent.getStringExtra(Notifications.EXTRA_TASK_DUE_LABEL)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        ReminderScheduler.snoozeTask(
            context = context,
            alarmManager = alarmManager,
            taskId = taskId,
            title = title,
            dueLabel = dueLabel,
            minutes = AppConstants.SNOOZE_MINUTES,
        )
    }

    private fun cancelTaskNotification(context: Context, taskId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(TaskReminderIds.notificationId(taskId))
        }
    }

    private fun cancelRoutineNotification(context: Context, action: String?, routineId: String?) {
        val notificationId = NotificationActionPolicy.notificationIdToCancel(action, routineId) ?: return
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    private fun rearm(context: Context, block: suspend (com.tag.routine.AppContainer) -> Unit) {
        val container = TagApp.ensureContainer(context) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { block(container) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun Intent.isTaskIntent(): Boolean =
        getStringExtra(Notifications.EXTRA_KIND) == Notifications.KIND_TASK

    companion object {
        const val ACTION_MARK_DONE = "com.tag.routine.MARK_DONE"
        const val ACTION_SNOOZE = "com.tag.routine.SNOOZE"
    }
}

/** Restores every alarm after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val container = TagApp.containerOrNull ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = container.repository.snapshot.first()
                ReminderScheduler.rescheduleAll(
                    context = context,
                    routines = snapshot.routines,
                    tasks = snapshot.tasks,
                    digestMinutes = snapshot.settings.dailyDigestMinutes,
                    digestEnabled = snapshot.settings.notificationsEnabled && snapshot.settings.dailyDigestEnabled,
                    notificationsEnabled = snapshot.settings.notificationsEnabled,
                )
            } finally {
                pending.finish()
            }
        }
    }
}
