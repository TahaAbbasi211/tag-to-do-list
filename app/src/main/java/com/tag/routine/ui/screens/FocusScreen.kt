package com.tag.routine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.Dates
import com.tag.routine.data.FocusStats
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.FocusState
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.AppChip
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.MetaPill
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.RoutineIconMap
import com.tag.routine.ui.components.ScreenHeader
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.StatTile
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dedicated Focus section: pick a routine, choose any duration, run the timer and
 * review focus history — all without leaving the screen.
 */
@Composable
fun FocusScreen(
    routines: List<RoutineEntity>,
    focus: FocusState,
    stats: FocusStats,
    defaultMinutes: Int,
    onStart: (RoutineEntity, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var minutesText by remember { mutableStateOf(defaultMinutes.toString()) }

    // Keep the custom field in step with the persisted default, but never clobber
    // what the user is currently typing.
    LaunchedEffect(defaultMinutes) {
        if (focus.routineId == null) minutesText = defaultMinutes.toString()
    }

    // Keep a valid selection even after the chosen routine is deleted or archived.
    val selected = routines.firstOrNull { it.id == selectedId }
    LaunchedEffect(routines.map { it.id }) {
        if (selectedId != null && routines.none { it.id == selectedId }) selectedId = null
    }

    val parsedMinutes = minutesText.toIntOrNull()
    val validMinutes = parsedMinutes != null &&
        parsedMinutes in AppConstants.MIN_FOCUS_MINUTES..AppConstants.MAX_FOCUS_MINUTES
    val effectiveMinutes = if (validMinutes) {
        parsedMinutes!!
    } else {
        AppConstants.clampFocusMinutes(parsedMinutes ?: defaultMinutes)
    }

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
        item {
            ScreenHeader(
                title = stringResource(R.string.focus_title),
                subtitle = stringResource(R.string.focus_subtitle),
            )
        }

        if (focus.routineId != null) {
            item {
                RunningSession(
                    focus = focus,
                    appLocale = appLocale,
                    onPause = onPause,
                    onResume = onResume,
                    onFinish = onFinish,
                )
            }
        } else {
            item {
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
                    onStart = { selected?.let { onStart(it, effectiveMinutes) } },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                SectionHeader(title = stringResource(R.string.focus_overview))
                FocusStatsGrid(stats = stats, appLocale = appLocale)
            }
        }

        if (stats.recent.isNotEmpty()) {
            item {
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
            item {
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
                RingWithLabel(
                    progress = 1f,
                    ringSize = 196.dp,
                    strokeWidth = 14.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    startColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    endColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    ) {
                        Text(
                            text = Dates.formatDigits("%02d:00".format(Locale.ROOT, effectiveMinutes), appLocale),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
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

            if (routines.isEmpty()) {
                Spacer(Modifier.height(spacing.lg))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = stringResource(R.string.focus_needs_routine),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            Spacer(Modifier.height(spacing.xl))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
                items(AppConstants.FOCUS_PRESETS) { option ->
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
                keyboardActions = KeyboardActions(onDone = { if (validMinutes && selectedId != null) onStart() }),
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

            AppButton(
                text = stringResource(R.string.focus_start_session),
                onClick = onStart,
                enabled = selectedId != null && validMinutes,
                leadingIcon = {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

/** Big countdown ring with pause / resume / give-up controls. */
@Composable
private fun RunningSession(
    focus: FocusState,
    appLocale: Locale,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val stackButtons = LocalDensity.current.fontScale > 1.35f
    val statusColor = if (focus.running) brand.success else brand.warning

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            MetaPill(
                text = stringResource(
                    R.string.focus_running,
                    focus.routineTitle ?: stringResource(R.string.focus_history_untitled),
                ),
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

            RingWithLabel(
                progress = focus.progress,
                ringSize = 216.dp,
                strokeWidth = 16.dp,
            ) {
                val mins = focus.remainingSeconds / 60
                val secs = focus.remainingSeconds % 60
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = spacing.lg),
                ) {
                    Text(
                        text = Dates.formatDigits("%02d:%02d".format(Locale.ROOT, mins, secs), appLocale),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = Dates.formatDigits(
                            stringResource(R.string.focus_minutes, focus.plannedMinutes),
                            appLocale,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            if (stackButtons) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    AppButton(
                        text = stringResource(if (focus.running) R.string.focus_pause else R.string.focus_resume),
                        onClick = { if (focus.running) onPause() else onResume() },
                        variant = AppButtonVariant.Primary,
                        leadingIcon = {
                            Icon(
                                imageVector = if (focus.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                    AppButton(
                        text = stringResource(R.string.focus_give_up),
                        onClick = onFinish,
                        variant = AppButtonVariant.Secondary,
                        leadingIcon = {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        text = stringResource(R.string.focus_give_up),
                        onClick = onFinish,
                        variant = AppButtonVariant.Secondary,
                        leadingIcon = {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    )
                    AppButton(
                        text = stringResource(if (focus.running) R.string.focus_pause else R.string.focus_resume),
                        onClick = { if (focus.running) onPause() else onResume() },
                        variant = AppButtonVariant.Primary,
                        leadingIcon = {
                            Icon(
                                imageVector = if (focus.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
    }
}

/** Three balanced 2-column rows of equal-height stat tiles summarising recorded focus time. */
@Composable
private fun FocusStatsGrid(stats: FocusStats, appLocale: Locale) {
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val resources = LocalContext.current.resources
    val daySuffix = stringResource(R.string.streak_days_short)

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            StatTile(
                value = Dates.formatDuration(stats.todayMinutes, resources, appLocale),
                label = stringResource(R.string.focus_stat_today),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = MaterialTheme.colorScheme.primary,
            )
            StatTile(
                value = Dates.formatDuration(stats.weekMinutes, resources, appLocale),
                label = stringResource(R.string.focus_stat_week),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = brand.success,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            StatTile(
                value = Dates.formatDigits("${stats.totalSessions}", appLocale),
                label = stringResource(R.string.focus_stat_sessions),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = MaterialTheme.colorScheme.tertiary,
            )
            StatTile(
                value = Dates.formatDigits("${stats.streak}", appLocale),
                label = stringResource(R.string.focus_stat_streak),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = brand.streak,
                suffix = daySuffix,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            StatTile(
                value = Dates.formatDuration(stats.totalMinutes, resources, appLocale),
                label = stringResource(R.string.focus_stat_total),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                accent = MaterialTheme.colorScheme.secondary,
            )
            StatTile(
                value = Dates.formatDuration(stats.averageSessionMinutes, resources, appLocale),
                label = stringResource(R.string.focus_stat_average),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                            Instant.ofEpochMilli(endedAt)
                                .atZone(ZoneId.systemDefault())
                                .format(historyFormatter.withLocale(appLocale)),
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

/** Localised, short date+time used by the history rows. */
private val historyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
