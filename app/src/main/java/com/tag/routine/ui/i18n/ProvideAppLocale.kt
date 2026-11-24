package com.tag.routine.ui.i18n

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tag.routine.ui.motion.AnimatedDialogSurface

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
 * Material 3 dialog whose title, body and buttons render in the app's chosen language
 * and enter with [AnimatedDialogSurface] (spring scale + fade).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalizedAlertDialog(
    localizedContext: Context,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        AnimatedDialogSurface {
            ProvideAppLocale(localizedContext) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 560.dp)
                        .fillMaxWidth(),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    ) {
                        if (title != null) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        title()
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        if (text != null) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        text()
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        dismissButton?.invoke()
                                        confirmButton()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
