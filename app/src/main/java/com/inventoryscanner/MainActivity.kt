package com.inventoryscanner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = Settings(getSharedPreferences("settings", MODE_PRIVATE))
        // AppCompat doesn't persist night mode (unlike app locales); apply it before the first frame.
        AppCompatDelegate.setDefaultNightMode(settings.nightMode)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(File(filesDir, "area.jsonl"))
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var editingSettings by rememberSaveable { mutableStateOf(!settings.configured) }
                    if (editingSettings) SettingsScreen(settings, onDone = { editingSettings = false })
                    else ScanScreen(store, settings, onSettings = { editingSettings = true })
                }
            }
        }
    }
}
