package com.tag.routine.widget

import com.tag.routine.data.Dates
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.RoutineLogEntity
import com.tag.routine.data.StatsEngine
import java.time.LocalDate

/**
 * One item the widget can display: a routine scheduled on the widget's day.
 *
 * [icon] is the persisted icon key (see `RoutineIcons`), resolved to a drawable by the
 * renderer. [timeMinutes] is minutes from midnight, or null for an untimed routine.
 */
data class WidgetItem(
    val id: String,
    val title: String,
    val icon: String,
    val timeMinutes: Int?,
    val done: Boolean,
)

/** Which of the three shapes the widget is in for the day it is showing. */
enum class WidgetMode {
    /** Nothing is scheduled: a neutral message, never a "0%" failure. */
    EMPTY,

    /** Some items are still open. */
    IN_PROGRESS,

    /** Everything scheduled is done. */
    COMPLETE,
}

/**
 * Everything the widget needs to draw one day, with no Android types in it.
 *
 * [percent] uses the same truncation as the Today screen (`(progress * 100).toInt()`), so
 * the widget and the app never disagree by a rounding step.
 */
data class WidgetState(
    val mode: WidgetMode,
    val percent: Int,
    val completed: Int,
    val total: Int,
    /** At most [WidgetStateBuilder.MAX_NEXT_ITEMS] open items, in the order they come up. */
    val nextItems: List<WidgetItem>,
) {
    val hasItems: Boolean get() = total > 0
}

/**
 * Builds the widget's state from the app's own scheduling and completion data.
 *
 * This deliberately contains no scheduling rules of its own: the day's items come from
 * [StatsEngine.scheduledOn] (the same call the Today screen and the digest notification
 * use) and completion comes from the `routine_logs` rows for that date, exactly as
 * `AppViewModel` reads them. Ordering mirrors the Today screen as well
 * (`timeMinutes ?: Int.MAX_VALUE`, then `sortOrder`).
 *
 * Pure JVM code, so it is unit-tested without an emulator.
 */
object WidgetStateBuilder {

    /** How many open items the wide widget shows. */
    const val MAX_NEXT_ITEMS = 3

    /**
     * @param routines every routine row, archived ones included; they are filtered out by
     *   [StatsEngine.isScheduledOn], not here.
     * @param routineLogs completion rows for any date; only [date] is considered.
     * @param date the day being shown, normally today in the device's time zone.
     * @param nowMinutes current wall-clock time as minutes from midnight, or null when the
     *   caller has no clock. When supplied, items still ahead come first, then items whose
     *   time has already passed but that are still open, then untimed items.
     * @param maxNextItems cap on [WidgetState.nextItems].
     */
    fun build(
        routines: List<RoutineEntity>,
        routineLogs: List<RoutineLogEntity>,
        date: LocalDate,
        nowMinutes: Int? = null,
        maxNextItems: Int = MAX_NEXT_ITEMS,
    ): WidgetState {
        val iso = Dates.iso(date)
        val doneIds = routineLogs.asSequence()
            .filter { it.date == iso }
            .map { it.routineId }
            .toSet()

        // Same order the Today screen uses: timed items in clock order, then untimed ones
        // in the manual order the user set.
        val scheduled = StatsEngine.scheduledOn(date, routines).sortedWith(
            compareBy<RoutineEntity> { it.timeMinutes ?: Int.MAX_VALUE }
                .thenBy { it.sortOrder },
        )

        val items = scheduled.map { routine ->
            WidgetItem(
                id = routine.id,
                title = routine.title,
                icon = routine.icon,
                timeMinutes = routine.timeMinutes,
                done = routine.id in doneIds,
            )
        }

        val total = items.size
        val completed = items.count { it.done }
        val percent = if (total == 0) {
            0
        } else {
            (completed.toFloat() / total * 100f).toInt().coerceIn(0, 100)
        }

        val mode = when {
            total == 0 -> WidgetMode.EMPTY
            completed >= total -> WidgetMode.COMPLETE
            else -> WidgetMode.IN_PROGRESS
        }

        val next = orderPending(items.filter { !it.done }, nowMinutes)
            .take(maxNextItems.coerceAtLeast(0))

        return WidgetState(
            mode = mode,
            percent = percent,
            completed = completed,
            total = total,
            nextItems = next,
        )
    }

    /**
     * Orders the open items the way the widget reads them: what is still ahead, then what
     * was missed but is still open, then anything without a time. Each group keeps clock
     * order, and the sort is stable so untimed items keep the user's manual order.
     */
    private fun orderPending(pending: List<WidgetItem>, nowMinutes: Int?): List<WidgetItem> {
        val byClock = compareBy<WidgetItem> { it.timeMinutes ?: Int.MAX_VALUE }
        if (nowMinutes == null) return pending.sortedWith(byClock)

        val upcoming = pending.filter { it.timeMinutes != null && it.timeMinutes >= nowMinutes }
        val rest = pending.filter { it.timeMinutes == null || it.timeMinutes < nowMinutes }
        val overdue = rest.filter { it.timeMinutes != null }
        val untimed = rest.filter { it.timeMinutes == null }

        return upcoming.sortedWith(byClock) + overdue.sortedWith(byClock) + untimed
    }
}
