package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the arithmetic that keeps the focus timer honest while the app is closed.
 *
 * Every case here is a failure mode the shipped build actually had: a timer that reset
 * when the app was closed, a paused run that kept counting, and a session recorded with
 * the wrong duration because the process had been frozen for hours.
 */
class FocusTimerEngineTest {

    private val t0 = 1_700_000_000_000L

    private fun run(
        minutes: Int = 25,
        segmentStartedAt: Long? = t0,
        accumulatedSeconds: Int = 0,
        startedAt: Long = t0,
        routineId: String? = "r1",
        routineTitle: String? = "Reading",
    ) = ActiveFocusRun(
        id = "run-1",
        routineId = routineId,
        routineTitle = routineTitle,
        plannedMinutes = minutes,
        segmentStartedAt = segmentStartedAt,
        accumulatedSeconds = accumulatedSeconds,
        startedAt = startedAt,
    )

    // ------------------------------------------------------------ elapsed time

    @Test
    fun `a wall-clock jump is respected rather than counted tick by tick`() {
        // The process was frozen for ten minutes; the timer must still be correct.
        val active = run(minutes = 25)
        assertEquals(600, active.elapsedSeconds(t0 + 10 * 60_000L))
        assertEquals(900, active.remainingSeconds(t0 + 10 * 60_000L))
    }

    @Test
    fun `a backwards clock never produces negative elapsed time`() {
        val active = run(minutes = 25)
        assertEquals(0, active.elapsedSeconds(t0 - 5 * 60_000L))
        assertEquals(1500, active.remainingSeconds(t0 - 5 * 60_000L))
    }

    @Test
    fun `remaining time stops at zero once the planned length passes`() {
        val active = run(minutes = 10)
        assertEquals(0, active.remainingSeconds(t0 + 60 * 60_000L))
        assertTrue(active.isComplete(t0 + 10 * 60_000L))
        assertFalse(active.isComplete(t0 + 9 * 60_000L))
    }

    // ------------------------------------------------------------ pause / resume

    @Test
    fun `a paused run keeps its banked time and stops counting`() {
        val active = run(minutes = 25)
        val paused = FocusTimerEngine.pause(active, now = t0 + 5 * 60_000L)

        assertFalse(paused.running)
        assertEquals(300, paused.accumulatedSeconds)
        assertNull(paused.segmentStartedAt)
        // Time passing while paused must not advance the timer.
        assertEquals(300, paused.elapsedSeconds(t0 + 30 * 60_000L))
    }

    @Test
    fun `resuming continues from the banked total`() {
        val paused = FocusTimerEngine.pause(run(minutes = 25), now = t0 + 5 * 60_000L)
        val resumedAt = t0 + 30 * 60_000L
        val resumed = FocusTimerEngine.resume(paused, now = resumedAt)

        assertTrue(resumed.running)
        assertEquals(resumedAt, resumed.segmentStartedAt)
        assertEquals(300, resumed.elapsedSeconds(resumedAt))
        assertEquals(600, resumed.elapsedSeconds(resumedAt + 5 * 60_000L))
    }

    @Test
    fun `pausing twice or resuming twice is a no-op`() {
        val paused = FocusTimerEngine.pause(run(), now = t0 + 60_000L)
        assertEquals(paused, FocusTimerEngine.pause(paused, now = t0 + 120_000L))

        val resumed = FocusTimerEngine.resume(paused, now = t0 + 120_000L)
        assertEquals(resumed, FocusTimerEngine.resume(resumed, now = t0 + 180_000L))
    }

    @Test
    fun `pause never banks more than the planned length`() {
        val paused = FocusTimerEngine.pause(run(minutes = 5), now = t0 + 90 * 60_000L)
        assertEquals(300, paused.accumulatedSeconds)
    }

    // ------------------------------------------------------------ completion

    @Test
    fun `tick reports completion exactly at the planned length`() {
        val active = run(minutes = 5)
        assertFalse(FocusTimerEngine.tick(active, t0 + 299_000L).completed)

        val ticked = FocusTimerEngine.tick(active, t0 + 300_000L)
        assertTrue(ticked.completed)
        assertEquals(300, ticked.run.accumulatedSeconds)
        assertFalse(ticked.run.running)
    }

