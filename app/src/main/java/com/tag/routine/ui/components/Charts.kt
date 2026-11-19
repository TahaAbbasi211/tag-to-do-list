package com.tag.routine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.tag.routine.R
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.DayStat
import com.tag.routine.data.HeatmapCalendar
import com.tag.routine.data.HeatmapIntensity
import com.tag.routine.data.WeeklyReportEngine
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Horizontal seven-day selector showing completion dots and the active month/year header. */
@Composable
fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    dotFor: (LocalDate) -> Float,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    days: Int = 7,
    calendarType: CalendarType = CalendarType.GREGORIAN,
    showMonthHeader: Boolean = true,
    hasItemsFor: (LocalDate) -> Boolean = { dotFor(it) > 0f },
) {
    val brand = tagBrand()
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val start = today.minusDays(3)
    Column(modifier = modifier.fillMaxWidth()) {
        if (showMonthHeader) {
            Text(
                text = Dates.monthYearTitle(selected, appLocale, calendarType),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(days) { index ->
                val date = start.plusDays(index.toLong())
                val isSelected = date == selected
                val isToday = date == today
                val isFuture = date.isAfter(today)
                val ratio = dotFor(date)
                val hasItems = hasItemsFor(date)
                val container by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = tween(220),
                    label = "week-bg",
                )
                val contentColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(220),
                    label = "week-fg",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(container)
                        .then(
                            if (!isSelected) {
                                Modifier.border(
                                    1.dp,
                                    if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp),
                                )
                            } else Modifier
                        )
                        .clickable { onSelect(date) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = Dates.shortDayName(date, appLocale),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.85f),
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Dates.formatDigits(Dates.dayOfMonth(date, calendarType).toString(), appLocale),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                when {
                                    !hasItems -> Color.Transparent
                                    ratio >= 0.999f -> if (isSelected) contentColor else brand.success
                                    ratio > 0f -> if (isSelected) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    else -> if (isSelected) contentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** Animated seven-day bar chart of completion ratios. */
@Composable
fun WeeklyBarChart(
    stats: List<DayStat>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 132.dp,
) {
    val brand = tagBrand()
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val fontScale = LocalDensity.current.fontScale
    val useHorizontalScroll = fontScale > 1.25f
    val trackHeight = (barHeight - 34.dp).coerceAtLeast(64.dp)
    val data = stats.takeLast(7)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(data.map { it.ratio to it.date }) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(650))
    }

    val rowModifier = if (useHorizontalScroll) {
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { stat ->
                val isToday = stat.date == today
                val heightFraction = (stat.ratio * reveal.value).coerceIn(0f, 1f)
                val columnModifier = if (useHorizontalScroll) {
                    Modifier.width((42.dp * fontScale.coerceAtMost(2.2f)))
                } else {
                    Modifier.weight(1f)
                }
                Column(
                    modifier = columnModifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (stat.hasData) Dates.formatPercent((stat.ratio * 100).toInt(), appLocale) else "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brand.chartTrack),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight * heightFraction)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isToday) {
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary,
                                            ),
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                            ),
                                        )
                                    },
                                ),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = Dates.dayOfWeekLabel(stat.date, appLocale).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private data class PlacedMonthLabel(
    val text: String,
    val startDp: Dp,
)

/**
 * Interactive 12-week activity heatmap aligned to calendar weeks.
 *
 * Weeks flow chronologically from Start to End (automatically mirroring in RTL), and rows run
 * from the active calendar's first day of week (Saturday in Jalali or Persian Gregorian, Monday
 * otherwise). Days without scheduled items use a neutral bordered swatch distinct from
 * 0%-completion days, tapping a day displays its item and completion summary, and narrow screens
 * or large system fonts switch the week grid to horizontal scroll instead of clipping cells or
 * weekday/month labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeatmapGrid(
    stats: List<DayStat>,
    modifier: Modifier = Modifier,
    weeks: Int = HeatmapCalendar.DEFAULT_WEEKS,
    today: LocalDate = Dates.today(),
    calendarType: CalendarType = CalendarType.GREGORIAN,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalContext.current.resources
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val density = LocalDensity.current

    val firstDayOfWeek = remember(calendarType, appLocale) {
        WeeklyReportEngine.defaultFirstDayOfWeek(calendarType, appLocale)
    }
    val weekColumns = remember(stats, today, weeks, firstDayOfWeek) {
        HeatmapCalendar.build(
            stats = stats,
            today = today,
            weeks = weeks,
            firstDayOfWeek = firstDayOfWeek,
        )
    }
    val monthAnchors = remember(weekColumns, calendarType) {
        HeatmapCalendar.monthLabels(weekColumns, calendarType)
    }
    val weekdayLabels = remember(firstDayOfWeek, appLocale) {
        HeatmapCalendar.weekdayDates(firstDayOfWeek).map { date ->
            Dates.shortDayName(date, appLocale)
        }
    }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val selectedCell = remember(weekColumns, selectedDate) {
        selectedDate?.let { target ->
            weekColumns.asSequence()
                .flatMap { it.cells.asSequence() }
                .firstOrNull { !it.isFuture && it.date == target }
        }
    }

    val neutralColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val neutralBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val zeroDoneColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryColor = MaterialTheme.colorScheme.primary
    val level1Color = lerp(zeroDoneColor, primaryColor, 0.34f)
    val level2Color = lerp(zeroDoneColor, primaryColor, 0.58f)
    val level3Color = lerp(zeroDoneColor, primaryColor, 0.80f)
    val completeColor = primaryColor
    val selectedBorderColor = MaterialTheme.colorScheme.onSurface
    val todayBorderColor = MaterialTheme.colorScheme.primary

    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val maxLabelWidthDp = remember(weekdayLabels, labelStyle, density) {
        with(density) {
            weekdayLabels.maxOfOrNull { label ->
                textMeasurer.measure(text = label, style = labelStyle).size.width
            }?.toDp() ?: 16.dp
        }.coerceAtLeast(16.dp)
    }
    val maxLabelHeightDp = remember(weekdayLabels, labelStyle, density) {
        with(density) {
            weekdayLabels.maxOfOrNull { label ->
                textMeasurer.measure(text = label, style = labelStyle).size.height
            }?.toDp() ?: 16.dp
        }
    }

    val neutralLabel = stringResource(R.string.insights_heatmap_legend_neutral)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellGap = 4.dp
            val labelGap = 8.dp
            val minCellSize = 18.dp
            val weekCount = weekColumns.size.coerceAtLeast(1)
            val availableWidth = (maxWidth - maxLabelWidthDp - labelGap).coerceAtLeast(0.dp)
            val fitCellWidth = (availableWidth - cellGap * (weekCount - 1)) / weekCount
            val needsScroll = fitCellWidth < minCellSize
            val cellWidth = if (needsScroll) minCellSize else fitCellWidth
            val rowHeight = max(cellWidth.coerceIn(minCellSize, 24.dp), maxLabelHeightDp + 2.dp)
            val cornerRadius = (minOf(cellWidth, rowHeight) * 0.28f).coerceIn(4.dp, 7.dp)
            val cellShape = RoundedCornerShape(cornerRadius)
            val gridWidth = cellWidth * weekCount + cellGap * (weekCount - 1)

            val placedMonthLabels = remember(monthAnchors, cellWidth, cellGap, gridWidth, appLocale, calendarType, labelStyle, density) {
                val stepPx = with(density) { (cellWidth + cellGap).toPx() }
                val totalGridWidthPx = with(density) { gridWidth.toPx() }
                val minGapPx = with(density) { 6.dp.toPx() }
                var nextAllowedStartPx = 0f
                buildList {
                    monthAnchors.forEachIndexed { idx, anchor ->
                        val text = Dates.shortMonthName(anchor.referenceDate, appLocale, calendarType)
                        val textWidthPx = textMeasurer.measure(text = text, style = labelStyle).size.width.toFloat()
                        val rawStartPx = anchor.columnIndex * stepPx
                        val startPx = rawStartPx.coerceAtMost((totalGridWidthPx - textWidthPx).coerceAtLeast(0f))
                        val nextAnchorStartPx = monthAnchors.getOrNull(idx + 1)?.let { it.columnIndex * stepPx } ?: Float.MAX_VALUE
                        if (startPx >= nextAllowedStartPx && startPx + textWidthPx + minGapPx <= nextAnchorStartPx) {
                            add(PlacedMonthLabel(text = text, startDp = with(density) { startPx.toDp() }))
                            nextAllowedStartPx = startPx + textWidthPx + minGapPx
                        } else if (idx == monthAnchors.lastIndex && startPx >= nextAllowedStartPx && startPx + textWidthPx <= totalGridWidthPx) {
                            add(PlacedMonthLabel(text = text, startDp = with(density) { startPx.toDp() }))
                            nextAllowedStartPx = startPx + textWidthPx + minGapPx
                        }
                    }
                }
            }
            val monthRowHeight = remember(placedMonthLabels, labelStyle, maxLabelHeightDp, density) {
                val measured = with(density) {
                    placedMonthLabels.maxOfOrNull { label ->
                        textMeasurer.measure(text = label.text, style = labelStyle).size.height
                    }?.toDp() ?: 0.dp
                }
                max(maxLabelHeightDp, measured)
            }

            val scrollState = rememberScrollState()
            LaunchedEffect(weekCount, needsScroll) {
                if (needsScroll) {
                    val maxScroll = snapshotFlow { scrollState.maxValue }
                        .first { it in 1 until Int.MAX_VALUE }
                    scrollState.scrollTo(maxScroll)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.width(maxLabelWidthDp),
                    verticalArrangement = Arrangement.spacedBy(cellGap),
                ) {
                    Spacer(Modifier.height(monthRowHeight))
                    weekdayLabels.forEach { label ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(labelGap))

                val weeksContainerModifier = if (needsScroll) {
                    Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                } else {
                    Modifier.weight(1f)
                }

                Column(
                    modifier = weeksContainerModifier,
                    verticalArrangement = Arrangement.spacedBy(cellGap),
                ) {
                    Box(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(monthRowHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        placedMonthLabels.forEach { label ->
                            Text(
                                text = label.text,
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = label.startDp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.width(gridWidth),
                        horizontalArrangement = Arrangement.spacedBy(cellGap),
                    ) {
                        weekColumns.forEach { weekColumn ->
                            Column(
                                modifier = Modifier.width(cellWidth),
                                verticalArrangement = Arrangement.spacedBy(cellGap),
                            ) {
                                weekColumn.cells.forEach { cell ->
                                    if (cell.isFuture) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowHeight),
                                        )
                                    } else {
                                        val isSelected = selectedDate == cell.date
                                        val bgColor = when (cell.intensity) {
                                            HeatmapIntensity.FUTURE -> Color.Transparent
                                            HeatmapIntensity.NEUTRAL -> neutralColor
                                            HeatmapIntensity.ZERO_DONE -> zeroDoneColor
                                            HeatmapIntensity.LEVEL_1 -> level1Color
                                            HeatmapIntensity.LEVEL_2 -> level2Color
                                            HeatmapIntensity.LEVEL_3 -> level3Color
                                            HeatmapIntensity.COMPLETE -> completeColor
                                        }
                                        val borderModifier = when {
                                            isSelected -> Modifier.border(
                                                width = 2.dp,
                                                color = selectedBorderColor,
                                                shape = cellShape,
                                            )
                                            cell.isToday -> Modifier.border(
                                                width = 1.5.dp,
                                                color = todayBorderColor,
                                                shape = cellShape,
                                            )
                                            cell.intensity == HeatmapIntensity.NEUTRAL -> Modifier.border(
                                                width = 1.dp,
                                                color = neutralBorder,
                                                shape = cellShape,
                                            )
                                            else -> Modifier
                                        }
                                        val cellStat = cell.stat
                                        val dateSummary = Dates.dayMonth(cell.date, appLocale, calendarType)
                                        val a11ySummary = Dates.formatDigits(
                                            if (cellStat != null && cellStat.hasData) {
                                                "$dateSummary ${cellStat.completed}/${cellStat.scheduled}"
                                            } else {
                                                "$dateSummary $neutralLabel"
                                            },
                                            appLocale,
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowHeight)
                                                .clip(cellShape)
                                                .background(bgColor)
                                                .then(borderModifier)
                                                .semantics { contentDescription = a11ySummary }
                                                .clickable {
                                                    selectedDate = if (isSelected) null else cell.date
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chronological endpoints aligned to the grid direction (Start = oldest, End = today).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = Dates.formatDigits(
                    stringResource(R.string.insights_heatmap_weeks_ago, weekColumns.size),
                    appLocale,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.day_today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Legend distinguishing neutral empty days from 0%-completed and intensity levels.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(neutralColor)
                        .border(1.dp, neutralBorder, RoundedCornerShape(3.dp)),
                )
                Text(
                    text = neutralLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.insights_heatmap_legend_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(zeroDoneColor, level1Color, level2Color, level3Color, completeColor).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(swatch),
                    )
                }
                Text(
                    text = stringResource(R.string.insights_heatmap_legend_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Selected day summary panel (updates on tap).
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            if (selectedCell != null) {
                val stat = selectedCell.stat
                val daysDiff = ChronoUnit.DAYS.between(today, selectedCell.date)
                val formattedDay = Dates.dayMonth(selectedCell.date, appLocale, calendarType)
                val dateLabel = if (daysDiff in -1L..0L) {
                    "${Dates.relativeDayLabel(selectedCell.date, today, resources, appLocale, calendarType)} · $formattedDay"
                } else {
                    formattedDay
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Text(
                            text = Dates.formatDigits(dateLabel, appLocale),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (stat != null && stat.hasData) {
                            val pct = (stat.ratio * 100f).roundToInt().coerceIn(0, 100)
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            ) {
                                Text(
                                    text = Dates.formatPercent(pct, appLocale),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    Text(
                        text = if (stat != null && stat.hasData) {
                            Dates.formatDigits(
                                stringResource(
                                    R.string.insights_heatmap_day_summary,
                                    stat.scheduled,
                                    stat.completed,
                                ),
                                appLocale,
                            )
                        } else {
                            stringResource(R.string.insights_heatmap_day_neutral)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.insights_heatmap_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
        }
    }
}

/** Compact ring plus label used in the routine leaderboard. */
@Composable
fun MiniRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    stroke: androidx.compose.ui.unit.Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "mini-ring",
    )
    Canvas(modifier = modifier.size(size)) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2f
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        drawArc(
            color = color.copy(alpha = 0.16f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        if (animated > 0.01f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}
