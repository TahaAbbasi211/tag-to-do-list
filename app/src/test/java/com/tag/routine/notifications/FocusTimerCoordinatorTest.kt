package com.tag.routine.notifications

import com.tag.routine.data.ActiveFocusRun
import com.tag.routine.data.FocusBreakSettings
import com.tag.routine.data.FocusPhase
import com.tag.routine.data.FocusRunStorage
import com.tag.routine.data.FocusSessionEntity
import com.tag.routine.data.FocusTimerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerCoordinatorTest {

    private class InMemoryFocusStorage(
        private val recordDelayMs: Long = 0L,
    ) : FocusRunStorage {
        var activeRun: ActiveFocusRun? = null
        var breakSettings = FocusBreakSettings()
        val sessions = linkedMapOf<String, FocusSessionEntity>()
        var recordCallCount = 0

        override suspend fun currentActiveFocusRun(): ActiveFocusRun? = activeRun

        override suspend fun setActiveFocusRun(run: ActiveFocusRun) {
            activeRun = run
        }

        override suspend fun clearActiveFocusRun() {
            activeRun = null
        }

        override suspend fun focusBreakSettings(): FocusBreakSettings = breakSettings

        override suspend fun recordFocusSession(
            routineId: String?,
            startedAt: Long,
            endedAt: Long,
            plannedMinutes: Int,
            completed: Boolean,
            id: String,
        ) {
            if (recordDelayMs > 0L) delay(recordDelayMs)
            recordCallCount++
            sessions[id] = FocusSessionEntity(
                id = id,
                routineId = routineId,
                startedAt = startedAt,
                endedAt = endedAt,
                plannedMinutes = plannedMinutes,
                completed = completed,
            )
        }
    }

    private val t0 = 1_700_000_000_000L

    @Test
    fun `switching from active routine settles prior run, runs alarm cancel hook, and starts new run`() = runBlocking {
        val storage = InMemoryFocusStorage()
        val coordinator = FocusTimerCoordinator { storage }

        val first = coordinator.start(
            routineId = "r1",
            routineTitle = "Morning walk",
            minutes = 25,
            now = t0,
        )
        assertEquals(first, storage.activeRun)

        var hookCalledBeforeSettle = false
        val switchTime = t0 + 5 * 60_000L
        val second = coordinator.switchTo(
            routineId = "r2",
            routineTitle = "Deep study",
            minutes = 45,
            now = switchTime,
            onBeforeSettle = {
                // Hook runs before clearing/starting so prior completion alarm is cancelled cleanly.
                assertTrue(storage.sessions.isEmpty())
                hookCalledBeforeSettle = true
            },
        )

        assertTrue(hookCalledBeforeSettle)
        assertNotEquals(first.id, second.id)
        assertEquals("r2", second.routineId)
        assertEquals("Deep study", second.routineTitle)
        assertEquals(45, second.plannedMinutes)
        assertEquals(second, coordinator.run.value)
        assertEquals(second, storage.activeRun)

        // Prior run was recorded with its real 5-minute elapsed duration as abandoned.
        assertEquals(1, storage.sessions.size)
        val recorded = requireNotNull(storage.sessions[first.id])
        assertEquals("r1", recorded.routineId)
        assertEquals(t0, recorded.startedAt)
        assertEquals(t0 + 5 * 60_000L, recorded.endedAt)
        assertFalse(recorded.completed)
    }

    @Test
    fun `switching from short run under 60s clears prior run without recording bogus session`() = runBlocking {
        val storage = InMemoryFocusStorage()
        val coordinator = FocusTimerCoordinator { storage }

        coordinator.start(routineId = "r1", routineTitle = "Stretch", minutes = 15, now = t0)
        val next = coordinator.switchTo(
            routineId = "r2",
            routineTitle = "Read",
            minutes = 30,
            now = t0 + 30_000L,
        )

        assertEquals("r2", next.routineId)
        assertTrue(storage.sessions.isEmpty())
        assertEquals(next, storage.activeRun)
    }

    @Test
    fun `switching from paused or general focus run preserves banked time and routineId`() = runBlocking {
        val storage = InMemoryFocusStorage()
        val coordinator = FocusTimerCoordinator { storage }

        val general = coordinator.start(
            routineId = null,
            routineTitle = null,
            minutes = 25,
            now = t0,
        )
        coordinator.pause(now = t0 + 4 * 60_000L)

        // Switching 20 minutes later must only credit the 4 banked minutes.
        val next = coordinator.switchTo(
            routineId = "r9",
            routineTitle = "Code review",
            minutes = 25,
            now = t0 + 24 * 60_000L,
        )

        assertEquals("r9", next.routineId)
        assertEquals(1, storage.sessions.size)
        val recorded = requireNotNull(storage.sessions[general.id])
        assertNull(recorded.routineId)
        assertEquals(4 * 60_000L, recorded.endedAt - recorded.startedAt)
        assertFalse(recorded.completed)
    }

    @Test
    fun `direct start while run is active settles prior run instead of losing it`() = runBlocking {
        val storage = InMemoryFocusStorage()
        val coordinator = FocusTimerCoordinator { storage }

        val first = coordinator.start("r1", "Walk", 20, now = t0)
        val second = coordinator.start("r2", "Read", 10, now = t0 + 3 * 60_000L)

        assertEquals("r2", second.routineId)
        assertEquals(1, storage.sessions.size)
        assertEquals("r1", storage.sessions[first.id]?.routineId)
    }

    @Test
    fun `concurrent advance and switchTo serialize under settleLock without double recording`() = runBlocking {
        val storage = InMemoryFocusStorage(recordDelayMs = 25L)
        val coordinator = FocusTimerCoordinator { storage }

        val first = coordinator.start("r1", "Workout", 10, now = t0)
        val completionTime = t0 + 10 * 60_000L

        val advanceJob = async(Dispatchers.Default) {
            coordinator.advance(now = completionTime)
        }
        val switchJob = async(Dispatchers.Default) {
            coordinator.switchTo("r2", "Meditate", 15, now = completionTime)
        }

        advanceJob.await()
        val next = switchJob.await()

        assertEquals(1, storage.recordCallCount)
        assertEquals(1, storage.sessions.size)
        val recorded = requireNotNull(storage.sessions[first.id])
        assertEquals("r1", recorded.routineId)
        assertTrue(recorded.completed)
        assertEquals(10 * 60_000L, recorded.endedAt - recorded.startedAt)
        assertEquals(next, coordinator.run.value)
        assertEquals("r2", storage.activeRun?.routineId)
    }

    @Test
    fun `persisted run on disk without in-memory state is still settled on switch`() = runBlocking {
        val storage = InMemoryFocusStorage()
        val diskRun = FocusTimerEngine.start(
            id = "disk-run-1",
            routineId = "r-disk",
            routineTitle = "Disk Routine",
            minutes = 20,
            now = t0,
        )
        storage.activeRun = diskRun

        val coordinator = FocusTimerCoordinator { storage }
        val next = coordinator.switchTo("r2", "Fresh", 15, now = t0 + 6 * 60_000L)

        assertEquals("r2", next.routineId)
        assertEquals(1, storage.sessions.size)
        assertEquals("r-disk", storage.sessions["disk-run-1"]?.routineId)
    }

    @Test
    fun `break mode off records the focus and clears the run as before`() = runBlocking {
        val storage = InMemoryFocusStorage()
        val coordinator = FocusTimerCoordinator { storage }
        coordinator.start("r1", "Focus", 1, now = t0)

        val transition = coordinator.advanceWithTransition(now = t0 + 60_000L)

        assertTrue(transition.completed)
        assertEquals(FocusPhase.FOCUS, transition.previousPhase)
        assertTrue(transition.recordedFocus)
        assertNull(transition.next)
        assertNull(coordinator.run.value)
        assertEquals(1, storage.sessions.size)
        assertTrue(requireNotNull(storage.sessions.values.first()).completed)
    }

    @Test
    fun `break mode on records only the focus and starts a short break`() = runBlocking {
        val storage = InMemoryFocusStorage().apply {
            breakSettings = FocusBreakSettings(enabled = true, shortBreakMinutes = 5)
        }
        val coordinator = FocusTimerCoordinator { storage }
        coordinator.start("r1", "Focus", 1, now = t0)

        val transition = coordinator.advanceWithTransition(now = t0 + 60_000L)
        val next = requireNotNull(transition.next)

        assertEquals(FocusPhase.SHORT_BREAK, next.phase)
        assertTrue(next.running)
        assertEquals(1, storage.sessions.size)
        assertEquals(next, storage.activeRun)
        assertEquals(next, coordinator.run.value)
    }

    @Test
    fun `finishing a break yields a paused focus run and adds no history row`() = runBlocking {
        val storage = InMemoryFocusStorage().apply {
            breakSettings = FocusBreakSettings(enabled = true, shortBreakMinutes = 5)
        }
        val coordinator = FocusTimerCoordinator { storage }
        coordinator.start("r1", "Focus", 1, now = t0)
        coordinator.advanceWithTransition(now = t0 + 60_000L)

        val transition = coordinator.advanceWithTransition(now = t0 + 6 * 60_000L)
        val next = requireNotNull(transition.next)

        assertEquals(FocusPhase.FOCUS, next.phase)
        assertEquals(2, next.cycleCount)
        assertFalse(next.running)
        assertEquals(1, storage.sessions.size)
        assertEquals(next, storage.activeRun)
    }

    @Test
    fun `skipping a break advances the cycle without recording break time`() = runBlocking {
        val storage = InMemoryFocusStorage().apply {
            breakSettings = FocusBreakSettings(enabled = true)
        }
        val coordinator = FocusTimerCoordinator { storage }
        coordinator.start("r1", "Focus", 1, now = t0)
        coordinator.advanceWithTransition(now = t0 + 60_000L)

        assertTrue(coordinator.skipBreak(now = t0 + 2 * 60_000L))
        val next = requireNotNull(coordinator.run.value)

        assertEquals(FocusPhase.FOCUS, next.phase)
        assertEquals(2, next.cycleCount)
        assertFalse(next.running)
        assertEquals(1, storage.sessions.size)
        assertEquals(next, storage.activeRun)
    }

    @Test
    fun `stopping during a break discards it without recording`() = runBlocking {
        val storage = InMemoryFocusStorage().apply {
            breakSettings = FocusBreakSettings(enabled = true)
        }
        val coordinator = FocusTimerCoordinator { storage }
        coordinator.start("r1", "Focus", 1, now = t0)
        coordinator.advanceWithTransition(now = t0 + 60_000L)

        coordinator.finish(now = t0 + 2 * 60_000L)

        assertNull(coordinator.run.value)
        assertNull(storage.activeRun)
        assertEquals(1, storage.sessions.size)
    }
}
