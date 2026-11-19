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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.FocusedItemStat
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEmojis
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.HeatmapGrid
import com.tag.routine.ui.components.MiniRing
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.ScreenHeader
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.StatTile
import com.tag.routine.ui.components.ThinProgressBar
import com.tag.routine.ui.components.WeeklyBarChart
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun InsightsScreen(state: UiState) {
    val insights = state.insights
    val focusSummary = state.focusSummary
    val spacing = MaterialTheme.spacing
    val brand = tagBrand()
    val resources = LocalContext.current.resources
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val daySuffix = stringResource(R.string.streak_days_short)

    if (insights == null || insights.days.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            ScreenHeader(
                title = stringResource(R.string.nav_insights),
                subtitle = stringResource(R.string.insights_subtitle),
            )
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                EmptyState(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                    },
                    title = stringResource(R.string.insights_empty_title),
                    message = stringResource(R.string.insights_empty_message),
                )
            }
            Spacer(Modifier.height(80.dp))
        }
        return
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
                title = stringResource(R.string.nav_insights),
                subtitle = stringResource(R.string.insights_subtitle),
            )
        }

        // Consistency score hero
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(brand.streak.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = brand.streak,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                    Text(
                        text = stringResource(R.string.insights_consistency),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.lg))
                    RingWithLabel(
                        progress = (insights.weeklyConsistency / 100f).coerceIn(0f, 1f),
                        ringSize = 136.dp,
                        strokeWidth = 12.dp,
                        trackColor = brand.chartTrack,
                        startColor = MaterialTheme.colorScheme.primary,
                        endColor = brand.success,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = Dates.formatDigits("${insights.weeklyConsistency}", appLocale),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = Dates.formatDigits(stringResource(R.string.insights_out_of_100), appLocale),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // Key metrics grid (2 balanced columns so large fonts never cramp)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    StatTile(
                        value = Dates.formatDigits("${insights.currentStreak}", appLocale),
                        label = stringResource(R.string.insights_current_streak),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        accent = brand.streak,
                        suffix = daySuffix,
                    )
                    StatTile(
                        value = Dates.formatDigits("${insights.bestStreak}", appLocale),
                        label = stringResource(R.string.insights_best_streak),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        accent = MaterialTheme.colorScheme.primary,
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
                        value = Dates.formatDigits("${insights.totalCompletions}", appLocale),
                        label = stringResource(R.string.insights_completions),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        accent = brand.success,
                    )
                    StatTile(
                        value = Dates.formatDuration(focusSummary.totalMinutes, resources, appLocale),
                        label = stringResource(R.string.insights_focus_time),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        // Total focus & top focused items breakdown
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    SectionHeader(
                        title = stringResource(R.string.insights_focus_breakdown_title),
                        subtitle = stringResource(R.string.insights_focus_breakdown_subtitle),
                    )
                    Spacer(Modifier.height(spacing.md))

                    if (focusSummary.totalMinutes == 0 && focusSummary.topItems.isEmpty()) {
                        Text(
                            text = stringResource(R.string.insights_focus_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val top = focusSummary.topItems.take(5)
                        val maxItemMinutes = (top.firstOrNull()?.minutes ?: focusSummary.totalMinutes).coerceAtLeast(1)
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

                        top.forEachIndexed { index, itemStat ->
                            FocusedItemRow(
                                stat = itemStat,
                                maxMinutes = maxItemMinutes,
                                isDark = isDark,
                                resources = resources,
                                appLocale = appLocale,
                            )
                            if (index < top.lastIndex || focusSummary.unlinkedMinutes > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = spacing.sm),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                        }

                        if (focusSummary.unlinkedMinutes > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.insights_focus_unlinked),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = Dates.formatDigits(
                                            stringResource(
                                                R.string.insights_focus_sessions_count,
                                                focusSummary.unlinkedSessions,
                                            ),
                                            appLocale,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )
                                }
                                Spacer(Modifier.width(spacing.sm))
                                Text(
                                    text = Dates.formatDuration(focusSummary.unlinkedMinutes, resources, appLocale),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Weekday strengths & streak forecast
        if (insights.bestWeekday != null || insights.worstWeekday != null) {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Column(modifier = Modifier.padding(spacing.lg)) {
                        SectionHeader(title = stringResource(R.string.insights_streak_forecast))
                        Spacer(Modifier.height(spacing.md))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            insights.bestWeekday?.let { day ->
                                StatTile(
                                    value = weekdayName(day, appLocale),
                                    label = stringResource(R.string.insights_best_weekday),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    accent = brand.success,
                                )
                            }
                            insights.worstWeekday?.let { day ->
                                StatTile(
                                    value = weekdayName(day, appLocale),
                                    label = stringResource(R.string.insights_worst_weekday),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    accent = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(spacing.md))
                        Text(
                            text = Dates.formatDigits(
                                stringResource(R.string.insights_forecast_body, insights.projectedStreak),
                                appLocale,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Weekly bar chart
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    SectionHeader(
                        title = stringResource(R.string.insights_last_7_days),
                        subtitle = stringResource(R.string.insights_daily_rate),
                    )
                    Spacer(Modifier.height(spacing.lg))
                    WeeklyBarChart(
                        stats = insights.days.takeLast(7),
                        today = state.today,
                    )
                }
            }
        }

        // 12-week heatmap
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    SectionHeader(
                        title = stringResource(R.string.insights_activity),
                        subtitle = stringResource(R.string.insights_last_12_weeks),
                    )
                    Spacer(Modifier.height(spacing.md))
                    HeatmapGrid(stats = insights.days)
                }
            }
        }

        // Top routines leaderboard
        if (state.routineRates.isNotEmpty()) {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Column(modifier = Modifier.padding(spacing.lg)) {
                        SectionHeader(
                            title = stringResource(R.string.insights_top_routines),
                            subtitle = stringResource(R.string.insights_rate_30_days),
                        )
                        Spacer(Modifier.height(spacing.md))
                        val top = state.routineRates.take(5)
                        top.forEachIndexed { index, (routine, rate) ->
                            RoutineLeaderboardRow(
                                title = routine.title,
                                rate = rate,
                                appLocale = appLocale,
                            )
                            if (index < top.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = spacing.sm),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Task stats
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    SectionHeader(title = stringResource(R.string.insights_tasks))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        StatTile(
                            value = Dates.formatDigits("${state.totalTasks}", appLocale),
                            label = stringResource(R.string.insights_total_tasks),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        StatTile(
                            value = Dates.formatDigits("${state.doneTasks}", appLocale),
                            label = stringResource(R.string.insights_completed),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = brand.success,
                        )
                    }
                    val rate = if (state.totalTasks == 0) 0f else state.doneTasks.toFloat() / state.totalTasks
                    StatTile(
                        value = Dates.formatDigits("${(rate * 100).toInt()}", appLocale),
                        label = stringResource(R.string.insights_completion_rate),
                        modifier = Modifier.fillMaxWidth(),
                        accent = MaterialTheme.colorScheme.secondary,
                        suffix = Dates.percentSymbol(appLocale),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusedItemRow(
    stat: FocusedItemStat,
    maxMinutes: Int,
    isDark: Boolean,
    resources: android.content.res.Resources,
    appLocale: Locale,
) {
    val spacing = MaterialTheme.spacing
    val accent = Color(RoutineColors.hexFor(stat.routine.colorId, isDark))
    val emoji = RoutineEmojis.resolve(stat.routine.icon)
    val fraction = (stat.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 16.sp)
            }
            Spacer(Modifier.width(spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.routine.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Dates.formatDigits(
                        stringResource(R.string.insights_focus_sessions_count, stat.sessionCount),
                        appLocale,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = Dates.formatDuration(stat.minutes, resources, appLocale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
        ThinProgressBar(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            color = accent,
        )
    }
}

/** Localised short weekday name for an ISO weekday number (1=Mon..7=Sun). */
private fun weekdayName(isoWeekday: Int, locale: Locale): String {
    val date = Dates.today()
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(isoWeekday)))
    return Dates.dayOfWeekLabel(date, locale)
}

@Composable
private fun RoutineLeaderboardRow(
    title: String,
    rate: Float,
    appLocale: Locale,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniRing(
            progress = rate,
            size = 38.dp,
            stroke = 4.dp,
            color = MaterialTheme.colorScheme.primary,
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
            Text(
                text = Dates.formatDigits(
                    stringResource(R.string.insights_percent_completion, (rate * 100).toInt()),
                    appLocale,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = Dates.formatPercent((rate * 100).toInt(), appLocale),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
        )
    }
}
