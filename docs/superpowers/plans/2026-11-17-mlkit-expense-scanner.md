# ML Kit Expense Scanner, Cards & Store Format Learning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add on-device Google ML Kit Text Recognition in Omni to scan card transaction screenshots, store receipts, and PDFs (via upload or live camera without saving images), manage cards with optional billing cycles, and automatically learn recurring store/source formats.

**Architecture:** `OcrImageScanner` extracts bounding-box text lines on `Dispatchers.IO` from transient bitmaps/PDF pages and deletes temporary camera files in `finally`. `OcrExpenseParser` groups lines by vertical row geometry, parses signed card amounts (`-` expense, `+` credit) and dates, and `CardBillingCycleCalculator` routes dates to the matching monthly `BudgetCycleEntity`. `ScanReviewSheet` lets users append extra parts (`+ Scan Another Part`), edit/uncheck rows, assign envelopes, and commit via `BudgetRepository.logScannedExpensesBatch` while automatically upserting `ScanFormatProfileEntity` by envelope name for cross-cycle reuse.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose Material 3, Room 2.7.2 (`MIGRATION_1_2`), Hilt 2.56.2, Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`), Android `PdfRenderer`, `FileProvider` + `ActivityResultContracts.TakePicture` / `OpenDocument`, JUnit4 + Truth + Robolectric.

**Spec:** [2026-11-17-mlkit-expense-scanner-design.md](file:///C:/Users/mcalonzo/codes/personal-finance-os/docs/superpowers/specs/2026-11-17-mlkit-expense-scanner-design.md)

## Global Constraints

- **Offline-first & Privacy:** Use bundled `com.google.mlkit:text-recognition` (no cloud OCR/LLM calls, no `CAMERA` runtime permission required via `TakePicture`).
- **Zero Permanent Image Storage:** Never copy uploaded images or camera photos to `filesDir`; delete `cacheDir/scan_tmp/*.jpg` in `finally` and recycle `Bitmap`s immediately after OCR.
- **Database Migration Safety:** Bump `AppDatabase` from `version = 1` to `version = 2` with explicit `MIGRATION_1_2`; never use `fallbackToDestructiveMigration()`.
- **Monetary & UI Standards:** Store all amounts as `Long` centavos (`100L = ₱1.00`), format in `₱` with `JetBrains Mono` (`tnum`), use `OLEDColors` tokens, and keep all clickable targets `>= 48.dp`.
- **Feature Isolation & Map:** Implement on a dedicated git branch and update `docs/FEATURE_MAP.md` (`FM-009`).

## Review Focus

- **Database upgrade preserves v1 user data:** `MIGRATION_1_2` must keep existing `budget_cycles`, `envelopes`, and `transactions` intact without throwing schema mismatch errors.
- **Signed card amounts & summary noise:** Card screenshots with `+1,000.00` credits/refunds and receipt `TOTAL` / `VAT` / `CHANGE` lines must be unchecked by default (`isSelected = false`), while `-250.00` debits normalize to positive `25000L` (`isSelected = true`).
- **Cross-month envelope resolution:** When a card transaction date + `billingCycleDay` routes to a different `YearMonth` than the active cycle, `logScannedExpensesBatch` must resolve or create that target cycle and find/clone the chosen envelope without failing the FK constraint.
- **Camera temp-file cleanup on failure/cancel:** `OcrImageScanner` and `MainViewModel` must delete `cacheDir/scan_tmp/*.jpg` even when ML Kit throws or the user cancels camera capture.
- **Multi-part receipt duplicate flagging:** Appending a second receipt part with an overlapping `(title, amountCentavos)` item must flag the duplicate in `ScanReviewSheet` so the user doesn't double-log it.

---

### Task 1: Room v2 Schema (`CardEntity`, `ScanFormatProfileEntity`), `ScanDao`, and `MIGRATION_1_2`

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/entity/CardEntity.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/entity/ScanFormatProfileEntity.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/dao/ScanDao.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/AppDatabase.kt:21-43`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/di/AppModule.kt:25-60`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/local/ScanDatabaseMigrationTest.kt`

**Interfaces:**
- Produces: `CardEntity`, `ScanFormatProfileEntity`, `ScanDao`, `AppDatabase.MIGRATION_1_2: Migration`.

- [ ] **Step 1: Write the failing test**

Create `ScanDatabaseMigrationTest.kt` testing `MIGRATION_1_2` on a SQLite v1 schema seeded with a `budget_cycles` row, verifying v1 data survives migration and `ScanDao` inserts/reads `CardEntity` and `ScanFormatProfileEntity` by `storeKey`.

```kotlin
@Test
fun `migration 1 to 2 preserves existing cycle data and creates cards and scan_format_profiles`() = runTest {
    // Open SQLite helper at v1 with budget_cycles row, run AppDatabase.MIGRATION_1_2.migrate(db),
    // verify budget_cycles count == 1 and scan_format_profiles + cards tables accept inserts.
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.ScanDatabaseMigrationTest" --no-daemon`
Expected: FAIL (`Unresolved reference 'CardEntity'`, `'ScanFormatProfileEntity'`, `'MIGRATION_1_2'`).

- [ ] **Step 3: Implement entities, `ScanDao`, `AppDatabase` v2 + `MIGRATION_1_2`, and `AppModule` provider**

- `CardEntity`: `@Entity(tableName = "cards")` with `id: Long = 0L`, `name: String`, `cardType: String` (`"CREDIT"`, `"DEBIT"`, `"EWALLET"`), `billingCycleDay: Int? = null`, `createdAtEpochMs: Long = System.currentTimeMillis()`.
- `ScanFormatProfileEntity`: `@Entity(tableName = "scan_format_profiles", indices = [Index(value = ["storeKey"], unique = true)])` with `id: Long = 0L`, `storeKey: String`, `displayName: String`, `defaultEnvelopeName: String? = null`, `ignoredKeywordsPipe: String = ""`, `scanCount: Int = 1`, `updatedAtEpochMs: Long = System.currentTimeMillis()`.
- `ScanDao`: `observeCards(): Flow<List<CardEntity>>`, `getCardById(id: Long): CardEntity?`, `upsertCard(card: CardEntity): Long`, `deleteCardById(id: Long)`, `getAllFormatProfilesOnce(): List<ScanFormatProfileEntity>`, `getFormatProfileByKey(storeKey: String): ScanFormatProfileEntity?`, `upsertFormatProfile(profile: ScanFormatProfileEntity): Long`.
- `AppDatabase`: add `CardEntity::class, ScanFormatProfileEntity::class`, `version = 2`, `abstract fun scanDao(): ScanDao`, and `companion object { val MIGRATION_1_2 = object : Migration(1, 2) { ... } }` executing `CREATE TABLE IF NOT EXISTS cards ...` and `CREATE TABLE IF NOT EXISTS scan_format_profiles ...` + `CREATE UNIQUE INDEX IF NOT EXISTS index_scan_format_profiles_storeKey ON scan_format_profiles(storeKey)`.
- `AppModule`: add `.addMigrations(AppDatabase.MIGRATION_1_2)` to `Room.databaseBuilder` and `@Provides fun provideScanDao(db: AppDatabase): ScanDao = db.scanDao()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.ScanDatabaseMigrationTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/local/ app/src/main/java/com/mcalonzo/personalfinanceos/di/AppModule.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/local/ScanDatabaseMigrationTest.kt
git commit -m "feat(scan): add Room v2 cards and scan_format_profiles tables with MIGRATION_1_2"
```

---

### Task 2: Domain Calculators (`CardBillingCycleCalculator` and `OcrExpenseParser`)

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/model/ScanModels.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/calculator/CardBillingCycleCalculator.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/calculator/OcrExpenseParser.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/domain/calculator/OcrExpenseParserAndBillingCycleTest.kt`

**Interfaces:**
- Consumes: `ScanFormatProfileEntity` (from Task 1).
- Produces:
  - `enum class ScanDocumentType { CARD, RECEIPT }`
  - `data class OcrLineBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)`
  - `data class ScannedExpenseDraft(val localId: String, val title: String, val amountCentavos: Long, val parsedDate: LocalDate?, val dateLabel: String, val targetYearMonth: YearMonth, val isCredit: Boolean, val isSummaryNoise: Boolean, val isDuplicate: Boolean, val isSelected: Boolean, val envelopeName: String?)`
  - `data class OcrParseResult(val detectedStoreName: String, val drafts: List<ScannedExpenseDraft>)`
  - `CardBillingCycleCalculator.resolveTargetMonth(transactionDate: LocalDate?, billingCycleDay: Int?, fallbackMonth: YearMonth): YearMonth`
  - `OcrExpenseParser.parse(lines: List<OcrLineBox>, documentType: ScanDocumentType, billingCycleDay: Int?, activeMonth: YearMonth, matchedProfile: ScanFormatProfileEntity?, cardDisplayName: String? = null, referenceYear: Int = activeMonth.year): OcrParseResult`
  - `OcrExpenseParser.detectStoreCandidate(lines: List<OcrLineBox>): String`
  - `OcrExpenseParser.flagDuplicates(existing: List<ScannedExpenseDraft>, incoming: List<ScannedExpenseDraft>): List<ScannedExpenseDraft>`

- [ ] **Step 1: Write the failing tests**

In `OcrExpenseParserAndBillingCycleTest.kt`, assert:
1. `CardBillingCycleCalculator.resolveTargetMonth(LocalDate.of(2026, 11, 16), billingCycleDay = 15, YearMonth.of(2026, 11))` returns `YearMonth.of(2026, 12)`, while `2026-11-15` returns `2026-11`.
2. `OcrExpenseParser.parse(...)` in `CARD` mode parses `"-250.00"` as `amountCentavos = 25_000L, isSelected = true, isCredit = false` and `"+1,500.00"` as `amountCentavos = 150_000L, isSelected = false, isCredit = true`.
3. `OcrExpenseParser.parse(...)` in `RECEIPT` mode pairs left-side items (and 2-line `"Eggs"` + `"2 x 45.00  90.00"`) with right-side amounts, and sets `isSelected = false, isSummaryNoise = true` for `TOTAL`, `VAT`, and `CHANGE` rows.
4. `matchedProfile` with `ignoredKeywordsPipe = "POINTS EARNED"` auto-unchecks `"Points Earned"` and sets `envelopeName = matchedProfile.defaultEnvelopeName`.
5. `OcrExpenseParser.flagDuplicates` marks incoming rows whose normalized `(title, amountCentavos)` already exist in `existing` with `isDuplicate = true, isSelected = false`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.domain.calculator.OcrExpenseParserAndBillingCycleTest" --no-daemon`
Expected: FAIL (`Unresolved reference 'OcrExpenseParser'`).

- [ ] **Step 3: Implement `ScanModels.kt`, `CardBillingCycleCalculator.kt`, and `OcrExpenseParser.kt`**

- Group `OcrLineBox` items by vertical Y-center within `max(12, medianHeight * 0.55)` into sorted rows (`top`, then `left`).
- Extract rightmost monetary token per row (`[+-]?\s*(?:₱|PHP)?\s*\d{1,3}(?:,\d{3})*(?:\.\d{2})?`), parse date tokens (`MM/dd/yyyy`, `yyyy-MM-dd`, `MMM dd[, yyyy]`, `dd MMM yyyy`), and keep remaining left tokens as `title`.
- Normalize `storeKey = storeName.uppercase().replace(Regex("[^A-Z0-9 ]"), "").trim()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.domain.calculator.OcrExpenseParserAndBillingCycleTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/domain/ app/src/test/java/com/mcalonzo/personalfinanceos/domain/calculator/OcrExpenseParserAndBillingCycleTest.kt
git commit -m "feat(scan): add geometry-aware OcrExpenseParser and CardBillingCycleCalculator"
```

---

### Task 3: ML Kit Dependency, `FileProvider` & Ephemeral `OcrImageScanner`

**Files:**
- Modify: `gradle/libs.versions.toml:15-65`
- Modify: `app/build.gradle.kts:85-105`
- Modify: `app/src/main/AndroidManifest.xml:40-52`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/ocr/OcrImageScanner.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/ocr/OcrImageScannerTest.kt`

**Interfaces:**
- Consumes: `OcrLineBox` (from Task 2).
- Produces: `OcrImageScanner.createTempCameraFile(context: Context): File`, `OcrImageScanner.getTempCameraUri(context: Context, file: File): Uri`, `OcrImageScanner.scanImageUri(context: Context, uri: Uri): List<OcrLineBox>`, `OcrImageScanner.scanPdfUri(context: Context, uri: Uri, maxPages: Int = 10): List<OcrLineBox>`, `OcrImageScanner.deleteTempFileQuietly(file: File?)`.

- [ ] **Step 1: Write the failing test**

In `OcrImageScannerTest.kt` (Robolectric), assert `createTempCameraFile(context)` creates a file inside `context.cacheDir/scan_tmp/`, `getTempCameraUri(context, file)` returns a `content://com.mcalonzo.personalfinanceos.fileprovider/...` URI, and `deleteTempFileQuietly(file)` removes it cleanly without leaving files behind.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.ocr.OcrImageScannerTest" --no-daemon`
Expected: FAIL (`Unresolved reference 'OcrImageScanner'`).

- [ ] **Step 3: Add ML Kit dependency, `FileProvider`, `file_paths.xml`, and `OcrImageScanner.kt`**

- Add `mlkit-text-recognition = "com.google.mlkit:text-recognition:16.0.1"` in `libs.versions.toml` and `app/build.gradle.kts`.
- Add `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">` with `@xml/file_paths` (`<cache-path name="scan_tmp" path="scan_tmp/" />`).
- Implement `OcrImageScanner` using `InputImage.fromBitmap(bitmap, 0)` with `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` (bridged to suspend via `suspendCancellableCoroutine` on `addOnSuccessListener` / `addOnFailureListener`), downsampling bitmaps to `maxDimension <= 2048px`, rendering PDFs with `PdfRenderer`, and recycling bitmaps in `finally`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.ocr.OcrImageScannerTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/java/com/mcalonzo/personalfinanceos/data/ocr/OcrImageScanner.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/ocr/OcrImageScannerTest.kt
git commit -m "feat(scan): add ML Kit text recognition dependency, FileProvider, and OcrImageScanner"
```

---

### Task 4: Repository & ViewModel Wiring (`BudgetRepository`, `MainViewModel`)

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepository.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModel.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepositoryTest.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `ScanDao`, `CardEntity`, `ScanFormatProfileEntity` (Task 1), `OcrExpenseParser`, `ScanDocumentType`, `ScannedExpenseDraft` (Task 2), `OcrImageScanner` (Task 3).
- Produces:
  - `BudgetRepository`: `observeCards(): Flow<List<CardEntity>>`, `upsertCard(id: Long = 0L, name: String, cardType: String, billingCycleDay: Int?): Long`, `deleteCard(cardId: Long)`, `findMatchingFormatProfile(storeCandidate: String, cardName: String?): ScanFormatProfileEntity?`, `logScannedExpensesBatch(items: List<ScannedExpenseDraft>, storeName: String, uncheckedNoiseTitles: List<String>): Int`.
  - `MainUiState`: `cards: List<CardEntity> = emptyList()`, `scanBatchState: ScanBatchUiState = ScanBatchUiState()`.
  - `MainViewModel`: `upsertCard(...)`, `deleteCard(...)`, `startScanFromLines(...)` (shared helper for unit tests + URI/camera wrappers `scanImageUri`, `scanPdfUri`, `scanCameraFile`), `updateScanBatchStoreName(...)`, `applyBatchDefaultEnvelope(...)`, `updateScanDraft(...)`, `toggleScanDraft(...)`, `clearScanBatch()`, `confirmScannedExpenses()`.

- [ ] **Step 1: Write the failing tests**

- In `BudgetRepositoryTest.kt`: add `logScannedExpensesBatch inserts multi-cycle transactions, updates envelope spent, and auto-upserts ScanFormatProfileEntity`.
- In `MainViewModelTest.kt`: add `upsertCard, processScannedLines, and confirmScannedExpenses log selected items and remember store default envelope across cycles`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.repository.BudgetRepositoryTest" --tests "com.mcalonzo.personalfinanceos.ui.viewmodel.MainViewModelTest" --no-daemon`
Expected: FAIL (`Unresolved reference 'logScannedExpensesBatch'`).

- [ ] **Step 3: Implement `BudgetRepository` and `MainViewModel` scan & card methods**

- `BudgetRepository.logScannedExpensesBatch`: inside `db.withTransaction`, for each selected draft (`amountCentavos > 0L`), call `getOrCreateCycleId(draft.targetYearMonth)`, resolve `EnvelopeEntity` in that cycle by `envelopeName` (case-insensitive; if absent, clone the envelope template/active envelope by name into that cycle or fall back to the first envelope in that cycle), insert `TransactionEntity`, call `budgetDao.incrementEnvelopeSpent(envelope.id, draft.amountCentavos)`, and upsert `ScanFormatProfileEntity` with `defaultEnvelopeName` = most-frequent envelope name in the confirmed batch and merged `ignoredKeywordsPipe`.
- `MainViewModel`: inject `ScanDao` / `OcrImageScanner` (nullable default in internal constructor for Robolectric/JVM unit tests), delete temp camera `File` inside `finally`, and append new parts via `OcrExpenseParser.flagDuplicates(existing, incoming)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.repository.BudgetRepositoryTest" --tests "com.mcalonzo.personalfinanceos.ui.viewmodel.MainViewModelTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepository.kt app/src/main/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModel.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepositoryTest.kt app/src/test/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModelTest.kt
git commit -m "feat(scan): wire batch expense logging, card CRUD, and format profile learning"
```

---

### Task 5: UI Sheets (`ScanSheets.kt`), `LogExpenseSheet`, `MainScreen`, `MainActivity` & Feature Map

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/ScanSheets.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/LogExpenseSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MainScreen.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/MainActivity.kt`
- Modify: `docs/FEATURE_MAP.md`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ui/ScanSheetsTest.kt`

**Interfaces:**
- Consumes: `CardEntity` (Task 1), `ScanDocumentType`, `ScannedExpenseDraft` (Task 2), `OcrImageScanner` (Task 3), `MainUiState.cards` & `scanBatchState` (Task 4).
- Produces: `ScanSourceSheet`, `AddEditCardSheet`, `ScanReviewSheet`, and `FM-009` entry in `docs/FEATURE_MAP.md`.

- [ ] **Step 1: Write the failing UI tests**

In `ScanSheetsTest.kt` (Robolectric Compose), test:
1. `ScanSourceSheet` hides card chips in `Receipt` mode, shows saved cards + `+ Add Card` when `Card` is selected, and triggers input callbacks.
2. `AddEditCardSheet` validates card name & optional billing cycle (`1..31`) and submits `(name, cardType, billingCycleDay)`.
3. `ScanReviewSheet` renders parsed drafts (including duplicate badge and credit unchecked state), allows editing a row title/amount, toggling check, tapping `+ Scan Another Part`, and confirming `"Log X Expenses"`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.ScanSheetsTest" --no-daemon`
Expected: FAIL (`Unresolved reference 'ScanSourceSheet'`).

- [ ] **Step 3: Implement `ScanSheets.kt`, update `LogExpenseSheet.kt`, `MainScreen.kt`, `MainActivity.kt`, and `docs/FEATURE_MAP.md`**

- `LogExpenseSheet`: add `"Scan Receipt / Screenshot"` outlined button (`>= 48.dp`) that opens `ScanSourceSheet`.
- `MainActivity`: register `OpenDocument` launchers for `image/*` and `application/pdf`, plus `TakePicture` launcher using `OcrImageScanner.createTempCameraFile` / `getTempCameraUri` (cleaning up temp file if `success == false`).
- `docs/FEATURE_MAP.md`: add `FM-009 | ML Kit Expense Scanner & Cards | Core | LogExpenseSheet -> ScanSourceSheet / ScanReviewSheet | ... | verified`.

- [ ] **Step 4: Run full test suite and APK build**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon`
Expected: `BUILD SUCCESSFUL` with all unit + Robolectric Compose UI tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/ app/src/test/java/com/mcalonzo/personalfinanceos/ui/ScanSheetsTest.kt docs/FEATURE_MAP.md
git commit -m "feat(scan): add ScanSourceSheet, AddEditCardSheet, ScanReviewSheet, and FM-009 wiring"
```
