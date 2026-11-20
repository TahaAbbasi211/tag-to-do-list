package com.tag.routine.widget

import com.tag.routine.data.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A failed snapshot read must leave the widget exactly as it was: nothing is pushed, so
 * the launcher keeps the previous RemoteViews. Only a snapshot that was really read (even
 * an empty one) may redraw the widget, which is what makes the "nothing scheduled" state
 * honest.
 */
class WidgetUpdaterTest {

    private val targets = intArrayOf(7, 9)

    /** Simulates the launcher: each id holds the last content pushed to it. */
    private class FakeHost {
        val shown = mutableMapOf(7 to "previous", 9 to "previous")
        var pushes = 0
        suspend fun push(id: Int, snapshot: Snapshot) {
            pushes++
            shown[id] = if (snapshot.routines.isEmpty()) "empty" else "items:${snapshot.routines.size}"
        }
    }

    private fun run(result: WidgetSnapshotLoader.Result, host: FakeHost): Boolean = runBlocking {
        WidgetUpdater.updateWith(targets, result) { id, snap -> host.push(id, snap) }
    }

    @Test
    fun containerNotReady_skipsAndNeverReads() = runBlocking {
        var reads = 0
        val result = WidgetSnapshotLoader.load(containerReady = false) { reads++; Snapshot() }
        assertTrue(result is WidgetSnapshotLoader.Result.Skipped)
        assertEquals(0, reads)

        val host = FakeHost()
        assertFalse(run(result, host))
        assertEquals(0, host.pushes)
        assertEquals(mapOf(7 to "previous", 9 to "previous"), host.shown)
    }

    @Test
    fun nullSnapshot_keepsPreviousContent() = runBlocking {
        val result = WidgetSnapshotLoader.load(containerReady = true) { null }
        assertTrue(result is WidgetSnapshotLoader.Result.Skipped)

        val host = FakeHost()
        assertFalse(run(result, host))
        assertEquals(0, host.pushes)
        assertEquals(mapOf(7 to "previous", 9 to "previous"), host.shown)
    }

    @Test
    fun throwingRead_keepsPreviousContent() = runBlocking {
        val boom = IllegalStateException("db not open")
        val result = WidgetSnapshotLoader.load(containerReady = true) { throw boom }
        assertTrue(result is WidgetSnapshotLoader.Result.Skipped)
        assertEquals(boom, (result as WidgetSnapshotLoader.Result.Skipped).error)

        val host = FakeHost()
        assertFalse(run(result, host))
        assertEquals(0, host.pushes)
        assertEquals(mapOf(7 to "previous", 9 to "previous"), host.shown)
    }

    @Test
    fun realEmptySnapshot_drawsEmptyState() = runBlocking {
        val result = WidgetSnapshotLoader.load(containerReady = true) { Snapshot(loaded = true) }
        assertTrue(result is WidgetSnapshotLoader.Result.Loaded)

        val host = FakeHost()
        assertTrue(run(result, host))
        assertEquals(2, host.pushes)
        assertEquals(mapOf(7 to "empty", 9 to "empty"), host.shown)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun cancellation_isNotSwallowed(): Unit = runBlocking {
        WidgetSnapshotLoader.load(containerReady = true) {
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        Unit
    }

    @Test
    fun skippedThenSucceeds_retriesOnceAndUpdatesWidget() = runBlocking {
        var attempts = 0
        var pauses = 0
        val host = FakeHost()
        val updated = WidgetUpdater.updateWithRetry(
            targets = targets,
            load = {
                attempts++
                if (attempts == 1) {
                    WidgetSnapshotLoader.Result.Skipped("container not ready")
                } else {
                    WidgetSnapshotLoader.Result.Loaded(Snapshot(loaded = true))
                }
            },
            push = { id, snap -> host.push(id, snap) },
            pause = { pauses++ },
        )
        assertTrue(updated)
        assertEquals(2, attempts)
        assertEquals(1, pauses)
        assertEquals(2, host.pushes)
        assertEquals(mapOf(7 to "empty", 9 to "empty"), host.shown)
    }

    @Test
    fun skippedTwice_stopsAfterOneRetryAndKeepsPreviousContent() = runBlocking {
        var attempts = 0
        var pauses = 0
        val host = FakeHost()
        val updated = WidgetUpdater.updateWithRetry(
            targets = targets,
            load = {
                attempts++
                WidgetSnapshotLoader.Result.Skipped("still not ready")
            },
            push = { id, snap -> host.push(id, snap) },
            pause = { pauses++ },
        )
        assertFalse(updated)
        assertEquals(2, attempts)
        assertEquals(1, pauses)
        assertEquals(0, host.pushes)
        assertEquals(mapOf(7 to "previous", 9 to "previous"), host.shown)
    }
}
