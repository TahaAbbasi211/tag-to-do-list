package com.tag.routine.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tag.routine.data.AnimationSetting
import com.tag.routine.data.AppSettings
import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.DayPhase
import com.tag.routine.ui.motion.DayPhaseColors
import com.tag.routine.ui.motion.LocalAmbientAnimationsActive
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.LocalHapticsEnabled
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.rememberIsLifecycleResumed
import com.tag.routine.ui.motion.rememberResolvedAnimationLevel
import java.time.LocalTime

// ------------------------------------------------------------------ palette definitions

internal object PaletteDef {
    // Each palette defines 1 clear primary color, 1 accent color (secondary), and 1 tertiary accent
    data class Def(
        val primary: Color,
        val primaryDeep: Color,
        val secondary: Color,        // Accent color
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
        secondary = Pink,
        secondaryDeep = PinkDeep,
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
        primaryDeep = CoralDeep,
        secondary = Amber,
        secondaryDeep = AmberDeep,
        tertiary = Pink,
        onHeroLight = Color(0xFF3E160A),
        onHeroMuted = Color(0xFF8A3D1D),
    )
}

// ------------------------------------------------------------------ color schemes with depth surfaces

private fun darkScheme(def: PaletteDef.Def): ColorScheme =
    darkColorScheme(
        primary = def.primary,
        onPrimary = Color(0xFF0B091B),
        primaryContainer = def.primary.copy(alpha = 0.22f),
        onPrimaryContainer = Color(0xFFEDE9FE),
        secondary = def.secondary,
        onSecondary = Color(0xFF021B17),
        secondaryContainer = def.secondary.copy(alpha = 0.20f),
        onSecondaryContainer = Color(0xFFCCFBF1),
        tertiary = def.tertiary,
        onTertiary = Color(0xFF241600),
        tertiaryContainer = def.tertiary.copy(alpha = 0.20f),
        onTertiaryContainer = Color(0xFFFEF3C7),
        background = NightBg,
        onBackground = NightOn,
        surface = NightSurface,
        onSurface = NightOn,
        surfaceVariant = NightSurfaceHigh,
        onSurfaceVariant = NightOnVariant,
        surfaceContainerLowest = NightSurfaceLowest,
        surfaceContainerLow = NightSurfaceLow,
        surfaceContainer = NightSurfaceContainer,
        surfaceContainerHigh = NightSurfaceHigh,
        surfaceContainerHighest = NightSurfaceHighest,
        outline = NightOutline,
        outlineVariant = NightOutlineVariant,
        error = Coral,
        onError = Color(0xFF2B0A06),
        errorContainer = Coral.copy(alpha = 0.22f),
        onErrorContainer = Color(0xFFFFD4D0),
    )

private fun lightScheme(def: PaletteDef.Def): ColorScheme =
    lightColorScheme(
        primary = def.primaryDeep,
        onPrimary = Color.White,
        primaryContainer = def.primary.copy(alpha = 0.14f),
        onPrimaryContainer = def.primaryDeep,
        secondary = def.secondaryDeep,
        onSecondary = Color.White,
        secondaryContainer = def.secondary.copy(alpha = 0.14f),
        onSecondaryContainer = Color(0xFF064E3B),
        tertiary = Color(0xFFB45309),
        onTertiary = Color.White,
        tertiaryContainer = Amber.copy(alpha = 0.18f),
        onTertiaryContainer = Color(0xFF451A03),
        background = DayBg,
        onBackground = DayOn,
        surface = DaySurface,
        onSurface = DayOn,
        surfaceVariant = DaySurfaceHigh,
        onSurfaceVariant = DayOnVariant,
        surfaceContainerLowest = DaySurfaceLowest,
        surfaceContainerLow = DaySurfaceLow,
        surfaceContainer = DaySurfaceContainer,
        surfaceContainerHigh = DaySurfaceHigh,
        surfaceContainerHighest = DaySurfaceHighest,
        outline = DayOutline,
        outlineVariant = DayOutlineVariant,
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
    )

// ------------------------------------------------------------------ brand & dynamic adapter

