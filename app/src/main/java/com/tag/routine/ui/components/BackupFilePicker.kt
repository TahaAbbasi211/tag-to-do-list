package com.tag.routine.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tag.routine.data.Dates

/**
 * Returns a callback that opens the Storage Access Framework save dialog with a dated
 * default filename (`tag-backup-YYYY-MM-DD.json`) and passes the chosen [Uri] to [onUriSelected].
 */
@Composable
fun rememberBackupExportLauncher(
    onUriSelected: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) onUriSelected(uri)
    }

    return remember(launcher) {
        { launcher.launch("tag-backup-${Dates.iso(Dates.today())}.json") }
    }
}

/**
 * Returns a callback that opens the Storage Access Framework file picker for choosing a
 * JSON backup file and passes the chosen [Uri] to [onUriSelected].
 */
@Composable
fun rememberBackupImportLauncher(
    onUriSelected: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onUriSelected(uri)
    }

    return remember(launcher) {
        { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }
}
