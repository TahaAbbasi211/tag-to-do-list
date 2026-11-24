package com.tag.routine.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SeededParticle(
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
 * Deterministic, testable particle generator with strict per-level particle caps:
 * - [AnimationLevel.FULL]: max 60 particles
 * - [AnimationLevel.REDUCED]: max 24 particles
 * - [AnimationLevel.OFF]: 0 particles
 */
object ParticleEngine {

    const val MAX_PARTICLES_FULL = 60
    const val MAX_PARTICLES_REDUCED = 24

    val DEFAULT_PALETTE: List<Color> = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF6B6B), // Coral
        Color(0xFF4FD9C0), // Mint / Teal
        Color(0xFFA78BFA), // Purple
        Color(0xFF60A5FA), // Sky Blue
        Color(0xFFF472B6), // Pink
        Color(0xFFF59E0B), // Amber
        Color(0xFF34D399), // Emerald
    )

    fun maxParticlesFor(level: AnimationLevel): Int = when (level) {
        AnimationLevel.FULL -> MAX_PARTICLES_FULL
        AnimationLevel.REDUCED -> MAX_PARTICLES_REDUCED
        AnimationLevel.OFF -> 0
    }

    fun particleCountFor(level: AnimationLevel, requested: Int): Int =
        requested.coerceIn(0, maxParticlesFor(level))

    fun generateConfetti(
        seed: Long,
        level: AnimationLevel,
        requestedCount: Int = MAX_PARTICLES_FULL,
        palette: List<Color> = DEFAULT_PALETTE,
    ): List<SeededParticle> {
        val count = particleCountFor(level, requestedCount)
        if (count == 0 || palette.isEmpty()) return emptyList()

        return List(count) { index ->
            val random = Random(seed + index.toLong() * 37L + 23L)
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = 200f + random.nextFloat() * 480f
            SeededParticle(
                startX = 0.25f + random.nextFloat() * 0.5f,
                startY = 0.12f + random.nextFloat() * 0.08f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 180f,
                size = 12f + random.nextFloat() * 16f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 14f,
                color = palette[index % palette.size],
                isCircle = index % 3 == 0,
            )
        }
    }

    fun generateSparkles(
        seed: Long,
        level: AnimationLevel,
        requestedCount: Int = 24,
        palette: List<Color> = DEFAULT_PALETTE,
    ): List<SeededParticle> {
        val count = particleCountFor(level, requestedCount)
        if (count == 0 || palette.isEmpty()) return emptyList()

        return List(count) { index ->
            val random = Random(seed + index.toLong() * 53L + 11L)
            val angle = (index.toFloat() / count.toFloat()) * 2f * PI.toFloat() +
                (random.nextFloat() - 0.5f) * 0.35f
            val speed = 150f + random.nextFloat() * 320f
            SeededParticle(
                startX = 0.5f + (random.nextFloat() - 0.5f) * 0.16f,
                startY = 0.22f + (random.nextFloat() - 0.5f) * 0.08f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                size = 10f + random.nextFloat() * 12f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 6f,
                color = palette[index % palette.size],
                isCircle = false,
            )
        }
    }
}

/**
 * Pure Canvas confetti burst with deterministic seeded particles, capped per [AnimationLevel],
 * and hidden from TalkBack ([clearAndSetSemantics]).
 */
@Composable
fun ConfettiBurst(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    seed: Long = 101L,
    requestedCount: Int = ParticleEngine.MAX_PARTICLES_FULL,
    palette: List<Color> = ParticleEngine.DEFAULT_PALETTE,
) {
    val level = LocalAnimationLevel.current
    if (!trigger || level.isOff) return

    val particles = remember(seed, level, requestedCount, palette) {
        ParticleEngine.generateConfetti(
            seed = seed,
            level = level,
            requestedCount = requestedCount,
            palette = palette,
        )
    }
    if (particles.isEmpty()) return

    val anim = remember(seed, trigger) { Animatable(0f) }
    LaunchedEffect(trigger, seed, level) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (level == AnimationLevel.FULL) 2200 else 1300,
                easing = TagMotion.EmphasizedDecelerate,
            ),
        )
    }

    val active by remember(anim) { derivedStateOf { anim.value < 1f } }
    if (active) {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
        ) {
            val t = anim.value
            val gravity = 650f
            val alpha = (1f - (t * 1.15f).coerceIn(0f, 1f))

            for (i in particles.indices) {
                val p = particles[i]
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
 * Radial 4-point star sparkle burst with pre-allocated [Path] (no draw-loop allocations)
 * and hidden from TalkBack ([clearAndSetSemantics]).
 */
@Composable
fun SparkleBurst(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    seed: Long = 202L,
    requestedCount: Int = 16,
    palette: List<Color> = ParticleEngine.DEFAULT_PALETTE,
) {
    val level = LocalAnimationLevel.current
    if (!trigger || level.isOff) return

    val particles = remember(seed, level, requestedCount, palette) {
        ParticleEngine.generateSparkles(
            seed = seed,
            level = level,
            requestedCount = requestedCount,
            palette = palette,
        )
    }
    if (particles.isEmpty()) return

    val anim = remember(seed, trigger) { Animatable(0f) }
    val starPath = remember { Path() }

    LaunchedEffect(trigger, seed, level) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (level == AnimationLevel.FULL) 1100 else 700,
                easing = LinearEasing,
            ),
        )
    }

    val active by remember(anim) { derivedStateOf { anim.value < 1f } }
    if (active) {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
        ) {
            val t = anim.value
            val alpha = if (t < 0.25f) (t / 0.25f) else (1f - ((t - 0.25f) / 0.75f)).coerceIn(0f, 1f)
            val scale = (1f - t * 0.35f).coerceIn(0.3f, 1f)

            for (i in particles.indices) {
                val p = particles[i]
                val x = (p.startX * size.width) + p.vx * t
                val y = (p.startY * size.height) + p.vy * t
                val r = p.size * scale
                val inner = r * 0.32f

                starPath.reset()
                starPath.moveTo(x, y - r)
                starPath.lineTo(x + inner, y - inner)
                starPath.lineTo(x + r, y)
                starPath.lineTo(x + inner, y + inner)
                starPath.lineTo(x, y + r)
                starPath.lineTo(x - inner, y + inner)
                starPath.lineTo(x - r, y)
                starPath.lineTo(x - inner, y - inner)
                starPath.close()

                rotate(p.rotationSpeed * t * 180f, pivot = Offset(x, y)) {
                    drawPath(
                        path = starPath,
                        color = p.color.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}
