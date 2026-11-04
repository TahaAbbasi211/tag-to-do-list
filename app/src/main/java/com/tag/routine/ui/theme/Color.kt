package com.tag.routine.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ------------------------------------------------------------------ palette

internal val Violet = Color(0xFF7C6CFF)
internal val VioletDeep = Color(0xFF5B4BE0)
internal val VioletSoft = Color(0xFFA99BFF)
internal val Mint = Color(0xFF4FD9C0)
internal val MintDeep = Color(0xFF12A88C)
internal val Amber = Color(0xFFFFB65C)
internal val Coral = Color(0xFFFF7A6B)
internal val Rose = Color(0xFFFF6B9D)
internal val Sky = Color(0xFF4CC9F0)
internal val SkyDeep = Color(0xFF1A9FD8)
internal val RoyalPurple = Color(0xFF9C5BFF)
internal val RoyalPurpleDeep = Color(0xFF7A3BEF)
internal val Pink = Color(0xFFFF7AB3)
internal val PinkDeep = Color(0xFFF0558C)

internal val NightBg = Color(0xFF0A0E1A)
internal val NightSurface = Color(0xFF121826)
internal val NightSurfaceHigh = Color(0xFF19203250)
internal val NightVariant = Color(0xFF1B2233)
internal val NightOutline = Color(0xFF2A3348)
internal val NightOn = Color(0xFFE9EDF8)
internal val NightOnVariant = Color(0xFF97A3BC)

internal val DayBg = Color(0xFFF4F5FB)
internal val DaySurface = Color(0xFFFFFFFF)
internal val DayVariant = Color(0xFFEAEDF7)
internal val DayOutline = Color(0xFFD9DEEC)
internal val DayOn = Color(0xFF101427)
internal val DayOnVariant = Color(0xFF59637A)

/**
 * Brand colours that sit outside the Material colour roles: gradients, priority
 * markers, streak accents and chart ramps.
 */
@Immutable
data class TagBrand(
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val ringStart: Color,
    val ringEnd: Color,
    val streak: Color,
    val success: Color,
    val priorityLow: Color,
    val priorityNormal: Color,
    val priorityHigh: Color,
    val chartTrack: Color,
    val gridEmpty: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val cardBorder: Color,
    val isDark: Boolean,
)

val DarkBrand = TagBrand(
    heroStart = Color(0xFF2A2153),
    heroMid = Color(0xFF3B2E7A),
    heroEnd = Color(0xFF1D3A54),
    ringStart = Violet,
    ringEnd = Mint,
    streak = Amber,
    success = Mint,
    priorityLow = Color(0xFF6E7B96),
    priorityNormal = Sky,
    priorityHigh = Coral,
    chartTrack = Color(0xFF232B3E),
    gridEmpty = Color(0xFF1A2131),
    onHero = Color(0xFFF3F1FF),
    onHeroMuted = Color(0xFFC3BEE8),
    cardBorder = Color(0x14FFFFFF),
    isDark = true,
)

val LightBrand = TagBrand(
    heroStart = Color(0xFF6C5CFF),
    heroMid = Color(0xFF8B6CFF),
    heroEnd = Color(0xFF3FB9C9),
    ringStart = VioletDeep,
    ringEnd = MintDeep,
    streak = Color(0xFFEB9E2F),
    success = MintDeep,
    priorityLow = Color(0xFF97A1B8),
    priorityNormal = Color(0xFF2E9BD6),
    priorityHigh = Coral,
    chartTrack = Color(0xFFE3E7F3),
    gridEmpty = Color(0xFFE8EBF5),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFE2DCFF),
    cardBorder = Color(0x14000000),
    isDark = false,
)

val LocalTagBrand = staticCompositionLocalOf { DarkBrand }
