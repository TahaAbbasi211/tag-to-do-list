package com.tag.routine.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tag.routine.MainActivity
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.Dates
import com.tag.routine.data.ExcludedDates
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.TaskEntity
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Pure notification-permission decision, kept free of Android calls so it is unit-testable.
 *
 * POST_NOTIFICATIONS is only a runtime permission from Android 13 (API 33); before that
 * the system-wide notifications switch alone decides whether posting is allowed.
 */
internal object NotificationPolicy {
    fun shouldPost(
        sdkInt: Int,
        permissionGranted: Boolean,
        notificationsEnabled: Boolean,
    ): Boolean = (sdkInt < Build.VERSION_CODES.TIRAMISU || permissionGranted) && notificationsEnabled

    fun shouldRequestRuntimePermission(
        sdkInt: Int,
        permissionGranted: Boolean,
        onboarded: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted && onboarded
}

object Notifications {
    const val CHANNEL_ROUTINES = "tag_routines"
    const val CHANNEL_DIGEST = "tag_digest"
    const val CHANNEL_TASKS = "tag_tasks"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ROUTINE_ID = "routine_id"
    const val EXTRA_ROUTINE_TITLE = "routine_title"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_TITLE = "task_title"
    const val EXTRA_TASK_DUE_LABEL = "task_due_label"
    const val EXTRA_DIGEST_TEXT = "digest_text"
    const val KIND_ROUTINE = "routine"
    const val KIND_TASK = "task"
    const val KIND_DIGEST = "digest"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val routines = NotificationChannel(
            CHANNEL_ROUTINES,
            context.getString(R.string.notif_channel_routines),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_routines_desc)
            enableVibration(true)
        }
        val digest = NotificationChannel(
            CHANNEL_DIGEST,
            context.getString(R.string.notif_channel_digest),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_digest_desc)
        }
        val tasks = NotificationChannel(
            CHANNEL_TASKS,
            context.getString(R.string.notif_channel_tasks),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_tasks_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(routines)
        manager.createNotificationChannel(digest)
        manager.createNotificationChannel(tasks)
    }

    fun notifyRoutine(context: Context, routineId: String, title: String) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            routineId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Mark done" completes the routine straight from the notification, without
        // opening the app. "Snooze" re-arms the same routine 15 minutes later.
        val doneIntent = PendingIntent.getBroadcast(
            context,
            routineId.hashCode() + 1,
            Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_MARK_DONE
                putExtra(EXTRA_ROUTINE_ID, routineId)
                putExtra(EXTRA_ROUTINE_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            routineId.hashCode() + 2,
            Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_SNOOZE
                putExtra(EXTRA_ROUTINE_ID, routineId)
                putExtra(EXTRA_ROUTINE_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ROUTINES)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_routine_body))
            .setColor(0xFF7C6CFF.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.notif_action_done),
                doneIntent,
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_snooze),
                snoozeIntent,
            )
            .build()
        post(context, routineId.hashCode(), notification)
    }

    /**
     * Task reminder with the same safe posting path as routines. The due label is
     * formatted by the receiver through Dates, so it follows the app language and the
     * Jalali/Gregorian choice instead of hard-coding a pattern here.
     */
    fun notifyTask(context: Context, taskId: String, title: String, dueLabel: String?) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            TaskReminderIds.notificationId(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = PendingIntent.getBroadcast(
            context,
            TaskReminderIds.doneRequestCode(taskId),
            Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_MARK_DONE
                putExtra(EXTRA_KIND, KIND_TASK)
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            TaskReminderIds.snoozeRequestCode(taskId),
            Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_SNOOZE
                putExtra(EXTRA_KIND, KIND_TASK)
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_TASK_DUE_LABEL, dueLabel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (dueLabel.isNullOrBlank()) {
            context.getString(R.string.notif_task_body_no_time)
        } else {
            context.getString(R.string.notif_task_body, dueLabel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF7C6CFF.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_action_done), doneIntent)
            .addAction(0, context.getString(R.string.notif_action_snooze), snoozeIntent)
            .build()
        post(context, TaskReminderIds.notificationId(taskId), notification)
    }

    fun notifyDigest(context: Context, text: String) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(context.getString(R.string.notif_digest_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFF4FD9C0.toInt())
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        post(context, 9001, notification)
    }

    /**
     * Posts a notification only when the user still allows it.
     *
     * [canPost] is checked here so every caller is safe, and the explicit
     * [SecurityException] catch covers POST_NOTIFICATIONS being revoked between that
     * check and the platform call. Dropping the notification is intentional: the focus
     * timer and reminder alarms keep working without it.
     */
    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked after the check; nothing to show, nothing to crash.
        }
    }

    fun canPost(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return NotificationPolicy.shouldPost(
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = permissionGranted,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }
}

