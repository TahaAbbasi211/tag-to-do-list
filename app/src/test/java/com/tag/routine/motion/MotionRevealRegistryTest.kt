package com.tag.routine.motion

import com.tag.routine.ui.motion.MotionRevealRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionRevealRegistryTest {

    @Test
    fun consumeFirstReveal_returnsTrueOncePerKeyAndTracksIndependentKeys() {
        val registry = MotionRevealRegistry()

        assertFalse(registry.hasRevealed("today-hero-ring"))
        assertTrue(registry.consumeFirstReveal("today-hero-ring"))
        assertTrue(registry.hasRevealed("today-hero-ring"))
        assertFalse(registry.consumeFirstReveal("today-hero-ring"))

        // Independent key still gets its first reveal even after tab switch simulation.
        assertTrue(registry.consumeFirstReveal("insights-hero-ring"))
        assertFalse(registry.consumeFirstReveal("insights-hero-ring"))
    }

    @Test
    fun shouldAnimateValue_animatesInitialAndRealChangesOnly() {
        val registry = MotionRevealRegistry()
        val key = "today-hero-count"

        // First display animates.
        assertTrue(registry.shouldAnimateValue(key, 60))
        // Returning to tab with unchanged value does not replay.
        assertFalse(registry.shouldAnimateValue(key, 60))
        // Real data change animates to new target.
        assertTrue(registry.shouldAnimateValue(key, 80))
        // Staying at updated value after tab round-trip does not replay.
        assertFalse(registry.shouldAnimateValue(key, 80))
    }
}
