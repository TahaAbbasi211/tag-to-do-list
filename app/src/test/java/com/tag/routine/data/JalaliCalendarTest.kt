package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Unit tests for [JalaliCalendar] and Jalali support in [Dates].
 * Reference Gregorian <-> Jalali mappings are cross-checked against ICU's Persian calendar.
 */
class JalaliCalendarTest {

    @Test
    fun `verified reference dates round-trip accurately`() {
        val cases = listOf(
            LocalDate.of(1970, 1, 1) to JalaliDate(1348, 10, 11),
            LocalDate.of(1979, 2, 11) to JalaliDate(1357, 11, 22),
            LocalDate.of(1997, 3, 20) to JalaliDate(1375, 12, 30),
            LocalDate.of(2000, 2, 29) to JalaliDate(1378, 12, 10),
            LocalDate.of(2021, 3, 20) to JalaliDate(1399, 12, 30),
            LocalDate.of(2024, 3, 19) to JalaliDate(1402, 12, 29),
            LocalDate.of(2024, 3, 20) to JalaliDate(1403, 1, 1),
            LocalDate.of(2025, 3, 19) to JalaliDate(1403, 12, 29),
            LocalDate.of(2025, 3, 20) to JalaliDate(1403, 12, 30),
            LocalDate.of(2025, 3, 21) to JalaliDate(1404, 1, 1),
            LocalDate.of(2026, 3, 20) to JalaliDate(1404, 12, 29),
            LocalDate.of(2026, 3, 21) to JalaliDate(1405, 1, 1),
            LocalDate.of(2026, 11, 19) to JalaliDate(1405, 8, 28),
            LocalDate.of(2030, 3, 20) to JalaliDate(1408, 12, 30),
            LocalDate.of(2030, 3, 21) to JalaliDate(1409, 1, 1),
        )

        for ((gregorian, jalali) in cases) {
            val epochDay = gregorian.toEpochDay()
            assertEquals("epochDay -> Jalali failed for $gregorian", jalali, Dates.epochDayToJalali(epochDay))
            assertEquals("Jalali -> epochDay failed for $jalali", epochDay, Dates.jalaliToEpochDay(jalali.year, jalali.month, jalali.day))
            assertEquals(gregorian, Dates.fromJalali(jalali.year, jalali.month, jalali.day))
        }
    }

    @Test
    fun `month lengths follow 31-30-29 or 30 rule for leap years`() {
        for (year in listOf(1402, 1403, 1404, 1405, 1408)) {
            for (month in 1..6) {
                assertEquals("Month $month in $year should be 31 days", 31, Dates.persianMonthLength(year, month))
            }
            for (month in 7..11) {
                assertEquals("Month $month in $year should be 30 days", 30, Dates.persianMonthLength(year, month))
            }
        }

        // Leap years verified against ICU PersianCalendar: 1375, 1379, 1399, 1403, 1408
        for (leapYear in listOf(1375, 1379, 1383, 1387, 1391, 1395, 1399, 1403, 1408)) {
            assertTrue("$leapYear should be leap", Dates.isPersianLeapYear(leapYear))
            assertEquals(30, Dates.persianMonthLength(leapYear, 12))
        }

        // Non-leap years verified against ICU PersianCalendar
        for (commonYear in listOf(1401, 1402, 1404, 1405, 1406, 1407)) {
            assertFalse("$commonYear should not be leap", Dates.isPersianLeapYear(commonYear))
            assertEquals(29, Dates.persianMonthLength(commonYear, 12))
        }
    }

    @Test
    fun `year boundaries advance cleanly across 29 and 30 Esfand to 1 Farvardin`() {
        // Common year boundary: 1404-12-29 -> 1405-01-01
        val esfand29_1404 = Dates.jalaliToEpochDay(1404, 12, 29)
        val farvardin1_1405 = Dates.jalaliToEpochDay(1405, 1, 1)
        assertEquals(esfand29_1404 + 1L, farvardin1_1405)
        assertEquals(JalaliDate(1405, 1, 1), Dates.epochDayToJalali(esfand29_1404 + 1L))

        // Leap year boundary: 1403-12-29 -> 1403-12-30 -> 1404-01-01
        val esfand29_1403 = Dates.jalaliToEpochDay(1403, 12, 29)
        val esfand30_1403 = Dates.jalaliToEpochDay(1403, 12, 30)
        val farvardin1_1404 = Dates.jalaliToEpochDay(1404, 1, 1)
        assertEquals(esfand29_1403 + 1L, esfand30_1403)
        assertEquals(esfand30_1403 + 1L, farvardin1_1404)
        assertEquals(JalaliDate(1403, 12, 30), Dates.epochDayToJalali(esfand29_1403 + 1L))
        assertEquals(JalaliDate(1404, 1, 1), Dates.epochDayToJalali(esfand30_1403 + 1L))

        // Future leap boundary: 1408-12-30 -> 1409-01-01
        assertEquals(
            Dates.jalaliToEpochDay(1408, 12, 30) + 1L,
            Dates.jalaliToEpochDay(1409, 1, 1),
        )
    }