/**
 * Schedules routine and task reminders plus the daily summary. Alarms are re-armed by
 * [NotificationReceiver] after they fire so they behave like repeating alarms while
 * still respecting per-day schedules.
 */
object ReminderScheduler {

    fun routineRequestCode(routineId: String): Int = routineId.hashCode() and 0x7FFFFFF

    fun digestRequestCode(): Int = 7001

    fun rescheduleAll(
        context: Context,
        routines: List<RoutineEntity>,
        tasks: List<TaskEntity> = emptyList(),
        digestMinutes: Int?,
        digestEnabled: Boolean,
        notificationsEnabled: Boolean = true,
        removedRoutineIds: Collection<String> = emptyList(),
        removedTaskIds: Collection<String> = emptyList(),
    ) {
        Notifications.ensureChannels(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val notificationManager = NotificationManagerCompat.from(context)
        removedRoutineIds.forEach { routineId ->
            cancelRoutine(context, alarmManager, routineId)
            runCatching { notificationManager.cancel(routineId.hashCode()) }
        }
        removedTaskIds.forEach { taskId ->
            cancelTask(context, alarmManager, taskId)
            runCatching { notificationManager.cancel(TaskReminderIds.notificationId(taskId)) }
        }
        routines.forEach { routine ->
            if (notificationsEnabled && !routine.archived && routine.reminder && routine.timeMinutes != null) {
                scheduleRoutine(context, alarmManager, routine)
            } else {
                cancelRoutine(context, alarmManager, routine.id)
            }
        }
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            if (notificationsEnabled && TaskReminderPolicy.shouldSchedule(task, now)) {
                scheduleTask(context, alarmManager, task)
            } else {
                cancelTask(context, alarmManager, task.id)
            }
        }
        if (notificationsEnabled && digestEnabled && digestMinutes != null) {
            scheduleDigest(context, alarmManager, digestMinutes)
        } else {
            cancelDigest(context, alarmManager)
        }
    }

    fun scheduleRoutine(context: Context, alarmManager: AlarmManager, routine: RoutineEntity) {
        val minutes = routine.timeMinutes ?: return
        val triggerAt = nextTrigger(routine, minutes)
        if (triggerAt == null) {
            cancelRoutine(context, alarmManager, routine.id)
            return
        }
        val pending = routinePendingIntent(context, routine)
        setAlarm(context, alarmManager, triggerAt, pending, routine.id.hashCode())
    }

