package com.tag.routine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.LargeScreenFrame
import com.tag.routine.ui.i18n.AppLanguage
import com.tag.routine.ui.motion.AuroraBackground
import com.tag.routine.ui.motion.CheckDraw
import com.tag.routine.ui.motion.GlassCard
import com.tag.routine.ui.motion.HapticKind
import com.tag.routine.ui.motion.LocalAmbientAnimationsActive
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.floatY
import com.tag.routine.ui.motion.pressScale
import com.tag.routine.ui.motion.rememberTagHaptics
import com.tag.routine.ui.motion.staggeredEnter
import com.tag.routine.ui.theme.tagBrand
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * First-launch onboarding experience on a living aurora canvas with scroll parallax,
 * pure-Canvas orbiting emblem, and a high-contrast glass card for optional name entry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    currentLanguage: String = "",
    onSelectLanguage: (String) -> Unit = {},
    onDone: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val brand = tagBrand()
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    val scrollState = rememberScrollState()
    val trimmed = name.trim()

    AuroraBackground(
        colors = brand.auroraColors,
        baseColor = Color.Transparent,
        parallaxOffsetPx = { -scrollState.value * 0.25f },
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(brand.heroStart, brand.heroMid, brand.heroEnd),
                ),
            ),
    ) {
        LargeScreenFrame {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingLanguagePicker(
                    currentLanguage = currentLanguage,
                    onHeroColor = brand.onHero,
                    onSelect = { tag ->
                        haptics(HapticKind.TICK)
                        onSelectLanguage(tag)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEnter(index = 0, key = "onboarding-lang"),
                )

                Spacer(Modifier.height(20.dp))

                OnboardingCanvasIllustration(
                    onHeroColor = brand.onHero,
                    accentStart = brand.ringStart,
                    accentEnd = brand.ringEnd,
                    streakColor = brand.streak,
                    scrollOffsetPx = { scrollState.value.toFloat() },
                )

                Spacer(Modifier.height(22.dp))

                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = brand.onHero,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .staggeredEnter(index = 0, key = "onboarding")
                        .semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = brand.onHeroMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .staggeredEnter(index = 1, key = "onboarding"),
                )

                Spacer(Modifier.height(26.dp))

                GlassCard(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .staggeredEnter(index = 2, key = "onboarding"),
                    shape = RoundedCornerShape(26.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    rimColors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                    elevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text(stringResource(R.string.onboarding_name_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (trimmed.isNotEmpty()) {
                                        haptics(HapticKind.CONFIRM)
                                        onDone(trimmed)
                                    }
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedVisibility(
                            visible = trimmed.isNotEmpty(),
                            enter = if (level.isOff) {
                                fadeIn(TagMotion.instantTween(0))
                            } else {
                                expandVertically(TagMotion.snappySpring()) + fadeIn(TagMotion.quickTween())
                            },
                            exit = if (level.isOff) {
                                fadeOut(TagMotion.instantTween(0))
                            } else {
                                shrinkVertically(TagMotion.quickTween()) + fadeOut(TagMotion.quickTween())
                            },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_greeting_preview, trimmed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        AppButton(
                            text = stringResource(R.string.onboarding_continue),
                            onClick = {
                                haptics(HapticKind.CONFIRM)
                                onDone(trimmed)
                            },
                            variant = AppButtonVariant.Primary,
                            enabled = trimmed.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.small,
                        )

                        Spacer(Modifier.height(8.dp))

                        AppButton(
                            text = stringResource(R.string.onboarding_skip),
                            onClick = {
                                haptics(HapticKind.TICK)
                                onSkip()
                            },
                            variant = AppButtonVariant.Text,
                        )
                    }
                }
            }
        }
    }
}

/** Pure Canvas floating medallion illustration with gentle orbiting accents and scroll parallax. */
@Composable
private fun OnboardingCanvasIllustration(
    onHeroColor: Color,
    accentStart: Color,
    accentEnd: Color,
    streakColor: Color,
    scrollOffsetPx: () -> Float,
) {
    val level = LocalAnimationLevel.current
    val ambientActive = LocalAmbientAnimationsActive.current

    val checkReveal = remember { Animatable(if (level.isOff) 1f else 0f) }
    LaunchedEffect(level) {
        if (level.isOff) {
            checkReveal.snapTo(1f)
        } else {
            checkReveal.snapTo(0f)
            checkReveal.animateTo(1f, TagMotion.emphasizedTween(delayMillis = 150))
        }
    }

    val orbitState = if (level.allowsAmbient && ambientActive) {
        val transition = rememberInfiniteTransition(label = "onboarding-orbit")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TagMotion.DURATION_AMBIENT, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbit-angle",
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(132.dp)
            .graphicsLayer {
                if (level == com.tag.routine.ui.motion.AnimationLevel.FULL) {
                    translationY = -scrollOffsetPx() * 0.14f
                }
            }
            .floatY(amplitudeDp = 4.dp)
            .clearAndSetSemantics {}
            .drawWithCache {
                val c = Offset(size.width / 2f, size.height / 2f)
                val outerR = size.minDimension * 0.46f
                val midR = size.minDimension * 0.34f
                val badgeR = size.minDimension * 0.23f
                val haloBrush = Brush.radialGradient(
                    colors = listOf(onHeroColor.copy(alpha = 0.26f), Color.Transparent),
                    center = c,
                    radius = outerR,
                )
                val ringBrush = Brush.linearGradient(listOf(accentStart, accentEnd))

                onDrawBehind {
                    drawCircle(brush = haloBrush, radius = outerR, center = c)
                    drawCircle(
                        brush = ringBrush,
                        radius = midR,
                        center = c,
                        style = Stroke(width = 2.2.dp.toPx()),
                    )
                    drawCircle(
                        color = onHeroColor.copy(alpha = 0.18f),
                        radius = badgeR,
                        center = c,
                    )

                    val angle = orbitState?.value ?: 0.65f
                    val ox = c.x + cos(angle) * midR
                    val oy = c.y + sin(angle) * midR
                    drawCircle(color = streakColor, radius = 4.5.dp.toPx(), center = Offset(ox, oy))

                    val ox2 = c.x + cos(angle + PI.toFloat()) * (outerR * 0.88f)
                    val oy2 = c.y + sin(angle + PI.toFloat()) * (outerR * 0.88f)
                    drawCircle(color = accentEnd.copy(alpha = 0.85f), radius = 3.dp.toPx(), center = Offset(ox2, oy2))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CheckDraw(
            progressProvider = { checkReveal.value },
            modifier = Modifier.size(48.dp),
            color = onHeroColor,
            strokeWidth = 3.2.dp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingLanguagePicker(
    currentLanguage: String,
    onHeroColor: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemLang = LocalConfiguration.current.locales[0]?.language?.lowercase(Locale.ROOT)
    val sanitized = AppLanguage.sanitize(currentLanguage)
    val activeTag = if (sanitized != AppLanguage.SYSTEM) {
        sanitized
    } else {
        AppLanguage.supported.firstOrNull { it.tag == systemLang }?.tag ?: AppLanguage.ENGLISH
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLanguage.supported.forEach { entry ->
            val isSelected = entry.tag == activeTag
            val shape = RoundedCornerShape(percent = 50)
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .pressScale(pressedScale = 0.94f)
                    .clip(shape)
                    .semantics { selected = isSelected }
                    .clickable { onSelect(entry.tag) },
                shape = shape,
                color = if (isSelected) {
                    onHeroColor
                } else {
                    Color(0xFF0A0F1E).copy(alpha = 0.42f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) onHeroColor else onHeroColor.copy(alpha = 0.28f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = entry.nativeName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF0F172A) else onHeroColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
