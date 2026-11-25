package com.tag.routine.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tag.routine.ui.theme.tabularNumbers
import com.tag.routine.ui.theme.tagBrand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Multi-blob radial gradient backdrop that drifts smoothly when [AnimationLevel.FULL] and
 * [LocalAmbientAnimationsActive] are true, stays calm/static in [AnimationLevel.REDUCED],
 * and renders a lightweight static wash in [AnimationLevel.OFF].
 *
 * All gradient brushes are cached in [drawWithCache] so zero shader allocations occur per frame.
 * Completely hidden from TalkBack via [clearAndSetSemantics].
 */
@Composable
fun AuroraBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Transparent,
    parallaxOffsetPx: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current

    val phaseAState: androidx.compose.runtime.State<Float>?
    val phaseBState: androidx.compose.runtime.State<Float>?
    if (level.allowsAmbient && ambientActive) {
        val transition = rememberInfiniteTransition(label = "aurora")
        phaseAState = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TagMotion.DURATION_AMBIENT, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "aurora-a",
        )
        phaseBState = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TagMotion.DURATION_AMBIENT_SLOW, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "aurora-b",
        )
    } else {
        phaseAState = null
        phaseBState = null
    }

    val safeColors = if (colors.size >= 3) colors else {
        val fallback = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        )
        colors + fallback.drop(colors.size)
    }

    Box(modifier = modifier.clipToBounds()) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .drawWithCache {
                    val minDim = size.minDimension.coerceAtLeast(1f)
                    val radius1 = minDim * 0.72f
                    val radius2 = minDim * 0.65f
                    val radius3 = minDim * 0.58f

                    val brush1 = Brush.radialGradient(
                        colors = listOf(safeColors[0].copy(alpha = 0.42f), Color.Transparent),
                        center = Offset.Zero,
                        radius = radius1,
                    )
                    val brush2 = Brush.radialGradient(
                        colors = listOf(safeColors[1].copy(alpha = 0.36f), Color.Transparent),
                        center = Offset.Zero,
                        radius = radius2,
                    )
                    val brush3 = Brush.radialGradient(
                        colors = listOf(safeColors[2].copy(alpha = 0.28f), Color.Transparent),
                        center = Offset.Zero,
                        radius = radius3,
                    )

                    onDrawBehind {
                        if (baseColor != Color.Transparent) {
                            drawRect(color = baseColor)
                        }
                        if (level.isOff) {
                            translate(left = size.width * 0.22f, top = size.height * 0.24f) {
                                drawCircle(brush = brush1, radius = radius1, center = Offset.Zero)
                            }
                            return@onDrawBehind
                        }

                        val phaseA = phaseAState?.value ?: 0f
                        val phaseB = phaseBState?.value ?: 0f
                        val parallax = parallaxOffsetPx()
                        val driftX1 = cos(phaseA) * size.width * 0.08f
                        val driftY1 = sin(phaseA) * size.height * 0.07f + parallax * 0.5f

                        val driftX2 = sin(phaseB) * size.width * 0.09f
                        val driftY2 = cos(phaseB) * size.height * 0.06f + parallax * 0.8f

                        val driftX3 = cos(phaseA + 1.4f) * size.width * 0.06f
                        val driftY3 = sin(phaseB + 0.9f) * size.height * 0.08f + parallax

                        translate(left = size.width * 0.18f + driftX1, top = size.height * 0.22f + driftY1) {
                            drawCircle(brush = brush1, radius = radius1, center = Offset.Zero)
                        }
                        translate(left = size.width * 0.82f + driftX2, top = size.height * 0.30f + driftY2) {
                            drawCircle(brush = brush2, radius = radius2, center = Offset.Zero)
                        }
                        translate(left = size.width * 0.54f + driftX3, top = size.height * 0.84f + driftY3) {
                            drawCircle(brush = brush3, radius = radius3, center = Offset.Zero)
                        }
                    }
                },
        )
        content()
    }
}

/**
 * Translucent glass-like surface with a 1dp gradient rim border and soft depth shadow,
 * designed to float cleanly over [AuroraBackground] or brand gradients.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    rimColors: List<Color> = listOf(
        Color.White.copy(alpha = 0.28f),
        Color.White.copy(alpha = 0.08f),
    ),
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = remember(rimColors) {
        BorderStroke(1.dp, Brush.linearGradient(colors = rimColors))
    }
    val shadowMod = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        )
    } else {
        Modifier
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier
                .then(shadowMod)
                .pressScale(pressedScale = 0.985f),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    } else {
        Card(
            modifier = modifier.then(shadowMod),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}

/**
 * Animated integer readout that smoothly counts from its previous (or zero) value to [value]
 * using [TagMotion.EmphasizedDecelerate]. Snaps instantaneously when [AnimationLevel.OFF].
 */
