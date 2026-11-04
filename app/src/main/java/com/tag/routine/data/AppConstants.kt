package com.tag.routine.data

/**
 * Central home for tuning values that used to be scattered magic numbers.
 * Keeping them here makes the behaviour of the app auditable in one place.
 */
object AppConstants {

    /**
     * Fraction of a day's routines that must be completed for the day to count
     * toward a streak.
     */
    const val STREAK_THRESHOLD = 0.8f

    /** How many days of history the insights screen looks at. */
    const val HISTORY_DAYS = 120

    /** Trailing window used to project the current pace forward. */
    const val PROJECTION_DAYS = 30

    /** How long the UI keeps its data warm after the last collector detaches. */
    const val STATE_TIMEOUT_MILLIS = 5_000L

    /** Shortest focus run worth recording, in seconds. */
    const val MIN_FOCUS_SECONDS = 60

    /** Default focus length offered in the timer sheet, in minutes. */
    const val DEFAULT_FOCUS_MINUTES = 25

    /** Focus lengths offered as quick choices, in minutes. */
    val FOCUS_PRESETS = listOf(15, 25, 45, 60)

    /** How far ahead a snoozed reminder is re-armed, in minutes. */
    const val SNOOZE_MINUTES = 15

    /** Grace period so an alarm is never scheduled in the immediate past. */
    const val ALARM_GRACE_MILLIS = 5_000L

    /** Inexact-alarm fallback window when exact alarms are unavailable. */
    const val ALARM_WINDOW_MILLIS = 10 * 60 * 1000L

    /** Hours in a day, used for the digest-time picker. */
    const val HOURS_PER_DAY = 24

    /** Minutes in an hour. */
    const val MINUTES_PER_HOUR = 60
}
