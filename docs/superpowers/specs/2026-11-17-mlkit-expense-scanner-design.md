# Design Spec: ML Kit Expense Scanner, Cards & Store Format Learning

**Date:** 2026-11-17
**Status:** Ready for Approval
**Feature Map:** `FM-009` (to be added to `docs/FEATURE_MAP.md`)

---

## 1. Intent & Why

Users want to populate daily expense logs in Omni by scanning **card transaction screenshots**, **store receipts** (grocery, gas, department store — no fixed template), and **PDF documents** using on-device **Google ML Kit Text Recognition**.

Key motivations:
- Eliminate manual typing when logging multiple items from a card history screenshot or a store receipt.
- Keep financial data private and offline: **never permanently store scanned images**; extract text in memory/temp cache and delete the temporary image immediately.
- Support **cards with optional billing cycles** so card transaction dates map to the right monthly budget cycle automatically.
- Improve accuracy over time for recurring stores by **automatically saving and updating a lightweight text-pattern profile** per store/source.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- Add simple **Card Management** (`CardEntity`: card name, card type, optional billing cycle day `1..31`).
- Before scanning, let the user choose **Document Type**: **Card** (then pick a saved card or add one) or **Receipt**.
- Support **three input paths**:
  1. Upload an image / screenshot (`OpenDocument` / Photo Picker).
  2. Upload a PDF document (`OpenDocument` -> rendered page-by-page via `android.graphics.pdf.PdfRenderer`).
  3. Capture a live camera photo (`ActivityResultContracts.TakePicture` to a temporary cache file).
- Support **long receipts & multi-page screenshots** via **multi-part scanning** (`+ Scan Another Part` inside the review sheet) that appends newly scanned items into the same review batch.
- Handle **signed amounts** on card screenshots: `-250.00` (debit) -> positive expense (`25000L`, selected by default); `+250.00` (credit/refund/payment) -> auto-unchecked by default.
- Route card transaction dates using the selected card's optional **billing cycle cut-off day** (or fall back to the active budget cycle when no billing cycle is set or no date is parsed).
- Provide a **Review & Import Sheet** where the user can edit titles/amounts, check/uncheck items, assign envelopes (bulk default or per-item), and log all selected items in one atomic Room transaction.
- **Automatically save/update a store format profile** in Room when an import is confirmed, remembering the store signature, default envelope name (across cycles), and ignored summary/noise keywords.

### Non-Goals
- Storing receipt photos or screenshots in app storage or gallery.
- Cloud OCR or LLM API calls (must run 100% on-device and offline).
- Custom in-app CameraX viewfinder UI (system camera via `TakePicture` is sufficient).
- Full credit-card statement reconciliation, interest calculation, or balance tracking.

### Anti-Goals
- Losing existing Room database data on upgrade (must use an explicit `Migration(1, 2)` — never `fallbackToDestructiveMigration()`).
- Logging summary/noise lines (`TOTAL`, `SUBTOTAL`, `VAT`, `CHANGE`, `CASH`, `BALANCE`) or `+` incoming payments/refunds as expenses without user confirmation.
- Leaving temporary camera capture files behind in `cacheDir` after scanning fails or cancels.

---

## 3. Constraints

- **Offline-first & Privacy:** Use `com.google.mlkit:text-recognition` (bundled Latin model). No network permission or cloud upload for OCR.
- **Zero Permanent Image Storage:**
  - Uploaded `Uri`s are read transiently via `ContentResolver` without taking persistable URI permissions or copying files to `filesDir`.
  - Live camera photos use `FileProvider` pointing to `cacheDir/scan_tmp/<uuid>.jpg`, deleted inside a `finally` block as soon as ML Kit completes (or on cancel/error).
  - Decoded `Bitmap`s (from images or `PdfRenderer`) are downsampled (`maxDimension <= 2048px`) and recycled immediately after OCR.
- **Database Migration Safety:** `AppDatabase` is currently `version = 1`. Adding `cards` and `scan_format_profiles` must bump `version = 2` with `MIGRATION_1_2` creating only the new tables.
- **Monetary & UI Standards:** All amounts stored as `Long` centavos (`100L = ₱1.00`), formatted in `₱` with `JetBrains Mono` (`tnum`), dark OLED palette (`OLEDColors`), and `>= 48.dp` touch targets.

---

## 4. Approaches Considered

