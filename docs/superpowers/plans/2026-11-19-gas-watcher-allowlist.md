# Gas Watcher Brand & Fuel Type Allowlist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict Gas Watcher station selection, saved config normalization, and fuel-type options to 5 brands (`Petron`, `Shell`, `Caltex`, `Seaoil`, `Phoenix`) and 2 fuel types (`Unleaded`, `Premium 95`).

**Architecture:** Keep `GasWatchParser` general-purpose and introduce a shared `GasWatchAllowlist` object in `GasWatchParser.kt`. Apply `GasWatchAllowlist` at three boundaries: `GasPriceRepository.fetchStationsForLocation`, `SharedPreferencesGasWatcherConfigStore.readFromPrefs`, and `GasWatcherConfigDialog`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room/SharedPreferences, JUnit4 + Robolectric + Truth + `kotlinx-coroutines-test`.

**Spec:** [docs/superpowers/specs/2026-11-19-gas-watcher-allowlist-design.md](file:///C:/Users/mcalonzo/codes/personal-finance-os/docs/superpowers/specs/2026-11-19-gas-watcher-allowlist-design.md)

## Global Constraints

- Allowed brands (case-insensitive, trimmed): `petron`, `shell`, `caltex`, `seaoil`, `phoenix`.
- Allowed fuel types: `GasFuelType.UNLEADED` (`"unleaded"`) and `GasFuelType.PREMIUM_95` (`"premium95"`).
- Do not break general `GasWatchParser.parseStations` behavior for existing parser unit tests.
- All tests run via `.\gradlew.bat testDebugUnitTest`.

## Review Focus

- Brand string has mixed case or surrounding whitespace (`" Shell "`, `"CALTEX"`) -> must still match `ALLOWED_BRANDS`.
- Allowed-brand station carries only `diesel`/`kerosene` (no `unleaded` or `premium95`) -> must be excluded from `fetchStationsForLocation`.
- City HTML has stations, but zero match the allowlist -> `fetchStationsForLocation` must return `Result.failure`, not an empty list.
- Legacy `SharedPreferences` contains `fuel_key = "diesel"` -> `readFromPrefs()` must coerce to `PREMIUM_95` (`"premium95"` / `"Premium 95"`).
- `GasWatcherConfigDialog` opens with `currentState.fuelKey = "diesel"` or a station with empty/non-allowed prices -> `availableFuelTypes` must only show `Unleaded` and `Premium 95`, and `selectedFuelType` must coerce to `PREMIUM_95`.

---

### Task 1: Add `GasWatchAllowlist` and filter stations in `GasPriceRepository`

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/data/remote/GasWatchParser.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/GasPriceRepository.kt:87-95`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/repository/GasPriceRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `GasWatchAllowlist.ALLOWED_BRANDS: Set<String>`
  - `GasWatchAllowlist.ALLOWED_FUEL_TYPES: List<GasFuelType>`
  - `GasWatchAllowlist.isAllowedBrand(brand: String): Boolean`
  - `GasWatchAllowlist.isAllowedFuel(fuel: GasFuelType): Boolean`
  - `GasWatchAllowlist.coerceFuelType(fuel: GasFuelType): GasFuelType`
  - `GasWatchAllowlist.filterStations(stations: List<ParsedGasStation>): List<ParsedGasStation>`

- [ ] **Step 1: Write failing tests in `GasPriceRepositoryTest.kt`**

Add two tests to `GasPriceRepositoryTest`:
1. `` `fetchStationsForLocation filters to allowed brands and allowed fuel types` ``:
   - Serve HTML containing:
     - `Petron` with `unleaded`, `premium95`, `diesel` -> kept, `pricesCentavos` has only `UNLEADED` and `PREMIUM_95`.
     - ` Shell ` (mixed case + whitespace) with `unleaded` -> kept.
     - `Cleanfuel` with `premium95` -> excluded (brand not in allowlist).
     - `Caltex` with only `diesel` -> excluded (no allowed fuel price).
   - Assert `stations` contains only the `Petron` and `Shell` stations and `pricesCentavos.keys` contains no `DIESEL`.
2. `` `fetchStationsForLocation fails when no stations match allowlist` ``:
   - Serve HTML with only `Cleanfuel` (`diesel`).
   - Assert `result.isFailure` is `true`.

- [ ] **Step 2: Run targeted test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.repository.GasPriceRepositoryTest"`
Expected: FAIL (unfiltered `Cleanfuel` / `DIESEL` returned).

- [ ] **Step 3: Implement `GasWatchAllowlist` and wire into `GasPriceRepository.fetchStationsForLocation`**

1. In `GasWatchParser.kt`, add `object GasWatchAllowlist`:
   - `ALLOWED_BRANDS = setOf("petron", "shell", "caltex", "seaoil", "phoenix")`
   - `ALLOWED_FUEL_TYPES = listOf(GasFuelType.UNLEADED, GasFuelType.PREMIUM_95)`
   - `isAllowedBrand(brand: String) = brand.trim().lowercase() in ALLOWED_BRANDS`
   - `isAllowedFuel(fuel: GasFuelType) = fuel in ALLOWED_FUEL_TYPES`
   - `coerceFuelType(fuel: GasFuelType) = if (isAllowedFuel(fuel)) fuel else GasFuelType.PREMIUM_95`
   - `filterStations(stations: List<ParsedGasStation>): List<ParsedGasStation>`: keep stations where `isAllowedBrand(it.brand)`, map `pricesCentavos` to only entries where `isAllowedFuel(key)`, and drop any station whose filtered `pricesCentavos` is empty.
2. In `GasPriceRepository.fetchStationsForLocation(locationSlug: String)`:
   - Apply `GasWatchAllowlist.filterStations(GasWatchParser.parseStations(html))`.
   - If empty, return `Result.failure(IllegalStateException("No matching stations found for $locationSlug"))`.

- [ ] **Step 4: Run `GasPriceRepositoryTest` and `GasWatchParserTest` to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.repository.GasPriceRepositoryTest" --tests "com.mcalonzo.personalfinanceos.data.remote.GasWatchParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/remote/GasWatchParser.kt app/src/main/java/com/mcalonzo/personalfinanceos/data/repository/GasPriceRepository.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/repository/GasPriceRepositoryTest.kt
git commit -m "feat(motocare): filter gas watcher stations by allowed brands and fuels"
```

---

### Task 2: Coerce legacy non-allowed fuel keys in `GasWatcherConfigStore`

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/GasWatcherConfigStore.kt:66-85`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/local/GasWatcherConfigStoreTest.kt` (Create)

**Interfaces:**
- Consumes: `GasWatchAllowlist.coerceFuelType(GasFuelType)`

- [ ] **Step 1: Write failing test in `GasWatcherConfigStoreTest.kt`**

Create `GasWatcherConfigStoreTest` (Robolectric):
- Test `` `readFromPrefs coerces disallowed legacy fuelKey to Premium 95` ``:
  - Write `"fuel_key" = "diesel"` and `"fuel_name" = "Diesel"` into `"gas_watcher_prefs"` SharedPreferences.
  - Instantiate `SharedPreferencesGasWatcherConfigStore(context)` and call `getConfig()`.
  - Assert `fuelKey == "premium95"` and `fuelDisplayName == "Premium 95"`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.GasWatcherConfigStoreTest"`
Expected: FAIL (`fuelKey` is `"diesel"`).

- [ ] **Step 3: Update `SharedPreferencesGasWatcherConfigStore.readFromPrefs()`**

Parse `fuel_key` via `GasFuelType.fromKeyOrLabel(...)`, pass through `GasWatchAllowlist.coerceFuelType(...)`, and use `coercedFuel.jsonKey` and `coercedFuel.displayName` (falling back to `coercedFuel.displayName` if `fuel_key` was coerced).

- [ ] **Step 4: Run `GasWatcherConfigStoreTest` to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.GasWatcherConfigStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/local/GasWatcherConfigStore.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/local/GasWatcherConfigStoreTest.kt
git commit -m "fix(motocare): coerce legacy disallowed gas watcher fuel keys to premium95"
```

---

### Task 3: Enforce allowlist in `GasWatcherConfigDialog`

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/GasWatcherConfigDialog.kt:90-140`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ui/MotoAndRemindersScreenTest.kt`

**Interfaces:**
- Consumes: `GasWatchAllowlist.ALLOWED_FUEL_TYPES`, `GasWatchAllowlist.coerceFuelType(GasFuelType)`

- [ ] **Step 1: Write failing UI test in `MotoAndRemindersScreenTest.kt`**

Add test `` `gas watcher settings only shows allowed brands and unleaded or premium95 fuel types` ``:
- Pass `motoState` with `fuelKey = "diesel"`, and `stations` containing a `Petron` station (with `UNLEADED`, `PREMIUM_95`, `DIESEL`) plus a `Cleanfuel` station (with `PREMIUM_95`).
- Open `GasWatcherConfigDialog`.
- Assert `"Cleanfuel"` does not exist, `"Diesel"` does not exist, `"Unleaded"` and `"Premium 95"` exist.

- [ ] **Step 2: Run `MotoAndRemindersScreenTest` to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.MotoAndRemindersScreenTest"`
Expected: FAIL (`"Cleanfuel"` and `"Diesel"` are rendered).

- [ ] **Step 3: Enforce `GasWatchAllowlist` in `GasWatcherConfigDialog.kt`**

- Coerce initial `selectedFuelType` with `GasWatchAllowlist.coerceFuelType(GasFuelType.fromKeyOrLabel(currentState.fuelKey))`.
- Filter `pickerState.stations` with `GasWatchAllowlist.filterStations(...)` before search/display so even pre-populated/test states respect the allowlist.
- Compute `availableFuelTypes` from `GasWatchAllowlist.ALLOWED_FUEL_TYPES` (filtered by `selectedStation?.pricesCentavos` when present).

- [ ] **Step 4: Run full test suite to verify all tests pass**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/GasWatcherConfigDialog.kt app/src/test/java/com/mcalonzo/personalfinanceos/ui/MotoAndRemindersScreenTest.kt
git commit -m "feat(motocare): enforce brand and fuel type allowlist in gas watcher dialog"
```
