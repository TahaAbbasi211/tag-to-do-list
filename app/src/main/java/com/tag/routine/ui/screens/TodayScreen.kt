package com.tag.routine.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.Groups
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEmojis
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.RoutineGroupSection
import com.tag.routine.ui.TodayRoutine
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AnimatedCheck
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.ConfettiEffect
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.SwipeToDelete
import com.tag.routine.ui.components.TimelineRoutineItem
import com.tag.routine.ui.components.TodayEmptyState
import com.tag.routine.ui.components.WeekStrip
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
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
    onMoveRoutine: (String, Boolean) -> Unit,
    onOpenTemplates: () -> Unit = {},
    onDismissTemplatesCard: () -> Unit = {},
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
        Dates.relativeDayLabel(selected, today, resources)
    }
    val greetingText = Dates.formatDigits(rawGreeting, appLocale)

    val showSearch = state.routineCount > 8
    var query by remember { mutableStateOf("") }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Hero progress card, painted with the brand gradient (always deep enough for >= 4.5:1 contrast).
            val brand = tagBrand()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(brand.heroStart, brand.heroMid, brand.heroEnd),
                        ),
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(28.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = brand.onHero,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Dates.formatDigits(
                                stringResource(
                                    R.string.today_complete_count,
                                    state.completedCount,
                                    state.routineCount,
                                ),
                                appLocale,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = brand.onHeroMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val streak = state.insights?.currentStreak ?: 0
                            if (streak > 0) {
                                HeroPill(
                                    text = "🔥 " + Dates.formatDigits(
                                        stringResource(R.string.today_streak_days, streak),
                                        appLocale,
                                    ),
                                    contentColor = Color(0xFFFDE68A),
                                    accentColor = brand.streak,
                                )
                            }
                            if (state.routineCount > 0 && state.progress >= 1f) {
                                HeroPill(
                                    text = stringResource(R.string.today_perfect_day),
                                    contentColor = Color(0xFFA7F3D0),
                                    accentColor = brand.success,
                                )
                            }
                            HeroPill(
                                text = "▶ " + stringResource(R.string.today_focus_start),
                                contentColor = Color.White,
                                accentColor = Color.White,
                                onClick = onStartFocus,
                            )
                        }
                    }
                    RingWithLabel(
                        progress = state.progress,
                        ringSize = 118.dp,
                        strokeWidth = 12.dp,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        startColor = brand.ringStart,
                        endColor = brand.ringEnd,
                    ) {
                        Text(
                            text = Dates.formatPercent((state.progress * 100).toInt(), appLocale),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = brand.onHero,
                        )
                    }
                }
            }

            // Week strip
            WeekStrip(
                selected = selected,
                today = today,
                dotFor = { date -> state.dayRatios[Dates.iso(date)] ?: 0f },
                hasItemsFor = { date -> Dates.iso(date) in state.scheduledDates },
                onSelect = onSelectDate,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
            )

            Spacer(Modifier.height(4.dp))

            val hasAnyRoutine = state.allRoutines.isNotEmpty() ||
                state.groups.isNotEmpty() ||
                state.routineCount > 0
            val showFirstDayCard = !state.settings.templatesCardDismissed &&
                !hasAnyRoutine &&
                (state.settings.firstLaunchDate.isBlank() || state.settings.firstLaunchDate == Dates.iso(today))
            if (showFirstDayCard) {
                FirstDayTemplatesCard(
                    onClick = onOpenTemplates,
                    onDismiss = onDismissTemplatesCard,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
                )
            }

            if (showSearch) {
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
                                    contentDescription = null,
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
                        .padding(horizontal = MaterialTheme.spacing.lg),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Routine groups
            if (state.groups.isEmpty()) {
                TodayEmptyState(
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = MaterialTheme.spacing.lg,
                        top = MaterialTheme.spacing.lg,
                        end = MaterialTheme.spacing.lg,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
                ) {
                    items(visibleGroups, key = { it.name }) { section ->
                        RoutineGroupSectionBlock(
                            section = section,
                            appLocale = appLocale,
                            onToggleRoutine = onToggleRoutine,
                            onEditRoutine = onEditRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                            onMoveRoutine = onMoveRoutine,
                        )
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onAddRoutine,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(MaterialTheme.spacing.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.today_add_routine))
        }

        // Confetti burst on 100% completion
        val is100Percent = state.routineCount > 0 && state.progress >= 1f
        var confettiTriggered by remember { mutableStateOf(false) }
        LaunchedEffect(is100Percent) {
            confettiTriggered = is100Percent
        }
        ConfettiEffect(trigger = confettiTriggered)
    }
}

@Composable
private fun HeroPill(
    text: String,
    contentColor: Color,
    accentColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFF0A0F1E).copy(alpha = 0.68f))
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoutineGroupSectionBlock(
    section: RoutineGroupSection,
    appLocale: Locale,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
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
                            isFirst = index == 0,
                            isLast = index == section.items.lastIndex,
                            canMoveUp = index > 0,
                            canMoveDown = index < section.items.lastIndex,
                            onToggleRoutine = onToggleRoutine,
                            onEditRoutine = onEditRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                            onMoveRoutine = onMoveRoutine,
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
    isFirst: Boolean,
    isLast: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
) {
    val routine = item.routine
    val done = item.done
    val emoji = remember(routine.icon) { RoutineEmojis.resolve(routine.icon) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = Color(RoutineColors.hexFor(routine.colorId, isDark))
    val timeLabel = routine.timeMinutes?.let { Dates.formatDigits(Dates.timeLabel(it, appLocale), appLocale) }
    var menuOpen by remember { mutableStateOf(false) }
    val view = LocalView.current

    fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Exception) {}
    }

    TimelineRoutineItem(
        title = routine.title,
        emoji = emoji,
        accentColor = accentColor,
        done = done,
        timeLabel = timeLabel,
        isFirst = isFirst,
        isLast = isLast,
        onToggle = {
            performHaptic()
            onToggleRoutine(routine.id, !done)
        },
        onClick = {
            performHaptic()
            onToggleRoutine(routine.id, !done)
        },
        onLongClick = { menuOpen = true },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(32.dp),
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

@Suppress("unused")
@Composable
private fun RoutineRow(
    item: TodayRoutine,
    appLocale: Locale,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
) {
    val routine = item.routine
    val done = item.done
    val emoji = remember(routine.icon) { RoutineEmojis.resolve(routine.icon) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = Color(RoutineColors.hexFor(routine.colorId, isDark))
    val timeLabel = routine.timeMinutes?.let { Dates.timeLabel(it, appLocale) }
    var menuOpen by remember { mutableStateOf(false) }
    val view = LocalView.current

    fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Exception) {}
    }

    val baseCardColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBgColor by animateColorAsState(
        targetValue = if (done) lerp(baseCardColor, accentColor, 0.08f) else baseCardColor,
        animationSpec = tween(durationMillis = 250),
        label = "row-bg",
    )
    val iconBgColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = if (done) 0.24f else 0.15f),
        animationSpec = tween(durationMillis = 250),
        label = "icon-bg",
    )

    val rowShape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .combinedClickable(
                onClick = {
                    performHaptic()
                    onToggleRoutine(routine.id, !done)
                },
                onLongClick = { menuOpen = true },
            ),
        shape = rowShape,
        color = cardBgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.78f else 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(32.dp),
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

            AnimatedCheck(
                checked = done,
                onClick = { onToggleRoutine(routine.id, !done) },
                size = 28.dp,
                accent = accentColor,
            )
        }
    }
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
                modifier = Modifier.size(32.dp),
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
