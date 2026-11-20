package com.tag.routine.widget

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.tag.routine.data.AppSettings
import com.tag.routine.ui.i18n.AppLanguage
import java.util.Locale

/**
 * Resolves the language the widget should render in.
 *
 * The widget is drawn by the launcher, not by the app's Compose tree, so it does not
 * inherit the in-app language override that `LocalizedApp` provides. Without this, a user
 * who picked Persian inside the app would get an English widget. This mirrors
 * `LocalizedApp`: an empty stored tag means "follow the device", anything else is applied
 * as a configuration override.
 */
object WidgetLocale {

    /** The locale the widget should use for [settings]. */
    fun localeFor(settings: AppSettings): Locale {
        val tag = AppLanguage.sanitize(settings.language)
        if (tag.isBlank()) {
            return Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        }
        return Locale.forLanguageTag(tag)
    }

    /**
     * A context whose resources resolve against [settings]' language, so `getString` and
     * `getQuantityString` return the app's language rather than the device's.
     *
     * Falls back to [base] unchanged when the override cannot be built, so a widget update
     * can never fail just because a locale is unavailable.
     */
    fun localizedContext(base: Context, settings: AppSettings): Context {
        val locale = localeFor(settings)
        return runCatching {
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            base.createConfigurationContext(config)
        }.getOrDefault(base)
    }

    /** True when [locale] is written right-to-left, so the widget can mirror its layout. */
    fun isRtl(locale: Locale): Boolean =
        locale.language.lowercase(Locale.ROOT) in RTL_LANGUAGES

    private val RTL_LANGUAGES = setOf("fa", "ar", "he", "iw", "ur", "ps", "sd", "ug", "yi")
}
