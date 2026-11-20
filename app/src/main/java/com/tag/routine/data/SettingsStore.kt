package com.tag.routine.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "tag_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val themeMode = intPreferencesKey("theme_mode")
        val themePalette = stringPreferencesKey("theme_palette")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val language = stringPreferencesKey("language")
        val calendarType = stringPreferencesKey("calendar_type")
        val userName = stringPreferencesKey("user_name")
        val notifications = booleanPreferencesKey("notifications")
        val digestEnabled = booleanPreferencesKey("digest_enabled")
        val digestMinutes = intPreferencesKey("digest_minutes")
        val haptics = booleanPreferencesKey("haptics")
        val seeded = booleanPreferencesKey("seeded")
        val onboarded = booleanPreferencesKey("onboarded")
        val focusMinutes = intPreferencesKey("focus_minutes")
        val activeFocusRun = stringPreferencesKey("active_focus_run")
        val firstLaunchDate = stringPreferencesKey("first_launch_date")
        val templatesCardDismissed = booleanPreferencesKey("templates_card_dismissed")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        val storedName = prefs[Keys.userName] ?: ""
        AppSettings(
            themeMode = prefs[Keys.themeMode] ?: AppSettings.ThemeMode.SYSTEM,
            themePalette = prefs[Keys.themePalette] ?: AppSettings.Palettes.VIOLET,
            dynamicColor = prefs[Keys.dynamicColor] ?: false,
            language = prefs[Keys.language] ?: "",
            calendarType = CalendarType.fromKey(prefs[Keys.calendarType]),
            userName = storedName,
            notificationsEnabled = prefs[Keys.notifications] ?: true,
            dailyDigestEnabled = prefs[Keys.digestEnabled] ?: true,
            dailyDigestMinutes = prefs[Keys.digestMinutes] ?: (8 * 60),
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            // Upgrade path: installs from before onboarding existed have no flag, so
            // treat an already-saved name as "onboarded" and never ask those users
            // for their name a second time.
            onboarded = (prefs[Keys.onboarded] ?: false) || storedName.isNotBlank(),
            focusMinutes = AppConstants.clampFocusMinutes(
                prefs[Keys.focusMinutes] ?: AppConstants.DEFAULT_FOCUS_MINUTES,
            ),
            firstLaunchDate = prefs[Keys.firstLaunchDate] ?: "",
            templatesCardDismissed = prefs[Keys.templatesCardDismissed] ?: false,
        )
    }

    val seeded: Flow<Boolean> = context.settingsStore.data.map { it[Keys.seeded] ?: false }

    /**
     * The focus run that is currently in progress, or null when no timer is running.
     *
     * This is the durable copy of the timer: it is written when a run starts, pauses or
     * resumes, and cleared when it ends. Because it lives in DataStore rather than in
     * memory, a run survives the process being killed when the user closes the app.
     */
    val activeFocusRun: Flow<ActiveFocusRun?> = context.settingsStore.data.map { prefs ->
        ActiveFocusRunCodec.decode(prefs[Keys.activeFocusRun])
    }

    suspend fun setActiveFocusRun(run: ActiveFocusRun) = context.settingsStore.edit {
        it[Keys.activeFocusRun] = ActiveFocusRunCodec.encode(run)
    }

    suspend fun clearActiveFocusRun() = context.settingsStore.edit {
        it.remove(Keys.activeFocusRun)
    }

    suspend fun setSeeded(value: Boolean) = context.settingsStore.edit { it[Keys.seeded] = value }

    suspend fun setThemeMode(mode: Int) = context.settingsStore.edit { it[Keys.themeMode] = mode }

    suspend fun setThemePalette(palette: String) =
        context.settingsStore.edit { it[Keys.themePalette] = palette }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.settingsStore.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setLanguage(tag: String) =
        context.settingsStore.edit { it[Keys.language] = tag }

    suspend fun setCalendarType(type: CalendarType) =
        context.settingsStore.edit { it[Keys.calendarType] = type.key }

    suspend fun setUserName(name: String) =
        context.settingsStore.edit { it[Keys.userName] = name }

    suspend fun setNotifications(enabled: Boolean) =
        context.settingsStore.edit { it[Keys.notifications] = enabled }

    suspend fun setDigestEnabled(enabled: Boolean) =
        context.settingsStore.edit { it[Keys.digestEnabled] = enabled }

    suspend fun setDigestMinutes(minutes: Int) =
        context.settingsStore.edit { it[Keys.digestMinutes] = minutes }

    suspend fun setHaptics(enabled: Boolean) = context.settingsStore.edit { it[Keys.haptics] = enabled }

    suspend fun setOnboarded(value: Boolean) = context.settingsStore.edit { it[Keys.onboarded] = value }

    suspend fun setFocusMinutes(minutes: Int) = context.settingsStore.edit {
        it[Keys.focusMinutes] = AppConstants.clampFocusMinutes(minutes)
    }

    /** Records [todayIso] as the first launch date if none has been stored yet. */
    suspend fun ensureFirstLaunchDate(todayIso: String) = context.settingsStore.edit { prefs ->
        if (prefs[Keys.firstLaunchDate].isNullOrBlank()) {
            prefs[Keys.firstLaunchDate] = todayIso
        }
    }

    suspend fun setTemplatesCardDismissed(dismissed: Boolean) = context.settingsStore.edit {
        it[Keys.templatesCardDismissed] = dismissed
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    /** Atomically applies preferences from a validated backup without touching active timer state. */
    suspend fun applyBackupSettings(backup: BackupSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.themeMode] = backup.themeMode.coerceIn(0, 2)
            prefs[Keys.themePalette] = BackupSettings.validPalette(backup.themePalette)
            prefs[Keys.dynamicColor] = backup.dynamicColor
            prefs[Keys.language] = BackupSettings.validLanguage(backup.language)
            prefs[Keys.calendarType] = backup.calendarType.key
            prefs[Keys.userName] = backup.userName
            prefs[Keys.notifications] = backup.notificationsEnabled
            prefs[Keys.digestEnabled] = backup.dailyDigestEnabled
            prefs[Keys.digestMinutes] = backup.dailyDigestMinutes.coerceIn(0, 1439)
            prefs[Keys.haptics] = backup.hapticsEnabled
            prefs[Keys.focusMinutes] = AppConstants.clampFocusMinutes(backup.focusMinutes)
            prefs[Keys.templatesCardDismissed] = backup.templatesCardDismissed
            prefs[Keys.onboarded] = true
            prefs[Keys.seeded] = true
        }
    }
}
