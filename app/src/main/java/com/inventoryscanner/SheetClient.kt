package com.inventoryscanner

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Talks to the owner's Apps Script web app (see "Sheet Contract" in SPEC.md). Never log the URL. */
object SheetClient {
    /** True for https://script.google.com/... URLs only (never send data elsewhere or over http). */
    fun isValidUrl(url: String): Boolean =
        runCatching { URI(url).let { it.scheme == "https" && it.host == "script.google.com" } }.getOrDefault(false)

    /** Connection test (GET). Success = spreadsheet name. Blocking — call from Dispatchers.IO. */
    fun test(url: String): Result<String> = request(url, null).mapCatching {
        it.optString("sheet").ifEmpty { throw Exception("Unexpected response") }
    }

    /** Append rows (POST). Success = rows added (0 when the server says duplicate batch). Blocking. */
    fun upload(url: String, batchId: String, rows: List<List<String>>): Result<Int> {
        val body = JSONObject().put("batchId", batchId).put("rows", JSONArray(rows.map { JSONArray(it) }))
        return request(url, body.toString()).mapCatching {
            it.optInt("added", -1).takeIf { n -> n >= 0 } ?: throw Exception("Unexpected response")
        }
    }

    /** Failure is an IOException (network) or an Exception with a short readable message. */
    private fun request(url: String, body: String?): Result<JSONObject> {
        if (!isValidUrl(url)) return Result.failure(Exception("Invalid link"))
        return try {
            // Same URI object that was validated, so no URL/URI parser differential.
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                // Apps Script answers POST with 302 → script.googleusercontent.com. HttpURLConnection
                // follows it as a GET without the body and refuses https→http. doPost has already run;
                // batchId dedup on the server makes a retry safe.
                conn.instanceFollowRedirects = true
                if (body != null) {
                    conn.doOutput = true // POST. No streaming mode: the JDK can't redirect a streamed body.
                    conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val status = conn.responseCode
                val stream = if (status < 400) conn.inputStream else conn.errorStream
                parse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected response", e))
        }
    }

    internal fun parse(status: Int, body: String): Result<JSONObject> {
        if (status !in 200..299) return Result.failure(Exception("HTTP $status"))
        // HTML here usually means a Google sign-in page: deployment access isn't "Anyone".
        val json = try { JSONObject(body) } catch (_: JSONException) {
            return Result.failure(Exception("Unexpected response"))
        }
        if (json.optBoolean("ok")) return Result.success(json)
        return Result.failure(Exception(json.optString("error").ifBlank { "Unexpected response" }))
    }
}