1. **Approach A — On-Device ML Kit (`text-recognition`) + Geometry-Aware Heuristic Parser + System Camera/Picker *(Selected)***
   - Uses bundled ML Kit Latin model, `TakePicture` + `OpenDocument`, `PdfRenderer` for PDFs, bounding-box row grouping, and a Review Sheet with automatic Room format learning.
   - *Tradeoff:* Fast, offline, zero API cost, minimal permissions (`FileProvider` only, no `CAMERA` runtime permission needed for `TakePicture`), and handles arbitrary receipt/screenshot layouts via row geometry + editable review.

2. **Approach B — Custom In-App CameraX Viewfinder + ML Kit**
   - Same parser/UI as A, but adds `androidx.camera` dependencies and `CAMERA` runtime permission for an embedded viewfinder.
   - *Tradeoff:* Extra complexity and permission friction for marginal gain over `TakePicture`.

3. **Approach C — ML Kit OCR + Cloud LLM Parsing**
   - Sends extracted text to an external LLM API to parse arbitrary formats.
   - *Tradeoff:* Requires internet, API key/cost, and sends private financial text off-device.

---

## 5. Architecture & Core Flows

### 5.1 Cards (`CardEntity`)
- New Room entity `cards` (added in `AppDatabase` v2):
  - `id: Long` (PK, autoGenerate)
  - `name: String` (e.g., `"BDO Visa"`, `"GCash Card"`)
  - `cardType: String` (`"CREDIT"`, `"DEBIT"`, `"EWALLET"`)
  - `billingCycleDay: Int?` (nullable `1..31`; optional cut-off day of month)
  - `createdAtEpochMs: Long`
- Users can add, edit, and delete cards via a simple `AddEditCardSheet` / `CardsSheet` reachable directly from the **Card** scan flow (`+ Add Card`) and from `LogExpenseSheet`.

