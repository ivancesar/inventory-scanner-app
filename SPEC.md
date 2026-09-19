# Spec: Inventory Scanner (Android barcode scanner → Google Sheet)

## Objective

A simple Android app for non-technical users. They scan barcodes one "area" at a time (a shelf, room, or pallet), and when they finish an area, the scans are appended to one shared Google Sheet.

**Users**
- **Sheet owner** (one person, somewhat technical). Sets up the sheet once and hands out a link or QR code.
- **Scanners** (many people, not technical). Install the APK, scan the QR code or paste the link, type their name, then scan.

**Flow**
1. User opens the app.
2. **First run only:** Settings screen. User connects the sheet (scans the setup QR code or pastes the link) and enters their name. The app tests the connection and shows the sheet's name before allowing Save.
3. **Area screen:** user types a name for the scanning area.
4. **Scan screen:** the camera scans continuously. Each read is saved on the phone right away and shown in a list with a count.
5. **Finish area:** after a confirmation, all of the area's scans are uploaded and appended to the sheet as `Timestamp | Code | Area | User`. When the upload succeeds, local data for the area is cleared and the app returns to step 3.

**User stories / acceptance criteria**
- As a scanner, I set up once and am never asked again. Settings stay reachable from a gear icon for later changes.
- As a scanner, when I hold a barcode up to the camera it's recorded within about 1 second, with a beep and a vibration.
- As a scanner, when I scan a code that's already in this area, I'm asked "Already scanned in this area. Add again?" and can choose Add or Skip.
- As a scanner, holding one barcode in view doesn't record it (or ask about it) over and over.
- As a scanner, a button on the Scan screen opens a drawer listing every scan in this area (newest first, with time). Long-pressing a scan asks "Delete this scan?", and it's removed only if I confirm.
- As a scanner, the drawer has an **Abandon area** button. It asks for confirmation ("Abandon area and delete N scans? This can't be undone.") and only then deletes the area's local scans without uploading and returns to the Area screen.
- As a scanner, I can switch the app between **English** and **Croatian** in Settings. The change applies immediately and is remembered.
- As a scanner, if the app is killed or the phone restarts mid-area, reopening the app takes me back to the same area with all scans intact.
- As a scanner, if I have no signal when I tap Finish, nothing is lost. I see an error, the scans stay on the phone, and I can retry later.
- As the sheet owner, rows from every user land in one tab, in order, without overwriting each other, even when two people finish areas at the same moment.
- As the sheet owner, barcodes keep their leading zeros (`0012345` stays `0012345`) and are never interpreted as formulas.

## Assumptions

1. **Connection method: Apps Script web app, not a public edit link.** Google doesn't allow apps to write to a sheet anonymously, even one set to "anyone with the link can edit". So the owner pastes our script into the sheet once (Extensions → Apps Script → Deploy as web app, "Anyone" access), and the resulting `/exec` URL is the "link" users connect with. **Anyone who has this URL can append rows, so it should be treated like a password.** The owner can revoke it by redeploying.
2. There's one shared sheet. Rows are written to a tab named **`Scans`**, which the script creates, with a header row, if it doesn't exist.
3. Timestamp = the moment of the scan (not the upload), in the phone's local time, formatted `yyyy-MM-dd HH:mm:ss`.
4. Only one area is open at a time. A new area can't start until the current one is finished or abandoned.
5. All barcode formats that ML Kit supports are accepted, including QR codes.
6. The app is distributed as a sideloaded, signed release APK. There is no Play Store listing.
7. The UI is portrait-only. Min Android 8.0 (API 26).
8. The language defaults to Croatian if the phone is set to Croatian, and English otherwise. The choice in Settings overrides this. It's implemented with `AppCompatDelegate.setApplicationLocales` (per-app language), which is why `androidx.appcompat` is included.

## Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose, single Activity | Standard modern Android |
| Navigation | One `screen` state variable in the Activity; scan list is a Compose `ModalBottomSheet` drawer | 3 screens don't need a nav library |
| Language | `strings.xml` (default EN) + `values-hr/strings.xml`; `AppCompatDelegate.setApplicationLocales` | Built-in per-app language, persisted by AppCompat |
| Camera + decoding | CameraX `LifecycleCameraController` + ML Kit `barcode-scanning` (bundled model) via `camera-mlkit-vision` `MlKitAnalyzer` | Continuous scanning, works offline, no Play Services download |
| Local storage | `SharedPreferences` for settings and the current area; append-only JSON-lines file in `filesDir` for pending scans | Survives process death with no database |
| HTTP | `HttpURLConnection` + `org.json` (both in the platform) | No networking dependency needed for one POST |
| Sheet side | Google Apps Script (`doGet` / `doPost`) | No OAuth, no GCP project |
| Build | Gradle wrapper, AGP + Kotlin at current stable; compileSdk/targetSdk = latest stable | |

Exact dependency versions are pinned in `gradle/libs.versions.toml` when the project is scaffolded.

## Sheet Contract (app ↔ Apps Script)

```
GET  <url>                       → {"ok":true,"sheet":"<spreadsheet name>"}   // connection test
POST <url>  Content-Type: text/plain
  {"batchId":"<uuid>","rows":[["2026-09-19 14:03:22","0012345678905","Aisle 3","Ana"], ...]}
                                 → {"ok":true,"added":N}
                                 → {"ok":true,"added":0,"duplicate":true}    // batchId already written
                                 → {"ok":false,"error":"<message>"}
```

- `batchId` is generated when the area is created and reused on every retry. The script records batch IDs it has written, so a retry after a lost response doesn't append the same rows twice.
- The script takes `LockService.getScriptLock()` before appending, so simultaneous uploads don't interleave or overwrite.
- The script validates input: each row has exactly 4 strings, each at most 500 characters, and a batch has at most 5,000 rows. Anything else is rejected with `ok:false`.
- Code, Area, and User are written as literal text. Leading zeros are kept, and a leading `=`, `+`, `-`, or `@` is never evaluated.
- Apps Script answers a POST with a 302 redirect to a GET for the response body. The client must follow it. `HttpURLConnection` does this for https→https.

## Commands

```bash
# from repo root
./gradlew assembleDebug                      # build debug APK
./gradlew testDebugUnitTest                  # JVM unit tests
./gradlew lintDebug                          # Android lint
./gradlew assembleRelease                    # signed APK for distribution (keystore via local keystore.properties)
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Apps Script smoke test (after deploying):
```bash
curl -sL "$SCRIPT_URL"
curl -sL -H 'Content-Type: text/plain' -d '{"batchId":"test-1","rows":[["2026-09-19 10:00:00","0012","Test","curl"]]}' "$SCRIPT_URL"
```

## Project Structure

```
inventory-scanner-app/
  SPEC.md
  README.md                 → owner setup guide (deploy script, make QR) + user install steps
  apps-script/Code.gs       → the script the owner pastes into the sheet
  app/src/main/java/com/inventoryscanner/
    MainActivity.kt         → Compose screens: Settings, Area, Scan (+ scans drawer); screen state
    Scanner.kt              → CameraX + ML Kit preview/analyzer composable, repeat-suppression
    Store.kt                → settings, current area, pending scans (prefs + JSONL file)
    SheetClient.kt          → GET test + POST upload, response parsing
  app/src/test/java/com/inventoryscanner/  → JVM unit tests
  app/src/main/res/values/strings.xml      → English
  app/src/main/res/values-hr/strings.xml   → Croatian
  gradle/libs.versions.toml
```

Start with as few files as possible and split only when a file gets hard to follow.

## Code Style

Kotlin official style (`kotlin.code.style=official`). Plain functions and data classes. No DI framework, no repository or use-case layers, no interfaces with only one implementation. Keep logic that can be tested out of Composables so it can run on the JVM.

```kotlin
data class Scan(val time: String, val code: String)

class Store(private val prefs: SharedPreferences, private val scansFile: File) {
    fun addScan(scan: Scan) =
        scansFile.appendText(JSONArray(listOf(scan.time, scan.code)).toString() + "\n")

