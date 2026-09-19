# Tasks: Inventory Scanner

Standard check for every task: `./gradlew testDebugUnitTest lintDebug assembleDebug` passes, then commit to `main`.

- [x] **1. Scaffold project**
  - Acceptance: `com.inventoryscanner`, minSdk 26, portrait, empty Compose screen reading "Inventory Scanner" from `strings.xml` (EN + HR); wrapper jar committed; CAMERA + INTERNET permissions only.
  - Verify: `assembleDebug` succeeds; APK installs and launches on the emulator.
  - Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/*`, `gradlew*`, `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `values/strings.xml`, `values-hr/strings.xml`, `.gitignore`

- [x] **2. Apps Script + owner setup guide**
  - Acceptance: `doGet` returns the sheet name; `doPost` validates input, takes the script lock, dedups on `batchId`, creates the `Scans` tab and header if missing, and stores Code, Area, and User as plain text.
  - Verify: `node apps-script/Code.test.js` (validation and text-escaping); after you deploy, the spec's curl smoke test.
  - Files: `apps-script/Code.gs`, `apps-script/Code.test.js`, `README.md`

- [x] **3. Store + row building (TDD)**
  - Acceptance: settings (URL, name) in prefs; the open area (name, batchId) and its scans in one JSONL file; delete one scan; clear on finish or abandon; data survives a new `Store` instance; `rows()` returns `[time, code, area, user]` with trimmed values.
  - Verify: `StoreTest` passes.
  - Files: `Store.kt`, `StoreTest.kt`

- [x] **4. SheetClient (TDD)**
  - Acceptance: `test(url)` returns the sheet name or an error; `upload(url, batchId, rows)` returns success for `ok` and `duplicate`, and a readable failure for `ok:false`, HTML, non-2xx responses, or an IOException. Runs off the main thread, with timeouts.
  - Verify: `SheetClientTest` (parsing) passes.
  - Files: `SheetClient.kt`, `SheetClientTest.kt`

- [x] **CP1** — all unit tests and the node test pass. Commit.

- [x] **5. Settings screen**
  - Acceptance: fields for URL and name, an EN/HR switch that applies immediately via `setApplicationLocales`, and a "Test connection" button that shows the sheet name. Save is enabled only after a successful test with a non-blank name. Shown automatically on first run; reachable later from a gear icon.
  - Verify: manual on the emulator (first run, relaunch skips Settings, language switch).
  - Files: `MainActivity.kt`, `strings.xml` ×2, `app/build.gradle.kts` (appcompat), `AndroidManifest.xml` (locale config)

- [x] **6. Area screen + resume**
  - Acceptance: requires a non-blank area name; starting an area creates its batchId; relaunching with an open area goes straight to the Scan screen.
  - Verify: manual (force-stop mid-area, then relaunch).
  - Files: `MainActivity.kt`, `strings.xml` ×2

- [x] **7. Scan screen**
  - Acceptance: camera permission request, with an explanation if it's denied; continuous scanning; `RepeatFilter` ignores the last handled code until it's been out of view for 1.5 s (`ponytail:` knob); each scan is saved to disk before feedback (beep and vibration); a code already in the area pauses scanning and asks Add/Skip; the count is shown.
  - Verify: `RepeatFilterTest`; manual on the emulator's virtual camera or a phone.
  - Files: `Scanner.kt`, `RepeatFilterTest.kt`, `MainActivity.kt`, `app/build.gradle.kts`, `libs.versions.toml`, `strings.xml` ×2

- [x] **8. Scans drawer**
  - Acceptance: a button opens a bottom sheet with scans (newest first, with time); long-press → confirm → delete; "Abandon area" → confirm with count → clears everything and returns to the Area screen.
  - Verify: manual; the Store delete/clear paths are already unit-tested.
  - Files: `MainActivity.kt`, `strings.xml` ×2

- [x] **9. Finish + upload**
  - Acceptance: Finish → confirm with count → progress → on success clear and return to the Area screen; on failure show the error, keep all data, and allow retry. Finish is disabled when there are 0 scans.
  - Verify: manual in airplane mode (error, data kept) and online.
  - Files: `MainActivity.kt`, `strings.xml` ×2

- [ ] **CP2** *(emulator pass done: first-run gate, EN↔HR switch persisting after force-stop on API 30, bad-link rejection, resume after kill, drawer delete, finish failure keeps data, abandon, new area. Real sheet: curl GET/POST/duplicate/validation OK; app Test connection + Finish upload OK from emulator. Owner confirmed in sheet: leading zeros kept, =1+1 stored as text, timestamps parsed as dates (criteria 7, 9). Still needs a phone for real barcodes, beep/haptic, QR.)* — **you:** deploy the test sheet script and send me the URL. Full flow on a real phone. Check Success Criteria 1–9 and 11–13.

- [x] **10. QR option in Settings**
  - Acceptance: a "Scan QR" button opens the camera; the first QR code containing an `https://script.google.com/` URL fills the URL field and runs the connection test.
  - Verify: manual with a QR code generated from the test URL.
  - Files: `MainActivity.kt`, `strings.xml` ×2

- [x] **11. Release build + docs**
  - Acceptance: `assembleRelease` is signed from `keystore.properties` (ignored by git) and falls back to an unsigned build if that file is missing; README covers creating the keystore, backing it up, building, and installing the APK; the QR generation step is documented.
  - Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease`; `apksigner verify`.
  - Files: `app/build.gradle.kts`, `README.md`, `.gitignore`

- [ ] **CP3** — all spec Success Criteria checked. **You:** proofread `values-hr/strings.xml`.

## Scan screen redesign ([docs/ideas/scan-screen-redesign.md](../docs/ideas/scan-screen-redesign.md))

Coordinator prep (done on `main` before the agents): 5 Tabler vector drawables, new EN/HR strings, `Store.rename`, `Settings.nightMode`, theme + edge-to-edge in `MainActivity`, `StoreTest.renameKeepsBatchAndScans`.

- [ ] **12. Scan screen redesign** (agent A; owns `ScanScreens.kt`, `Scanner.kt`)
  - Acceptance: always-dark camera screen; top row torch / area pill (tap → rename dialog) / settings; aiming frame; green pill for a new scan, amber pill + warning haptic + Add/Skip dialog for a duplicate; bottom row clipboard button with white/black count badge, empty centre, Finish on the right; `BarcodeCamera(..., torch)`; light status-bar icons on the scan screen.
  - Verify: `./gradlew testDebugUnitTest lintDebug assembleDebug`; emulator check.
- [ ] **13. Settings dropdowns** (agent B; owns `SettingsScreen.kt`)
  - Acceptance: Language and Theme (System/Light/Dark) as `ExposedDropdownMenuBox` dropdowns built from lists; both apply immediately; the theme is saved in `Settings.nightMode`.
  - Verify: same; emulator check that the theme flips and survives a force-stop.
- [ ] **14. Merge, emulator check, SPEC update** (coordinator)

## Area popup + unified controls (branch `feature/area-popup-unified-controls`)

- [x] **15. New area popup** — replaces the New area page; same popup design as Rename; can't be dismissed without a name, Settings is the other button.
- [x] **16. Unified scan-screen controls** — one style (48dp, #2A2A2A, white 22dp icons; circles or pills); torch hidden without a flash.
- [x] **17. Code-review fixes** — Theme dropdown state; nav-bar scrim on API < 29; live-state guard in `handle()`; distinct duplicate tone; rename focus inside the dialog; dropdown fallback; `Store.rename` rejects blank names; result pill is a live region. The upload-batch fix was dropped by decision (see SPEC assumption 9).
