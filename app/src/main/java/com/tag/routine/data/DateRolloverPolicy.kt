package com.tag.routine.data

import java.time.LocalDate

data class DateRolloverResult(
    val today: LocalDate,
    val selectedDate: LocalDate,
    val changed: Boolean,
)

/**
 * Pure midnight / date-change reconciliation rule.
 *
 * When the calendar day changes while the app is open, `today` updates to [observedToday].
 * If the user was looking at the old "today", `selectedDate` follows along; if they had
 * explicitly picked another past or future day, that selection stays intact.
 */
object DateRolloverPolicy {
    fun reconcile(
        currentToday: LocalDate,
        selectedDate: LocalDate,
        observedToday: LocalDate,
    ): DateRolloverResult {
        if (observedToday == currentToday) {
            return DateRolloverResult(
                today = currentToday,
                selectedDate = selectedDate,
                changed = false,
            )
        }
        val nextSelected = if (selectedDate == currentToday) observedToday else selectedDate
        return DateRolloverResult(
            today = observedToday,
            selectedDate = nextSelected,
            changed = true,
        )
    }
}