@Composable
fun CountUpText(
    value: Int,
    format: (Int) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = FontWeight.Bold,
    maxLines: Int = 1,
    animateFromZero: Boolean = false,
    revealKey: Any? = null,
) {
    val level = LocalAnimationLevel.current
    val registry = LocalMotionRevealRegistry.current
    val initial = remember(revealKey, level) {
        when {
            level.isOff -> value
            revealKey != null && registry != null -> registry.initialInt(revealKey, value, animateFromZero)
            animateFromZero && level.allowsMotion -> 0
            else -> value
        }
    }
    val anim = remember { Animatable(initial.toFloat()) }
    var lastRendered by remember { mutableFloatStateOf(initial.toFloat()) }

    LaunchedEffect(value, level) {
        if (level.isOff) {
            anim.snapTo(value.toFloat())
            lastRendered = value.toFloat()
            if (revealKey != null) registry?.recordInt(revealKey, value)
            return@LaunchedEffect
        }
        val startValue = lastRendered
        if (revealKey != null) registry?.recordInt(revealKey, value)
        if (startValue == value.toFloat()) {
            anim.snapTo(value.toFloat())
            lastRendered = value.toFloat()
            return@LaunchedEffect
        }
        val durationMs = TagMotion.levelAdjustedDuration(TagMotion.DURATION_EMPHASIZED, level)
        anim.snapTo(startValue)
        anim.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(durationMillis = durationMs, easing = TagMotion.EmphasizedDecelerate),
        )
        lastRendered = value.toFloat()
    }

    val displayed = if (level.isOff) value else anim.value.roundToInt()

    Text(
        text = format(displayed),
        modifier = modifier,
        style = style.tabularNumbers(),
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Spring-driven progress ring with a glowing leading tip, subtle gradient rotation in
 * [AnimationLevel.FULL], optional initial 0->progress fill, and celebration pulse support.
 * Preserves TalkBack range & description semantics.
 */
@Composable
fun AnimatedRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color? = null,
    startColor: Color? = null,
    endColor: Color? = null,
    animateFromZero: Boolean = false,
    pulseTrigger: Int = 0,
    contentDescription: String? = null,
    revealKey: Any? = null,
) {
    val brand = tagBrand()
    val level = LocalAnimationLevel.current
    val registry = LocalMotionRevealRegistry.current
    val ambientActive = LocalAmbientAnimationsActive.current
    val resolvedTrack = trackColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val resolvedStart = startColor ?: brand.ringStart
    val resolvedEnd = endColor ?: brand.ringEnd
    val target = progress.coerceIn(0f, 1f)

    val initialSweep = remember(revealKey, level) {
        when {
            level.isOff -> target
            revealKey != null && registry != null -> registry.initialFloat(revealKey, target, animateFromZero)
            animateFromZero && level.allowsMotion -> 0f
            else -> target
        }
    }
    val sweepAnim = remember { Animatable(initialSweep) }
    LaunchedEffect(target, level) {
        if (level.isOff) {
            sweepAnim.snapTo(target)
            if (revealKey != null) registry?.recordFloat(revealKey, target)
            return@LaunchedEffect
        }
        if (revealKey != null) registry?.recordFloat(revealKey, target)
        if (sweepAnim.value == target) return@LaunchedEffect
        sweepAnim.animateTo(
            targetValue = target,
            animationSpec = if (level == AnimationLevel.FULL) {
                TagMotion.gentleSpring()
            } else {
                TagMotion.quickTween()
            },
        )
    }

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(pulseTrigger, level) {
        if (pulseTrigger > 0 && level.allowsMotion) {
            pulseScale.animateTo(1.08f, animationSpec = TagMotion.instantTween())
            pulseScale.animateTo(1f, animationSpec = TagMotion.bouncySpring())
        } else {
            pulseScale.snapTo(1f)
        }
    }

    val gradientRotationState = if (level.allowsAmbient && ambientActive && target > 0.01f) {
        val transition = rememberInfiniteTransition(label = "ring-rotate")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TagMotion.DURATION_AMBIENT_SLOW * 2, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ring-angle",
        )
    } else {
        null
    }

    Spacer(
        modifier = modifier
            .graphicsLayer {
                val s = pulseScale.value
                scaleX = s
                scaleY = s
            }
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            }
            .drawWithCache {
                val stroke = strokeWidth.toPx()
                val glowWidth = stroke + 6.dp.toPx()
                val tipGlowRadius = stroke * 1.15f
                val inset = stroke / 2f
                val glowInset = glowWidth / 2f
                val arcSize = Size(max(1f, size.width - stroke), max(1f, size.height - stroke))
                val glowSize = Size(max(1f, size.width - glowWidth), max(1f, size.height - glowWidth))
                val ringCenter = Offset(size.width / 2f, size.height / 2f)

                val glowBrush = Brush.sweepGradient(
                    colors = listOf(
                        resolvedStart.copy(alpha = 0.28f),
                        resolvedEnd.copy(alpha = 0.34f),
                        resolvedStart.copy(alpha = 0.28f),
                    ),
                    center = ringCenter,
                )
                val arcBrush = Brush.sweepGradient(
                    colors = listOf(resolvedStart, resolvedEnd, resolvedStart),
                    center = ringCenter,
                )
                val tipGlowBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.72f),
                        resolvedEnd.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                    center = Offset.Zero,
                    radius = tipGlowRadius,
                )

                onDrawBehind {
                    drawArc(
                        color = resolvedTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )

                    val sweep = sweepAnim.value.coerceIn(0f, 1f)
                    if (sweep > 0.001f) {
                        val gradientRotation = gradientRotationState?.value ?: 0f
                        val sweepDeg = 360f * sweep
                        rotate(-90f + gradientRotation, pivot = ringCenter) {
                            drawArc(
                                brush = glowBrush,
                                startAngle = -gradientRotation,
                                sweepAngle = sweepDeg,
                                useCenter = false,
                                topLeft = Offset(glowInset, glowInset),
                                size = glowSize,
                                style = Stroke(width = glowWidth, cap = StrokeCap.Round),
                            )
                            drawArc(
                                brush = arcBrush,
                                startAngle = -gradientRotation,
                                sweepAngle = sweepDeg,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }

                        // Glowing leading tip dot
                        val arcRadius = (size.minDimension - stroke) / 2f
                        val tipRad = ((-90f + sweepDeg) * PI / 180.0).toFloat()
                        val tipX = ringCenter.x + cos(tipRad) * arcRadius
                        val tipY = ringCenter.y + sin(tipRad) * arcRadius
                        translate(left = tipX, top = tipY) {
                            drawCircle(brush = tipGlowBrush, radius = tipGlowRadius, center = Offset.Zero)
                            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = stroke * 0.24f, center = Offset.Zero)
                        }
                    }
                }
            },
    )
}