private fun brandFor(
    def: PaletteDef.Def,
    dark: Boolean,
    dayPhase: DayPhase = DayPhase.NOON,
    dynamicScheme: ColorScheme? = null,
): TagBrand {
    val primary = dynamicScheme?.primary ?: (if (dark) def.primary else def.primaryDeep)
    val secondary = dynamicScheme?.secondary ?: (if (dark) def.secondary else def.secondaryDeep)
    val tertiary = dynamicScheme?.tertiary ?: def.tertiary

    val phaseTints = DayPhaseColors.compute(
        phase = dayPhase,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        dark = dark,
        isDynamic = dynamicScheme != null,
    )

    return TagBrand(
        heroStart = phaseTints.heroStart,
        heroMid = phaseTints.heroMid,
        heroEnd = phaseTints.heroEnd,
        ringStart = lerp(primary, Color.White, 0.45f),
        ringEnd = lerp(secondary, Color.White, 0.28f),
        streak = tertiary,
        success = secondary,
        warning = tertiary,
        priorityLow = Color(0xFF718096),
        priorityNormal = primary,
        priorityHigh = Coral,
        chartTrack = if (dark) NightOutlineVariant else Color(0xFFE2E8F0),
        gridEmpty = if (dark) NightSurfaceContainer else Color(0xFFF1F5F9),
        onHero = Color(0xFFFFFFFF),
        onHeroMuted = Color(0xFFE2E8F0),
        cardBorder = if (dark) Color(0x1FFFFFFF) else Color(0x1A0F172A),
        isDark = dark,
        auroraColors = phaseTints.auroraColors,
    )
}

// The SDK_INT >= 31 guard below is real; lint's NewApi analysis does not propagate it
// through these Compose Material3 color helpers, so suppress only this false positive.
@SuppressLint("NewApi")
private fun chooseScheme(
    context: Context,
    dark: Boolean,
    dynamicColor: Boolean,
    def: PaletteDef.Def,
): ColorScheme {
    val brand = if (dark) darkScheme(def) else lightScheme(def)
    if (Build.VERSION.SDK_INT >= 31) {
        if (dynamicColor) {
            return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
    }
    return brand
}

/**
 * Root theme composable supporting Light, Dark, custom brand palettes,
 * Material You Dynamic Color (on Android 12+), and the unified spacing system.
 */
@Composable
fun TagTheme(
    themeMode: Int = AppSettings.ThemeMode.SYSTEM,
    paletteKey: String = AppSettings.Palettes.VIOLET,
    dynamicColor: Boolean = false,
    animationSetting: AnimationSetting = AnimationSetting.AUTO,
    hapticsEnabled: Boolean = true,
    dayPhase: DayPhase = DayPhase.of(LocalTime.now().hour),
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppSettings.ThemeMode.LIGHT -> false
        AppSettings.ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val def = paletteFor(paletteKey)

    val scheme = chooseScheme(context, dark, dynamicColor, def)

    val dynamicScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) scheme else null
    val brand = brandFor(def = def, dark = dark, dayPhase = dayPhase, dynamicScheme = dynamicScheme)
    val animationLevel = rememberResolvedAnimationLevel(animationSetting)
    val lifecycleResumed = rememberIsLifecycleResumed()

    val animatedScheme = animateTagColorScheme(scheme, animationLevel)
    val animatedBrand = animateTagBrand(brand, animationLevel)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: context.findActivity()
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalTagBrand provides animatedBrand,
        LocalAppSpacing provides AppSpacing(),
        LocalAnimationLevel provides animationLevel,
        LocalHapticsEnabled provides hapticsEnabled,
        LocalAmbientAnimationsActive provides (animationLevel.allowsAmbient && lifecycleResumed),
    ) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = TagTypography,
            shapes = TagShapes,
            content = content,
        )
    }
}

@Composable
private fun themeColor(target: Color, level: AnimationLevel, label: String): Color {
    if (level.isOff) return target
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = if (level == AnimationLevel.FULL) TagMotion.standardTween() else TagMotion.quickTween(),
        label = label,
    )
    return animated
}

