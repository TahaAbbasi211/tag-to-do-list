package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Unit tests for [FocusSummaryEngine]: daily focus totals, unlinked / deleted-routine
 * focus sessions, and per-item focus ranking.
 */
class FocusSummaryEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 4, 10)

    private fun routine(id: String, title: String) = RoutineEntity(
        id = id,
        title = title,
        groupName = Groups.MORNING,
        icon = "book",
        timeMinutes = null,
        daysMask = Dates.EVERY_DAY,
        reminder = false,
        colorHex = Palette.VIOLET,
        sortOrder = 0,
        archived = false,
        createdAt = 0L,
    )

    private fun at(date: LocalDate, hour: Int = 10, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        id: String,
        date: LocalDate,
        minutes: Int,
        routineId: String? = null,
        completed: Boolean = true,
        hour: Int = 10,
        minute: Int = 0,
    ): FocusSessionEntity {
        val start = at(date, hour, minute)
        return FocusSessionEntity(
            id = id,
            routineId = routineId,
            startedAt = start,
            endedAt = start + minutes * 60_000L,
            plannedMinutes = minutes,
            completed = completed,
        )
    }

    // ------------------------------------------------------------ daily totals

    @Test
    fun `daily focus sum totals all sessions on the target day and excludes adjacent days`() {
        val sessions = listOf(
            session("y1", today.minusDays(1), 40, routineId = "r1", hour = 23, minute = 59),
            session("t1", today, 25, routineId = "r1", hour = 0, minute = 0),
            session("t2", today, 15, routineId = null, completed = false, hour = 14, minute = 30),
            session("t3", today, 30, routineId = "r2", hour = 23, minute = 55),
            session("f1", today.plusDays(1), 50, routineId = "r1", hour = 0, minute = 0),
        )

        val summary = FocusSummaryEngine.compute(
            sessions = sessions,
            routines = listOf(routine("r1", "Study"), routine("r2", "Read")),
            today = today,
            zone = zone,
        )

        assertEquals(70, summary.todayMinutes)
        assertEquals(70, FocusSummaryEngine.minutesForDay(sessions, today, zone))
        assertEquals(40, FocusSummaryEngine.minutesForDay(sessions, today.minusDays(1), zone))
        assertEquals(50, FocusSummaryEngine.minutesForDay(sessions, today.plusDays(1), zone))
        assertEquals(160, summary.totalMinutes)
    }

    @Test
    fun `daily focus sum returns zero when no sessions exist on that day`() {
        val sessions = listOf(
            session("y1", today.minusDays(1), 25, routineId = "r1"),
        )
        val summary = FocusSummaryEngine.compute(
            sessions = sessions,
            routines = listOf(routine("r1", "Study")),
            today = today,
            zone = zone,
        )
        assertEquals(0, summary.todayMinutes)
        assertEquals(25, summary.totalMinutes)
    }

    @Test
    fun `reversed timestamps and empty lists never produce negative daily totals`() {
        val emptySummary = FocusSummaryEngine.compute(emptyList(), emptyList(), today, zone)
        assertEquals(0, emptySummary.todayMinutes)
        assertEquals(0, emptySummary.totalMinutes)
        assertTrue(emptySummary.topItems.isEmpty())

        val broken = FocusSessionEntity(
            id = "broken",
            routineId = "r1",
            startedAt = at(today, 12, 0),
            endedAt = at(today, 11, 30),
            plannedMinutes = 25,
            completed = false,
        )
        val valid = session("ok", today, 20, routineId = "r1")
        val summary = FocusSummaryEngine.compute(
            sessions = listOf(broken, valid),
            routines = listOf(routine("r1", "Study")),
            today = today,
            zone = zone,
        )
        assertEquals(20, summary.todayMinutes)
        assertEquals(20, summary.totalMinutes)
    }

    // ------------------------------------------------------------ unlinked items

    @Test
    fun `sessions without a routine stay intact and count in today and total focus time`() {
        val sessions = listOf(
            session("u1", today, 25, routineId = null),
            session("u2", today.minusDays(2), 35, routineId = null),
        )
        val summary = FocusSummaryEngine.compute(
            sessions = sessions,
            routines = listOf(routine("r1", "Reading")),
            today = today,
            zone = zone,
        )

        assertEquals(25, summary.todayMinutes)
        assertEquals(60, summary.totalMinutes)
        assertEquals(60, summary.unlinkedMinutes)
        assertEquals(2, summary.unlinkedSessions)
        assertEquals(2, summary.totalSessions)
        assertTrue("Unlinked sessions must not create phantom items", summary.topItems.isEmpty())
    }

    @Test
    fun `sessions whose linked routine was deleted remain in totals and fall into unlinked`() {
        val sessions = listOf(
            session("deleted", today, 30, routineId = "removed-id"),
            session("active", today, 15, routineId = "r1"),
        )
        val summary = FocusSummaryEngine.compute(
            sessions = sessions,
            routines = listOf(routine("r1", "Meditation")),
            today = today,
            zone = zone,
        )

        assertEquals(45, summary.todayMinutes)
        assertEquals(45, summary.totalMinutes)
        assertEquals(30, summary.unlinkedMinutes)
        assertEquals(1, summary.unlinkedSessions)
        assertEquals(1, summary.topItems.size)
        assertEquals("r1", summary.topItems.single().routine.id)
        assertEquals(15, summary.topItems.single().minutes)
    }

    // ------------------------------------------------------------ per-item ranking

    @Test
    fun `per-item focus aggregates multi-day sessions and ranks highest total first`() {
        val rStudy = routine("r-study", "Deep study")
        val rCode = routine("r-code", "Coding")
        val rWalk = routine("r-walk", "Evening walk")

        val sessions = listOf(
            session("s1", today, 25, routineId = "r-study"),
            session("s2", today.minusDays(1), 50, routineId = "r-study"),
            session("s3", today, 60, routineId = "r-code"),
            session("s4", today.minusDays(3), 20, routineId = "r-walk"),
            session("s5", today, 10, routineId = null),
        )

        val summary = FocusSummaryEngine.compute(
            sessions = sessions,
            routines = listOf(rStudy, rCode, rWalk),
            today = today,
            zone = zone,
        )

        assertEquals(95, summary.todayMinutes)
        assertEquals(165, summary.totalMinutes)
        assertEquals(10, summary.unlinkedMinutes)
        assertEquals(listOf("r-study", "r-code", "r-walk"), summary.topItems.map { it.routine.id })
        assertEquals(listOf(75, 60, 20), summary.topItems.map { it.minutes })
        assertEquals(listOf(2, 1, 1), summary.topItems.map { it.sessionCount })
        assertEquals(
            summary.totalMinutes,
            summary.topItems.sumOf { it.minutes } + summary.unlinkedMinutes,
        )
    }

    @Test
    fun `formatDuration formats minutes and hours with locale digits`() {
        val fa = Locale.forLanguageTag("fa")
        assertEquals("25m", Dates.formatDuration(25, resources = null, locale = Locale.ENGLISH))
        assertEquals("2h", Dates.formatDuration(120, resources = null, locale = Locale.ENGLISH))
        assertEquals("1h 15m", Dates.formatDuration(75, resources = null, locale = Locale.ENGLISH))
        assertEquals("۲۵m", Dates.formatDuration(25, resources = null, locale = fa))
        assertEquals("۱h ۱۵m", Dates.formatDuration(75, resources = null, locale = fa))
    }
}
