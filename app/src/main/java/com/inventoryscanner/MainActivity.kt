package com.inventoryscanner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(getSharedPreferences("settings", MODE_PRIVATE))
        val store = Store(File(filesDir, "area.jsonl"))
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var editingSettings by rememberSaveable { mutableStateOf(!settings.configured) }
                    // Read from disk at startup, so a killed app resumes its open area.
                    var areaOpen by rememberSaveable { mutableStateOf(store.load() != null) }
                    when {
                        editingSettings -> SettingsScreen(settings, onDone = { editingSettings = false })
                        !areaOpen -> AreaScreen(
                            onStart = { store.start(it); areaOpen = true },
                            onSettings = { editingSettings = true },
                        )
                        else -> ScanScreen(
                            store, settings,
                            onClosed = { areaOpen = false },
                            onSettings = { editingSettings = true },
                        )
                    }
                }
            }
        }
    }
}