@Composable
private fun animateTagColorScheme(target: ColorScheme, level: AnimationLevel): ColorScheme {
    if (level.isOff) return target
    return target.copy(
        primary = themeColor(target.primary, level, "cs-primary"),
        onPrimary = themeColor(target.onPrimary, level, "cs-onPrimary"),
        primaryContainer = themeColor(target.primaryContainer, level, "cs-primaryContainer"),
        onPrimaryContainer = themeColor(target.onPrimaryContainer, level, "cs-onPrimaryContainer"),
        secondary = themeColor(target.secondary, level, "cs-secondary"),
        onSecondary = themeColor(target.onSecondary, level, "cs-onSecondary"),
        secondaryContainer = themeColor(target.secondaryContainer, level, "cs-secondaryContainer"),
        onSecondaryContainer = themeColor(target.onSecondaryContainer, level, "cs-onSecondaryContainer"),
        tertiary = themeColor(target.tertiary, level, "cs-tertiary"),
        onTertiary = themeColor(target.onTertiary, level, "cs-onTertiary"),
        tertiaryContainer = themeColor(target.tertiaryContainer, level, "cs-tertiaryContainer"),
        onTertiaryContainer = themeColor(target.onTertiaryContainer, level, "cs-onTertiaryContainer"),
        background = themeColor(target.background, level, "cs-background"),
        onBackground = themeColor(target.onBackground, level, "cs-onBackground"),
        surface = themeColor(target.surface, level, "cs-surface"),
        onSurface = themeColor(target.onSurface, level, "cs-onSurface"),
        surfaceVariant = themeColor(target.surfaceVariant, level, "cs-surfaceVariant"),
        onSurfaceVariant = themeColor(target.onSurfaceVariant, level, "cs-onSurfaceVariant"),
        surfaceContainerLowest = themeColor(target.surfaceContainerLowest, level, "cs-scl"),
        surfaceContainerLow = themeColor(target.surfaceContainerLow, level, "cs-sclow"),
        surfaceContainer = themeColor(target.surfaceContainer, level, "cs-sc"),
        surfaceContainerHigh = themeColor(target.surfaceContainerHigh, level, "cs-sch"),
        surfaceContainerHighest = themeColor(target.surfaceContainerHighest, level, "cs-schigh"),
        outline = themeColor(target.outline, level, "cs-outline"),
        outlineVariant = themeColor(target.outlineVariant, level, "cs-outlineVariant"),
        error = themeColor(target.error, level, "cs-error"),
        onError = themeColor(target.onError, level, "cs-onError"),
        errorContainer = themeColor(target.errorContainer, level, "cs-errorContainer"),
        onErrorContainer = themeColor(target.onErrorContainer, level, "cs-onErrorContainer"),
    )
}

@Composable
private fun animateTagBrand(target: TagBrand, level: AnimationLevel): TagBrand {
    if (level.isOff) return target
    return target.copy(
        heroStart = themeColor(target.heroStart, level, "tb-heroStart"),
        heroMid = themeColor(target.heroMid, level, "tb-heroMid"),
        heroEnd = themeColor(target.heroEnd, level, "tb-heroEnd"),
        ringStart = themeColor(target.ringStart, level, "tb-ringStart"),
        ringEnd = themeColor(target.ringEnd, level, "tb-ringEnd"),
        streak = themeColor(target.streak, level, "tb-streak"),
        success = themeColor(target.success, level, "tb-success"),
        warning = themeColor(target.warning, level, "tb-warning"),
        priorityNormal = themeColor(target.priorityNormal, level, "tb-pNorm"),
        chartTrack = themeColor(target.chartTrack, level, "tb-chartTrack"),
        gridEmpty = themeColor(target.gridEmpty, level, "tb-gridEmpty"),
        cardBorder = themeColor(target.cardBorder, level, "tb-cardBorder"),
        auroraColors = target.auroraColors.mapIndexed { idx, color ->
            themeColor(color, level, "tb-aurora-$idx")
        },
    )
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
