package com.tag.routine.motion

import com.tag.routine.ui.motion.StreakMilestones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakMilestoneTest {

    @Test
    fun `recognizes exact milestone days and rejects non-milestones`() {
        val expected = listOf(3, 7, 14, 30, 50, 100)
        for (day in expected) {
            assertTrue(StreakMilestones.isMilestone(day))
            assertEquals(day, StreakMilestones.milestoneFor(day))
        }
        for (day in listOf(0, 1, 2, 4, 6, 8, 15, 29, 31, 49, 99, 101)) {
            assertFalse(StreakMilestones.isMilestone(day))
            assertNull(StreakMilestones.milestoneFor(day))
        }
    }

    @Test
    fun `reachedNewMilestone fires only when advancing onto a milestone`() {
        assertEquals(3, StreakMilestones.reachedNewMilestone(previous = 2, current = 3))
        assertEquals(7, StreakMilestones.reachedNewMilestone(previous = 6, current = 7))
        assertNull(StreakMilestones.reachedNewMilestone(previous = 7, current = 7))
        assertNull(StreakMilestones.reachedNewMilestone(previous = 8, current = 7))
        assertNull(StreakMilestones.reachedNewMilestone(previous = 3, current = 4))
    }
}