/**
 * Small Canvas-drawn animated flame icon for streak chips and milestone banners.
 * Pre-allocates paths in [drawWithCache] so zero allocations happen during flicker frames.
 */
@Composable
fun StreakFlame(
    modifier: Modifier = Modifier,
    outerColor: Color = Color(0xFFFF9800),
    innerColor: Color = Color(0xFFFFEB3B),
) {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current

    val flickerState: androidx.compose.runtime.State<Float>?
    val swayState: androidx.compose.runtime.State<Float>?
    if (level.allowsAmbient && ambientActive) {
        val transition = rememberInfiniteTransition(label = "streak-flame")
        flickerState = transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 720, easing = TagMotion.StandardEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flame-scale",
        )
        swayState = transition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 960, easing = TagMotion.StandardEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flame-tilt",
        )
    } else {
        flickerState = null
        swayState = null
    }

    Spacer(
        modifier = modifier
            .size(18.dp)
            .clearAndSetSemantics {}
            .drawWithCache {
                val w = size.width
                val h = size.height
                val outerPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.06f)
                    cubicTo(w * 0.80f, h * 0.34f, w * 0.92f, h * 0.62f, w * 0.50f, h * 0.94f)
                    cubicTo(w * 0.08f, h * 0.62f, w * 0.20f, h * 0.34f, w * 0.50f, h * 0.06f)
                    close()
                }
                val innerPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.36f)
                    cubicTo(w * 0.68f, h * 0.52f, w * 0.72f, h * 0.74f, w * 0.50f, h * 0.88f)
                    cubicTo(w * 0.28f, h * 0.74f, w * 0.32f, h * 0.52f, w * 0.50f, h * 0.36f)
                    close()
                }
                val basePivot = Offset(w * 0.5f, h * 0.88f)
                val outerBrush = Brush.verticalGradient(listOf(innerColor, outerColor))

                onDrawBehind {
                    val flicker = flickerState?.value ?: 1f
                    val sway = swayState?.value ?: 0f
                    rotate(degrees = sway, pivot = basePivot) {
                        scale(scaleX = 1f, scaleY = flicker, pivot = basePivot) {
                            drawPath(path = outerPath, brush = outerBrush)
                            drawPath(path = innerPath, color = innerColor.copy(alpha = 0.92f))
                        }
                    }
                }
            },
    )
}

