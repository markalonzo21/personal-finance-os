# Personal Finance OS — Design Specification

**Target Platform:** Native Android (Android 15 / Material 3)  
**Currency:** Philippine Peso (`₱`) strictly  
**Primary Mockup Reference:** [budget-uipro-v13.html](file:///C:/Users/mcalonzo/.superpowers/brainstorm/session-1/content/budget-uipro-v13.html)  
**Date:** 2026-11-14  

---

## 1. Intent & Why

Personal Finance OS is a fast, offline-first native Android daily-driver application built for disciplined personal cashflow management in Philippine Peso (`₱`). It integrates three cohesive daily workflows into a single executive experience:

1. **Budget Core:** Monthly safe-to-spend balance with live daily pacing, flexible category envelopes, monthly baseline routine rollover, embedded Statement of Account (SOA) PDF storage, and one-tap expense logging.
2. **Reminders & Obligations:** Active loans and recurring bills/subscriptions management with late-due alerts, configurable due-date push notification offsets, and interactive one-tap settlement directly from Android notifications.
3. **Moto Care & Gas Price Watcher:** Preventive Maintenance Schedule (PMS) and odometer tracker tailored for **Honda Click 160** (5.5L tank), paired with an automated weekly Saturday noon fuel price scraper targeting **Petron Commonwealth Ave Fairview** (Premium 95) from `gaswatchph.com` with weekly hike/rollback trend diff indicators.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Executive Hero Clarity:** Single "Safe-to-Spend" balance card (`Total Budget - Variable & Bill Spent - Committed Unpaid Loans`) paired with real-time daily spend pacing (`Safe-to-Spend / Days Remaining in Month`).
- **Zero-Recreation Monthly Reset:** Editable **Monthly Baseline Routine** template that automatically clones into a new monthly cycle on the 1st of each month without manual re-entry.
- **Native SOA PDF Archival:** Attach a local Statement of Account (SOA) PDF file per monthly cycle (with Replace File support) and revisit it anytime via native in-app `PdfRenderer` rendering.
- **Bills, Subscriptions & Active Loans:** Track recurring monthly bills (Converge FiberX, Meralco, Netflix, Spotify) and active installment loans (e.g., vehicle/personal bank loans) with 1-tap settlement, 5-second undo protection, overdue day badges (`Overdue by X days`), and configurable push notification alerts (7d, 3d, 1d before, on due date at 9:00 AM, and daily repeat if overdue).
- **Dynamic Category Customization:** Support adding new budget envelopes with user-selectable emoji/icons (no fixed/hardcoded icons).
- **Honda Click 160 Moto Care:** Track current odometer mileage, PMS intervals (Honda 4T SL 10W-30 Engine Oil, CVT Cleaning & Belt Inspection, Final Gear Oil), and auto-calculate Honda Click 160 5.5L full-tank cost from live fuel prices.
- **Weekly Gas Price Watcher:** Automatically scrape `https://gaswatchph.com/quezon-city` every Saturday at 12:00 PM (`Asia/Manila`) for **Petron Commonwealth Ave Fairview** (`id: 621`, `prices.premium95`), compute the weekly price diff (`▲ +₱X.XX` hike or `▼ -₱X.XX` rollback), display a clean card ("Petron Fairview • Premium 95" without street address or source URL clutter), and provide 1-tap full-tank expense logging directly into the Motorcycle Gas envelope.

### Non-Goals
- Cloud backend synchronization, multi-device sync, or user account authentication.
- Open banking / third-party bank credential scraping or automated SMS/email parsing.
- Multi-currency conversion or foreign exchange tracking.

### Anti-Goals
- **Progress-bar fatigue:** Never render multiple competing rings or gauges on the hero card; limit the hero to one Safe-to-Spend multi-segment track.
- **Redundant navigation controls:** Never duplicate bottom navigation destinations (such as a top-bar Routine button alongside the bottom-bar Routine tab).
- **Cramped or colliding segmented tabs:** Never allow horizontal flex overflow collision; enforce a strict 4-column balanced CSS/Compose Row grid (`Envelopes`, `Bills`, `Loans`, `Activity`) with compact badges (e.g., `1!` instead of verbose `"1 Overdue"`).
- **Accessibility regressions:** Never use touch targets below `48dp` or text contrast below `4.5:1` WCAG AA.

---

## 3. Design System & Constraints (`ui-ux-pro-max`)

- **Profile:** Personal Finance Tracker (#91) + Minimal Swiss Modernism 2.0 + Dark Mode OLED.
- **Color Palette Tokens:**
  - Base Background (`bgBase`): `#06090E`
  - Card Surface (`bgCard`): `#0D1522`
  - Subtle Container (`bgCardSubtle`): `#121D2F`
  - Elevated Surface (`bgCardElevated`): `#18263E`
  - Border Subtle: `rgba(255, 255, 255, 0.08)`
  - Primary Accent (`accentSky`): `#38BDF8` / `#0284C7`
  - Safe / Paid (`accentEmerald`): `#10B981`
  - Warning / Commitments (`accentAmber`): `#F59E0B`
  - Alert / Overdue / Hike (`accentRose`): `#F43F5E`
  - Text Primary: `#F8FAFC`
  - Text Secondary: `#94A3B8`
  - Text Tertiary: `#64748B`
- **Typography:**
  - UI & Headings: `Plus Jakarta Sans` (Weights 400–800)
  - Numeric Data (`₱` amounts, percentages, odometer km, due days): `JetBrains Mono` with `fontFeatureSettings = "tnum"` (tabular numerals) so columns stay strictly aligned.
- **Screen Architecture & Navigation:**
  - **Top App Bar:** Month Cycle Picker pill (`November 2026 ▾`) + `SOA` PDF pill button.
  - **Hero Executive Card:** Label `"SAFE-TO-SPEND BALANCE"` (no leading dot), large `₱` balance, pacing chip (`₱X,XXX / day`), remaining days summary, 3-segment progress bar (Variable Spent / Committed Loans & Bills / Safe Remaining), and 3-column metric summary (`Total Budget`, `Spent So Far`, `Fixed / Loans`).
  - **Budget Sub-Tabs (4-Column Balanced Grid):** `Envelopes` (count badge), `Bills` (compact overdue badge `N!` in Rose when overdue), `Loans` (count badge), `Activity` (transaction count badge).
  - **Envelopes Section:** Clean header titled `ENVELOPES` with `+ Add Category` action; tappable category emoji icon boxes that open an icon picker sheet; inline `+` quick-log button per envelope.
  - **Floating Action Button (FAB):** Elevated circular `+` button (`58dp`, sky/blue gradient) anchored above the bottom bar on the right for instant expense logging.
  - **Bottom Navigation Bar (4 Destinations):**
    1. `₱ Budget` (custom Philippine Peso glyph `₱`)
    2. `🔔 Reminders` (Chronological Due-Date Alerts Hub & master notification switches)
    3. `🛵 Moto Care` (Honda Click 160 Hero, Petron Fairview Gas Watcher, PMS Tracker)
    4. `⚙️ Routine` (Opens editable Monthly Baseline Routine template modal/sheet)

---

## 4. Approaches Considered

| Approach | Summary | Tradeoffs | Verdict |
|---|---|---|---|
| **Approach A: Native Kotlin + Jetpack Compose (Single-Activity MVI) + Room + WorkManager** | Pure native Android stack using Jetpack Compose Material 3, Room SQLite Flow pipelines, Kotlin Coroutines, and Android `WorkManager` + `PdfRenderer`. | Slightly higher initial Kotlin UI authoring than web wrappers, but delivers zero-latency scrolling, rock-solid background `WorkManager` / `AlarmManager` notification triggers, native `PdfRenderer`, and minimal battery/RAM overhead. | **Selected** |
| **Approach B: Multi-Activity / Fragment XML Architecture** | Traditional XML layouts with multiple Activities/Fragments. | Higher boilerplate, awkward cross-screen state sharing between Envelopes, Loans, and Notifications, slower Compose theme alignment. | Rejected |
| **Approach C: Cross-Platform Hybrid (Flutter / React Native / Capacitor)** | Cross-platform UI layer wrapping native bridges. | Unreliable exact-time background wakeups across Android OEMs without extra native modules, heavier APK footprint, suboptimal `PdfRenderer` interop. | Rejected |

**Reason for Selection:** Since this is a strictly personal Android daily driver requiring reliable background Saturday noon web scraping, actionable heads-up push notifications (`Pay Now` / `Snooze 1h`), and fast native PDF inspection offline, Approach A gives the highest reliability and tightest OS integration.

---

## 5. Data Models & Room Database Schema

All currency values are stored as `Long` minor units (centavos, e.g., `₱78.40` = `7840L`) or `Double` with strict `BigDecimal` formatting to avoid floating-point drift. Using `Long` centavos (`amountCentavos: Long`) is recommended.

```kotlin
// 1. Baseline Monthly Routine Template (Editable & Auto-Cloned)
@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey val id: Int = 1,
    val totalMonthlyBudgetCentavos: Long,
    val autoRolloverEnabled: Boolean = true,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "routine_envelope_templates")
data class RoutineEnvelopeTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconEmoji: String,
    val colorHex: String,
    val allocatedLimitCentavos: Long,
    val isGasEnvelope: Boolean = false,
    val sortOrder: Int
)

@Entity(tableName = "routine_bill_templates")
data class RoutineBillTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconEmoji: String,
    val defaultAmountCentavos: Long,
    val dueDayOfMonth: Int,
    val isActive: Boolean = true
)

// 2. Active Monthly Budget Cycles & SOA PDF Attachment
@Entity(tableName = "budget_cycles")
data class BudgetCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String, // ISO "YYYY-MM", e.g., "2026-11"
    val totalBudgetCentavos: Long,
    val soaPdfLocalUri: String? = null, // App-private internal storage copy
    val soaFileName: String? = null,
    val soaUploadedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "envelopes",
    foreignKeys = [ForeignKey(entity = BudgetCycleEntity::class, parentColumns = ["id"], childColumns = ["cycleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cycleId")]
)
data class EnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val name: String,
    val iconEmoji: String,
    val colorHex: String,
    val allocatedLimitCentavos: Long,
    val spentCentavos: Long = 0L,
    val isGasEnvelope: Boolean = false
)

// 3. Variable & Settlement Transactions
@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = BudgetCycleEntity::class, parentColumns = ["id"], childColumns = ["cycleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cycleId"), Index("envelopeId")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val envelopeId: Long? = null,
    val billId: Long? = null,
    val loanId: Long? = null,
    val title: String,
    val categoryLabel: String,
    val amountCentavos: Long,
    val timestampEpochMs: Long
)

// 4. Recurring Bills & Subscriptions (Per Cycle with Notification Cadence)
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val templateGroupId: String, // Stable key across months
    val name: String,
    val iconEmoji: String,
    val amountCentavos: Long,
    val dueDayOfMonth: Int, // 1..31
    val isPaid: Boolean = false,
    val paidAtEpochMs: Long? = null,
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true
)

// 5. Active Installment Loans
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconEmoji: String,
    val monthlyAmortizationCentavos: Long,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val dueDayOfMonth: Int,
    val nextDueYearMonth: String, // ISO "YYYY-MM", e.g., "2026-11"
    val lastPaidCycleYearMonth: String? = null, // e.g., "2026-11"
    val isActive: Boolean = true,
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true
)

// 6. Moto Care & Gas Price Watcher
@Entity(tableName = "moto_profile")
data class MotoProfileEntity(
    @PrimaryKey val id: Int = 1,
    val modelName: String = "Honda Click 160",
    val currentOdometerKm: Int = 8450,
    val tankCapacityLiters: Double = 5.5
)

@Entity(tableName = "pms_intervals")
data class PmsIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskName: String,
    val iconEmoji: String,
    val intervalKm: Int,
    val lastServiceOdometerKm: Int,
    val lastServiceEpochMs: Long
)

@Entity(tableName = "gas_price_history")
data class GasPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: Int = 621, // Petron Commonwealth Ave Fairview
    val stationDisplayName: String = "Petron Fairview",
    val fuelType: String = "Premium 95",
    val pricePerLiterCentavos: Long,
    val previousPricePerLiterCentavos: Long?,
    val diffCentavos: Long, // positive = hike, negative = rollback
    val fetchedAtEpochMs: Long,
    val isManualOverride: Boolean = false
)
```

---

## 6. Background Workers & Architecture Flows

### A. Monthly Rollover (`MonthRolloverWorker`)
- Triggered on app launch (`MainViewModel` init) and daily midnight `WorkManager` check.
- Checks if `BudgetCycleEntity` exists for current `YearMonth` (`Asia/Manila`).
- If absent:
  1. Creates new `BudgetCycleEntity` inheriting `totalBudgetCentavos` from `RoutineTemplateEntity`.
  2. Clones all `RoutineEnvelopeTemplateEntity` rows into fresh `EnvelopeEntity` rows with `spentCentavos = 0L` (carrying `isGasEnvelope`).
  3. Clones all active `RoutineBillTemplateEntity` rows (`isActive == true`) into fresh `BillEntity` rows (`templateGroupId = template.id.toString()`) with `isPaid = false`.
  4. `LoanEntity` rows remain global; overdue/due status derives from `nextDueYearMonth <= currentYearMonth` while `isActive && paidInstallments < totalInstallments`.

### B. Safe-to-Spend & Undo State Flow
- Formula:
  $$\text{SafeToSpend} = \text{TotalBudget} - \text{VariableAndBillSpent} - \text{CommittedUnpaidLoans}$$
  Settling a `LoanEntity` moves its `monthlyAmortizationCentavos` from `CommittedUnpaidLoans` to `Spent`, keeping `SafeToSpend` constant (no double deduction). Settling a `BillEntity` or logging an Envelope expense increases `VariableAndBillSpent` and decreases `SafeToSpend`.
- Daily pacing:
  $$\text{DailyPace} = \max\left(0, \frac{\text{SafeToSpend}}{\text{DaysRemainingInclusiveInMonth}}\right)$$
- Deleting or undoing an expense/payment within the 5-second Snackbar/Toast window triggers a Room `@Transaction` reversal that deletes the `TransactionEntity`, decrements `EnvelopeEntity.spentCentavos` (or resets `BillEntity.isPaid` / `LoanEntity.lastPaidCycleYearMonth` and `nextDueYearMonth`), and decrements `paidInstallments` if it was a loan payment.

### C. Due-Date Notifications (`DueReminderWorker`)
- Scheduled daily at `09:00 AM` (`Asia/Manila`) via `PeriodicWorkRequest` + `ExistingPeriodicWorkPolicy.KEEP` (rescheduled on `BOOT_COMPLETED`).
- Evaluates every unpaid `BillEntity` (for active cycle) and active unpaid `LoanEntity`:
  - Computes `daysUntilDue = dueDate - today`.
  - Fires a high-priority Android Notification on channel `due_date_alerts` when `daysUntilDue` matches enabled flags (`7`, `3`, `1`, `0`), or `< 0` when `notifyDailyOverdue == true`.
  - Attaches two `PendingIntent` action buttons:
    - **`Pay ₱X,XXX.XX`**: Invokes `NotificationActionReceiver` (`ACTION_PAY_NOW`) using `goAsync()` to settle the item in Room, update `SafeToSpend`, log a transaction, and dismiss the notification.
    - **`Remind in 1h`**: Enqueues a one-time `OneTimeWorkRequest` (`delay = 1 hour`) to re-notify.

### D. Gas Price Watcher (`GasPriceSyncWorker`)
- Scheduled weekly for **Saturday at 12:00 PM (`Asia/Manila`)** using `WorkManager` initial delay calculation targeting the next Saturday `12:00:00` (and manual pull-to-refresh in Moto Care).
- **Extraction Pipeline:**
  1. HTTP GET `https://gaswatchph.com/quezon-city` with desktop/mobile User-Agent header (`OkHttp`, 15s timeout).
  2. Parse embedded JSON object containing `"id": 621` / `"name": "Petron Commonwealth Ave Fairview"` and extract `prices.premium95`.
  3. Convert `Double` price to minor centavos (e.g., `78.40` -> `7840L`).
  4. Compare with latest stored `GasPriceHistoryEntity.pricePerLiterCentavos` (default `previous = current` on first install) to compute `diffCentavos`.
  5. Persist new `GasPriceHistoryEntity`.
- **UI Display Rules (per user feedback):**
  - Display card header strictly as **Petron Fairview** / **Premium 95** — **do not** render the street address (`Commonwealth Ave...`) or source attribution (`Source: gaswatchph.com`).
  - Render weekly trend pill: `▲ +₱X.XX` (Rose `#F43F5E`) on price hike, `▼ -₱X.XX` (Emerald `#10B981`) on price rollback, or `— ₱0.00` when flat, with `Prev: ₱XX.XX/L` reference underneath.
  - Auto-compute **Click 160 Full Tank**: `pricePerLiter * 5.5` with a 1-tap `Log Full Tank` action button that inserts a transaction into the `"Motorcycle Gas"` envelope.

### E. SOA PDF Archival & Viewer
- Selecting a PDF via Android Storage Access Framework (`ActivityResultContracts.OpenDocument`) copies the file stream into `context.filesDir/soa/<yearMonth>_<uuid>.pdf` so URI permissions never expire across reboots.
- In-app viewer renders pages to `Bitmap` using `android.graphics.pdf.PdfRenderer` with pinch-to-zoom and page indicator, plus an instant **Replace File** option.

---

## 7. What Is Left to the Builder

- Gradle dependency catalog (`gradle/libs.versions.toml`) selecting exact Compose BOM, Room, WorkManager, Hilt, and OkHttp library versions compatible with AGP / targetSdk 35.
- Hilt `@HiltViewModel` and `@HiltWorker` module wiring (`HiltWorkerFactory`).
- Edge-case unit tests (month rollover math, leap-year/short-month due-day clamping e.g. `dueDayOfMonth = 31` in February, HTML scraper parser unit tests against saved fixture HTML, and 5-second undo transaction reversal tests).
- Smooth Material 3 bottom-sheet animations and haptics feedback bindings.

---

## 8. Builder Calls & Resolved Edge Cases

The following decisions resolve every architectural edge case identified during the builder review:

- **BC-1 (Safe-to-Spend & Settlement Math):** Active unpaid loans for the cycle are pre-reserved in `Fixed / Loans` (`committedUnpaidLoansCentavos`). `SafeToSpend = TotalBudget - VariableAndBillSpent - CommittedUnpaidLoans`. Settling a loan moves its amount from `CommittedUnpaidLoans` to `Spent` (leaving `SafeToSpend` unchanged, never double-deducted). Settling a recurring Bill (`BillEntity`) or logging an Envelope expense adds to `Spent` and reduces `SafeToSpend`. Adding a new envelope increases `TotalBudget` by its limit unless overridden in Routine.
- **BC-2 (Recurring Bill Templates & Rollover Scope):** `RoutineBillTemplateEntity` stores baseline recurring bills cloned by `MonthRolloverWorker` into each new cycle. Editing the Routine Template mid-month updates future cycles by default, with an optional checkbox `"Also apply to current month"`. If the app is unopened for multiple months, `MonthRolloverWorker` creates only the current active `YearMonth` cycle.
- **BC-3 (Cross-Month Overdue Loans & Auto-Completion):** `LoanEntity.nextDueYearMonth` tracks the next unpaid cycle. If `nextDueYearMonth < currentYearMonth`, it surfaces as overdue; paying increments `paidInstallments` and advances `nextDueYearMonth` by 1 month. When `paidInstallments >= totalInstallments`, `isActive` automatically sets to `false`.
- **BC-4 (CRUD, Variable Bills, Past Cycles, Odometer & PMS Cost Logging):** Full add/edit/delete CRUD for Envelopes, Bills, Loans, PMS tasks, and Transactions (deleting a transaction reverses its impact on the linked envelope/bill/loan). In-app `Pay Now` on a Bill opens a quick amount-confirmation sheet (supporting variable bills like Meralco); push notification `Pay` settles the stored amount immediately. Tapping the odometer pill (`8,450 km`) edits mileage; `+ Log PMS` includes an optional cost input that logs a budget transaction if `> 0`. Past cycles in the Month Picker allow viewing history, replacing SOA PDFs, and settling unpaid bills.
- **BC-5 (First-Launch Seeding & Gas Envelope Binding):** `AppDatabase.Callback` seeds the baseline `₱50,000` routine, 5 envelopes (`Motorcycle Gas` flagged with `isGasEnvelope = true`), sample bills/loans, Honda Click 160 (`5.5L`), and initial Petron Fairview price (`₱78.40/L`). `Log Full Tank` targets the envelope with `isGasEnvelope == true`.
- **BC-6 (Single SOA PDF per Cycle with Safe Cleanup):** One SOA PDF per cycle stored in `filesDir/soa/`; replacing a PDF deletes the old file only after the new file stream copy succeeds.
- **BC-7 (Gas Watcher Cold-Start, Manual Override, & Verified JSON Fixture):** Runs a one-time `GasPriceSyncWorker` on first launch plus the Saturday 12:00 PM periodic work. Tapping the pump price opens a manual override dialog (`isManualOverride = true`). Parser targets the verified JSON object in `<script>` tag #4 of `https://gaswatchph.com/quezon-city`: `{"id": 621, "brand": "petron", "name": "Petron Commonwealth Ave Fairview", "prices": {"premium95": 78.4, ...}}`.
