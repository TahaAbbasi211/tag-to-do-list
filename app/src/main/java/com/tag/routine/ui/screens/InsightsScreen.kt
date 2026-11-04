package com.tag.routine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.Insights
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.HeatmapGrid
import com.tag.routine.ui.components.MiniRing
import com.tag.routine.ui.components.RoutineIconMap
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.StatTile
import com.tag.routine.ui.components.WeeklyBarChart

@Composable
fun InsightsScreen(state: UiState) {
    val insights = state.insights
    if (insights == null || insights.days.isEmpty()) {
        EmptyState(
            icon = {
                Icon(
                    imageVector = Icons.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = stringResource(R.string.insights_empty_title),
            message = stringResource(R.string.insights_empty_message),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Consistency score hero
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFB65C),
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = stringResource(R.string.insights_consistency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "${insights.weeklyConsistency}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.insights_out_of_100),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Stat tiles row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    value = "${insights.currentStreak}",
                    label = stringResource(R.string.insights_current_streak),
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFFFFB65C),
                    suffix = "d",
                )
                StatTile(
                    value = "${insights.bestStreak}",
                    label = stringResource(R.string.insights_best_streak),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                    suffix = "d",
                )
                StatTile(
                    value = "${insights.totalCompletions}",
                    label = stringResource(R.string.insights_completions),
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFF4FD9C0),
                )
            }
            if (insights.focusMinutes > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        value = formatFocus(insights.focusMinutes),
                        label = stringResource(R.string.insights_focus_time),
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFF7C6CFF),
                    )
                }
            }
        }

        // Weekday strengths
        if (insights.bestWeekday != null || insights.worstWeekday != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(title = stringResource(R.string.insights_activity))
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            insights.bestWeekday?.let { day ->
                                StatTile(
                                    value = weekdayName(day),
                                    label = stringResource(R.string.insights_best_weekday),
                                    modifier = Modifier.weight(1f),
                                    accent = Color(0xFF4FD9C0),
                                )
                            }
                            insights.worstWeekday?.let { day ->
                                StatTile(
                                    value = weekdayName(day),
                                    label = stringResource(R.string.insights_worst_weekday),
                                    modifier = Modifier.weight(1f),
                                    accent = Color(0xFFF0713C),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.insights_forecast_body, insights.projectedStreak),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Weekly bar chart
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.insights_last_7_days),
                        subtitle = stringResource(R.string.insights_daily_rate),
                    )
                    Spacer(Modifier.height(16.dp))
                    WeeklyBarChart(
                        stats = insights.days.takeLast(7),
                        today = state.today,
                    )
                }
            }
        }

        // Heatmap
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.insights_activity),
                        subtitle = stringResource(R.string.insights_last_12_weeks),
                    )
                    Spacer(Modifier.height(12.dp))
                    HeatmapGrid(stats = insights.days)
                }
            }
        }

        // Top routines leaderboard
        if (state.routineRates.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.insights_top_routines),
                            subtitle = stringResource(R.string.insights_rate_30_days),
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.routineRates.take(5).forEach { (routine, rate) ->
                                RoutineLeaderboardRow(routine.title, routine.icon, rate)
                            }
                        }
                    }
                }
            }
        }

        // Task stats
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = stringResource(R.string.insights_tasks))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            value = "${state.totalTasks}",
                            label = stringResource(R.string.insights_total_tasks),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = "${state.doneTasks}",
                            label = stringResource(R.string.insights_completed),
                            modifier = Modifier.weight(1f),
                            accent = Color(0xFF4FD9C0),
                        )
                        val rate = if (state.totalTasks == 0) 0f else state.doneTasks.toFloat() / state.totalTasks
                        StatTile(
                            value = "${(rate * 100).toInt()}",
                            label = stringResource(R.string.insights_completion_rate),
                            modifier = Modifier.weight(1f),
                            suffix = "%",
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Localised short weekday name for an ISO weekday number (1=Mon..7=Sun). */
private fun weekdayName(isoWeekday: Int): String {
    val date = Dates.today()
        .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(isoWeekday)))
    return Dates.dayOfWeekLabel(date)
}

/** "1h 25m" / "45m" for the focus-time tile. */
private fun formatFocus(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

@Composable
private fun RoutineLeaderboardRow(title: String, iconKey: String, rate: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniRing(
            progress = rate,
            size = 36.dp,
            stroke = 4.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.insights_percent_completion, (rate * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${(rate * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
