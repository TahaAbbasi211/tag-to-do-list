package com.tag.routine.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tag.routine.R
import com.tag.routine.TagApp
import com.tag.routine.data.AppConstants
import com.tag.routine.data.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Delivers a reminder notification, handles its actions, and re-arms the next occurrence. */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Notifications.ensureChannels(context)

        // Notification actions carry an action string rather than a kind extra.
        when (intent.action) {
            ACTION_MARK_DONE -> {
                handleMarkDone(context, intent)
                return
            }
            ACTION_SNOOZE -> {
                handleSnooze(context, intent)
                return
            }
        }

        when (val kind = intent.getStringExtra(Notifications.EXTRA_KIND)) {
            Notifications.KIND_ROUTINE -> {
                val routineId = intent.getStringExtra(Notifications.EXTRA_ROUTINE_ID) ?: return
                val title = intent.getStringExtra(Notifications.EXTRA_ROUTINE_TITLE)
                    ?: context.getString(R.string.focus_title)
                Notifications.notifyRoutine(context, routineId, title)
                rearm { container ->
                    val routine = container.repository.routine(routineId) ?: return@rearm
                    if (routine.reminder && routine.timeMinutes != null && !routine.archived) {
                        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return@rearm
                        ReminderScheduler.scheduleRoutine(context, alarmManager, routine)
                    }
                }
            }

            Notifications.KIND_DIGEST -> {
                rearm { container ->
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

    /** Marks the routine done for today straight from the notification. */
    private fun handleMarkDone(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(Notifications.EXTRA_ROUTINE_ID) ?: return
        rearm { container ->
            container.repository.toggleRoutine(routineId, Dates.today(), true)
        }
    }

    /** Re-arms the routine 15 minutes from now and dismisses the notification. */
    private fun handleSnooze(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(Notifications.EXTRA_ROUTINE_ID) ?: return
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

    private fun rearm(block: suspend (com.tag.routine.AppContainer) -> Unit) {
        val container = TagApp.containerOrNull ?: return
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch { runCatching { block(container) } }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.tag.routine.MARK_DONE"
        const val ACTION_SNOOZE = "com.tag.routine.SNOOZE"
    }
}

/** Restores every alarm after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = TagApp.containerOrNull ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = container.repository.snapshot.first()
                ReminderScheduler.rescheduleAll(
                    context = context,
                    routines = snapshot.routines,
                    digestMinutes = snapshot.settings.dailyDigestMinutes,
                    digestEnabled = snapshot.settings.notificationsEnabled && snapshot.settings.dailyDigestEnabled,
                )
            } finally {
                pending.finish()
            }
        }
    }
}
