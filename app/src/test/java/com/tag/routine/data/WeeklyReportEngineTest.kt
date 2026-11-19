package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Unit tests for [WeeklyReportEngine]: empty weeks, neutral days, incomplete current week
 * handling, previous-week comparisons, best/weakest items, month/year boundaries, and
 * large multi-year histories.
 */
class WeeklyReportEngineTest {

    private fun routine(
        id: String,
        title: String = "Routine $id",
        daysMask: Int = Dates.EVERY_DAY,
        repeatDaily: Boolean = true,
        dateEpochDay: Long = 0L,
        endDateEpochDay: Long? = null,
        archived: Boolean = false,
    ) = RoutineEntity(
        id = id,
        title = title,
        groupName = Groups.MORNING,
        icon = "spark",
        timeMinutes = null,
        daysMask = daysMask,
        reminder = false,
        colorHex = Palette.VIOLET,
        sortOrder = 0,
        archived = archived,
        createdAt = 0L,
        dateEpochDay = dateEpochDay,
        repeatDaily = repeatDaily,
        endDateEpochDay = endDateEpochDay,
    )

    private fun log(routineId: String, date: LocalDate) =
        RoutineLogEntity(routineId = routineId, date = Dates.iso(date), doneAt = 0L)

    // ------------------------------------------------------------ empty week

    @Test
    fun `empty week produces null percentages, zero counts, no items, and NO_BASELINE`() {
        val wednesday = LocalDate.of(2026, 5, 13)
        val report = WeeklyReportEngine.compute(
            routines = emptyList(),
            routineLogs = emptyList(),
            today = wednesday,
        )

        assertNull(report.currentWeekPercent)
        assertNull(report.previousWeekPercent)
        assertNull(report.deltaPercent)
        assertEquals(WeekComparison.NO_BASELINE, report.comparison)
        assertEquals(0, report.perfectDaysCurrentWeek)
        assertEquals(0, report.activeDaysCurrentWeek)
        assertEquals(0, report.perfectDaysPreviousWeek)
        assertEquals(0, report.activeDaysPreviousWeek)
        assertEquals(0, report.currentStreak)
        assertEquals(0, report.bestStreak)
        assertNull(report.bestItem)
        assertNull(report.weakestItem)
        assertFalse(report.hasAnyWeekData)
    }

    @Test
    fun `empty Insights days list does not crash`() {
        val emptyInsights = Insights(
            days = emptyList(),
            currentStreak = 0,
            bestStreak = 0,
            weeklyConsistency = 0,
            totalCompletions = 0,
            completedToday = 0,
            scheduledToday = 0,
            perfectDays = 0,
        )
        val report = WeeklyReportEngine.compute(
            insights = emptyInsights,
            routineRates = emptyList(),
            today = LocalDate.of(2026, 5, 13),
        )

        assertNull(report.currentWeekPercent)
        assertNull(report.previousWeekPercent)
        assertNull(report.deltaPercent)
        assertEquals(WeekComparison.NO_BASELINE, report.comparison)
    }

