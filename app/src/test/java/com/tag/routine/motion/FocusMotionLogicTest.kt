package com.tag.routine.motion

import com.tag.routine.ui.motion.FocusMotionLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusMotionLogicTest {

    @Test
    fun `warmthFraction clamps inputs and follows smoothstep curve`() {
        assertEquals(0f, FocusMotionLogic.warmthFraction(-0.4f), 0.0001f)
        assertEquals(0f, FocusMotionLogic.warmthFraction(0f), 0.0001f)
        assertEquals(0.5f, FocusMotionLogic.warmthFraction(0.5f), 0.0001f)
        assertEquals(1f, FocusMotionLogic.warmthFraction(1f), 0.0001f)
        assertEquals(1f, FocusMotionLogic.warmthFraction(1.5f), 0.0001f)

        val early = FocusMotionLogic.warmthFraction(0.2f)
        val late = FocusMotionLogic.warmthFraction(0.8f)
        assertTrue(early in 0f..0.2f)
        assertTrue(late in 0.8f..1f)
        assertTrue(early < late)
    }

    @Test
    fun `isLastMinute requires active running timer within final 60 seconds`() {
        assertFalse(FocusMotionLogic.isLastMinute(remainingSeconds = 30, running = false, totalSeconds = 600))
        assertFalse(FocusMotionLogic.isLastMinute(remainingSeconds = 30, running = true, totalSeconds = 0))
        assertFalse(FocusMotionLogic.isLastMinute(remainingSeconds = 0, running = true, totalSeconds = 600))
        assertFalse(FocusMotionLogic.isLastMinute(remainingSeconds = 61, running = true, totalSeconds = 600))

        assertTrue(FocusMotionLogic.isLastMinute(remainingSeconds = 60, running = true, totalSeconds = 600))
        assertTrue(FocusMotionLogic.isLastMinute(remainingSeconds = 1, running = true, totalSeconds = 600))
    }

    @Test
    fun `calm mode is allowed only while an active focus session exists`() {
        assertTrue(FocusMotionLogic.shouldAllowCalmMode(hasActiveSession = true))
        assertFalse(FocusMotionLogic.shouldAllowCalmMode(hasActiveSession = false))

        assertTrue(FocusMotionLogic.resolveCalmMode(requestedCalm = true, hasActiveSession = true))
        assertFalse(FocusMotionLogic.resolveCalmMode(requestedCalm = false, hasActiveSession = true))
        assertFalse(FocusMotionLogic.resolveCalmMode(requestedCalm = true, hasActiveSession = false))
        assertFalse(FocusMotionLogic.resolveCalmMode(requestedCalm = false, hasActiveSession = false))
    }

    @Test
    fun `detectCelebrationTimestamp fires strictly once for a new completed run`() {
        // Cold start: baseline already equals latest -> no celebration
        assertNull(
            FocusMotionLogic.detectCelebrationTimestamp(
                hadActiveSession = true,
                baselineCompletedEndedAt = 1000L,
                latestCompletedEndedAt = 1000L,
                lastCelebratedEndedAt = null,
            ),
        )

        // Abandoned or no active session -> no celebration
        assertNull(
            FocusMotionLogic.detectCelebrationTimestamp(
                hadActiveSession = false,
                baselineCompletedEndedAt = 1000L,
                latestCompletedEndedAt = 2000L,
                lastCelebratedEndedAt = null,
            ),
        )

        // Null latest completion -> no celebration
        assertNull(
            FocusMotionLogic.detectCelebrationTimestamp(
                hadActiveSession = true,
                baselineCompletedEndedAt = 1000L,
                latestCompletedEndedAt = null,
                lastCelebratedEndedAt = null,
            ),
        )

        // Fresh completion -> emits endedAt once
        assertEquals(
            2000L,
            FocusMotionLogic.detectCelebrationTimestamp(
                hadActiveSession = true,
                baselineCompletedEndedAt = 1000L,
                latestCompletedEndedAt = 2000L,
                lastCelebratedEndedAt = null,
            ),
        )

        // Already celebrated -> suppressed
        assertNull(
            FocusMotionLogic.detectCelebrationTimestamp(
                hadActiveSession = true,
                baselineCompletedEndedAt = 1000L,
                latestCompletedEndedAt = 2000L,
                lastCelebratedEndedAt = 2000L,
            ),
        )
    }
}
