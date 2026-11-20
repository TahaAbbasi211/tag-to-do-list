package com.tag.routine.widget

import com.tag.routine.data.Dates
import com.tag.routine.data.Groups
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.RoutineLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the home-screen widget's state builder.
 *
 * The point of these is that the widget must not invent its own statistics: it has to
 * agree with [com.tag.routine.data.StatsEngine] and with the Today screen for the same
 * inputs. Each case therefore pins the exact numbers the app would show.
 */
class WidgetStateBuilderTest {

    private val day: LocalDate = LocalDate.of(2026, 3, 15) // a Sunday

    private fun routine(
        id: String,
        title: String = "Routine $id",
        timeMinutes: Int? = null,
        sortOrder: Int = 0,
        icon: String = "spark",
        daysMask: Int = Dates.EVERY_DAY,
        repeatDaily: Boolean = true,
        dateEpochDay: Long = 0L,
        archived: Boolean = false,
        endDateEpochDay: Long? = null,
        excludedDates: String = "",
    ) = RoutineEntity(
        id = id,
        title = title,
        groupName = Groups.MORNING,
        icon = icon,
        timeMinutes = timeMinutes,
        daysMask = daysMask,
        reminder = false,
        colorHex = 0xFF7C6CFF,
        sortOrder = sortOrder,
        archived = archived,
        createdAt = 0L,
        dateEpochDay = dateEpochDay,
        repeatDaily = repeatDaily,
        endDateEpochDay = endDateEpochDay,
        excludedDates = excludedDates,
    )

    private fun log(routineId: String, date: LocalDate = day) =
        RoutineLogEntity(routineId = routineId, date = Dates.iso(date), doneAt = 0L)

    // ------------------------------------------------------------ empty day

    @Test
    fun `a day with nothing scheduled is neutral, not zero percent`() {
        val state = WidgetStateBuilder.build(emptyList(), emptyList(), day)

        assertEquals(WidgetMode.EMPTY, state.mode)
        assertEquals(0, state.total)
        assertEquals(0, state.completed)
        assertEquals(0, state.percent)
        assertTrue(state.nextItems.isEmpty())
        assertEquals(false, state.hasItems)
    }

    @Test
    fun `routines that are not scheduled today leave the day empty`() {
        // Monday-only routine, and an archived one: neither belongs to this Sunday.
        val mondayOnly = routine("a", daysMask = Dates.weekdayBit(java.time.DayOfWeek.MONDAY))
        val archived = routine("b", archived = true)

        val state = WidgetStateBuilder.build(listOf(mondayOnly, archived), emptyList(), day)

        assertEquals(WidgetMode.EMPTY, state.mode)
        assertEquals(0, state.total)
    }

    @Test
    fun `a routine excluded on this date does not count`() {
        val excluded = routine("a", excludedDates = Dates.iso(day))

        val state = WidgetStateBuilder.build(listOf(excluded), emptyList(), day)

        assertEquals(WidgetMode.EMPTY, state.mode)
    }

    // ------------------------------------------------------------ completion

    @Test
    fun `partly done day reports the same count and percent as the app`() {
        val routines = listOf(routine("a"), routine("b"), routine("c"), routine("d"), routine("e"))
        val logs = listOf(log("a"), log("b"), log("c"))

        val state = WidgetStateBuilder.build(routines, logs, day)

        assertEquals(WidgetMode.IN_PROGRESS, state.mode)
        assertEquals(5, state.total)
        assertEquals(3, state.completed)
        assertEquals(60, state.percent)
        assertEquals(2, state.nextItems.size)
    }

    @Test
    fun `percent is truncated exactly like the Today screen`() {
        // 2 of 3 is 66.67%; the app shows (0.6667 * 100).toInt() == 66, not 67.
        val routines = listOf(routine("a"), routine("b"), routine("c"))
        val logs = listOf(log("a"), log("b"))

        val state = WidgetStateBuilder.build(routines, logs, day)

        assertEquals(66, state.percent)
    }

    @Test
    fun `a fully done day is complete with one hundred percent`() {
        val routines = listOf(routine("a"), routine("b"))
        val logs = listOf(log("a"), log("b"))

        val state = WidgetStateBuilder.build(routines, logs, day)

        assertEquals(WidgetMode.COMPLETE, state.mode)
        assertEquals(100, state.percent)
        assertEquals(2, state.completed)
        assertEquals(2, state.total)
        assertTrue(state.nextItems.isEmpty())
    }

    @Test
    fun `logs from another day do not count towards today`() {
        val routines = listOf(routine("a"), routine("b"))
        val logs = listOf(log("a", day.minusDays(1)), log("b", day.plusDays(1)))

        val state = WidgetStateBuilder.build(routines, logs, day)

        assertEquals(0, state.completed)
        assertEquals(WidgetMode.IN_PROGRESS, state.mode)
    }

    @Test
    fun `a log for a routine not scheduled today is ignored`() {
        val routines = listOf(routine("a"))
        val logs = listOf(log("a"), log("ghost"))

        val state = WidgetStateBuilder.build(routines, logs, day)

        assertEquals(1, state.completed)
        assertEquals(1, state.total)
        assertEquals(WidgetMode.COMPLETE, state.mode)
    }

    // ------------------------------------------------------------ repeat rules

    @Test
    fun `a repeating routine scheduled on this weekday counts`() {
        val sunday = routine("a", daysMask = Dates.weekdayBit(java.time.DayOfWeek.SUNDAY))

        val state = WidgetStateBuilder.build(listOf(sunday), emptyList(), day)

        assertEquals(1, state.total)
        assertEquals(WidgetMode.IN_PROGRESS, state.mode)
    }

