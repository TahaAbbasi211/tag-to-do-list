package com.tag.routine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.data.Dates
import com.tag.routine.ui.accessibility.rememberReducedMotion
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import java.util.Locale

// ==================================================================
// 1. AppCard
// ==================================================================

/**
 * Modern card container with subtle elevation, delicate border, and soft rounded corners.
 * Adapts to depth surfaces in light and dark modes.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.985f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "card-press",
        )

        Card(
            onClick = onClick,
            modifier = modifier.scale(scale),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            content = content,
        )
    }
}

// ==================================================================
// 2. ScreenHeader & SectionHeader
// ==================================================================

/**
 * Top-of-screen page header establishing clear hierarchy with title,
 * optional supporting subtitle, and optional trailing summary/action slot.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Consistent section heading across the app with clear typographic hierarchy,
 * optional subtitle, optional leading badge/icon, and trailing action slot.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

// ==================================================================
// 3. AppButton
// ==================================================================

enum class AppButtonVariant {
    Primary,
    Secondary,
    Tonal,
    Text,
}

/**
 * Modern tactile button with Primary, Secondary (outlined), Tonal, and Text variants.
 * Features subtle spring press feedback, loading indicator, and icon slots.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !loading) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btn-press",
    )

    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

    val buttonContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = when (variant) {
                        AppButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
                        AppButtonVariant.Tonal -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            } else {
                leadingIcon?.invoke()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (!loading) {
                trailingIcon?.invoke()
            }
        }
    }

    when (variant) {
        AppButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier.scale(pressScale),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { buttonContent() }
        }
        AppButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.scale(pressScale),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { buttonContent() }
        }
        AppButtonVariant.Tonal -> {
            Button(
                onClick = onClick,
                modifier = modifier.scale(pressScale),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { buttonContent() }
        }
        AppButtonVariant.Text -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.scale(pressScale),
                enabled = enabled && !loading,
                shape = shape,
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { buttonContent() }
        }
    }
}

// ==================================================================
// 4. AppChip
// ==================================================================

/**
 * Modern selectable chip used for filters, tags, and category chips.
 * Smoothly animates state changes between active and inactive.
 */
@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    dotColor: Color? = null,
    trailingCount: Int? = null,
    shape: Shape = RoundedCornerShape(percent = 50),
) {
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val animatedBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(220),
        label = "chip-bg",
    )
    val animatedContent by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "chip-content",
    )

    Surface(
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = shape,
        color = animatedBg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) animatedContent.copy(alpha = 0.9f) else dotColor),
                )
            } else {
                leadingIcon?.invoke()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = animatedContent,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingCount != null && trailingCount > 0) {
                Text(
                    text = Dates.formatDigits(trailingCount.toString(), appLocale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) animatedContent.copy(alpha = 0.9f) else animatedContent.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// ==================================================================
// 5. EmptyState
// ==================================================================

/**
 * Modern empty state presentation with animated pulsing icon badge,
 * friendly headline, readable supporting copy, and optional action button slot.
 */
@Composable
fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty-pulse")
    val reducedMotion = rememberReducedMotion()
    val pulseScale: Float
    val pulseAlpha: Float
    if (reducedMotion) {
        pulseScale = 1f
        pulseAlpha = 1f
    } else {
        val scaleAnim by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "empty-pulse-scale",
        )
        val alphaAnim by infiniteTransition.animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "empty-pulse-alpha",
        )
        pulseScale = scaleAnim
        pulseAlpha = alphaAnim
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(pulseScale)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.graphicsLayer { alpha = pulseAlpha }) {
                icon()
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
            lineHeight = 21.sp,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ==================================================================
// 6. Large-screen frame & adaptive stat rows
// ==================================================================

/**
 * Centres a screen's content and caps its width on tablets, foldables and landscape
 * phones, without pulling in any window-size-class dependency. Below [maxWidth] the
 * frame is a no-op, so phone layouts are unchanged.
 */
@Composable
fun LargeScreenFrame(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 640.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            content = content,
        )
    }
}

/**
 * Lays out two stat tiles side by side, and stacks them once the system font is large
 * enough (fontScale > 1.4) that a half-width tile would clip its value.
 */
@Composable
fun StatTilePair(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val stack = LocalDensity.current.fontScale > 1.4f
    if (stack) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ==================================================================
// 7. AppBottomNav (Floating Bottom Bar with Smooth Selection Indicator)
// ==================================================================

data class BottomNavTabItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating bottom navigation bar with soft rounded pill geometry, gentle border,
 * elevation, and a smooth animated selection pill indicator that highlights active tabs.
 */
@Composable
fun <T> AppBottomNav(
    items: List<BottomNavTabItem<T>>,
    current: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = tagBrand()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, brand.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = current == item.value

                val animatedBg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "nav-bg",
                )
                val animatedTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "nav-tint",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "nav-scale",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(animatedBg)
                        .clickable { onSelect(item.value) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = animatedTint,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(iconScale),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedTint,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
