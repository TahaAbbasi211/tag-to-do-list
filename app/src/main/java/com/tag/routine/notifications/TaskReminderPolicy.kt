package com.tag.routine.notifications

import com.tag.routine.data.TaskEntity

/** What the app should do with a task's alarm after a state change. */
enum class TaskReminderAction { SCHEDULE, CANCEL, NONE }

/**
 * Pure scheduling policy for task reminders. Keeping it out of the receiver and the
 * ViewModel makes the "checking a task cancels, un-checking re-arms" contract testable.
 */
object TaskReminderPolicy {

    fun shouldSchedule(task: TaskEntity, nowMillis: Long): Boolean =
        !task.done && task.dueDate != null && task.remindAt != null && task.remindAt > nowMillis

    fun actionForToggle(task: TaskEntity, becomingDone: Boolean, nowMillis: Long): TaskReminderAction =
        when {
            becomingDone -> TaskReminderAction.CANCEL
            shouldSchedule(task, nowMillis) -> TaskReminderAction.SCHEDULE
            task.remindAt != null -> TaskReminderAction.CANCEL
            else -> TaskReminderAction.NONE
        }

    fun actionForSavedTask(task: TaskEntity, nowMillis: Long): TaskReminderAction =
        when {
            task.done -> TaskReminderAction.CANCEL
            shouldSchedule(task, nowMillis) -> TaskReminderAction.SCHEDULE
            task.remindAt != null -> TaskReminderAction.CANCEL
            else -> TaskReminderAction.NONE
        }
}