    @Test
    fun `a run discovered long after it finished is clamped to its planned length`() {
        // The app was closed for two hours; the session must report 25 minutes, not 120.
        val ticked = FocusTimerEngine.tick(run(minutes = 25), t0 + 120 * 60_000L)
        assertEquals(25 * 60, ticked.run.accumulatedSeconds)
        assertEquals(1500, ticked.run.elapsedSeconds(t0 + 120 * 60_000L))
    }

    @Test
    fun `a paused run never completes on a tick`() {
        val paused = FocusTimerEngine.pause(run(minutes = 5), now = t0 + 60_000L)
        assertFalse(FocusTimerEngine.tick(paused, t0 + 10 * 60_000L).completed)
    }

    // ------------------------------------------------------------ recording

    @Test
    fun `a completed run is recorded as completed with its planned duration`() {
        val active = run(minutes = 25)
        val session = FocusTimerEngine.toSession(active, id = "run-1", now = t0 + 25 * 60_000L)

        requireNotNull(session)
        assertEquals("run-1", session.id)
        assertEquals(t0, session.startedAt)
        assertEquals(t0 + 25 * 60_000L, session.endedAt)
        assertEquals(25, session.plannedMinutes)
        assertTrue(session.completed)
        // The stats engine measures minutes as endedAt - startedAt.
        assertEquals(25, ((session.endedAt - session.startedAt) / 60_000L).toInt())
    }

    @Test
    fun `a run settled hours late is not recorded as hours of focus`() {
        val active = run(minutes = 25)
        val session = FocusTimerEngine.toSession(active, id = "run-1", now = t0 + 10 * 3_600_000L)

        requireNotNull(session)
        // 25 minutes of focus, even though it was only written down 10 hours later.
        assertEquals(25, ((session.endedAt - session.startedAt) / 60_000L).toInt())
        assertTrue(session.completed)
    }

    @Test
    fun `an abandoned run is recorded with its real shorter length`() {
        val active = run(minutes = 25)
        val session = FocusTimerEngine.toSession(active, id = "run-1", now = t0 + 5 * 60_000L)

        requireNotNull(session)
        assertFalse(session.completed)
        assertEquals(5, ((session.endedAt - session.startedAt) / 60_000L).toInt())
    }

    @Test
    fun `a run under the minimum length is not recorded`() {
        val active = run(minutes = 25)
        val session = FocusTimerEngine.toSession(
            active,
            id = "run-1",
            now = t0 + (AppConstants.MIN_FOCUS_SECONDS - 1) * 1000L,
        )
        assertNull(session)
    }

    @Test
    fun `a paused run is recorded from its banked time`() {
        val paused = FocusTimerEngine.pause(run(minutes = 25), now = t0 + 10 * 60_000L)
        val session = FocusTimerEngine.toSession(paused, id = "run-1", now = t0 + 90 * 60_000L)

        requireNotNull(session)
        assertEquals(10, ((session.endedAt - session.startedAt) / 60_000L).toInt())
    }

    // ------------------------------------------------------------ start

    @Test
    fun `starting clamps the duration and stores the routine`() {
        val started = FocusTimerEngine.start(
            id = "run-9",
            routineId = "r7",
            routineTitle = "Deep work",
            minutes = 10_000,
            now = t0,
        )
        assertEquals(AppConstants.MAX_FOCUS_MINUTES, started.plannedMinutes)
        assertEquals("r7", started.routineId)
        assertEquals("Deep work", started.routineTitle)
        assertEquals(t0, started.segmentStartedAt)
        assertEquals(t0, started.startedAt)
        assertEquals(0, started.accumulatedSeconds)
        assertTrue(started.running)
    }

    // ------------------------------------------------------------ display

    @Test
    fun `clock formatting covers minutes and hours`() {
        assertEquals("00:00", FocusTimerEngine.formatClock(0))
        assertEquals("05:09", FocusTimerEngine.formatClock(309))
        assertEquals("59:59", FocusTimerEngine.formatClock(3599))
        assertEquals("1:00:00", FocusTimerEngine.formatClock(3600))
        assertEquals("2:03:04", FocusTimerEngine.formatClock(7384))
        assertEquals("00:00", FocusTimerEngine.formatClock(-5))
    }

