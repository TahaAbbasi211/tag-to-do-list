package com.tag.routine.ui.accessibility

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * System "remove animations" support.
 *
 * Android disables animators when the developer/accessibility setting
 * `ANIMATOR_DURATION_SCALE` is 0; `ValueAnimator.areAnimatorsEnabled()` reports exactly that
 * (minSdk 26, so no platform-version guard is needed). Motion-heavy decorations then fall
 * back to their final state instead of moving.
 */
object MotionPreferences {

    /** Pure rule used by tests: a zero duration scale means animations must not run. */
    fun animationsEnabled(animatorDurationScale: Float): Boolean =
        animatorDurationScale > 0f

    /** Pure rule combining the system flag with an already-read duration scale. */
    fun shouldAnimate(animatorsEnabled: Boolean, animatorDurationScale: Float): Boolean =
        animatorsEnabled && animationsEnabled(animatorDurationScale)
}

/** True when the system asks apps to remove animations. */
@Composable
fun rememberReducedMotion(): Boolean {
    val view = LocalView.current
    return remember(view) {
        try {
            !ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            false
        }
    }
}
