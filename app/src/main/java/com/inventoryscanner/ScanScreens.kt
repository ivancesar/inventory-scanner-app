package com.inventoryscanner

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var rename by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var pill by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // code to isDuplicate
    val paused = dup != null || drawer || finish || uploading || uploadError != null || rename

    // Hide the pill after a moment, but keep the amber one while the duplicate dialog is open.
    LaunchedEffect(pill, dup) { if (pill != null && dup == null) { delay(1500); pill = null } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { torch = false }

    // White status/nav bar icons over the black bars; restore the theme's choice on leaving.
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val light = bars?.run { isAppearanceLightStatusBars to isAppearanceLightNavigationBars }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (bars != null && light != null) {
                bars.isAppearanceLightStatusBars = light.first
                bars.isAppearanceLightNavigationBars = light.second
            }
        }
    }

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
        pill = code to false
    }

    fun handle(code: String) {
        when {
            code.length > MAX_CELL -> Toast.makeText(context, R.string.scan_too_long, Toast.LENGTH_SHORT).show()
            area.scans.any { it.code == code } -> {
                dup = code
                pill = code to true
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                )
            }
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

    val n = area.scans.size
    // Always dark like a camera app; the drawer and dialogs below stay outside and follow the app theme.
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        torch, { torch = it },
                        colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = AMBER),
                    ) { Icon(painterResource(R.drawable.ic_torch), stringResource(R.string.torch)) }
                    Surface(
                        onClick = { rename = true },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        shape = CircleShape,
                        color = Color(0xE6202020),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(area.name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_pencil), stringResource(R.string.rename_title), Modifier.size(18.dp))
                        }
                    }
                    SettingsButton(onSettings)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    BarcodeCamera(paused, ::handle, Modifier.fillMaxSize(), torch)
                    pill?.let { (code, isDup) ->
                        Surface(
                            Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            shape = CircleShape,
                            color = if (isDup) AMBER else Color(0xFF2E7D32),
                            contentColor = if (isDup) Color.Black else Color.White,
                        ) {
                            Text(
                                if (isDup) code else "✓ $code",
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BadgedBox(badge = {
                        if (n > 0) Badge(containerColor = Color.White, contentColor = Color.Black) {
                            Text("$n", Modifier.clearAndSetSemantics {}) // already in the icon's description
                        }
                    }) {
                        // Dark tile, like a camera app's gallery thumbnail.
                        Surface({ drawer = true }, Modifier.size(56.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF2A2A2A)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_clipboard_list),
                                    pluralStringResource(R.plurals.scans_count, n, n),
                                    Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f)) // empty on purpose: nothing that looks like a shutter
                    Button(
                        { finish = true },
                        enabled = n > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) {
                        Icon(painterResource(R.drawable.ic_cloud_upload), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.scan_finish))
                    }
                }
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
            onDismissRequest = { dup = null; pill = null },
            title = { Text(stringResource(R.string.scan_dup_title)) },
            text = { Text(stringResource(R.string.scan_dup_message, code)) },
            confirmButton = { TextButton({ dup = null; save(code) }) { Text(stringResource(R.string.scan_dup_add)) } },
            dismissButton = { TextButton({ dup = null; pill = null }) { Text(stringResource(R.string.scan_dup_skip)) } },
        )
    }

    if (rename) {
        // Whole name selected and focused, so typing replaces it and a tap can place the cursor.
        var text by remember { mutableStateOf(TextFieldValue(area.name, TextRange(0, area.name.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    text,
                    { text = if (it.text.length > MAX_CELL) it.copy(text = it.text.take(MAX_CELL)) else it },
                    Modifier.focusRequester(focus),
                    label = { Text(stringResource(R.string.area_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton({ area = store.rename(text.text); rename = false }, enabled = text.text.isNotBlank()) {
                    Text(stringResource(R.string.settings_save))
                }
            },
            dismissButton = { TextButton({ rename = false }) { Text(stringResource(R.string.cancel)) } },
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

private val AMBER = Color(0xFFFFB300)

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title)) }
}
