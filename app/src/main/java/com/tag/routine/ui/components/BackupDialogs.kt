package com.tag.routine.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tag.routine.R
import com.tag.routine.data.BackupErrorCode
import com.tag.routine.data.BackupMode
import com.tag.routine.data.BackupPreview
import com.tag.routine.data.BackupRestoreReport
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.JalaliCalendar
import com.tag.routine.ui.BackupUiState
import com.tag.routine.ui.i18n.LocalizedAlertDialog
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.motion.AnimatedDialogSurface
import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.CheckDraw
import com.tag.routine.ui.motion.HapticKind
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.motion.breathing
import com.tag.routine.ui.motion.rememberTagHaptics
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun BackupDialogs(
    backupState: BackupUiState,
    calendarType: CalendarType,
    localizedContext: Context,
    onDismiss: () -> Unit,
    onConfirmMerge: () -> Unit,
    onRequestReplace: () -> Unit,
    onBackToPreview: () -> Unit,
    onConfirmReplace: () -> Unit,
) {
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    when (backupState) {
        BackupUiState.Idle -> Unit

        is BackupUiState.Working -> {
            BackupWorkingDialog(
                isExport = backupState.isExport,
                localizedContext = localizedContext,
            )
        }

        is BackupUiState.ExportSuccess -> {
            LocalizedAlertDialog(
                localizedContext = localizedContext,
                onDismissRequest = onDismiss,
                title = {
                    BackupSuccessTitle(text = stringResource(R.string.dialog_export_title))
                },
                text = { Text(stringResource(R.string.dialog_export_body, backupState.fileName)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
            )
        }

        BackupUiState.ExportError -> {
            LocalizedAlertDialog(
                localizedContext = localizedContext,
                onDismissRequest = onDismiss,
                title = {
                    BackupErrorTitle(text = stringResource(R.string.dialog_export_failed_title))
                },
                text = { Text(stringResource(R.string.dialog_export_failed_body)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
            )
        }

        is BackupUiState.PreviewReady -> {
            BackupPreviewDialog(
                preview = backupState.preview,
                calendarType = calendarType,
                localizedContext = localizedContext,
                appLocale = appLocale,
                onDismiss = onDismiss,
                onMerge = onConfirmMerge,
                onReplace = onRequestReplace,
            )
        }

        is BackupUiState.ConfirmReplace -> {
            BackupReplaceConfirmDialog(
                preview = backupState.preview,
                localizedContext = localizedContext,
                appLocale = appLocale,
                onDismiss = onDismiss,
                onBack = onBackToPreview,
                onConfirm = onConfirmReplace,
            )
        }

        is BackupUiState.RestoreSuccess -> {
            val report = backupState.report
            LocalizedAlertDialog(
                localizedContext = localizedContext,
                onDismissRequest = onDismiss,
                title = {
                    BackupSuccessTitle(text = stringResource(R.string.dialog_import_title))
                },
                text = {
                    RestoreSuccessBody(
                        report = report,
                        appLocale = appLocale,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
            )
        }

        is BackupUiState.RestoreError -> {
            LocalizedAlertDialog(
                localizedContext = localizedContext,
                onDismissRequest = onDismiss,
                title = {
                    BackupErrorTitle(text = stringResource(R.string.dialog_import_failed))
                },
                text = { Text(stringResource(errorMessageRes(backupState.code))) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
            )
        }
    }
}

@Composable
private fun BackupWorkingDialog(
    isExport: Boolean,
    localizedContext: Context,
) {
    val spacing = MaterialTheme.spacing
    Dialog(onDismissRequest = {}) {
        AnimatedDialogSurface {
            ProvideAppLocale(localizedContext) {
                AppCard(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 420.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    elevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .breathing(minScale = 0.94f, maxScale = 1.06f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isExport) Icons.Rounded.Backup else Icons.Rounded.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = stringResource(
                                if (isExport) R.string.backup_working_export else R.string.backup_working_import,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(percent = 50)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSuccessTitle(text: String) {
    val level = LocalAnimationLevel.current
    val brand = tagBrand()
    val haptics = rememberTagHaptics()
    val badgeScale = remember { Animatable(if (level.isOff) 1f else 0.4f) }
    val tickProgress = remember { Animatable(if (level.isOff) 1f else 0f) }

    LaunchedEffect(level) {
        haptics(HapticKind.CONFIRM)
        if (level.isOff) {
            badgeScale.snapTo(1f)
            tickProgress.snapTo(1f)
        } else {
            badgeScale.snapTo(0.4f)
            badgeScale.animateTo(1f, TagMotion.bouncySpring())
            tickProgress.snapTo(0f)
            tickProgress.animateTo(1f, TagMotion.standardTween())
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    val s = badgeScale.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(brand.success),
            contentAlignment = Alignment.Center,
        ) {
            CheckDraw(
                progressProvider = { tickProgress.value },
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                strokeWidth = 2.4.dp,
            )
        }
        Text(text = text)
    }
}

@Composable
private fun BackupErrorTitle(text: String) {
    val level = LocalAnimationLevel.current
    val haptics = rememberTagHaptics()
    val density = LocalDensity.current
    val maxShakePx = remember(density) { with(density) { 8.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(level) {
        haptics(HapticKind.REJECT)
        if (level.isOff) {
            offsetX.snapTo(0f)
        } else {
            val amp = if (level == AnimationLevel.FULL) maxShakePx else maxShakePx * 0.5f
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 360
                    (-amp) at 60
                    (amp * 0.85f) at 130
                    (-amp * 0.55f) at 200
                    (amp * 0.30f) at 270
                    0f at 360
                },
            )
        }
    }

    Row(
        modifier = Modifier.graphicsLayer { translationX = offsetX.value },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(26.dp),
        )
        Text(text = text)
    }
}

@Composable
private fun BackupPreviewDialog(
    preview: BackupPreview,
    calendarType: CalendarType,
    localizedContext: Context,
    appLocale: Locale,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val createdText = preview.exportedAt?.let { millis ->
        Dates.shortDateTime(millis, locale = appLocale, calendarType = calendarType)
    } ?: stringResource(R.string.backup_preview_created_unknown)

    val rangeText = if (preview.dateRangeStart != null && preview.dateRangeEnd != null) {
        val start = formatDateWithYear(preview.dateRangeStart, appLocale, calendarType)
        val end = formatDateWithYear(preview.dateRangeEnd, appLocale, calendarType)
        if (preview.dateRangeStart == preview.dateRangeEnd) start else "$start – $end"
    } else {
        stringResource(R.string.backup_preview_date_range_none)
    }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedDialogSurface {
            ProvideAppLocale(localizedContext) {
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
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.backup_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            PreviewRow(
                                label = stringResource(R.string.backup_preview_created),
                                value = createdText,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            PreviewRow(
                                label = stringResource(R.string.backup_preview_items),
                                value = Dates.formatDigits(preview.routineCount.toString(), appLocale),
                            )
                            PreviewRow(
                                label = stringResource(R.string.backup_preview_tasks),
                                value = Dates.formatDigits(preview.taskCount.toString(), appLocale),
                            )
                            PreviewRow(
                                label = stringResource(R.string.backup_preview_focus),
                                value = Dates.formatDigits(preview.focusSessionCount.toString(), appLocale),
                            )
                            PreviewRow(
                                label = stringResource(R.string.backup_preview_tags),
                                value = Dates.formatDigits(preview.tagCount.toString(), appLocale),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            PreviewRow(
                                label = stringResource(R.string.backup_preview_date_range),
                                value = rangeText,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.backup_preview_mode_hint),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Merge mode (safe default)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppButton(
                            text = stringResource(R.string.backup_mode_merge),
                            onClick = onMerge,
                            variant = AppButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        )
                        Text(
                            text = stringResource(R.string.backup_mode_merge_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Replace mode (leads to explicit warning confirmation)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = onReplace,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.backup_mode_replace),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = stringResource(R.string.backup_mode_replace_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        variant = AppButtonVariant.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun BackupReplaceConfirmDialog(
    preview: BackupPreview,
    localizedContext: Context,
    appLocale: Locale,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Dialog(onDismissRequest = onDismiss) {
        AnimatedDialogSurface {
            ProvideAppLocale(localizedContext) {
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
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.backup_replace_confirm_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_replace_confirm_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(spacing.md),
                        )
                    }

                    Text(
                        text = Dates.formatDigits(
                            stringResource(
                                R.string.backup_restore_replace_body,
                                preview.routineCount,
                                preview.taskCount,
                                preview.tagCount,
                                preview.focusSessionCount,
                            ),
                            appLocale,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_replace_confirm_action),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        AppButton(
                            text = stringResource(R.string.action_back),
                            onClick = onBack,
                            variant = AppButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        )
                        AppButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onDismiss,
                            variant = AppButtonVariant.Text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RestoreSuccessBody(
    report: BackupRestoreReport,
    appLocale: Locale,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        val summaryRes = if (report.mode == BackupMode.MERGE) {
            R.string.backup_restore_merge_body
        } else {
            R.string.backup_restore_replace_body
        }
        Text(
            text = Dates.formatDigits(
                stringResource(
                    summaryRes,
                    report.routinesAdded,
                    report.tasksAdded,
                    report.tagsAdded,
                    report.focusSessionsAdded,
                ),
                appLocale,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (report.mode == BackupMode.MERGE && report.skippedDuplicates > 0) {
            Text(
                text = Dates.formatDigits(
                    stringResource(R.string.backup_restore_merge_skipped, report.skippedDuplicates),
                    appLocale,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.backup_restore_safety_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDateWithYear(
    date: LocalDate,
    locale: Locale,
    calendarType: CalendarType,
): String = when (calendarType) {
    CalendarType.GREGORIAN -> {
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        Dates.formatDigits(date.format(fmt), locale)
    }
    CalendarType.JALALI -> {
        val j = JalaliCalendar.fromLocalDate(date)
        Dates.formatDigits("${j.day} ${JalaliCalendar.monthName(j.month)} ${j.year}", locale)
    }
}

private fun errorMessageRes(code: BackupErrorCode): Int = when (code) {
    BackupErrorCode.FILE_TOO_LARGE -> R.string.backup_error_too_large
    BackupErrorCode.EMPTY_FILE -> R.string.backup_error_empty
    BackupErrorCode.INVALID_JSON,
    BackupErrorCode.INVALID_FORMAT,
    -> R.string.backup_error_invalid_format
    BackupErrorCode.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
    BackupErrorCode.MISSING_REQUIRED_FIELD,
    BackupErrorCode.INVALID_FIELD_TYPE,
    BackupErrorCode.INVALID_DATE,
    -> R.string.backup_error_corrupt_data
    BackupErrorCode.DUPLICATE_ID -> R.string.backup_error_duplicate_ids
    BackupErrorCode.SAFETY_BACKUP_FAILED -> R.string.backup_error_safety_failed
    BackupErrorCode.IO_ERROR,
    BackupErrorCode.RESTORE_FAILED,
    -> R.string.backup_error_generic
}
