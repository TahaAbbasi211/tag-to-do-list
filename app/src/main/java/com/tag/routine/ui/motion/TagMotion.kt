package com.tag.routine.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

/**
 * Unified motion design tokens for Tag v2 ("Living Surface").
 *
 * All durations, easing curves, spring constants, and staggered entrance timings across
 * the app must originate from these tokens rather than ad-hoc numeric literals.
 */
object TagMotion {

    // ------------------------------------------------------------------ duration tokens (ms)

    const val DURATION_INSTANT = 90
    const val DURATION_QUICK = 180
    const val DURATION_STANDARD = 320
    const val DURATION_EMPHASIZED = 520
    const val DURATION_AMBIENT = 6800
    const val DURATION_AMBIENT_SLOW = 10400

    const val STAGGER_STEP_MS = 45
    const val STAGGER_MAX_DELAY_MS = 400

    // ------------------------------------------------------------------ easing curves

    /** Standard Material 3-inspired easing for symmetric state transitions. */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized decelerate curve for elements entering the viewport or counting up. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized accelerate curve for elements exiting quickly. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // ------------------------------------------------------------------ spring specs

    /** Crisp feedback spring for taps, pill slides, and press recovery. */
    fun <T> snappySpring() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Smooth spring for ring progress, collapses, and layout-adjacent motion. */
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessLow,
    )

    /** Playful spring with restrained overshoot for celebrations, badges, and ticks. */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ------------------------------------------------------------------ tween specs

    fun <T> instantTween(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = DURATION_INSTANT, delayMillis = delayMillis, easing = StandardEasing)

    fun <T> quickTween(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = DURATION_QUICK, delayMillis = delayMillis, easing = StandardEasing)

    fun <T> standardTween(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = DURATION_STANDARD, delayMillis = delayMillis, easing = StandardEasing)

    fun <T> emphasizedTween(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = DURATION_EMPHASIZED, delayMillis = delayMillis, easing = EmphasizedDecelerate)

    fun <T> exitTween(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = DURATION_QUICK, delayMillis = delayMillis, easing = EmphasizedAccelerate)

    // ------------------------------------------------------------------ pure math helpers

    /**
     * Computes staggered entrance delay for item [index], capped at [maxDelayMs]
     * so long lists settle promptly without trailing stragglers.
     */
    fun staggerDelayMillis(
        index: Int,
        stepMs: Int = STAGGER_STEP_MS,
        maxDelayMs: Int = STAGGER_MAX_DELAY_MS,
    ): Int {
        if (index <= 0 || stepMs <= 0 || maxDelayMs <= 0) return 0
        return (index.toLong() * stepMs.toLong()).coerceAtMost(maxDelayMs.toLong()).toInt()
    }

    /**
     * Evaluates an integer count-up interpolation from [from] to [to] at normalized
     * [fraction] in `[0f, 1f]` using [easing].
     */
    fun evaluateCountUp(
        from: Int,
        to: Int,
        fraction: Float,
        easing: Easing = EmphasizedDecelerate,
    ): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val eased = easing.transform(clamped).coerceIn(0f, 1f)
        return (from + (to - from) * eased).roundToInt()
    }

    /** Scales or disables tween duration cleanly according to the active [AnimationLevel]. */
    fun levelAdjustedDuration(baseMs: Int, level: AnimationLevel): Int = when (level) {
        AnimationLevel.FULL -> baseMs.coerceAtLeast(0)
        AnimationLevel.REDUCED -> (baseMs / 2).coerceIn(0, DURATION_QUICK)
        AnimationLevel.OFF -> 0
    }
}