    @Test
    fun `invalid Jalali dates throw IllegalArgumentException`() {
        val invalidInputs = listOf(
            Triple(1404, 12, 30), // 1404 is not a leap year
            Triple(1405, 7, 31),  // Mehr has 30 days
            Triple(1405, 0, 1),   // month < 1
            Triple(1405, 13, 1),  // month > 12
            Triple(1405, 1, 0),   // day < 1
            Triple(1405, 1, 32),  // day > 31
        )
        for ((y, m, d) in invalidInputs) {
            val threw = runCatching { Dates.jalaliToEpochDay(y, m, d) }.isFailure
            assertTrue("Expected failure for invalid Jalali date $y-$m-$d", threw)
        }
    }

    @Test
    fun `exhaustive multi-decade epochDay round-trip and monotonic progression`() {
        val start = LocalDate.of(1970, 1, 1).toEpochDay()
        val end = LocalDate.of(2045, 12, 31).toEpochDay()
        var prev = Dates.epochDayToJalali(start)
        assertEquals(start, Dates.jalaliToEpochDay(prev.year, prev.month, prev.day))

        for (epochDay in (start + 1)..end) {
            val current = Dates.epochDayToJalali(epochDay)
            assertEquals(
                "Round-trip failed at epochDay=$epochDay ($current)",
                epochDay,
                Dates.jalaliToEpochDay(current.year, current.month, current.day),
            )
            // Verify strict +1 day progression inside Jalali calendar
            val expectedNext = when {
                prev.day < Dates.persianMonthLength(prev.year, prev.month) ->
                    JalaliDate(prev.year, prev.month, prev.day + 1)
                prev.month < 12 ->
                    JalaliDate(prev.year, prev.month + 1, 1)
                else ->
                    JalaliDate(prev.year + 1, 1, 1)
            }
            assertEquals("Progression mismatch at epochDay=$epochDay", expectedNext, current)
            prev = current
        }
    }

    @Test
    fun `Jalali month names stay Persian in all app languages while digits follow locale`() {
        val date = LocalDate.of(2026, 3, 21) // 1 Farvardin 1405
        val fa = Locale.forLanguageTag("fa")
        val nonPersianLocales = listOf(
            Locale.ENGLISH,
            Locale.GERMAN,
            Locale.FRENCH,
            Locale.forLanguageTag("tr"),
        )

        assertEquals("فروردین", Dates.monthName(date, Locale.ENGLISH, CalendarType.JALALI))
        assertEquals("فروردین", Dates.shortMonthName(date, Locale.GERMAN, CalendarType.JALALI))
        assertEquals("۱ فروردین", Dates.shortDate(date, fa, CalendarType.JALALI))
        assertEquals("فروردین ۱۴۰۵", Dates.monthYearTitle(date, fa, CalendarType.JALALI))

        for (loc in nonPersianLocales) {
            assertEquals("1 فروردین", Dates.shortDate(date, loc, CalendarType.JALALI))
            assertEquals("فروردین 1405", Dates.monthYearTitle(date, loc, CalendarType.JALALI))
            assertTrue(Dates.dayMonth(date, loc, CalendarType.JALALI).contains("فروردین"))
            assertTrue(Dates.full(date, loc, CalendarType.JALALI).contains("1405"))
        }

        // All 12 Persian month names in order
        assertEquals(
            listOf(
                "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
            ),
            Dates.persianMonthNames(),
        )
    }

    @Test
    fun `week starts on Saturday in Jalali mode regardless of locale`() {
        assertEquals(DayOfWeek.SATURDAY, Dates.firstDayOfWeek(CalendarType.JALALI, Locale.ENGLISH))
        assertEquals(DayOfWeek.SATURDAY, Dates.firstDayOfWeek(CalendarType.JALALI, Locale.GERMAN))
        assertEquals(DayOfWeek.SATURDAY, Dates.firstDayOfWeek(CalendarType.JALALI, Locale.forLanguageTag("fa")))

        // Default Gregorian preserves Monday for non-Persian and Saturday for Persian
        assertEquals(DayOfWeek.MONDAY, Dates.firstDayOfWeek(CalendarType.GREGORIAN, Locale.ENGLISH))
        assertEquals(DayOfWeek.SATURDAY, Dates.firstDayOfWeek(CalendarType.GREGORIAN, Locale.forLanguageTag("fa")))

        val jalaliWeekdays = Dates.weekdayOptions(CalendarType.JALALI, Locale.ENGLISH)
        assertEquals(
            listOf(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            jalaliWeekdays.map { it.dayOfWeek },
        )
        // Stored daysMask bits remain Monday-based (Saturday == bit 5)
        assertEquals(1 shl 5, jalaliWeekdays.first().bit)
        assertEquals(1 shl 6, jalaliWeekdays[1].bit)
        assertEquals(1 shl 0, jalaliWeekdays[2].bit)
    }

    @Test
    fun `CalendarType defaults to Gregorian for legacy or unknown settings`() {
        assertEquals(CalendarType.GREGORIAN, AppSettings().calendarType)
        assertEquals(CalendarType.GREGORIAN, CalendarType.fromKey(null))
        assertEquals(CalendarType.GREGORIAN, CalendarType.fromKey(""))
        assertEquals(CalendarType.GREGORIAN, CalendarType.fromKey("unknown"))
        assertEquals(CalendarType.JALALI, CalendarType.fromKey("jalali"))
    }
}
