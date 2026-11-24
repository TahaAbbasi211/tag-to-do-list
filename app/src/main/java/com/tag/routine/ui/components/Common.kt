package com.tag.routine.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.R
import com.tag.routine.ui.accessibility.rememberReducedMotion
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import kotlin.math.abs

/**
 * Wraps [content] so the user can swipe it away to delete. Works in both directions and
 * reveals a red delete affordance behind the row. The caller is responsible for showing
 * an undo action, so a stray swipe never destroys data irrecoverably.
 *
 * When settled (`targetValue == Settled` and zero offset), `backgroundContent` emits no
 * composables at all so the coloured layer can never anti-alias or bleed around the
 * rounded corners of [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    confirmImmediately: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Fire the delete once the box is confirmed dismissed. `confirmValueChange` is the
    // hook Material 3 1.3.x exposes for this; the guard makes sure a drag that crosses
    // the threshold more than once still only deletes (and offers one undo) once.
    var dismissHandled by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                if (confirmImmediately) {
                    if (!dismissHandled) {
                        dismissHandled = true
                        onDelete()
                    }
                    true
                } else {
                    onDelete()
                    false
                }
            } else {
                true
            }
        },
    )

    val density = LocalDensity.current
    val revealThresholdPx = remember(density) { with(density) { 84.dp.toPx() } }
    val direction = state.dismissDirection
    val offset = try {
        state.requireOffset()
    } catch (_: IllegalStateException) {
        0f
    }
    val isAtRest = state.currentValue == SwipeToDismissBoxValue.Settled &&
        state.targetValue == SwipeToDismissBoxValue.Settled &&
        abs(offset) <= 2f
    val isSwiping = !isAtRest && direction != SwipeToDismissBoxValue.Settled
    val reveal = if (isSwiping) (abs(offset) / revealThresholdPx).coerceIn(0f, 1f) else 0f
    val deleteColor = MaterialTheme.colorScheme.errorContainer

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            if (isSwiping) {
                val iconAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.CenterEnd
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(deleteColor.copy(alpha = reveal))
                        .padding(horizontal = 20.dp),
                    contentAlignment = iconAlignment,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = reveal),
                    )
                }
            }
        },
    ) {
        content()
    }
}

/** Animated progress ring with a gradient sweep and soft glow. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color? = null,
    startColor: Color? = null,
    endColor: Color? = null,
    animate: Boolean = true,
    contentDescription: String? = null,
) {
    val brand = tagBrand()
    val resolvedTrack = trackColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val resolvedStart = startColor ?: brand.ringStart
    val resolvedEnd = endColor ?: brand.ringEnd
    val target = progress.coerceIn(0f, 1f)
    val reducedMotion = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress-ring",
    )
    val sweep = if (animate && !reducedMotion) animated else target

    Canvas(
        modifier = modifier.semantics {
            contentDescription?.let { this.contentDescription = it }
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        },
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)

        // 1. Background track ring
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
            // 2. Soft shadow / glow arc layered beneath the progress arc
            val glowWidth = stroke + 6.dp.toPx()
            val glowInset = glowWidth / 2f
            val glowSize = Size(size.width - glowWidth, size.height - glowWidth)
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            resolvedStart.copy(alpha = 0.28f),
                            resolvedEnd.copy(alpha = 0.32f),
                            resolvedStart.copy(alpha = 0.28f),
                        ),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(glowInset, glowInset),
                    size = glowSize,
                    style = Stroke(width = glowWidth, cap = StrokeCap.Round),
                )
            }

            // 3. Crisp progress arc with seamless sweep gradient starting and ending at the same color
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(resolvedStart, resolvedEnd, resolvedStart),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
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
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(ringSize)
            .semantics(mergeDescendants = contentDescription != null) {
                contentDescription?.let { this.contentDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
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

/** Circular check control that fills and draws a tick with a spring, scale bounce, and light haptics. */
@Composable
fun AnimatedCheck(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
    stateDescription: String? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (reducedMotion || !checked) {
            scale.snapTo(1f)
        } else {
            scale.animateTo(1.22f, animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing))
            scale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    val fill = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (reducedMotion) {
            fill.snapTo(if (checked) 1f else 0f)
        } else {
            fill.animateTo(
                targetValue = if (checked) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val view = LocalView.current
    val border = MaterialTheme.colorScheme.outline
    val schemeSurface = MaterialTheme.colorScheme.surfaceContainerHigh

    val animBorderColor by animateColorAsState(
        targetValue = if (checked) accent else border,
        animationSpec = tween(durationMillis = 200),
        label = "check-border",
    )
    val animBgColor by animateColorAsState(
        targetValue = if (checked) accent else schemeSurface,
        animationSpec = tween(durationMillis = 200),
        label = "check-bg",
    )

    fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(percent = 50))
            .clickable {
                performHaptic()
                onClick()
            }
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                stateDescription?.let { this.stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .scale(scale.value)
                .clip(RoundedCornerShape(percent = 50)),
        ) {
        val value = fill.value
        val radius = this.size.minDimension / 2f

        if (value <= 0.01f) {
            drawCircle(color = animBgColor, radius = radius)
            drawCircle(
                color = animBorderColor,
                radius = radius - 1.dp.toPx(),
                style = Stroke(width = 1.6.dp.toPx()),
            )
        } else {
            drawCircle(color = accent.copy(alpha = 0.16f + 0.2f * value), radius = radius)
            drawCircle(color = animBgColor, radius = radius * value.coerceAtLeast(0.55f))
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
}

private data class ConfettiParticle(
    val startX: Float,
    val startY: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val rotationSpeed: Float,
    val color: Color,
    val isCircle: Boolean,
)

/**
 * Pure Canvas celebratory confetti explosion when reaching 100% progress.
 * Built with zero external dependencies.
 */
@Composable
fun ConfettiEffect(
    trigger: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!trigger) return
    // Respect the system "remove animations" setting: no confetti motion at all.
    if (rememberReducedMotion()) return

    val anim = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearOutSlowInEasing),
        )
    }

    val particles = remember {
        val palette = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFF6B6B), // Coral
            Color(0xFF4FD9C0), // Mint / Teal
            Color(0xFFA78BFA), // Purple
            Color(0xFF60A5FA), // Sky Blue
            Color(0xFFF472B6), // Pink
            Color(0xFFF59E0B), // Amber
            Color(0xFF34D399), // Emerald
        )
        List(55) { index ->
            val random = java.util.Random(index.toLong() * 37L + 23L)
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 200f + random.nextFloat() * 480f
            ConfettiParticle(
                startX = 0.25f + random.nextFloat() * 0.5f,
                startY = 0.12f + random.nextFloat() * 0.08f,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed - 180f,
                size = 12f + random.nextFloat() * 16f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 14f,
                color = palette[index % palette.size],
                isCircle = index % 3 == 0,
            )
        }
    }

    if (anim.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val t = anim.value
            val gravity = 650f
            val alpha = (1f - (t * 1.15f).coerceIn(0f, 1f))

            particles.forEach { p ->
                val x = (p.startX * size.width) + p.vx * t
                val y = (p.startY * size.height) + p.vy * t + 0.5f * gravity * t * t
                val rot = p.rotationSpeed * t * 360f

                rotate(rot, pivot = Offset(x, y)) {
                    if (p.isCircle) {
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = p.size / 2f,
                            center = Offset(x, y),
                        )
                    } else {
                        drawRect(
                            color = p.color.copy(alpha = alpha),
                            topLeft = Offset(x - p.size / 2f, y - p.size / 3f),
                            size = Size(p.size, p.size * 0.65f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Warm and friendly empty state for the Today screen with a large pulsing icon.
 * Removes the old "+" button in the center so only the FAB remains.
 */
@Composable
fun TodayEmptyState(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val reducedMotion = rememberReducedMotion()
    val pulseScale: Float
    val pulseAlpha: Float
    if (reducedMotion) {
        pulseScale = 1f
        pulseAlpha = 1f
    } else {
        val scaleAnim by infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )
        val alphaAnim by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        pulseScale = scaleAnim
        pulseAlpha = alphaAnim
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { alpha = pulseAlpha },
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.today_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
            lineHeight = 22.sp,
        )
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
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.22f)),
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
                fontWeight = FontWeight.Medium,
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
    AppChip(
        text = text,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        dotColor = dotColor,
        trailingCount = trailingCount,
    )
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
    val reducedMotion = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "thin-bar",
    )
    val barProgress = if (reducedMotion) progress.coerceIn(0f, 1f) else animated
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(barProgress)
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
    AppCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.lg,
                vertical = MaterialTheme.spacing.md,
            ),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!suffix.isNullOrBlank()) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
