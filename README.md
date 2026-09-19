# Inventory Scanner

Android app for scanning barcodes area by area and appending them to a shared Google Sheet as `Timestamp | Code | Area | User`.
See [SPEC.md](SPEC.md).

## Sheet owner: one-time setup (about 5 minutes)

1. Create a Google Sheet (or open the one you want to use). Scans go to a tab called **Scans**, which is created automatically.
2. In the sheet, open **Extensions → Apps Script**.
3. Delete everything in the editor, paste in the whole of [`apps-script/Code.gs`](apps-script/Code.gs), and click **Save** (💾).
4. Click **Deploy → New deployment**. Click the gear next to "Select type" and choose **Web app**.
   - **Execute as:** Me
   - **Who has access:** Anyone
5. Click **Deploy** and approve the permissions. Google will warn that the app isn't verified. Click **Advanced → Go to … (unsafe)**. This is expected, because the script is your own.
6. Copy the **Web app URL**. It ends in `/exec`.
7. Check it: open the URL in a browser. You should see `{"ok":true,"sheet":"<your sheet name>"}`.
8. Make a QR code for your team. In desktop Chrome, open the URL, click the address bar, choose **Share → Create QR code**, and download it. Print it or send it to your team.

> **Treat the URL and QR code like a password.** Anyone who has them can add rows to the sheet (but can't read it).
> To revoke access, go to **Deploy → Manage deployments**, archive the deployment, and create a new one. The new deployment has a new URL.

**Updating the script later:** paste in the new code, then go to **Deploy → Manage deployments → ✏️ Edit → Version: New version → Deploy**. This keeps the same URL, so phones don't need to be set up again.

## Development

Requires the Android SDK. The JDK is taken from Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest lintDebug assembleDebug
node apps-script/Code.test.js
```
