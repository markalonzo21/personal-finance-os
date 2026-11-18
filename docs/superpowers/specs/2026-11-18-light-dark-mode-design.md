# Light and Dark Mode Design

**Date:** 2026-11-18
**Status:** Draft (pending builder check & user approval)

## Intent & Why

Personal Finance OS currently ships with a single dark OLED theme (`OLEDColors` and `PersonalFinanceDarkColorScheme`). Users in bright environments or who prefer light interfaces cannot switch themes or follow the OS appearance setting. Adding a Warm Paper light theme, a persisted `System` / `Light` / `Dark` preference, and a dedicated `Settings` bottom tab gives users comfortable daytime readability without losing the existing dark OLED look.

## Goals, Non-Goals, Anti-Goals

**Goals:**
- Support three persisted theme modes: `System` (default on fresh install), `Light` (Warm Paper palette), and `Dark` (existing `OLEDColors` palette).
- Add a `Settings` tab as the 5th item in `BottomNavigationBar` hosting an Appearance section with a `System` / `Light` / `Dark` selector.
- Update all screens, sheets, dialogs, and system bars (`statusBarStyle` / `navigationBarStyle`) reactively when the theme changes.
- Preserve the existing Dark OLED appearance pixel-for-pixel when Dark mode is active.

**Non-Goals:**
- Material You dynamic wallpaper colors (`dynamicColor`).
- Custom user-editable accent colors or multiple light/dark themes beyond Warm Paper and Dark OLED.
- Moving `GasWatcherConfigDialog` or other feature-specific dialogs into `SettingsScreen` (can be done separately later).

**Anti-Goals:**
- Leaving screens partially themed (e.g., light background with dark hardcoded card gradients or unreadable low-contrast accents on `#FFFFFF`).
- Keeping `OLEDColors` as `@Composable` getters backed by mutable state (misleading name and fragile in non-composable scopes).

## Constraints

- `OLEDColors` is currently referenced directly across 13 UI files (`BudgetScreen.kt`, `MotoCareScreen.kt`, `RemindersScreen.kt`, `MainScreen.kt`, `BillPaymentAndCrudSheets.kt`, `ScanSheets.kt`, `RoutineSheet.kt`, `GasWatcherConfigDialog.kt`, `LogExpenseSheet.kt`, `PdfViewerSheet.kt`, `IconPickerSheet.kt`, `ReminderCadenceSheet.kt`, `CurrencyFormatters.kt`), plus 5 hardcoded `Color(0x...)` literals in `BudgetScreen.kt` (hero gradient and progress tracks) and `MainScreen.kt` (FAB gradient).
- `MainActivity` currently locks `enableEdgeToEdge` to `SystemBarStyle.dark(...)`, and `res/values/themes.xml` hardcodes black/`#06090E` for cold start.
- Per `AGENTS.md`, implementation must occur on a dedicated feature branch (e.g., `feat/light-dark-mode`) and update `docs/FEATURE_MAP.md`.

## Approaches Considered

1. **Approach A — `CompositionLocal` `FinanceColors` + `MaterialTheme.colorScheme` (Chosen):**
   Define an immutable `FinanceColors` data class (`DarkOLEDColors` and `LightWarmPaperColors`) with the exact semantic token set (`bgBase`, `bgCard`, `bgCardSubtle`, `bgCardElevated`, `borderSubtle`, 6 accents, 3 text levels, plus surface gradient/track tokens), provide it via `LocalFinanceColors` inside `PersonalFinanceTheme` (exposed as `FinanceTheme.colors`), and wire `PersonalFinanceLightColorScheme` / `PersonalFinanceDarkColorScheme` into `MaterialTheme`. Replace `OLEDColors.*` and the 5 hardcoded `Color(0x...)` literals across the 13 UI files with `FinanceTheme.colors.*`.
   - *Why chosen:* Maps 1-to-1 to every existing custom token without forcing 4 surface levels and 6 accents into Material3's `ColorScheme` slots, is compiler-checked, and keeps Material3 sheets/inputs/snackbars in sync.
2. **Approach B — Pure `MaterialTheme.colorScheme` + `ColorScheme` extensions:**
   Map base/surface/accents to Material3 `ColorScheme` and add extension properties for extra tokens (`accentPurple`, `textTertiary`, etc.).
   - *Tradeoff:* Splits color tokens awkwardly between `MaterialTheme.colorScheme` and custom extensions, and extensions would need `isLight` checks.

## Design Details

