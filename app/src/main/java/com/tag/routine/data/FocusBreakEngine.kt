package com.tag.routine.data

import java.util.UUID

/**
 * The break-related settings the timer needs, decoupled from [AppSettings] so the
 * state machine can be tested without Android or DataStore.
 */
data class FocusBreakSettings(
    val enabled: Boolean = false,
    val shortBreakMinutes: Int = AppConstants.DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = AppConstants.DEFAULT_LONG_BREAK_MINUTES,
    val longBreakInterval: Int = AppConstants.DEFAULT_LONG_BREAK_INTERVAL,
    val autoStartBreak: Boolean = true,
) {
    companion object {
        fun from(settings: AppSettings): FocusBreakSettings = FocusBreakSettings(
            enabled = settings.breakModeEnabled,
            shortBreakMinutes = AppConstants.clampShortBreakMinutes(settings.shortBreakMinutes),
            longBreakMinutes = AppConstants.clampLongBreakMinutes(settings.longBreakMinutes),
            longBreakInterval = AppConstants.clampLongBreakInterval(settings.longBreakInterval),
            autoStartBreak = settings.autoStartBreak,
        )
    }
}

/**
 * Pure Pomodoro phase state machine.
 *
 * A focus session always moves to a break when break mode is on: the short break by
 * default, and the long break once [FocusBreakSettings.longBreakInterval] focus
 * sessions in the cycle have completed. A finished or skipped break produces a paused
 * focus run at the full length, ready for the user to start. No break is ever turned
 * into a recorded focus session.
 */
object FocusBreakEngine {

    /** The break that follows a completed [focus], or null when break mode is off. */
    fun afterFocus(
        focus: ActiveFocusRun,
        settings: FocusBreakSettings,
        now: Long = System.currentTimeMillis(),
    ): ActiveFocusRun? {
        if (!settings.enabled || focus.phase != FocusPhase.FOCUS) return null
        val interval = AppConstants.clampLongBreakInterval(settings.longBreakInterval)
        val cycle = focus.cycleCount.coerceAtLeast(1)
        val longBreak = cycle >= interval
        val minutes = if (longBreak) {
            AppConstants.clampLongBreakMinutes(settings.longBreakMinutes)
        } else {
            AppConstants.clampShortBreakMinutes(settings.shortBreakMinutes)
        }
        val phase = if (longBreak) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
        return focus.copy(
            id = UUID.randomUUID().toString(),
            plannedMinutes = minutes,
            segmentStartedAt = if (settings.autoStartBreak) now else null,
            accumulatedSeconds = 0,
            startedAt = now,
            phase = phase,
            cycleCount = cycle,
            focusMinutes = AppConstants.clampFocusMinutes(focus.focusMinutes),
        )
    }

    /** The paused focus run that follows [breakRun], with the cycle advanced. */
    fun nextFocus(
        breakRun: ActiveFocusRun,
        settings: FocusBreakSettings,
        now: Long = System.currentTimeMillis(),
    ): ActiveFocusRun {
        val interval = AppConstants.clampLongBreakInterval(settings.longBreakInterval)
        val nextCycle = if (breakRun.phase == FocusPhase.LONG_BREAK) {
            1
        } else {
            (breakRun.cycleCount.coerceAtLeast(1) + 1).coerceAtMost(interval)
        }
        val focusMinutes = AppConstants.clampFocusMinutes(breakRun.focusMinutes)
        return breakRun.copy(
            id = UUID.randomUUID().toString(),
            plannedMinutes = focusMinutes,
            segmentStartedAt = null,
            accumulatedSeconds = 0,
            startedAt = now,
            phase = FocusPhase.FOCUS,
            cycleCount = nextCycle,
            focusMinutes = focusMinutes,
        )
    }

    /** Skips the active break, returning the next paused focus run, or null if not in a break. */
    fun skipBreak(
        run: ActiveFocusRun,
        settings: FocusBreakSettings,
        now: Long = System.currentTimeMillis(),
    ): ActiveFocusRun? =
        if (run.phase.isBreak) nextFocus(run, settings, now) else null
}