    fun scans(): List<Scan> =
        if (!scansFile.exists()) emptyList()
        else scansFile.readLines().filter { it.isNotBlank() }.map {
            val a = JSONArray(it); Scan(a.getString(0), a.getString(1))
        }
}
```

- Names: `PascalCase` types, `camelCase` functions/vals, no Hungarian or `I`-prefixed names.
- UI strings go in `strings.xml`, never hardcoded. Every key must exist in both `values/` and `values-hr/`. Lint `MissingTranslation` is treated as an error.
- Deliberate shortcuts get a `ponytail:` comment that names the limitation.

## Testing Strategy

- **JVM unit tests (JUnit 4)** in `app/src/test` cover the logic that can break quietly:
  - Row building: timestamp format; Area and User trimmed; column order `Timestamp, Code, Area, User`.
  - `Store`: scans round-trip through the file, survive a new `Store` instance (simulated restart), and are cleared after a successful upload. Deleting one scan removes only that scan. Abandoning clears the scans and the current area.
  - Repeat suppression: the same code held in view is reported once; a new code or a gap longer than the cooldown re-arms it.
  - `SheetClient` response parsing: `ok`, `duplicate`, `ok:false`, non-JSON/HTML error pages, and HTTP errors all map to success or a readable failure.
- **Apps Script:** the curl smoke test above, plus a manual check in the sheet for leading zeros, `=1+1` stored as text, and a duplicate `batchId` ignored.
- **Manual device checklist** (on a real phone, before each APK is handed out): first-run setup through QR, first-run setup through paste, scan 10 codes, duplicate prompt, open the drawer and long-press → delete (confirm and cancel), abandon an area (confirm and cancel), switch EN↔HR in Settings, kill the app mid-area and reopen, finish while in airplane mode (error, data kept), finish online (rows appear, local data cleared).
- No instrumented or Espresso tests unless requested. The camera path is verified by hand.

## Boundaries

- **Always:** persist every scan to disk before showing it as saved; keep local data until the server confirms `ok:true`; run `testDebugUnitTest` and `lintDebug` before calling a task done; keep the script URL out of logs.
- **Ask first:** adding any dependency beyond those listed in Tech Stack; changing the sheet contract or column order; adding screens or features not in this spec; changing min SDK.
- **Never:** commit the release keystore, `keystore.properties`, or a real script URL; request permissions other than `CAMERA` and `INTERNET`; send data anywhere except the configured script URL; add analytics or crash reporting.

## Success Criteria

1. A fresh install on a real Android 8+ phone goes from first launch to a successful sheet upload using only the QR or paste setup, with no Google sign-in.
2. The Settings screen appears on first launch only, and Save is blocked until the connection test succeeds and a name is entered.
3. A barcode held in front of the camera is recorded within about 1 s, with a beep and vibration, and is recorded once no matter how long it stays in view.
4. Re-scanning a code that's already in the area prompts Add/Skip, and the answer is respected.
5. Force-stopping the app mid-area and reopening it restores the area name and every scan.
6. Finish while offline shows an error and keeps every scan. Finish online appends exactly N rows `Timestamp | Code | Area | User` to the `Scans` tab, clears local data, and returns to the Area screen.
7. Retrying an upload whose response was lost doesn't create duplicate rows.
8. Two phones finishing at the same time produce all rows from both, with none lost or overwritten.
9. `0012345` and `=1+1` show in the sheet exactly as scanned.
11. Deleting a scan from the drawer takes two steps (long-press, then confirm). A deleted scan is never uploaded.
12. Abandon takes two steps (button, then confirm). After it, no scans from that area remain on the phone or are uploaded.
13. Every screen and dialog is fully translated in both English and Croatian, and switching in Settings changes the UI without reinstalling.
10. `./gradlew testDebugUnitTest lintDebug assembleRelease` passes.

## Decisions (resolved questions)

1. **Mistaken scans:** a drawer lists the area's scans. Long-press → confirm to delete one.
2. **Abandoning an area:** an Abandon area button in the drawer, with a confirmation dialog.
3. **App name / package:** "Inventory Scanner", `com.inventoryscanner` (applicationId and namespace).
4. **Language:** English and Croatian, switchable in Settings.
