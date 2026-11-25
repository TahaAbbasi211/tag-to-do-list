package com.tag.routine.motion

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.tag.routine.ui.motion.DayPhase
import com.tag.routine.ui.theme.TagBrand
import com.tag.routine.ui.theme.lerpColorScheme
import com.tag.routine.ui.theme.lerpTagBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MotionThemePolicyTest {

    @Test
    fun nextBoundaryHour_returnsExpectedPhaseTransitionHour() {
        assertEquals(4, DayPhase.nextBoundaryHour(0))
        assertEquals(4, DayPhase.nextBoundaryHour(3))
        assertEquals(7, DayPhase.nextBoundaryHour(4))
        assertEquals(7, DayPhase.nextBoundaryHour(6))
        assertEquals(12, DayPhase.nextBoundaryHour(7))
        assertEquals(12, DayPhase.nextBoundaryHour(11))
        assertEquals(16, DayPhase.nextBoundaryHour(12))
        assertEquals(16, DayPhase.nextBoundaryHour(15))
        assertEquals(19, DayPhase.nextBoundaryHour(16))
        assertEquals(19, DayPhase.nextBoundaryHour(18))
        assertEquals(21, DayPhase.nextBoundaryHour(19))
        assertEquals(21, DayPhase.nextBoundaryHour(20))
        assertEquals(4, DayPhase.nextBoundaryHour(21))
        assertEquals(4, DayPhase.nextBoundaryHour(23))
    }

    @Test
    fun millisUntilNextBoundary_computesExactDelayBeforeAndAcrossMidnight() {
        // 03:59:30 -> boundary is 04:00:00 (30 seconds later)
        assertEquals(
            30_000L,
            DayPhase.millisUntilNextBoundary(LocalTime.of(3, 59, 30)),
        )
        // 23:30:00 -> boundary wraps across midnight to 04:00:00 (4.5 hours = 16,200,000 ms)
        assertEquals(
            16_200_000L,
            DayPhase.millisUntilNextBoundary(LocalTime.of(23, 30, 0)),
        )
        // 12:00:00 -> boundary is 16:00:00 (4 hours = 14,400,000 ms)
        assertEquals(
            14_400_000L,
            DayPhase.millisUntilNextBoundary(LocalTime.of(12, 0, 0)),
        )
        assertTrue(DayPhase.millisUntilNextBoundary(LocalTime.of(12, 0, 0)) > 0L)
    }

    @Test
    fun lerpColorScheme_and_lerpTagBrand_matchEndpointsAndMidpoint() {
        val startScheme = lightColorScheme(primary = Color(0xFF000000))
        val endScheme = darkColorScheme(primary = Color(0xFFFFFFFF))

        assertEquals(startScheme.primary, lerpColorScheme(startScheme, endScheme, 0f).primary)
        assertEquals(endScheme.primary, lerpColorScheme(startScheme, endScheme, 1f).primary)
        val midScheme = lerpColorScheme(startScheme, endScheme, 0.5f)
        assertEquals(lerp(startScheme.primary, endScheme.primary, 0.5f), midScheme.primary)
        assertNotEquals(startScheme.primary, midScheme.primary)
        assertNotEquals(endScheme.primary, midScheme.primary)

        val startBrand = TagBrand(
            heroStart = Color(0xFF000000),
            heroMid = Color(0xFF101010),
            heroEnd = Color(0xFF202020),
            ringStart = Color(0xFF303030),
            ringEnd = Color(0xFF404040),
            streak = Color(0xFF505050),
            success = Color(0xFF000000),
            warning = Color(0xFF606060),
            priorityLow = Color(0xFF707070),
            priorityNormal = Color(0xFF808080),
            priorityHigh = Color(0xFF909090),
            chartTrack = Color(0xFFA0A0A0),
            gridEmpty = Color(0xFFB0B0B0),
            onHero = Color.White,
            onHeroMuted = Color(0xFFEEEEEE),
            cardBorder = Color(0x1A0F172A),
            isDark = false,
            auroraColors = listOf(Color(0xFF000000), Color(0xFF202020)),
        )
        val endBrand = startBrand.copy(
            heroStart = Color(0xFFFFFFFF),
            success = Color(0xFFFFFFFF),
            isDark = true,
            auroraColors = listOf(Color(0xFFFFFFFF), Color(0xFF808080)),
        )

        val atZero = lerpTagBrand(startBrand, endBrand, 0f)
        val atOne = lerpTagBrand(startBrand, endBrand, 1f)
        val atMid = lerpTagBrand(startBrand, endBrand, 0.5f)
        assertEquals(startBrand.heroStart, atZero.heroStart)
        assertEquals(startBrand.success, atZero.success)
        assertEquals(endBrand.heroStart, atOne.heroStart)
        assertEquals(endBrand.success, atOne.success)
        assertEquals(true, atMid.isDark)
        assertEquals(lerp(startBrand.heroStart, endBrand.heroStart, 0.5f), atMid.heroStart)
        assertEquals(lerp(startBrand.success, endBrand.success, 0.5f), atMid.success)
        assertEquals(2, atMid.auroraColors.size)
        assertEquals(lerp(startBrand.auroraColors[0], endBrand.auroraColors[0], 0.5f), atMid.auroraColors[0])
    }
}
