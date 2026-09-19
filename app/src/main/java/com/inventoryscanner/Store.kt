package com.inventoryscanner

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Scan(val time: String, val code: String)
data class Area(val name: String, val batchId: String, val scans: List<Scan>)

private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun now(): String = LocalDateTime.now().format(TIME)

/** The single open area and its scans, persisted in one file so it survives process death. */
class Store(private val file: File) {
    // Format: header JSON object, then one "\n[time,code]" per scan. The leading newline
    // isolates a partial line left by a crash mid-append, so load() just skips it.

    fun load(): Area? {
        if (!file.exists()) return null
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = JSONObject(lines.firstOrNull() ?: return null)
        val scans = lines.drop(1).mapNotNull {
            try {
                JSONArray(it).let { a -> Scan(a.getString(0), a.getString(1)) }
            } catch (_: JSONException) {
                null
            }
        }
        return Area(header.getString("area"), header.getString("batchId"), scans)
    }

    fun start(name: String): Area {
        // Never silently overwrite an open area's scans.
        check(load() == null) { "An area is already open" }
        return Area(name.trim(), UUID.randomUUID().toString(), emptyList()).also(::rewrite)
    }

    fun add(code: String, time: String = now()): Scan {
        check(file.exists()) { "No open area" }
        write(file, "\n" + JSONArray(listOf(time, code)), append = true)
        return Scan(time, code)
    }

    fun delete(index: Int) {
        val area = checkNotNull(load()) { "No open area" }
        rewrite(area.copy(scans = area.scans.toMutableList().apply { removeAt(index) }))
    }

    fun rename(name: String): Area {
        require(name.isNotBlank()) { "Blank area name" }
        val area = checkNotNull(load()) { "No open area" }
        return area.copy(name = name.trim()).also(::rewrite)
    }

    fun clear() {
        Files.deleteIfExists(file.toPath())
    }

    // Crash-safe: a reader sees either the old file or the new one, never a mix.
    private fun rewrite(area: Area) {
        val header = JSONObject().put("area", area.name).put("batchId", area.batchId)
        val text = header.toString() + area.scans.joinToString("") { "\n" + JSONArray(listOf(it.time, it.code)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        write(tmp, text, append = false)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun write(f: File, text: String, append: Boolean) =
        FileOutputStream(f, append).use { it.write(text.toByteArray()); it.fd.sync() }
}

/** Sheet rows in contract order: Timestamp, Code, Area, User. */
fun Area.rows(user: String): List<List<String>> =
    scans.map { listOf(it.time, it.code, name.trim(), user.trim()) }

/** User settings in SharedPreferences. */
class Settings(private val prefs: SharedPreferences) {
    var url: String
        get() = prefs.getString("url", "") ?: ""
        set(v) = prefs.edit { putString("url", v) }
    var user: String
        get() = prefs.getString("user", "") ?: ""
        set(v) = prefs.edit { putString("user", v) }
    var nightMode: Int
        get() = prefs.getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = prefs.edit { putInt("nightMode", v) }
    var pillStyle: PillStyle
        get() = PillStyle.entries.firstOrNull { it.name == prefs.getString("pillStyle", null) } ?: PillStyle.BlueAmber
        set(v) = prefs.edit { putString("pillStyle", v.name) }
    val configured: Boolean get() = url.isNotBlank() && user.isNotBlank()
}
