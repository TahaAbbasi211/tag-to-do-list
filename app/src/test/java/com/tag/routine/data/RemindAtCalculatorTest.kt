package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RemindAtCalculatorTest {

    private val tehran = ZoneId.of("Asia/Tehran")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun millis(date: String, hour: Int, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun atDueTimeReturnsTheDueMoment() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = 14 * 60 + 30,
            offset = ReminderOffset.AT_DUE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2025-05-10", 14, 30, tehran), result.epochMillis)
    }

    @Test
    fun tenMinutesBeforeSubtractsTenMinutes() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = 14 * 60 + 30,
            offset = ReminderOffset.TEN_MINUTES_BEFORE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2025-05-10", 14, 20, tehran), result.epochMillis)
    }

    @Test
    fun oneHourBeforeSubtractsOneHour() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = 14 * 60 + 30,
            offset = ReminderOffset.ONE_HOUR_BEFORE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2025-05-10", 13, 30, tehran), result.epochMillis)
    }

    @Test
    fun oneDayBeforeWithTimeSubtractsExactlyOneDay() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = 14 * 60 + 30,
            offset = ReminderOffset.ONE_DAY_BEFORE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2025-05-09", 14, 30, tehran), result.epochMillis)
    }

    @Test
    fun oneDayBeforeWithoutTimeUsesNineAmOnThePreviousDay() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = null,
            offset = ReminderOffset.ONE_DAY_BEFORE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2025-05-09", 9, 0, tehran), result.epochMillis)
    }

    @Test
    fun otherOffsetsWithoutTimeAreRejected() {
        for (offset in listOf(ReminderOffset.AT_DUE, ReminderOffset.TEN_MINUTES_BEFORE, ReminderOffset.ONE_HOUR_BEFORE)) {
            val result = RemindAtCalculator.calculate(
                dueDate = LocalDate.parse("2025-05-10"),
                dueTimeMinutes = null,
                offset = offset,
                zone = tehran,
                nowMillis = 0L,
            )
            assertEquals(RemindError.MISSING_DUE_TIME, result.error)
            assertNull(result.epochMillis)
        }
    }

    @Test
    fun invalidDueTimeIsRejected() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = 24 * 60,
            offset = ReminderOffset.AT_DUE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(RemindError.INVALID_DUE_TIME, result.error)
    }

    @Test
    fun aReminderAtOrBeforeNowIsInThePast() {
        val due = millis("2025-05-10", 14, 30, tehran)
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-05-10"),
            dueTimeMinutes = 14 * 60 + 30,
            offset = ReminderOffset.AT_DUE,
            zone = tehran,
            nowMillis = due,
        )
        assertEquals(RemindError.IN_PAST, result.error)
    }

    @Test
    fun tenMinutesBeforeCrossesMidnight() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-01-02"),
            dueTimeMinutes = 5,
            offset = ReminderOffset.TEN_MINUTES_BEFORE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2025-01-01", 23, 55, tehran), result.epochMillis)
    }

    @Test
    fun daylightSavingSpringForwardUsesTheRealElapsedHour() {
        // 03:30 CEST minus one real hour is 01:30 CET; 02:30 does not exist that night.
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-03-30"),
            dueTimeMinutes = 3 * 60 + 30,
            offset = ReminderOffset.ONE_HOUR_BEFORE,
            zone = berlin,
            nowMillis = 0L,
        )
        assertEquals(
            LocalDateTime.of(2025, 3, 30, 1, 30).atZone(berlin).toInstant().toEpochMilli(),
            result.epochMillis,
        )
    }

    @Test
    fun oneDayBeforeKeepsTheWallClockAcrossDaylightSaving() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2025-03-31"),
            dueTimeMinutes = 3 * 60 + 30,
            offset = ReminderOffset.ONE_DAY_BEFORE,
            zone = berlin,
            nowMillis = 0L,
        )
        assertEquals(
            LocalDateTime.of(2025, 3, 30, 3, 30).atZone(berlin).toInstant().toEpochMilli(),
            result.epochMillis,
        )
    }

    @Test
    fun timeZoneChangesTheAbsoluteInstant() {
        val utc = RemindAtCalculator.calculateMillis(
            LocalDate.parse("2025-05-10"),
            9 * 60,
            ReminderOffset.AT_DUE,
            ZoneId.of("UTC"),
        )
        val tehranAtNine = RemindAtCalculator.calculateMillis(
            LocalDate.parse("2025-05-10"),
            9 * 60,
            ReminderOffset.AT_DUE,
            tehran,
        )
        assertEquals(3 * 60 * 60 * 1000L + 30 * 60 * 1000L, utc!! - tehranAtNine!!)
    }

    @Test
    fun jalaliAndGregorianDatesAgreeAfterConversion() {
        val gregorian = Dates.fromJalali(1404, 1, 1)
        assertEquals(LocalDate.parse("2025-03-21"), gregorian)
        val jalaliResult = RemindAtCalculator.calculateMillis(
            gregorian,
            8 * 60,
            ReminderOffset.TEN_MINUTES_BEFORE,
            tehran,
        )
        val gregorianResult = RemindAtCalculator.calculateMillis(
            LocalDate.parse("2025-03-21"),
            8 * 60,
            ReminderOffset.TEN_MINUTES_BEFORE,
            tehran,
        )
        assertEquals(gregorianResult, jalaliResult)
    }

    @Test
    fun infersTheOffsetFromAStoredInstant() {
        val due = LocalDate.parse("2025-05-10")
        val stored = RemindAtCalculator.calculateMillis(
            due,
            18 * 60,
            ReminderOffset.ONE_HOUR_BEFORE,
            tehran,
        )
        assertEquals(
            ReminderOffset.ONE_HOUR_BEFORE,
            RemindAtCalculator.inferOffset(due, 18 * 60, stored, tehran),
        )
    }

    @Test
    fun leapDayIsHandled() {
        val result = RemindAtCalculator.calculate(
            dueDate = LocalDate.parse("2024-02-29"),
            dueTimeMinutes = 0,
            offset = ReminderOffset.AT_DUE,
            zone = tehran,
            nowMillis = 0L,
        )
        assertEquals(millis("2024-02-29", 0, 0, tehran), result.epochMillis)
    }
}
