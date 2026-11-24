package com.tag.routine.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.tag.routine.BuildConfig
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.AppSettings
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.ui.BackupUiState
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.BackupDialogs
import com.tag.routine.ui.components.LargeScreenFrame
import com.tag.routine.ui.components.ScreenHeader
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.rememberBackupExportLauncher
import com.tag.routine.ui.components.rememberBackupImportLauncher
import com.tag.routine.ui.i18n.AppLanguage
import com.tag.routine.ui.i18n.LocalizedAlertDialog
import com.tag.routine.ui.theme.PaletteDef
import com.tag.routine.ui.theme.spacing
import java.util.Locale

@Composable
fun SettingsScreen(
    state: UiState,
    backupState: BackupUiState,
    onThemeMode: (Int) -> Unit,
    onThemePalette: (String) -> Unit,
    onDynamicColor: (Boolean) -> Unit = {},
    onCalendarType: (CalendarType) -> Unit = {},
    onNotifications: (Boolean) -> Unit,
    onDigest: (Boolean) -> Unit,
    onDigestMinutes: (Int) -> Unit,
    onExportUri: (Uri) -> Unit,
    onImportUri: (Uri) -> Unit,
    onDismissBackupDialog: () -> Unit,
    onConfirmMergeRestore: () -> Unit,
    onRequestReplaceRestore: () -> Unit,
    onBackToBackupPreview: () -> Unit,
    onConfirmReplaceRestore: () -> Unit,
    onReset: () -> Unit,
    onAddTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onSetName: (String) -> Unit,
    onFocusMinutes: (Int) -> Unit,
    onOpenLanguages: () -> Unit,
) {
    val settings = state.settings
    val spacing = MaterialTheme.spacing
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val stackInputRow = LocalDensity.current.fontScale > 1.35f

    // Dialogs are hosted in their own window, which resets LocalContext to the Activity's
    // system-locale context. Capture the app-localized one here so dialog text stays in
    // the language the user picked.
    val localizedContext = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf(settings.userName) }
    var focusInput by remember { mutableStateOf(settings.focusMinutes.toString()) }
    val launchExport = rememberBackupExportLauncher(onExportUri)
    val launchImport = rememberBackupImportLauncher(onImportUri)
    val backupBusy = backupState is BackupUiState.Working

    // The stored name can change elsewhere (onboarding or backup restore), so mirror it
    // into the field whenever the persisted name changes.
    LaunchedEffect(settings.userName) {
        nameInput = settings.userName
    }
    LaunchedEffect(settings.focusMinutes) {
        focusInput = settings.focusMinutes.toString()
    }

    LargeScreenFrame {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.lg,
                bottom = 96.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_settings),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        // 1. Profile
        SettingsSection(
            title = stringResource(R.string.settings_profile),
            icon = Icons.Rounded.Person,
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.settings_your_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.height(spacing.md))
            AppButton(
                text = stringResource(R.string.settings_save_name),
                onClick = { onSetName(nameInput.trim()); nameInput = nameInput.trim() },
                enabled = nameInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )
        }

        // 2. Appearance
        SettingsSection(
            title = stringResource(R.string.settings_appearance),
            icon = Icons.Rounded.Palette,
        ) {
            Text(
                text = stringResource(R.string.settings_theme_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            ThemeSelector(
                currentMode = settings.themeMode,
                onSelect = onThemeMode,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(spacing.xs))
                SettingsToggle(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_sub),
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }
            if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Spacer(Modifier.height(spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = stringResource(R.string.settings_theme_colour),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.sm))
                PaletteSelector(
                    currentPalette = settings.themePalette,
                    onSelect = onThemePalette,
                )
            }
        }

        // 3. Languages
        SettingsSection(
            title = stringResource(R.string.settings_languages),
            icon = Icons.Rounded.Language,
            onCardClick = onOpenLanguages,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_language_open),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_language_current,
                            currentLanguageLabel(settings.language),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // 3b. Calendar type
        SettingsSection(
            title = stringResource(R.string.settings_calendar_type),
            icon = Icons.Rounded.CalendarMonth,
        ) {
            Text(
                text = stringResource(R.string.settings_calendar_type_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            CalendarTypeSelector(
                current = settings.calendarType,
                stackVertically = LocalDensity.current.fontScale > 1.5f,
                onSelect = onCalendarType,
            )
        }

        // 4. Focus defaults
        SettingsSection(
            title = stringResource(R.string.settings_focus),
            icon = Icons.Rounded.Timer,
        ) {
            Text(
                text = stringResource(R.string.settings_focus_default),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_focus_default_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            val parsed = focusInput.toIntOrNull()
            val valid = parsed != null &&
                parsed in AppConstants.MIN_FOCUS_MINUTES..AppConstants.MAX_FOCUS_MINUTES
            if (stackInputRow) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    OutlinedTextField(
                        value = focusInput,
                        onValueChange = { focusInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.focus_custom_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = stringResource(R.string.action_save),
                        onClick = { parsed?.let(onFocusMinutes) },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedTextField(
                        value = focusInput,
                        onValueChange = { focusInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.focus_custom_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = stringResource(R.string.action_save),
                        onClick = { parsed?.let(onFocusMinutes) },
                        enabled = valid,
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }

        // 5. Notifications
        SettingsSection(
            title = stringResource(R.string.settings_notifications),
            icon = Icons.Rounded.Notifications,
        ) {
            SettingsToggle(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_enable_reminders),
                subtitle = stringResource(R.string.settings_enable_reminders_sub),
                checked = settings.notificationsEnabled,
                onCheckedChange = onNotifications,
            )
            if (settings.notificationsEnabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                SettingsToggle(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_daily_digest),
                    subtitle = stringResource(
                        R.string.settings_daily_digest_sub,
                        Dates.formatDigits(Dates.timeLabel(settings.dailyDigestMinutes, appLocale), appLocale),
                    ),
                    checked = settings.dailyDigestEnabled,
                    onCheckedChange = onDigest,
                )
                if (settings.dailyDigestEnabled) {
                    DigestTimeSlider(
                        minutes = settings.dailyDigestMinutes,
                        appLocale = appLocale,
                        onMinutesChange = onDigestMinutes,
                    )
                }
            }
        }

        // 6. Tags
        SettingsSection(
            title = stringResource(R.string.settings_tags),
            icon = Icons.AutoMirrored.Rounded.Label,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (state.tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_tags_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = spacing.xs),
                    )
                } else {
                    state.tags.forEachIndexed { index, tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.colorHex)),
                            )
                            Spacer(Modifier.width(spacing.md))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onDeleteTag(tag.id) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteForever,
                                    contentDescription = stringResource(R.string.settings_delete_tag),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        if (index < state.tags.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.xs))
                }

                if (stackInputRow) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            label = { Text(stringResource(R.string.settings_new_tag)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                        )
                        AppButton(
                            text = stringResource(R.string.action_add),
                            onClick = {
                                if (newTag.isNotBlank()) {
                                    onAddTag(newTag)
                                    newTag = ""
                                }
                            },
                            enabled = newTag.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            label = { Text(stringResource(R.string.settings_new_tag)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                        )
                        AppButton(
                            text = stringResource(R.string.action_add),
                            onClick = {
                                if (newTag.isNotBlank()) {
                                    onAddTag(newTag)
                                    newTag = ""
                                }
                            },
                            enabled = newTag.isNotBlank(),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
        }

        // 7. Data & Backup
        SettingsSection(
            title = stringResource(R.string.settings_data),
            icon = Icons.Rounded.Backup,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                AppButton(
                    text = stringResource(R.string.settings_export),
                    onClick = launchExport,
                    enabled = !backupBusy,
                    loading = backupState is BackupUiState.Working && backupState.isExport,
                    variant = AppButtonVariant.Secondary,
                    leadingIcon = {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
                AppButton(
                    text = stringResource(R.string.settings_import),
                    onClick = launchImport,
                    enabled = !backupBusy,
                    loading = backupState is BackupUiState.Working && !backupState.isExport,
                    variant = AppButtonVariant.Secondary,
                    leadingIcon = {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
                AppButton(
                    text = stringResource(R.string.settings_reset),
                    onClick = { showResetDialog = true },
                    variant = AppButtonVariant.Secondary,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
            }
        }

        // 8. About
        SettingsSection(
            title = stringResource(R.string.settings_about),
            icon = Icons.Rounded.Info,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xs))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = Dates.formatDigits(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        appLocale,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_built_with),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        LocalizedAlertDialog(
            localizedContext = localizedContext,
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dialog_reset_title)) },
            text = { Text(stringResource(R.string.dialog_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.dialog_reset_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    BackupDialogs(
        backupState = backupState,
        calendarType = settings.calendarType,
        localizedContext = localizedContext,
        onDismiss = onDismissBackupDialog,
        onConfirmMerge = onConfirmMergeRestore,
        onRequestReplace = onRequestReplaceRestore,
        onBackToPreview = onBackToBackupPreview,
        onConfirmReplace = onConfirmReplaceRestore,
    )
}

/** Titled Settings group with consistent [SectionHeader] icon badge and [AppCard] container. */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    onCardClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column {
        SectionHeader(
            title = title,
            leading = {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )
        Spacer(Modifier.height(spacing.sm))
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            onClick = onCardClick,
        ) {
            Column(modifier = Modifier.padding(spacing.lg)) {
                content()
            }
        }
    }
}

@Composable
private fun ThemeSelector(
    currentMode: Int,
    onSelect: (Int) -> Unit,
) {
    val modes = listOf(
        AppSettings.ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        AppSettings.ThemeMode.LIGHT to stringResource(R.string.theme_light),
        AppSettings.ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    SegmentedOptionRow(
        options = modes,
        current = currentMode,
        onSelect = onSelect,
    )
}

@Composable
private fun CalendarTypeSelector(
    current: CalendarType,
    stackVertically: Boolean,
    onSelect: (CalendarType) -> Unit,
) {
    val options = listOf(
        CalendarType.GREGORIAN to stringResource(R.string.calendar_gregorian),
        CalendarType.JALALI to stringResource(R.string.calendar_jalali),
    )
    SegmentedOptionRow(
        options = options,
        current = current,
        stackVertically = stackVertically,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> SegmentedOptionRow(
    options: List<Pair<T, String>>,
    current: T,
    stackVertically: Boolean = false,
    onSelect: (T) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val renderOption: @Composable (Modifier, T, String) -> Unit = { itemModifier, value, label ->
        val selected = current == value
        val bg by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(220),
            label = "seg-bg",
        )
        val fg by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            animationSpec = tween(220),
            label = "seg-fg",
        )
        Surface(
            modifier = itemModifier,
            shape = MaterialTheme.shapes.small,
            color = bg,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            onClick = { onSelect(value) },
        ) {
            Box(
                modifier = (if (stackVertically) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                    .padding(horizontal = spacing.xs, vertical = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (stackVertically) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            options.forEach { (value, label) ->
                renderOption(Modifier.fillMaxWidth(), value, label)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            options.forEach { (value, label) ->
                renderOption(Modifier.weight(1f).fillMaxHeight(), value, label)
            }
        }
    }
}

/** Human-readable name of the active language, for the Settings summary row. */
@Composable
private fun currentLanguageLabel(tag: String): String {
    val sanitized = AppLanguage.sanitize(tag)
    if (sanitized == AppLanguage.SYSTEM) return stringResource(R.string.settings_language_system)
    return AppLanguage.supported.firstOrNull { it.tag == sanitized }?.nativeName
        ?: stringResource(R.string.settings_language_system)
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
        Spacer(Modifier.width(spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DigestTimeSlider(
    minutes: Int,
    appLocale: Locale,
    onMinutesChange: (Int) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.padding(top = spacing.sm)) {
        Text(
            text = stringResource(
                R.string.settings_digest_time,
                Dates.formatDigits(Dates.timeLabel(minutes, appLocale), appLocale),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onMinutesChange(it.toInt()) },
            valueRange = 0f..1440f,
            steps = 23, // every hour
        )
    }
}

@Composable
private fun PaletteSelector(
    currentPalette: String,
    onSelect: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val palettes = listOf(
        PaletteOption(AppSettings.Palettes.VIOLET, stringResource(R.string.palette_violet), PaletteDef.VIOLET.primary),
        PaletteOption(AppSettings.Palettes.SKY, stringResource(R.string.palette_sky), PaletteDef.SKY.primary),
        PaletteOption(AppSettings.Palettes.PURPLE, stringResource(R.string.palette_purple), PaletteDef.PURPLE.primary),
        PaletteOption(AppSettings.Palettes.PINK, stringResource(R.string.palette_pink), PaletteDef.PINK.primary),
        PaletteOption(AppSettings.Palettes.TEAL, stringResource(R.string.palette_teal), PaletteDef.TEAL.primary),
        PaletteOption(AppSettings.Palettes.SUNSET, stringResource(R.string.palette_sunset), PaletteDef.SUNSET.primary),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        palettes.forEach { option ->
            val selected = currentPalette == option.key
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(option.key) }
                    .padding(spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(option.color)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Transparent
                            },
                            shape = MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private data class PaletteOption(val key: String, val label: String, val color: Color)
