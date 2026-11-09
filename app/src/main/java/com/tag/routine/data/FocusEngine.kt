package com.tag.routine.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One recorded focus run, resolved for display on the Focus screen.
 *
 * [routineTitle] is null when the run was not attached to a routine, or when the
 * routine has since been deleted.
 */
data class FocusSessionRow(
    val id: String,
    val routineTitle: String?,
    val minutes: Int,
    val completed: Boolean,
    val endedAt: Long,
)

/** Aggregated focus numbers shown on the Focus screen. */
data class FocusStats(
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val todaySessions: Int = 0,
    val completedSessions: Int = 0,
    val totalSessions: Int = 0,
    /** Consecutive days ending today with at least one recorded run. */
    val streak: Int = 0,
    /** Most recent runs, newest first. */
    val recent: List<FocusSessionRow> = emptyList(),
) {
    val averageSessionMinutes: Int
        get() = if (totalSessions == 0) 0 else totalMinutes / totalSessions
}

/**
 * Derived focus-timer numbers for the Focus screen.
 *
 * Kept out of the composables and computed from immutable inputs so the arithmetic
 * can be unit-tested without an Android runtime, in the same spirit as [StatsEngine].
 */
object FocusEngine {

    /** Same streak rule as routines: consecutive days ending today with any session. */
    fun compute(
        sessions: List<FocusSessionEntity>,
        routines: List<RoutineEntity> = emptyList(),
        today: LocalDate = Dates.today(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): FocusStats {
        val titles = routines.associate { it.id to it.title }

        fun minutesOf(session: FocusSessionEntity): Int {
            val millis = (session.endedAt - session.startedAt).coerceAtLeast(0L)
            return (millis / 60_000L).toInt()
        }

        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()

        val todaySessions = sessions.filter { it.startedAt >= todayStart }
        val weekSessions = sessions.filter { it.startedAt >= weekStart }

        // A day counts toward the streak when it holds at least one recorded run.
        val daysWithSessions: Set<LocalDate> = sessions.mapTo(mutableSetOf()) { session ->
            Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
        }

        var streak = 0
        var cursor = today
        // Today is still in progress, so an empty today does not break yesterday's run.
        if (cursor !in daysWithSessions) cursor = cursor.minusDays(1)
        while (cursor in daysWithSessions) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return FocusStats(
            todayMinutes = todaySessions.sumOf(::minutesOf),
            weekMinutes = weekSessions.sumOf(::minutesOf),
            totalMinutes = sessions.sumOf(::minutesOf),
            todaySessions = todaySessions.size,
            completedSessions = sessions.count { it.completed },
            totalSessions = sessions.size,
            streak = streak,
            recent = sessions
                .sortedByDescending { it.endedAt }
                .take(20)
                .map { session ->
                    FocusSessionRow(
                        id = session.id,
                        routineTitle = session.routineId?.let { titles[it] },
                        minutes = minutesOf(session),
                        completed = session.completed,
                        endedAt = session.endedAt,
                    )
                },
        )
    }
}
