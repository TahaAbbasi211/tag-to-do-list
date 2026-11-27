package com.tag.routine.ui

import com.tag.routine.data.Dates
import com.tag.routine.data.TaskEntity
import java.time.LocalDate

/**
 * Pure task filtering used by AppViewModel. Extracted so search, status and tag chips
 * can be tested together without a Room database or Compose runtime.
 */
object TaskFilterEngine {

    data class Criteria(
        val query: String,
        val filter: TaskFilter,
        val tagId: String?,
        val today: LocalDate,
    )

    fun apply(tasks: List<TaskEntity>, criteria: Criteria): List<TaskEntity> {
        val query = criteria.query.trim()
        return tasks.filter { task ->
            val matchesTag = criteria.tagId == null || task.tagId == criteria.tagId
            val matchesQuery = query.isBlank() ||
                task.title.contains(query, ignoreCase = true) ||
                task.note.contains(query, ignoreCase = true)
            matchesTag && matchesQuery && matchesStatus(task, criteria.filter, criteria.today)
        }
    }

    private fun matchesStatus(task: TaskEntity, filter: TaskFilter, today: LocalDate): Boolean =
        when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.DONE -> task.done
            TaskFilter.TODAY -> !task.done && task.dueDate == Dates.iso(today)
            TaskFilter.OVERDUE -> !task.done && task.dueDate != null &&
                (Dates.parse(task.dueDate)?.isBefore(today) == true)
            TaskFilter.UPCOMING -> !task.done && task.dueDate != null &&
                (Dates.parse(task.dueDate)?.isAfter(today) == true)
        }
}
