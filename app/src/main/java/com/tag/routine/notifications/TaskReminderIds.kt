package com.tag.routine.notifications

/**
 * PendingIntent and notification identifiers for task reminders.
 *
 * Routine alarms use the non-negative range (routineId.hashCode() and 0x7FFFFFFF) and
 * the digest owns 7001, so task identifiers reserve the negative range. The top two bits
 * also split alarm, done and snooze intents, which means a task can never overwrite its
 * own alarm with an action PendingIntent.
 */
object TaskReminderIds {

    private const val ALARM_BASE = Int.MIN_VALUE // 0x80000000
    private const val DONE_BASE = -1073741824 // 0xC0000000
    private const val SNOOZE_BASE = -536870912 // 0xE0000000

    private fun hash(taskId: String): Int = taskId.hashCode() and 0x3FFFFFFF

    /** Request code for the alarm that fires the reminder. Always negative. */
    fun alarmRequestCode(taskId: String): Int = ALARM_BASE or hash(taskId)

    /** Notification id for the reminder. Reuses the alarm code; separate id space. */
    fun notificationId(taskId: String): Int = alarmRequestCode(taskId)

    fun doneRequestCode(taskId: String): Int = DONE_BASE or hash(taskId)

    fun snoozeRequestCode(taskId: String): Int = SNOOZE_BASE or hash(taskId)
}
