# GitHub Actions Release APK & Personal Google Drive Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically run unit tests, build a signed release APK on every push to `main`, and upload a uniquely versioned APK (`Omni-v<versionName>-b<run_number>-<short_sha>.apk`) to personal Google Drive via `rclone`.

**Architecture:** A single GitHub Actions workflow (`.github/workflows/release-to-drive.yml`) on `ubuntu-latest` sets up JDK 17, decodes `app/release.jks` from `RELEASE_KEYSTORE_BASE64`, runs `./gradlew testDebugUnitTest assembleRelease --no-daemon`, renames `app-release.apk`, uploads via `rclone copy` using `RCLONE_CONF` and `GDRIVE_FOLDER_ID`, and deletes temporary secret files in an `if: always()` step. A short setup guide (`docs/RELEASE_GDRIVE_SETUP.md`) documents the exact PowerShell commands to populate the 6 GitHub Secrets.

**Tech Stack:** GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`), `rclone`, Gradle 8.13, JDK 17.

**Spec:** [2026-11-18-github-actions-gdrive-release-design.md](file:///C:/Users/mcalonzo/codes/personal-finance-os/docs/superpowers/specs/2026-11-18-github-actions-gdrive-release-design.md)

## Global Constraints

- Trigger on `push` to `main` and `workflow_dispatch`.
- Never commit `app/release.jks`, passwords, or `rclone.conf` to git; decode in CI from GitHub Secrets and delete in `if: always()`.
- Run `testDebugUnitTest` before/with `assembleRelease` so failing tests block Drive upload.
- Keep separate versioned APK files per build (`Omni-v<versionName>-b<run_number>-<short_sha>.apk`).

## Review Focus

- **Secret cleanup on failure:** `app/release.jks` and temp `rclone.conf` must be removed with `if: always()` even if Gradle or `rclone` fails.
- **Missing secret guard:** Fail early with a clear message if `RELEASE_KEYSTORE_BASE64`, `RCLONE_CONF`, or `GDRIVE_FOLDER_ID` is empty.
- **Versioned filename collision-free:** Filename includes both `github.run_number` and 7-char `github.sha` so re-runs or repeated pushes never overwrite previous APKs.

---

### Task 1: Add GitHub Actions Release-to-Drive Workflow & Setup Guide

**Files:**
- Create: `.github/workflows/release-to-drive.yml`
- Create: `docs/RELEASE_GDRIVE_SETUP.md`

**Interfaces:**
- Consumes: `app/build.gradle.kts` (`versionName`, `FINANCEOS_STORE_PASSWORD`, `FINANCEOS_KEY_ALIAS`, `FINANCEOS_KEY_PASSWORD`).
- Produces: `.github/workflows/release-to-drive.yml` and `docs/RELEASE_GDRIVE_SETUP.md`.

- [ ] **Step 1: Create `.github/workflows/release-to-drive.yml`**
  - `on: push: branches: [main]` and `workflow_dispatch:`.
  - Checkout repo, setup JDK 17 (`temurin`), setup Gradle cache.
  - Validate required secrets (`RELEASE_KEYSTORE_BASE64`, `RCLONE_CONF`, `GDRIVE_FOLDER_ID`), decode `app/release.jks`.
  - Run `./gradlew testDebugUnitTest assembleRelease --no-daemon` with `FINANCEOS_STORE_PASSWORD`, `FINANCEOS_KEY_ALIAS`, `FINANCEOS_KEY_PASSWORD` env vars.
  - Extract `versionName` from `app/build.gradle.kts` (`Omni-v${VERSION_NAME}-b${GITHUB_RUN_NUMBER}-${SHORT_SHA}.apk`), install `rclone`, write temp `rclone.conf`, and run `rclone copy "${RENAMED_APK}" "gdrive:" --drive-root-folder-id "${GDRIVE_FOLDER_ID}"`.
  - Also upload APK as a GitHub Actions workflow artifact (`actions/upload-artifact@v4`).
  - `if: always()` step: `rm -f app/release.jks "${RUNNER_TEMP}/rclone.conf"`.

- [ ] **Step 2: Create `docs/RELEASE_GDRIVE_SETUP.md`**
  - Document the 6 GitHub Secrets (`RELEASE_KEYSTORE_BASE64`, `FINANCEOS_STORE_PASSWORD`, `FINANCEOS_KEY_ALIAS`, `FINANCEOS_KEY_PASSWORD`, `RCLONE_CONF`, `GDRIVE_FOLDER_ID`) with exact PowerShell commands for Windows (`[Convert]::ToBase64String(...)` and `rclone config` / `gdrive` remote snippet).

- [ ] **Step 3: Validate YAML syntax & workflow structure**
  - Run Python `yaml.safe_load` (or PowerShell check) on `.github/workflows/release-to-drive.yml` and verify `git status` shows no secret files tracked.

- [ ] **Step 4: Commit**
  - `git add .github/workflows/release-to-drive.yml docs/RELEASE_GDRIVE_SETUP.md`
  - `git commit -m "ci: add GitHub Actions workflow to build release APK and upload to Google Drive"`
