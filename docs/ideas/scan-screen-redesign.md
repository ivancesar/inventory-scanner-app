# Scan screen redesign

## Problem statement
How might we make scanning feel like using the phone's camera app: point at a barcode, get confirmation, and keep going? Area, list, settings and finishing should each be one obvious tap away, for people who aren't technical.

## Recommended direction
**Camera-first scan screen, styled after the Xiaomi camera app** (always dark, black bars):
- Top row: **torch** on the left, the **area name** in the centre (a mostly opaque dark pill with a pencil icon), **settings** on the right.
- The camera view has a thin **aiming frame**.
- After a new scan, a **green "✓ code" pill** flashes above the bottom bar.
- Bottom row: the **clipboard list button** on the left with a count badge (white badge, black number), the centre **empty** (so nothing looks like a shutter), and a **Finish** pill on the right with an upload icon.

**Duplicates:** an amber pill shows the code, the phone gives a distinct warning vibration, and scanning pauses while the **"Already scanned. Add it again?" (Add / Skip)** popup is shown. Add saves the scan and turns the pill green; Skip saves nothing. An automatic scan can't slip through.

**Area handling:**
- Tapping the area pill opens a **Rename area** modal (text field, Cancel/Save). The scans are kept; the new name applies when uploading.
- The **New area** screen stays. It appears whenever no area is open (first use, or after Finish or Abandon), and it follows the Light/Dark theme.

**Settings:**
- **Language** becomes a dropdown, so more languages can be added later.
- A new **Theme** dropdown offers System (the default), Light and Dark. It applies to Settings, New area, the scans list and dialogs. The camera screen is always dark.

## Key assumptions to validate
- [ ] **Users find Finish at bottom right without any hint.** Check on the phone test: hand the phone to someone and ask them to "send the scans".
- [ ] **The green pill plus a beep is enough feedback for a new scan.** Check on the warehouse floor.
- [ ] **Renaming an area after scanning is safe.** It is: the name is only attached to the rows at upload time, and the batch ID is unchanged.

## MVP scope
**In:**
- The new scan screen layout.
- The rename modal.
- The scan-result pill.
- The torch.
- The Language and Theme dropdowns, with the theme saved like the other settings.
- The New area screen with theming.
- New strings in English and Croatian.

**Out:** everything in the next section.

## Not doing (and why)
- **Zoom buttons (0.6× / 1× / 2×)** — the barcode reader handles normal distances; add them only if the phone test shows problems with far-away labels.
- **Front camera switch** — nobody scans shelves with a selfie camera.
- **Mode carousel** — this app has a single mode.
- **An icon library** — the 5 or so icons (torch, settings, clipboard, upload, pencil) will be small vector files in the project.
- **Restyling Settings like a camera app** — it stays a normal form, just themed.
