package com.tag.routine.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.ui.theme.tagBrand

/**
 * Wraps [content] so the user can swipe it away to delete. Works in both directions and
 * reveals a red delete affordance behind the row. The caller is responsible for showing
 * an undo action, so a stray swipe never destroys data irrecoverably.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

/** Animated progress ring with a gradient sweep. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color? = null,
    startColor: Color? = null,
    endColor: Color? = null,
    animate: Boolean = true,
) {
    val brand = tagBrand()
    val resolvedTrack = trackColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val resolvedStart = startColor ?: brand.ringStart
    val resolvedEnd = endColor ?: brand.ringEnd
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "progress-ring",
    )
    val sweep = if (animate) animated else target

    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = resolvedTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (sweep > 0.001f) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(resolvedStart, resolvedEnd, resolvedStart),
                    center = center,
                ),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Ring with centred content, used for the hero card and stat tiles. */
@Composable
fun RingWithLabel(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 132.dp,
    strokeWidth: Dp = 13.dp,
    trackColor: Color? = null,
    startColor: Color? = null,
    endColor: Color? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        ProgressRing(
            progress = progress,
            modifier = Modifier.size(ringSize),
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            startColor = startColor,
            endColor = endColor,
        )
        content()
    }
}

/** Circular check control that fills and draws a tick with a spring. */
@Composable
fun AnimatedCheck(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val fill = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        fill.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    val border = MaterialTheme.colorScheme.outline
    val schemeSurface = MaterialTheme.colorScheme.surfaceContainerHigh

    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
    ) {
        val value = fill.value
        val radius = this.size.minDimension / 2f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)

        if (value <= 0.01f) {
            drawCircle(color = schemeSurface, radius = radius)
            drawCircle(
                color = border,
                radius = radius - 1.dp.toPx(),
                style = Stroke(width = 1.6.dp.toPx()),
            )
        } else {
            drawCircle(color = accent.copy(alpha = 0.16f + 0.2f * value), radius = radius)
            drawCircle(color = accent, radius = radius * value.coerceAtLeast(0.55f))
        }

        // Tick: revealed by progress so it draws itself on.
        val w = this.size.width
        val h = this.size.height
        val tick = Path().apply {
            moveTo(w * 0.29f, h * 0.53f)
            lineTo(w * 0.44f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.35f)
        }
        if (value > 0.2f) {
            val reveal = ((value - 0.2f) / 0.8f).coerceIn(0f, 1f)
            drawPath(
                path = tick,
                color = Color.White,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
                alpha = reveal,
            )
        }
    }
}

/** Small pill used for tags, filters and metadata. */
@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/** Selectable pill used for filters and filters. */
@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    trailingCount: Int? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = container,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (dotColor != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) content.copy(alpha = 0.9f) else dotColor),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
            if (trailingCount != null && trailingCount > 0) {
                Text(
                    text = trailingCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) content.copy(alpha = 0.75f) else content.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Section heading with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Empty-state block: icon, headline, supporting line. */
@Composable
fun EmptyState(
    icon: (@Composable () -> Unit),
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** Thin progress bar used for per-tag breakdowns. */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "thin-bar",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(percent = 50))
                .background(color),
        )
    }
}

/** Big numeric readout with a caption, used across the insights screen. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    suffix: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                if (suffix != null) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
