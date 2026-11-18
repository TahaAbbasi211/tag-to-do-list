package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers the copy rules that the Today screen depends on:
 *
 *  * a copy creates an item only, never a completion log;
 *  * a copy lands on the selected day and nowhere else;
 *  * pressing the same action twice never creates a duplicate;
 *  * items that already show up on the target day are not copied again.
 */
class CopyEngineTest {

    private val monday: LocalDate = LocalDate.of(2026, 3, 9)
    private val tuesday: LocalDate = monday.plusDays(1)
    private val wednesday: LocalDate = monday.plusDays(2)

    private fun routine(
        id: String,
        title: String = "Routine $id",
        date: LocalDate = monday,
        repeatDaily: Boolean = false,
        daysMask: Int = Dates.EVERY_DAY,
        icon: String = "☀️",
        colorId: Int = 2,
        timeMinutes: Int? = 7 * 60,
        sortOrder: Int = 0,
        endDate: LocalDate? = null,
        excludedDates: String = "",
    ) = RoutineEntity(
        id = id,
        title = title,
        groupName = Groups.MORNING,
        icon = icon,
        timeMinutes = timeMinutes,
        daysMask = daysMask,
        reminder = true,
        colorHex = RoutineColors.hexFor(colorId, false),
        colorId = colorId,
        sortOrder = sortOrder,
        archived = false,
        createdAt = 0L,
        dateEpochDay = date.toEpochDay(),
        repeatDaily = repeatDaily,
        endDateEpochDay = endDate?.toEpochDay(),
        excludedDates = excludedDates,
    )

    // ------------------------------------------------------------- copy of one

    @Test
    fun `copy keeps the look and the time but is pinned to the target day`() {
        val source = routine("a", icon = "💧", colorId = 4, timeMinutes = 8 * 60 + 30)
        val copy = CopyEngine.copyOf(source, tuesday, sortOrder = 7, now = 1234L)

        assertEquals("Routine a", copy.title)
        assertEquals("💧", copy.icon)
        assertEquals(4, copy.colorId)
        assertEquals(8 * 60 + 30, copy.timeMinutes)
        assertEquals(Groups.MORNING, copy.groupName)

        assertEquals(tuesday.toEpochDay(), copy.dateEpochDay)
        assertEquals(false, copy.repeatDaily)
        assertEquals(7, copy.sortOrder)
        assertEquals(1234L, copy.createdAt)
        assertNull(copy.endDateEpochDay)
        assertEquals("", copy.excludedDates)
    }

    @Test
    fun `copy id is derived from the source and the day`() {
        val source = routine("a")
        val first = CopyEngine.copyId(source.id, tuesday)
        val second = CopyEngine.copyId(source.id, tuesday)
        assertEquals(first, second)
        assertTrue(first.startsWith(CopyEngine.COPY_ID_PREFIX))
        assertEquals(first, CopyEngine.copyOf(source, tuesday, 0, 0L).id)
        assertTrue(first != CopyEngine.copyId(source.id, wednesday))
    }

    @Test
    fun `a single-day item is copied onto the next day`() {
        val source = routine("a", date = monday)
        val planned = CopyEngine.planFor(source, tuesday, existingIds = setOf("a"), sortOrder = 1, now = 0L)
        assertNotNull(planned)
        assertEquals(tuesday.toEpochDay(), planned!!.dateEpochDay)
    }

    // ------------------------------------------------------------- no duplicates

    @Test
    fun `an item that already shows on the target day is not copied`() {
        val source = routine("a", date = tuesday)
        assertNull(CopyEngine.planFor(source, tuesday, existingIds = setOf("a"), sortOrder = 0, now = 0L))
    }

    @Test
    fun `a repeating item that covers the target day is not copied`() {
        val daily = routine("a", date = monday, repeatDaily = true)
        assertNull(CopyEngine.planFor(daily, tuesday, existingIds = setOf("a"), sortOrder = 0, now = 0L))
    }

    @Test
    fun `a repeating item whose mask skips the target day is copied`() {
        // Monday-only repeat: Tuesday is not covered, so a copy is legitimate.
        val mondayOnly = routine("a", date = monday, repeatDaily = true, daysMask = Dates.weekdayBit(monday))
        val planned = CopyEngine.planFor(mondayOnly, tuesday, existingIds = setOf("a"), sortOrder = 0, now = 0L)
        assertNotNull(planned)
        assertEquals(false, planned!!.repeatDaily)
        assertEquals(tuesday.toEpochDay(), planned.dateEpochDay)
    }

    @Test
    fun `pressing copy twice writes nothing the second time`() {
        val routines = listOf(routine("a", date = monday), routine("b", date = monday))

        val first = CopyEngine.plan(monday, tuesday, routines, orderStart = 10, now = 0L)
        assertEquals(2, first.size)

        // Second press sees the copies the first press wrote.
        val second = CopyEngine.plan(
            sourceDay = monday,
            targetDay = tuesday,
            routines = routines + first,
            orderStart = 20,
            now = 0L,
        )
        assertEquals(emptyList<RoutineEntity>(), second)
    }

    @Test
    fun `an existing copy on the target day blocks a second one even when the source repeats`() {
        val mondayOnly = routine("a", date = monday, repeatDaily = true, daysMask = Dates.weekdayBit(monday))
        val existingCopy = CopyEngine.copyOf(mondayOnly, tuesday, sortOrder = 0, now = 0L)
        val planned = CopyEngine.plan(
            sourceDay = monday,
            targetDay = tuesday,
            routines = listOf(mondayOnly, existingCopy),
            orderStart = 0,
            now = 0L,
        )
        assertEquals(emptyList<RoutineEntity>(), planned)
    }

