package com.tag.routine.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Semantic haptic feedback categories across Tag. */
enum class HapticKind {
    /** Light tactile tick for switches, tabs, and threshold crossings. */
    TICK,

    /** Positive confirmation for completing a routine, task, or saving. */
    CONFIRM,

    /** Subtle warning/rejection cue for errors or destructive confirmation thresholds. */
    REJECT,

    /** Celebratory pulse when reaching 100% or a streak milestone. */
    CELEBRATE,
}

/**
 * Returns a stable haptic trigger callback that respects the user's [LocalHapticsEnabled]
 * preference and guards API 30+ constants cleanly for minSdk 26.
 */
@Composable
fun rememberTagHaptics(): (HapticKind) -> Unit {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) {
        { kind ->
            if (enabled) {
                triggerHaptic(view, kind)
            }
        }
    }
}

private fun triggerHaptic(view: View, kind: HapticKind) {
    try {
        val constant = when (kind) {
            HapticKind.TICK -> HapticFeedbackConstants.CLOCK_TICK
            HapticKind.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            HapticKind.REJECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            HapticKind.CELEBRATE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        view.performHapticFeedback(constant)
    } catch (_: Exception) {
        // Ignore platform or OEM haptic denials silently.
    }
}
