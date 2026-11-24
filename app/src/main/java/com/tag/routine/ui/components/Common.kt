package com.tag.routine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.R
import com.tag.routine.ui.motion.CheckDraw
import com.tag.routine.ui.motion.ConfettiBurst
import com.tag.routine.ui.motion.CountUpText
import com.tag.routine.ui.motion.HapticKind
import com.tag.routine.ui.motion.LocalAmbientAnimationsActive
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.floatY
import com.tag.routine.ui.motion.glowPulse
import com.tag.routine.ui.motion.rememberTagHaptics
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
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
    val rawFraction = if (isSwiping) abs(offset) / revealThresholdPx else 0f
    // Elastic rubber-band curve so background intensity ramps smoothly without overshooting.
    val reveal = if (isSwiping) (1f - 1f / (1f + rawFraction * 1.45f)).coerceIn(0f, 1f) else 0f
    val crossedThreshold = isSwiping && rawFraction >= 1f
    LaunchedEffect(crossedThreshold) {
        if (crossedThreshold) {
            haptics(HapticKind.TICK)
        }
    }
    val iconScaleState = animateFloatAsState(
        targetValue = when {
            !isSwiping -> 0.78f
            crossedThreshold -> 1.24f
            else -> 0.80f + 0.30f * rawFraction.coerceIn(0f, 1f)
        },
        animationSpec = if (level.allowsMotion) TagMotion.bouncySpring() else TagMotion.instantTween(0),
        label = "swipe-trash-scale",
    )
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
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = reveal.coerceAtLeast(0.25f)),
                        modifier = Modifier.graphicsLayer {
                            val s = iconScaleState.value
                            scaleX = s
                            scaleY = s
                        },
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
    val level = LocalAnimationLevel.current
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (level.allowsMotion) TagMotion.gentleSpring() else TagMotion.instantTween(),
        label = "progress-ring",
    )
    val sweep = if (animate && level.allowsMotion) animated else target

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
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(checked, level) {
        if (level.isOff || !checked) {
            scale.snapTo(1f)
        } else {
            scale.animateTo(1.22f, animationSpec = TagMotion.instantTween())
            scale.animateTo(1f, animationSpec = TagMotion.bouncySpring())
        }
    }

    val fill = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked, level) {
        if (level.isOff) {
            fill.snapTo(if (checked) 1f else 0f)
        } else {
            fill.animateTo(
                targetValue = if (checked) 1f else 0f,
                animationSpec = TagMotion.bouncySpring(),
            )
        }
    }

    val border = MaterialTheme.colorScheme.outline
    val schemeSurface = MaterialTheme.colorScheme.surfaceContainerHigh

    val colorSpec = if (level.isOff) TagMotion.instantTween<Color>(0) else TagMotion.quickTween()
    val animBorderColor by animateColorAsState(
        targetValue = if (checked) accent else border,
        animationSpec = colorSpec,
        label = "check-border",
    )
    val animBgColor by animateColorAsState(
        targetValue = if (checked) accent else schemeSurface,
        animationSpec = colorSpec,
        label = "check-bg",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(percent = 50))
            .clickable {
                haptics(HapticKind.CONFIRM)
                onClick()
            }
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                stateDescription?.let { this.stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val s = scale.value
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val value = fill.value.coerceIn(0f, 1.15f)
                val radius = this.size.minDimension / 2f

                if (value <= 0.01f) {
                    drawCircle(color = animBgColor, radius = radius)
                    drawCircle(
                        color = animBorderColor,
                        radius = radius - 1.dp.toPx(),
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                } else {
                    drawCircle(color = accent.copy(alpha = 0.16f + 0.2f * value.coerceAtMost(1f)), radius = radius)
                    drawCircle(color = animBgColor, radius = radius * value.coerceIn(0.55f, 1f))
                }
            }

            CheckDraw(
                progressProvider = { ((fill.value - 0.18f) / 0.82f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                strokeWidth = 2.4.dp,
            )
        }
    }
}

/**
 * Pure Canvas celebratory confetti explosion when reaching 100% progress.
 * Built with zero external dependencies and deterministic seeded particles.
 */
@Composable
fun ConfettiEffect(
    trigger: Boolean,
    modifier: Modifier = Modifier,
) {
    ConfettiBurst(trigger = trigger, modifier = modifier)
}

/**
 * Warm, animated sunrise illustration empty state for the Today screen with a
 * softly glowing CTA button when [onAddRoutine] is supplied.
 */
