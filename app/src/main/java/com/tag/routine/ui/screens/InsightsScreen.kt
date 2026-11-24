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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.R
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.FocusedItemStat
import com.tag.routine.data.ReportedItem
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEmojis
import com.tag.routine.data.WeekComparison
import com.tag.routine.data.WeeklyReport
import com.tag.routine.data.WeeklyReportEngine
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.HeatmapGrid
import com.tag.routine.ui.components.LargeScreenFrame
import com.tag.routine.ui.components.MiniRing
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.ScreenHeader
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.StatTile
import com.tag.routine.ui.components.StatTilePair
import com.tag.routine.ui.components.ThinProgressBar
import com.tag.routine.ui.components.WeeklyBarChart
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

@Composable
fun InsightsScreen(state: UiState) {
    LargeScreenFrame {
        InsightsContent(state)
    }
}

@Composable
private fun InsightsContent(state: UiState) {
    val insights = state.insights
    val focusSummary = state.focusSummary
    val spacing = MaterialTheme.spacing
    val brand = tagBrand()
    val resources = LocalContext.current.resources
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val fontScale = LocalDensity.current.fontScale
    val daySuffix = stringResource(R.string.streak_days_short)

    val hasAnyInsightsData = insights != null &&
        insights.days.isNotEmpty() && (
            insights.days.any { it.hasData || it.taskCompleted > 0 } ||
                insights.totalCompletions > 0 ||
                state.routineRates.isNotEmpty() ||
                state.totalTasks > 0 ||
                focusSummary.totalMinutes > 0 ||
                focusSummary.totalSessions > 0
            )

    if (insights == null || !hasAnyInsightsData) {
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

    val calendarType = state.settings.calendarType
    val firstDayOfWeek = remember(calendarType, appLocale) {
        WeeklyReportEngine.defaultFirstDayOfWeek(calendarType, appLocale)
    }
    val weeklyReport = remember(insights, state.routineRates, state.today, firstDayOfWeek) {
        WeeklyReportEngine.compute(
            insights = insights,
            routineRates = state.routineRates,
            today = state.today,
            firstDayOfWeek = firstDayOfWeek,
        )
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
                        ringSize = if (fontScale > 1.4f) 200.dp else 136.dp,
                        strokeWidth = 12.dp,
                        trackColor = brand.chartTrack,
                        startColor = MaterialTheme.colorScheme.primary,
                        endColor = brand.success,
                        contentDescription = Dates.formatDigits(
                            stringResource(R.string.a11y_progress, insights.weeklyConsistency),
                            appLocale,
                        ),
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
                StatTilePair { tileModifier ->
                    StatTile(
                        value = Dates.formatDigits("${insights.currentStreak}", appLocale),
                        label = stringResource(R.string.insights_current_streak),
                        modifier = tileModifier,
                        accent = brand.streak,
                        suffix = daySuffix,
                    )
                    StatTile(
                        value = Dates.formatDigits("${insights.bestStreak}", appLocale),
                        label = stringResource(R.string.insights_best_streak),
                        modifier = tileModifier,
                        accent = MaterialTheme.colorScheme.primary,
                        suffix = daySuffix,
                    )
                }
                StatTilePair { tileModifier ->
                    StatTile(
                        value = Dates.formatDigits("${insights.totalCompletions}", appLocale),
                        label = stringResource(R.string.insights_completions),
                        modifier = tileModifier,
                        accent = brand.success,
                    )
                    StatTile(
                        value = Dates.formatDuration(focusSummary.totalMinutes, resources, appLocale),
                        label = stringResource(R.string.insights_focus_time),
                        modifier = tileModifier,
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        // Weekly report
        item {
            WeeklyReportCard(
                report = weeklyReport,
                daySuffix = daySuffix,
                appLocale = appLocale,
                calendarType = calendarType,
            )
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
                        StatTilePair { tileModifier ->
                            insights.bestWeekday?.let { day ->
                                StatTile(
                                    value = weekdayName(day, appLocale),
                                    label = stringResource(R.string.insights_best_weekday),
                                    modifier = tileModifier,
                                    accent = brand.success,
                                )
                            }
                            insights.worstWeekday?.let { day ->
                                StatTile(
                                    value = weekdayName(day, appLocale),
                                    label = stringResource(R.string.insights_worst_weekday),
                                    modifier = tileModifier,
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
                    HeatmapGrid(
                        stats = insights.days,
                        today = state.today,
                        calendarType = calendarType,
                    )
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
                    StatTilePair { tileModifier ->
                        StatTile(
                            value = Dates.formatDigits("${state.totalTasks}", appLocale),
                            label = stringResource(R.string.insights_total_tasks),
                            modifier = tileModifier,
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        StatTile(
                            value = Dates.formatDigits("${state.doneTasks}", appLocale),
                            label = stringResource(R.string.insights_completed),
                            modifier = tileModifier,
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

@Composable
private fun WeeklyReportCard(
    report: WeeklyReport,
    daySuffix: String,
    appLocale: Locale,
    calendarType: CalendarType,
) {
    val spacing = MaterialTheme.spacing
    val brand = tagBrand()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val baseSubtitle = if (report.isCurrentWeekIncomplete) {
        stringResource(
            R.string.insights_weekly_report_subtitle_partial,
            report.elapsedDaysInCurrentWeek,
            WeeklyReportEngine.DAYS_IN_WEEK,
        )
    } else {
        stringResource(R.string.insights_weekly_report_subtitle_complete)
    }
    val weekRange = "${Dates.shortDate(report.currentWeekStart, appLocale, calendarType)} – " +
        Dates.shortDate(report.currentWeekEnd, appLocale, calendarType)
    val subtitle = Dates.formatDigits("$baseSubtitle · $weekRange", appLocale)

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
            SectionHeader(
                title = stringResource(R.string.insights_weekly_report_title),
                subtitle = subtitle,
            )

            StatTilePair { tileModifier ->
                StatTile(
                    value = report.currentWeekPercent?.let { Dates.formatPercent(it, appLocale) } ?: "–",
                    label = stringResource(R.string.insights_this_week),
                    modifier = tileModifier,
                    accent = MaterialTheme.colorScheme.primary,
                )
                StatTile(
                    value = report.previousWeekPercent?.let { Dates.formatPercent(it, appLocale) } ?: "–",
                    label = stringResource(R.string.insights_last_week),
                    modifier = tileModifier,
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }

            StatTilePair { tileModifier ->
                StatTile(
                    value = Dates.formatDigits("${report.perfectDaysCurrentWeek}", appLocale),
                    label = stringResource(R.string.insights_perfect_days),
                    modifier = tileModifier,
                    accent = brand.success,
                    suffix = daySuffix,
                )
                StatTile(
                    value = Dates.formatDigits("${report.currentStreak}", appLocale),
                    label = stringResource(R.string.insights_streak),
                    modifier = tileModifier,
                    accent = brand.streak,
                    suffix = daySuffix,
                )
            }

            val (statusBg, statusFg, statusText) = when (report.comparison) {
                WeekComparison.IMPROVED -> Triple(
                    brand.success.copy(alpha = 0.14f),
                    brand.success,
                    Dates.formatDigits(
                        stringResource(
                            R.string.insights_weekly_compare_up,
                            Dates.formatPercent(report.deltaPercent ?: 0, appLocale),
                        ),
                        appLocale,
                    ),
                )
                WeekComparison.DECLINED -> Triple(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    Dates.formatDigits(
                        stringResource(
                            R.string.insights_weekly_compare_down,
                            Dates.formatPercent(abs(report.deltaPercent ?: 0), appLocale),
                        ),
                        appLocale,
                    ),
                )
                WeekComparison.STEADY -> Triple(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    Dates.formatDigits(
                        stringResource(
                            R.string.insights_weekly_compare_steady,
                            Dates.formatPercent(report.currentWeekPercent ?: 0, appLocale),
                        ),
                        appLocale,
                    ),
                )
                WeekComparison.INCOMPLETE_WEEK -> Triple(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Dates.formatDigits(
                        stringResource(
                            R.string.insights_weekly_compare_incomplete,
                            WeeklyReportEngine.DAYS_IN_WEEK,
                        ),
                        appLocale,
                    ),
                )
                WeekComparison.NO_BASELINE -> Triple(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    stringResource(R.string.insights_weekly_compare_no_baseline),
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = statusBg,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = statusFg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }

            if (report.bestItem != null || report.weakestItem != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                report.bestItem?.let { best ->
                    WeeklyHighlightRow(
                        label = stringResource(R.string.insights_weekly_best_item),
                        item = best,
                        isDark = isDark,
                        appLocale = appLocale,
                        accentColor = brand.success,
                    )
                }
                report.weakestItem?.let { weakest ->
                    WeeklyHighlightRow(
                        label = stringResource(R.string.insights_weekly_weakest_item),
                        item = weakest,
                        isDark = isDark,
                        appLocale = appLocale,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else if (!report.hasAnyWeekData) {
                Text(
                    text = stringResource(R.string.insights_weekly_no_items),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyHighlightRow(
    label: String,
    item: ReportedItem,
    isDark: Boolean,
    appLocale: Locale,
    accentColor: Color,
) {
    val spacing = MaterialTheme.spacing
    val itemAccent = Color(RoutineColors.hexFor(item.routine.colorId, isDark))
    val emoji = RoutineEmojis.resolve(item.routine.icon)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(itemAccent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 16.sp)
        }
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.routine.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = Dates.formatPercent(item.percent, appLocale),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
