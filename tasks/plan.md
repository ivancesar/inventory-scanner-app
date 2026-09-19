# Plan: Inventory Scanner

Implements [SPEC.md](../SPEC.md). The task list is in [todo.md](todo.md).

## Approach

Build the logic that can be tested first (sheet script, local store, HTTP client), then the UI screens in the order a user moves through them, then release packaging. Every task leaves the app in a buildable, installable state.

## Components and dependencies

```
apps-script/Code.gs ──(HTTP contract)──> SheetClient.kt ─┐
Store.kt (prefs + scans.jsonl) ──────────────────────────┼─> MainActivity.kt (Settings / Area / Scan + drawer)
Scanner.kt (CameraX + ML Kit, RepeatFilter) ─────────────┘
strings.xml (en) + values-hr/strings.xml ────────────────> all screens
```

- `Code.gs`, `Store`, and `SheetClient` don't depend on each other and can be built in any order. `SheetClient` must implement the contract in the spec exactly.
- The UI depends on all three. The Settings screen's QR option reuses `Scanner`, so it comes after the Scan screen.

## Order

| # | Slice | Why here |
|---|---|---|
| 1 | Scaffold: Gradle project, wrapper, empty Compose activity, EN/HR string files | Everything else needs a project that builds |
| 2 | Apps Script + owner setup guide in README | You can deploy a test sheet while I build the app, which unblocks the real end-to-end test |
| 3 | `Store` + row building (TDD) | Core data-safety logic, pure JVM |
| 4 | `SheetClient` (TDD on response parsing) | Pure JVM apart from the socket |
| **CP1** | **Checkpoint:** all unit tests pass; `Code.gs` validation passes its node test | |
| 5 | Settings screen: URL paste, name, language, Test connection, first-run gate | First screen users see |
| 6 | Area screen + resume after the app is killed | |
| 7 | Scan screen: camera, `RepeatFilter`, beep and vibration, duplicate prompt | Biggest slice, and the riskiest (camera) |
| 8 | Scans drawer: list, long-press delete, Abandon | |
| 9 | Finish: confirm, upload, error/retry, clear | Completes the core loop |
| **CP2** | **Checkpoint:** full flow on a real phone against a real test sheet (**needs you**) | |
| 10 | QR option in Settings (reuses Scanner) | |
| 11 | Release signing, README user install steps, final checklist | |
| **CP3** | **Checkpoint:** all spec Success Criteria checked off | |

## Environment

- JDK: the Android Studio JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`). The system `java` is version 1.8, which is too old.
- Versions are copied from `../imageBrowse`, which already builds here: AGP 9.3.1 (built-in Kotlin), Gradle 9.7.0, Compose BOM 2026.08.00, compileSdk/targetSdk 36. The only new libraries are CameraX (`camera-view`, `camera-mlkit-vision`) and ML Kit `barcode-scanning`.
- `.gitignore` gets `!gradle/wrapper/gradle-wrapper.jar` plus the standard Android ignores (`build/`, `local.properties`, `keystore.properties`, `*.jks`).

## Risks

| Risk | Mitigation |
|---|---|
| Apps Script's 302 redirect after a POST isn't followed, or the response body gets lost | The client follows redirects manually if needed. `batchId` dedup makes retries safe either way. This is verified at CP2 with the real script. |
| `setValues` turns codes into numbers or formulas | The script prefixes Code, Area, and User with `'` so they're stored as plain text. Checked with a node unit test and in the real sheet. |
| The camera can't be tested properly on an emulator | `RepeatFilter` logic is unit-tested. Camera behaviour is checked at CP2 on your phone. |
| Croatian translations are machine-quality | I'll write them, and you proofread `values-hr/strings.xml` before release. |
| The release keystore gets lost, so users can't install updates without uninstalling (which loses unsent scans) | README tells you to back up the keystore. It's never committed. |

## What I need from you

- **By CP2:** make a test Google Sheet, deploy `Code.gs` using the README steps, and send me the `/exec` URL. It stays out of git. You'll also need an Android phone for the scanning check.
- **Before release:** proofread the Croatian strings.