@Composable
fun TodayEmptyState(
    modifier: Modifier = Modifier,
    onAddRoutine: (() -> Unit)? = null,
) {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current
    val brand = tagBrand()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = brand.streak

    val driftState = if (level.allowsAmbient && ambientActive) {
        val transition = rememberInfiniteTransition(label = "sunrise-empty")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TagMotion.DURATION_AMBIENT, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sunrise-phase",
        )
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .floatY(amplitudeDp = 3.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val phase = driftState?.value ?: 0f
                val bob = sin(phase) * 3.dp.toPx()
                val horizonY = size.height * 0.72f
                val centerX = size.width * 0.5f

                // Outer warm sun halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.28f),
                            primaryColor.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(centerX, horizonY - 12.dp.toPx() + bob),
                        radius = size.minDimension * 0.45f,
                    ),
                    radius = size.minDimension * 0.45f,
                    center = Offset(centerX, horizonY - 12.dp.toPx() + bob),
                )

                // Rising sun disc
                val sunRadius = 24.dp.toPx()
                val sunCenter = Offset(centerX, horizonY - 10.dp.toPx() + bob * 0.7f)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(tertiaryColor, primaryColor.copy(alpha = 0.85f)),
                    ),
                    radius = sunRadius,
                    center = sunCenter,
                )

                // Floating decorative geometric accents
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.65f),
                    radius = 5.dp.toPx(),
                    center = Offset(size.width * 0.22f, size.height * 0.34f - bob),
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.55f),
                    radius = 4.dp.toPx(),
                    center = Offset(size.width * 0.80f, size.height * 0.28f + bob),
                )
                drawRoundRect(
                    color = tertiaryColor.copy(alpha = 0.48f),
                    topLeft = Offset(size.width * 0.74f, size.height * 0.52f - bob * 0.6f),
                    size = Size(10.dp.toPx(), 10.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )

                // Gentle horizon arc
                drawLine(
                    color = primaryColor.copy(alpha = 0.35f),
                    start = Offset(size.width * 0.16f, horizonY),
                    end = Offset(size.width * 0.84f, horizonY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = secondaryColor.copy(alpha = 0.28f),
                    start = Offset(size.width * 0.28f, horizonY + 10.dp.toPx()),
                    end = Offset(size.width * 0.72f, horizonY + 10.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
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
        if (onAddRoutine != null) {
            Spacer(Modifier.height(20.dp))
            AppButton(
                text = stringResource(R.string.today_add_routine),
                onClick = onAddRoutine,
                modifier = Modifier.glowPulse(
                    glowColor = MaterialTheme.colorScheme.primary,
                    cornerRadius = 16.dp,
                    maxAlpha = 0.34f,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                shape = RoundedCornerShape(16.dp),
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



/** Thin animated progress bar running smoothly on the GPU transform layer (LTR/RTL aware). */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
    animateFromZero: Boolean = false,
) {
    val level = LocalAnimationLevel.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val target = progress.coerceIn(0f, 1f)
    val fillAnim = remember { Animatable(if (animateFromZero && level.allowsMotion) 0f else target) }

    LaunchedEffect(target, level) {
        if (level.isOff) {
            fillAnim.snapTo(target)
        } else {
            fillAnim.animateTo(
                targetValue = target,
                animationSpec = if (level == com.tag.routine.ui.motion.AnimationLevel.FULL) {
                    TagMotion.emphasizedTween()
                } else {
                    TagMotion.quickTween()
                },
            )
        }
    }

    val barProgress = if (level.isOff) target else fillAnim.value.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    scaleX = barProgress
                    transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0.5f)
                }
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
    StatTileFrame(
        label = label,
        modifier = modifier,
        accent = accent,
        suffix = suffix,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Count-up numeric variant of [StatTile] that preserves layout, suffix styling, and font scaling. */
@Composable
fun AnimatedStatTile(
    count: Int,
    format: (Int) -> String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    suffix: String? = null,
    animateFromZero: Boolean = true,
) {
    StatTileFrame(
        label = label,
        modifier = modifier,
        accent = accent,
        suffix = suffix,
    ) {
        CountUpText(
            value = count,
            format = format,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            animateFromZero = animateFromZero,
        )
    }
}

@Composable
private fun StatTileFrame(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color,
    suffix: String?,
    valueContent: @Composable () -> Unit,
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
                valueContent()
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
