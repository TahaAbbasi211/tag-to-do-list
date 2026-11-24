package com.tag.routine.ui.editors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tag.routine.R
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.motion.AnimatedDialogSurface
import com.tag.routine.ui.motion.HapticKind
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.pressScale
import com.tag.routine.ui.motion.rememberTagHaptics
import java.time.LocalDate
import java.util.Locale

/**
 * Picks one or more days to copy a routine onto.
 *
 * Only items are copied: the dialog never offers to copy the tick state, and the routine
 * itself is left untouched. Days the routine already appears on are still selectable but
 * produce nothing, so the action stays safe to repeat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CopyToDayDialog(
    routineTitle: String,
    today: LocalDate,
    localizedContext: android.content.Context,
    calendarType: CalendarType = CalendarType.GREGORIAN,
    onDismiss: () -> Unit,
    onConfirm: (List<LocalDate>) -> Unit,
) {
    var selected by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }

    // Two weeks is enough to place an item without turning the sheet into a calendar.
    val days = remember(today) { (0L..13L).map { today.plusDays(it) } }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedDialogSurface {
            ProvideAppLocale(localizedContext) {
            val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            val monthSpan = remember(days, appLocale, calendarType) {
                Dates.monthYearRange(days.first(), days.last(), appLocale, calendarType)
            }
            AppCard(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                elevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.copy_to_other_day_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = Dates.formatDigits(
                            stringResource(R.string.copy_to_other_day_message, routineTitle),
                            appLocale,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${stringResource(R.string.copy_to_other_day_pick_days)} · $monthSpan",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        days.forEach { day ->
                            DayChip(
                                day = day,
                                isToday = day == today,
                                locale = appLocale,
                                calendarType = calendarType,
                                selected = day in selected,
                                onClick = {
                                    selected = if (day in selected) selected - day else selected + day
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onDismiss,
                            variant = AppButtonVariant.Text,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = stringResource(R.string.copy_to_other_day_confirm),
                            onClick = {
                                onConfirm(days.filter { it in selected })
                                onDismiss()
                            },
                            variant = AppButtonVariant.Primary,
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun DayChip(
    day: LocalDate,
    isToday: Boolean,
    locale: Locale,
    calendarType: CalendarType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    val colorSpec = if (level.isOff) TagMotion.instantTween<Color>(0) else TagMotion.quickTween()
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = colorSpec,
        label = "copy-day-bg",
    )
    val content by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorSpec,
        label = "copy-day-fg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = colorSpec,
        label = "copy-day-border",
    )

    var tapCount by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(tapCount, level) {
        if (tapCount > 0 && level.allowsMotion) {
            ripple.snapTo(0.01f)
            ripple.animateTo(1f, TagMotion.emphasizedTween())
            ripple.snapTo(0f)
        } else {
            ripple.snapTo(0f)
        }
    }
    val rippleTint = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 46.dp, minHeight = 56.dp)
            .pressScale(pressedScale = 0.92f)
            .drawWithContent {
                drawContent()
                val r = ripple.value
                if (r > 0.01f && r < 1f) {
                    drawCircle(
                        color = rippleTint.copy(alpha = (1f - r) * 0.28f),
                        radius = size.maxDimension * (0.35f + r * 0.55f),
                    )
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(
                width = if (!selected && isToday) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .semantics { this.selected = selected }
            .clickable {
                tapCount++
                haptics(HapticKind.TICK)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Dates.shortDayName(day, locale),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = Dates.formatDigits(Dates.dayOfMonth(day, calendarType).toString(), locale),
                style = MaterialTheme.typography.titleSmall,
                color = content,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
