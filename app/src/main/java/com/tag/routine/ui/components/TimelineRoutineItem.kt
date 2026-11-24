package com.tag.routine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.data.AppSettings
import com.tag.routine.data.RoutineColors
import com.tag.routine.ui.accessibility.AccessibilityLabels
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.theme.TagTheme

/**
 * Single-day vertical timeline item for a routine:
 * start-side time slot + continuous vertical connector line with colored circular emoji badge +
 * title (strikethrough when completed) + colored completion check on the trailing edge.
 *
 * Because Compose mirrors [Row] order in RTL locales, the vertical track automatically sits
 * on the left in LTR and on the right in RTL.
 */
@Composable
fun TimelineRoutineItem(
    title: String,
    emoji: String,
    accentColor: Color,
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    timeLabel: String? = null,
    subtitle: String? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    doneLabel: String? = null,
    notDoneLabel: String? = null,
    noTimeLabel: String? = null,
    accessibilityLocale: java.util.Locale = java.util.Locale.getDefault(),
    customAccessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fontScale = LocalDensity.current.fontScale
    val lineColor = accentColor.copy(alpha = 0.35f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    // One merged semantics node reads the whole row at once (title, time, status).
    val rowLabels = if (doneLabel != null && notDoneLabel != null) {
        AccessibilityLabels.routineRow(
            title = title,
            timeLabel = timeLabel,
            done = done,
            doneLabel = doneLabel,
            notDoneLabel = notDoneLabel,
            noTimeLabel = noTimeLabel ?: "",
            locale = accessibilityLocale,
        )
    } else {
        null
    }

    val level = LocalAnimationLevel.current
    val colorSpec = if (level.isOff) TagMotion.instantTween<Color>(0) else TagMotion.quickTween()
    val circleFill by animateColorAsState(
        targetValue = if (done) lerp(accentColor, surfaceColor, if (isDark) 0.25f else 0.15f) else accentColor,
        animationSpec = colorSpec,
        label = "timeline-circle-fill",
    )
    val titleColor by animateColorAsState(
        targetValue = if (done) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.76f else 0.68f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "timeline-title-color",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .combinedClickable(
                onClick = { onClick?.invoke() ?: onToggle() },
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {
                rowLabels?.let { contentDescription = it }
                if (customAccessibilityActions.isNotEmpty()) {
                    customActions = customAccessibilityActions
                }
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed-width start time slot keeps the vertical timeline axis aligned across timed and untimed rows.
        Box(
            modifier = Modifier
                .width(if (fontScale > 1.4f) 84.dp else 54.dp)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!timeLabel.isNullOrBlank()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Vertical connecting track + colored circular icon badge
        Box(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val circleRadius = 21.dp
            val strokeWidth = 2.dp
            Canvas(modifier = Modifier.matchParentSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radiusPx = circleRadius.toPx()
                val strokePx = strokeWidth.toPx()
                val topEnd = (centerY - radiusPx).coerceAtLeast(0f)
                val bottomStart = (centerY + radiusPx).coerceAtMost(size.height)

                if (!isFirst && topEnd > 0f) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, topEnd),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Butt,
                    )
                }
                if (!isLast && bottomStart < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, bottomStart),
                        end = Offset(centerX, size.height),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Butt,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(circleFill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emoji,
                    fontSize = 19.sp,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title & optional subtitle
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
                color = titleColor,
                maxLines = if (fontScale > 1.5f) 4 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
            Spacer(Modifier.width(4.dp))
        }

        AnimatedCheck(
            checked = done,
            onClick = onToggle,
            size = 26.dp,
            accent = accentColor,
            stateDescription = if (doneLabel != null && notDoneLabel != null) {
                AccessibilityLabels.checkState(done, doneLabel, notDoneLabel)
            } else {
                null
            },
        )
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun TimelineRoutineSampleList(
    isDark: Boolean,
    isRtl: Boolean,
) {
    val items = if (isRtl) {
        listOf(
            Triple("نوشیدن آب", "💧", true) to "۰۷:۰۰",
            Triple("کشش صبحگاهی", "🧘", true) to "۰۷:۱۵",
            Triple("مطالعه عمیق", "📖", false) to "۰۹:۰۰",
            Triple("پیاده‌روی عصر", "🚶", false) to null,
        )
    } else {
        listOf(
            Triple("Drink a glass of water", "💧", true) to "07:00",
            Triple("Morning stretch", "🧘", true) to "07:15",
            Triple("Deep study session", "📖", false) to "09:00",
            Triple("Evening walk", "🚶", false) to null,
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            items.forEachIndexed { index, (meta, time) ->
                val (title, emoji, done) = meta
                val color = Color(RoutineColors.hexFor(index, isDark))
                TimelineRoutineItem(
                    title = title,
                    emoji = emoji,
                    accentColor = color,
                    done = done,
                    timeLabel = time,
                    isFirst = index == 0,
                    isLast = index == items.lastIndex,
                    onToggle = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "TimelineRoutineItem - Light LTR")
@Composable
private fun TimelineRoutineItemLightPreview() {
    TagTheme(themeMode = AppSettings.ThemeMode.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            TimelineRoutineSampleList(isDark = false, isRtl = false)
        }
    }
}

@Preview(showBackground = true, name = "TimelineRoutineItem - Dark LTR")
@Composable
private fun TimelineRoutineItemDarkPreview() {
    TagTheme(themeMode = AppSettings.ThemeMode.DARK) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            TimelineRoutineSampleList(isDark = true, isRtl = false)
        }
    }
}

@Preview(showBackground = true, name = "TimelineRoutineItem - Light RTL (fa)")
@Composable
private fun TimelineRoutineItemRtlLightPreview() {
    TagTheme(themeMode = AppSettings.ThemeMode.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            TimelineRoutineSampleList(isDark = false, isRtl = true)
        }
    }
}

@Preview(showBackground = true, name = "TimelineRoutineItem - Dark RTL (fa)")
@Composable
private fun TimelineRoutineItemRtlDarkPreview() {
    TagTheme(themeMode = AppSettings.ThemeMode.DARK) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            TimelineRoutineSampleList(isDark = true, isRtl = true)
        }
    }
}
