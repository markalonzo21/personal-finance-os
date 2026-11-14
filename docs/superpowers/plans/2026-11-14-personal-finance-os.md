# Personal Finance OS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Personal Finance OS, an offline-first native Android daily-driver app for Philippine Peso (`₱`) monthly budgeting, recurring bill/loan due-date notifications, SOA PDF archival, and Honda Click 160 maintenance with an automated Saturday noon Petron Fairview fuel price watcher.

**Architecture:** Single-activity Jetpack Compose (Material 3) MVI architecture backed by Room SQLite `Flow` streams, Hilt dependency injection, `PdfRenderer` internal storage viewing, and Android `WorkManager` background workers (`MonthRolloverWorker`, `DueReminderWorker`, `GasPriceSyncWorker`) plus a `BroadcastReceiver` for 1-tap notification settlement. All monetary math uses `Long` minor centavos (`100L = ₱1.00`) to avoid floating-point rounding drift.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (BOM 2025.x, Material 3), Room 2.7.x, Hilt 2.5x, WorkManager 2.10.x, OkHttp 4.12.x, Kotlin Coroutines & Flow, Java `java.time` (`Asia/Manila`), JUnit4 + Truth + Coroutines Test + Robolectric / Compose UI Test.

**Spec:** [2026-11-14-personal-finance-os-design.md](file:///C:/Users/mcalonzo/docs/superpowers/specs/2026-11-14-personal-finance-os-design.md)

## Global Constraints

- All monetary values stored as `Long` centavos (`100L = ₱1.00`), displayed strictly in Philippine Peso (`₱`) using `JetBrains Mono` with `fontFeatureSettings = "tnum"`.
- Dark OLED palette tokens strictly matched to spec: Base `#06090E`, Card `#0D1522`, Subtle `#121D2F`, Elevated `#18263E`, Sky `#38BDF8`/`#0284C7`, Emerald `#10B981`, Amber `#F59E0B`, Rose `#F43F5E`, WCAG AA contrast >= 4.5:1, and all clickable targets >= `48.dp`.
- Budget Hero label `"SAFE-TO-SPEND BALANCE"` has no leading bullet dot; Top App Bar holds Month Picker + `SOA` PDF pill only (no redundant top-bar Routine button).
- Budget sub-tabs render in a strict 4-column equal-width grid (`Envelopes`, `Bills`, `Loans`, `Activity`) with compact `N!` overdue badge; Envelopes section title is `"ENVELOPES"`; Budget bottom nav icon renders `"₱"`; `+ Log Expense` uses a circular `58.dp` elevated FAB.
- Gas Price Watcher UI header displays `"Petron Fairview"` / `"Premium 95"` with no street address and no source URL, calculates `Honda Click 160` (`5.5L`) full tank, and shows weekly trend diff (`▲ +₱X.XX` / `▼ -₱X.XX`).

## Review Focus

1. **Short-month due-day clamping:** `dueDayOfMonth = 31` in 28/29/30-day months (`2026-02`, `2026-11`) must clamp to `YearMonth.atEndOfMonth()` without throwing `DateTimeException` (tested in Task 2).
2. **Pre-reserved loan settlement math:** Settling or reversing a `LoanEntity` shifts `monthlyAmortizationCentavos` between `CommittedUnpaidLoans` and `Spent` without double-subtracting `SafeToSpend` (tested in Task 2 and Task 3).
3. **GasWatchPH scraper resilience:** Null `prices.premium95`, missing station `621`, malformed HTML, or network failure preserves cached `GasPriceHistoryEntity` and returns `Result.retry()` without writing `0L` (tested in Task 4).
4. **Atomic 5-second undo reversal:** Undoing an expense, bill payment, or loan payment deletes the `TransactionEntity` and restores envelope `spentCentavos`, bill `isPaid`, and loan `paidInstallments`/`nextDueYearMonth`/`isActive` atomically (tested in Task 3).
5. **Safe SOA PDF replacement:** Replacing a cycle's SOA PDF copies the new stream to `filesDir/soa/` before deleting the previous PDF file, leaving the old file intact on copy failure (tested in Task 5).

---

## File Structure

```text
app/
├── build.gradle.kts
├── gradle/libs.versions.toml
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── java/com/mcalonzo/personalfinanceos/
    │       ├── FinanceOsApplication.kt
    │       ├── MainActivity.kt
    │       ├── data/local/
    │       │   ├── AppDatabase.kt
    │       │   ├── SeedDatabaseCallback.kt
    │       │   ├── SoaStorageManager.kt
    │       │   ├── entity/ (11 Entity files matching Spec §5 & §8)
    │       │   └── dao/ (RoutineDao, BudgetDao, ObligationDao, MotoDao)
    │       ├── data/remote/
    │       │   └── GasWatchParser.kt
    │       ├── data/repository/
    │       │   ├── BudgetRepository.kt
    │       │   └── GasPriceRepository.kt
    │       ├── di/AppModule.kt
    │       ├── domain/
    │       │   ├── calculator/ (BudgetCalculator, DueDateCalculator, MotoCalculator)
    │       │   ├── model/ (CycleOverview, BillWithStatus, UndoAction, GasTrendState)
    │       │   └── usecase/MonthRolloverUseCase.kt
    │       ├── worker/
    │       │   ├── MonthRolloverWorker.kt
    │       │   ├── GasPriceSyncWorker.kt
    │       │   ├── DueReminderWorker.kt
    │       │   ├── NotificationHelper.kt
    │       │   ├── NotificationActionReceiver.kt
    │       │   ├── BootCompletedReceiver.kt
    │       │   └── WorkScheduler.kt
    │       └── ui/
    │           ├── theme/ (Color.kt, Type.kt, Theme.kt)
    │           ├── viewmodel/MainViewModel.kt
    │           ├── components/ (PdfViewerSheet.kt, IconPickerSheet.kt, LogExpenseSheet.kt, RoutineSheet.kt, ReminderCadenceSheet.kt)
    │           └── screens/ (MainScreen.kt, BudgetScreen.kt, RemindersScreen.kt, MotoCareScreen.kt)
    └── test/java/com/mcalonzo/personalfinanceos/
        ├── data/local/SeedDatabaseTest.kt
        ├── data/local/SoaStorageManagerTest.kt
        ├── data/remote/GasWatchParserTest.kt
        ├── data/repository/BudgetRepositoryTest.kt
        ├── domain/calculator/CalculatorsTest.kt
        ├── worker/DueReminderWorkerTest.kt
        ├── worker/WorkSchedulerTest.kt
        └── ui/ (BudgetScreenTest.kt, MotoAndRemindersScreenTest.kt)
```

---

### Task 1: Project Scaffolding, Design System Tokens & Room Database Schema + Pre-Seed Callback

**Files:**
- Create: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/mcalonzo/personalfinanceos/FinanceOsApplication.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/theme/Color.kt`, `Type.kt`, `Theme.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/entity/*.kt` (11 `@Entity` data classes from Spec §5 + §8)
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/dao/*.kt` (`RoutineDao`, `BudgetDao`, `ObligationDao`, `MotoDao`)
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/AppDatabase.kt`, `SeedDatabaseCallback.kt`, `di/AppModule.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/local/SeedDatabaseTest.kt`

**Interfaces:**
- Consumes: None (foundation layer).
- Produces:
  - `AppDatabase` (`routineDao()`, `budgetDao()`, `obligationDao()`, `motoDao()`)
  - All 11 Room entities using `Long` centavos (including `RoutineEnvelopeTemplateEntity.isGasEnvelope`, `EnvelopeEntity.isGasEnvelope`, `RoutineBillTemplateEntity`, `LoanEntity.nextDueYearMonth`)
  - `OLEDColors`, `PersonalFinanceTheme`, `PlusJakartaSansFamily`, `JetBrainsMonoFamily`

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/com/mcalonzo/personalfinanceos/data/local/SeedDatabaseTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
class SeedDatabaseTest {
    @Test
    fun `fresh database seeds routine, gas envelope tag, click 160 profile, and petron fairview baseline`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback { db })
            .allowMainThreadQueries()
            .build()

        val routine = db.routineDao().getRoutineTemplateOnce()
        val envelopes = db.routineDao().getEnvelopeTemplatesOnce()
        val moto = db.motoDao().getMotoProfileOnce()
        val latestGas = db.motoDao().getLatestGasPriceOnce()

        assertThat(routine?.totalMonthlyBudgetCentavos).isEqualTo(5_000_000L)
        assertThat(envelopes.count { it.isGasEnvelope }).isEqualTo(1)
        assertThat(moto?.modelName).isEqualTo("Honda Click 160")
        assertThat(moto?.tankCapacityLiters).isEqualTo(5.5)
        assertThat(latestGas?.stationId).isEqualTo(621)
        assertThat(latestGas?.stationDisplayName).isEqualTo("Petron Fairview")
        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.SeedDatabaseTest"`
Expected: FAIL (Gradle project / `AppDatabase` not yet created).

- [ ] **Step 3: Scaffold Android Gradle project, Theme tokens, Room Entities, DAOs, and `SeedDatabaseCallback`**

1. Create `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts` (`minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`), and `AndroidManifest.xml` (permissions: `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`).
2. Create `Color.kt`, `Type.kt` (setting `fontFeatureSettings = "tnum"` on numeric styles), and `Theme.kt` matching Spec §3 hex tokens exactly.
3. Implement all 11 Room `@Entity` classes from Spec §5 + §8, `RoutineDao`, `BudgetDao`, `ObligationDao`, `MotoDao`, `AppDatabase`, `SeedDatabaseCallback` (seeding `₱50,000` baseline routine, 5 envelope templates with `"Motorcycle Gas"` flagged `isGasEnvelope = true`, sample recurring bills & loans, `Honda Click 160` profile at `8450 km` / `5.5L`, 3 PMS intervals, and `Petron Fairview` initial `7840L` history record), and Hilt `AppModule`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.SeedDatabaseTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gradle/ build.gradle.kts app/
git commit -m "feat: scaffold Android project, OLED theme tokens, and Room database with seed data"
```

---

### Task 2: Domain Calculators — Safe-to-Spend, Daily Pacing, Due-Date Clamping & PMS Progress

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/calculator/BudgetCalculator.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/calculator/DueDateCalculator.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/calculator/MotoCalculator.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/domain/calculator/CalculatorsTest.kt`

**Interfaces:**
- Consumes: Standard `java.time.YearMonth` and `java.time.LocalDate` (`Asia/Manila`).
- Produces:
  - `BudgetCalculator.calculateSafeToSpend(totalBudgetCentavos: Long, variableAndBillSpentCentavos: Long, committedUnpaidLoansCentavos: Long): Long`
  - `BudgetCalculator.calculateDailyPace(safeToSpendCentavos: Long, today: LocalDate, cycleMonth: YearMonth): Long`
  - `BudgetCalculator.formatPeso(centavos: Long, showDecimal: Boolean = true): String`
  - `DueDateCalculator.resolveDueDate(yearMonth: YearMonth, dueDayOfMonth: Int): LocalDate`
  - `DueDateCalculator.daysUntilDue(yearMonth: YearMonth, dueDayOfMonth: Int, today: LocalDate): Long`
  - `DueDateCalculator.shouldNotify(daysUntilDue: Long, notify7d: Boolean, notify3d: Boolean, notify1d: Boolean, notifyOnDue: Boolean, notifyDailyOverdue: Boolean): Boolean`
  - `MotoCalculator.calculateFullTankCentavos(pricePerLiterCentavos: Long, tankLiters: Double = 5.5): Long`
  - `MotoCalculator.calculatePmsRemainingKm(currentOdometerKm: Int, intervalKm: Int, lastServiceOdometerKm: Int): Int`
  - `MotoCalculator.calculatePriceDiff(newPriceCentavos: Long, previousPriceCentavos: Long?): Long`

- [ ] **Step 1: Write the failing test**

In `CalculatorsTest.kt` (covering Review Focus #1 and #2):
```kotlin
class CalculatorsTest {
    @Test
    fun `safe to spend reserves unpaid loans upfront and keeps balance unchanged when loan settles`() {
        val beforePay = BudgetCalculator.calculateSafeToSpend(5_000_000L, 2_300_000L, 835_000L)
        assertThat(beforePay).isEqualTo(1_865_000L) // ₱18,650.00
        // Loan ₱4,850 settled -> moves from committed loans to spent; Safe-to-Spend remains ₱18,650.00
        val afterPay = BudgetCalculator.calculateSafeToSpend(5_000_000L, 2_785_000L, 350_000L)
        assertThat(afterPay).isEqualTo(1_865_000L)
    }

    @Test
    fun `dueDayOfMonth 31 clamps to end of month in February and November without throwing`() {
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2026, 2), 31)).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2028, 2), 31)).isEqualTo(LocalDate.of(2028, 2, 29))
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2026, 11), 31)).isEqualTo(LocalDate.of(2026, 11, 30))
    }

    @Test
    fun `notification triggers only on configured lead days or overdue`() {
        assertThat(DueDateCalculator.shouldNotify(3L, true, true, true, true, true)).isTrue()
        assertThat(DueDateCalculator.shouldNotify(5L, true, true, true, true, true)).isFalse()
        assertThat(DueDateCalculator.shouldNotify(-2L, false, false, false, false, true)).isTrue()
    }

    @Test
    fun `honda click 160 full tank and weekly price diff calculate accurately`() {
        assertThat(MotoCalculator.calculateFullTankCentavos(7840L, 5.5)).isEqualTo(43120L) // ₱431.20
        assertThat(MotoCalculator.calculatePriceDiff(7840L, 7795L)).isEqualTo(45L) // +₱0.45
        assertThat(MotoCalculator.calculatePriceDiff(7690L, 7840L)).isEqualTo(-150L) // -₱1.50
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.domain.calculator.CalculatorsTest"`
Expected: FAIL (`Unresolved reference: BudgetCalculator`).

- [ ] **Step 3: Implement `BudgetCalculator`, `DueDateCalculator`, and `MotoCalculator`**

Implement deterministic pure Kotlin objects with `Locale("en", "PH")` Peso formatting, `YearMonth.atEndOfMonth()` clamping (`min(dueDayOfMonth, yearMonth.lengthOfMonth())`), `ChronoUnit.DAYS.between`, and `BigDecimal` multiplication for `5.5 * pricePerLiterCentavos`. For past months in `calculateDailyPace`, return `0L`; for current month, divide by remaining days inclusive (`lengthOfMonth - today.dayOfMonth + 1`).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.domain.calculator.CalculatorsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/domain/calculator/ app/src/test/java/com/mcalonzo/personalfinanceos/domain/calculator/
git commit -m "feat: add budget, due-date clamping, and Honda Click 160 fuel calculators"
```

---

### Task 3: Budget & Routine Repository — Expense Logging, Bill/Loan Settlement, 5s Undo & Month Rollover

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/model/CycleOverview.kt`, `UndoAction.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/domain/usecase/MonthRolloverUseCase.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepository.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/MonthRolloverWorker.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepositoryTest.kt`

**Interfaces:**
- Consumes: `AppDatabase` DAOs (`RoutineDao`, `BudgetDao`, `ObligationDao`), `BudgetCalculator`, `DueDateCalculator`.
- Produces:
  - `MonthRolloverUseCase.invoke(targetMonth: YearMonth): Long`
  - `BudgetRepository.observeActiveCycleOverview(yearMonth: YearMonth): Flow<CycleOverview>`
  - `BudgetRepository.logExpense(cycleId: Long, envelopeId: Long, amountCentavos: Long, title: String): UndoAction`
  - `BudgetRepository.logGasFullTank(cycleId: Long, amountCentavos: Long, label: String): UndoAction` (resolves `isGasEnvelope == true` or falls back cleanly)
  - `BudgetRepository.settleBill(billId: Long, overrideAmountCentavos: Long? = null): UndoAction`
  - `BudgetRepository.settleLoan(loanId: Long, cycleId: Long): UndoAction`
  - `BudgetRepository.undoAction(action: UndoAction)`
  - `BudgetRepository.deleteTransactionAndRevert(transactionId: Long)`
  - `BudgetRepository.addEnvelope(cycleId: Long, name: String, iconEmoji: String, limitCentavos: Long, colorHex: String, addToRoutine: Boolean)`
  - `BudgetRepository.updateRoutineTemplate(...)`

- [ ] **Step 1: Write the failing test**

In `BudgetRepositoryTest.kt` (covering Review Focus #2 and #4):
```kotlin
@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryTest {
    @Test
    fun `undoing expense, bill settlement, and loan installment atomically restores prior state`() = runTest {
        // 1. Roll over 2026-11
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val initial = repo.observeActiveCycleOverview(YearMonth.of(2026, 11)).first()
        val initialSafe = initial.safeToSpendCentavos

        // 2. Log variable expense & undo
        val gasUndo = repo.logGasFullTank(cycleId, 43120L, "Petron Fairview Full Tank")
        val afterGas = repo.observeActiveCycleOverview(YearMonth.of(2026, 11)).first()
        assertThat(afterGas.safeToSpendCentavos).isEqualTo(initialSafe - 43120L)
        repo.undoAction(gasUndo)
        assertThat(repo.observeActiveCycleOverview(YearMonth.of(2026, 11)).first().safeToSpendCentavos).isEqualTo(initialSafe)

        // 3. Settle final loan installment -> paidInstallments reaches total, isActive becomes false -> undo restores active & prev cycle
        val loanId = db.obligationDao().insertLoan(LoanEntity(name = "Short Loan", iconEmoji = "🏦", monthlyAmortizationCentavos = 100_000L, totalInstallments = 1, paidInstallments = 0, dueDayOfMonth = 20, nextDueYearMonth = "2026-11"))
        val loanUndo = repo.settleLoan(loanId, cycleId)
        val settledLoan = db.obligationDao().getLoanById(loanId)!!
        assertThat(settledLoan.paidInstallments).isEqualTo(1)
        assertThat(settledLoan.isActive).isFalse()

        repo.undoAction(loanUndo)
        val revertedLoan = db.obligationDao().getLoanById(loanId)!!
        assertThat(revertedLoan.paidInstallments).isEqualTo(0)
        assertThat(revertedLoan.isActive).isTrue()
        assertThat(revertedLoan.nextDueYearMonth).isEqualTo("2026-11")
    }

    @Test
    fun `rollover creates only target month cycle and clones routine envelopes and bills without backfilling skipped months`() = runTest {
        rolloverUseCase(YearMonth.of(2026, 11))
        val decCycleId = rolloverUseCase(YearMonth.of(2027, 2)) // skipped Dec & Jan
        val cycles = db.budgetDao().getAllCyclesOnce()
        assertThat(cycles.map { it.yearMonth }).containsExactly("2026-11", "2027-02")
        assertThat(db.budgetDao().getEnvelopesForCycleOnce(decCycleId).count { it.isGasEnvelope }).isEqualTo(1)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.repository.BudgetRepositoryTest"`
Expected: FAIL (`Unresolved reference: BudgetRepository`).

- [ ] **Step 3: Implement `MonthRolloverUseCase`, `BudgetRepository`, and `MonthRolloverWorker`**

Implement `@Transaction`-wrapped Room operations in `BudgetRepository`. Note: `settleBill` updates `BillEntity.amountCentavos` if `overrideAmountCentavos != null` (supporting variable utility bills per BC-4). `settleLoan` advances `nextDueYearMonth` by 1 month (`YearMonth.parse(nextDueYearMonth).plusMonths(1)`), sets `lastPaidCycleYearMonth`, increments `paidInstallments`, and flips `isActive = paidInstallments + 1 < totalInstallments`. `deleteTransactionAndRevert` reads the `TransactionEntity` and reverses whichever entity (`envelopeId`, `billId`, `loanId`) originated it.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.repository.BudgetRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/domain/ app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepository.kt app/src/main/java/com/mcalonzo/personalfinanceos/worker/MonthRolloverWorker.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/repository/
git commit -m "feat: add BudgetRepository with atomic undo, loan completion, and month rollover"
```

---

### Task 4: GasWatchPH Scraper Parser & `GasPriceSyncWorker` (Saturday 12:00 PM Schedule)

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/remote/GasWatchParser.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/GasPriceRepository.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/GasPriceSyncWorker.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/WorkScheduler.kt`
- Create: `app/src/test/resources/gaswatch_quezon_city_fixture.html` (fixture containing station `621` inline `<script>` JSON matching verified structure)
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/remote/GasWatchParserTest.kt`, `app/src/test/java/com/mcalonzo/personalfinanceos/worker/WorkSchedulerTest.kt`

**Interfaces:**
- Consumes: `MotoDao`, `MotoCalculator`, `OkHttpClient`.
- Produces:
  - `GasWatchParser.parseStationPremium95(html: String, targetStationId: Int = 621): ParsedGasPrice?`
  - `GasPriceRepository.observeLatestPrice(): Flow<GasPriceHistoryEntity?>`
  - `GasPriceRepository.syncFromRemote(): Result<GasPriceHistoryEntity>`
  - `GasPriceRepository.setManualPriceOverride(priceCentavos: Long)`
  - `WorkScheduler.nextSaturdayNoonDelay(now: ZonedDateTime): Duration`
  - `WorkScheduler.enqueueWeeklyGasWatcher(workManager: WorkManager)`
  - `WorkScheduler.enqueueColdStartGasSync(workManager: WorkManager)`

- [ ] **Step 1: Write the failing tests**

In `GasWatchParserTest.kt` & `WorkSchedulerTest.kt` (covering Review Focus #3):
```kotlin
class GasWatchParserTest {
    @Test
    fun `parses Petron Commonwealth Ave Fairview id 621 premium95 price cleanly`() {
        val fixture = javaClass.classLoader!!.getResource("gaswatch_quezon_city_fixture.html")!!.readText()
        val parsed = GasWatchParser.parseStationPremium95(fixture, targetStationId = 621)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.stationName).isEqualTo("Petron Commonwealth Ave Fairview")
        assertThat(parsed.premium95Centavos).isEqualTo(7840L)
    }

    @Test
    fun `returns null when premium95 is null or station 621 missing without throwing`() {
        val nullFuelHtml = """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":null}}</script>"""
        assertThat(GasWatchParser.parseStationPremium95(nullFuelHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95("<html><body>blocked</body></html>", 621)).isNull()
    }
}

class WorkSchedulerTest {
    @Test
    fun `calculates initial delay to upcoming Saturday 12pm Asia Manila`() {
        val manila = ZoneId.of("Asia/Manila")
        val fridayMorning = ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(fridayMorning)).isEqualTo(Duration.ofHours(27))

        val saturdayAfternoon = ZonedDateTime.of(2026, 11, 14, 15, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(saturdayAfternoon)).isEqualTo(Duration.ofDays(7).minusHours(3))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.remote.GasWatchParserTest" --tests "com.mcalonzo.personalfinanceos.worker.WorkSchedulerTest"`
Expected: FAIL (`Unresolved reference: GasWatchParser`).

- [ ] **Step 3: Implement `GasWatchParser`, `GasPriceRepository`, `GasPriceSyncWorker`, and `WorkScheduler`**

In `GasWatchParser`, parse the station object by locating `"id": 621` (with optional whitespace/escaped quotes) and extracting `"prices": { ... "premium95": <number> ... }` via regex / `org.json.JSONObject`, rejecting `null` or `<= 0.0` prices. In `GasPriceRepository.syncFromRemote()`, wrap network & parse calls safely: only insert a new `GasPriceHistoryEntity` when valid, computing `diffCentavos = newCentavos - latestCentavos`; on failure return `Result.failure(...)` so existing cached history stays untouched, and map failures inside `GasPriceSyncWorker` to `ListenableWorker.Result.retry()`. Configure `PeriodicWorkRequestBuilder<GasPriceSyncWorker>(7, TimeUnit.DAYS)` with `setInitialDelay(nextSaturdayNoonDelay)` and `NetworkType.CONNECTED`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.remote.GasWatchParserTest" --tests "com.mcalonzo.personalfinanceos.worker.WorkSchedulerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/remote/ app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/GasPriceRepository.kt app/src/main/java/com/mcalonzo/personalfinanceos/worker/ app/src/test/
git commit -m "feat: add GasWatchPH station 621 parser, Saturday noon worker, and manual override repo"
```

---

### Task 5: Local SOA PDF Storage Manager & Native `PdfRenderer` Viewer Sheet

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/SoaStorageManager.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/PdfViewerSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepository.kt` (add `attachSoaPdf(cycleId, yearMonth, uri)`)
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/local/SoaStorageManagerTest.kt`

**Interfaces:**
- Consumes: `Context.contentResolver`, `BudgetDao`, `android.graphics.pdf.PdfRenderer`.
- Produces:
  - `SoaStorageManager.saveOrReplaceSoa(yearMonth: String, inputStream: InputStream, originalFileName: String, previousLocalPath: String?): StoredSoaFile`
  - `PdfViewerSheet(localPath: String?, fileName: String?, onUploadOrReplaceClick: () -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Write the failing test**

In `SoaStorageManagerTest.kt` (covering Review Focus #5):
```kotlin
class SoaStorageManagerTest {
    @Rule @JvmField val tempFolder = TemporaryFolder()

    @Test
    fun `replacing SOA PDF copies new file first and deletes old file only after write succeeds`() {
        val manager = SoaStorageManager(tempFolder.root)
        val first = manager.saveOrReplaceSoa("2026-11", "%PDF-1.4 old".byteInputStream(), "BPI_Oct.pdf", null)
        assertThat(File(first.localPath).exists()).isTrue()

        val failingStream = object : InputStream() {
            override fun read(): Int = throw IOException("Disk read failed")
        }
        assertThrows(IOException::class.java) {
            manager.saveOrReplaceSoa("2026-11", failingStream, "Broken.pdf", first.localPath)
        }
        assertThat(File(first.localPath).exists()).isTrue() // Old file preserved on failure

        val second = manager.saveOrReplaceSoa("2026-11", "%PDF-1.4 new".byteInputStream(), "BPI_Nov.pdf", first.localPath)
        assertThat(File(second.localPath).exists()).isTrue()
        assertThat(File(first.localPath).exists()).isFalse() // Old file cleaned up after success
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.SoaStorageManagerTest"`
Expected: FAIL (`Unresolved reference: SoaStorageManager`).

- [ ] **Step 3: Implement `SoaStorageManager` and `PdfViewerSheet`**

Write stream to a temporary `.tmp` file under `filesDir/soa/`, rename to `<yearMonth>_<uuid>.pdf` upon successful flush, then delete `previousLocalPath` only after rename succeeds (cleaning up `.tmp` on exception). In `PdfViewerSheet`, open `ParcelFileDescriptor` in a `DisposableEffect` and render pages with `PdfRenderer.Page` scaled onto dark-framed white sheet cards with pinch-to-zoom (`detectTransformGestures`), page counter, and `"Upload SOA PDF"` / `"Replace File"` button (`minHeight = 48.dp`).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.SoaStorageManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/local/SoaStorageManager.kt app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/BudgetRepository.kt app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/PdfViewerSheet.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/local/SoaStorageManagerTest.kt
git commit -m "feat: add internal SOA PDF replacement manager and native PdfRenderer viewer"
```

---

### Task 6: Due-Date Notification Engine (`DueReminderWorker` & `NotificationActionReceiver`)

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/NotificationHelper.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/DueReminderWorker.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/NotificationActionReceiver.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/BootCompletedReceiver.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/worker/WorkScheduler.kt` (add `enqueueDailyDueReminders` and `scheduleSnoozeReminder`)
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/worker/DueReminderWorkerTest.kt`

**Interfaces:**
- Consumes: `ObligationDao`, `BudgetDao`, `DueDateCalculator`, `BudgetRepository`.
- Produces:
  - `NotificationHelper.showDueAlert(context: Context, itemKey: String, notificationId: Int, title: String, body: String, entityType: String, entityId: Long, amountCentavos: Long)`
  - `DueReminderWorker` (`INPUT_ITEM_KEY` optional for snoozed 1h single-item trigger)
  - `NotificationActionReceiver` (`ACTION_PAY_NOW`, `ACTION_SNOOZE_1H`, extras: `EXTRA_ENTITY_TYPE`, `EXTRA_ENTITY_ID`, `EXTRA_NOTIFICATION_ID`)
  - `WorkScheduler.enqueueDailyDueReminders(workManager: WorkManager)`

- [ ] **Step 1: Write the failing test**

In `DueReminderWorkerTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
class DueReminderWorkerTest {
    @Test
    fun `broadcast ACTION_PAY_NOW settles due bill, logs transaction, and cancels notification`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationId = 1001
        val billId = repo.insertUnpaidTestBill(cycleId, name = "Converge FiberX", amountCentavos = 150_000L, dueDay = 11)
        NotificationHelper.showDueAlert(context, "bill_$billId", notificationId, "Bill Due", "₱1,500.00", "bill", billId, 150_000L)

        val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_NOW
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_TYPE, "bill")
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, billId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        NotificationActionReceiver().onReceive(context, payIntent)
        advanceUntilIdle()

        val bill = db.obligationDao().getBillById(billId)!!
        assertThat(bill.isPaid).isTrue()
        assertThat(db.budgetDao().getTransactionsForCycleOnce(cycleId)).hasSize(1)
        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications.none { it.id == notificationId }).isTrue()
    }

    @Test
    fun `evaluates unpaid bills and cross-month overdue loans for due notifications`() = runTest {
        // Seed 1 overdue unpaid bill (dueDay = 11, today = Nov 14) and 1 paid loan -> run DueReminderWorker.doWork()
        val result = TestListenableWorkerBuilder<DueReminderWorker>(context).build().doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications).hasLength(1)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.worker.DueReminderWorkerTest"`
Expected: FAIL (`Unresolved reference: DueReminderWorker`).

- [ ] **Step 3: Implement Notification channel helper, `DueReminderWorker`, `NotificationActionReceiver`, and `BootCompletedReceiver`**

Create `due_date_alerts` channel (`IMPORTANCE_HIGH`). In `NotificationActionReceiver`, call `goAsync()` and run `budgetRepository.settleBill(id)` or `budgetRepository.settleLoan(id, activeCycleId)` inside a `CoroutineScope(Dispatchers.IO)` before canceling notification ID and calling `pendingResult.finish()`. Register both receivers in `AndroidManifest.xml`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.worker.DueReminderWorkerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mcalonzo/personalfinanceos/worker/ app/src/test/java/com/mcalonzo/personalfinanceos/worker/
git commit -m "feat: add due-date push notification worker and 1-tap Pay/Snooze action receiver"
```

---

### Task 7: Jetpack Compose UI — Budget Screen, 4-Column Tabs, Modals & Main Navigation Shell

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModel.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MainScreen.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/BudgetScreen.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/LogExpenseSheet.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/RoutineSheet.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/IconPickerSheet.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/BillPaymentAndCrudSheets.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/MainActivity.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ui/BudgetScreenTest.kt`

**Interfaces:**
- Consumes: `BudgetRepository`, `GasPriceRepository`, `SoaStorageManager`, `BudgetCalculator`, `PersonalFinanceTheme`.
- Produces:
  - `MainUiState` (`budgetState: BudgetUiState`, `selectedBottomTab: BottomTab`, `activeUndo: UndoAction?`), `BudgetUiState` (`yearMonthLabel: String`, `safeToSpendFormatted: String`, `dailyPaceFormatted: String`, `totalBudgetFormatted: String`, `spentSoFarFormatted: String`, `committedLoansFormatted: String`, `overdueBillsCount: Int`, `envelopes: List<EnvelopeItemUi>`, `bills: List<BillItemUi>`, `loans: List<LoanItemUi>`, `transactions: List<TransactionItemUi>`, `selectedSubTab: BudgetSubTab`)
  - `MainViewModel` (`uiState: StateFlow<MainUiState>`, actions: `selectMonth`, `switchBottomTab`, `switchBudgetSubTab`, `logExpense`, `settleBill`, `settleLoan`, `undoLast`, `addEnvelope`, `updateEnvelopeIcon`, `saveRoutine`)
  - Stateless `MainScreen(state: MainUiState, ...)` and `BudgetScreen(state: BudgetUiState, ...)`

- [ ] **Step 1: Write the failing UI test**

In `BudgetScreenTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
class BudgetScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `budget screen renders safe to spend hero without dot, 4 equal tabs without top routine button, and ₱ nav glyph`() {
        val sampleState = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = "November 2026",
                safeToSpendFormatted = "₱18,650.00",
                dailyPaceFormatted = "₱1,097 / day",
                overdueBillsCount = 1,
                envelopes = listOf(EnvelopeItemUi(1L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 880_000L))
            )
        )
        rule.setContent { PersonalFinanceTheme { MainScreen(state = sampleState, onSelectBottomTab = {}, onOpenExpenseSheet = {}, onOpenRoutineSheet = {}) } }
        rule.onNodeWithText("SAFE-TO-SPEND BALANCE").assertIsDisplayed()
        rule.onNodeWithText("ENVELOPES").assertIsDisplayed()
        rule.onNodeWithText("Envelopes").assertIsDisplayed()
        rule.onNodeWithText("Bills").assertIsDisplayed()
        rule.onNodeWithText("Loans").assertIsDisplayed()
        rule.onNodeWithText("Activity").assertIsDisplayed()
        rule.onNodeWithText("1!").assertIsDisplayed()
        rule.onNodeWithText("₱").assertIsDisplayed()
        rule.onAllNodesWithText("Routine").assertCountEquals(1) // bottom nav bar only
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.BudgetScreenTest"`
Expected: FAIL (`Unresolved reference: MainScreen`).

- [ ] **Step 3: Implement `MainViewModel`, `MainScreen`, `BudgetScreen`, and interactive Bottom Sheets**

Enforce Spec §3 & §8 rules:
- `SnackbarHost` displays 5-second Undo button after logging expenses, settling bills, or settling loans (`SnackbarDuration.Short` / custom 5s timer).
- `Bills` tab renders recurring bills with overdue badge (`Overdue by N days`), `Pay Now` opens amount confirmation sheet for variable amounts (like Meralco), plus add/edit/delete bill options.
- `Envelopes` lets user tap any emoji box to open `IconPickerSheet`, tap `+` to pre-fill category in `LogExpenseSheet`, or tap `+ Add Category` to create an envelope with custom emoji.
- `Routine` bottom bar opens `RoutineSheet` with `"Also apply to current month"` toggle.
- `Activity` allows deleting a logged transaction with balance reversal.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.BudgetScreenTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/MainActivity.kt app/src/main/java/com/mcalonzo/personalfinanceos/ui/ app/src/test/java/com/mcalonzo/personalfinanceos/ui/BudgetScreenTest.kt
git commit -m "feat: implement Budget Screen UI, 4-column grid tabs, FAB, and bottom sheets"
```

---

### Task 8: Jetpack Compose UI — Reminders Hub & Moto Care Screen (Honda Click 160 & Petron Fairview Trend)

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/RemindersScreen.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MotoCareScreen.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/ReminderCadenceSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModel.kt` (wire `updateOdometer`, `logPmsService` with optional cost expense, `logClick160FullTank`, `triggerManualGasSync`, `overrideGasPrice`, `updateReminderCadence`, `previewDueNotification`)
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MainScreen.kt` (render `RemindersScreen` & `MotoCareScreen`)
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ui/MotoAndRemindersScreenTest.kt`

**Interfaces:**
- Consumes: `MainViewModel`, `MotoCalculator`, `BudgetCalculator`, `DueDateCalculator`, `NotificationHelper`.
- Produces:
  - `MotoUiState` (`modelName: String`, `odometerKm: Int`, `tankCapacityLiters: Double`, `stationDisplayName: String`, `fuelType: String`, `pricePerLiterFormatted: String`, `prevPriceFormatted: String`, `trendBadgeText: String`, `isPriceUp: Boolean`, `fullTankCostFormatted: String`, `pmsItems: List<PmsItemUi>`)
  - `RemindersUiState` (`globalPushEnabled: Boolean`, `items: List<DueReminderItemUi>`)
  - `MotoCareScreen(state: MotoUiState, ...)` and `RemindersScreen(state: RemindersUiState, ...)`

- [ ] **Step 1: Write the failing UI test**

In `MotoAndRemindersScreenTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
class MotoAndRemindersScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `moto care displays Honda Click 160, Petron Fairview without address or source, and weekly trend diff`() {
        val motoState = MotoUiState(
            modelName = "Honda Click 160",
            odometerKm = 8450,
            tankCapacityLiters = 5.5,
            stationDisplayName = "Petron Fairview",
            fuelType = "Premium 95",
            pricePerLiterFormatted = "₱78.40",
            prevPriceFormatted = "Prev: ₱77.95/L (+₱0.45)",
            trendBadgeText = "▲ +₱0.45",
            isPriceUp = true,
            fullTankCostFormatted = "₱431.20",
            pmsItems = emptyList()
        )
        rule.setContent { PersonalFinanceTheme { MotoCareScreen(state = motoState, onLogFullTank = {}, onRefreshGas = {}, onEditOdometer = {}, onOverridePrice = {}, onAddPms = {}) } }
        rule.onNodeWithText("Honda Click 160").assertIsDisplayed()
        rule.onNodeWithText("Petron Fairview").assertIsDisplayed()
        rule.onNodeWithText("Premium 95").assertIsDisplayed()
        rule.onNodeWithText("▲ +₱0.45").assertIsDisplayed()
        rule.onNodeWithText("₱431.20").assertIsDisplayed()
        rule.onNodeWithText("Commonwealth Ave", substring = true).assertDoesNotExist()
        rule.onNodeWithText("gaswatchph", substring = true).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.MotoAndRemindersScreenTest"`
Expected: FAIL (`Unresolved reference: MotoCareScreen`).

- [ ] **Step 3: Implement `RemindersScreen`, `MotoCareScreen`, `ReminderCadenceSheet`, and wire ViewModel**

In `MotoCareScreen`: display `Honda Click 160` hero card with tappable odometer badge (`updateOdometer`), `Petron Fairview` / `Premium 95` gas card with trend diff pill (`▲ +₱X.XX` in Rose `#F43F5E` or `▼ -₱X.XX` in Emerald `#10B981`), tappable price for manual override (`overrideGasPrice`), pull-to-refresh sync (`triggerManualGasSync`), `Log Full Tank` CTA (`logClick160FullTank`), and PMS schedule cards with `+ Log PMS` sheet supporting optional cost transaction logging. In `RemindersScreen`: render global push toggle, chronological overdue/upcoming timeline sorted by due date, `Preview Alert` test push button, and `ReminderCadenceSheet` per bill/loan (`7d`, `3d`, `1d`, `0d`, `dailyOverdue`).

- [ ] **Step 4: Run full unit test suite to verify everything passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS (all unit and Compose Robolectric tests pass cleanly).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/ app/src/test/java/com/mcalonzo/personalfinanceos/
git commit -m "feat: add Reminders hub, Moto Care Honda Click 160 screen, and Petron Fairview watcher UI"
```

---

## Self-Review Results

- **Spec coverage:** Every requirement in Spec Sections 1–8 (`Budget`, `SOA PDF`, `Baseline Routine`, `Bills & Subscriptions`, `Active Loans`, `Due-Date Notifications`, `Moto Care`, `GasWatchPH` scraper, and all 7 `BC-*` rules) maps explicitly to Tasks 1–8.
- **Type & signature consistency:** Entity column names (`isGasEnvelope`, `nextDueYearMonth`, `pricePerLiterCentavos`, `previousPricePerLiterCentavos`, `diffCentavos`), calculator signatures, repository methods, and worker classes use identical spelling across all tasks.
- **Review Focus coverage:** All 5 edge cases have dedicated assertions in `CalculatorsTest`, `BudgetRepositoryTest`, `GasWatchParserTest`, and `SoaStorageManagerTest`.
