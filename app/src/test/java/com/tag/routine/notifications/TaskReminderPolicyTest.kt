package com.tag.routine.notifications

import com.tag.routine.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderPolicyTest {

    private fun task(
        id: String = "task-1",
        dueDate: String? = "2025-05-10",
        remindAt: Long? = 2_000L,
        done: Boolean = false,
    ) = TaskEntity(
        id = id,
        title = "Task",
        note = "",
        tagId = null,
        priority = 1,
        dueDate = dueDate,
        dueTimeMinutes = 540,
        remindAt = remindAt,
        done = done,
        doneAt = null,
        createdAt = 0L,
    )

    @Test
    fun futureUndoneTaskIsScheduled() {
        assertTrue(TaskReminderPolicy.shouldSchedule(task(remindAt = 2_000L), nowMillis = 1_000L))
        assertEquals(
            TaskReminderAction.SCHEDULE,
            TaskReminderPolicy.actionForSavedTask(task(remindAt = 2_000L), nowMillis = 1_000L),
        )
    }

    @Test
    fun checkedTaskCancels() {
        assertEquals(
            TaskReminderAction.CANCEL,
            TaskReminderPolicy.actionForToggle(task(remindAt = 2_000L), becomingDone = true, nowMillis = 1_000L),
        )
        assertEquals(
            TaskReminderAction.CANCEL,
            TaskReminderPolicy.actionForSavedTask(task(remindAt = 2_000L, done = true), nowMillis = 1_000L),
        )
    }

    @Test
    fun uncheckingAFutureReminderRearmsIt() {
        assertEquals(
            TaskReminderAction.SCHEDULE,
            TaskReminderPolicy.actionForToggle(task(remindAt = 2_000L), becomingDone = false, nowMillis = 1_000L),
        )
    }

    @Test
    fun uncheckingAnExpiredReminderDoesNotArmIt() {
        assertEquals(
            TaskReminderAction.CANCEL,
            TaskReminderPolicy.actionForToggle(task(remindAt = 500L), becomingDone = false, nowMillis = 1_000L),
        )
    }

    @Test
    fun taskWithoutReminderDoesNothing() {
        assertEquals(
            TaskReminderAction.NONE,
            TaskReminderPolicy.actionForToggle(task(remindAt = null), becomingDone = false, nowMillis = 1_000L),
        )
    }

    @Test
    fun taskWithoutDueDateCancelsAnyStaleReminder() {
        assertEquals(
            TaskReminderAction.CANCEL,
            TaskReminderPolicy.actionForSavedTask(task(dueDate = null, remindAt = 2_000L), nowMillis = 1_000L),
        )
    }

    @Test
    fun taskRequestCodesStayOutOfTheRoutineNamespace() {
        val ids = listOf("task-a", "task-b", "0f8fad5b-d9cb-469f-a165-70867728950e")
        for (id in ids) {
            val alarm = TaskReminderIds.alarmRequestCode(id)
            assertTrue("alarm must be negative for $id", alarm < 0)
            assertTrue("routine code must be non-negative", ReminderScheduler.routineRequestCode(id) >= 0)
            assertNotEquals(ReminderScheduler.digestRequestCode(), alarm)
            assertNotEquals(TaskReminderIds.doneRequestCode(id), alarm)
            assertNotEquals(TaskReminderIds.snoozeRequestCode(id), alarm)
            assertNotEquals(TaskReminderIds.doneRequestCode(id), TaskReminderIds.snoozeRequestCode(id))
        }
        assertNotEquals(
            TaskReminderIds.alarmRequestCode("task-a"),
            TaskReminderIds.alarmRequestCode("task-b"),
        )
    }
}
