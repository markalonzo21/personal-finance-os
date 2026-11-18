# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **Primary user:** Personal single-user daily driver for an individual earner/spender in the Philippines (and Honda Click 160 rider) managing monthly cashflow, obligations, and vehicle maintenance on their own Android device.

## Product Purpose

Omni (Personal Finance OS) provides an offline-first daily-driver system for Philippine Peso (`₱`) monthly budgeting, recurring bill and installment loan tracking, Statement of Account (SOA) PDF archival, ML Kit receipt/statement scanning, due-date notifications, and motorcycle PMS/fuel tracking. Success means knowing exact Safe-to-Spend balance at a glance without double-deducting fixed loan commitments or relying on cloud sync.

## Positioning

- **Offline-first `₱` Safe-to-Spend:** Local Room SQLite persistence with `Long` centavos arithmetic, separating variable envelope spending from committed installment loans so Safe-to-Spend is never double-deducted.
- **Unified Personal OS:** Combines monthly cycle envelopes, recurring bills, loan amortization tracking, local SOA PDF viewing, and Honda Click 160 PMS + Petron Commonwealth D.A gas price watching in one native Android app.

## Operating Context

- **Cadence:** Monthly budget cycles with automated month rollover (`MonthRolloverWorker`), recurring bill/loan due-date notifications (`DueReminderWorker`) with 1-tap notification settlement, and Saturday noon gas price sync (`GasPriceSyncWorker`).
- **On-the-go usage:** Quick daily expense logging via FAB or inline envelope `+`, privacy amount masking (`₱••••`) for public/commute viewing, and offline receipt/SOA PDF inspection via Android `PdfRenderer` and SAF `OpenDocument`.

## Capabilities and Constraints

- **Platform & Persistence:** Native Android (Kotlin, Jetpack Compose Material 3), Room SQLite (`Long` centavos), Hilt, WorkManager, OkHttp, ML Kit Text Recognition.
- **Currency:** Philippine Peso (`₱`) only.
- **Confirmed interaction conventions:** Whole-card tap-to-edit on budget envelopes; confirmation guards on destructive deletions and loan settlements.
- **Network scope:** Offline-first core; network used only for scheduled gas price watching (`GasPriceSyncWorker`).

## Brand Commitments

- **Product name:** Omni (Personal Finance OS).

## Evidence on Hand

- Feature registry: `docs/FEATURE_MAP.md` (`FM-001` through `FM-009`).
- Design specification & plan: `docs/superpowers/specs/2026-11-14-personal-finance-os-design.md`, `docs/superpowers/plans/2026-11-14-personal-finance-os.md`.
- Core UI screens: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/BudgetScreen.kt`, `MainScreen.kt`, `RemindersScreen.kt`, `MotoCareScreen.kt`.

## Product Principles

1. **Protect Safe-to-Spend accuracy:** Never double-deduct committed loans or blur variable envelopes with fixed obligations.
2. **Offline-first reliability:** All core budgeting, SOA viewing, and logging workflows must work without network connectivity.
3. **One-tap daily speed, guarded destructives:** Keep routine expense logging fast while confirming irreversible deletes and settlements.
4. **Privacy in public:** Support instant amount masking across balances, envelopes, bills, loans, and activity.

## Accessibility & Inclusion

- Maintain minimum 48dp touch targets, explicit `contentDescription` labels for icon/progress controls, and clear status text alongside color indicators.
