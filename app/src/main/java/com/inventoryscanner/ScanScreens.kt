package com.inventoryscanner

import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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

/** Camera screen for the open area. With no open area it asks for a new one's name (scanning paused meanwhile). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(store: Store, settings: Settings, onSettings: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Read from disk, so a killed app resumes its open area. null = no open area.
    var area by remember { mutableStateOf(store.load()) }
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
    val hasFlash = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }
    val paused = area == null || dup != null || drawer || finish || uploading || uploadError != null || rename

    // Hide the pill after a moment, but keep the amber one while the duplicate dialog is open.
    LaunchedEffect(pill, dup) { if (pill != null && dup == null) { delay(1500); pill = null } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { torch = false }

    // White status/nav bar icons over the black bars; restore the theme's choice on leaving.
    val window = LocalActivity.current?.window
    @Suppress("DEPRECATION") // navigationBarColor, only touched below API 29
    DisposableEffect(window) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val light = bars?.run { isAppearanceLightStatusBars to isAppearanceLightNavigationBars }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        // Below API 29 enableEdgeToEdge paints an opaque nav bar scrim (near-white in light theme).
        val legacy = window?.takeIf { Build.VERSION.SDK_INT < 29 }
        val navColor = legacy?.navigationBarColor
        legacy?.navigationBarColor = Color.Black.toArgb()
        onDispose {
            if (bars != null && light != null) {
                bars.isAppearanceLightStatusBars = light.first
                bars.isAppearanceLightNavigationBars = light.second
            }
            navColor?.let { legacy.navigationBarColor = it }
        }
    }

    // Throws on some devices when audio can't init; a missing beep beats a crash.
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    fun save(code: String) {
        store.add(code) // on disk before any feedback
        area = store.load()
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        )
        pill = code to false
    }

    fun handle(code: String) {
        // Live state, not the camera's `paused`: that only updates on recomposition, so a second
        // code could slip in while the duplicate popup opens.
        val a = area
        if (a == null || dup != null || rename || finish || uploading || uploadError != null || drawer) return
        when {
            code.length > MAX_CELL -> Toast.makeText(context, R.string.scan_too_long, Toast.LENGTH_SHORT).show()
            a.scans.any { it.code == code } -> {
                dup = code
                pill = code to true
                // A distinct sound too: without VIBRATE, Android 8-10 has no REJECT haptic.
                tone?.startTone(ToneGenerator.TONE_PROP_NACK, 400)
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                )
            }
            else -> save(code)
        }
    }

    fun upload() {
        val a = area ?: return
        uploading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { SheetClient.upload(settings.url, a.batchId, a.rows(settings.user)) }
            uploading = false
            result.onSuccess {
                // Only now, after the server confirmed. Count is ours: the server says 0 for a duplicate retry.
                store.clear()
                Toast.makeText(context, res.getQuantityString(R.plurals.upload_done, a.scans.size, a.scans.size), Toast.LENGTH_LONG).show()
                area = null
            }.onFailure {
                uploadError = if (it is IOException) res.getString(R.string.error_network) else it.message.orEmpty()
            }
        }
    }

    val a = area
    val n = a?.scans?.size ?: 0
    // Always dark like a camera app; the drawer and dialogs below stay outside and follow the app theme.
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasFlash) {
                        CircleButton(
                            R.drawable.ic_torch, stringResource(R.string.torch),
                            Modifier.toggleable(torch, role = Role.Switch) { torch = it },
                            if (torch) AMBER else Color.White,
                        )
                    } else Spacer(Modifier.size(48.dp)) // keeps the area pill centred
                    Surface(
                        onClick = { rename = area != null },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(48.dp),
                        shape = PILL,
                        color = CONTROL,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(a?.name.orEmpty(), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_pencil), stringResource(R.string.rename_title), Modifier.size(22.dp))
                        }
                    }
                    CircleButton(R.drawable.ic_settings, stringResource(R.string.settings_title), Modifier.clickable(role = Role.Button, onClick = onSettings))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // No camera behind the New area popup: no permission prompt stealing focus, no stray torch.
                    if (a != null) BarcodeCamera(paused, ::handle, Modifier.fillMaxSize(), torch)
                    pill?.let { (code, isDup) ->
                        Surface(
                            Modifier.align(Alignment.BottomCenter).padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
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
                        CircleButton(
                            R.drawable.ic_clipboard_list, pluralStringResource(R.plurals.scans_count, n, n),
                            Modifier.clickable(role = Role.Button) { drawer = true },
                        )
                    }
                    Spacer(Modifier.weight(1f)) // empty on purpose: nothing that looks like a shutter
                    Surface(
                        { finish = true },
                        Modifier.height(48.dp),
                        enabled = n > 0,
                        shape = PILL,
                        color = CONTROL,
                        contentColor = if (n > 0) Color.White else Color.White.copy(alpha = .38f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_cloud_upload), null, Modifier.size(22.dp))
                            Text(stringResource(R.string.scan_finish))
                        }
                    }
                }
            }
        }
    }

    if (a == null) {
        val activity = LocalActivity.current
        AreaNameDialog(
            R.string.area_title, "", R.string.area_start, { if (area == null) area = store.start(it) }, // double confirm
            R.string.settings_title, onSettings, onBack = { activity?.finish() },
        )
        return
    }

    if (drawer) {
        ModalBottomSheet({ drawer = false }) {
            if (a.scans.isEmpty()) Text(stringResource(R.string.drawer_empty), Modifier.padding(16.dp))
            // Newest first, but keep each scan's original index for store.delete().
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(a.scans.withIndex().reversed()) { (i, scan) ->
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
        AreaNameDialog(
            R.string.rename_title, a.name, R.string.settings_save, { area = store.rename(it); rename = false },
            R.string.cancel, { rename = false },
        )
    }

    deleteIndex?.let { i ->
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(a.scans[i].code) },
            confirmButton = {
                TextButton({ store.delete(i); area = store.load(); deleteIndex = null }) {
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
            text = { Text(stringResource(R.string.abandon_message, a.scans.size)) },
            confirmButton = {
                TextButton(
                    { abandon = false; drawer = false; store.clear(); area = null },
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
            text = { Text(stringResource(R.string.finish_message, a.scans.size, a.name)) },
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

/** Area name popup (new area / rename). onConfirm gets the non-blank text, untrimmed (Store trims). */
@Composable
private fun AreaNameDialog(
    title: Int,
    initial: String,
    confirmText: Int,
    onConfirm: (String) -> Unit,
    dismissText: Int,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null, // set = back runs this and outside taps are ignored
) {
    // Whole name selected, so typing replaces it and a tap can place the cursor.
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val confirm = { if (text.text.isNotBlank()) onConfirm(text.text) }
    AlertDialog(
        onDismissRequest = onBack ?: onDismiss,
        properties = DialogProperties(dismissOnClickOutside = onBack == null),
        title = { Text(stringResource(title)) },
        text = {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() } // in here, so the field is attached first
            OutlinedTextField(
                text,
                { text = if (it.text.length > MAX_CELL) it.copy(text = it.text.take(MAX_CELL)) else it },
                Modifier.focusRequester(focus),
                label = { Text(stringResource(R.string.area_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = { TextButton(confirm, enabled = text.text.isNotBlank()) { Text(stringResource(confirmText)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(dismissText)) } },
    )
}

private val AMBER = Color(0xFFFFB300)
private val CONTROL = Color(0xFF2A2A2A)
private val PILL = RoundedCornerShape(50)

/** 48dp dark circle with a 22dp icon; pass the clickable/toggleable in `modifier`. */
@Composable
private fun CircleButton(icon: Int, description: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(CONTROL).then(modifier), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), description, Modifier.size(22.dp), tint = tint)
    }
}