    @Test
    fun `days without scheduled items inside a week are neutral rather than failures`() {
        // Sunday (complete week: Mon May 11 .. Sun May 17, 2026).
        // Only Mon and Thu have one-off items scheduled, and both are completed.
        val sunday = LocalDate.of(2026, 5, 17)
        val mon = LocalDate.of(2026, 5, 11)
        val thu = LocalDate.of(2026, 5, 14)

        val rMon = routine("r-mon", "Monday task", repeatDaily = false, dateEpochDay = mon.toEpochDay())
        val rThu = routine("r-thu", "Thursday task", repeatDaily = false, dateEpochDay = thu.toEpochDay())

        val report = WeeklyReportEngine.compute(
            routines = listOf(rMon, rThu),
            routineLogs = listOf(log("r-mon", mon), log("r-thu", thu)),
            today = sunday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(100, report.currentWeekPercent)
        assertEquals(2, report.activeDaysCurrentWeek)
        assertEquals(2, report.perfectDaysCurrentWeek)
        assertEquals(2, report.currentStreak)
    }

    // ------------------------------------------------------------ incomplete week

    @Test
    fun `incomplete current week averages only elapsed days and avoids misleading delta`() {
        // Today is Wednesday May 13, 2026 -> elapsed = 3 days (Mon, Tue, Wed).
        // Last week (Mon May 4 .. Sun May 10): Mon-Tue 100%, Wed-Sun 0% -> ~29% full week.
        // Current week: Mon-Wed 100%. Must NOT report +71% as if the week were finished.
        val wednesday = LocalDate.of(2026, 5, 13)
        val prevMon = LocalDate.of(2026, 5, 4)
        val r = routine("r1", "Daily stretch", dateEpochDay = prevMon.toEpochDay())

        val logs = listOf(
            log("r1", prevMon),
            log("r1", prevMon.plusDays(1)),
            log("r1", LocalDate.of(2026, 5, 11)),
            log("r1", LocalDate.of(2026, 5, 12)),
            log("r1", wednesday),
        )

        val report = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = logs,
            today = wednesday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertTrue(report.isCurrentWeekIncomplete)
        assertEquals(3, report.elapsedDaysInCurrentWeek)
        assertEquals(100, report.currentWeekPercent)
        assertEquals(29, report.previousWeekPercent)
        assertEquals(3, report.perfectDaysCurrentWeek)
        assertEquals(3, report.activeDaysCurrentWeek)
        assertNull(report.deltaPercent)
        assertEquals(WeekComparison.INCOMPLETE_WEEK, report.comparison)
    }

    @Test
    fun `incomplete week on day 1 with no completions yet does not claim a crash vs last week`() {
        val monday = LocalDate.of(2026, 5, 11)
        val prevMon = LocalDate.of(2026, 5, 4)
        val r = routine("r1", "Read", dateEpochDay = prevMon.toEpochDay())
        val prevLogs = (0L..6L).map { log("r1", prevMon.plusDays(it)) }

        val report = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = prevLogs,
            today = monday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertTrue(report.isCurrentWeekIncomplete)
        assertEquals(1, report.elapsedDaysInCurrentWeek)
        assertEquals(0, report.currentWeekPercent)
        assertEquals(100, report.previousWeekPercent)
        assertNull(report.deltaPercent)
        assertEquals(WeekComparison.INCOMPLETE_WEEK, report.comparison)
    }

    // ------------------------------------------------------------ comparison with previous week

    @Test
    fun `complete week compares fairly with previous week across IMPROVED, DECLINED, and STEADY`() {
        val sunday = LocalDate.of(2026, 5, 17)
        val curMon = LocalDate.of(2026, 5, 11)
        val prevMon = LocalDate.of(2026, 5, 4)

        val r1 = routine("r1", "Water", dateEpochDay = prevMon.toEpochDay())
        val r2 = routine("r2", "Walk", dateEpochDay = prevMon.toEpochDay())

        // Previous week: r1 done all 7 days, r2 done 0 days -> 50% each day -> 50% weekly.
        // Current week: r1 and r2 done all 7 days -> 100% weekly -> +50% improved.
        val prevLogs = (0L..6L).map { log("r1", prevMon.plusDays(it)) }
        val curLogs = (0L..6L).flatMap { d ->
            listOf(log("r1", curMon.plusDays(d)), log("r2", curMon.plusDays(d)))
        }

        val improved = WeeklyReportEngine.compute(
            routines = listOf(r1, r2),
            routineLogs = prevLogs + curLogs,
            today = sunday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertFalse(improved.isCurrentWeekIncomplete)
        assertEquals(100, improved.currentWeekPercent)
        assertEquals(50, improved.previousWeekPercent)
        assertEquals(50, improved.deltaPercent)
        assertEquals(WeekComparison.IMPROVED, improved.comparison)
        assertEquals(7, improved.perfectDaysCurrentWeek)
        assertEquals(0, improved.perfectDaysPreviousWeek)
        assertEquals("r1", improved.bestItem?.routine?.id)
        assertEquals("r2", improved.weakestItem?.routine?.id)
        assertEquals(100, improved.bestItem?.percent)
        assertEquals(50, improved.weakestItem?.percent)
        assertEquals(7, improved.currentStreak)

        // Declined case: previous week 100%, current week 50% -> -50%.
        val prevAllLogs = (0L..6L).flatMap { d ->
            listOf(log("r1", prevMon.plusDays(d)), log("r2", prevMon.plusDays(d)))
        }
        val curHalfLogs = (0L..6L).map { log("r1", curMon.plusDays(it)) }
        val declined = WeeklyReportEngine.compute(
            routines = listOf(r1, r2),
            routineLogs = prevAllLogs + curHalfLogs,
            today = sunday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(50, declined.currentWeekPercent)
        assertEquals(100, declined.previousWeekPercent)
        assertEquals(-50, declined.deltaPercent)
        assertEquals(WeekComparison.DECLINED, declined.comparison)

        // Steady case: both weeks 100% -> delta 0, weakestItem is null because both are 100%.
        val steady = WeeklyReportEngine.compute(
            routines = listOf(r1, r2),
            routineLogs = prevAllLogs + curLogs,
            today = sunday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(100, steady.currentWeekPercent)
        assertEquals(100, steady.previousWeekPercent)
        assertEquals(0, steady.deltaPercent)
        assertEquals(WeekComparison.STEADY, steady.comparison)
        assertNotNull(steady.bestItem)
        assertNull(steady.weakestItem)
    }

    @Test
    fun `previous week with no scheduled items yields NO_BASELINE instead of comparing against zero`() {
        val sunday = LocalDate.of(2026, 5, 17)
        val curMon = LocalDate.of(2026, 5, 11)
        val r = routine("r1", "New habit", dateEpochDay = curMon.toEpochDay())
        val logs = (0L..6L).map { log("r1", curMon.plusDays(it)) }

        val report = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = logs,
            today = sunday,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(100, report.currentWeekPercent)
        assertNull(report.previousWeekPercent)
        assertNull(report.deltaPercent)
        assertEquals(WeekComparison.NO_BASELINE, report.comparison)
    }

    @Test
    fun `unscheduled old one-off routines outside window are not reported as weakest item`() {
        val today = LocalDate.of(2026, 5, 17)
        val oldOneOff = routine(
            id = "old",
            title = "Ancient task",
            repeatDaily = false,
            dateEpochDay = today.minusDays(60).toEpochDay(),
        )
        val active = routine(
            id = "act",
            title = "Daily water",
            repeatDaily = true,
            dateEpochDay = today.minusDays(14).toEpochDay(),
        )
        val logs = (0L..14L).map { log("act", today.minusDays(it)) }

        val report = WeeklyReportEngine.compute(
            routines = listOf(oldOneOff, active),
            routineLogs = logs,
            today = today,
        )

        assertEquals("act", report.bestItem?.routine?.id)
        assertNull(report.weakestItem)
    }

    // ------------------------------------------------------------ month & year boundaries

    @Test
    fun `month boundary handles February to March transition including leap year`() {
        // 2024 is a leap year: Feb 26 (Mon) .. Mar 3 (Sun) crosses Feb 29 -> Mar 1.
        val sundayMar3 = LocalDate.of(2024, 3, 3)
        val feb19 = LocalDate.of(2024, 2, 19)
        val r = routine("r1", "Journal", dateEpochDay = feb19.toEpochDay())

        // Previous week (Feb 19..25): 4 of 7 days done.
        // Current week (Feb 26..Mar 3): all 7 days done (includes Feb 29!).
        val prevLogs = (0L..3L).map { log("r1", feb19.plusDays(it)) }
        val curStart = LocalDate.of(2024, 2, 26)
        val curLogs = (0L..6L).map { log("r1", curStart.plusDays(it)) }

        val report = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = prevLogs + curLogs,
            today = sundayMar3,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(LocalDate.of(2024, 2, 26), report.currentWeekStart)
        assertEquals(LocalDate.of(2024, 3, 3), report.currentWeekEnd)
        assertEquals(LocalDate.of(2024, 2, 19), report.previousWeekStart)
        assertEquals(LocalDate.of(2024, 2, 25), report.previousWeekEnd)
        assertFalse(report.isCurrentWeekIncomplete)
        assertEquals(100, report.currentWeekPercent)
        assertEquals(57, report.previousWeekPercent)
        assertEquals(43, report.deltaPercent)
        assertEquals(WeekComparison.IMPROVED, report.comparison)
    }

    @Test
    fun `year boundary crosses December 31 to January 1 accurately in Monday and Saturday starts`() {
        // 2026-01-04 is Sunday; Monday-start current week is 2025-12-29 .. 2026-01-04.
        val jan4 = LocalDate.of(2026, 1, 4)
        val dec22 = LocalDate.of(2025, 12, 22)
        val r = routine("r1", "Meditation", dateEpochDay = dec22.toEpochDay())

        val prevLogs = (0L..6L).map { log("r1", dec22.plusDays(it)) } // 100%
        val curLogs = listOf(
            log("r1", LocalDate.of(2025, 12, 31)),
            log("r1", LocalDate.of(2026, 1, 1)),
            log("r1", LocalDate.of(2026, 1, 2)),
        ) // 3 of 7 days -> 43%

        val mondayReport = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = prevLogs + curLogs,
            today = jan4,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(LocalDate.of(2025, 12, 29), mondayReport.currentWeekStart)
        assertEquals(LocalDate.of(2026, 1, 4), mondayReport.currentWeekEnd)
        assertEquals(LocalDate.of(2025, 12, 22), mondayReport.previousWeekStart)
        assertEquals(LocalDate.of(2025, 12, 28), mondayReport.previousWeekEnd)
        assertEquals(43, mondayReport.currentWeekPercent)
        assertEquals(100, mondayReport.previousWeekPercent)
        assertEquals(-57, mondayReport.deltaPercent)
        assertEquals(WeekComparison.DECLINED, mondayReport.comparison)

        // Persian Saturday-start week for Friday 2026-01-02: Sat 2025-12-27 .. Fri 2026-01-02.
        val jan2 = LocalDate.of(2026, 1, 2)
        val satReport = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = prevLogs + curLogs,
            today = jan2,
            firstDayOfWeek = WeeklyReportEngine.defaultFirstDayOfWeek(Locale.forLanguageTag("fa")),
        )
        assertEquals(DayOfWeek.SATURDAY, WeeklyReportEngine.defaultFirstDayOfWeek(Locale.forLanguageTag("fa")))
        assertEquals(LocalDate.of(2025, 12, 27), satReport.currentWeekStart)
        assertEquals(LocalDate.of(2026, 1, 2), satReport.currentWeekEnd)
        assertEquals(LocalDate.of(2025, 12, 20), satReport.previousWeekStart)
        assertEquals(LocalDate.of(2025, 12, 26), satReport.previousWeekEnd)
        assertFalse(satReport.isCurrentWeekIncomplete)
    }

    // ------------------------------------------------------------ multi-year scale

    @Test
    fun `years of routine logs compute accurately without overflow or crash`() {
        val today = LocalDate.of(2026, 11, 18) // Wednesday
        val fourYearsAgo = today.minusYears(4)
        val r1 = routine("r1", "Morning walk", dateEpochDay = fourYearsAgo.toEpochDay())
        val r2 = routine("r2", "Evening read", dateEpochDay = fourYearsAgo.toEpochDay())

        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(fourYearsAgo, today)
        val logs = ArrayList<RoutineLogEntity>((totalDays * 2).toInt())
        var cursor = fourYearsAgo
        while (!cursor.isAfter(today)) {
            logs.add(log("r1", cursor))
            if (cursor.dayOfMonth % 2 == 0) {
                logs.add(log("r2", cursor))
            }
            cursor = cursor.plusDays(1)
        }

        val report = WeeklyReportEngine.compute(
            routines = listOf(r1, r2),
            routineLogs = logs,
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertTrue(report.isCurrentWeekIncomplete)
        assertEquals("r1", report.bestItem?.routine?.id)
        assertEquals("r2", report.weakestItem?.routine?.id)
        assertTrue(report.currentStreak >= 1)
    }

    @Test
    fun `Jalali calendar groups weeks Saturday to Friday even under English locale`() {
        val firstDay = WeeklyReportEngine.defaultFirstDayOfWeek(CalendarType.JALALI, Locale.ENGLISH)
        assertEquals(DayOfWeek.SATURDAY, firstDay)

        // 2026-03-21 is Saturday 1 Farvardin 1405
        val nowruz1405 = LocalDate.of(2026, 3, 21)
        val thursday = nowruz1405.plusDays(5) // 2026-03-26 (6th day of week: Sat..Thu)
        val r = routine("r1", "Nowruz", dateEpochDay = nowruz1405.minusDays(7).toEpochDay())
        val report = WeeklyReportEngine.compute(
            routines = listOf(r),
            routineLogs = listOf(log("r1", nowruz1405)),
            today = thursday,
            firstDayOfWeek = firstDay,
        )

        assertEquals(nowruz1405, report.currentWeekStart)
        assertEquals(nowruz1405.plusDays(6), report.currentWeekEnd) // Friday 2026-03-27
        assertEquals(nowruz1405.minusDays(7), report.previousWeekStart) // Saturday 2026-03-14
        assertEquals(nowruz1405.minusDays(1), report.previousWeekEnd) // Friday 2026-03-20
        assertEquals(6, report.elapsedDaysInCurrentWeek)
        assertTrue(report.isCurrentWeekIncomplete)
    }
}