    @Test
    fun `a repeating routine respects its end date`() {
        val ended = routine("a", endDateEpochDay = day.minusDays(1).toEpochDay())

        val state = WidgetStateBuilder.build(listOf(ended), emptyList(), day)

        assertEquals(WidgetMode.EMPTY, state.mode)
    }

    @Test
    fun `a one-off routine counts only on its own date`() {
        val oneOff = routine("a", repeatDaily = false, dateEpochDay = day.toEpochDay())

        val onItsDay = WidgetStateBuilder.build(listOf(oneOff), emptyList(), day)
        val nextDay = WidgetStateBuilder.build(listOf(oneOff), emptyList(), day.plusDays(1))

        assertEquals(1, onItsDay.total)
        assertEquals(WidgetMode.IN_PROGRESS, onItsDay.mode)
        assertEquals(WidgetMode.EMPTY, nextDay.mode)
    }

    // ------------------------------------------------------------ next items

    @Test
    fun `next items are ordered by clock time and skip finished ones`() {
        val routines = listOf(
            routine("late", timeMinutes = 18 * 60, sortOrder = 0),
            routine("early", timeMinutes = 7 * 60, sortOrder = 1),
            routine("mid", timeMinutes = 12 * 60, sortOrder = 2),
        )
        val logs = listOf(log("early"))

        val state = WidgetStateBuilder.build(routines, logs, day)

        assertEquals(listOf("mid", "late"), state.nextItems.map { it.id })
    }

    @Test
    fun `next items are capped at three`() {
        val routines = (0 until 6).map { index ->
            routine("r$index", timeMinutes = 6 * 60 + index * 30, sortOrder = index)
        }

        val state = WidgetStateBuilder.build(routines, emptyList(), day)

        assertEquals(6, state.total)
        assertEquals(WidgetStateBuilder.MAX_NEXT_ITEMS, state.nextItems.size)
        assertEquals(listOf("r0", "r1", "r2"), state.nextItems.map { it.id })
    }

    @Test
    fun `untimed items sort after timed ones and keep manual order`() {
        val routines = listOf(
            routine("untimed2", timeMinutes = null, sortOrder = 5),
            routine("timed", timeMinutes = 9 * 60, sortOrder = 9),
            routine("untimed1", timeMinutes = null, sortOrder = 2),
        )

        val state = WidgetStateBuilder.build(routines, emptyList(), day)

        assertEquals(listOf("timed", "untimed1", "untimed2"), state.nextItems.map { it.id })
    }

    @Test
    fun `with a clock, items still ahead come before ones already passed`() {
        val routines = listOf(
            routine("morning", timeMinutes = 7 * 60),
            routine("noon", timeMinutes = 12 * 60),
            routine("evening", timeMinutes = 20 * 60),
        )

        // 13:00: noon has passed but is still open, so it goes after the evening item.
        // Within the already-passed group the order stays clock order, per the spec.
        val state = WidgetStateBuilder.build(routines, emptyList(), day, nowMinutes = 13 * 60)

        assertEquals(listOf("evening", "morning", "noon"), state.nextItems.map { it.id })
    }

    @Test
    fun `an item due exactly now still counts as ahead`() {
        val routines = listOf(routine("now", timeMinutes = 9 * 60))

        val state = WidgetStateBuilder.build(routines, emptyList(), day, nowMinutes = 9 * 60)

        assertEquals(listOf("now"), state.nextItems.map { it.id })
    }

    @Test
    fun `next items carry the icon and time the widget renders`() {
        val routines = listOf(routine("a", title = "Drink water", timeMinutes = 8 * 60 + 15, icon = "water"))

        val state = WidgetStateBuilder.build(routines, emptyList(), day)
        val item = state.nextItems.single()

        assertEquals("Drink water", item.title)
        assertEquals("water", item.icon)
        assertEquals(8 * 60 + 15, item.timeMinutes)
        assertEquals(false, item.done)
    }

    // ------------------------------------------------------------ midnight boundary

    @Test
    fun `the same data flips to empty once the day rolls over`() {
        val routines = listOf(routine("a", timeMinutes = 8 * 60))
        val logs = listOf(log("a", day))

        val today = WidgetStateBuilder.build(routines, logs, day)
        val tomorrow = WidgetStateBuilder.build(routines, logs, day.plusDays(1))

        assertEquals(WidgetMode.COMPLETE, today.mode)
        assertEquals(100, today.percent)
        // A repeating routine is scheduled tomorrow too, but nothing is logged for it yet.
        assertEquals(WidgetMode.IN_PROGRESS, tomorrow.mode)
        assertEquals(0, tomorrow.completed)
        assertEquals(0, tomorrow.percent)
    }

    @Test
    fun `a one-off day becomes empty after midnight`() {
        val oneOff = routine("a", repeatDaily = false, dateEpochDay = day.toEpochDay())
        val logs = listOf(log("a", day))

        val state = WidgetStateBuilder.build(listOf(oneOff), logs, day.plusDays(1))

        assertEquals(WidgetMode.EMPTY, state.mode)
    }

    @Test
    fun `midnight itself belongs to the new day`() {
        val routines = listOf(routine("a"))
        val logs = listOf(log("a", day))

        // 00:00 of the next day: yesterday's completion must not leak in.
        val state = WidgetStateBuilder.build(routines, logs, day.plusDays(1), nowMinutes = 0)

        assertEquals(0, state.completed)
        assertEquals(0, state.percent)
        assertEquals(WidgetMode.IN_PROGRESS, state.mode)
    }
}