### 1. Theme Preference & Persistence
- Add `ThemeMode` enum (`SYSTEM`, `LIGHT`, `DARK`) with `SYSTEM` as default.
- Mirror `GasWatcherConfigStore`: define `ThemePreferenceStore` interface (`observeThemeMode(): Flow<ThemeMode>`, `getThemeMode(): ThemeMode`, `setThemeMode(mode: ThemeMode)`), `SharedPreferencesThemePreferenceStore` (`@Singleton` with `MutableStateFlow`), and `InMemoryThemePreferenceStore` for tests, bound in `AppModule`.
- Expose `themeMode: ThemeMode` in `MainUiState` and `onSelectThemeMode(ThemeMode)` on `MainViewModel`.

### 2. Color Tokens & `PersonalFinanceTheme`
- **`DarkOLEDColors`:** Preserves all existing `OLEDColors` values (`bgBase = #06090E`, `bgCard = #0D1522`, `bgCardSubtle = #121D2F`, `bgCardElevated = #18263E`, `borderSubtle = 0x14FFFFFF`, `accentSky = #38BDF8`, `accentSkyDeep = #0284C7`, `accentEmerald = #10B981`, `accentAmber = #F59E0B`, `accentRose = #F43F5E`, `accentPurple = #A855F7`, `textPrimary = #F8FAFC`, `textSecondary = #94A3B8`, `textTertiary = #64748B`, plus hero gradient `#152238` -> `#0A111E` and track `#1E293B`).
- **`LightWarmPaperColors` (Warm Paper):**
  - Surfaces & borders: `bgBase = #F7F6F2`, `bgCard = #FFFFFF`, `bgCardSubtle = #F0EFEA`, `bgCardElevated = #FFFFFF` (or `#FAF9F6`), `borderSubtle = #E7E5DF`.
  - Text: `textPrimary = #1C1917`, `textSecondary = #57534E`, `textTertiary = #78716C`.
  - High-contrast accents on light surfaces: `accentSky = #0369A1`, `accentSkyDeep = #0284C7`, `accentEmerald = #059669`, `accentAmber = #D97706`, `accentRose = #E11D48`, `accentPurple = #9333EA`.
- `PersonalFinanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` selects `DarkOLEDColors` + `PersonalFinanceDarkColorScheme` or `LightWarmPaperColors` + `PersonalFinanceLightColorScheme` and provides `LocalFinanceColors`.

### 3. UI Migration
- Replace all `OLEDColors.*` references across the 13 UI files with `FinanceTheme.colors.*` (and remove or deprecate `OLEDColors` once unused).
- Replace the 5 hardcoded `Color(0x...)` values in `BudgetScreen.kt` (hero card gradient and progress bar track) and `MainScreen.kt` (FAB gradient) with `FinanceTheme.colors` tokens so light mode renders warm paper/stone hero and track surfaces instead of dark navy blocks.

### 4. `Settings` Bottom Tab & Screen
- Add `BottomTab.SETTINGS` to `BottomTab` enum and add a 5th `BottomIconNavItem` (`Icons.Rounded.Settings`, label `"Settings"`) in `BottomNavigationBar` in `MainScreen.kt`.
- Create `SettingsScreen` in `ui/screens/SettingsScreen.kt` with an **Appearance** card containing a 3-way segmented control (`System`, `Light`, `Dark`) reflecting `state.themeMode` and calling `onSelectThemeMode`.
- Keep FAB hidden on `Settings` (already gated to `BottomTab.BUDGET`).

### 5. System Bars & Cold-Start Window
- In `MainActivity`, collect `themeMode` and resolve `isDarkTheme` (`when (themeMode) { SYSTEM -> isSystemInDarkTheme(); LIGHT -> false; DARK -> true }`).
- Use `SideEffect` / `DisposableEffect` to call `enableEdgeToEdge` with `SystemBarStyle.dark(AndroidColor.TRANSPARENT)` when `isDarkTheme` is true and `SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)` when false.
- Move the dark `#06090E` window/bar attributes from `res/values/themes.xml` to `res/values-night/themes.xml` and set light `#F7F6F2` equivalents in `res/values/themes.xml` so cold-start matches system appearance.

## Left to the Builder

- Exact Material3 `ColorScheme` slot mappings (`surfaceContainer*`, `primaryContainer`, `onPrimary`, etc.) for `PersonalFinanceLightColorScheme`, plus exact Warm Paper hero gradient/track colors that maintain readable contrast.
- Segmented control styling inside `SettingsScreen` (matching existing card/pill patterns in `BudgetScreen` / `MotoCareScreen`).
- Unit tests (`ThemePreferenceStoreTest`, `ThemeAndStartupConfigTest` updates, `MainViewModel` theme state tests) and UI/Robolectric tests for `SettingsScreen` and theme switching.
- Creating the feature branch and updating `docs/FEATURE_MAP.md`.
