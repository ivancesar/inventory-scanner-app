package com.inventoryscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning

/**
 * Continuous camera preview that reports barcodes. Handles the CAMERA runtime permission itself:
 * requests it on first show; if denied shows R.string.camera_permission text + an R.string.camera_grant button
 * (re-request, or open app settings if permanently denied).
 * Calls onCode once per "new" code (RepeatFilter), on the main thread. While paused = true, it must not call onCode
 * (a dialog is open) — but keep feeding RepeatFilter so the code still in view isn't re-reported the instant pause ends.
 */
@Composable
fun BarcodeCamera(paused: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun granted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    // The user may have granted it in system settings meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = granted() }

    if (!hasPermission) {
        Column(
            modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_permission))
            Button(onClick = {
                // No rationale after a denial means "don't ask again": only settings can grant it now.
                if (activity == null || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                    launcher.launch(Manifest.permission.CAMERA)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            }) { Text(stringResource(R.string.camera_grant)) }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPaused by rememberUpdatedState(paused)
    val currentOnCode by rememberUpdatedState(onCode)
    val controller = remember { LifecycleCameraController(context) }
    DisposableEffect(lifecycleOwner) {
        val scanner = BarcodeScanning.getClient()
        val filter = RepeatFilter()
        val main = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            main,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, main) { result ->
                val codes = result.getValue(scanner).orEmpty().mapNotNull { it.rawValue?.takeIf(String::isNotEmpty) }
                val code = filter.onFrame(codes, SystemClock.elapsedRealtime())
                if (code != null && !currentPaused) currentOnCode(code)
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            controller.clearImageAnalysisAnalyzer()
            scanner.close()
        }
    }
    AndroidView({ PreviewView(it).apply { this.controller = controller } }, modifier)
}

/** Suppresses repeats: the last reported code is ignored until it has been out of view for quietMs, or a different code is reported. */
class RepeatFilter(private val quietMs: Long = 1500) { // ponytail: tuning knob
    // Reported codes still in view -> when last seen. Usually just the last reported one; with several codes
    // in view together it also keeps the earlier ones, so they don't ping-pong every frame.
    private val held = HashMap<String, Long>()

    /** Codes visible in one analyzed frame -> the code to report now, or null. */
    fun onFrame(codes: List<String>, nowMs: Long): String? {
        held.values.removeAll { nowMs - it > quietMs }
        for (c in codes) if (c in held) held[c] = nowMs
        val new = codes.firstOrNull { it !in held } ?: return null
        held.keys.retainAll(codes.toSet()) // codes out of view when another is reported are re-armed
        held[new] = nowMs
        return new
    }
}
