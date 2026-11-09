package com.tag.routine.data

/**
 * The decision half of the focus timer, kept free of Android types so it can be unit
 * tested directly.
 *
 * The timer deliberately computes elapsed time from wall-clock timestamps instead of
 * counting ticks. A `delay(1_000)` counter loses time whenever the process is frozen,
 * which is exactly what happens while the app sits in the background — the run would
 * finish late and, in the old implementation, reset to zero when the app was closed.
 * Here the clock is the only source of truth; [tick] merely re-derives elapsed time.
 */
object FocusTimerEngine {

    /** Fired when a run reaches its planned length. */
    data class Ticked(val run: ActiveFocusRun, val completed: Boolean)

    /** Starts a fresh run for [routineId] lasting [minutes], with the clock starting now. */
    fun start(
        id: String,
        routineId: String?,
        routineTitle: String?,
        minutes: Int,
        now: Long = System.currentTimeMillis(),
    ): ActiveFocusRun {
        val planned = AppConstants.clampFocusMinutes(minutes)
        return ActiveFocusRun(
            id = id,
            routineId = routineId,
            routineTitle = routineTitle,
            plannedMinutes = planned,
            segmentStartedAt = now,
            accumulatedSeconds = 0,
            startedAt = now,
        )
    }

    /** Banks the time elapsed so far and stops the clock. No-op when already paused. */
    fun pause(run: ActiveFocusRun, now: Long = System.currentTimeMillis()): ActiveFocusRun {
        if (!run.running) return run
        return run.copy(
            accumulatedSeconds = run.elapsedSeconds(now).coerceAtMost(run.totalSeconds),
            segmentStartedAt = null,
        )
    }

    /** Restarts the clock from the banked total. No-op when already running. */
    fun resume(run: ActiveFocusRun, now: Long = System.currentTimeMillis()): ActiveFocusRun {
        if (run.running) return run
        return run.copy(segmentStartedAt = now)
    }

    /**
     * Re-derives the run against the current clock.
     *
     * A running run that reached its planned length is clamped to exactly that length:
     * the overshoot is discarded so a session recorded hours later — because the process
     * was killed mid-run — still reports its planned duration instead of the wall-clock
     * gap.
     */
    fun tick(run: ActiveFocusRun, now: Long = System.currentTimeMillis()): Ticked {
        if (!run.running) return Ticked(run, completed = false)
        val elapsed = run.elapsedSeconds(now)
        if (elapsed < run.totalSeconds) return Ticked(run, completed = false)
        val finished = run.copy(
            accumulatedSeconds = run.totalSeconds,
            segmentStartedAt = null,
        )
        return Ticked(finished, completed = true)
    }

    /**
     * Converts a finished run into the row to record.
     *
     * [ActiveFocusRun.startedAt] dates the session from when the run began, so a run
     * that was killed mid-way and only completed later still lands on the right day.
     *
     * The end time is derived as `startedAt + elapsed` rather than "now": the stats
     * engine measures a session by `endedAt - startedAt`, so a run that was settled long
     * after it actually finished must not be reported as hours of focus. Returns null
     * when nothing worth recording happened.
     */
    fun toSession(
        run: ActiveFocusRun,
        id: String,
        now: Long = System.currentTimeMillis(),
    ): FocusSessionEntity? {
        val elapsed = run.elapsedSeconds(now).coerceAtMost(run.totalSeconds)
        if (elapsed < AppConstants.MIN_FOCUS_SECONDS) return null
        // Never trust a start far in the future (a clock change mid-run).
        val startedAt = run.startedAt.coerceAtMost(now)
        val endedAt = (startedAt + elapsed * 1000L).coerceAtMost(now).coerceAtLeast(startedAt)
        return FocusSessionEntity(
            id = id,
            routineId = run.routineId,
            startedAt = startedAt,
            endedAt = endedAt,
            plannedMinutes = run.plannedMinutes,
            completed = elapsed >= run.totalSeconds,
        )
    }

    /** Human-readable "MM:SS" / "H:MM:SS" for the notification's text. */
    fun formatClock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }
}