### 5.2 Entry, Document Type & Multi-Part Capture Flow
- [LogExpenseSheet.kt](file:///C:/Users/mcalonzo/codes/personal-finance-os/app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/LogExpenseSheet.kt) gains a `"Scan Receipt / Screenshot"` action.
- Tapping it opens a compact **Scan Source Setup** step:
  1. **Document Type:** `Card` or `Receipt`.
  2. If **`Card`** is selected: show the user's saved cards as selectable chips (plus `+ Add Card` if none or to add another).
  3. **Input Method:** `Upload Image`, `Upload PDF`, or `Take Photo` (`TakePicture`).
- Scanning runs off the main thread (`Dispatchers.IO` / `Task.await()`), showing a loading state, then opens `ScanReviewSheet` with the extracted `ScannedExpenseDraft` list.
- Inside `ScanReviewSheet`, **`+ Scan Another Part`** launches the 3-source picker/camera using the same selected Document Type / Card and **appends** newly parsed items to the existing draft list (flagging likely duplicates if an identical title + amount already exists in the batch).

### 5.3 Geometry-Aware OCR Parser & Signed Amounts
- Input to parser: ML Kit `Text` (`textBlocks` -> `lines` -> `elements`, each with `boundingBox`) plus `ScanDocumentType` (`CARD` or `RECEIPT`).
- **Row Grouping:** Group lines/elements whose vertical `boundingBox.centerY()` falls within a tolerance (proportional to median line height) into logical horizontal rows sorted top-to-bottom, left-to-right.
- **Amount & Sign Extraction:**
  - Detect right-most (or trailing) monetary tokens (`₱`, `PHP`, or decimal/comma currency numbers like `-250.00`, `+1,000.00`, `85.50`).
  - In `CARD` mode: `-250.00` is normalized to positive `25000L` centavos (`isSelected = true`); `+250.00` (or credit/refund lines) is flagged `isCredit = true` and `isSelected = false` by default.
  - In `RECEIPT` mode: amounts are normalized to positive centavos (`> 0L`); 2-line items (`ITEM NAME` on row $N$, `2 x 45.00   90.00` on row $N+1$) are paired.
- **Title & Date Extraction:**
  - Left-side tokens form the item/merchant title (preserving quantity prefixes like `2x` when present).
  - Transaction dates (`MM/dd/yyyy`, `yyyy-MM-dd`, `MMM dd`, etc.) are parsed into `LocalDate?` (`parsedDate`) and formatted as `dateLabel` metadata.
- **Noise / Summary Filtering:** Rows matching summary keywords (`TOTAL`, `SUBTOTAL`, `AMOUNT DUE`, `VAT`, `TAX`, `CASH`, `CHANGE`, `TENDERED`, `BALANCE`, `POINTS`) or matched profile `ignoredKeywords` are marked `isSelected = false`.

### 5.4 Billing-Cycle Date Routing
- When `CARD` mode is used with a card that has `billingCycleDay = D` (`1..31`) and a row has `parsedDate != null`:
  - If `parsedDate.dayOfMonth > D`, the row targets `YearMonth.from(parsedDate).plusMonths(1)`; otherwise `YearMonth.from(parsedDate)`.
- When `billingCycleDay == null`, `parsedDate == null`, or in `RECEIPT` mode: the row targets the currently selected budget cycle (`selectedYearMonth`).
- `ScanReviewSheet` displays the resolved target month per row (with an option to override to the current month if needed).

### 5.5 Automatic Store / Format Learning (`ScanFormatProfileEntity`)
- New Room entity `scan_format_profiles` (added in `AppDatabase` v2):
  - `id: Long` (PK, autoGenerate)
  - `storeKey: String` (unique normalized header signature, e.g. `"SM SUPERMARKET"`, `"BDO VISA"`)
  - `displayName: String`
  - `defaultEnvelopeName: String?` *(Builder-check call: stored by normalized envelope name instead of `envelopeId` so the learned default survives monthly cycle rollovers when new `EnvelopeEntity` IDs are created)*
  - `ignoredKeywordsPipe: String` (pipe-delimited normalized keywords/labels unchecked by user)
  - `scanCount: Int`
  - `updatedAtEpochMs: Long`
- **Matching on Scan:** Extract candidate store/source name from top non-numeric header lines (or selected card name for card scans). Look up matching `ScanFormatProfileEntity`. If found:
  - Pre-assign `defaultEnvelopeName` to matching envelopes in the active cycle.
  - Auto-uncheck rows matching `ignoredKeywordsPipe`.
- **Automatic Save/Update on Import:** When the user taps `"Log X Expenses"`, automatically upsert the profile for `storeName` (if non-blank):
  - Set `defaultEnvelopeName` to the envelope name most frequently chosen in the confirmed batch.
  - Merge newly unchecked noise/summary row labels into `ignoredKeywordsPipe`.
  - Increment `scanCount` and update `updatedAtEpochMs`.

### 5.6 Review & Atomic Batch Logging
- `ScanReviewSheet` displays:
  - Detected store/source name (editable), selected Card badge (if `CARD`), and target cycle summary.
  - Default batch envelope selector + `"+ Scan Another Part"` action.
  - Scrollable checklist of parsed rows: checkbox, editable title, editable PHP amount, `dateLabel` / target month badge, and per-row envelope chip.
- Confirming calls `MainViewModel.logScannedExpenses(...)` -> `BudgetRepository.logScannedExpensesBatch(...)`, executing inside `db.withTransaction`:
  - Resolves `getOrCreateCycleId(targetYearMonth)` for each checked item's target month and resolves the target `EnvelopeEntity` by name in that cycle (falling back to creating or matching the selected envelope if not yet in that cycle).
  - Inserts one `TransactionEntity` per checked item (using `parsedDate` at noon Manila time if present, else `System.currentTimeMillis()`).
  - Increments each target `EnvelopeEntity.spentCentavos` atomically.
  - Automatically upserts the `ScanFormatProfileEntity`.

---

## 6. Left to the Builder

- `FileProvider` configuration in `AndroidManifest.xml` and `res/xml/file_paths.xml` for `cacheDir/scan_tmp/`.
- Exact Y-center tolerance, regex patterns for PH currency/dates, and 2-line receipt item pairing heuristics.
- Duplicate-item indicator heuristic when appending multiple receipt parts.
- Handling edge cases when a historical/future target cycle doesn't yet have an envelope matching the selected envelope name (e.g., cloning/creating or falling back cleanly).
- Unit tests for `OcrExpenseParser`, `CardBillingCycleCalculator`, `ScanFormatProfile` matching/updating, `Migration(1, 2)` in `AppDatabase`, `BudgetRepository.logScannedExpensesBatch`, and Compose UI tests for `ScanReviewSheet` and `AddEditCardSheet`.
