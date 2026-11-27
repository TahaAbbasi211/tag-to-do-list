package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Unit tests for the pure date helpers. These run on the JVM, so they must not
 * touch any Android framework class.
 */
class DatesTest {

    @Test
    fun `iso and parse round-trip`() {
        val date = LocalDate.of(2026, 3, 9)
        val iso = Dates.iso(date)
        assertEquals("2026-03-09", iso)
        assertEquals(date, Dates.parse(iso))
    }

    @Test
    fun `parse returns null for malformed input`() {
        assertNull(Dates.parse("not-a-date"))
        assertNull(Dates.parse(""))
        assertNull(Dates.parse("2026-13-01"))
    }

    @Test
    fun `weekdayBit maps Monday to the lowest bit`() {
        // 2026-03-09 is a Monday.
        assertEquals(1 shl 0, Dates.weekdayBit(LocalDate.of(2026, 3, 9)))
        // 2026-03-15 is a Sunday, the seventh bit.
        assertEquals(1 shl 6, Dates.weekdayBit(LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun `EVERY_DAY covers all seven weekdays`() {
        val monday = LocalDate.of(2026, 3, 9)
        for (offset in 0..6) {
            val bit = Dates.weekdayBit(monday.plusDays(offset.toLong()))
            assertTrue("day $offset not in EVERY_DAY", (Dates.EVERY_DAY and bit) != 0)
        }
    }

    @Test
    fun `formatDigits renders Persian digits only for fa locale and Latin otherwise`() {
        val fa = Locale.forLanguageTag("fa")
        val en = Locale.ENGLISH
        val de = Locale.GERMAN

        assertEquals("۱۰۰\u066A", Dates.formatDigits("100%", fa))
        assertEquals("۱۰۰\u066A", Dates.formatPercent(100, fa))
        assertEquals("\u066A", Dates.percentSymbol(fa))
        assertEquals("۳ / ۳", Dates.formatDigits("3 / 3", fa))
        assertEquals("۰۷:۳۰", Dates.timeLabel(7 * 60 + 30, fa))

        assertEquals("100%", Dates.formatDigits("۱۰۰\u066A", en))
        assertEquals("100%", Dates.formatPercent(100, en))
        assertEquals("%", Dates.percentSymbol(en))
        assertEquals("3 / 3", Dates.formatDigits("۳ / ۳", de))
        assertEquals("07:30", Dates.timeLabel(7 * 60 + 30, en))
    }

    @Test
    fun `dayOfWeekLabel follows explicit locale instead of phone default`() {
        val monday = LocalDate.of(2026, 3, 9)
        val faLabel = Dates.dayOfWeekLabel(monday, Locale.forLanguageTag("fa"))
        val enLabel = Dates.dayOfWeekLabel(monday, Locale.ENGLISH)

        assertNotEquals(enLabel, faLabel)
        assertTrue(enLabel.startsWith("M", ignoreCase = true))
        assertTrue(faLabel.startsWith("د"))
    }

    @Test
    fun `monthYearRange and relativeDayLabel respect calendarType and digits`() {
        val fa = Locale.forLanguageTag("fa")
        val en = Locale.ENGLISH
        val esfand25 = LocalDate.of(2026, 3, 16) // 1404-12-25
        val farvardin8 = LocalDate.of(2026, 3, 28) // 1405-01-08
        val farvardin1 = LocalDate.of(2026, 3, 21) // 1405-01-01
        val aban28 = LocalDate.of(2026, 11, 19) // 1405-08-28

        assertEquals("اسفند ۱۴۰۴ – فروردین ۱۴۰۵", Dates.monthYearRange(esfand25, farvardin8, fa, CalendarType.JALALI))
        assertEquals("Esfand 1404 – Farvardin 1405", Dates.monthYearRange(esfand25, farvardin8, en, CalendarType.JALALI))
        assertEquals("آبان ۱۴۰۵", Dates.monthYearRange(aban28, aban28, fa, CalendarType.JALALI))
        assertEquals("Aban 1405", Dates.monthYearRange(aban28, aban28, Locale.FRENCH, CalendarType.JALALI))

        // Beyond +/- 6 days, relativeDayLabel falls back to dayMonth in the active calendar.
        val farLabelFa = Dates.relativeDayLabel(farvardin1, aban28, null, fa, CalendarType.JALALI)
        val farLabelEn = Dates.relativeDayLabel(farvardin1, aban28, null, en, CalendarType.JALALI)
        assertTrue(farLabelFa.contains("۱ فروردین"))
        assertTrue(farLabelEn.contains("1 Farvardin"))
    }

    @Test
    fun `nextWeekend returns Friday in Jalali and Saturday in Gregorian across all weekdays`() {
        // 2026-11-23 is Monday.
        val monday = LocalDate.of(2026, 11, 23)
        for (offset in 0L..6L) {
            val day = monday.plusDays(offset)

            val jalaliWeekend = Dates.nextWeekend(day, CalendarType.JALALI)
            assertEquals(DayOfWeek.FRIDAY, jalaliWeekend.dayOfWeek)
            assertFalse(jalaliWeekend.isBefore(day))
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(day, jalaliWeekend) in 0L..6L)

            val gregorianWeekend = Dates.nextWeekend(day, CalendarType.GREGORIAN)
            assertEquals(DayOfWeek.SATURDAY, gregorianWeekend.dayOfWeek)
            assertFalse(gregorianWeekend.isBefore(day))
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(day, gregorianWeekend) in 0L..6L)
        }

        val friday = LocalDate.of(2026, 11, 27)
        val saturday = LocalDate.of(2026, 11, 28)
        assertEquals(friday, Dates.nextWeekend(friday, CalendarType.JALALI))
        assertEquals(saturday, Dates.nextWeekend(saturday, CalendarType.GREGORIAN))
        // On Saturday in Jalali mode, next Friday is 6 days ahead.
        assertEquals(friday.plusWeeks(1), Dates.nextWeekend(saturday, CalendarType.JALALI))
    }

    @Test
    fun `toLatinDigits accepts Persian and Arabic-Indic keyboards`() {
        assertEquals("2025", Dates.toLatinDigits("۲۰۲۵"))
        assertEquals("09:30", Dates.toLatinDigits("٠٩:٣٠"))
        assertEquals("12", Dates.toLatinDigits("12"))
        assertEquals("abc", Dates.toLatinDigits("abc"))
    }
}
