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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tag.routine.BuildConfig
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.AppSettings
import com.tag.routine.data.Dates
import com.tag.routine.data.ImportOutcome
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.BackupFilePicker
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.i18n.AppLanguage
import com.tag.routine.ui.i18n.LocalizedAlertDialog
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.theme.PaletteDef
import com.tag.routine.ui.theme.spacing

@Composable
fun SettingsScreen(
    state: UiState,
    onThemeMode: (Int) -> Unit,
    onThemePalette: (String) -> Unit,
    onDynamicColor: (Boolean) -> Unit = {},
    onNotifications: (Boolean) -> Unit,
    onDigest: (Boolean) -> Unit,
    onDigestMinutes: (Int) -> Unit,
    onExport: () -> String,
    onImported: (ImportOutcome) -> Unit,
    onReset: () -> Unit,
    onAddTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onSetName: (String) -> Unit,
    onFocusMinutes: (Int) -> Unit,
    onOpenLanguages: () -> Unit,
) {
    val settings = state.settings
    // Dialogs are hosted in their own window, which resets LocalContext to the Activity's
    // system-locale context. Capture the app-localized one here so dialog text stays in
    // the language the user picked.
    val localizedContext = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<ImportOutcome?>(null) }
    var newTag by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf(settings.userName) }
    var focusInput by remember { mutableStateOf(settings.focusMinutes.toString()) }
    val pickBackup = BackupFilePicker { outcome ->
        importResult = outcome
        onImported(outcome)
    }

    // The stored name can change elsewhere (first-launch onboarding), so mirror it
    // into the field whenever the screen is not being edited.
    LaunchedEffect(settings.userName) {
        if (nameInput.isBlank()) nameInput = settings.userName
    }
    LaunchedEffect(settings.focusMinutes) {
        focusInput = settings.focusMinutes.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Profile
        SettingsSection(title = stringResource(R.string.settings_profile)) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.settings_your_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(10.dp))
            AppButton(
                text = stringResource(R.string.settings_save_name),
                onClick = { onSetName(nameInput.trim()); nameInput = nameInput.trim() },
                enabled = nameInput.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
            )
        }

        // Appearance
        SettingsSection(title = stringResource(R.string.settings_appearance)) {
            Text(
                text = stringResource(R.string.settings_theme_mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ThemeSelector(
                currentMode = settings.themeMode,
                onSelect = onThemeMode,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(16.dp))
                SettingsToggle(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_sub),
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }
            if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_theme_colour),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PaletteSelector(
                    currentPalette = settings.themePalette,
                    onSelect = onThemePalette,
                )
            }
        }

        // Focus
        SettingsSection(title = stringResource(R.string.settings_focus)) {
            Text(
                text = stringResource(R.string.settings_focus_default),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.settings_focus_default_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = focusInput,
                    onValueChange = { focusInput = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.focus_custom_minutes)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                val parsed = focusInput.toIntOrNull()
                val valid = parsed != null &&
                    parsed in AppConstants.MIN_FOCUS_MINUTES..AppConstants.MAX_FOCUS_MINUTES
                AppButton(
                    text = stringResource(R.string.action_save),
                    onClick = { parsed?.let(onFocusMinutes) },
                    enabled = valid,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }

        // Notifications
        SettingsSection(title = stringResource(R.string.settings_notifications)) {
            SettingsToggle(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_enable_reminders),
                subtitle = stringResource(R.string.settings_enable_reminders_sub),
                checked = settings.notificationsEnabled,
                onCheckedChange = onNotifications,
            )
            if (settings.notificationsEnabled) {
                SettingsToggle(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_daily_digest),
                    subtitle = stringResource(
                        R.string.settings_daily_digest_sub,
                        Dates.timeLabel(settings.dailyDigestMinutes),
                    ),
                    checked = settings.dailyDigestEnabled,
                    onCheckedChange = onDigest,
                )
                if (settings.dailyDigestEnabled) {
                    DigestTimeSlider(
                        minutes = settings.dailyDigestMinutes,
                        onMinutesChange = onDigestMinutes,
                    )
                }
            }
        }

        // Tags
        SettingsSection(title = stringResource(R.string.settings_tags)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Color(tag.colorHex)),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onDeleteTag(tag.id) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = stringResource(R.string.settings_delete_tag),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text(stringResource(R.string.settings_new_tag)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }

        // Data
        SettingsSection(title = stringResource(R.string.settings_data)) {
            AppButton(
                text = stringResource(R.string.settings_export),
                onClick = { exportPath = onExport() },
                variant = AppButtonVariant.Secondary,
                leadingIcon = {
                    Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(10.dp))
            AppButton(
                text = stringResource(R.string.settings_import),
                onClick = { pickBackup() },
                variant = AppButtonVariant.Secondary,
                leadingIcon = {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(10.dp))
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
                shape = RoundedCornerShape(14.dp),
            )
        }

        // About
        SettingsSection(title = stringResource(R.string.settings_about)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_built_with),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // Languages — kept last, as a single row that opens the dedicated language page.
        SettingsSection(title = stringResource(R.string.settings_languages)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                onClick = onOpenLanguages,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_language_open),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_language_current,
                                currentLanguageLabel(settings.language),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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

    // Export result dialog
    exportPath?.let { path ->
        LocalizedAlertDialog(
            localizedContext = localizedContext,
            onDismissRequest = { exportPath = null },
            title = { Text(stringResource(R.string.dialog_export_title)) },
            text = { Text(stringResource(R.string.dialog_export_body, path)) },
            confirmButton = {
                TextButton(onClick = { exportPath = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    // Import result dialog
    importResult?.let { result ->
        LocalizedAlertDialog(
            localizedContext = localizedContext,
            onDismissRequest = { importResult = null },
            title = {
                Text(
                    stringResource(
                        if (result.ok) R.string.dialog_import_title else R.string.dialog_import_failed,
                    ),
                )
            },
            text = {
                if (result.ok) {
                    Text(
                        stringResource(
                            R.string.dialog_import_body,
                            result.routines,
                            result.tasks,
                            result.tags,
                        ),
                    )
                }
                // On failure the raw parser message is English-only, so it is not shown;
                // the localized title already says the file could not be read.
            },
            confirmButton = {
                TextButton(onClick = { importResult = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

/** Outcome of a backup import, surfaced to the user in a dialog. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.lg)) {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { (mode, label) ->
            val selected = currentMode == mode
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(220),
                label = "theme-bg",
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(220),
                label = "theme-fg",
            )
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = bg,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                onClick = { onSelect(mode) },
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DigestTimeSlider(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_digest_time, Dates.timeLabel(minutes)),
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        palettes.forEach { option ->
            val selected = currentPalette == option.key
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(option.key) }
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(option.color)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(14.dp),
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
                Spacer(Modifier.height(5.dp))
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
