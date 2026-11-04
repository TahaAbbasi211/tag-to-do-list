package com.tag.routine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.tag.routine.data.AppSettings

// ------------------------------------------------------------------ palette definitions

internal object PaletteDef {
    // Violet (default), Sky, Purple, Pink, Teal, Sunset
    data class Def(
        val primary: Color,
        val primaryDeep: Color,
        val secondary: Color,
        val secondaryDeep: Color,
        val tertiary: Color,
        val onHeroLight: Color,
        val onHeroMuted: Color,
    )

    val VIOLET = Def(
        primary = Violet,
        primaryDeep = VioletDeep,
        secondary = Mint,
        secondaryDeep = MintDeep,
        tertiary = Amber,
        onHeroLight = Color(0xFF1E1450),
        onHeroMuted = Color(0xFF383258),
    )
    val SKY = Def(
        primary = Sky,
        primaryDeep = SkyDeep,
        secondary = Mint,
        secondaryDeep = MintDeep,
        tertiary = Amber,
        onHeroLight = Color(0xFF022130),
        onHeroMuted = Color(0xFF455F6E),
    )
    val PURPLE = Def(
        primary = RoyalPurple,
        primaryDeep = RoyalPurpleDeep,
        secondary = Violet,
        secondaryDeep = VioletDeep,
        tertiary = Rose,
        onHeroLight = Color(0xFF22093C),
        onHeroMuted = Color(0xFF4F217F),
    )
    val PINK = Def(
        primary = Pink,
        primaryDeep = PinkDeep,
        secondary = Violet,
        secondaryDeep = VioletDeep,
        tertiary = Amber,
        onHeroLight = Color(0xFF330B20),
        onHeroMuted = Color(0xFF7A2C4F),
    )
    val TEAL = Def(
        primary = MintDeep,
        primaryDeep = MintDeep,
        secondary = Sky,
        secondaryDeep = SkyDeep,
        tertiary = Amber,
        onHeroLight = Color(0xFF052E2B),
        onHeroMuted = Color(0xFF3A6F6B),
    )
    val SUNSET = Def(
        primary = Coral,
        primaryDeep = Color(0xFFE65A3A),
        secondary = Amber,
        secondaryDeep = Color(0xFFD48A2E),
        tertiary = Pink,
        onHeroLight = Color(0xFF3E160A),
        onHeroMuted = Color(0xFF8A3D1D),
    )
}

// ------------------------------------------------------------------ color schemes

private fun darkScheme(def: PaletteDef.Def): androidx.compose.material3.ColorScheme =
    darkColorScheme(
        primary = def.primary,
        onPrimary = Color(0xFF15102E),
        primaryContainer = def.primary.copy(alpha = 0.24f),
        onPrimaryContainer = def.onHeroLight,
        secondary = def.secondary,
        onSecondary = Color(0xFF04241E),
        secondaryContainer = def.secondary.copy(alpha = 0.24f),
        onSecondaryContainer = Color(0xFFBFF3E8),
        tertiary = def.tertiary,
        onTertiary = Color(0xFF2C1B00),
        background = NightBg,
        onBackground = NightOn,
        surface = NightSurface,
        onSurface = NightOn,
        surfaceVariant = NightVariant,
        onSurfaceVariant = NightOnVariant,
        surfaceContainerLowest = Color(0xFF0C111C),
        surfaceContainerLow = Color(0xFF101623),
        surfaceContainer = Color(0xFF141B2A),
        surfaceContainerHigh = Color(0xFF191F31),
        surfaceContainerHighest = Color(0xFF1E2537),
        outline = NightOutline,
        outlineVariant = Color(0xFF232B3E),
        error = Coral,
        onError = Color(0xFF2B0A06),
    )

private fun lightScheme(def: PaletteDef.Def): androidx.compose.material3.ColorScheme =
    lightColorScheme(
        primary = def.primaryDeep,
        onPrimary = Color.White,
        primaryContainer = def.primary.copy(alpha = 0.17f),
        onPrimaryContainer = def.primaryDeep,
        secondary = def.secondaryDeep,
        onSecondary = Color.White,
        secondaryContainer = def.secondary.copy(alpha = 0.17f),
        onSecondaryContainer = Color(0xFF00382E),
        tertiary = Color(0xFFB77414),
        onTertiary = Color.White,
        background = DayBg,
        onBackground = DayOn,
        surface = DaySurface,
        onSurface = DayOn,
        surfaceVariant = DayVariant,
        onSurfaceVariant = DayOnVariant,
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFBFF),
        surfaceContainer = Color(0xFFF6F7FD),
        surfaceContainerHigh = Color(0xFFF1F3FB),
        surfaceContainerHighest = Color(0xFFEBEEF8),
        outline = DayOutline,
        outlineVariant = Color(0xFFE6E9F3),
        error = Color(0xFFD64535),
        onError = Color.White,
    )

// ------------------------------------------------------------------ themes

private fun brandFor(def: PaletteDef.Def, dark: Boolean): TagBrand {
    val tint = if (dark) 0.22f else 0.30f
    val bg = if (dark) NightSurface else DaySurface
    val muted = if (dark) 0.7f else 0.55f
    return TagBrand(
        heroStart = bg.copy(alpha = tint),
        heroMid = if (dark) Color(0xFF3B2E7A) else def.primaryDeep,
        heroEnd = if (dark) Color(0xFF1D3A54) else def.primary,
        ringStart = def.primary,
        ringEnd = def.secondary,
        streak = def.tertiary,
        success = def.secondary,
        priorityLow = Color(0xFF6E7B96),
        priorityNormal = def.primary,
        priorityHigh = Coral,
        chartTrack = if (dark) Color(0xFF232B3E) else Color(0xFFE3E7F3),
        gridEmpty = if (dark) Color(0xFF1A2131) else Color(0xFFE8EBF5),
        onHero = if (dark) Color(0xFFF3F1FF) else Color(0xFF1E1450),
        onHeroMuted = if (dark) Color(0xFFC3BEE8) else def.onHeroMuted,
        cardBorder = if (dark) Color(0x14FFFFFF) else Color(0x14000000),
        isDark = dark,
    )
}

@Composable
fun TagTheme(
    themeMode: Int = AppSettings.ThemeMode.SYSTEM,
    paletteKey: String = AppSettings.Palettes.VIOLET,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppSettings.ThemeMode.LIGHT -> false
        AppSettings.ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val def = paletteFor(paletteKey)
    val brand = brandFor(def, dark)
    val scheme = if (dark) darkScheme(def) else lightScheme(def)
    CompositionLocalProvider(LocalTagBrand provides brand) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TagTypography,
            shapes = TagShapes,
            content = content,
        )
    }
}

internal fun paletteFor(key: String): PaletteDef.Def =
    when (key) {
        AppSettings.Palettes.SKY -> PaletteDef.SKY
        AppSettings.Palettes.PURPLE -> PaletteDef.PURPLE
        AppSettings.Palettes.PINK -> PaletteDef.PINK
        AppSettings.Palettes.TEAL -> PaletteDef.TEAL
        AppSettings.Palettes.SUNSET -> PaletteDef.SUNSET
        else -> PaletteDef.VIOLET
    }

/** Convenience accessor for the brand palette. */
@Composable
fun tagBrand(): TagBrand = LocalTagBrand.current
