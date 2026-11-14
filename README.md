# Personal Finance OS

Offline-first native Android daily-driver app for Philippine Peso (`₱`) monthly budgeting, recurring bill and loan due-date notifications, SOA PDF archival, and Honda Click 160 maintenance with an automated Saturday noon Petron Fairview fuel price watcher.

## Architecture & Tech Stack

- **UI:** Kotlin 2.x, Jetpack Compose (Material 3, Dark OLED palette)
- **Persistence:** Room SQLite (`Long` centavos for all currency amounts)
- **Background & Alerts:** WorkManager (`MonthRolloverWorker`, `DueReminderWorker`, `GasPriceSyncWorker`) + 1-tap notification settlement
- **DI & Networking:** Hilt, OkHttp, Android `PdfRenderer`

## Planning Documents

- [Design Specification](docs/superpowers/specs/2026-11-14-personal-finance-os-design.md)
- [TDD Implementation Plan](docs/superpowers/plans/2026-11-14-personal-finance-os.md)
