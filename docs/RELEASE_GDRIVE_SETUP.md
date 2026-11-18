# GitHub Actions -> Personal Google Drive Release Setup

The workflow [release-to-drive.yml](../.github/workflows/release-to-drive.yml) runs on every push to `main` (and manual `workflow_dispatch`), executes `./gradlew testDebugUnitTest assembleRelease --no-daemon`, and uploads a versioned APK (`Omni-v<versionName>-b<run_number>-<short_sha>.apk`) to your Google Drive folder.

Add these **6 Repository Secrets** in GitHub (**Settings -> Secrets and variables -> Actions -> New repository secret**):

| Secret Name | Value / How to Generate (Windows PowerShell) |
| :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | Run in repo root:<br>`[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\release.jks")) \| Set-Clipboard` |
| `FINANCEOS_STORE_PASSWORD` | Your keystore password (default in `app/build.gradle.kts`: `android`) |
| `FINANCEOS_KEY_ALIAS` | Your key alias (default in `app/build.gradle.kts`: `personal_finance_os`) |
| `FINANCEOS_KEY_PASSWORD` | Your key password (default in `app/build.gradle.kts`: `android`) |
| `GDRIVE_FOLDER_ID` | Folder ID from your Google Drive URL: `https://drive.google.com/drive/folders/<GDRIVE_FOLDER_ID>` |
| `RCLONE_CONF` | See steps below |

## Generating `RCLONE_CONF` (One-Time)

1. Install `rclone` on Windows (e.g. `winget install Rclone.Rclone`), then open a new terminal.
2. Run:
   ```powershell
   rclone authorize "drive"
   ```
3. Sign in with your personal Google account in the browser and copy the JSON token printed in the terminal (`{"access_token":...}`).
4. Paste this into the `RCLONE_CONF` GitHub Secret (replacing `<PASTE_TOKEN_JSON_HERE>`):
   ```ini
   [gdrive]
   type = drive
   scope = drive.file
   token = <PASTE_TOKEN_JSON_HERE>
   ```
   *(Note: if you want `rclone` to upload into an existing folder created manually in Drive rather than only folders created by `rclone`, use `scope = drive` instead of `scope = drive.file`.)*