    /**
     * Fires the routine again [minutes] from now, used by the notification's snooze action.
     * The one-shot alarm carries the same extras, so the receiver re-arms the normal
     * schedule once it has delivered.
     */
    fun snoozeRoutine(
        context: Context,
        alarmManager: AlarmManager,
        routineId: String,
        title: String?,
        minutes: Int,
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(Notifications.EXTRA_KIND, Notifications.KIND_ROUTINE)
            putExtra(Notifications.EXTRA_ROUTINE_ID, routineId)
            if (title != null) putExtra(Notifications.EXTRA_ROUTINE_TITLE, title)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            routineRequestCode(routineId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(context, alarmManager, triggerAt, pending, routineRequestCode(routineId))

        // Clear the notification the action was tapped on.
        androidx.core.app.NotificationManagerCompat.from(context).cancel(routineId.hashCode())
    }

    fun scheduleDigest(context: Context, alarmManager: AlarmManager, minutes: Int) {
        val triggerAt = nextTrigger(Dates.EVERY_DAY, minutes)
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(Notifications.EXTRA_KIND, Notifications.KIND_DIGEST)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            digestRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(context, alarmManager, triggerAt, pending, digestRequestCode())
    }

    fun cancelRoutine(context: Context, alarmManager: AlarmManager, routineId: String) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            routineRequestCode(routineId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun scheduleTask(context: Context, alarmManager: AlarmManager, task: TaskEntity) {
        val remindAt = task.remindAt
        if (remindAt == null || !TaskReminderPolicy.shouldSchedule(task, System.currentTimeMillis())) {
            cancelTask(context, alarmManager, task.id)
            return
        }
        val pending = taskPendingIntent(context, task)
        setAlarm(
            context,
            alarmManager,
            remindAt,
            pending,
            TaskReminderIds.alarmRequestCode(task.id),
        )
    }

    /** Fires a task reminder again after the notification's snooze action. */
    fun snoozeTask(
        context: Context,
        alarmManager: AlarmManager,
        taskId: String,
        title: String?,
        dueLabel: String?,
        minutes: Int,
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(Notifications.EXTRA_KIND, Notifications.KIND_TASK)
            putExtra(Notifications.EXTRA_TASK_ID, taskId)
            if (title != null) putExtra(Notifications.EXTRA_TASK_TITLE, title)
            if (dueLabel != null) putExtra(Notifications.EXTRA_TASK_DUE_LABEL, dueLabel)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            TaskReminderIds.alarmRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(context, alarmManager, triggerAt, pending, TaskReminderIds.alarmRequestCode(taskId))
        runCatching {
            NotificationManagerCompat.from(context).cancel(TaskReminderIds.notificationId(taskId))
        }
    }

    fun cancelTask(context: Context, alarmManager: AlarmManager, taskId: String) {
        val pending = PendingIntent.getBroadcast(
            context,
            TaskReminderIds.alarmRequestCode(taskId),
            Intent(context, NotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun taskPendingIntent(context: Context, task: TaskEntity): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(Notifications.EXTRA_KIND, Notifications.KIND_TASK)
            putExtra(Notifications.EXTRA_TASK_ID, task.id)
            putExtra(Notifications.EXTRA_TASK_TITLE, task.title)
        }
        return PendingIntent.getBroadcast(
            context,
            TaskReminderIds.alarmRequestCode(task.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancelDigest(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            digestRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun routinePendingIntent(context: Context, routine: RoutineEntity): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(Notifications.EXTRA_KIND, Notifications.KIND_ROUTINE)
            putExtra(Notifications.EXTRA_ROUTINE_ID, routine.id)
            putExtra(Notifications.EXTRA_ROUTINE_TITLE, routine.title)
        }
        return PendingIntent.getBroadcast(
            context,
            routineRequestCode(routine.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAt: Long,
        pending: PendingIntent,
        requestCode: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Exact alarms are unavailable: fall back to an inexact window so reminders still fire.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, AppConstants.ALARM_WINDOW_MILLIS, pending)
            return
        }
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }.onFailure {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, AppConstants.ALARM_WINDOW_MILLIS, pending)
        }
    }

    /** Next epoch millis matching [routine]'s schedule at [minutes] past midnight, or null if none upcoming. */
    fun nextTrigger(routine: RoutineEntity, minutes: Int): Long? {
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMillis = System.currentTimeMillis()
        if (!routine.repeatDaily) {
            val targetDate = LocalDate.ofEpochDay(routine.dateEpochDay)
            if (targetDate.isBefore(today)) return null
            val candidate = ZonedDateTime.of(
                targetDate,
                java.time.LocalTime.of((minutes / 60) % 24, minutes % 60),
                zone,
            ).toInstant().toEpochMilli()
            return if (candidate > nowMillis + AppConstants.ALARM_GRACE_MILLIS) candidate else null
        }
        var date = today
        repeat(30) {
            val dayEpoch = date.toEpochDay()
            if (routine.endDateEpochDay != null && dayEpoch > routine.endDateEpochDay) return null
            val startOk = routine.dateEpochDay == 0L || dayEpoch >= routine.dateEpochDay
            val dayMatches = startOk &&
                (routine.daysMask and Dates.weekdayBit(date)) != 0 &&
                !ExcludedDates.isExcluded(routine.excludedDates, Dates.iso(date))
            if (dayMatches) {
                val candidate = ZonedDateTime.of(
                    date,
                    java.time.LocalTime.of((minutes / 60) % 24, minutes % 60),
                    zone,
                ).toInstant().toEpochMilli()
                if (candidate > nowMillis + AppConstants.ALARM_GRACE_MILLIS) {
                    return candidate
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** Next epoch millis matching [daysMask] at [minutes] past midnight, always in the future. */
    fun nextTrigger(daysMask: Int, minutes: Int): Long {
        val zone = java.time.ZoneId.systemDefault()
        var date = LocalDate.now(zone)
        repeat(8) {
            val candidate = ZonedDateTime.of(
                date,
                java.time.LocalTime.of((minutes / 60) % 24, minutes % 60),
                zone,
            )
            val dayMatches = (daysMask and Dates.weekdayBit(date)) != 0
            if (dayMatches && candidate.toInstant().toEpochMilli() > System.currentTimeMillis() + AppConstants.ALARM_GRACE_MILLIS) {
                return candidate.toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return ZonedDateTime.of(
            LocalDate.now(zone).plusDays(1),
            java.time.LocalTime.of((minutes / 60) % 24, minutes % 60),
            zone,
        ).toInstant().toEpochMilli()
    }
}
