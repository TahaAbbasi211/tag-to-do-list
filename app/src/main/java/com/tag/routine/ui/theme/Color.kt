package com.tag.routine.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ------------------------------------------------------------------ palette swatches

internal val Violet = Color(0xFF7C6CFF)
internal val VioletDeep = Color(0xFF5B4BE0)
internal val VioletSoft = Color(0xFFA99BFF)

internal val Mint = Color(0xFF4FD9C0)
internal val MintDeep = Color(0xFF0F766E)

internal val Amber = Color(0xFFFFB65C)
internal val AmberDeep = Color(0xFFB45309)

internal val Coral = Color(0xFFFF7A6B)
internal val CoralDeep = Color(0xFFC2410C)

internal val Rose = Color(0xFFFF6B9D)
internal val RoseDeep = Color(0xFFBE123C)

internal val Sky = Color(0xFF4CC9F0)
internal val SkyDeep = Color(0xFF0369A1)

internal val RoyalPurple = Color(0xFF9C5BFF)
internal val RoyalPurpleDeep = Color(0xFF7A3BEF)

internal val Pink = Color(0xFFFF7AB3)
internal val PinkDeep = Color(0xFFBE185D)

// ------------------------------------------------------------------ high-contrast surface tiers

// Dark Theme Surfaces (Deep, rich slate-navy with distinct visual depth tiers)
internal val NightBg = Color(0xFF090D16)
internal val NightSurface = Color(0xFF111726)
internal val NightSurfaceLowest = Color(0xFF0C101B)
internal val NightSurfaceLow = Color(0xFF141C2E)
internal val NightSurfaceContainer = Color(0xFF182236)
internal val NightSurfaceHigh = Color(0xFF1F2B44)
internal val NightSurfaceHighest = Color(0xFF273654)
internal val NightOutline = Color(0xFF2E3E5C)
internal val NightOutlineVariant = Color(0xFF1E2B42)
internal val NightOn = Color(0xFFF1F5F9)
internal val NightOnVariant = Color(0xFFA0AEC0)

// Light Theme Surfaces (Crisp, clean, soft grey-blue with subtle elevation steps)
internal val DayBg = Color(0xFFF6F8FC)
internal val DaySurface = Color(0xFFFFFFFF)
internal val DaySurfaceLowest = Color(0xFFFFFFFF)
internal val DaySurfaceLow = Color(0xFFF8FAFC)
internal val DaySurfaceContainer = Color(0xFFF1F4F9)
internal val DaySurfaceHigh = Color(0xFFE9EDF5)
internal val DaySurfaceHighest = Color(0xFFE2E7F0)
internal val DayOutline = Color(0xFFCBD5E1)
internal val DayOutlineVariant = Color(0xFFE2E8F0)
internal val DayOn = Color(0xFF0F172A)
internal val DayOnVariant = Color(0xFF475569)

/**
 * Brand colours that sit outside the standard Material colour roles:
 * hero gradients, progress arcs, streak counters, and chart tracks.
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
    val warning: Color = streak,
    val priorityLow: Color,
    val priorityNormal: Color,
    val priorityHigh: Color,
    val chartTrack: Color,
    val gridEmpty: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val cardBorder: Color,
    val isDark: Boolean,
    val auroraColors: List<Color> = listOf(Violet, Mint, Amber),
)

val DarkBrand = TagBrand(
    heroStart = Color(0xFF2A2153),
    heroMid = Color(0xFF3B2E7A),
    heroEnd = Color(0xFF1D3A54),
    ringStart = Violet,
    ringEnd = Mint,
    streak = Amber,
    success = Mint,
    priorityLow = Color(0xFF718096),
    priorityNormal = Sky,
    priorityHigh = Coral,
    chartTrack = Color(0xFF1E2B42),
    gridEmpty = Color(0xFF182236),
    onHero = Color(0xFFF8FAFC),
    onHeroMuted = Color(0xFFCBD5E1),
    cardBorder = Color(0x1FFFFFFF),
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
    priorityLow = Color(0xFF94A3B8),
    priorityNormal = Color(0xFF0284C7),
    priorityHigh = Coral,
    chartTrack = Color(0xFFE2E8F0),
    gridEmpty = Color(0xFFF1F5F9),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFE2DCFF),
    cardBorder = Color(0x12000000),
    isDark = false,
)

val LocalTagBrand = staticCompositionLocalOf { DarkBrand }
