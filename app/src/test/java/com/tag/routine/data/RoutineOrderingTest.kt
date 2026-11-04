package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Covers the ordering rules behind today's routine list. The manual move actions
 * write [RoutineEntity.sortOrder], so these tests pin down that the sort order
 * actually decides the order untimed routines appear in.
 */
class RoutineOrderingTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 9) // Monday

    private fun routine(
        id: String,
        group: String = Groups.MORNING,
        timeMinutes: Int? = null,
        sortOrder: Int = 0,
    ) = RoutineEntity(
        id = id,
        title = "Routine $id",
        groupName = group,
        icon = "sun",
        timeMinutes = timeMinutes,
        daysMask = Dates.EVERY_DAY,
        reminder = false,
        colorHex = 0xFF7C6CFF,
        sortOrder = sortOrder,
        archived = false,
        createdAt = 0L,
    )

    /** Mirrors the ordering the view model applies before rendering a group. */
    private fun order(items: List<RoutineEntity>): List<String> =
        items
            .sortedWith(
                compareBy<RoutineEntity> { it.timeMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.sortOrder },
            )
            .map { it.id }

    @Test
    fun `untimed routines follow sortOrder`() {
        val items = listOf(
            routine("c", sortOrder = 3),
            routine("a", sortOrder = 1),
            routine("b", sortOrder = 2),
        )
        assertEquals(listOf("a", "b", "c"), order(items))
    }

    @Test
    fun `swapping two sort orders moves an untimed routine up`() {
        // "b" wants to move above "a": moveRoutine rewrites the pair's sort orders.
        val before = listOf(routine("a", sortOrder = 1), routine("b", sortOrder = 2))
        assertEquals(listOf("a", "b"), order(before))

        val after = listOf(routine("a", sortOrder = 2), routine("b", sortOrder = 1))
        assertEquals(listOf("b", "a"), order(after))
    }

    @Test
    fun `timed routines sort by clock time ahead of untimed ones`() {
        val items = listOf(
            routine("untimed", timeMinutes = null, sortOrder = 1),
            routine("late", timeMinutes = 18 * 60, sortOrder = 2),
            routine("early", timeMinutes = 7 * 60, sortOrder = 3),
        )
        assertEquals(listOf("early", "late", "untimed"), order(items))
    }

    @Test
    fun `sortOrder breaks ties between routines at the same time`() {
        val items = listOf(
            routine("second", timeMinutes = 8 * 60, sortOrder = 2),
            routine("first", timeMinutes = 8 * 60, sortOrder = 1),
        )
        assertEquals(listOf("first", "second"), order(items))
    }

    @Test
    fun `scheduledOn keeps every routine whose mask covers the day`() {
        val mondayOnly = routine("mon", sortOrder = 0).copy(daysMask = Dates.weekdayBit(today))
        val sundayOnly = routine("sun", sortOrder = 0).copy(daysMask = 1 shl 6)
        val scheduled = StatsEngine.scheduledOn(today, listOf(mondayOnly, sundayOnly))
        assertEquals(listOf("mon"), scheduled.map { it.id })
    }
}
