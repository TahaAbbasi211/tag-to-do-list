package com.tag.routine.data

import java.util.Locale

enum class RoutineTimeError {
    NONE,
    INVALID_HOUR,
    INVALID_MINUTE,
    INVALID_BOTH,
}

data class RoutineTimeValidation(
    val timeMinutes: Int?,
    val hourError: Boolean,
    val minuteError: Boolean,
    val error: RoutineTimeError,
) {
    val isValid: Boolean get() = error == RoutineTimeError.NONE
}

/**
 * Pure validator for the routine editor's separate Hour and Minute text fields.
 *
 * Ensures that out-of-range inputs (`hour !in 0..23` or `minute !in 0..59`) or a minute
 * entered without an hour never leave a stale or out-of-bounds `timeMinutes` behind.
 */
object RoutineTimeValidator {

    fun validate(hourText: String, minuteText: String): RoutineTimeValidation {
        val rawHour = Dates.formatDigits(hourText.trim(), Locale.ROOT)
        val rawMinute = Dates.formatDigits(minuteText.trim(), Locale.ROOT)

        if (rawHour.isEmpty() && rawMinute.isEmpty()) {
            return RoutineTimeValidation(
                timeMinutes = null,
                hourError = false,
                minuteError = false,
                error = RoutineTimeError.NONE,
            )
        }

        val hour = rawHour.toIntOrNull()
        val minute = if (rawMinute.isEmpty()) 0 else rawMinute.toIntOrNull()

        val hourBad = rawHour.isEmpty() || hour == null || hour !in 0 until AppConstants.HOURS_PER_DAY
        val minuteBad = rawMinute.isNotEmpty() && (minute == null || minute !in 0 until AppConstants.MINUTES_PER_HOUR)

        val error = when {
            hourBad && minuteBad -> RoutineTimeError.INVALID_BOTH
            hourBad -> RoutineTimeError.INVALID_HOUR
            minuteBad -> RoutineTimeError.INVALID_MINUTE
            else -> RoutineTimeError.NONE
        }

        val resolvedMinutes = if (error == RoutineTimeError.NONE) {
            hour!! * AppConstants.MINUTES_PER_HOUR + minute!!
        } else {
            null
        }

        return RoutineTimeValidation(
            timeMinutes = resolvedMinutes,
            hourError = hourBad,
            minuteError = minuteBad,
            error = error,
        )
    }
}
