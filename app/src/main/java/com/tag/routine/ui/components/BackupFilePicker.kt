package com.tag.routine.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tag.routine.TagApp
import com.tag.routine.data.ImportOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds a callback that opens the system file picker, reads the chosen JSON backup and
 * restores it. Uses the Storage Access Framework, so no storage permission is needed.
 *
 * The repository is resolved from the application container at click time rather than at
 * composition time, which keeps the composable free of any ViewModel dependency.
 */
@Composable
fun BackupFilePicker(
    onResult: (ImportOutcome) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                restoreBackup(context, uri)
            }
            onResult(outcome)
        }
    }

    return remember(launcher) {
        { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }
}

private suspend fun restoreBackup(context: Context, uri: Uri): ImportOutcome {
    val repository = TagApp.containerOrNull?.repository
        ?: return ImportOutcome(ok = false, error = "App not ready")

    return try {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: return ImportOutcome(ok = false, error = "Could not open the file")

        val result = repository.importJson(raw)
        ImportOutcome(
            ok = true,
            routines = result.routines,
            tasks = result.tasks,
            tags = result.tags,
        )
    } catch (t: Throwable) {
        ImportOutcome(ok = false, error = t.message ?: "Invalid backup")
    }
}
