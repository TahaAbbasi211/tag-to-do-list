package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateRolloverPolicyTest {

    @Test
    fun `same calendar day produces no rollover change`() {
        val today = LocalDate.of(2026, 11, 25)
        val result = DateRolloverPolicy.reconcile(
            currentToday = today,
            selectedDate = today.minusDays(1),
            observedToday = today,
        )
        assertFalse(result.changed)
        assertEquals(today, result.today)
        assertEquals(today.minusDays(1), result.selectedDate)
    }

    @Test
    fun `midnight rollover advances selectedDate when user was viewing today`() {
        val oldToday = LocalDate.of(2026, 11, 25)
        val newToday = oldToday.plusDays(1)
        val result = DateRolloverPolicy.reconcile(
            currentToday = oldToday,
            selectedDate = oldToday,
            observedToday = newToday,
        )
        assertTrue(result.changed)
        assertEquals(newToday, result.today)
        assertEquals(newToday, result.selectedDate)
    }

    @Test
    fun `midnight rollover preserves explicit past or future selectedDate`() {
        val oldToday = LocalDate.of(2026, 11, 25)
        val newToday = oldToday.plusDays(1)

        val pastResult = DateRolloverPolicy.reconcile(
            currentToday = oldToday,
            selectedDate = oldToday.minusDays(2),
            observedToday = newToday,
        )
        assertTrue(pastResult.changed)
        assertEquals(newToday, pastResult.today)
        assertEquals(oldToday.minusDays(2), pastResult.selectedDate)

        val futureResult = DateRolloverPolicy.reconcile(
            currentToday = oldToday,
            selectedDate = oldToday.plusDays(3),
            observedToday = newToday,
        )
        assertTrue(futureResult.changed)
        assertEquals(newToday, futureResult.today)
        assertEquals(oldToday.plusDays(3), futureResult.selectedDate)
    }

    @Test
    fun `multi-day sleep or backwards timezone shift still updates today`() {
        val oldToday = LocalDate.of(2026, 11, 25)
        val threeDaysLater = oldToday.plusDays(3)
        val forward = DateRolloverPolicy.reconcile(oldToday, oldToday, threeDaysLater)
        assertTrue(forward.changed)
        assertEquals(threeDaysLater, forward.today)
        assertEquals(threeDaysLater, forward.selectedDate)

        val dayEarlier = oldToday.minusDays(1)
        val backward = DateRolloverPolicy.reconcile(oldToday, oldToday, dayEarlier)
        assertTrue(backward.changed)
        assertEquals(dayEarlier, backward.today)
        assertEquals(dayEarlier, backward.selectedDate)
    }
}
