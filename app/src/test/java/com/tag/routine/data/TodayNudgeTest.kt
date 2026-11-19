package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins down which dynamic line the Today hero card shows for a given time and progress. */
class TodayNudgeTest {

    @Test
    fun `an empty day is neutral`() {
        assertEquals(TodayNudge.EMPTY, TodayNudge.forDay(hour = 9, total = 0, completed = 0))
    }

    @Test
    fun `a finished day is celebrated whatever the hour`() {
        assertEquals(TodayNudge.ALL_DONE, TodayNudge.forDay(hour = 9, total = 3, completed = 3))
        assertEquals(TodayNudge.ALL_DONE, TodayNudge.forDay(hour = 23, total = 3, completed = 3))
    }

    @Test
    fun `exactly one open item is called out`() {
        assertEquals(TodayNudge.ONE_LEFT, TodayNudge.forDay(hour = 8, total = 4, completed = 3))
        assertEquals(TodayNudge.ONE_LEFT, TodayNudge.forDay(hour = 23, total = 1, completed = 0))
    }

    @Test
    fun `late evening suggests planning tomorrow`() {
        assertEquals(TodayNudge.NIGHT, TodayNudge.forDay(hour = 22, total = 5, completed = 1))
        assertEquals(TodayNudge.NIGHT, TodayNudge.forDay(hour = 2, total = 5, completed = 1))
    }

    @Test
    fun `morning with nothing done is a morning nudge`() {
        assertEquals(TodayNudge.MORNING, TodayNudge.forDay(hour = 7, total = 5, completed = 0))
        assertEquals(TodayNudge.MORNING, TodayNudge.forDay(hour = 11, total = 5, completed = 1))
    }

    @Test
    fun `being at least half way is recognised`() {
        assertEquals(TodayNudge.HALFWAY, TodayNudge.forDay(hour = 15, total = 4, completed = 2))
    }

    @Test
    fun `the afternoon with little done keeps going`() {
        assertEquals(TodayNudge.KEEP_GOING, TodayNudge.forDay(hour = 15, total = 4, completed = 0))
    }

    @Test
    fun `out of range values do not crash`() {
        assertEquals(TodayNudge.EMPTY, TodayNudge.forDay(hour = 25, total = 0, completed = -2))
        assertEquals(TodayNudge.ALL_DONE, TodayNudge.forDay(hour = -1, total = 2, completed = 9))
    }

    @Test
    fun `hour boundaries switch cleanly between night morning and afternoon`() {
        assertEquals(TodayNudge.NIGHT, TodayNudge.forDay(hour = 4, total = 4, completed = 0))
        assertEquals(TodayNudge.MORNING, TodayNudge.forDay(hour = 5, total = 4, completed = 0))
        assertEquals(TodayNudge.KEEP_GOING, TodayNudge.forDay(hour = 12, total = 4, completed = 0))
        assertEquals(TodayNudge.KEEP_GOING, TodayNudge.forDay(hour = 20, total = 4, completed = 1))
        assertEquals(TodayNudge.NIGHT, TodayNudge.forDay(hour = 21, total = 4, completed = 1))
    }
}
