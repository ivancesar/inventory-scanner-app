package com.inventoryscanner

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The server rejects any cell longer than this, which would block the whole upload. */
private const val MAX_CELL = 500

/** Name a new area. onStart gets the trimmed, non-blank name. */
@Composable
fun AreaScreen(onStart: (String) -> Unit, onSettings: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val start = { if (name.isNotBlank()) onStart(name.trim()) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.area_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            SettingsButton(onSettings)
        }
        OutlinedTextField(
            name,
            { name = it.take(MAX_CELL) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.area_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { start() }),
        )
        Button(start, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { Text(stringResource(R.string.area_start)) }
    }
}

/** Scanning an open area (store.load() != null). onClosed after a successful upload or abandon (store is then empty). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(store: Store, settings: Settings, onClosed: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf(store.load()!!) }
    var dup by remember { mutableStateOf<String?>(null) }
    var drawer by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var abandon by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    // Throws on some devices when audio can't init; a missing beep beats a crash.
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    fun save(code: String) {
        store.add(code) // on disk before any feedback
        area = store.load()!!
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        )
    }

    fun handle(code: String) {
        when {
            code.length > MAX_CELL -> Toast.makeText(context, R.string.scan_too_long, Toast.LENGTH_SHORT).show()
            area.scans.any { it.code == code } -> dup = code
            else -> save(code)
        }
    }

    fun upload() {
        val a = area
        uploading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { SheetClient.upload(settings.url, a.batchId, a.rows(settings.user)) }
            uploading = false
            result.onSuccess {
                // Only now, after the server confirmed. Count is ours: the server says 0 for a duplicate retry.
                store.clear()
                Toast.makeText(context, res.getQuantityString(R.plurals.upload_done, a.scans.size, a.scans.size), Toast.LENGTH_LONG).show()
                onClosed()
            }.onFailure {
                uploadError = if (it is IOException) res.getString(R.string.error_network) else it.message.orEmpty()
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(area.name, style = MaterialTheme.typography.titleLarge)
                Text(pluralStringResource(R.plurals.scans_count, area.scans.size, area.scans.size))
            }
            SettingsButton(onSettings)
        }
        BarcodeCamera(
            paused = dup != null || drawer || finish || uploading || uploadError != null,
            onCode = ::handle,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton({ drawer = true }, Modifier.weight(1f)) { Text(stringResource(R.string.scan_list)) }
            Button({ finish = true }, Modifier.weight(1f), enabled = area.scans.isNotEmpty()) {
                Text(stringResource(R.string.scan_finish))
            }
        }
    }

    if (drawer) {
        ModalBottomSheet({ drawer = false }) {
            if (area.scans.isEmpty()) Text(stringResource(R.string.drawer_empty), Modifier.padding(16.dp))
            // Newest first, but keep each scan's original index for store.delete().
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(area.scans.withIndex().reversed()) { (i, scan) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { deleteIndex = i })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(scan.time.substringAfter(' '), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(scan.code)
                    }
                }
            }
            TextButton(
                { abandon = true },
                Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.abandon)) }
        }
    }

    dup?.let { code ->
        AlertDialog(
            onDismissRequest = { dup = null },
            title = { Text(stringResource(R.string.scan_dup_title)) },
            text = { Text(stringResource(R.string.scan_dup_message, code)) },
            confirmButton = { TextButton({ dup = null; save(code) }) { Text(stringResource(R.string.scan_dup_add)) } },
            dismissButton = { TextButton({ dup = null }) { Text(stringResource(R.string.scan_dup_skip)) } },
        )
    }

    deleteIndex?.let { i ->
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(area.scans[i].code) },
            confirmButton = {
                TextButton({ store.delete(i); area = store.load()!!; deleteIndex = null }) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = { TextButton({ deleteIndex = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (abandon) {
        AlertDialog(
            onDismissRequest = { abandon = false },
            title = { Text(stringResource(R.string.abandon_title)) },
            text = { Text(stringResource(R.string.abandon_message, area.scans.size)) },
            confirmButton = {
                TextButton(
                    { abandon = false; drawer = false; store.clear(); onClosed() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.abandon_confirm)) }
            },
            dismissButton = { TextButton({ abandon = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (finish) {
        AlertDialog(
            onDismissRequest = { finish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = { Text(stringResource(R.string.finish_message, area.scans.size, area.name)) },
            confirmButton = { TextButton({ finish = false; upload() }) { Text(stringResource(R.string.finish_confirm)) } },
            dismissButton = { TextButton({ finish = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (uploading) {
        // No-op onDismissRequest: back and outside taps are swallowed until the upload returns.
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.uploading))
                }
            },
        )
    }

    uploadError?.let { msg ->
        AlertDialog(
            onDismissRequest = { uploadError = null },
            text = { Text(stringResource(R.string.upload_failed, msg)) },
            confirmButton = { TextButton({ uploadError = null; upload() }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton({ uploadError = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val label = stringResource(R.string.settings_title)
    IconButton(onClick, Modifier.semantics { contentDescription = label }) { Text("⚙") }
}
