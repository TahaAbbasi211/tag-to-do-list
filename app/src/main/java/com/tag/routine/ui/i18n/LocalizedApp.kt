package com.tag.routine.ui.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Wraps [content] in a context configured for [languageTag], so every
 * `stringResource(...)` inside resolves against that language's resources.
 *
 * This works on all supported API levels without AppCompat: `stringResource` reads
 * `LocalContext.current.resources`, so supplying a configuration-overridden context is
 * enough. An empty or null [languageTag] keeps the device language.
 *
 * The layout direction is flipped to RTL for languages that need it (Persian), so
 * Compose mirrors padding, alignment and row order automatically.
 */
@Composable
fun LocalizedApp(
    languageTag: String?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val tag = languageTag.orEmpty()

    if (tag.isBlank()) {
        CompositionLocalProvider(
            LocalConfiguration provides LocalConfiguration.current,
            LocalLayoutDirection provides context.resources.layoutDirection(),
            content = content,
        )
        return
    }

    val locale = remember(tag) { Locale.forLanguageTag(tag) }
    val localizedContext = remember(tag, context) { LocalizedContext(context, locale) }
    val direction = remember(tag) {
        if (isRtl(locale)) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    // Date/time formatters in `Dates` read Locale.getDefault(), so keep it in sync.
    SideEffect { Locale.setDefault(locale) }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides direction,
        content = content,
    )
}

/**
 * A [ContextWrapper] around [base] whose [getResources] resolves against [locale].
 *
 * It deliberately wraps the ORIGINAL context instead of returning what
 * `createConfigurationContext` produces directly: that call yields a bare ContextImpl,
 * which is not a `ContextWrapper`. Composition locals that walk the context chain to
 * find the Activity — notably `LocalActivityResultRegistryOwner`, whose default value
 * follows `ContextWrapper.baseContext` — stopped finding it, so
 * `rememberLauncherForActivityResult` threw `IllegalStateException` as soon as a
 * language override was active. That crashed the Settings screen, whose backup picker
 * is the app's only activity-result launcher.
 */
private class LocalizedContext(base: Context, locale: Locale) : ContextWrapper(base) {

    private val localizedResources: Resources by lazy {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        base.createConfigurationContext(config).resources
    }

    override fun getResources(): Resources = localizedResources
}

private fun android.content.res.Resources.layoutDirection(): LayoutDirection =
    if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

private fun isRtl(locale: Locale): Boolean {
    val language = locale.language.lowercase(Locale.ROOT)
    return language in RTL_LANGUAGES
}

private val RTL_LANGUAGES = setOf("fa", "ar", "he", "iw", "ur", "ps", "sd", "ug", "yi")

/** Language tags the app ships translations for, in menu order. */
object AppLanguage {
    const val SYSTEM = ""
    const val ENGLISH = "en"
    const val PERSIAN = "fa"
    const val FRENCH = "fr"
    const val GERMAN = "de"
    const val TURKISH = "tr"

    /** One selectable language: its persisted tag plus native and English names. */
    data class Entry(
        val tag: String,
        /** Name in the language's own script, shown as the row's primary label. */
        val nativeName: String,
        /** English name, shown as a secondary line so every entry is discoverable. */
        val englishName: String,
    )

    /** Every supported language, in menu order. */
    val supported: List<Entry> = listOf(
        Entry(ENGLISH, "English", "English"),
        Entry(PERSIAN, "فارسی", "Persian"),
        Entry(FRENCH, "Français", "French"),
        Entry(GERMAN, "Deutsch", "German"),
        Entry(TURKISH, "Türkçe", "Turkish"),
    )

    /** Every selectable value, `SYSTEM` first. */
    val all = listOf(SYSTEM) + supported.map { it.tag }

    /** Resolves a stored tag to a valid one, falling back to the system default. */
    fun sanitize(tag: String?): String = if (tag != null && tag in all) tag else SYSTEM
}
