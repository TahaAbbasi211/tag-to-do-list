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
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "tag_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val themeMode = intPreferencesKey("theme_mode")
        val themePalette = stringPreferencesKey("theme_palette")
        val language = stringPreferencesKey("language")
        val userName = stringPreferencesKey("user_name")
        val notifications = booleanPreferencesKey("notifications")
        val digestEnabled = booleanPreferencesKey("digest_enabled")
        val digestMinutes = intPreferencesKey("digest_minutes")
        val haptics = booleanPreferencesKey("haptics")
        val seeded = booleanPreferencesKey("seeded")
        val onboarded = booleanPreferencesKey("onboarded")
        val focusMinutes = intPreferencesKey("focus_minutes")
        val activeFocusRun = stringPreferencesKey("active_focus_run")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        val storedName = prefs[Keys.userName] ?: ""
        AppSettings(
            themeMode = prefs[Keys.themeMode] ?: AppSettings.ThemeMode.SYSTEM,
            themePalette = prefs[Keys.themePalette] ?: AppSettings.Palettes.VIOLET,
            language = prefs[Keys.language] ?: "",
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

    suspend fun setLanguage(tag: String) =
        context.settingsStore.edit { it[Keys.language] = tag }

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
}
