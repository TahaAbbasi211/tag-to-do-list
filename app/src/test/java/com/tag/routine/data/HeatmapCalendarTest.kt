package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Unit tests for [HeatmapCalendar]: 12-week alignment, neutral vs zero-done distinction,
 * intensity ramp, future day padding, Persian Saturday-start vs Monday-start, and year/month
 * boundary continuity.
 */
class HeatmapCalendarTest {

    @Test
    fun `builds 12 chronological week columns aligned to firstDayOfWeek with future days padded`() {
        val wednesday = LocalDate.of(2026, 5, 13)
        val columns = HeatmapCalendar.build(
            stats = emptyList(),
            today = wednesday,
            weeks = 12,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(12, columns.size)
        assertEquals(LocalDate.of(2026, 2, 23), columns.first().weekStart)
        assertEquals(LocalDate.of(2026, 5, 11), columns.last().weekStart)

        val lastWeek = columns.last().cells
        assertEquals(7, lastWeek.size)
        // Mon..Wed (0..2) are not future; Wed (2) is today; Thu..Sun (3..6) are future placeholders.
        assertFalse(lastWeek[0].isFuture)
        assertFalse(lastWeek[2].isFuture)
        assertTrue(lastWeek[2].isToday)
        assertEquals(HeatmapIntensity.NEUTRAL, lastWeek[2].intensity)

        for (i in 3..6) {
            assertTrue(lastWeek[i].isFuture)
            assertNull(lastWeek[i].stat)
            assertEquals(HeatmapIntensity.FUTURE, lastWeek[i].intensity)
        }
    }

    @Test
    fun `distinguishes neutral days with no items from scheduled days with zero completed`() {
        val today = LocalDate.of(2026, 5, 13)
        val stats = listOf(
            DayStat(today.minusDays(4), scheduled = 0, completed = 0, taskCompleted = 0),
            DayStat(today.minusDays(3), scheduled = 4, completed = 0, taskCompleted = 0),
            DayStat(today.minusDays(2), scheduled = 4, completed = 1, taskCompleted = 0), // 25% -> L1
            DayStat(today.minusDays(1), scheduled = 4, completed = 2, taskCompleted = 0), // 50% -> L2
            DayStat(today, scheduled = 4, completed = 3, taskCompleted = 0),              // 75% -> L3
        )
        val columns = HeatmapCalendar.build(stats, today = today, weeks = 12, firstDayOfWeek = DayOfWeek.MONDAY)
        val allCells = columns.flatMap { it.cells }.associateBy { it.date }

        assertEquals(HeatmapIntensity.NEUTRAL, allCells[today.minusDays(4)]?.intensity)
        assertEquals(HeatmapIntensity.ZERO_DONE, allCells[today.minusDays(3)]?.intensity)
        assertEquals(HeatmapIntensity.LEVEL_1, allCells[today.minusDays(2)]?.intensity)
        assertEquals(HeatmapIntensity.LEVEL_2, allCells[today.minusDays(1)]?.intensity)
        assertEquals(HeatmapIntensity.LEVEL_3, allCells[today]?.intensity)
        assertEquals(
            HeatmapIntensity.COMPLETE,
            HeatmapCalendar.classify(DayStat(today, scheduled = 4, completed = 4, taskCompleted = 0)),
        )
    }

    @Test
    fun `Persian Saturday-start aligns rows Sat through Fri and crosses year boundary cleanly`() {
        // 2026-01-02 is a Friday (last day of Saturday-start week Dec 27 .. Jan 2).
        val friday = LocalDate.of(2026, 1, 2)
        val weekdayOrder = HeatmapCalendar.weekdayDates(DayOfWeek.SATURDAY).map { it.dayOfWeek }
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
            weekdayOrder,
        )

        val columns = HeatmapCalendar.build(
            stats = emptyList(),
            today = friday,
            weeks = 12,
            firstDayOfWeek = DayOfWeek.SATURDAY,
        )
        assertEquals(12, columns.size)
        assertEquals(LocalDate.of(2025, 12, 27), columns.last().weekStart)
        assertEquals(friday, columns.last().cells.last().date)
        assertTrue(columns.last().cells.last().isToday)
        assertFalse(columns.last().cells.last().isFuture)
    }

    @Test
    fun `Jalali monthLabels emit Persian month transitions across Nowruz boundary`() {
        // 2026-04-20 = 1405-01-31 (last day of Farvardin 1405)
        val today = LocalDate.of(2026, 4, 20)
        val columns = HeatmapCalendar.build(
            stats = emptyList(),
            today = today,
            weeks = 12,
            firstDayOfWeek = DayOfWeek.SATURDAY,
        )
        val labels = HeatmapCalendar.monthLabels(columns, CalendarType.JALALI)

        // Should cover Bahman 1404, Esfand 1404, and Farvardin 1405
        assertTrue(labels.any { it.year == 1404 && it.month == 12 })
        assertTrue(labels.any { it.year == 1405 && it.month == 1 })
        val farvardinLabel = labels.first { it.year == 1405 && it.month == 1 }
        assertTrue(farvardinLabel.columnIndex in 0 until columns.size)
        assertEquals("Farvardin", Dates.shortMonthName(farvardinLabel.referenceDate, java.util.Locale.ENGLISH, CalendarType.JALALI))
        assertEquals("فروردین", Dates.shortMonthName(farvardinLabel.referenceDate, java.util.Locale.forLanguageTag("fa"), CalendarType.JALALI))
    }
}
