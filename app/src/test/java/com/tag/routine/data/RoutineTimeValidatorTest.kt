package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTimeValidatorTest {

    @Test
    fun `empty hour and minute is valid with null timeMinutes`() {
        val result = RoutineTimeValidator.validate("", "")
        assertTrue(result.isValid)
        assertNull(result.timeMinutes)
        assertFalse(result.hourError)
        assertFalse(result.minuteError)
        assertEquals(RoutineTimeError.NONE, result.error)
    }

    @Test
    fun `valid boundaries 00-00 and 23-59 resolve cleanly`() {
        val midnight = RoutineTimeValidator.validate("0", "")
        assertTrue(midnight.isValid)
        assertEquals(0, midnight.timeMinutes)

        val endOfDay = RoutineTimeValidator.validate("23", "59")
        assertTrue(endOfDay.isValid)
        assertEquals(23 * 60 + 59, endOfDay.timeMinutes)

        val persianDigits = RoutineTimeValidator.validate("۰۷", "۳۰")
        assertTrue(persianDigits.isValid)
        assertEquals(7 * 60 + 30, persianDigits.timeMinutes)
    }

    @Test
    fun `out-of-range hour or minute rejects save and clears timeMinutes`() {
        val badHour = RoutineTimeValidator.validate("24", "30")
        assertFalse(badHour.isValid)
        assertTrue(badHour.hourError)
        assertFalse(badHour.minuteError)
        assertNull(badHour.timeMinutes)
        assertEquals(RoutineTimeError.INVALID_HOUR, badHour.error)

        val badHour99 = RoutineTimeValidator.validate("99", "30")
        assertFalse(badHour99.isValid)
        assertNull(badHour99.timeMinutes)

        val badMinute = RoutineTimeValidator.validate("09", "60")
        assertFalse(badMinute.isValid)
        assertFalse(badMinute.hourError)
        assertTrue(badMinute.minuteError)
        assertNull(badMinute.timeMinutes)
        assertEquals(RoutineTimeError.INVALID_MINUTE, badMinute.error)

        val bothBad = RoutineTimeValidator.validate("25", "75")
        assertFalse(bothBad.isValid)
        assertTrue(bothBad.hourError)
        assertTrue(bothBad.minuteError)
        assertNull(bothBad.timeMinutes)
        assertEquals(RoutineTimeError.INVALID_BOTH, bothBad.error)
    }

    @Test
    fun `minute without hour is rejected instead of defaulting hour to zero`() {
        val validMinNoHour = RoutineTimeValidator.validate("", "30")
        assertFalse(validMinNoHour.isValid)
        assertTrue(validMinNoHour.hourError)
        assertFalse(validMinNoHour.minuteError)
        assertNull(validMinNoHour.timeMinutes)
        assertEquals(RoutineTimeError.INVALID_HOUR, validMinNoHour.error)

        val badMinNoHour = RoutineTimeValidator.validate("", "88")
        assertFalse(badMinNoHour.isValid)
        assertTrue(badMinNoHour.hourError)
        assertTrue(badMinNoHour.minuteError)
        assertNull(badMinNoHour.timeMinutes)
        assertEquals(RoutineTimeError.INVALID_BOTH, badMinNoHour.error)
    }
}
