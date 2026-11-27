package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusBreakEngineTest {

    private val t0 = 1_700_000_000_000L
    private val enabled = FocusBreakSettings(
        enabled = true,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        autoStartBreak = true,
    )

    private fun focus(cycle: Int = 1, minutes: Int = 25) = ActiveFocusRun(
        id = "focus-$cycle",
        routineId = "r1",
        routineTitle = "Deep work",
        plannedMinutes = minutes,
        segmentStartedAt = t0,
        accumulatedSeconds = 0,
        startedAt = t0,
        phase = FocusPhase.FOCUS,
        cycleCount = cycle,
        focusMinutes = minutes,
    )

    private fun breakRun(phase: FocusPhase, cycle: Int = 1) = ActiveFocusRun(
        id = "break-$cycle",
        routineId = "r1",
        routineTitle = "Deep work",
        plannedMinutes = if (phase == FocusPhase.LONG_BREAK) 15 else 5,
        segmentStartedAt = t0,
        accumulatedSeconds = 0,
        startedAt = t0,
        phase = phase,
        cycleCount = cycle,
        focusMinutes = 25,
    )

    @Test
    fun `break mode off leaves the legacy focus-only behavior untouched`() {
        val next = FocusBreakEngine.afterFocus(
            focus = focus(),
            settings = FocusBreakSettings(enabled = false),
            now = t0 + 25 * 60_000L,
        )
        assertNull(next)
    }

    @Test
    fun `first focus session is followed by a running short break`() {
        val next = requireNotNull(
            FocusBreakEngine.afterFocus(focus(cycle = 1), enabled, t0 + 25 * 60_000L),
        )
        assertEquals(FocusPhase.SHORT_BREAK, next.phase)
        assertEquals(5, next.plannedMinutes)
        assertEquals(1, next.cycleCount)
        assertEquals(25, next.focusMinutes)
        assertTrue(next.running)
        assertEquals(t0 + 25 * 60_000L, next.startedAt)
        assertNotEquals("focus-1", next.id)
    }

    @Test
    fun `session at the interval boundary gets a long break`() {
        val next = requireNotNull(
            FocusBreakEngine.afterFocus(focus(cycle = 4), enabled, t0 + 25 * 60_000L),
        )
        assertEquals(FocusPhase.LONG_BREAK, next.phase)
        assertEquals(15, next.plannedMinutes)
        assertEquals(4, next.cycleCount)
    }

    @Test
    fun `auto-start off leaves the break paused but ready`() {
        val next = requireNotNull(
            FocusBreakEngine.afterFocus(
                focus = focus(),
                settings = enabled.copy(autoStartBreak = false),
                now = t0 + 25 * 60_000L,
            ),
        )
        assertEquals(FocusPhase.SHORT_BREAK, next.phase)
        assertFalse(next.running)
        assertEquals(0, next.accumulatedSeconds)
    }

    @Test
    fun `short break advances the cycle and leaves focus paused`() {
        val next = FocusBreakEngine.nextFocus(
            breakRun = breakRun(FocusPhase.SHORT_BREAK, cycle = 1),
            settings = enabled,
            now = t0 + 30 * 60_000L,
        )
        assertEquals(FocusPhase.FOCUS, next.phase)
        assertEquals(25, next.plannedMinutes)
        assertEquals(2, next.cycleCount)
        assertEquals(25, next.focusMinutes)
        assertFalse(next.running)
        assertEquals(t0 + 30 * 60_000L, next.startedAt)
    }

    @Test
    fun `long break resets the cycle to one`() {
        val next = FocusBreakEngine.nextFocus(
            breakRun = breakRun(FocusPhase.LONG_BREAK, cycle = 4),
            settings = enabled,
            now = t0 + 40 * 60_000L,
        )
        assertEquals(FocusPhase.FOCUS, next.phase)
        assertEquals(1, next.cycleCount)
        assertFalse(next.running)
    }

    @Test
    fun `skip break produces the same paused focus run as a finished break`() {
        val skipped = requireNotNull(
            FocusBreakEngine.skipBreak(breakRun(FocusPhase.SHORT_BREAK, cycle = 2), enabled, t0),
        )
        assertEquals(FocusPhase.FOCUS, skipped.phase)
        assertEquals(3, skipped.cycleCount)
        assertFalse(skipped.running)
    }

    @Test
    fun `skip break is a no-op outside a break`() {
        assertNull(FocusBreakEngine.skipBreak(focus(), enabled, t0))
    }

    @Test
    fun `break phases are never converted to focus history`() {
        assertNull(
            FocusTimerEngine.toSession(
                breakRun(FocusPhase.SHORT_BREAK, cycle = 1),
                id = "break-1",
                now = t0 + 5 * 60_000L,
            ),
        )
    }
}