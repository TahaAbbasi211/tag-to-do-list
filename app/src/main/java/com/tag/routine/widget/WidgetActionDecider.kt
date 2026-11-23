package com.tag.routine.widget

import com.tag.routine.data.Dates
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.RoutineLogEntity
import com.tag.routine.data.StatsEngine
import java.time.LocalDate
import java.util.Objects

/** Why a widget mark-done action skipped writing a completion log. */
enum class WidgetSkipReason {
    INVALID_INPUT,
    STALE_DATE,
    ROUTINE_MISSING,
    NOT_SCHEDULED_TODAY,
    ALREADY_DONE,
}

/** Pure outcome of handling a widget tick tap against current database state. */
sealed interface WidgetActionDecision {
    data class MarkDone(val routineId: String, val date: LocalDate) : WidgetActionDecision
    data class RefreshOnly(val reason: WidgetSkipReason) : WidgetActionDecision
}

/**
 * Pure JVM decision logic for the wide widget's one-tap completion button.
 *
 * The action is strictly idempotent ("mark done", never toggle): stale dates (after
 * midnight), deleted/unscheduled routines, or items already completed today produce
 * [WidgetActionDecision.RefreshOnly] so the widget re-syncs without duplicating logs.
 */
object WidgetActionDecider {

    const val MISSING_EPOCH_DAY = Long.MIN_VALUE

    fun decide(
        routineId: String?,
        actionEpochDay: Long,
        today: LocalDate,
        routines: List<RoutineEntity>,
        routineLogs: List<RoutineLogEntity>,
    ): WidgetActionDecision {
        if (routineId.isNullOrBlank() || actionEpochDay == MISSING_EPOCH_DAY) {
            return WidgetActionDecision.RefreshOnly(WidgetSkipReason.INVALID_INPUT)
        }
        if (actionEpochDay != today.toEpochDay()) {
            return WidgetActionDecision.RefreshOnly(WidgetSkipReason.STALE_DATE)
        }
        val routine = routines.firstOrNull { it.id == routineId }
            ?: return WidgetActionDecision.RefreshOnly(WidgetSkipReason.ROUTINE_MISSING)
        if (!StatsEngine.isScheduledOn(routine, today)) {
            return WidgetActionDecision.RefreshOnly(WidgetSkipReason.NOT_SCHEDULED_TODAY)
        }
        val todayIso = Dates.iso(today)
        if (routineLogs.any { it.routineId == routineId && it.date == todayIso }) {
            return WidgetActionDecision.RefreshOnly(WidgetSkipReason.ALREADY_DONE)
        }
        return WidgetActionDecision.MarkDone(routineId = routineId, date = today)
    }

    /**
     * Produces a positive request code unique per (rowIndex, routineId, epochDay) and
     * disjoint from [com.tag.routine.notifications.ReminderScheduler] and `openApp` (0).
     */
    fun requestCodeFor(rowIndex: Int, routineId: String, epochDay: Long): Int {
        val slotBits = (rowIndex.coerceIn(0, 3) and 0x3) shl 24
        val keyBits = Objects.hash(routineId, epochDay) and 0x00FFFFFF
        return 0x20000000 or slotBits or keyBits
    }
}
