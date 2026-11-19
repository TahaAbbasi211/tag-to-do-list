package com.tag.routine.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aggregated focus duration and session count for one existing routine.
 */
data class FocusedItemStat(
    val routine: RoutineEntity,
    val minutes: Int,
    val sessionCount: Int,
)

/**
 * Focus totals used on Today's hero card and Insights' focus breakdown.
 *
 * Unlinked sessions (`routineId == null`, or referencing a deleted routine) always
 * remain counted in [todayMinutes], [totalMinutes], [totalSessions] and [unlinkedMinutes],
 * while [topItems] summarises sessions belonging to existing routines.
 */
data class FocusSummary(
    val todayMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val totalSessions: Int = 0,
    val unlinkedMinutes: Int = 0,
    val unlinkedSessions: Int = 0,
    val topItems: List<FocusedItemStat> = emptyList(),
)

/**
 * Pure-Kotlin aggregation for daily focus totals and per-item focus breakdown.
 *
 * Kept separate from [FocusEngine] and [FocusTimerEngine] so existing timer and
 * Focus-screen calculations remain completely untouched.
 */
object FocusSummaryEngine {

    /** Whole minutes elapsed in [session], clamped to zero if timestamps are inverted. */
    fun sessionMinutes(session: FocusSessionEntity): Int {
        val millis = (session.endedAt - session.startedAt).coerceAtLeast(0L)
        return (millis / 60_000L).toInt()
    }

    /** Total focus minutes started on [day] in [zone], including unlinked sessions. */
    fun minutesForDay(
        sessions: List<FocusSessionEntity>,
        day: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextDayStart = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions
            .filter { it.startedAt in dayStart until nextDayStart }
            .sumOf(::sessionMinutes)
    }

    /**
     * Computes today's total focus time, overall focus time, unlinked session totals,
     * and the ranked per-item breakdown for [today] in [zone].
     */
    fun compute(
        sessions: List<FocusSessionEntity>,
        routines: List<RoutineEntity>,
        today: LocalDate = Dates.today(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): FocusSummary {
        if (sessions.isEmpty()) return FocusSummary()

        val routinesById = routines.associateBy { it.id }
        val todayMinutes = minutesForDay(sessions, today, zone)

        var totalMinutes = 0
        var unlinkedMinutes = 0
        var unlinkedSessions = 0
        val byRoutine = linkedMapOf<String, Pair<Int, Int>>() // routineId -> (minutes, count)

        for (session in sessions) {
            val mins = sessionMinutes(session)
            totalMinutes += mins

            val routine = session.routineId?.let { routinesById[it] }
            if (routine == null) {
                unlinkedMinutes += mins
                unlinkedSessions++
            } else {
                val prev = byRoutine[routine.id] ?: (0 to 0)
                byRoutine[routine.id] = (prev.first + mins) to (prev.second + 1)
            }
        }

        val topItems = byRoutine.entries
            .mapNotNull { (routineId, agg) ->
                val routine = routinesById[routineId] ?: return@mapNotNull null
                if (agg.first <= 0) return@mapNotNull null
                FocusedItemStat(
                    routine = routine,
                    minutes = agg.first,
                    sessionCount = agg.second,
                )
            }
            .sortedWith(
                compareByDescending<FocusedItemStat> { it.minutes }
                    .thenByDescending { it.sessionCount }
                    .thenBy { it.routine.title },
            )

        return FocusSummary(
            todayMinutes = todayMinutes,
            totalMinutes = totalMinutes,
            totalSessions = sessions.size,
            unlinkedMinutes = unlinkedMinutes,
            unlinkedSessions = unlinkedSessions,
            topItems = topItems,
        )
    }

    /** Calendar date on which [session] started in [zone]. */
    fun sessionDate(
        session: FocusSessionEntity,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
}
