package com.inventoryscanner

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Shown on first run (settings.configured == false) and from the gear button later. Saving persists and calls onDone. */
@Composable
fun SettingsScreen(settings: Settings, onDone: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    // Saveable: a language switch recreates the Activity.
    var url by rememberSaveable { mutableStateOf(settings.url) }
    var name by rememberSaveable { mutableStateOf(settings.user) }
    var testedUrl by rememberSaveable { mutableStateOf("") } // last url that passed the test
    var sheet by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val connected = testedUrl.isNotEmpty() && testedUrl == url.trim()

    fun test() {
        val u = url.trim()
        error = ""
        if (!SheetClient.isValidUrl(u)) {
            error = res.getString(R.string.error_bad_url)
            return
        }
        testing = true
        scope.launch {
            withContext(Dispatchers.IO) { SheetClient.test(u) }
                .onSuccess { sheet = it; testedUrl = u }
                .onFailure {
                    error = if (it is IOException) res.getString(R.string.error_network)
                    else res.getString(R.string.error_connection, it.message)
                }
            testing = false
        }
    }

    if (scanning) {
        BackHandler { scanning = false }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarcodeCamera(paused = false, modifier = Modifier.weight(1f).fillMaxWidth(), onCode = { code ->
                if (!scanning) return@BarcodeCamera // another frame before recomposition removed the camera
                if (SheetClient.isValidUrl(code.trim())) {
                    url = code.trim()
                    scanning = false
                    test()
                } else {
                    Toast.makeText(context, R.string.error_bad_url, Toast.LENGTH_SHORT).show()
                }
            })
            TextButton({ scanning = false }, BIG.padding(16.dp)) { Text(stringResource(R.string.cancel)) }
        }
        return
    }

    BackHandler(enabled = settings.configured, onBack = onDone)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = "" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_url)) },
            placeholder = { Text(stringResource(R.string.settings_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        OutlinedButton({ scanning = true }, BIG) { Text(stringResource(R.string.settings_scan_qr)) }
        OutlinedButton(::test, BIG, enabled = !testing) { Text(stringResource(R.string.settings_test)) }
        when {
            testing -> Text(stringResource(R.string.settings_testing))
            connected -> Text(stringResource(R.string.settings_connected, sheet), color = MaterialTheme.colorScheme.primary)
            error.isNotEmpty() -> Text(error, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )

        Text(stringResource(R.string.settings_language))
        // The effective locale: the app language if one was chosen, else the system's.
        val current = if (LocalLocale.current.platformLocale.language == "hr") "hr" else "en"
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("en" to R.string.lang_en, "hr" to R.string.lang_hr).forEachIndexed { i, (tag, label) ->
                SegmentedButton(
                    selected = current == tag,
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(label)) }
            }
        }

        Button(
            onClick = { settings.url = url.trim(); settings.user = name.trim(); onDone() },
            modifier = BIG,
            enabled = connected && !testing && name.isNotBlank(),
        ) { Text(stringResource(R.string.settings_save)) }
        if (settings.configured) TextButton(onDone, BIG) { Text(stringResource(R.string.cancel)) }
    }
}

// Large touch targets for non-technical users.
private val BIG = Modifier.fillMaxWidth().heightIn(min = 56.dp)
