package com.tag.routine.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.Groups
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEmojis
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.TodayNudge
import com.tag.routine.ui.RoutineGroupSection
import com.tag.routine.ui.TodayRoutine
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.LargeScreenFrame
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.SwipeToDelete
import com.tag.routine.ui.components.TimelineRoutineItem
import com.tag.routine.ui.components.TodayEmptyState
import com.tag.routine.ui.components.WeekStrip
import com.tag.routine.ui.editors.CopyToDayDialog
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.motion.AnimatedRing
import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.AuroraBackground
import com.tag.routine.ui.motion.ConfettiBurst
import com.tag.routine.ui.motion.CountUpText
import com.tag.routine.ui.motion.GlassCard
import com.tag.routine.ui.motion.HapticKind
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.LocalMotionRevealRegistry
import com.tag.routine.ui.motion.SparkleBurst
import com.tag.routine.ui.motion.StreakFlame
import com.tag.routine.ui.motion.StreakMilestones
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.pressScale
import com.tag.routine.ui.motion.rememberTagHaptics
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tabularNumbers
import com.tag.routine.ui.theme.tagBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    state: UiState,
    onToggleRoutine: (String, Boolean) -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onStartFocus: () -> Unit,
    onStartFocusForRoutine: (RoutineEntity) -> Unit = {},
    onMoveRoutine: (String, Boolean) -> Unit,
    onOpenTemplates: () -> Unit = {},
    onDismissTemplatesCard: () -> Unit = {},
    onCopyPreviousDay: () -> Unit = {},
    onCopyRoutineToDays: (String, List<java.time.LocalDate>) -> Unit = { _, _ -> },
) {
    val today = state.today
    val selected = state.selectedDate
    val isToday = selected == today
    val context = LocalContext.current
    val resources = context.resources
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    // Build the greeting: cap the name so the line never overflows the hero card.
    val rawGreeting = if (isToday) {
        Dates.greetingWithName(
            name = state.settings.userName.take(18),
            resources = resources,
        )
    } else {
        Dates.relativeDayLabel(
            date = selected,
            today = today,
            resources = resources,
            locale = appLocale,
            calendarType = state.settings.calendarType,
        )
    }
    val greetingText = Dates.formatDigits(rawGreeting, appLocale)

    // The hero card's second line reacts to the clock and to how much of the day is done.
    // The wording lives in the five string files; only the choice is decided here.
    val nudge = TodayNudge.forDay(
        hour = java.time.LocalTime.now().hour,
        total = state.routineCount,
        completed = state.completedCount,
    )
    val nudgeText = Dates.formatDigits(
        stringResource(
            when (nudge) {
                TodayNudge.EMPTY -> R.string.nudge_empty
                TodayNudge.ALL_DONE -> R.string.nudge_all_done
                TodayNudge.ONE_LEFT -> R.string.nudge_one_left
                TodayNudge.NIGHT -> R.string.nudge_night
                TodayNudge.MORNING -> R.string.nudge_morning
                TodayNudge.HALFWAY -> R.string.nudge_halfway
                TodayNudge.KEEP_GOING -> R.string.nudge_keep_going
            },
        ),
        appLocale,
    )

    val showSearch = state.routineCount > 8
    var query by remember { mutableStateOf("") }

    // The item whose "copy to another day" sheet is open, if any.
    var copyTarget by remember { mutableStateOf<RoutineEntity?>(null) }

    // Filter routines by the search box; group sections with no match disappear.
    val visibleGroups = remember(state.groups, query, showSearch) {
        if (!showSearch || query.isBlank()) {
            state.groups
        } else {
            val needle = query.trim().lowercase()
            state.groups.mapNotNull { section ->
                val hits = section.items.filter { it.routine.title.lowercase().contains(needle) }
                if (hits.isEmpty()) null else section.copy(items = hits)
            }
        }
    }

    val upNextRoutineId = remember(visibleGroups) {
        visibleGroups.asSequence().flatMap { it.items.asSequence() }.firstOrNull { !it.done }?.routine?.id
    }

    // Track day navigation direction so timeline rows slide from the matching edge.
    val selectedIso = Dates.iso(selected)
    var lastSeenDate by remember { mutableStateOf(selected) }
    var lastDayDirection by remember { mutableIntStateOf(0) }
    val dayDirection = if (selected != lastSeenDate) {
        val dir = selected.compareTo(lastSeenDate).coerceIn(-1, 1)
        lastDayDirection = dir
        lastSeenDate = selected
        dir
    } else {
        lastDayDirection
    }

    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    val brand = tagBrand()
    val density = LocalDensity.current
    val stackHero = density.fontScale > 1.35f
    val listState = rememberLazyListState()
    val collapseThresholdPx = remember(density) { with(density) { 128.dp.toPx() } }

    val showCompactHero by remember {
        derivedStateOf {
            state.groups.isNotEmpty() &&
                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > collapseThresholdPx)
        }
    }
    val fabCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }

    // 100% completion celebration: fires at most once per ISO date when transitioning to 100%.
    val is100Percent = state.routineCount > 0 && state.progress >= 1f
    var celebratedDates by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var lastProgressKey by remember { mutableStateOf(selectedIso to is100Percent) }
    var celebrateActive by remember { mutableStateOf(false) }
    var celebrateSeed by remember { mutableLongStateOf(0L) }
    var ringPulseCount by remember { mutableIntStateOf(0) }
    val heroFlashAlpha = remember { Animatable(0f) }

    LaunchedEffect(selectedIso, is100Percent) {
        val (prevIso, prevComplete) = lastProgressKey
        lastProgressKey = selectedIso to is100Percent
        if (prevIso == selectedIso && !prevComplete && is100Percent && selectedIso !in celebratedDates) {
            celebratedDates = celebratedDates + selectedIso
            celebrateSeed = System.currentTimeMillis()
            celebrateActive = true
            ringPulseCount++
            haptics(HapticKind.CELEBRATE)
            if (level.allowsMotion) {
                launch {
                    heroFlashAlpha.snapTo(0f)
                    heroFlashAlpha.animateTo(0.28f, TagMotion.instantTween())
                    heroFlashAlpha.animateTo(0f, TagMotion.emphasizedTween())
                }
            }
        } else if (prevIso != selectedIso || !is100Percent) {
            celebrateActive = false
        }
    }

    // Streak bump when completing items raises the streak.
    val streak = state.insights?.currentStreak ?: 0
    var previousStreak by remember { mutableIntStateOf(streak) }
    val streakBumpScale = remember { Animatable(1f) }
    LaunchedEffect(streak, level) {
        if (streak > previousStreak && level.allowsMotion) {
            streakBumpScale.snapTo(1f)
            streakBumpScale.animateTo(1.16f, TagMotion.instantTween())
            streakBumpScale.animateTo(1f, TagMotion.bouncySpring())
        }
        previousStreak = streak
    }

    val activeMilestone = StreakMilestones.milestoneFor(streak)
    var dismissedMilestone by rememberSaveable { mutableIntStateOf(0) }

    // FAB entrance spring (played once per session so switching tabs doesn't replay)
    val revealRegistry = LocalMotionRevealRegistry.current
    val animateFab = remember { revealRegistry?.consumeFirstReveal("today-fab") ?: true }
    val fabEntrance = remember { Animatable(if (level.isOff || !animateFab) 1f else 0.72f) }
    LaunchedEffect(level) {
        if (level.isOff || !animateFab) {
            fabEntrance.snapTo(1f)
        } else {
            fabEntrance.animateTo(1f, TagMotion.bouncySpring())
        }
    }

    val heroProgressDesc = Dates.formatDigits(
        stringResource(R.string.today_complete_count, state.completedCount, state.routineCount),
        appLocale,
    )
    val spacing = MaterialTheme.spacing

    LargeScreenFrame {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                // 0. Living Hero Card on AuroraBackground + GlassCard
                item(key = "today-hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.md),
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            containerColor = Color.Transparent,
                            rimColors = listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.08f),
                            ),
                            elevation = 8.dp,
                        ) {
                            AuroraBackground(
                                colors = brand.auroraColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(brand.heroStart, brand.heroMid, brand.heroEnd),
                                        ),
                                    ),
                                parallaxOffsetPx = {
                                    if (listState.firstVisibleItemIndex == 0) {
                                        -listState.firstVisibleItemScrollOffset * 0.16f
                                    } else {
                                        -80f
                                    }
                                },
                            ) {
                                // Subtle 100% completion hero color flash
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = heroFlashAlpha.value }
                                        .background(brand.success)
                                        .clearAndSetSemantics {},
                                )

                                val heroRing: @Composable () -> Unit = {
                                    val ringSize = if (stackHero) 176.dp else 118.dp
                                    Box(
                                        modifier = Modifier.size(ringSize),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AnimatedRing(
                                            progress = state.progress,
                                            modifier = Modifier.size(ringSize),
                                            strokeWidth = 12.dp,
                                            trackColor = Color.White.copy(alpha = 0.18f),
                                            startColor = brand.ringStart,
                                            endColor = brand.ringEnd,
                                            animateFromZero = true,
                                            pulseTrigger = ringPulseCount,
                                            revealKey = "today-hero-ring",
                                            contentDescription = heroProgressDesc,
                                        )
                                        CountUpText(
                                            value = (state.progress * 100).toInt(),
                                            format = { pct -> Dates.formatPercent(pct, appLocale) },
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = brand.onHero,
                                            animateFromZero = true,
                                            revealKey = "today-hero-count",
                                        )
                                    }
                                }

                                val heroInfo: @Composable (Modifier) -> Unit = { heroModifier ->
                                    Column(modifier = heroModifier) {
                                        StaggeredWordGreeting(
                                            text = greetingText,
                                            color = brand.onHero,
                                            stackHero = stackHero,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = heroProgressDesc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = brand.onHeroMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        // Dynamic nudge: crossfade + subtle vertical shift on state changes.
                                        AnimatedContent(
                                            targetState = nudgeText,
                                            transitionSpec = {
                                                if (level.isOff) {
                                                    ContentTransform(fadeIn(TagMotion.instantTween(0)), fadeOut(TagMotion.instantTween(0)))
                                                } else if (level == AnimationLevel.REDUCED) {
                                                    ContentTransform(fadeIn(TagMotion.quickTween()), fadeOut(TagMotion.quickTween()))
                                                } else {
                                                    ContentTransform(
                                                        fadeIn(TagMotion.standardTween()) + slideInVertically(
                                                            TagMotion.standardTween(),
                                                        ) { it / 4 },
                                                        fadeOut(TagMotion.quickTween()) + slideOutVertically(
                                                            TagMotion.quickTween(),
                                                        ) { -it / 4 },
                                                    )
                                                }
                                            },
                                            label = "today-nudge",
                                        ) { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = brand.onHero,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = if (stackHero) 4 else 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (streak > 0) {
                                                HeroPill(
                                                    text = Dates.formatDigits(
                                                        stringResource(R.string.today_streak_days, streak),
                                                        appLocale,
                                                    ),
                                                    contentColor = Color(0xFFFDE68A),
                                                    accentColor = brand.streak,
                                                    modifier = Modifier.graphicsLayer {
                                                        val s = streakBumpScale.value
                                                        scaleX = s
                                                        scaleY = s
                                                    },
                                                    leading = {
                                                        StreakFlame(
                                                            modifier = Modifier.size(16.dp),
                                                            outerColor = brand.streak,
                                                            innerColor = Color(0xFFFEF08A),
                                                        )
                                                    },
                                                )
                                            }
                                            if (is100Percent) {
                                                HeroPill(
                                                    text = stringResource(R.string.today_all_done_celebration),
                                                    contentColor = Color(0xFFA7F3D0),
                                                    accentColor = brand.success,
                                                    modifier = Modifier.semantics {
                                                        liveRegion = LiveRegionMode.Polite
                                                    },
                                                )
                                            }
                                            HeroPill(
                                                text = "⏱ " + Dates.formatDigits(
                                                    stringResource(
                                                        R.string.today_focus_time,
                                                        Dates.formatDuration(state.focusSummary.todayMinutes, resources, appLocale),
                                                    ),
                                                    appLocale,
                                                ),
                                                contentColor = Color(0xFFE0E7FF),
                                                accentColor = brand.ringStart,
                                                onClick = onStartFocus,
                                            )
                                            HeroPill(
                                                text = "▶ " + stringResource(R.string.today_focus_start),
                                                contentColor = Color.White,
                                                accentColor = Color.White,
                                                onClick = onStartFocus,
                                            )
                                        }
                                    }
                                }

                                if (stackHero) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(spacing.xl),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        heroInfo(Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(spacing.lg))
                                        heroRing()
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(spacing.xl),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        heroInfo(Modifier.weight(1f))
                                        heroRing()
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Week strip
                item(key = "today-week-strip") {
                    WeekStrip(
                        selected = selected,
                        today = today,
                        dotFor = { date -> state.dayRatios[Dates.iso(date)] ?: 0f },
                        hasItemsFor = { date -> Dates.iso(date) in state.scheduledDates },
                        onSelect = onSelectDate,
                        calendarType = state.settings.calendarType,
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // 2. Streak Milestone Celebration Banner
                if (activeMilestone != null && dismissedMilestone != activeMilestone) {
                    item(key = "streak-milestone-$activeMilestone") {
                        StreakMilestoneBanner(
                            streak = activeMilestone,
                            appLocale = appLocale,
                            accentColor = brand.streak,
                            onDismiss = { dismissedMilestone = activeMilestone },
                            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                        )
                    }
                }

                // 3. One-tap copy of the previous day's items
                if (state.copyFromPreviousDay.isNotEmpty()) {
                    item(key = "copy-previous-day") {
                        AppButton(
                            text = Dates.formatDigits(
                                stringResource(
                                    R.string.copy_yesterday_to_today_count,
                                    state.copyFromPreviousDay.size,
                                ),
                                appLocale,
                            ),
                            onClick = onCopyPreviousDay,
                            variant = AppButtonVariant.Tonal,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.xs),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }

                // 4. First-day templates suggestion
                val hasAnyRoutine = state.allRoutines.isNotEmpty() ||
                    state.groups.isNotEmpty() ||
                    state.routineCount > 0
                val showFirstDayCard = !state.settings.templatesCardDismissed &&
                    !hasAnyRoutine &&
                    (state.settings.firstLaunchDate.isBlank() || state.settings.firstLaunchDate == Dates.iso(today))
                if (showFirstDayCard) {
                    item(key = "first-day-templates") {
                        FirstDayTemplatesCard(
                            onClick = onOpenTemplates,
                            onDismiss = onDismissTemplatesCard,
                            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                        )
                    }
                }

                // 5. Search bar when list is long
                if (showSearch) {
                    item(key = "today-search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.today_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_clear_search),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.xs),
                        )
                    }
                }

                // 6. Routine timeline groups or Sunrise animated empty state
                if (state.groups.isEmpty()) {
                    item(key = "today-empty") {
                        TodayEmptyState(
                            onAddRoutine = onAddRoutine,
                            modifier = Modifier.padding(vertical = spacing.md),
                        )
                    }
                } else {
                    items(visibleGroups, key = { it.name }) { section ->
                        Box(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) {
                            RoutineGroupSectionBlock(
                                section = section,
                                appLocale = appLocale,
                                dayKey = selectedIso,
                                dayDirection = dayDirection,
                                upNextRoutineId = upNextRoutineId,
                                onToggleRoutine = onToggleRoutine,
                                onEditRoutine = onEditRoutine,
                                onDeleteRoutine = onDeleteRoutine,
                                onMoveRoutine = onMoveRoutine,
                                onCopyRoutine = { copyTarget = it },
                                onStartFocusForRoutine = onStartFocusForRoutine,
                            )
                        }
                    }
                }
            }

            // Collapsed Compact Hero Bar pinned cleanly on scroll
            val compactAlphaState = animateFloatAsState(
                targetValue = if (showCompactHero) 1f else 0f,
                animationSpec = if (level.isOff) TagMotion.instantTween(0) else TagMotion.quickTween(),
                label = "compact-hero-alpha",
            )
            val showCompactOverlay by remember {
                derivedStateOf { compactAlphaState.value > 0.02f }
            }
            if (showCompactOverlay) {
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.xs)
                        .graphicsLayer {
                            val a = compactAlphaState.value
                            alpha = a
                            translationY = (1f - a) * -24f
                        },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = brand.heroStart.copy(alpha = 0.94f),
                    rimColors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                    elevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedRing(
                                progress = state.progress,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 4.5.dp,
                                trackColor = Color.White.copy(alpha = 0.20f),
                                startColor = brand.ringStart,
                                endColor = brand.ringEnd,
                                contentDescription = heroProgressDesc,
                            )
                            Text(
                                text = Dates.formatPercent((state.progress * 100).toInt(), appLocale),
                                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                                fontWeight = FontWeight.Bold,
                                color = brand.onHero,
                                maxLines = 1,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greetingText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = brand.onHero,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = heroProgressDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = brand.onHeroMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (streak > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(Color(0xFF0A0F1E).copy(alpha = 0.55f))
                                    .border(1.dp, brand.streak.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                StreakFlame(
                                    modifier = Modifier.size(14.dp),
                                    outerColor = brand.streak,
                                    innerColor = Color(0xFFFEF08A),
                                )
                                Text(
                                    text = Dates.formatDigits(streak.toString(), appLocale),
                                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // FAB with spring entrance, press rotation, and scroll collapse
            val fabInteraction = remember { MutableInteractionSource() }
            val isFabPressed by fabInteraction.collectIsPressedAsState()
            val plusRotationState = animateFloatAsState(
                targetValue = if (isFabPressed && level.allowsMotion) 45f else 0f,
                animationSpec = if (level.isOff) TagMotion.instantTween(0) else TagMotion.snappySpring(),
                label = "fab-plus-rot",
            )
            ExtendedFloatingActionButton(
                onClick = {
                    haptics(HapticKind.TICK)
                    onAddRoutine()
                },
                expanded = !fabCollapsed,
                interactionSource = fabInteraction,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = if (fabCollapsed) stringResource(R.string.today_add_routine) else null,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = plusRotationState.value
                        },
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.today_add_routine),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(spacing.lg)
                    .graphicsLayer {
                        val s = fabEntrance.value
                        scaleX = s
                        scaleY = s
                    }
                    .pressScale(pressedScale = 0.94f, interactionSource = fabInteraction),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            )

            // Combined SparkleBurst + ConfettiBurst strictly within 60 FULL / 24 REDUCED / 0 OFF
            val confettiCount = if (level == AnimationLevel.REDUCED) 16 else 44
            val sparkleCount = if (level == AnimationLevel.REDUCED) 8 else 16
            SparkleBurst(
                trigger = celebrateActive,
                seed = celebrateSeed + 1L,
                requestedCount = sparkleCount,
            )
            ConfettiBurst(
                trigger = celebrateActive,
                seed = celebrateSeed,
                requestedCount = confettiCount,
            )
        }
    }

    // Copy-to-another-day sheet, opened from an item's overflow menu.
    val target = copyTarget
    if (target != null) {
        CopyToDayDialog(
            routineTitle = target.title,
            today = state.today,
            localizedContext = LocalContext.current,
            calendarType = state.settings.calendarType,
            onDismiss = { copyTarget = null },
            onConfirm = { days -> onCopyRoutineToDays(target.id, days) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaggeredWordGreeting(
    text: String,
    color: Color,
    stackHero: Boolean,
) {
    val level = LocalAnimationLevel.current
    val revealRegistry = LocalMotionRevealRegistry.current
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }

    if (level.isOff || stackHero || words.size <= 1) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = if (stackHero) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        return
    }

    val animateWords = remember(text) {
        revealRegistry?.shouldAnimateValue("today-greeting", text) ?: true
    }

    FlowRow(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = text
            heading()
        },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        words.forEachIndexed { index, word ->
            val wordAlpha = remember(text, index) { Animatable(if (animateWords) 0f else 1f) }
            LaunchedEffect(text, level) {
                if (animateWords) {
                    wordAlpha.snapTo(0f)
                    delay(TagMotion.staggerDelayMillis(index, stepMs = 50, maxDelayMs = 300).toLong())
                    wordAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = TagMotion.standardTween(),
                    )
                } else {
                    wordAlpha.snapTo(1f)
                }
            }
            Text(
                text = word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .graphicsLayer { alpha = wordAlpha.value }
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun StreakMilestoneBanner(
    streak: Int,
    appLocale: Locale,
    accentColor: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        containerColor = accentColor.copy(alpha = 0.16f),
        rimColors = listOf(
            accentColor.copy(alpha = 0.55f),
            accentColor.copy(alpha = 0.16f),
        ),
        elevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StreakFlame(
                modifier = Modifier.size(22.dp),
                outerColor = accentColor,
                innerColor = Color(0xFFFEF08A),
            )
            Text(
                text = Dates.formatDigits(
                    stringResource(R.string.streak_milestone_banner, streak),
                    appLocale,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroPill(
    text: String,
    contentColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.pressScale(pressedScale = 0.95f) else Modifier)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFF0A0F1E).copy(alpha = 0.68f))
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun RoutineGroupSectionBlock(
    section: RoutineGroupSection,
    appLocale: Locale,
    dayKey: String,
    dayDirection: Int,
    upNextRoutineId: String?,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
    onCopyRoutine: (RoutineEntity) -> Unit,
    onStartFocusForRoutine: (RoutineEntity) -> Unit = {},
) {
    val resources = LocalContext.current.resources
    val completedCount = section.items.count { it.done }
    val totalCount = section.items.size
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = Groups.label(section.name, resources),
            subtitle = Dates.formatDigits("$completedCount / $totalCount", appLocale),
            trailing = {
                if (totalCount > 0 && completedCount == totalCount) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (isDark) Color(0xFF10B981).copy(alpha = 0.22f) else Color(0xFFD1FAE5),
                    ) {
                        Text(
                            text = stringResource(R.string.group_complete_badge),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46),
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        Column(modifier = Modifier.fillMaxWidth()) {
            section.items.forEachIndexed { index, item ->
                key(item.routine.id) {
                    SwipeToDelete(
                        onDelete = { onDeleteRoutine(item.routine) },
                        shape = RoundedCornerShape(16.dp),
                        confirmImmediately = !item.routine.repeatDaily,
                    ) {
                        TimelineRoutineRow(
                            item = item,
                            appLocale = appLocale,
                            index = index,
                            dayKey = dayKey,
                            dayDirection = dayDirection,
                            isFirst = index == 0,
                            isLast = index == section.items.lastIndex,
                            prevDone = index > 0 && section.items[index - 1].done,
                            nextDone = index < section.items.lastIndex && section.items[index + 1].done,
                            isUpNext = item.routine.id == upNextRoutineId,
                            canMoveUp = index > 0,
                            canMoveDown = index < section.items.lastIndex,
                            onToggleRoutine = onToggleRoutine,
                            onEditRoutine = onEditRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                            onMoveRoutine = onMoveRoutine,
                            onCopyRoutine = onCopyRoutine,
                            onStartFocusForRoutine = onStartFocusForRoutine,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRoutineRow(
    item: TodayRoutine,
    appLocale: Locale,
    index: Int,
    dayKey: String,
    dayDirection: Int,
    isFirst: Boolean,
    isLast: Boolean,
    prevDone: Boolean,
    nextDone: Boolean,
    isUpNext: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
    onCopyRoutine: (RoutineEntity) -> Unit,
    onStartFocusForRoutine: (RoutineEntity) -> Unit = {},
) {
    val routine = item.routine
    val done = item.done
    val emoji = remember(routine.icon) { RoutineEmojis.resolve(routine.icon) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = Color(RoutineColors.hexFor(routine.colorId, isDark))
    val timeLabel = routine.timeMinutes?.let { Dates.formatDigits(Dates.timeLabel(it, appLocale), appLocale) }
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberTagHaptics()
    // Read the row aloud as one sentence and offer every swipe/edit action to TalkBack.
    val doneLabel = stringResource(R.string.a11y_status_done)
    val notDoneLabel = stringResource(R.string.a11y_status_not_done)
    val noTimeLabel = stringResource(R.string.a11y_no_time)

    TimelineRoutineItem(
        title = routine.title,
        emoji = emoji,
        accentColor = accentColor,
        done = done,
        timeLabel = timeLabel,
        isFirst = isFirst,
        isLast = isLast,
        index = index,
        dayKey = dayKey,
        dayDirection = dayDirection,
        prevDone = prevDone,
        nextDone = nextDone,
        isUpNext = isUpNext,
        doneLabel = doneLabel,
        notDoneLabel = notDoneLabel,
        noTimeLabel = noTimeLabel,
        accessibilityLocale = appLocale,
        customAccessibilityActions = buildList {
            add(
                CustomAccessibilityAction(stringResource(R.string.action_delete)) {
                    onDeleteRoutine(routine)
                    true
                },
            )
            add(
                CustomAccessibilityAction(stringResource(R.string.action_edit)) {
                    onEditRoutine(routine)
                    true
                },
            )
            add(
                CustomAccessibilityAction(stringResource(R.string.copy_to_other_day)) {
                    onCopyRoutine(routine)
                    true
                },
            )
            if (canMoveUp) {
                add(
                    CustomAccessibilityAction(stringResource(R.string.action_move_up)) {
                        onMoveRoutine(routine.id, true)
                        true
                    },
                )
            }
            if (canMoveDown) {
                add(
                    CustomAccessibilityAction(stringResource(R.string.action_move_down)) {
                        onMoveRoutine(routine.id, false)
                        true
                    },
                )
            }
        },
        onToggle = {
            haptics(HapticKind.CONFIRM)
            onToggleRoutine(routine.id, !done)
        },
        onClick = {
            haptics(HapticKind.CONFIRM)
            onToggleRoutine(routine.id, !done)
        },
        onLongClick = { menuOpen = true },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // The menu is a popup window that swaps LocalContext for the Activity's
                // system-locale context; capture the app-language context here and
                // re-provide it inside so the items follow the chosen language.
                val localizedContext = LocalContext.current
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ProvideAppLocale(localizedContext) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_focus_start)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onStartFocusForRoutine(routine)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_up)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            enabled = canMoveUp,
                            onClick = {
                                menuOpen = false
                                onMoveRoutine(routine.id, true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_down)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            enabled = canMoveDown,
                            onClick = {
                                menuOpen = false
                                onMoveRoutine(routine.id, false)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_to_other_day)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onCopyRoutine(routine)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onEditRoutine(routine)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDeleteRoutine(routine)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FirstDayTemplatesCard(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        elevation = 0.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.templates_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.templates_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDismiss,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.templates_card_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
