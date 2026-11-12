package com.tag.routine.ui.i18n

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Re-applies the in-app language override inside a dialog window.
 *
 * Compose dialogs â€” `Dialog`, `ModalBottomSheet`, `DatePickerDialog`, `AlertDialog` â€” host
 * their content in a separate window. That window has its own `AndroidComposeView`, whose
 * context comes from the Activity, so `LocalContext` inside the dialog is the Activity's
 * context and carries the *system* locale rather than the language the user picked in the
 * app. Everything composed in the dialog therefore resolved `stringResource(...)` against
 * the device language: the routine and task editors rendered in English while the rest of
 * the app was Persian.
 *
 * `MaterialTheme` kept working because dialogs do not override it, which is why only the
 * text gave the problem away.
 *
 * Wrap the dialog's content in this, passing the localized context from the calling
 * composition (the one [LocalizedApp] provided), and the override applies to the dialog's
 * own strings and to every Material component nested inside it.
 */
@Composable
fun ProvideAppLocale(
    localizedContext: Context,
    content: @Composable () -> Unit,
) {
    val configuration = localizedContext.resources.configuration
    val direction =
        if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides direction,
        content = content,
    )
}

/**
 * [AlertDialog] whose title, body and buttons render in the app's chosen language.
 *
 * Each slot is composed inside the dialog window, so each one has to be re-wrapped; doing
 * it here keeps the call sites readable.
 */
@Composable
fun LocalizedAlertDialog(
    localizedContext: Context,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title?.let { slot -> { ProvideAppLocale(localizedContext) { slot() } } },
        text = text?.let { slot -> { ProvideAppLocale(localizedContext) { slot() } } },
        confirmButton = { ProvideAppLocale(localizedContext) { confirmButton() } },
        dismissButton = dismissButton?.let { slot ->
            { ProvideAppLocale(localizedContext) { slot() } }
        },
    )
}
