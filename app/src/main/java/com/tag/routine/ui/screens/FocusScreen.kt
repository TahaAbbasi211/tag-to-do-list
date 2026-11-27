package com.tag.routine.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.FocusPhase
import com.tag.routine.data.FocusStats
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.FocusState
import com.tag.routine.ui.components.AnimatedStatTile
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.AppChip
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.LargeScreenFrame
import com.tag.routine.ui.components.MetaPill
import com.tag.routine.ui.components.RoutineIconMap
import com.tag.routine.ui.components.ScreenHeader
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.StatTilePair
import com.tag.routine.ui.motion.AnimatedRing
import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.AuroraBackground
import com.tag.routine.ui.motion.ConfettiBurst
import com.tag.routine.ui.motion.DigitRoll
import com.tag.routine.ui.motion.FocusMotionLogic
import com.tag.routine.ui.motion.HapticKind
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.SparkleBurst
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.breathing
import com.tag.routine.ui.motion.glowPulse
import com.tag.routine.ui.motion.pressScale
import com.tag.routine.ui.motion.rememberTagHaptics
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.util.Locale

/**
 * Dedicated Focus section: pick a routine, choose any duration, run the timer
 * (with optional distraction-free Calm Mode), and review focus history.
 */
@Composable
fun FocusScreen(
    routines: List<RoutineEntity>,
    focus: FocusState,
    stats: FocusStats,
    defaultMinutes: Int,
    calendarType: CalendarType = CalendarType.GREGORIAN,
    calmMode: Boolean = false,
    breakModeEnabled: Boolean = false,
    longBreakInterval: Int = AppConstants.DEFAULT_LONG_BREAK_INTERVAL,
    onToggleCalmMode: () -> Unit = {},
    onStart: (RoutineEntity?, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipBreak: () -> Unit = {},
    onFinish: () -> Unit,
) {
    val activeCalmMode = FocusMotionLogic.resolveCalmMode(
        requestedCalm = calmMode,
        hasActiveSession = focus.active,
    )
    val level = LocalAnimationLevel.current

    AnimatedContent(
        targetState = activeCalmMode,
        transitionSpec = {
            when {
                level.isOff -> ContentTransform(
                    targetContentEnter = fadeIn(TagMotion.instantTween(0)),
                    initialContentExit = fadeOut(TagMotion.instantTween(0)),
                )
                level == AnimationLevel.REDUCED -> ContentTransform(
                    targetContentEnter = fadeIn(TagMotion.quickTween()),
                    initialContentExit = fadeOut(TagMotion.quickTween()),
                )
                else -> ContentTransform(
                    targetContentEnter = fadeIn(TagMotion.standardTween()) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = TagMotion.standardTween(),
                    ),
                    initialContentExit = fadeOut(TagMotion.quickTween()),
                )
            }
        },
        label = "focus-calm-transition",
    ) { inCalm ->
        if (inCalm && focus.active) {
            FocusCalmSurface(
                focus = focus,
                onExitCalmMode = onToggleCalmMode,
            )
        } else {
            LargeScreenFrame {
                FocusContent(
                    routines = routines,
                    focus = focus,
                    stats = stats,
                    defaultMinutes = defaultMinutes,
                    calendarType = calendarType,
                    breakModeEnabled = breakModeEnabled,
                    longBreakInterval = longBreakInterval,
                    onEnterCalmMode = onToggleCalmMode,
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onSkipBreak = onSkipBreak,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun focusPhaseLabel(phase: FocusPhase): String = when (phase) {
    FocusPhase.FOCUS -> stringResource(R.string.focus_phase_focus)
    FocusPhase.SHORT_BREAK -> stringResource(R.string.focus_phase_short_break)
    FocusPhase.LONG_BREAK -> stringResource(R.string.focus_phase_long_break)
}

@Composable
private fun FocusCalmSurface(
    focus: FocusState,
    onExitCalmMode: () -> Unit,
) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val fontScale = LocalDensity.current.fontScale
    val ringSize = if (fontScale > 1.4f) 292.dp else 252.dp

    AuroraBackground(
        colors = if (focus.isBreak) {
            listOf(Color(0xFF2ED3B7), Color(0xFF4FD9C0), Color(0xFF38BDF8))
        } else {
            brand.auroraColors
        },
        baseColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        LargeScreenFrame {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xl),
                ) {
                    FocusCountdownRing(
                        focus = focus,
                        ringSize = ringSize,
                        strokeWidth = 18.dp,
                        appLocale = appLocale,
                        calmMode = true,
                        onToggleCalmMode = onExitCalmMode,
                        showPhaseLabel = focus.isBreak,
                    )
                    Text(
                        text = stringResource(R.string.focus_calm_mode_exit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusContent(
    routines: List<RoutineEntity>,
    focus: FocusState,
    stats: FocusStats,
    defaultMinutes: Int,
    calendarType: CalendarType = CalendarType.GREGORIAN,
    breakModeEnabled: Boolean,
    longBreakInterval: Int,
    onEnterCalmMode: () -> Unit,
    onStart: (RoutineEntity?, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipBreak: () -> Unit,
    onFinish: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val brand = tagBrand()
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var minutesText by remember { mutableStateOf(defaultMinutes.toString()) }

    // Keep the custom field in step with the persisted default, but never clobber
    // what the user is currently typing.
    LaunchedEffect(defaultMinutes) {
        if (!focus.active) minutesText = defaultMinutes.toString()
    }

    // Keep a valid selection even after the chosen routine is deleted or archived.
    val selected = routines.firstOrNull { it.id == selectedId }
    LaunchedEffect(routines.map { it.id }) {
        if (selectedId != null && routines.none { it.id == selectedId }) selectedId = null
    }

    // One-time celebration when a focus session reaches natural completion.
    val latestCompletedEndedAt = remember(stats.recent) {
        stats.recent.firstOrNull { it.completed }?.endedAt
    }
    var hadActiveSession by rememberSaveable { mutableStateOf(false) }
    var baselineCompletedEndedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastCelebratedEndedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var celebrateActive by remember { mutableStateOf(false) }
    var celebrateSeed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(focus.active, focus.phase, latestCompletedEndedAt) {
        // A break means the focus session just ended, even though the timer is still
        // "active"; celebrate the completed focus before the break UI takes over.
        if (focus.active && !focus.isBreak) {
            if (!hadActiveSession) {
                baselineCompletedEndedAt = latestCompletedEndedAt
                hadActiveSession = true
            }
        } else {
            val triggerTs = FocusMotionLogic.detectCelebrationTimestamp(
                hadActiveSession = hadActiveSession,
                baselineCompletedEndedAt = baselineCompletedEndedAt,
                latestCompletedEndedAt = latestCompletedEndedAt,
                lastCelebratedEndedAt = lastCelebratedEndedAt,
            )
            if (triggerTs != null) {
                lastCelebratedEndedAt = triggerTs
                hadActiveSession = false
                celebrateSeed = triggerTs
                celebrateActive = true
                haptics(HapticKind.CELEBRATE)
            } else if (!hadActiveSession) {
                baselineCompletedEndedAt = latestCompletedEndedAt
            }
        }
    }

    LaunchedEffect(celebrateActive) {
        if (celebrateActive) {
            delay(3_600)
            celebrateActive = false
        }
    }

    val parsedMinutes = minutesText.toIntOrNull()
    val validMinutes = parsedMinutes != null &&
        parsedMinutes in AppConstants.MIN_FOCUS_MINUTES..AppConstants.MAX_FOCUS_MINUTES
    val effectiveMinutes = if (validMinutes) {
        parsedMinutes!!
    } else {
        AppConstants.clampFocusMinutes(parsedMinutes ?: defaultMinutes)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.lg,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "focus-header") {
                ScreenHeader(
                    title = stringResource(R.string.focus_title),
                    subtitle = stringResource(R.string.focus_subtitle),
                )
            }

            item(key = "focus-celebration-banner") {
                AnimatedVisibility(
                    visible = celebrateActive,
                    enter = if (level.isOff) {
                        fadeIn(TagMotion.instantTween(0))
                    } else {
                        expandVertically(TagMotion.snappySpring()) + fadeIn(TagMotion.quickTween())
                    },
                    exit = if (level.isOff) {
                        fadeOut(TagMotion.instantTween(0))
                    } else {
                        shrinkVertically(TagMotion.quickTween()) + fadeOut(TagMotion.quickTween())
                    },
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(18.dp),
                        color = brand.success.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, brand.success.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = brand.success,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = stringResource(R.string.focus_session_completed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (focus.active) {
                item(key = "focus-running-session") {
                    RunningSession(
                        focus = focus,
                        appLocale = appLocale,
                        breakModeEnabled = breakModeEnabled,
                        longBreakInterval = longBreakInterval,
                        onToggleCalmMode = onEnterCalmMode,
                        onPause = onPause,
                        onResume = onResume,
                        onSkipBreak = onSkipBreak,
                        onFinish = {
                            hadActiveSession = false
                            onFinish()
                        },
                    )
                }
            } else {
                item(key = "focus-setup-card") {
                    FocusSetupCard(
                        routines = routines,
                        selectedId = selectedId,
                        selectedRoutine = selected,
                        onSelect = { selectedId = it },
                        minutesText = minutesText,
                        onMinutesText = { minutesText = it.filter { ch -> ch.isDigit() }.take(3) },
                        validMinutes = validMinutes,
                        effectiveMinutes = effectiveMinutes,
                        appLocale = appLocale,
                        onStart = { onStart(selected, effectiveMinutes) },
                    )
                }
            }

            item(key = "focus-overview") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    SectionHeader(title = stringResource(R.string.focus_overview))
                    FocusStatsGrid(stats = stats, appLocale = appLocale)
                }
            }

            if (stats.recent.isNotEmpty()) {
                item(key = "focus-history") {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    ) {
                        Column(modifier = Modifier.padding(spacing.lg)) {
                            SectionHeader(title = stringResource(R.string.focus_history))
                            Spacer(Modifier.height(spacing.md))
                            stats.recent.forEachIndexed { index, row ->
                                FocusHistoryRow(
                                    title = row.routineTitle
                                        ?: stringResource(R.string.focus_history_untitled),
                                    minutes = row.minutes,
                                    completed = row.completed,
                                    endedAt = row.endedAt,
                                    appLocale = appLocale,
                                    calendarType = calendarType,
                                )
                                if (index < stats.recent.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = spacing.sm),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "focus-history-empty") {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    ) {
                        EmptyState(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp),
                                )
                            },
                            title = stringResource(R.string.focus_history),
                            message = stringResource(R.string.focus_history_empty),
                        )
                    }
                }
            }
        }

        // One-shot particle celebration (strictly capped and hidden from TalkBack).
        SparkleBurst(
            trigger = celebrateActive,
            seed = celebrateSeed + 1L,
            modifier = Modifier.fillMaxSize(),
        )
        ConfettiBurst(
            trigger = celebrateActive,
            seed = celebrateSeed,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Breathing gradient countdown ring with cool->warm color shift, last-minute pulse, DigitRoll, and tap-to-calm. */
@Composable
private fun FocusCountdownRing(
    focus: FocusState,
    ringSize: Dp,
    strokeWidth: Dp,
    appLocale: Locale,
    calmMode: Boolean,
    onToggleCalmMode: () -> Unit,
    showPhaseLabel: Boolean = true,
) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val haptics = rememberTagHaptics()

    val warmth = FocusMotionLogic.warmthFraction(focus.progress)
    val coolStart = if (brand.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val coolEnd = if (brand.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val warmStart = if (brand.isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val warmEnd = if (brand.isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
    // Breaks stay calm and green/teal instead of heating up as they near the end.
    val ringStart = if (focus.isBreak) {
        if (brand.isDark) Color(0xFF2ED3B7) else Color(0xFF0F9D8A)
    } else {
        lerp(coolStart, warmStart, warmth)
    }
    val ringEnd = if (focus.isBreak) {
        if (brand.isDark) Color(0xFF4FD9C0) else Color(0xFF2ED3B7)
    } else {
        lerp(coolEnd, warmEnd, warmth)
    }

    val isLastMinute = !focus.isBreak && FocusMotionLogic.isLastMinute(
        remainingSeconds = focus.remainingSeconds,
        running = focus.running,
        totalSeconds = focus.totalSeconds,
    )

    val mins = focus.remainingSeconds / 60
    val secs = focus.remainingSeconds % 60
    val formattedClock = Dates.formatDigits("%02d:%02d".format(Locale.ROOT, mins, secs), appLocale)
    val phaseText = focusPhaseLabel(focus.phase)
    val a11yDescription = Dates.formatDigits(
        phaseText + ", " + stringResource(R.string.a11y_focus_remaining) + " " +
            "%02d:%02d".format(Locale.ROOT, mins, secs),
        appLocale,
    )
    val calmActionLabel = stringResource(
        if (calmMode) R.string.focus_calm_mode_exit else R.string.focus_calm_mode_enter,
    )

    Box(
        modifier = Modifier
            .size(ringSize)
            .breathing(
                minScale = 0.985f,
                maxScale = 1.025f,
                minAlpha = 0.94f,
                maxAlpha = 1f,
                durationMillis = 3600,
                enabled = focus.running,
            )
            .glowPulse(
                glowColor = ringEnd,
                cornerRadius = ringSize / 2,
                maxAlpha = 0.36f,
                durationMillis = 1200,
                enabled = isLastMinute,
            )
            .pressScale(pressedScale = 0.97f)
            .clip(CircleShape)
            .clickable(onClickLabel = calmActionLabel) {
                haptics(HapticKind.TICK)
                onToggleCalmMode()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
                progressBarRangeInfo = ProgressBarRangeInfo(focus.progress.coerceIn(0f, 1f), 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedRing(
            progress = focus.progress,
            modifier = Modifier
                .size(ringSize)
                .clearAndSetSemantics {},
            strokeWidth = strokeWidth,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            startColor = ringStart,
            endColor = ringEnd,
            animateFromZero = false,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = spacing.lg),
        ) {
            if (showPhaseLabel) {
                Text(
                    text = phaseText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (focus.isBreak) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.height(spacing.xs))
            }
            DigitRoll(
                text = formattedClock,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = if (calmMode || focus.isBreak) {
                    if (focus.isBreak) {
                        phaseText
                    } else {
                        focus.routineTitle ?: stringResource(R.string.focus_history_untitled)
                    }
                } else {
                    Dates.formatDigits(
                        stringResource(R.string.focus_minutes, focus.plannedMinutes),
                        appLocale,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Timer preview ring + routine picker + duration presets + custom input + primary start button. */
@Composable
private fun FocusSetupCard(
    routines: List<RoutineEntity>,
    selectedId: String?,
    selectedRoutine: RoutineEntity?,
    onSelect: (String?) -> Unit,
    minutesText: String,
    onMinutesText: (String) -> Unit,
    validMinutes: Boolean,
    effectiveMinutes: Int,
    appLocale: Locale,
    onStart: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fontScale = LocalDensity.current.fontScale
    val ringSize = if (fontScale > 1.4f) 244.dp else 196.dp
    val previewTimeText = Dates.formatDigits("%02d:00".format(Locale.ROOT, effectiveMinutes), appLocale)
    val previewA11y = Dates.formatDigits(
        stringResource(R.string.focus_minutes, effectiveMinutes),
        appLocale,
    )
    var startWaveTrigger by remember { mutableIntStateOf(0) }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
        ) {
            // Large centered timer ring preview
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(ringSize)
                        .semantics(mergeDescendants = true) {
                            contentDescription = previewA11y
                            progressBarRangeInfo = ProgressBarRangeInfo(1f, 0f..1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedRing(
                        progress = 1f,
                        modifier = Modifier
                            .size(ringSize)
                            .clearAndSetSemantics {},
                        strokeWidth = 14.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        startColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                        endColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f),
                        animateFromZero = true,
                        revealKey = "focus-setup-ring",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    ) {
                        DigitRoll(
                            text = previewTimeText,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = selectedRoutine?.title ?: stringResource(R.string.focus_timer_ready),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.xl))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            if (routines.isNotEmpty()) {
                Spacer(Modifier.height(spacing.lg))

                Text(
                    text = stringResource(R.string.focus_pick_routine),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.sm))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    item(key = "focus-general-session") {
                        AppChip(
                            text = stringResource(R.string.focus_history_untitled),
                            selected = selectedId == null,
                            onClick = { onSelect(null) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = if (selectedId == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    items(routines, key = { it.id }) { routine ->
                        val isSelected = selectedId == routine.id
                        val routineAccent = Color(RoutineColors.hexFor(routine.colorId, isDark))
                        AppChip(
                            text = routine.title,
                            selected = isSelected,
                            onClick = { onSelect(if (isSelected) null else routine.id) },
                            leadingIcon = {
                                Icon(
                                    imageVector = RoutineIconMap.icon(routine.icon),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else routineAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.lg))
            Text(
                text = stringResource(R.string.focus_duration),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(AppConstants.FOCUS_PRESETS, key = { it }) { option ->
                    AppChip(
                        text = Dates.formatDigits(
                            stringResource(R.string.focus_minutes, option),
                            appLocale,
                        ),
                        selected = minutesText == option.toString(),
                        onClick = { onMinutesText(option.toString()) },
                    )
                }
            }

            Spacer(Modifier.height(spacing.md))

            OutlinedTextField(
                value = minutesText,
                onValueChange = onMinutesText,
                label = { Text(stringResource(R.string.focus_custom_minutes)) },
                supportingText = {
                    Text(
                        text = Dates.formatDigits(
                            stringResource(
                                R.string.focus_custom_hint,
                                AppConstants.MIN_FOCUS_MINUTES,
                                AppConstants.MAX_FOCUS_MINUTES,
                            ),
                            appLocale,
                        ),
                    )
                },
                isError = minutesText.isNotEmpty() && !validMinutes,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (validMinutes) onStart() }),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))

            FocusTouchWaveButton(
                waveTrigger = startWaveTrigger,
                waveColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppButton(
                    text = stringResource(R.string.focus_start_session),
                    onClick = {
                        startWaveTrigger++
                        onStart()
                    },
                    enabled = validMinutes,
                    leadingIcon = {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

/** Big countdown ring with pause / resume / give-up controls and tap-to-calm hint. */
@Composable
private fun RunningSession(
    focus: FocusState,
    appLocale: Locale,
    breakModeEnabled: Boolean,
    longBreakInterval: Int,
    onToggleCalmMode: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipBreak: () -> Unit,
    onFinish: () -> Unit,
) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val fontScale = LocalDensity.current.fontScale
    val stackButtons = fontScale > 1.35f
    val statusColor = if (focus.isBreak) {
        MaterialTheme.colorScheme.tertiary
    } else if (focus.running) {
        brand.success
    } else {
        brand.warning
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (focus.isBreak) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            MetaPill(
                text = if (focus.isBreak) {
                    focusPhaseLabel(focus.phase)
                } else {
                    stringResource(
                        R.string.focus_running,
                        focus.routineTitle ?: stringResource(R.string.focus_history_untitled),
                    )
                },
                container = statusColor.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                },
            )

            if (breakModeEnabled || focus.isBreak) {
                Text(
                    text = focusPhaseLabel(focus.phase),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            FocusCountdownRing(
                focus = focus,
                ringSize = if (fontScale > 1.4f) 268.dp else 216.dp,
                strokeWidth = 16.dp,
                appLocale = appLocale,
                calmMode = false,
                onToggleCalmMode = onToggleCalmMode,
                showPhaseLabel = false,
            )

            if (breakModeEnabled) {
                Text(
                    text = Dates.formatDigits(
                        stringResource(
                            R.string.focus_cycle,
                            focus.cycleCount.coerceAtLeast(1),
                            longBreakInterval.coerceAtLeast(2),
                        ),
                        appLocale,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = stringResource(R.string.focus_calm_mode_enter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (stackButtons) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    FocusPlayPauseButton(
                        running = focus.running,
                        onToggle = { if (focus.running) onPause() else onResume() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = stringResource(
                            if (focus.isBreak) R.string.focus_skip_break else R.string.focus_give_up,
                        ),
                        onClick = if (focus.isBreak) onSkipBreak else onFinish,
                        variant = AppButtonVariant.Secondary,
                        leadingIcon = if (focus.isBreak) {
                            null
                        } else {
                            {
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    AppButton(
                        text = stringResource(
                            if (focus.isBreak) R.string.focus_skip_break else R.string.focus_give_up,
                        ),
                        onClick = if (focus.isBreak) onSkipBreak else onFinish,
                        variant = AppButtonVariant.Secondary,
                        leadingIcon = if (focus.isBreak) {
                            null
                        } else {
                            {
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    )
                    FocusPlayPauseButton(
                        running = focus.running,
                        onToggle = { if (focus.running) onPause() else onResume() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Play <-> Pause primary toggle with morphing icon, haptic confirmation, and touch wave. */
@Composable
private fun FocusPlayPauseButton(
    running: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    var waveTrigger by remember { mutableIntStateOf(0) }
    val shape = MaterialTheme.shapes.small

    FocusTouchWaveButton(
        waveTrigger = waveTrigger,
        waveColor = MaterialTheme.colorScheme.onPrimary,
        shape = shape,
        modifier = modifier,
    ) {
        Button(
            onClick = {
                waveTrigger++
                haptics(HapticKind.CONFIRM)
                onToggle()
            },
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(pressedScale = 0.96f),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedContent(
                    targetState = running,
                    transitionSpec = {
                        when {
                            level.isOff -> ContentTransform(
                                fadeIn(TagMotion.instantTween(0)),
                                fadeOut(TagMotion.instantTween(0)),
                            )
                            level == AnimationLevel.REDUCED -> ContentTransform(
                                fadeIn(TagMotion.quickTween()),
                                fadeOut(TagMotion.quickTween()),
                            )
                            else -> ContentTransform(
                                fadeIn(TagMotion.quickTween()) + scaleIn(
                                    initialScale = 0.65f,
                                    animationSpec = TagMotion.bouncySpring(),
                                ),
                                fadeOut(TagMotion.instantTween()) + scaleOut(
                                    targetScale = 0.65f,
                                    animationSpec = TagMotion.instantTween(),
                                ),
                            )
                        }
                    },
                    label = "play-pause-icon",
                ) { isRunning ->
                    Icon(
                        imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = stringResource(if (running) R.string.focus_pause else R.string.focus_resume),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Draws a GPU radial pulse overlay across [content] whenever [waveTrigger] increments. */
@Composable
private fun FocusTouchWaveButton(
    waveTrigger: Int,
    waveColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val level = LocalAnimationLevel.current
    val wave = remember { Animatable(0f) }

    LaunchedEffect(waveTrigger, level) {
        if (waveTrigger > 0 && level.allowsMotion) {
            wave.snapTo(0.01f)
            wave.animateTo(1f, TagMotion.emphasizedTween())
            wave.snapTo(0f)
        } else {
            wave.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                drawContent()
                val progress = wave.value
                if (progress > 0.01f && progress < 1f) {
                    val maxRadius = size.maxDimension * 0.72f
                    drawCircle(
                        color = waveColor.copy(alpha = (1f - progress) * 0.24f),
                        radius = maxRadius * progress,
                    )
                }
            },
    ) {
        content()
    }
}

/** Three balanced 2-column rows of equal-height count-up stat tiles summarising recorded focus time. */
@Composable
private fun FocusStatsGrid(stats: FocusStats, appLocale: Locale) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val resources = LocalContext.current.resources
    val daySuffix = stringResource(R.string.streak_days_short)

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        StatTilePair { tileModifier ->
            AnimatedStatTile(
                count = stats.todayMinutes,
                format = { Dates.formatDuration(it, resources, appLocale) },
                label = stringResource(R.string.focus_stat_today),
                modifier = tileModifier,
                accent = MaterialTheme.colorScheme.primary,
            )
            AnimatedStatTile(
                count = stats.weekMinutes,
                format = { Dates.formatDuration(it, resources, appLocale) },
                label = stringResource(R.string.focus_stat_week),
                modifier = tileModifier,
                accent = brand.success,
            )
        }
        StatTilePair { tileModifier ->
            AnimatedStatTile(
                count = stats.totalSessions,
                format = { Dates.formatDigits("$it", appLocale) },
                label = stringResource(R.string.focus_stat_sessions),
                modifier = tileModifier,
                accent = MaterialTheme.colorScheme.tertiary,
            )
            AnimatedStatTile(
                count = stats.streak,
                format = { Dates.formatDigits("$it", appLocale) },
                label = stringResource(R.string.focus_stat_streak),
                modifier = tileModifier,
                accent = brand.streak,
                suffix = daySuffix,
            )
        }
        StatTilePair { tileModifier ->
            AnimatedStatTile(
                count = stats.totalMinutes,
                format = { Dates.formatDuration(it, resources, appLocale) },
                label = stringResource(R.string.focus_stat_total),
                modifier = tileModifier,
                accent = MaterialTheme.colorScheme.secondary,
            )
            AnimatedStatTile(
                count = stats.averageSessionMinutes,
                format = { Dates.formatDuration(it, resources, appLocale) },
                label = stringResource(R.string.focus_stat_average),
                modifier = tileModifier,
                accent = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FocusHistoryRow(
    title: String,
    minutes: Int,
    completed: Boolean,
    endedAt: Long,
    appLocale: Locale,
    calendarType: CalendarType,
) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val statusColor = if (completed) brand.success else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = Dates.formatDigits(
                    buildString {
                        append(
                            stringResource(
                                if (completed) R.string.focus_history_completed
                                else R.string.focus_history_abandoned,
                            ),
                        )
                        append(" · ")
                        append(
                            Dates.shortDateTime(
                                epochMillis = endedAt,
                                zone = ZoneId.systemDefault(),
                                locale = appLocale,
                                calendarType = calendarType,
                            ),
                        )
                    },
                    appLocale,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        MetaPill(
            text = Dates.formatDigits(stringResource(R.string.focus_minutes, minutes), appLocale),
            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
