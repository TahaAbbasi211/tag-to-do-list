package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the pure date helpers. These run on the JVM, so they must not
 * touch any Android framework class.
 */
class DatesTest {

    @Test
    fun `iso and parse round-trip`() {
        val date = LocalDate.of(2026, 3, 9)
        val iso = Dates.iso(date)
        assertEquals("2026-03-09", iso)
        assertEquals(date, Dates.parse(iso))
    }

    @Test
    fun `parse returns null for malformed input`() {
        assertNull(Dates.parse("not-a-date"))
        assertNull(Dates.parse(""))
        assertNull(Dates.parse("2026-13-01"))
    }

    @Test
    fun `weekdayBit maps Monday to the lowest bit`() {
        // 2026-03-09 is a Monday.
        assertEquals(1 shl 0, Dates.weekdayBit(LocalDate.of(2026, 3, 9)))
        // 2026-03-15 is a Sunday, the seventh bit.
        assertEquals(1 shl 6, Dates.weekdayBit(LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun `EVERY_DAY covers all seven weekdays`() {
        val monday = LocalDate.of(2026, 3, 9)
        for (offset in 0..6) {
            val bit = Dates.weekdayBit(monday.plusDays(offset.toLong()))
            assertTrue("day $offset not in EVERY_DAY", (Dates.EVERY_DAY and bit) != 0)
        }
    }
}