/**
 * Rolling numeric text where changing digits roll vertically while static separators remain anchored.
 * Reads as a single coherent string for TalkBack and disables rolling in [AnimationLevel.OFF].
 */
@Composable
fun DigitRoll(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = FontWeight.Bold,
) {
    val level = LocalAnimationLevel.current
    val tabularStyle = style.tabularNumbers()

    if (level.isOff) {
        Text(
            text = text,
            modifier = modifier,
            style = tabularStyle,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
        )
        return
    }

    val duration = TagMotion.levelAdjustedDuration(TagMotion.DURATION_QUICK, level)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.semantics { contentDescription = text },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text.forEachIndexed { index, ch ->
                key(text.length - 1 - index) {
                    if (Character.isDigit(ch)) {
                        AnimatedContent(
                            targetState = ch,
                            transitionSpec = {
                                val slideOffset: (Int) -> Int = { height ->
                                    if (targetState > initialState) height / 2 else -height / 2
                                }
                                val exitOffset: (Int) -> Int = { height ->
                                    if (targetState > initialState) -height / 2 else height / 2
                                }
                                ContentTransform(
                                    targetContentEnter = slideInVertically(
                                        animationSpec = tween(duration, easing = TagMotion.EmphasizedDecelerate),
                                        initialOffsetY = slideOffset,
                                    ) + fadeIn(tween(duration)),
                                    initialContentExit = slideOutVertically(
                                        animationSpec = tween(duration, easing = TagMotion.EmphasizedAccelerate),
                                        targetOffsetY = exitOffset,
                                    ) + fadeOut(tween(duration)),
                                )
                            },
                            label = "digit-$index",
                        ) { digit ->
                            Text(
                                text = digit.toString(),
                                style = tabularStyle,
                                color = color,
                                fontWeight = fontWeight,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    } else {
                        Text(
                            text = ch.toString(),
                            style = tabularStyle,
                            color = color,
                            fontWeight = fontWeight,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pure Canvas checkmark that draws its stroke progressively along the path from `[0f..1f]`
 * using pre-allocated [Path] and [PathMeasure] instances (zero per-frame allocations).
 */
@Composable
fun CheckDraw(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 2.4.dp,
) {
    CheckDraw(
        progressProvider = { progress },
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
    )
}

/**
 * Lambda-backed overload of [CheckDraw] so animatable progress can be read strictly in the
 * draw phase without recomposing the parent control.
 */
@Composable
fun CheckDraw(
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 2.4.dp,
) {
    val fullPath = remember { Path() }
    val segmentPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val clamped = progressProvider().coerceIn(0f, 1f)
        if (clamped <= 0.01f) return@Canvas

        val w = size.width
        val h = size.height
        fullPath.reset()
        fullPath.moveTo(w * 0.28f, h * 0.53f)
        fullPath.lineTo(w * 0.44f, h * 0.69f)
        fullPath.lineTo(w * 0.73f, h * 0.34f)

        pathMeasure.setPath(fullPath, forceClosed = false)
        val totalLen = pathMeasure.length
        segmentPath.reset()
        pathMeasure.getSegment(0f, totalLen * clamped, segmentPath, startWithMoveTo = true)

        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** Animated scale + fade wrapper for modal dialog surfaces (used in Phase 3 & dialogs). */
@Composable
fun AnimatedDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val level = LocalAnimationLevel.current
    val progress = remember { Animatable(if (level.isOff) 1f else 0f) }

    LaunchedEffect(level) {
        if (level.isOff) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = if (level == AnimationLevel.FULL) {
                    TagMotion.bouncySpring()
                } else {
                    TagMotion.quickTween()
                },
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value.coerceIn(0f, 1.15f)
            alpha = p.coerceIn(0f, 1f)
            if (level == AnimationLevel.FULL) {
                val scale = 0.90f + 0.10f * p
                scaleX = scale
                scaleY = scale
            }
        },
        content = content,
    )
}

/** Smooth spring entrance wrapper for ModalBottomSheet body content. */
@Composable
fun AnimatedSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val level = LocalAnimationLevel.current
    val progress = remember { Animatable(if (level.isOff) 1f else 0f) }

    LaunchedEffect(level) {
        if (level.isOff) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = if (level == AnimationLevel.FULL) {
                    TagMotion.snappySpring()
                } else {
                    TagMotion.quickTween()
                },
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value.coerceIn(0f, 1.05f)
            alpha = p.coerceIn(0f, 1f)
            if (level == AnimationLevel.FULL) {
                translationY = (1f - p.coerceIn(0f, 1f)) * 24f
            }
        },
        content = content,
    )
}
