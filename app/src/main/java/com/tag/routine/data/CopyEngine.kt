package com.tag.routine.data

import java.time.LocalDate

/**
 * Plans the "copy items to another day" actions.
 *
 * The whole feature is expressed as pure functions over the current routine list, so it can
 * be unit tested without Room or an Android runtime. Two rules define the behaviour:
 *
 *  1. **Only items are copied.** A copy is a fresh one-off routine pinned to the target day.
 *     No completion log is written, so the tick state is never carried over.
 *
 *  2. **Copying is idempotent.** A copy's primary key is derived from the source id and the
 *     target day (see [copyId]). Pressing the same action twice therefore writes the same
 *     row instead of appending a second one, and a copy that is still on the target day is
 *     detected and skipped before it is planned at all.
 */
object CopyEngine {

    /** Prefix that marks a routine produced by a copy. */
    const val COPY_ID_PREFIX = "copy-"

    /** Separator between the source id and the target epoch day in a copy id. */
    const val COPY_ID_SEPARATOR = "@"

    /**
     * Strips any outer `copy-... @<epochDay>` envelope so copying a copy across multiple
     * days keeps the same lineage and can never nest `copy-copy-...` or create duplicates.
     */
    fun rootSourceId(id: String): String {
        var current = id
        while (current.startsWith(COPY_ID_PREFIX)) {
            val body = current.removePrefix(COPY_ID_PREFIX)
            val sep = body.lastIndexOf(COPY_ID_SEPARATOR)
            if (sep <= 0) break
            val epochPart = body.substring(sep + 1)
            if (epochPart.toLongOrNull() == null) break
            current = body.substring(0, sep)
        }
        return current
    }

    /** Deterministic id of the copy of [sourceId] for [target]. */
    fun copyId(sourceId: String, target: LocalDate): String =
        COPY_ID_PREFIX + rootSourceId(sourceId) + COPY_ID_SEPARATOR + target.toEpochDay()

    /**
     * The copy of [item] that belongs on [target].
     *
     * The copy keeps the look and the schedule of the original (title, group, icon, colour,
     * time, reminder) but is a single-day item: `repeatDaily` is off, the day is pinned to
     * [target] and no date exclusions or end date are inherited.
     */
    fun copyOf(
        item: RoutineEntity,
        target: LocalDate,
        sortOrder: Int,
        now: Long,
    ): RoutineEntity = item.copy(
        id = copyId(item.id, target),
        daysMask = Dates.EVERY_DAY,
        sortOrder = sortOrder,
        archived = false,
        createdAt = now,
        dateEpochDay = target.toEpochDay(),
        repeatDaily = false,
        endDateEpochDay = null,
        excludedDates = "",
    )

    /**
     * The copy of [item] for [target], or null when nothing should be written.
     *
     * Nothing is written when the item already shows up on [target] (a repeating item whose
     * schedule covers that day, or a single-day item that is already there) and when an
     * earlier copy of the same item is still on that day.
     */
    fun planFor(
        item: RoutineEntity,
        target: LocalDate,
        existingIds: Set<String>,
        sortOrder: Int,
        now: Long,
    ): RoutineEntity? {
        if (StatsEngine.isScheduledOn(item, target)) return null
        if (copyId(item.id, target) in existingIds) return null
        return copyOf(item, target, sortOrder, now)
    }

    /**
     * Every routine that [sourceDay] shows and that [targetDay] does not already show,
     * as copies destined for [targetDay].
     *
     * [orderStart] is the first `sortOrder` handed to a copy; each copy gets the next one so
     * the copied block keeps the order it had on the source day.
     */
    fun plan(
        sourceDay: LocalDate,
        targetDay: LocalDate,
        routines: List<RoutineEntity>,
        orderStart: Int,
        now: Long,
        existingIds: Set<String> = routines.mapTo(HashSet()) { it.id },
    ): List<RoutineEntity> {
        if (sourceDay == targetDay) return emptyList()
        val known = existingIds.toMutableSet()
        val planned = mutableListOf<RoutineEntity>()
        // Copy in the order the day actually shows its items: clock time first, then the
        // manual order, exactly like the timeline.
        val ordered = StatsEngine.scheduledOn(sourceDay, routines).sortedWith(
            compareBy<RoutineEntity> { it.timeMinutes ?: Int.MAX_VALUE }.thenBy { it.sortOrder },
        )
        ordered.forEach { item ->
            val copy = planFor(item, targetDay, known, orderStart + planned.size, now + planned.size)
            if (copy != null) {
                known += copy.id
                planned += copy
            }
        }
        return planned
    }

    /**
     * The copies of a single [item] for every day in [targets], newest input order kept.
     *
     * Duplicate days are collapsed, and days the item already appears on are skipped, so
     * tapping the same day twice never produces a second copy.
     */
    fun planToDays(
        item: RoutineEntity,
        targets: List<LocalDate>,
        routines: List<RoutineEntity>,
        orderStart: Int,
        now: Long,
        existingIds: Set<String> = routines.mapTo(HashSet()) { it.id },
    ): List<RoutineEntity> {
        val known = existingIds.toMutableSet()
        val planned = mutableListOf<RoutineEntity>()
        targets.distinct().forEach { target ->
            val copy = planFor(item, target, known, orderStart + planned.size, now + planned.size)
            if (copy != null) {
                known += copy.id
                planned += copy
            }
        }
        return planned
    }
}
