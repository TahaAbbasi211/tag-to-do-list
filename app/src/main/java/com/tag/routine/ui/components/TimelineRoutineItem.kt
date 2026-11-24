package com.tag.routine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.tag.routine.ui.motion.breathing
import com.tag.routine.ui.motion.glowPulse
import com.tag.routine.ui.motion.pressScale
import com.tag.routine.ui.motion.staggeredEnter
import com.tag.routine.ui.theme.TagTheme
import kotlinx.coroutines.launch

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
    index: Int = 0,
    dayKey: Any = Unit,
    dayDirection: Int = 0,
    prevDone: Boolean = false,
    nextDone: Boolean = false,
    isUpNext: Boolean = false,
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
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val trackBaseColor = accentColor.copy(alpha = 0.24f)
    val trackDoneColor = accentColor.copy(alpha = 0.92f)
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

    // Progressive vertical track entrance when opening or changing days.
    val lineReveal = remember(dayKey) { Animatable(if (level.isOff) 1f else 0f) }
    LaunchedEffect(dayKey, level) {
        if (level.isOff) {
            lineReveal.snapTo(1f)
        } else {
            val delay = TagMotion.staggerDelayMillis(index, stepMs = 30, maxDelayMs = 240)
            lineReveal.snapTo(0f)
            lineReveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TagMotion.levelAdjustedDuration(TagMotion.DURATION_STANDARD, level),
                    delayMillis = delay,
                    easing = TagMotion.EmphasizedDecelerate,
                ),
            )
        }
    }

    val topFillState = animateFloatAsState(
        targetValue = when {
            done && prevDone -> 1f
            done -> 0.55f
            else -> 0f
        },
        animationSpec = if (level.isOff) TagMotion.instantTween(0) else TagMotion.standardTween(),
        label = "track-top-fill",
    )
    val bottomFillState = animateFloatAsState(
        targetValue = when {
            done && nextDone -> 1f
            done -> 0.40f
            else -> 0f
        },
        animationSpec = if (level.isOff) TagMotion.instantTween(0) else TagMotion.standardTween(),
        label = "track-bottom-fill",
    )

    // Completion bounce & expanding ripple on !done -> done transition only.
    var prevDoneState by remember(dayKey) { mutableStateOf(done) }
    val badgeScale = remember { Animatable(1f) }
    val rippleProgress = remember { Animatable(0f) }
    LaunchedEffect(done, level) {
        val newlyCompleted = !prevDoneState && done
        prevDoneState = done
        if (newlyCompleted && level.allowsMotion) {
            launch {
                badgeScale.snapTo(1f)
                badgeScale.animateTo(1.18f, TagMotion.instantTween())
                badgeScale.animateTo(1f, TagMotion.bouncySpring())
            }
            launch {
                rippleProgress.snapTo(0.01f)
                rippleProgress.animateTo(1f, TagMotion.emphasizedTween())
                rippleProgress.snapTo(0f)
            }
        } else if (!done || level.isOff) {
            badgeScale.snapTo(1f)
            rippleProgress.snapTo(0f)
        }
    }

    // Start-to-end animated strikethrough progress.
    val strikeProgress = remember(dayKey) { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done, level) {
        val target = if (done) 1f else 0f
        if (level.isOff) {
            strikeProgress.snapTo(target)
        } else {
            strikeProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = TagMotion.levelAdjustedDuration(TagMotion.DURATION_STANDARD, level),
                    easing = TagMotion.EmphasizedDecelerate,
                ),
            )
        }
    }

    var titleLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .staggeredEnter(index = index, key = dayKey, direction = dayDirection)
            .pressScale(pressedScale = 0.985f)
            .heightIn(min = 72.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isUpNext && !done) {
                    lerp(surfaceColor, accentColor, if (isDark) 0.10f else 0.06f)
                } else {
                    surfaceColor
                },
            )
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

        // Vertical connecting track + colored circular icon badge + completion ripple
        Box(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val circleRadius = 21.dp
            val strokeWidth = 2.dp
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {},
            ) {
                val reveal = lineReveal.value.coerceIn(0f, 1f)
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radiusPx = circleRadius.toPx()
                val strokePx = strokeWidth.toPx()
                val topEnd = (centerY - radiusPx).coerceAtLeast(0f)
                val bottomStart = (centerY + radiusPx).coerceAtMost(size.height)

                if (!isFirst && topEnd > 0f && reveal > 0.01f) {
                    val drawnEnd = topEnd * reveal
                    drawLine(
                        color = trackBaseColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, drawnEnd),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Butt,
                    )
                    val tf = topFillState.value
                    if (tf > 0.01f) {
                        drawLine(
                            color = trackDoneColor,
                            start = Offset(centerX, drawnEnd * (1f - tf)),
                            end = Offset(centerX, drawnEnd),
                            strokeWidth = strokePx + 0.5.dp.toPx(),
                            cap = StrokeCap.Butt,
                        )
                    }
                }
                if (!isLast && bottomStart < size.height && reveal > 0.01f) {
                    val segHeight = size.height - bottomStart
                    val drawnEnd = bottomStart + segHeight * reveal
                    drawLine(
                        color = trackBaseColor,
                        start = Offset(centerX, bottomStart),
                        end = Offset(centerX, drawnEnd),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Butt,
                    )
                    val bf = bottomFillState.value
                    if (bf > 0.01f) {
                        drawLine(
                            color = trackDoneColor,
                            start = Offset(centerX, bottomStart),
                            end = Offset(centerX, bottomStart + segHeight * reveal * bf),
                            strokeWidth = strokePx + 0.5.dp.toPx(),
                            cap = StrokeCap.Butt,
                        )
                    }
                }

                // Expanding completion wave ring
                val rProg = rippleProgress.value
                if (rProg > 0.01f && rProg < 1f) {
                    val waveRadius = radiusPx + rProg * 14.dp.toPx()
                    drawCircle(
                        color = accentColor.copy(alpha = (1f - rProg) * 0.55f),
                        radius = waveRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx() * (1f - rProg * 0.5f)),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(42.dp)
                    .graphicsLayer {
                        val s = badgeScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .then(
                        if (isUpNext && !done) {
                            Modifier
                                .breathing(minScale = 0.98f, maxScale = 1.05f, minAlpha = 0.90f, maxAlpha = 1f)
                                .glowPulse(glowColor = accentColor, cornerRadius = 22.dp, maxAlpha = 0.42f)
                        } else {
                            Modifier
                        },
                    )
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
                color = titleColor,
                maxLines = if (fontScale > 1.5f) 4 else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { titleLayout = it },
                modifier = Modifier.drawWithContent {
                    drawContent()
                    val sp = strikeProgress.value.coerceIn(0f, 1f)
                    val layout = titleLayout
                    if (sp > 0.01f && layout != null && layout.lineCount > 0) {
                        val strokePx = 1.6.dp.toPx()
                        val lineCount = layout.lineCount
                        val totalProgress = sp * lineCount
                        for (line in 0 until lineCount) {
                            val lineFraction = (totalProgress - line).coerceIn(0f, 1f)
                            if (lineFraction <= 0f) continue
                            val left = layout.getLineLeft(line)
                            val right = layout.getLineRight(line)
                            if (right <= left) continue
                            val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + 1.dp.toPx()
                            val startX = if (isRtl) right else left
                            val endX = if (isRtl) {
                                right - (right - left) * lineFraction
                            } else {
                                left + (right - left) * lineFraction
                            }
                            drawLine(
                                color = titleColor.copy(alpha = 0.85f),
                                start = Offset(startX, y),
                                end = Offset(endX, y),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                },
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
