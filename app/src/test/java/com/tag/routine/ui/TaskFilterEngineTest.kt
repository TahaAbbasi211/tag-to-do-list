package com.tag.routine.ui

import com.tag.routine.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskFilterEngineTest {

    private val today = LocalDate.parse("2025-05-10")

    private fun task(
        id: String,
        title: String,
        note: String = "",
        tagId: String? = null,
        dueDate: String? = null,
        done: Boolean = false,
    ) = TaskEntity(
        id = id,
        title = title,
        note = note,
        tagId = tagId,
        priority = 1,
        dueDate = dueDate,
        dueTimeMinutes = null,
        remindAt = null,
        done = done,
        doneAt = null,
        createdAt = 0L,
    )

    private val tasks = listOf(
        task("1", "Buy milk", tagId = "home", dueDate = "2025-05-10"),
        task("2", "Write report", note = "quarterly", tagId = "work", dueDate = "2025-05-12"),
        task("3", "Pay rent", tagId = "home", dueDate = "2025-05-01"),
        task("4", "Old task", tagId = "work", dueDate = "2025-05-11", done = true),
        task("5", "No date", tagId = "home"),
    )

    private fun apply(
        query: String = "",
        filter: TaskFilter = TaskFilter.ALL,
        tagId: String? = null,
    ) = TaskFilterEngine.apply(tasks, TaskFilterEngine.Criteria(query, filter, tagId, today)).map { it.id }

    @Test
    fun tagFilterKeepsOnlyThatTag() {
        assertEquals(listOf("1", "3", "5"), apply(tagId = "home"))
        assertEquals(listOf("2", "4"), apply(tagId = "work"))
    }

    @Test
    fun querySearchesTitleAndNote() {
        assertEquals(listOf("2"), apply(query = "quarterly"))
        assertEquals(listOf("1"), apply(query = "MILK"))
    }

    @Test
    fun statusFiltersIgnoreDoneTasksExceptDone() {
        assertEquals(listOf("1"), apply(filter = TaskFilter.TODAY))
        assertEquals(listOf("2"), apply(filter = TaskFilter.UPCOMING))
        assertEquals(listOf("3"), apply(filter = TaskFilter.OVERDUE))
        assertEquals(listOf("4"), apply(filter = TaskFilter.DONE))
    }

    @Test
    fun allIncludesDoneTasks() {
        assertEquals(listOf("1", "2", "3", "4", "5"), apply())
    }

    @Test
    fun tagQueryAndStatusCombine() {
        assertEquals(listOf("2"), apply(query = "report", filter = TaskFilter.UPCOMING, tagId = "work"))
        assertEquals(emptyList<String>(), apply(query = "report", filter = TaskFilter.OVERDUE, tagId = "work"))
    }

    @Test
    fun blankQueryDoesNotFilter() {
        assertEquals(5, apply(query = "   ").size)
    }
}
