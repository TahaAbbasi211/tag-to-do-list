package com.tag.routine.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How long before the due time a task reminder fires.
 *
 * Only the resulting instant is persisted in TaskEntity.remindAt; the offset itself is
 * edited in the task sheet and can be inferred again from the stored instant.
 */
enum class ReminderOffset(val minutesBefore: Int) {
    AT_DUE(0),
    TEN_MINUTES_BEFORE(10),
    ONE_HOUR_BEFORE(60),
    ONE_DAY_BEFORE(24 * 60),
}

/** The reason a reminder could not be computed. */
enum class RemindError {
    MISSING_DUE_TIME,
    INVALID_DUE_TIME,
    IN_PAST,
}

/** Result of RemindAtCalculator. Exactly one of epochMillis and error is set. */
data class RemindResult(
    val epochMillis: Long? = null,
    val error: RemindError? = null,
) {
    val isSuccess: Boolean get() = epochMillis != null
}

/**
 * Pure clock arithmetic for task reminders.
 *
 * Keeping this free of Android types is what makes daylight-saving and time-zone
 * behaviour testable on the JVM. The caller supplies the zone and "now", so the result is
 * deterministic.
 */
object RemindAtCalculator {

    /**
     * Fixed fallback used by "1 day before" when the task has no due time. It is a
     * deliberate product rule: 09:00 local time on the day before the due date.
     */
    const val DAY_BEFORE_FALLBACK_MINUTES = 9 * 60

    fun calculate(
        dueDate: LocalDate,
        dueTimeMinutes: Int?,
        offset: ReminderOffset,
        zone: ZoneId,
        nowMillis: Long,
    ): RemindResult {
        val time = when {
            dueTimeMinutes != null -> {
                if (dueTimeMinutes !in 0 until 24 * 60) {
                    return RemindResult(error = RemindError.INVALID_DUE_TIME)
                }
                LocalTime.of(dueTimeMinutes / 60, dueTimeMinutes % 60)
            }
            offset == ReminderOffset.ONE_DAY_BEFORE ->
                LocalTime.of(DAY_BEFORE_FALLBACK_MINUTES / 60, DAY_BEFORE_FALLBACK_MINUTES % 60)
            else -> return RemindResult(error = RemindError.MISSING_DUE_TIME)
        }

        val dueAt = ZonedDateTime.of(dueDate, time, zone)
        // "One day before" is a calendar day, not 24 elapsed hours, so a daylight-saving
        // transition does not silently move the reminder to a different clock time.
        val remindAt = if (offset == ReminderOffset.ONE_DAY_BEFORE) {
            dueAt.minusDays(1)
        } else {
            dueAt.minusMinutes(offset.minutesBefore.toLong())
        }
        val millis = remindAt.toInstant().toEpochMilli()
        return if (millis <= nowMillis) {
            RemindResult(error = RemindError.IN_PAST)
        } else {
            RemindResult(epochMillis = millis)
        }
    }

    /** Convenience overload used by tests and callers that only need the instant. */
    fun calculateMillis(
        dueDate: LocalDate,
        dueTimeMinutes: Int?,
        offset: ReminderOffset,
        zone: ZoneId,
        nowMillis: Long = 0L,
    ): Long? = calculate(dueDate, dueTimeMinutes, offset, zone, nowMillis).epochMillis

    /**
     * Recovers which offset produced remindAt, so re-opening the editor keeps the
     * user's choice instead of silently snapping back to "at due time".
     */
    fun inferOffset(
        dueDate: LocalDate,
        dueTimeMinutes: Int?,
        remindAt: Long?,
        zone: ZoneId,
    ): ReminderOffset? {
        if (remindAt == null) return null
        return ReminderOffset.entries.firstOrNull { offset ->
            calculate(
                dueDate = dueDate,
                dueTimeMinutes = dueTimeMinutes,
                offset = offset,
                zone = zone,
                nowMillis = Long.MIN_VALUE,
            ).epochMillis == remindAt
        }
    }

    /** Due moment for a task, or null when the task has no due time. */
    fun dueInstantMillis(
        dueDate: LocalDate,
        dueTimeMinutes: Int,
        zone: ZoneId,
    ): Long = ZonedDateTime.of(
        dueDate,
        LocalTime.of(dueTimeMinutes / 60, dueTimeMinutes % 60),
        zone,
    ).toInstant().toEpochMilli()

    /** Due moment as an Instant for callers that format it through Dates. */
    fun dueAt(dueDate: LocalDate, dueTimeMinutes: Int, zone: ZoneId): Instant =
        Instant.ofEpochMilli(dueInstantMillis(dueDate, dueTimeMinutes, zone))
}
