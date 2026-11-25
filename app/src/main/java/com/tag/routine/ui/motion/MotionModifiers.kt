package com.tag.routine.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Tactile spring press scale (`0.96f` by default) applied strictly on the graphics layer
 * (zero recompositions during animation) with optional haptic feedback.
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    haptic: HapticKind? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val level = LocalAnimationLevel.current
    val triggerHaptic = rememberTagHaptics()

    val isPressed: Boolean
    val pointerModifier: Modifier
    if (interactionSource != null) {
        val collected by interactionSource.collectIsPressedAsState()
        isPressed = collected
        LaunchedEffect(isPressed) {
            if (isPressed && enabled && haptic != null) {
                triggerHaptic(haptic)
            }
        }
        pointerModifier = Modifier
    } else {
        var pointerPressed by remember { mutableStateOf(false) }
        isPressed = pointerPressed
        pointerModifier = if (enabled) {
            Modifier.pointerInput(enabled, haptic, triggerHaptic) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pointerPressed = true
                    if (haptic != null) {
                        triggerHaptic(haptic)
                    }
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    pointerPressed = false
                }
            }
        } else {
            Modifier
        }
    }

    if (level.isOff || !enabled) {
        return@composed this.then(pointerModifier)
    }

    val target = if (isPressed) pressedScale else 1f
    val animScaleState = animateFloatAsState(
        targetValue = target,
        animationSpec = if (level == AnimationLevel.FULL) {
            TagMotion.snappySpring()
        } else {
            TagMotion.instantTween()
        },
        label = "press-scale",
    )

    this
        .then(pointerModifier)
        .graphicsLayer {
            val s = animScaleState.value
            scaleX = s
            scaleY = s
        }
}

/**
 * Staggered entrance animation (fade + slight directional slide) with delay capped at
 * [TagMotion.STAGGER_MAX_DELAY_MS] (400ms).
 *
 * When [direction] is non-zero, slides horizontally respecting LTR/RTL layout direction
 * (e.g., `+1` enters from trailing/next edge, `-1` from leading/previous edge).
 * When [direction] is `0`, slides gently upward.
 */
fun Modifier.staggeredEnter(
    index: Int,
    key: Any = Unit,
    direction: Int = 0,
    slideDistanceDp: Dp = 18.dp,
): Modifier = composed {
    val level = LocalAnimationLevel.current
    if (level.isOff) return@composed this

    val registry = LocalMotionRevealRegistry.current
    val token = "stagger:$key:$index"
    val shouldAnimate = remember(token, level) {
        !level.isOff && (registry?.consumeFirstReveal(token) ?: true)
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slidePx = with(LocalDensity.current) { slideDistanceDp.toPx() }
    val progress = remember(key) { Animatable(if (shouldAnimate) 0f else 1f) }

    LaunchedEffect(key, level) {
        if (level.isOff || !shouldAnimate) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        val delayMs = if (level == AnimationLevel.FULL) {
            TagMotion.staggerDelayMillis(index)
        } else {
            TagMotion.staggerDelayMillis(index, stepMs = 20, maxDelayMs = 160)
        }
        val durationMs = TagMotion.levelAdjustedDuration(TagMotion.DURATION_STANDARD, level)
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                delayMillis = delayMs,
                easing = TagMotion.EmphasizedDecelerate,
            ),
        )
    }

    this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        alpha = p
        if (level == AnimationLevel.FULL) {
            val remaining = 1f - p
            if (direction == 0) {
                translationY = remaining * slidePx
            } else {
                val rtlFlip = if (isRtl) -1 else 1
                translationX = remaining * slidePx * direction.coerceIn(-1, 1) * rtlFlip
            }
        }
    }
}

/**
 * Gentle ambient breathing animation (scale + alpha) that runs ONLY when [AnimationLevel.FULL]
 * and [LocalAmbientAnimationsActive] are true.
 */
fun Modifier.breathing(
    minScale: Float = 0.97f,
    maxScale: Float = 1.03f,
    minAlpha: Float = 0.86f,
    maxAlpha: Float = 1f,
    durationMillis: Int = 2600,
    enabled: Boolean = true,
): Modifier = composed {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current
    if (!enabled || !level.allowsAmbient || !ambientActive) {
        return@composed this
    }

    val transition = rememberInfiniteTransition(label = "breathing")
    val phaseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = TagMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathing-phase",
    )

    this.graphicsLayer {
        val phase = phaseState.value
        val s = minScale + (maxScale - minScale) * phase
        scaleX = s
        scaleY = s
        alpha = minAlpha + (maxAlpha - minAlpha) * phase
    }
}

/**
 * Gentle vertical floating motion for decorative badges and empty-state illustrations.
 * Runs ONLY when [AnimationLevel.FULL] and [LocalAmbientAnimationsActive] are true.
 */
fun Modifier.floatY(
    amplitudeDp: Dp = 4.dp,
    durationMillis: Int = 3200,
    enabled: Boolean = true,
): Modifier = composed {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current
    if (!enabled || !level.allowsAmbient || !ambientActive) {
        return@composed this
    }

    val ampPx = with(LocalDensity.current) { amplitudeDp.toPx() }
    val transition = rememberInfiniteTransition(label = "float-y")
    val offsetState = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = TagMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float-y-phase",
    )

    this.graphicsLayer {
        translationY = offsetState.value * ampPx
    }
}

/**
 * Pulsating ambient outline glow drawn behind the target component without triggering
 * recomposition. Static subtle halo in [AnimationLevel.REDUCED], disabled in [AnimationLevel.OFF].
 */
fun Modifier.glowPulse(
    glowColor: Color,
    cornerRadius: Dp = 20.dp,
    maxAlpha: Float = 0.34f,
    durationMillis: Int = 2400,
    enabled: Boolean = true,
): Modifier = composed {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current
    if (!enabled || level.isOff) {
        return@composed this
    }

    val pulseAlphaState = if (level.allowsAmbient && ambientActive) {
        val transition = rememberInfiniteTransition(label = "glow-pulse")
        transition.animateFloat(
            initialValue = maxAlpha * 0.25f,
            targetValue = maxAlpha,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = TagMotion.StandardEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow-pulse-alpha",
        )
    } else {
        null
    }

    this.drawBehind {
        val pulseAlpha = pulseAlphaState?.value ?: (maxAlpha * 0.45f)
        val radiusPx = cornerRadius.toPx()
        val strokePx = 4.dp.toPx()
        drawRoundRect(
            color = glowColor.copy(alpha = pulseAlpha * 0.45f),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx * 1.8f),
        )
        drawRoundRect(
            color = glowColor.copy(alpha = pulseAlpha),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx * 0.65f),
        )
    }
}
