package com.tag.routine.motion

import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.ParticleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticleEngineTest {

    @Test
    fun `particle count obeys per-level ceiling`() {
        assertEquals(60, ParticleEngine.particleCountFor(AnimationLevel.FULL, 120))
        assertEquals(40, ParticleEngine.particleCountFor(AnimationLevel.FULL, 40))
        assertEquals(24, ParticleEngine.particleCountFor(AnimationLevel.REDUCED, 100))
        assertEquals(12, ParticleEngine.particleCountFor(AnimationLevel.REDUCED, 12))
        assertEquals(0, ParticleEngine.particleCountFor(AnimationLevel.OFF, 60))
        assertEquals(0, ParticleEngine.particleCountFor(AnimationLevel.FULL, -5))
    }

    @Test
    fun `confetti and sparkles are deterministic for the same seed and differ across seeds`() {
        val c1 = ParticleEngine.generateConfetti(seed = 42L, level = AnimationLevel.FULL, requestedCount = 60)
        val c2 = ParticleEngine.generateConfetti(seed = 42L, level = AnimationLevel.FULL, requestedCount = 60)
        val c3 = ParticleEngine.generateConfetti(seed = 99L, level = AnimationLevel.FULL, requestedCount = 60)

        assertEquals(60, c1.size)
        assertEquals(c1, c2)
        assertNotEquals(c1, c3)

        val sReduced = ParticleEngine.generateSparkles(seed = 7L, level = AnimationLevel.REDUCED, requestedCount = 50)
        assertTrue(sReduced.size <= ParticleEngine.MAX_PARTICLES_REDUCED)
        assertEquals(24, sReduced.size)

        val sOff = ParticleEngine.generateSparkles(seed = 7L, level = AnimationLevel.OFF, requestedCount = 20)
        assertTrue(sOff.isEmpty())
    }
}