    // ------------------------------------------------------------ surviving the app closing

    /**
     * The reported bug, encoded as a test: the user starts a timer, closes the app, and
     * comes back later. The run must still be there and must have kept counting, instead
     * of resetting to zero.
     */
    @Test
    fun `a run survives the app closing and keeps counting`() {
        val active = run(minutes = 25)

        // The app is closed: everything in memory is gone, only the encoded run remains.
        val stored = ActiveFocusRunCodec.encode(active)

        // Ten minutes later the app is reopened and the run is read back from disk.
        val reopened = requireNotNull(ActiveFocusRunCodec.decode(stored))
        val tenMinutesLater = t0 + 10 * 60_000L

        assertEquals(10 * 60, reopened.elapsedSeconds(tenMinutesLater))
        assertEquals(15 * 60, reopened.remainingSeconds(tenMinutesLater))
        assertTrue(reopened.running)
        assertFalse(reopened.isComplete(tenMinutesLater))
    }

    @Test
    fun `a timer that runs out while the app is closed is recorded as completed`() {
        val stored = ActiveFocusRunCodec.encode(run(minutes = 25))

        // The user comes back two hours later; the run finished long ago.
        val reopened = requireNotNull(ActiveFocusRunCodec.decode(stored))
        val twoHoursLater = t0 + 120 * 60_000L

        val ticked = FocusTimerEngine.tick(reopened, twoHoursLater)
        assertTrue(ticked.completed)

        val session = requireNotNull(FocusTimerEngine.toSession(reopened, id = "run-1", now = twoHoursLater))
        // Exactly the planned 25 minutes, dated from when it was started.
        assertEquals(25 * 60_000L, session.endedAt - session.startedAt)
        assertEquals(t0, session.startedAt)
        assertTrue(session.completed)
    }

    @Test
    fun `a paused run stays paused across the app closing`() {
        val paused = FocusTimerEngine.pause(run(minutes = 25), now = t0 + 5 * 60_000L)
        val reopened = requireNotNull(ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(paused)))

        // Time passing while the app is closed must not resume a paused timer.
        assertFalse(reopened.running)
        assertEquals(5 * 60, reopened.elapsedSeconds(t0 + 60 * 60_000L))
        assertFalse(FocusTimerEngine.tick(reopened, t0 + 60 * 60_000L).completed)
    }

    // ------------------------------------------------------------ codec

    @Test
    fun `a run survives an encode and decode round trip`() {
        val original = run(minutes = 45, accumulatedSeconds = 120, segmentStartedAt = null)
        val decoded = ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `a routine title containing the field separator survives the round trip`() {
        // A title is free text, so it must never be able to corrupt the stored record.
        val original = run(routineTitle = "Read\tthe\tbook")
        val decoded = ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(original))

        assertEquals("Read\tthe\tbook", decoded?.routineTitle)
        assertEquals(original, decoded)
    }

    @Test
    fun `non-ascii titles survive the round trip`() {
        val original = run(routineTitle = "مطالعه")
        val decoded = ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(original))
        assertEquals("مطالعه", decoded?.routineTitle)
    }

    @Test
    fun `a missing run decodes to null`() {
        assertNull(ActiveFocusRunCodec.decode(null))
        assertNull(ActiveFocusRunCodec.decode(""))
        assertNull(ActiveFocusRunCodec.decode("   "))
    }

    @Test
    fun `a corrupt or unknown record decodes to null instead of throwing`() {
        assertNull(ActiveFocusRunCodec.decode("garbage"))
        assertNull(ActiveFocusRunCodec.decode("2\ta\tb\tc\t25\t0\t0\t0"))
        assertNull(ActiveFocusRunCodec.decode("1\t\tnull\tnull\t25\t0\t0\t0"))
        assertNull(ActiveFocusRunCodec.decode("1\tid\tnull\tnull\tNaN\t0\t0\t0"))
    }

    @Test
    fun `a decoded run keeps its paused flag`() {
        val paused = run(segmentStartedAt = null, accumulatedSeconds = 300)
        val decoded = ActiveFocusRunCodec.decode(ActiveFocusRunCodec.encode(paused))

        assertFalse(requireNotNull(decoded).running)
        assertEquals(300, decoded.accumulatedSeconds)
    }
}
