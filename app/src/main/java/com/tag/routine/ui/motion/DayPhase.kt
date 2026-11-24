package com.tag.routine.ui.motion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Time-of-day visual phase that subtly shifts the living hero & aurora color temperature
 * while preserving the core purple/indigo brand identity and text contrast.
 */
enum class DayPhase {
    DAWN,
    MORNING,
    NOON,
    AFTERNOON,
    DUSK,
    NIGHT;

    companion object {
        /** Maps a 24-hour clock [hour] (normalized modulo 24) to its [DayPhase]. */
        fun of(hour: Int): DayPhase {
            val h = ((hour % 24) + 24) % 24
            return when (h) {
                in 4..6 -> DAWN
                in 7..11 -> MORNING
                in 12..15 -> NOON
                in 16..18 -> AFTERNOON
                in 19..20 -> DUSK
                else -> NIGHT
            }
        }
    }
}

data class PhaseThemeTints(
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val auroraColors: List<Color>,
)

/**
 * Pure color-blending helper for [DayPhase] hero gradients and aurora blobs.
 * Guarantees dark hero stops remain deep enough (`luminance <= 0.085f`) for WCAG AA
 * white-on-hero readability regardless of hour, palette, or Dynamic Color.
 */
object DayPhaseColors {

    private val HeroBase = Color(0xFF0E1426)

    fun compute(
        phase: DayPhase,
        primary: Color,
        secondary: Color,
        tertiary: Color,
        dark: Boolean,
        isDynamic: Boolean = false,
    ): PhaseThemeTints {
        val (baseWarm, baseCool, blendStart, blendEnd) = when (phase) {
            DayPhase.DAWN -> Quad(Color(0xFFFB7185), Color(0xFF6366F1), 0.18f, 0.22f)
            DayPhase.MORNING -> Quad(Color(0xFF8B5CF6), Color(0xFF38BDF8), 0.14f, 0.20f)
            DayPhase.NOON -> Quad(Color(0xFF6366F1), Color(0xFF2DD4BF), 0.10f, 0.18f)
            DayPhase.AFTERNOON -> Quad(Color(0xFF7C3AED), Color(0xFFF59E0B), 0.14f, 0.16f)
            DayPhase.DUSK -> Quad(Color(0xFFC084FC), Color(0xFFF43F5E), 0.20f, 0.24f)
            DayPhase.NIGHT -> Quad(Color(0xFF312E81), Color(0xFF0F172A), 0.26f, 0.30f)
        }

        // When Dynamic Color is active, derive phase warmth/coolness directly from Material You roles.
        val warmAccent = if (isDynamic) {
            when (phase) {
                DayPhase.DAWN, DayPhase.AFTERNOON, DayPhase.DUSK -> lerp(tertiary, primary, 0.35f)
                DayPhase.MORNING, DayPhase.NOON -> lerp(primary, tertiary, 0.22f)
                DayPhase.NIGHT -> lerp(primary, Color(0xFF0F172A), 0.40f)
            }
        } else {
            baseWarm
        }
        val coolAccent = if (isDynamic) {
            when (phase) {
                DayPhase.NIGHT -> lerp(secondary, Color(0xFF0B1020), 0.45f)
                else -> lerp(secondary, primary, 0.28f)
            }
        } else {
            baseCool
        }

        val tintedStart = lerp(primary, warmAccent, blendStart)
        val tintedMid = lerp(primary, coolAccent, blendStart * 0.8f)
        val tintedEnd = lerp(secondary, coolAccent, blendEnd)

        val heroStart = ensureDarkHeroStop(lerp(tintedStart, HeroBase, if (dark) 0.54f else 0.40f))
        val heroMid = ensureDarkHeroStop(lerp(tintedMid, HeroBase, if (dark) 0.44f else 0.32f))
        val heroEnd = ensureDarkHeroStop(lerp(tintedEnd, Color(0xFF0A1D2C), if (dark) 0.58f else 0.44f))

        val blobAlphaColor1 = lerp(primary, warmAccent, 0.28f)
        val blobAlphaColor2 = lerp(secondary, coolAccent, 0.24f)
        val blobAlphaColor3 = lerp(tertiary, warmAccent, 0.25f)

        return PhaseThemeTints(
            heroStart = heroStart,
            heroMid = heroMid,
            heroEnd = heroEnd,
            auroraColors = listOf(blobAlphaColor1, blobAlphaColor2, blobAlphaColor3),
        )
    }

    fun ensureDarkHeroStop(color: Color, maxLuminance: Float = 0.085f): Color {
        var result = color
        var step = 0
        while (result.luminance() > maxLuminance && step < 12) {
            result = lerp(result, Color(0xFF0B1020), 0.25f)
            step++
        }
        return result
    }

    private data class Quad(
        val warm: Color,
        val cool: Color,
        val startBlend: Float,
        val endBlend: Float,
    )
}
