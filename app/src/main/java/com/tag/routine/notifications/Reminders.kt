package com.tag.routine.notifications

import android.Manifest
import android.app.AlarmManager
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
import com.tag.routine.data.RoutineEntity
import java.time.LocalDate
import java.time.ZonedDateTime

object Notifications {
    const val CHANNEL_ROUTINES = "tag_routines"
    const val CHANNEL_DIGEST = "tag_digest"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ROUTINE_ID = "routine_id"
    const val EXTRA_ROUTINE_TITLE = "routine_title"
    const val EXTRA_DIGEST_TEXT = "digest_text"
    const val KIND_ROUTINE = "routine"
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
        manager.createNotificationChannel(routines)
        manager.createNotificationChannel(digest)
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
        NotificationManagerCompat.from(context).notify(routineId.hashCode(), notification)
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
        NotificationManagerCompat.from(context).notify(9001, notification)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }
}

/**
 * Schedules routine reminders and the daily summary. Alarms are re-armed by
 * [NotificationReceiver] after they fire so they behave like repeating alarms while
 * still respecting per-day schedules.
 */
object ReminderScheduler {

    fun routineRequestCode(routineId: String): Int = routineId.hashCode() and 0x7FFFFFF

    fun digestRequestCode(): Int = 7001

    fun rescheduleAll(context: Context, routines: List<RoutineEntity>, digestMinutes: Int?, digestEnabled: Boolean) {
        Notifications.ensureChannels(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        routines.filter { !it.archived }.forEach { routine ->
            if (routine.reminder && routine.timeMinutes != null) {
                scheduleRoutine(context, alarmManager, routine)
            } else {
                cancelRoutine(context, alarmManager, routine.id)
            }
        }
        if (digestEnabled && digestMinutes != null) {
            scheduleDigest(context, alarmManager, digestMinutes)
        } else {
            cancelDigest(context, alarmManager)
        }
    }

    fun scheduleRoutine(context: Context, alarmManager: AlarmManager, routine: RoutineEntity) {
        val minutes = routine.timeMinutes ?: return
        val triggerAt = nextTrigger(routine.daysMask, minutes)
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
