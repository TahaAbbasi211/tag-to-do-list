package com.tag.routine.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.FocusStats
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.FocusState
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.FilterPill
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.RoutineIconMap
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.StatTile
import com.tag.routine.ui.theme.tagBrand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (focus.routineId != null) {
            item {
                RunningSession(
                    focus = focus,
                    onPause = onPause,
                    onResume = onResume,
                    onFinish = onFinish,
                )
            }
        } else {
            item {
                DurationCard(
                    routines = routines,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    minutesText = minutesText,
                    onMinutesText = { minutesText = it.filter { ch -> ch.isDigit() }.take(3) },
                    validMinutes = validMinutes,
                    effectiveMinutes = effectiveMinutes,
                    onStart = { selected?.let { onStart(it, effectiveMinutes) } },
                )
            }
        }

        item { FocusStatsGrid(stats) }

        if (stats.recent.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(title = stringResource(R.string.focus_history))
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.recent.forEach { row ->
                                FocusHistoryRow(
                                    title = row.routineTitle
                                        ?: stringResource(R.string.focus_history_untitled),
                                    minutes = row.minutes,
                                    completed = row.completed,
                                    endedAt = row.endedAt,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                EmptyState(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    title = stringResource(R.string.focus_history),
                    message = stringResource(R.string.focus_history_empty),
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

/** Routine picker plus the free-form duration field. */
@Composable
private fun DurationCard(
    routines: List<RoutineEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    minutesText: String,
    onMinutesText: (String) -> Unit,
    validMinutes: Boolean,
    effectiveMinutes: Int,
    onStart: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                title = stringResource(R.string.focus_title),
                subtitle = stringResource(R.string.focus_subtitle),
            )

            if (routines.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.focus_needs_routine),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.focus_pick_routine),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            // A horizontal strip keeps the card compact no matter how many routines
            // exist, instead of a tall nested scroll area.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routines, key = { it.id }) { routine ->
                    val isSelected = selectedId == routine.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        onClick = { onSelect(if (isSelected) null else routine.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = RoutineIconMap.icon(routine.icon),
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(routine.colorHex)
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = routine.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.focus_duration),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppConstants.FOCUS_PRESETS) { option ->
                    FilterPill(
                        text = stringResource(R.string.focus_minutes, option),
                        selected = minutesText == option.toString(),
                        onClick = { onMinutesText(option.toString()) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Free-form entry: the user may type any length, not just the presets.
            OutlinedTextField(
                value = minutesText,
                onValueChange = onMinutesText,
                label = { Text(stringResource(R.string.focus_custom_minutes)) },
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.focus_custom_hint,
                            AppConstants.MIN_FOCUS_MINUTES,
                            AppConstants.MAX_FOCUS_MINUTES,
                        ),
                    )
                },
                // Only flag a problem once something was actually typed.
                isError = minutesText.isNotEmpty() && !validMinutes,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (validMinutes) onStart() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onStart,
                enabled = selectedId != null && validMinutes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.focus_start_session),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Big countdown ring with pause / resume / give-up controls. */
@Composable
private fun RunningSession(
    focus: FocusState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val brand = tagBrand()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.focus_running,
                    focus.routineTitle ?: stringResource(R.string.focus_history_untitled),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RingWithLabel(
                progress = focus.progress,
                ringSize = 188.dp,
                strokeWidth = 14.dp,
            ) {
                val mins = focus.remainingSeconds / 60
                val secs = focus.remainingSeconds % 60
                Text(
                    text = "%02d:%02d".format(mins, secs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = stringResource(
                    R.string.focus_minutes,
                    focus.plannedMinutes,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onFinish, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.focus_give_up))
                }
                Button(
                    onClick = { if (focus.running) onPause() else onResume() },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(
                            if (focus.running) R.string.focus_pause else R.string.focus_resume,
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (focus.running) brand.success else brand.streak),
            )
        }
    }
}

/** Two rows of stat tiles summarising all recorded focus time. */
@Composable
private fun FocusStatsGrid(stats: FocusStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                value = formatFocus(stats.todayMinutes),
                label = stringResource(R.string.focus_stat_today),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
            )
            StatTile(
                value = formatFocus(stats.weekMinutes),
                label = stringResource(R.string.focus_stat_week),
                modifier = Modifier.weight(1f),
                accent = Color(0xFF4FD9C0),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                value = "${stats.totalSessions}",
                label = stringResource(R.string.focus_stat_sessions),
                modifier = Modifier.weight(1f),
                accent = Color(0xFFFFB65C),
            )
            StatTile(
                value = "${stats.streak}",
                label = stringResource(R.string.focus_stat_streak),
                modifier = Modifier.weight(1f),
                accent = Color(0xFFFF6B9D),
                suffix = "d",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                value = formatFocus(stats.totalMinutes),
                label = stringResource(R.string.focus_stat_total),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = formatFocus(stats.averageSessionMinutes),
                label = stringResource(R.string.focus_stat_average),
                modifier = Modifier.weight(1f),
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (completed) Color(0xFF4FD9C0) else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = buildString {
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
                            .format(historyFormatter),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.focus_minutes, minutes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Localised, medium-length date+time used by the history rows. */
private val historyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)

/** "1h 25m" / "45m" for the focus tiles. */
private fun formatFocus(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}
