package com.tag.routine.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tag.routine.widget.WidgetRefresh
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "tag_settings")
private val Context.focusRuntimeStore: DataStore<Preferences> by preferencesDataStore(name = "tag_focus_runtime")

class SettingsStore(private val context: Context) {

    /**
     * The widget renders with the app's language and calendar (and is redrawn on theme
     * changes too), so those settings must trigger a widget refresh after they are saved.
     */
    private fun refreshWidget() = WidgetRefresh.request(context)

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
        val breakModeEnabled = booleanPreferencesKey("break_mode_enabled")
        val shortBreakMinutes = intPreferencesKey("short_break_minutes")
        val longBreakMinutes = intPreferencesKey("long_break_minutes")
        val longBreakInterval = intPreferencesKey("long_break_interval")
        val autoStartBreak = booleanPreferencesKey("auto_start_break")
        val activeFocusRun = stringPreferencesKey("active_focus_run")
        val firstLaunchDate = stringPreferencesKey("first_launch_date")
        val templatesCardDismissed = booleanPreferencesKey("templates_card_dismissed")
        val animationSetting = stringPreferencesKey("animation_setting")
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
            breakModeEnabled = prefs[Keys.breakModeEnabled] ?: false,
            shortBreakMinutes = AppConstants.clampShortBreakMinutes(
                prefs[Keys.shortBreakMinutes] ?: AppConstants.DEFAULT_SHORT_BREAK_MINUTES,
            ),
            longBreakMinutes = AppConstants.clampLongBreakMinutes(
                prefs[Keys.longBreakMinutes] ?: AppConstants.DEFAULT_LONG_BREAK_MINUTES,
            ),
            longBreakInterval = AppConstants.clampLongBreakInterval(
                prefs[Keys.longBreakInterval] ?: AppConstants.DEFAULT_LONG_BREAK_INTERVAL,
            ),
            autoStartBreak = prefs[Keys.autoStartBreak] ?: true,
            firstLaunchDate = prefs[Keys.firstLaunchDate] ?: "",
            templatesCardDismissed = prefs[Keys.templatesCardDismissed] ?: false,
            animationSetting = AnimationSetting.fromKey(prefs[Keys.animationSetting]),
        )
    }

    val seeded: Flow<Boolean> = context.settingsStore.data.map { it[Keys.seeded] ?: false }

    /**
     * The focus run that is currently in progress, or null when no timer is running.
     *
     * Stored in its own preferences file (`tag_focus_runtime`, excluded from Android Auto
     * Backup) so transient timer state never leaks across device restores while still
     * migrating any run persisted by older versions in `tag_settings`.
     */
    val activeFocusRun: Flow<ActiveFocusRun?> = combine(
        context.focusRuntimeStore.data,
        context.settingsStore.data,
    ) { runtime, legacy ->
        ActiveFocusRunCodec.decode(runtime[Keys.activeFocusRun] ?: legacy[Keys.activeFocusRun])
    }

    suspend fun setActiveFocusRun(run: ActiveFocusRun) {
        context.focusRuntimeStore.edit {
            it[Keys.activeFocusRun] = ActiveFocusRunCodec.encode(run)
        }
        if (context.settingsStore.data.first().contains(Keys.activeFocusRun)) {
            context.settingsStore.edit { it.remove(Keys.activeFocusRun) }
        }
    }

    suspend fun clearActiveFocusRun() {
        context.focusRuntimeStore.edit { it.remove(Keys.activeFocusRun) }
        if (context.settingsStore.data.first().contains(Keys.activeFocusRun)) {
            context.settingsStore.edit { it.remove(Keys.activeFocusRun) }
        }
    }

    suspend fun setSeeded(value: Boolean) = context.settingsStore.edit { it[Keys.seeded] = value }

    suspend fun setThemeMode(mode: Int) {
        context.settingsStore.edit { it[Keys.themeMode] = mode }
        refreshWidget()
    }

    suspend fun setThemePalette(palette: String) {
        context.settingsStore.edit { it[Keys.themePalette] = palette }
        refreshWidget()
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.dynamicColor] = enabled }
        refreshWidget()
    }

    suspend fun setLanguage(tag: String) {
        context.settingsStore.edit { it[Keys.language] = tag }
        refreshWidget()
    }

    suspend fun setCalendarType(type: CalendarType) {
        context.settingsStore.edit { it[Keys.calendarType] = type.key }
        refreshWidget()
    }

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

    suspend fun setBreakModeEnabled(enabled: Boolean) = context.settingsStore.edit {
        it[Keys.breakModeEnabled] = enabled
    }

    suspend fun setShortBreakMinutes(minutes: Int) = context.settingsStore.edit {
        it[Keys.shortBreakMinutes] = AppConstants.clampShortBreakMinutes(minutes)
    }

    suspend fun setLongBreakMinutes(minutes: Int) = context.settingsStore.edit {
        it[Keys.longBreakMinutes] = AppConstants.clampLongBreakMinutes(minutes)
    }

    suspend fun setLongBreakInterval(sessions: Int) = context.settingsStore.edit {
        it[Keys.longBreakInterval] = AppConstants.clampLongBreakInterval(sessions)
    }

    suspend fun setAutoStartBreak(enabled: Boolean) = context.settingsStore.edit {
        it[Keys.autoStartBreak] = enabled
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

    suspend fun setAnimationSetting(setting: AnimationSetting) = context.settingsStore.edit {
        it[Keys.animationSetting] = setting.key
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
            prefs[Keys.breakModeEnabled] = backup.breakModeEnabled
            prefs[Keys.shortBreakMinutes] = AppConstants.clampShortBreakMinutes(backup.shortBreakMinutes)
            prefs[Keys.longBreakMinutes] = AppConstants.clampLongBreakMinutes(backup.longBreakMinutes)
            prefs[Keys.longBreakInterval] = AppConstants.clampLongBreakInterval(backup.longBreakInterval)
            prefs[Keys.autoStartBreak] = backup.autoStartBreak
            prefs[Keys.templatesCardDismissed] = backup.templatesCardDismissed
            prefs[Keys.animationSetting] = backup.animationSetting.key
            prefs[Keys.onboarded] = true
            prefs[Keys.seeded] = true
        }
    }
}
