package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the streak and consistency maths. Everything here is pure JVM
 * logic, so the tests run without an emulator.
 */
class StatsEngineTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15) // a Sunday

    private fun routine(
        id: String,
        daysMask: Int = Dates.EVERY_DAY,
        createdAt: Long = 0L,
        archived: Boolean = false,
    ) = RoutineEntity(
        id = id,
        title = "Routine $id",
        groupName = Groups.MORNING,
        icon = "sun",
        timeMinutes = null,
        daysMask = daysMask,
        reminder = false,
        colorHex = 0xFF7C6CFF,
        sortOrder = 0,
        archived = archived,
        createdAt = createdAt,
    )

    private fun log(routineId: String, date: LocalDate) =
        RoutineLogEntity(routineId, Dates.iso(date), doneAt = 0L)

    @Test
    fun `scheduledOn respects the weekday mask`() {
        // Only Sundays (bit 7).
        val sundayOnly = routine("a", daysMask = 1 shl 6)
        assertTrue(StatsEngine.scheduledOn(today, listOf(sundayOnly)).isNotEmpty())
        assertTrue(StatsEngine.scheduledOn(today.minusDays(1), listOf(sundayOnly)).isEmpty())
    }

    @Test
    fun `scheduledOn skips archived routines`() {
        val archived = routine("a", archived = true)
        assertTrue(StatsEngine.scheduledOn(today, listOf(archived)).isEmpty())
    }

    @Test
    fun `scheduledOn skips routines created after the day ends`() {
        val tomorrow = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli() + 60_000L
        val future = routine("a", createdAt = tomorrow)
        assertTrue(StatsEngine.scheduledOn(today, listOf(future)).isEmpty())
    }

    @Test
    fun `a fully completed past day builds a streak`() {
        val routines = listOf(routine("a"), routine("b"))
        val logs = listOf(
            log("a", today.minusDays(1)), log("b", today.minusDays(1)),
            log("a", today.minusDays(2)), log("b", today.minusDays(2)),
        )
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        assertEquals(2, insights.currentStreak)
    }

    @Test
    fun `an unfinished today does not break yesterday's streak`() {
        val routines = listOf(routine("a"), routine("b"))
        val logs = listOf(
            log("a", today.minusDays(1)), log("b", today.minusDays(1)),
        )
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        // Today has nothing logged yet, but the grace rule keeps the streak at 1.
        assertTrue(insights.currentStreak >= 1)
    }

    @Test
    fun `a day below the threshold breaks the streak`() {
        // Ten routines, only one done: far below STREAK_THRESHOLD.
        val routines = (1..10).map { routine("r$it") }
        val logs = listOf(log("r1", today.minusDays(1)))
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        assertEquals(0, insights.currentStreak)
    }

    @Test
    fun `bestStreak finds the longest historical run`() {
        val routines = listOf(routine("a"))
        // Three consecutive perfect days, a gap, then one more.
        val logs = listOf(
            log("a", today.minusDays(1)),
            log("a", today.minusDays(2)),
            log("a", today.minusDays(3)),
            log("a", today.minusDays(6)),
        )
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        assertEquals(3, insights.bestStreak)
    }

    @Test
    fun `perfect day counting only includes days with scheduled work`() {
        val routines = listOf(routine("a"))
        val logs = listOf(
            log("a", today.minusDays(1)),
            log("a", today.minusDays(2)),
        )
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        assertEquals(2, insights.perfectDays)
    }

    @Test
    fun `totalCompletions counts every routine log`() {
        val routines = listOf(routine("a"), routine("b"))
        val logs = listOf(
            log("a", today.minusDays(1)),
            log("b", today.minusDays(1)),
            log("a", today.minusDays(2)),
        )
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        assertEquals(3, insights.totalCompletions)
    }

    @Test
    fun `empty data yields a zeroed but non-null report`() {
        val insights = StatsEngine.compute(emptyList(), emptyList(), emptyList(), today)
        assertEquals(0, insights.currentStreak)
        assertEquals(0, insights.bestStreak)
        assertEquals(0, insights.totalCompletions)
        assertEquals(0f, insights.todayRatio, 0.0001f)
        assertFalse(insights.days.isEmpty())
        assertEquals(StatsEngine.HISTORY_DAYS + 1, insights.days.size)
    }

    @Test
    fun `weekday highs and lows are reported when there is enough data`() {
        val routines = listOf(routine("a"))
        // Fill several Sundays but skip the rest of the week entirely.
        val logs = (1..4).map { log("a", today.minusDays(7L * it)) }
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        val sunday = 7
        assertEquals(sunday, insights.bestWeekday)
    }

    @Test
    fun `projected streak is never negative`() {
        val routines = listOf(routine("a"))
        val insights = StatsEngine.compute(routines, emptyList(), emptyList(), today)
        assertTrue(insights.projectedStreak >= 0)
    }

    @Test
    fun `focus minutes default to zero and are preserved`() {
        val base = StatsEngine.compute(listOf(routine("a")), emptyList(), emptyList(), today)
        assertEquals(0, base.focusMinutes)
        val withFocus = base.copy(focusMinutes = 90)
        assertEquals(90, withFocus.focusMinutes)
    }

    @Test
    fun `task logs are attributed to the right day`() {
        val routines = listOf(routine("a"))
        val taskLogs = listOf(
            TaskLogEntity("t1", Dates.iso(today.minusDays(1)), 0L),
            TaskLogEntity("t2", Dates.iso(today.minusDays(1)), 0L),
        )
        val insights = StatsEngine.compute(routines, emptyList(), taskLogs, today)
        val yesterday = insights.days.first { it.date == today.minusDays(1) }
        assertEquals(2, yesterday.taskCompleted)
    }

    @Test
    fun `weekly consistency stays within 0 and 100`() {
        val routines = listOf(routine("a"))
        val logs = (0..10).map { log("a", today.minusDays(it.toLong())) }
        val insights = StatsEngine.compute(routines, logs, emptyList(), today)
        assertTrue(insights.weeklyConsistency in 0..100)
        assertNotNull(insights.days)
    }
}
