package com.tag.routine.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
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
    dayPhase: DayPhase? = null,
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
    val animationLevel = rememberResolvedAnimationLevel(animationSetting)
    val lifecycleResumed = rememberIsLifecycleResumed()
    val resolvedDayPhase = dayPhase ?: rememberCurrentDayPhase(resumed = lifecycleResumed)
    val brand = brandFor(
        def = def,
        dark = dark,
        dayPhase = resolvedDayPhase,
        dynamicScheme = dynamicScheme,
    )

    val (animatedScheme, animatedBrand) = rememberAnimatedThemePair(
        targetScheme = scheme,
        targetBrand = brand,
        visualKey = ThemeVisualKey(
            dark = dark,
            paletteKey = paletteKey,
            dynamicColor = dynamicColor,
            dayPhase = resolvedDayPhase,
            primary = scheme.primary,
            secondary = scheme.secondary,
            background = scheme.background,
            surface = scheme.surface,
        ),
        level = animationLevel,
    )

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

private data class ThemeVisualKey(
    val dark: Boolean,
    val paletteKey: String,
    val dynamicColor: Boolean,
    val dayPhase: DayPhase,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
)

/**
 * Keeps [DayPhase] accurate across long foreground sessions by sleeping until the next
 * boundary hour only while the host lifecycle is actively `RESUMED`.
 */
@Composable
private fun rememberCurrentDayPhase(resumed: Boolean): DayPhase {
    var phase by remember { mutableStateOf(DayPhase.of(LocalTime.now().hour)) }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            val now = LocalTime.now()
            val current = DayPhase.of(now.hour)
            if (phase != current) {
                phase = current
            }
            delay(DayPhase.millisUntilNextBoundary(now) + 250L)
        }
    }
    return phase
}

/**
 * Drives all theme/palette/phase color transitions through a single Float [Animatable]
 * instead of dozens of concurrent per-token `animateColorAsState` instances.
 */
@Composable
private fun rememberAnimatedThemePair(
    targetScheme: ColorScheme,
    targetBrand: TagBrand,
    visualKey: ThemeVisualKey,
    level: AnimationLevel,
): Pair<ColorScheme, TagBrand> {
    val targetPair = targetScheme to targetBrand
    if (level.isOff) return targetPair

    var lastKey by remember { mutableStateOf(visualKey) }
    var fromPair by remember { mutableStateOf(targetPair) }
    var toPair by remember { mutableStateOf(targetPair) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(visualKey, level) {
        if (visualKey == lastKey) {
            toPair = targetPair
            return@LaunchedEffect
        }
        val currentFraction = progress.value.coerceIn(0f, 1f)
        val snapshotStart = lerpColorScheme(fromPair.first, toPair.first, currentFraction) to
            lerpTagBrand(fromPair.second, toPair.second, currentFraction)
        lastKey = visualKey
        fromPair = snapshotStart
        toPair = targetPair
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = if (level == AnimationLevel.FULL) {
                TagMotion.standardTween()
            } else {
                TagMotion.quickTween()
            },
        )
        fromPair = targetPair
    }

    val f = progress.value.coerceIn(0f, 1f)
    return if (f >= 0.999f && visualKey == lastKey) {
        targetPair
    } else {
        lerpColorScheme(fromPair.first, toPair.first, f) to
            lerpTagBrand(fromPair.second, toPair.second, f)
    }
}

internal fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return start
    if (f >= 1f) return stop
    return stop.copy(
        primary = lerp(start.primary, stop.primary, f),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, f),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, f),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, f),
        secondary = lerp(start.secondary, stop.secondary, f),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, f),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, f),
        onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, f),
        tertiary = lerp(start.tertiary, stop.tertiary, f),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, f),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, f),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, f),
        background = lerp(start.background, stop.background, f),
        onBackground = lerp(start.onBackground, stop.onBackground, f),
        surface = lerp(start.surface, stop.surface, f),
        onSurface = lerp(start.onSurface, stop.onSurface, f),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, f),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, f),
        surfaceContainerLowest = lerp(start.surfaceContainerLowest, stop.surfaceContainerLowest, f),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, f),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, f),
        surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, f),
        surfaceContainerHighest = lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, f),
        outline = lerp(start.outline, stop.outline, f),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, f),
        error = lerp(start.error, stop.error, f),
        onError = lerp(start.onError, stop.onError, f),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, f),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, f),
    )
}

internal fun lerpTagBrand(start: TagBrand, stop: TagBrand, fraction: Float): TagBrand {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return start
    if (f >= 1f) return stop
    return stop.copy(
        heroStart = lerp(start.heroStart, stop.heroStart, f),
        heroMid = lerp(start.heroMid, stop.heroMid, f),
        heroEnd = lerp(start.heroEnd, stop.heroEnd, f),
        ringStart = lerp(start.ringStart, stop.ringStart, f),
        ringEnd = lerp(start.ringEnd, stop.ringEnd, f),
        streak = lerp(start.streak, stop.streak, f),
        success = lerp(start.success, stop.success, f),
        warning = lerp(start.warning, stop.warning, f),
        priorityNormal = lerp(start.priorityNormal, stop.priorityNormal, f),
        chartTrack = lerp(start.chartTrack, stop.chartTrack, f),
        gridEmpty = lerp(start.gridEmpty, stop.gridEmpty, f),
        cardBorder = lerp(start.cardBorder, stop.cardBorder, f),
        auroraColors = List(stop.auroraColors.size) { idx ->
            val a = start.auroraColors.getOrElse(idx) { stop.auroraColors[idx] }
            lerp(a, stop.auroraColors[idx], f)
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