    // ------------------------------------------------------------- target day only

    @Test
    fun `copy yesterday to today writes copies for the target day only`() {
        val routines = listOf(
            routine("a", date = monday),
            routine("b", date = monday, repeatDaily = true, daysMask = Dates.EVERY_DAY),
        )
        val planned = CopyEngine.plan(monday, tuesday, routines, orderStart = 5, now = 0L)

        // "a" is copied; "b" repeats every day so it is already on Tuesday.
        assertEquals(1, planned.size)
        assertEquals("Routine a", planned.single().title)
        assertEquals(tuesday.toEpochDay(), planned.single().dateEpochDay)
        assertTrue(planned.all { it.dateEpochDay == tuesday.toEpochDay() })
        assertTrue(planned.all { !it.repeatDaily })
    }

    @Test
    fun `copying from a day to itself writes nothing`() {
        val routines = listOf(routine("a", date = monday))
        assertEquals(emptyList<RoutineEntity>(), CopyEngine.plan(monday, monday, routines, 0, 0L))
    }

    @Test
    fun `copy keeps the source order`() {
        val routines = listOf(
            routine("c", date = monday, sortOrder = 3, timeMinutes = 9 * 60),
            routine("a", date = monday, sortOrder = 1, timeMinutes = 7 * 60),
            routine("b", date = monday, sortOrder = 2, timeMinutes = 8 * 60),
        )
        val planned = CopyEngine.plan(monday, tuesday, routines, orderStart = 40, now = 0L)
        assertEquals(listOf("a", "b", "c"), planned.map { it.title.removePrefix("Routine ") })
        assertEquals(listOf(40, 41, 42), planned.map { it.sortOrder })
    }

    // ------------------------------------------------------------- copy to several days

    @Test
    fun `copy to several days writes one copy per selected day`() {
        val source = routine("a", date = monday)
        val planned = CopyEngine.planToDays(
            item = source,
            targets = listOf(tuesday, wednesday),
            routines = listOf(source),
            orderStart = 0,
            now = 0L,
        )
        assertEquals(2, planned.size)
        assertEquals(
            listOf(tuesday.toEpochDay(), wednesday.toEpochDay()),
            planned.map { it.dateEpochDay },
        )
    }

    @Test
    fun `copy to several days ignores repeated days and days already covered`() {
        val source = routine("a", date = monday)
        val planned = CopyEngine.planToDays(
            item = source,
            targets = listOf(tuesday, tuesday, monday, wednesday),
            routines = listOf(source),
            orderStart = 0,
            now = 0L,
        )
        // Tuesday is asked for twice and Monday is where the item already is.
        assertEquals(2, planned.size)
        assertEquals(
            listOf(tuesday.toEpochDay(), wednesday.toEpochDay()),
            planned.map { it.dateEpochDay },
        )
    }

    @Test
    fun `copy to several days is idempotent when repeated`() {
        val source = routine("a", date = monday)
        val first = CopyEngine.planToDays(source, listOf(tuesday), listOf(source), 0, 0L)
        val second = CopyEngine.planToDays(
            item = source,
            targets = listOf(tuesday),
            routines = listOf(source) + first,
            orderStart = 10,
            now = 0L,
        )
        assertEquals(emptyList<RoutineEntity>(), second)
    }

    @Test
    fun `chained copies across days keep the same root id and create no duplicates`() {
        val source = routine("a", date = monday)
        val toTueAndWed = CopyEngine.planToDays(
            item = source,
            targets = listOf(tuesday, wednesday),
            routines = listOf(source),
            orderStart = 0,
            now = 0L,
        )
        assertEquals(2, toTueAndWed.size)

        // Copying Tuesday -> Wednesday afterwards must recognize Wednesday already has the lineage.
        val tuesdayToWednesday = CopyEngine.plan(
            sourceDay = tuesday,
            targetDay = wednesday,
            routines = listOf(source) + toTueAndWed,
            orderStart = 10,
            now = 0L,
        )
        assertEquals(emptyList<RoutineEntity>(), tuesdayToWednesday)

        // And copyId of a copy points to the original root source id rather than nesting.
        val tuesdayCopy = toTueAndWed.first { it.dateEpochDay == tuesday.toEpochDay() }
        assertEquals(CopyEngine.copyId("a", wednesday), CopyEngine.copyId(tuesdayCopy.id, wednesday))
    }

    @Test
    fun `archived items on source day are not copied and reminders are kept on copies`() {
        val archived = routine("arch", date = monday).copy(archived = true)
        val active = routine("act", date = monday, colorId = 5, icon = "💧", timeMinutes = 9 * 60 + 15)
        val planned = CopyEngine.plan(monday, tuesday, listOf(archived, active), orderStart = 0, now = 100L)

        assertEquals(1, planned.size)
        val copy = planned.single()
        assertEquals("Routine act", copy.title)
        assertTrue(copy.reminder)
        assertEquals(5, copy.colorId)
        assertEquals(RoutineColors.hexFor(5, false), copy.colorHex)
        assertEquals("💧", copy.icon)
        assertEquals(9 * 60 + 15, copy.timeMinutes)
        assertEquals(tuesday.toEpochDay(), copy.dateEpochDay)
    }
}
