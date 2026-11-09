package com.tag.routine.data

import com.tag.routine.ui.FocusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers the focus-timer arithmetic behind the Focus screen: duration clamping and the
 * day/week/total aggregation, including the streak rule.
 */
class FocusEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2025, 3, 20)

    private fun at(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        id: String,
        date: LocalDate,
        minutes: Int,
        completed: Boolean = true,
        routineId: String? = null,
    ): FocusSessionEntity {
        val start = at(date)
        return FocusSessionEntity(
            id = id,
            routineId = routineId,
            startedAt = start,
            endedAt = start + minutes * 60_000L,
            plannedMinutes = minutes,
            completed = completed,
        )
    }

    // ------------------------------------------------------------ clamping

    @Test
    fun `custom duration is clamped into the supported range`() {
        assertEquals(AppConstants.MIN_FOCUS_MINUTES, AppConstants.clampFocusMinutes(0))
        assertEquals(AppConstants.MIN_FOCUS_MINUTES, AppConstants.clampFocusMinutes(-30))
        assertEquals(AppConstants.MAX_FOCUS_MINUTES, AppConstants.clampFocusMinutes(100_000))
    }

    @Test
    fun `durations above the old sixty minute cap are preserved`() {
        // The whole point of the custom field: 90 and 120 minutes must survive.
        assertEquals(90, AppConstants.clampFocusMinutes(90))
        assertEquals(120, AppConstants.clampFocusMinutes(120))
        assertTrue(AppConstants.MAX_FOCUS_MINUTES > 60)
    }

    @Test
    fun `focus presets are all inside the allowed range`() {
        for (preset in AppConstants.FOCUS_PRESETS) {
            assertEquals(preset, AppConstants.clampFocusMinutes(preset))
        }
    }

    // ------------------------------------------------------------ aggregation

    @Test
    fun `totals split into today week and all time`() {
        val sessions = listOf(
            session("a", today, 25),
            session("b", today, 35),
            session("c", today.minusDays(3), 50),
            session("d", today.minusDays(30), 60),
        )

        val stats = FocusEngine.compute(sessions, today = today, zone = zone)

        assertEquals(60, stats.todayMinutes)
        assertEquals(110, stats.weekMinutes)
        assertEquals(170, stats.totalMinutes)
        assertEquals(2, stats.todaySessions)
        assertEquals(4, stats.totalSessions)
    }

    @Test
    fun `average is zero when there are no sessions`() {
        val stats = FocusEngine.compute(emptyList(), today = today, zone = zone)
        assertEquals(0, stats.averageSessionMinutes)
        assertEquals(0, stats.totalMinutes)
        assertEquals(0, stats.streak)
        assertTrue(stats.recent.isEmpty())
    }

    @Test
    fun `average divides total minutes by session count`() {
        val sessions = listOf(
            session("a", today, 20),
            session("b", today, 40),
            session("c", today, 30),
        )
        val stats = FocusEngine.compute(sessions, today = today, zone = zone)
        assertEquals(30, stats.averageSessionMinutes)
    }

    // ------------------------------------------------------------ streak

    @Test
    fun `consecutive days ending today build a streak`() {
        val sessions = listOf(
            session("a", today, 10),
            session("b", today.minusDays(1), 10),
            session("c", today.minusDays(2), 10),
        )
        assertEquals(3, FocusEngine.compute(sessions, today = today, zone = zone).streak)
    }

    @Test
    fun `an idle today does not break yesterday's streak`() {
        // Today is still in progress, so grace is applied exactly as routines do it.
        val sessions = listOf(
            session("a", today.minusDays(1), 10),
            session("b", today.minusDays(2), 10),
        )
        assertEquals(2, FocusEngine.compute(sessions, today = today, zone = zone).streak)
    }

    @Test
    fun `a gap stops the streak at the gap`() {
        val sessions = listOf(
            session("a", today, 10),
            session("b", today.minusDays(2), 10),
        )
        assertEquals(1, FocusEngine.compute(sessions, today = today, zone = zone).streak)
    }

    @Test
    fun `several sessions on one day only count once toward the streak`() {
        val sessions = listOf(
            session("a", today, 10),
            session("b", today, 10),
            session("c", today.minusDays(1), 10),
        )
        assertEquals(2, FocusEngine.compute(sessions, today = today, zone = zone).streak)
    }

    // ------------------------------------------------------------ history

    @Test
    fun `sessions are listed newest first and resolve routine titles`() {
        val routines = listOf(
            RoutineEntity(
                id = "r1",
                title = "Reading",
                groupName = Groups.MORNING,
                icon = "book",
                timeMinutes = null,
                daysMask = Dates.EVERY_DAY,
                reminder = false,
                colorHex = Palette.VIOLET,
                sortOrder = 0,
                archived = false,
                createdAt = 0L,
            ),
        )
        val sessions = listOf(
            session("old", today.minusDays(2), 15, routineId = "r1"),
            session("new", today, 45, routineId = null),
        )

        val stats = FocusEngine.compute(sessions, routines = routines, today = today, zone = zone)

        assertEquals(listOf("new", "old"), stats.recent.map { it.id })
        assertEquals(null, stats.recent[0].routineTitle)
        assertEquals("Reading", stats.recent[1].routineTitle)
        assertEquals(45, stats.recent[0].minutes)
    }

    @Test
    fun `a deleted routine leaves the title null rather than dropping the session`() {
        val sessions = listOf(session("a", today, 20, routineId = "missing"))
        val stats = FocusEngine.compute(sessions, routines = emptyList(), today = today, zone = zone)
        assertEquals(1, stats.recent.size)
        assertEquals(null, stats.recent[0].routineTitle)
        assertEquals(1, stats.totalSessions)
    }

    @Test
    fun `abandoned sessions still count toward time and are not marked completed`() {
        val sessions = listOf(
            session("a", today, 5, completed = false),
            session("b", today, 25, completed = true),
        )
        val stats = FocusEngine.compute(sessions, today = today, zone = zone)
        assertEquals(30, stats.todayMinutes)
        assertEquals(1, stats.completedSessions)
        assertEquals(2, stats.totalSessions)
    }

    @Test
    fun `a session with a reversed clock does not produce negative minutes`() {
        val broken = FocusSessionEntity(
            id = "broken",
            routineId = null,
            startedAt = at(today),
            endedAt = at(today) - 60_000L,
            plannedMinutes = 25,
            completed = false,
        )
        val stats = FocusEngine.compute(listOf(broken), today = today, zone = zone)
        assertEquals(0, stats.totalMinutes)
    }

    // ------------------------------------------------------------ timer state

    @Test
    fun `focus state derives remaining time and progress`() {
        val state = FocusState(
            routineId = "r",
            plannedMinutes = 100,
            elapsedSeconds = 1500,
            running = true,
        )
        assertEquals(6000, state.totalSeconds)
        assertEquals(4500, state.remainingSeconds)
        assertEquals(0.25f, state.progress, 0.0001f)
    }

    @Test
    fun `remaining time never goes negative and progress is capped`() {
        val state = FocusState(plannedMinutes = 5, elapsedSeconds = 9_999)
        assertEquals(0, state.remainingSeconds)
        assertEquals(1f, state.progress, 0.0001f)
    }

    @Test
    fun `a hundred minute session is a valid focus length`() {
        // Regression guard: the sheet used to cap the timer at 60 minutes.
        val state = FocusState(plannedMinutes = 100, elapsedSeconds = 0)
        assertEquals(6000, state.totalSeconds)
        assertEquals(100, state.plannedMinutes)
    }
}
