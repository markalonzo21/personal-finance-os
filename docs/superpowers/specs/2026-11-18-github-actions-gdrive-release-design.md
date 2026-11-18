# Design Spec: GitHub Actions Release APK Build & Personal Google Drive Upload

**Date:** 2026-11-18
**Status:** Ready for Approval
**Size:** Small Change

---

## 1. Intent & Why

Automatically run unit tests, build a signed `app-release.apk` whenever `main` is updated, and upload a separate versioned APK to the user's personal Google Drive folder.

## 2. Goals, Non-Goals, and Anti-Goals

- **Goals:**
  - Trigger `.github/workflows/release-to-drive.yml` on `push` to `main` (and `workflow_dispatch` for manual re-runs).
  - Run `./gradlew testDebugUnitTest assembleRelease --no-daemon` on `ubuntu-latest` with JDK 17.
  - Decode `app/release.jks` from `RELEASE_KEYSTORE_BASE64` and sign using existing `FINANCEOS_STORE_PASSWORD`, `FINANCEOS_KEY_ALIAS`, `FINANCEOS_KEY_PASSWORD` env vars in `app/build.gradle.kts`.
  - Name each uploaded APK uniquely (`Omni-v<versionName>-b<run_number>-<short_sha>.apk`) so every build is preserved in Google Drive.
  - Upload to personal Google Drive via `rclone copy` using `RCLONE_CONF` and `GDRIVE_FOLDER_ID` secrets.
- **Non-Goals:** Play Store publishing, automated versionCode bumping in git, or storing keystore/Drive tokens in the repository.
- **Anti-Goals:** Uploading an unsigned APK, skipping tests before release build, or leaving `app/release.jks` / `rclone.conf` uncleaned on the runner (or committed to git).

## 3. Constraints

- `app/release.jks` is git-ignored; CI must decode it from `RELEASE_KEYSTORE_BASE64` before `assembleRelease` and remove it in an `if: always()` cleanup step.
- `app/build.gradle.kts` reads `versionName` (`1.0.0`) and signing env vars `FINANCEOS_STORE_PASSWORD`, `FINANCEOS_KEY_ALIAS`, `FINANCEOS_KEY_PASSWORD`.
- Personal `@gmail.com` Google Drive blocks Service Account uploads (`storageQuotaExceeded`), so `rclone` OAuth2 (`RCLONE_CONF`) is required.

## 4. Approaches

- **Approach A — GitHub Actions + `rclone` (`RCLONE_CONF` OAuth2 secret) *(Selected)*:** Works directly with personal Gmail Google Drive quota; simple `rclone copy` into `GDRIVE_FOLDER_ID` (`--drive-root-folder-id`).
- **Approach B — Google Service Account (Drive API v3):** Fails on personal Gmail Drive due to 0 GB Service Account quota (Workspace Shared Drive only).

## 5. Required GitHub Repository Secrets

1. `RELEASE_KEYSTORE_BASE64` — Base64 of `app/release.jks` (`[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\release.jks"))`)
2. `FINANCEOS_STORE_PASSWORD` — Keystore password
3. `FINANCEOS_KEY_ALIAS` — Key alias (e.g. `personal_finance_os`)
4. `FINANCEOS_KEY_PASSWORD` — Key password
5. `RCLONE_CONF` — Contents of `rclone.conf` containing the `[gdrive]` remote from `rclone authorize "drive"`
6. `GDRIVE_FOLDER_ID` — Target Google Drive folder ID from the folder URL (`https://drive.google.com/drive/folders/<ID>`)

## 6. Left to the Builder

- Exact `.github/workflows/release-to-drive.yml` YAML implementation (JDK 17 setup, Gradle cache, versionName extraction from `app/build.gradle.kts`, `rclone` setup, `if: always()` secret file cleanup, and GitHub Actions workflow artifact backup if desired).
- Setup instructions in `docs/RELEASE_GDRIVE_SETUP.md` (or workflow comments) so the user can populate the 6 GitHub Secrets cleanly.
