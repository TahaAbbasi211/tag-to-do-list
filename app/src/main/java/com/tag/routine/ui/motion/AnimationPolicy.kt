package com.tag.routine.ui.motion

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tag.routine.data.AnimationSetting

/** Effective runtime animation capability resolved from system + app settings + device RAM. */
enum class AnimationLevel {
    FULL,
    REDUCED,
    OFF;

    val isOff: Boolean get() = this == OFF
    val allowsMotion: Boolean get() = this != OFF
    val allowsAmbient: Boolean get() = this == FULL
    val allowsParticles: Boolean get() = this != OFF
}

/**
 * Pure, testable policy that resolves the effective [AnimationLevel].
 *
 * System accessibility ("Remove animations", i.e. `ANIMATOR_DURATION_SCALE == 0` or
 * `!ValueAnimator.areAnimatorsEnabled()`) always takes precedence and forces [AnimationLevel.OFF].
 */
object AnimationPolicy {

    /** Devices under 3 GiB RAM default to [AnimationLevel.REDUCED] when set to Auto. */
    const val LOW_MEMORY_THRESHOLD_BYTES: Long = 3L * 1024L * 1024L * 1024L

    fun resolve(
        userSetting: AnimationSetting,
        systemAnimatorsEnabled: Boolean,
        animatorDurationScale: Float = 1f,
        isLowRamDevice: Boolean = false,
        totalRamBytes: Long = 0L,
    ): AnimationLevel {
        if (!systemAnimatorsEnabled || animatorDurationScale <= 0f) {
            return AnimationLevel.OFF
        }
        return when (userSetting) {
            AnimationSetting.OFF -> AnimationLevel.OFF
            AnimationSetting.REDUCED -> AnimationLevel.REDUCED
            AnimationSetting.FULL -> AnimationLevel.FULL
            AnimationSetting.AUTO -> {
                val constrainedMemory = isLowRamDevice ||
                    (totalRamBytes in 1L until LOW_MEMORY_THRESHOLD_BYTES)
                if (constrainedMemory) AnimationLevel.REDUCED else AnimationLevel.FULL
            }
        }
    }
}

val LocalAnimationLevel = staticCompositionLocalOf { AnimationLevel.FULL }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * True only when ambient/infinite animations are permitted AND the hosting screen/lifecycle
 * is actively resumed in the foreground. Infinite loops must inspect this before running.
 */
val LocalAmbientAnimationsActive = staticCompositionLocalOf { true }

/** Resolves [AnimationLevel] from [setting], system animator state, and device memory profile. */
@Composable
fun rememberResolvedAnimationLevel(setting: AnimationSetting): AnimationLevel {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(setting, context, resumeTick) {
        val animatorsEnabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        val (isLowRam, totalRam) = readDeviceMemoryProfile(context)
        AnimationPolicy.resolve(
            userSetting = setting,
            systemAnimatorsEnabled = animatorsEnabled,
            animatorDurationScale = scale,
            isLowRamDevice = isLowRam,
            totalRamBytes = totalRam,
        )
    }
}

/** Tracks whether the current [LocalLifecycleOwner] is at least [Lifecycle.State.RESUMED]. */
@Composable
fun rememberIsLifecycleResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, _ ->
            resumed = source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return resumed
}

private fun readDeviceMemoryProfile(context: Context): Pair<Boolean, Long> =
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@runCatching false to 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        am.isLowRamDevice to info.totalMem
    }.getOrDefault(false to 0L)
