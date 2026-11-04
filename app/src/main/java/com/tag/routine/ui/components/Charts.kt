package com.tag.routine.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tag.routine.data.Dates
import com.tag.routine.data.DayStat
import com.tag.routine.ui.theme.tagBrand
import java.time.LocalDate

/** Horizontal seven-day selector showing completion dots. */
@Composable
fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    dotFor: (LocalDate) -> Float,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    days: Int = 7,
) {
    val brand = tagBrand()
    val start = today.minusDays(3)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(days) { index ->
            val date = start.plusDays(index.toLong())
            val isSelected = date == selected
            val isToday = date == today
            val isFuture = date.isAfter(today)
            val ratio = dotFor(date)
            val container = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
            val contentColor = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(container)
                    .clickable { onSelect(date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = Dates.dayOfWeekLabel(date).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            when {
                                ratio >= 0.999f -> if (isSelected) contentColor else brand.success
                                ratio > 0f -> if (isSelected) contentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                else -> Color.Transparent
                            },
                        ),
                )
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
    val data = stats.takeLast(7)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(data.map { it.ratio to it.date }) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(650))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { stat ->
                val isToday = stat.date == today
                val heightFraction = (stat.ratio * reveal.value).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (stat.hasData) "${(stat.ratio * 100).toInt()}%" else "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight - 34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((barHeight - 34.dp) * heightFraction)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isToday) {
                                        Brush.verticalGradient(listOf(brand.ringEnd, brand.ringStart))
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            ),
                                        )
                                    },
                                ),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = Dates.dayOfWeekLabel(stat.date).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Contribution-style heatmap of the trailing weeks. */
@Composable
fun HeatmapGrid(
    stats: List<DayStat>,
    modifier: Modifier = Modifier,
    weeks: Int = 12,
) {
    val brand = tagBrand()
    val cells = stats.takeLast(weeks * 7)
    val empty = brand.gridEmpty
    val ramp = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        brand.success,
    )
    Column(modifier = modifier) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                week.forEach { stat ->
                    val color = when {
                        !stat.hasData -> empty
                        stat.completed == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                        stat.ratio >= 0.999f -> ramp[3]
                        stat.ratio >= 0.66f -> ramp[2]
                        stat.ratio >= 0.34f -> ramp[1]
                        else -> ramp[0]
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color),
                    )
                }
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
