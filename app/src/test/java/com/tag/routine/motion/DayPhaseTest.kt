package com.tag.routine.motion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.tag.routine.ui.motion.DayPhase
import com.tag.routine.ui.motion.DayPhaseColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPhaseTest {

    @Test
    fun `maps all 24 hours and wrap-around values to expected DayPhase`() {
        assertEquals(DayPhase.NIGHT, DayPhase.of(0))
        assertEquals(DayPhase.NIGHT, DayPhase.of(3))
        assertEquals(DayPhase.DAWN, DayPhase.of(4))
        assertEquals(DayPhase.DAWN, DayPhase.of(6))
        assertEquals(DayPhase.MORNING, DayPhase.of(7))
        assertEquals(DayPhase.MORNING, DayPhase.of(11))
        assertEquals(DayPhase.NOON, DayPhase.of(12))
        assertEquals(DayPhase.NOON, DayPhase.of(15))
        assertEquals(DayPhase.AFTERNOON, DayPhase.of(16))
        assertEquals(DayPhase.AFTERNOON, DayPhase.of(18))
        assertEquals(DayPhase.DUSK, DayPhase.of(19))
        assertEquals(DayPhase.DUSK, DayPhase.of(20))
        assertEquals(DayPhase.NIGHT, DayPhase.of(21))
        assertEquals(DayPhase.NIGHT, DayPhase.of(23))
        assertEquals(DayPhase.MORNING, DayPhase.of(32))
        assertEquals(DayPhase.NIGHT, DayPhase.of(-1))
    }

    @Test
    fun `hero gradient stops remain dark enough for white text across all phases and dynamic mode`() {
        val brightPrimary = Color(0xFFB39DFF)
        val brightSecondary = Color(0xFF4FD9C0)
        val brightTertiary = Color(0xFFFFB65C)

        for (phase in DayPhase.entries) {
            for (dark in listOf(false, true)) {
                for (isDynamic in listOf(false, true)) {
                    val tints = DayPhaseColors.compute(
                        phase = phase,
                        primary = brightPrimary,
                        secondary = brightSecondary,
                        tertiary = brightTertiary,
                        dark = dark,
                        isDynamic = isDynamic,
                    )
                    assertEquals(3, tints.auroraColors.size)
                    assertTrue(tints.heroStart.luminance() <= 0.086f)
                    assertTrue(tints.heroMid.luminance() <= 0.086f)
                    assertTrue(tints.heroEnd.luminance() <= 0.086f)
                }
            }
        }
    }
}
