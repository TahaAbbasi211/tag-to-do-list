package com.tag.routine.ui.motion

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Session-scoped registry remembering which entry/count-up animations have already
 * played during the current UI session so switching tabs never replays entrance
 * animations from zero, while real value changes continue to animate smoothly.
 */
class MotionRevealRegistry {
    private val revealedKeys = HashSet<Any>()
    private val lastInts = HashMap<Any, Int>()
    private val lastFloats = HashMap<Any, Float>()
    private val lastValues = HashMap<Any, Any>()

    /** Returns true strictly on the first call for [key] during this session. */
    fun consumeFirstReveal(key: Any): Boolean = revealedKeys.add(key)

    fun hasRevealed(key: Any): Boolean = key in revealedKeys

    fun initialInt(key: Any, target: Int, animateFromZero: Boolean): Int {
        val prev = lastInts[key]
        if (prev != null) return prev
        return if (animateFromZero) 0 else target
    }

    fun recordInt(key: Any, value: Int) {
        lastInts[key] = value
    }

    fun initialFloat(key: Any, target: Float, animateFromZero: Boolean): Float {
        val prev = lastFloats[key]
        if (prev != null) return prev
        return if (animateFromZero) 0f else target
    }

    fun recordFloat(key: Any, value: Float) {
        lastFloats[key] = value
    }

    /**
     * Returns true when [key] has never been seen or when [value] differs from the last
     * recorded value for [key].
     */
    fun shouldAnimateValue(key: Any, value: Any): Boolean {
        val prev = lastValues[key]
        if (prev != null && prev == value) return false
        lastValues[key] = value
        return true
    }
}

val LocalMotionRevealRegistry = staticCompositionLocalOf<MotionRevealRegistry?> { null }
