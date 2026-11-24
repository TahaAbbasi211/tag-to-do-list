package com.tag.routine.motion

import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.TagMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagMotionTest {

    @Test
    fun `staggerDelayMillis grows linearly and caps at 400ms`() {
        assertEquals(0, TagMotion.staggerDelayMillis(-2))
        assertEquals(0, TagMotion.staggerDelayMillis(0))
        assertEquals(45, TagMotion.staggerDelayMillis(1))
        assertEquals(180, TagMotion.staggerDelayMillis(4))
        assertEquals(400, TagMotion.staggerDelayMillis(10))
        assertEquals(400, TagMotion.staggerDelayMillis(50))
    }

    @Test
    fun `evaluateCountUp starts at from, advances smoothly with easing, and ends at to`() {
        assertEquals(10, TagMotion.evaluateCountUp(10, 80, 0f))
        assertEquals(10, TagMotion.evaluateCountUp(10, 80, -0.5f))
        assertEquals(80, TagMotion.evaluateCountUp(10, 80, 1f))
        assertEquals(80, TagMotion.evaluateCountUp(10, 80, 1.5f))

        val mid = TagMotion.evaluateCountUp(0, 100, 0.5f)
        // EmphasizedDecelerate reaches > 50% of distance by half duration.
        assertTrue("Expected decelerated midpoint > 50, got $mid", mid > 50)
    }

    @Test
    fun `levelAdjustedDuration scales down for REDUCED and zeroes for OFF`() {
        assertEquals(320, TagMotion.levelAdjustedDuration(320, AnimationLevel.FULL))
        assertEquals(160, TagMotion.levelAdjustedDuration(320, AnimationLevel.REDUCED))
        assertEquals(180, TagMotion.levelAdjustedDuration(520, AnimationLevel.REDUCED))
        assertEquals(0, TagMotion.levelAdjustedDuration(520, AnimationLevel.OFF))
    }
}
