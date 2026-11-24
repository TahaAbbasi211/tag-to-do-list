package com.tag.routine.ui.motion

/**
 * Pure decision and curve helpers for Focus screen motion, calm mode, and one-shot
 * session-completion celebration triggers.
 */
object FocusMotionLogic {

    /** Last-minute threshold (in remaining seconds) that enables a subtle urgency pulse. */
    const val LAST_MINUTE_THRESHOLD_SECONDS = 60

    /**
     * Smoothly maps focus progress `[0f..1f]` into a calm-to-warm color blend factor
     * using a cubic smoothstep curve so early focus stays cool and intensifies near finish.
     */
    fun warmthFraction(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    /** True only while running with 1..60 seconds left in a valid session. */
    fun isLastMinute(
        remainingSeconds: Int,
        running: Boolean,
        totalSeconds: Int,
    ): Boolean = running && totalSeconds > 0 && remainingSeconds in 1..LAST_MINUTE_THRESHOLD_SECONDS

    /** Calm mode may only stay active while a focus session is selected/running. */
    fun shouldAllowCalmMode(hasActiveSession: Boolean): Boolean = hasActiveSession

    /** Coerces a requested calm-mode toggle so inactive sessions always remain normal. */
    fun resolveCalmMode(requestedCalm: Boolean, hasActiveSession: Boolean): Boolean =
        requestedCalm && hasActiveSession

    /**
     * Detects when a newly recorded completed focus session should trigger a one-time
     * celebration in the UI.
     *
     * Returns the new completed session's `endedAt` timestamp strictly once, and `null`
     * on cold start, abandoned stops (`completed == false`), or already-celebrated runs.
     */
    fun detectCelebrationTimestamp(
        hadActiveSession: Boolean,
        baselineCompletedEndedAt: Long?,
        latestCompletedEndedAt: Long?,
        lastCelebratedEndedAt: Long?,
    ): Long? {
        if (!hadActiveSession || latestCompletedEndedAt == null) return null
        if (latestCompletedEndedAt == baselineCompletedEndedAt) return null
        if (latestCompletedEndedAt == lastCelebratedEndedAt) return null
        return latestCompletedEndedAt
    }
}
