package com.tag.routine.accessibility

import com.tag.routine.ui.accessibility.MotionPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPreferencesTest {

    @Test
    fun `zero animator duration scale disables animation`() {
        assertFalse(MotionPreferences.animationsEnabled(0f))
    }

    @Test
    fun `normal animator duration scale keeps animation`() {
        assertTrue(MotionPreferences.animationsEnabled(1f))
        assertTrue(MotionPreferences.animationsEnabled(0.5f))
    }

    @Test
    fun `shouldAnimate requires both the system flag and a positive scale`() {
        assertTrue(MotionPreferences.shouldAnimate(animatorsEnabled = true, animatorDurationScale = 1f))
        assertFalse(MotionPreferences.shouldAnimate(animatorsEnabled = false, animatorDurationScale = 1f))
        assertFalse(MotionPreferences.shouldAnimate(animatorsEnabled = true, animatorDurationScale = 0f))
    }
}
