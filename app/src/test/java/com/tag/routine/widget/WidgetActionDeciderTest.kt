package com.tag.routine.widget

import com.tag.routine.data.Dates
import com.tag.routine.data.Groups
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.RoutineLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Unit tests for [WidgetActionDecider] covering valid mark-done actions, idempotent
 * handling of already-done / double-tapped / deleted / unscheduled items, stale post-midnight
 * PendingIntents, widget state progression, and unique PendingIntent request codes.
 */
class WidgetActionDeciderTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15) // Sunday
    private val todayEpoch: Long = today.toEpochDay()

    private fun routine(
        id: String,
        title: String = "Routine $id",
        timeMinutes: Int? = null,
        sortOrder: Int = 0,
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
        icon = "spark",
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

    private fun log(routineId: String, date: LocalDate = today) =
        RoutineLogEntity(routineId = routineId, date = Dates.iso(date), doneAt = 1_000L)

    @Test
    fun `valid open routine scheduled today produces MarkDone`() {
        val routines = listOf(routine("r1"))

        val decision = WidgetActionDecider.decide(
            routineId = "r1",
            actionEpochDay = todayEpoch,
            today = today,
            routines = routines,
            routineLogs = emptyList(),
        )

        assertEquals(WidgetActionDecision.MarkDone("r1", today), decision)
    }

    @Test
    fun `routine logged only yesterday still produces MarkDone for today`() {
        val routines = listOf(routine("r1"))
        val logs = listOf(log("r1", today.minusDays(1)))

        val decision = WidgetActionDecider.decide(
            routineId = "r1",
            actionEpochDay = todayEpoch,
            today = today,
            routines = routines,
            routineLogs = logs,
        )

        assertEquals(WidgetActionDecision.MarkDone("r1", today), decision)
    }

    @Test
    fun `already completed routine today returns RefreshOnly without unticking`() {
        val routines = listOf(routine("r1"))
        val logs = listOf(log("r1", today))

        val decision = WidgetActionDecider.decide(
            routineId = "r1",
            actionEpochDay = todayEpoch,
            today = today,
            routines = routines,
            routineLogs = logs,
        )

        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.ALREADY_DONE),
            decision,
        )
    }

    @Test
    fun `deleted routine returns RefreshOnly with ROUTINE_MISSING`() {
        val decision = WidgetActionDecider.decide(
            routineId = "deleted-id",
            actionEpochDay = todayEpoch,
            today = today,
            routines = listOf(routine("other")),
            routineLogs = emptyList(),
        )

        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.ROUTINE_MISSING),
            decision,
        )
    }

    @Test
    fun `archived routine returns RefreshOnly with NOT_SCHEDULED_TODAY`() {
        val routines = listOf(routine("r1", archived = true))

        val decision = WidgetActionDecider.decide(
            routineId = "r1",
            actionEpochDay = todayEpoch,
            today = today,
            routines = routines,
            routineLogs = emptyList(),
        )

        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.NOT_SCHEDULED_TODAY),
            decision,
        )
    }

    @Test
    fun `excluded, ended, or wrong-weekday routine returns NOT_SCHEDULED_TODAY`() {
        val excluded = routine("excluded", excludedDates = Dates.iso(today))
        val ended = routine("ended", endDateEpochDay = today.minusDays(1).toEpochDay())
        val mondayOnly = routine("monday", daysMask = Dates.weekdayBit(DayOfWeek.MONDAY))
        val routines = listOf(excluded, ended, mondayOnly)

        for (id in listOf("excluded", "ended", "monday")) {
            val decision = WidgetActionDecider.decide(
                routineId = id,
                actionEpochDay = todayEpoch,
                today = today,
                routines = routines,
                routineLogs = emptyList(),
            )
            assertEquals(
                WidgetActionDecision.RefreshOnly(WidgetSkipReason.NOT_SCHEDULED_TODAY),
                decision,
            )
        }
    }

    @Test
    fun `stale date from yesterday after midnight returns STALE_DATE`() {
        val routines = listOf(routine("r1"))

        val decision = WidgetActionDecider.decide(
            routineId = "r1",
            actionEpochDay = today.minusDays(1).toEpochDay(),
            today = today,
            routines = routines,
            routineLogs = emptyList(),
        )

        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.STALE_DATE),
            decision,
        )
    }

    @Test
    fun `future date mismatch returns STALE_DATE`() {
        val routines = listOf(routine("r1"))

        val decision = WidgetActionDecider.decide(
            routineId = "r1",
            actionEpochDay = today.plusDays(1).toEpochDay(),
            today = today,
            routines = routines,
            routineLogs = emptyList(),
        )

        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.STALE_DATE),
            decision,
        )
    }

    @Test
    fun `missing or blank inputs return INVALID_INPUT`() {
        val routines = listOf(routine("r1"))

        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.INVALID_INPUT),
            WidgetActionDecider.decide(null, todayEpoch, today, routines, emptyList()),
        )
        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.INVALID_INPUT),
            WidgetActionDecider.decide("   ", todayEpoch, today, routines, emptyList()),
        )
        assertEquals(
            WidgetActionDecision.RefreshOnly(WidgetSkipReason.INVALID_INPUT),
            WidgetActionDecider.decide("r1", WidgetActionDecider.MISSING_EPOCH_DAY, today, routines, emptyList()),
        )
    }

    @Test
    fun `double tap writes only a single completion log and ignores second tap`() {
        val routines = listOf(routine("r1"))
        val logs = mutableListOf<RoutineLogEntity>()

        val first = WidgetActionDecider.decide("r1", todayEpoch, today, routines, logs)
        assertTrue(first is WidgetActionDecision.MarkDone)
        if (first is WidgetActionDecision.MarkDone) {
            logs += log(first.routineId, first.date)
        }

        val second = WidgetActionDecider.decide("r1", todayEpoch, today, routines, logs)
        assertEquals(WidgetActionDecision.RefreshOnly(WidgetSkipReason.ALREADY_DONE), second)
        assertEquals(1, logs.size)
    }

    @Test
    fun `marking items done advances widget queue, updates counts, and enters COMPLETE`() {
        val routines = listOf(
            routine("r1", timeMinutes = 7 * 60, sortOrder = 0),
            routine("r2", timeMinutes = 9 * 60, sortOrder = 1),
            routine("r3", timeMinutes = 12 * 60, sortOrder = 2),
            routine("r4", timeMinutes = 18 * 60, sortOrder = 3),
        )

        val initial = WidgetStateBuilder.build(routines, emptyList(), today)
        assertEquals(todayEpoch, initial.epochDay)
        assertEquals(0, initial.completed)
        assertEquals(0, initial.percent)
        assertEquals(listOf("r1", "r2", "r3"), initial.nextItems.map { it.id })

        val afterFirst = WidgetStateBuilder.build(routines, listOf(log("r1")), today)
        assertEquals(1, afterFirst.completed)
        assertEquals(25, afterFirst.percent)
        assertEquals(WidgetMode.IN_PROGRESS, afterFirst.mode)
        assertEquals(listOf("r2", "r3", "r4"), afterFirst.nextItems.map { it.id })

        val allDone = WidgetStateBuilder.build(
            routines,
            listOf(log("r1"), log("r2"), log("r3"), log("r4")),
            today,
        )
        assertEquals(WidgetMode.COMPLETE, allDone.mode)
        assertEquals(4, allDone.completed)
        assertEquals(100, allDone.percent)
        assertTrue(allDone.nextItems.isEmpty())
    }

    @Test
    fun `requestCodeFor produces distinct positive codes per slot and routine`() {
        val codesSameRoutine = (0..2).map { row ->
            WidgetActionDecider.requestCodeFor(row, "r1", todayEpoch)
        }
        assertEquals(3, codesSameRoutine.toSet().size)

        val codeOtherRoutine = WidgetActionDecider.requestCodeFor(0, "r2", todayEpoch)
        assertNotEquals(codesSameRoutine[0], codeOtherRoutine)

        (codesSameRoutine + codeOtherRoutine).forEach { code ->
            assertTrue("request code must be >= 0x20000000, got $code", code >= 0x20000000)
        }
    }
}
