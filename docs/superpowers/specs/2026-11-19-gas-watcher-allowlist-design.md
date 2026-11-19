# Gas Watcher Brand & Fuel Type Allowlist Design

**Date:** 2026-11-19
**Branch:** `fix/gas-watcher-settings-ux` (or dedicated feature branch)
**Size:** Small change

## Intent & Why

Limit the MotoCare Gas Watcher station picker and configuration to the 5 preferred Philippine fuel brands and 2 gasoline grades relevant to a Honda Click 160 rider, cutting noise in large city lists (such as Quezon City) and hiding irrelevant fuels (Diesel, Premium Diesel, Kerosene, Premium 97, E-Gasoline).

## Goals & Non-Goals

- **Goals:**
  - Show only stations whose `brand` (case-insensitive) is one of: `Petron`, `Shell`, `Caltex`, `Seaoil`, `Phoenix`.
  - Show and allow only `GasFuelType.UNLEADED` (Unleaded 91) and `GasFuelType.PREMIUM_95` (Premium 95).
  - Exclude stations that have neither `UNLEADED` nor `PREMIUM_95` prices.
  - Normalize any previously saved non-allowed `fuelKey` in `GasWatcherConfigStore` to `PREMIUM_95`.
- **Non-Goals:**
  - Editable/toggleable brand or fuel-type filter chips in the UI.
  - Changing `GasWatchParser` HTML/JSON parsing behavior for general unit tests.

## Approaches Considered

1. **Approach A — Filter at Repository, Store & UI Boundary (Selected):**
   Keep `GasWatchParser` general-purpose; define a shared allowlist (`GasWatchAllowlist`) applied in `GasPriceRepository.fetchStationsForLocation`, `GasWatcherConfigStore.readFromPrefs`, and `GasWatcherConfigDialog`. Preserves parser testability while guaranteeing only allowed brands/grades reach the picker and saved config.
2. **Approach B — Filter inside `GasWatchParser.parseStations`:**
   Rejected because it couples the HTML/JSON parser to rider-specific preferences and breaks parser tests for other grades/brands.
3. **Approach C — Filter only in `GasWatcherConfigDialog`:**
   Rejected because repository callers would still receive unfiltered stations and legacy non-allowed `fuelKey` preferences wouldn't be normalized.

## Design Details

- **Allowlist (`GasWatchAllowlist` in `GasWatchParser.kt` or `GasPriceRepository.kt`):**
  - `ALLOWED_BRANDS = setOf("petron", "shell", "caltex", "seaoil", "phoenix")`
  - `ALLOWED_FUEL_TYPES = listOf(GasFuelType.UNLEADED, GasFuelType.PREMIUM_95)`
  - Helper `isAllowedBrand(brand: String)` (trimmed lowercase match) and `filterAllowedStations(stations)`.
- **`GasPriceRepository.fetchStationsForLocation`:**
  - Filter parsed stations to allowed brands that contain at least one `ALLOWED_FUEL_TYPES` price, retaining only allowed fuel entries in `pricesCentavos`.
  - If no stations match after filtering, return `Result.failure(IllegalStateException("No matching stations found for $locationSlug"))`.
- **`GasWatcherConfigStore` (`SharedPreferencesGasWatcherConfigStore`):**
  - On `readFromPrefs()`, coerce `fuelKey` through `GasFuelType.fromKeyOrLabel(...)` and fall back to `PREMIUM_95` if not in `ALLOWED_FUEL_TYPES`.
- **`GasWatcherConfigDialog`:**
  - Compute `availableFuelTypes` from `ALLOWED_FUEL_TYPES` (filtered by `selectedStation?.pricesCentavos` when present), and coerce `selectedFuelType` to `PREMIUM_95` if `currentState.fuelKey` isn't in `ALLOWED_FUEL_TYPES`.

## Left to the Builder

- Update/add unit tests in `GasPriceRepositoryTest`, `GasWatcherConfigStoreTest` (if present), and `MotoAndRemindersScreenTest` to verify brand/fuel filtering and legacy `fuelKey` fallback.
