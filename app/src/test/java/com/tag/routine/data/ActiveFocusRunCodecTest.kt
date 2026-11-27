package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveFocusRunCodecTest {

    private val t0 = 1_700_000_000_000L

    private fun run(phase: FocusPhase = FocusPhase.FOCUS) = ActiveFocusRun(
        id = "run-1",
        routineId = "routine-1",
        routineTitle = "Deep work",
        plannedMinutes = if (phase.isBreak) 5 else 25,
        segmentStartedAt = t0,
        accumulatedSeconds = 12,
        startedAt = t0 - 1_000L,
        phase = phase,
        cycleCount = 3,
        focusMinutes = 25,
    )

    @Test
    fun `legacy eight-field run decodes as a focus run`() {
        val legacy = listOf(
            "1",
            "legacy-run",
            "",
            "",
            "25",
            t0.toString(),
            "0",
            t0.toString(),
        ).joinToString("\t")

        val decoded = requireNotNull(ActiveFocusRunCodec.decode(legacy))
        assertEquals("legacy-run", decoded.id)
        assertEquals(FocusPhase.FOCUS, decoded.phase)
        assertEquals(1, decoded.cycleCount)
        assertEquals(25, decoded.focusMinutes)
        assertTrue(decoded.running)
    }

    @Test
    fun `phase-aware run round-trips phase cycle and focus length`() {
        val breakRun = run(FocusPhase.LONG_BREAK)
        val decoded = requireNotNull(ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(breakRun)))
        assertEquals(breakRun, decoded)
        assertEquals(FocusPhase.LONG_BREAK, decoded.phase)
        assertEquals(3, decoded.cycleCount)
        assertEquals(25, decoded.focusMinutes)
        assertEquals(5, decoded.plannedMinutes)
    }

    @Test
    fun `paused focus run round-trips as paused`() {
        val paused = run().copy(segmentStartedAt = null, accumulatedSeconds = 42)
        val decoded = requireNotNull(ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(paused)))
        assertEquals(paused, decoded)
        assertFalse(decoded.running)
    }

    @Test
    fun `unknown version and malformed records decode as absent`() {
        assertNull(ActiveFocusRunCodec.decode(""))
        assertNull(ActiveFocusRunCodec.decode("9\tbroken"))
        assertNull(ActiveFocusRunCodec.decode("1\ttoo\tfew"))
    }
}