# Light and Dark Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Warm Paper light theme, persisted `System` / `Light` / `Dark` theme preference, reactive system bars, and a `Settings` bottom tab to Personal Finance OS.

**Architecture:** Mirror `GasWatcherConfigStore` with a `SharedPreferences`-backed `ThemePreferenceStore` defaulting to `ThemeMode.SYSTEM`. Provide an immutable `FinanceColors` palette (`DarkOLEDColors` and `LightWarmPaperColors`) via `LocalFinanceColors` (`FinanceTheme.colors`) alongside Material3 `lightColorScheme`/`darkColorScheme` in `PersonalFinanceTheme`, and migrate all 13 UI files off hardcoded `OLEDColors`. Wire `BottomTab.SETTINGS` and `SettingsScreen` through `MainViewModel` and update `MainActivity` edge-to-edge bar styles reactively.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt, SharedPreferences, StateFlow, JUnit4, Robolectric, Google Truth.

**Spec:** [2026-11-18-light-dark-mode-design.md](file:///C:/Users/mcalonzo/codes/personal-finance-os/docs/superpowers/specs/2026-11-18-light-dark-mode-design.md)

## Global Constraints

- Feature branch isolation (`AGENTS.md`): create and work on a dedicated branch (e.g., `feat/light-dark-mode`), never directly on `main`.
- Fresh install default: `ThemeMode.SYSTEM`.
- Dark mode must preserve existing `OLEDColors` values (`bgBase = #06090E`, `bgCard = #0D1522`, `bgCardSubtle = #121D2F`, `bgCardElevated = #18263E`, `borderSubtle = 0x14FFFFFF`, `accentSky = #38BDF8`, `accentSkyDeep = #0284C7`, `accentEmerald = #10B981`, `accentAmber = #F59E0B`, `accentRose = #F43F5E`, `accentPurple = #A855F7`, `textPrimary = #F8FAFC`, `textSecondary = #94A3B8`, `textTertiary = #64748B`).
- Light mode uses the Warm Paper palette (`bgBase = #F7F6F2`, `bgCard = #FFFFFF`, `bgCardSubtle = #F0EFEA`, `bgCardElevated = #FAF9F6`, `borderSubtle = #E7E5DF`, `textPrimary = #1C1917`, `textSecondary = #57534E`, `textTertiary = #78716C`, `accentSky = #0369A1`, `accentSkyDeep = #0284C7`, `accentEmerald = #059669`, `accentAmber = #D97706`, `accentRose = #E11D48`, `accentPurple = #9333EA`).
- Feature Map (`AGENTS.md`): update `docs/FEATURE_MAP.md` before/after implementation and mark status `verified` once tests pass.

## Review Focus

- Corrupted or unrecognized string stored in `SharedPreferences` for theme mode must fall back safely to `ThemeMode.SYSTEM` without throwing `IllegalArgumentException`.
- Switching `ThemeMode` while on `SettingsScreen` must immediately update `FinanceTheme.colors`, `MaterialTheme.colorScheme`, and `MainActivity` system bar icon polarity without recreating/resetting navigation state.
- `BudgetScreen` hero card gradient (`#152238` -> `#0A111E`) and progress tracks (`#1E293B`), plus `MainScreen` FAB gradient (`#0EA5E9`), must use theme tokens so Light mode does not render dark navy blocks.
- `BottomNavigationBar` with 5 items (`Budget`, `Reminders`, `Moto Care`, `Routine`, `Settings`) must keep every tab clickable and readable without clipping labels on narrow screens.
- Existing `MainViewModel` unit tests that construct `MainViewModel` directly without passing `ThemePreferenceStore` must continue to compile and pass via a default `InMemoryThemePreferenceStore()`.

---

### Task 1: ThemePreferenceStore & Hilt Binding

**Files:**
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/data/local/ThemePreferenceStore.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/di/AppModule.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/data/local/ThemePreferenceStoreTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `SharedPreferences`, Hilt `@Singleton`
- Produces:
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`
  - `interface ThemePreferenceStore { fun observeThemeMode(): Flow<ThemeMode>; fun getThemeMode(): ThemeMode; fun setThemeMode(mode: ThemeMode) }`
  - `class InMemoryThemePreferenceStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferenceStore`
  - `class SharedPreferencesThemePreferenceStore @Inject constructor(@ApplicationContext context: Context) : ThemePreferenceStore`

- [ ] **Step 1: Create feature branch and write failing unit tests in `ThemePreferenceStoreTest.kt`**

```kotlin
package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemePreferenceStoreTest {
    @Test
    fun `fresh SharedPreferencesThemePreferenceStore defaults to SYSTEM and persists updates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesThemePreferenceStore(context)

        assertThat(store.getThemeMode()).isEqualTo(ThemeMode.SYSTEM)
        assertThat(store.observeThemeMode().first()).isEqualTo(ThemeMode.SYSTEM)

        store.setThemeMode(ThemeMode.LIGHT)
        assertThat(store.getThemeMode()).isEqualTo(ThemeMode.LIGHT)

        val reloaded = SharedPreferencesThemePreferenceStore(context)
        assertThat(reloaded.getThemeMode()).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun `unknown stored preference value falls back to SYSTEM`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "CORRUPTED_VALUE")
            .commit()

        val store = SharedPreferencesThemePreferenceStore(context)
        assertThat(store.getThemeMode()).isEqualTo(ThemeMode.SYSTEM)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.ThemePreferenceStoreTest"`
Expected: FAIL with unresolved reference `ThemePreferenceStore` / `ThemeMode`.

- [ ] **Step 3: Implement `ThemePreferenceStore.kt` and bind it in `AppModule.kt`**

Implement `ThemeMode`, `ThemePreferenceStore`, `InMemoryThemePreferenceStore`, and `SharedPreferencesThemePreferenceStore` (using `"theme_preferences"` prefs name and `"theme_mode"` key, catching unknown enum names and falling back to `ThemeMode.SYSTEM`). Add `@Provides @Singleton fun provideThemePreferenceStore(store: SharedPreferencesThemePreferenceStore): ThemePreferenceStore = store` to `AppModule`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.data.local.ThemePreferenceStoreTest"`
Expected: PASS (2 tests passed).

- [ ] **Step 5: Commit**

```bash
git checkout -b feat/light-dark-mode
git add app/src/main/java/com/mcalonzo/personalfinanceos/data/local/ThemePreferenceStore.kt app/src/main/java/com/mcalonzo/personalfinanceos/di/AppModule.kt app/src/test/java/com/mcalonzo/personalfinanceos/data/local/ThemePreferenceStoreTest.kt
git commit -m "feat: add ThemePreferenceStore with System/Light/Dark persistence"
```

---

### Task 2: FinanceColors, Warm Paper Palette & PersonalFinanceTheme

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ThemeAndStartupConfigTest.kt`

**Interfaces:**
- Consumes: `androidx.compose.runtime.Immutable`, `staticCompositionLocalOf`, `lightColorScheme`, `darkColorScheme`
- Produces:
  - `@Immutable data class FinanceColors(...)` with `bgBase`, `bgCard`, `bgCardSubtle`, `bgCardElevated`, `borderSubtle`, `heroGradientStart`, `heroGradientEnd`, `progressTrack`, `fabGradientEnd`, `accentSky`, `accentSkyDeep`, `accentEmerald`, `accentAmber`, `accentRose`, `accentPurple`, `textPrimary`, `textSecondary`, `textTertiary`
  - `val DarkOLEDColors: FinanceColors`
  - `val LightWarmPaperColors: FinanceColors`
  - `object FinanceTheme { val colors: FinanceColors @Composable get() = LocalFinanceColors.current }`
  - `@Composable fun PersonalFinanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`

- [ ] **Step 1: Add failing theme palette & `FinanceTheme.colors` test to `ThemeAndStartupConfigTest.kt`**

Add a `@Test` using Robolectric + Compose (`ComposeContentTestRule` or `createComposeRule`) that renders `PersonalFinanceTheme(darkTheme = false)` and `PersonalFinanceTheme(darkTheme = true)` and asserts:
- `darkTheme = true` provides `DarkOLEDColors` (`bgBase == Color(0xFF06090E)`, `heroGradientStart == Color(0xFF152238)`, `progressTrack == Color(0xFF1E293B)`) and `MaterialTheme.colorScheme.background == Color(0xFF06090E)`.
- `darkTheme = false` provides `LightWarmPaperColors` (`bgBase == Color(0xFFF7F6F2)`, `bgCard == Color(0xFFFFFFFF)`, `textPrimary == Color(0xFF1C1917)`, `accentSky == Color(0xFF0369A1)`) and `MaterialTheme.colorScheme.background == Color(0xFFF7F6F2)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ThemeAndStartupConfigTest"`
Expected: FAIL with unresolved reference `FinanceTheme` / `darkTheme` parameter.

- [ ] **Step 3: Implement `FinanceColors`, `DarkOLEDColors`, `LightWarmPaperColors` in `Color.kt` and `LocalFinanceColors` / `FinanceTheme` / `PersonalFinanceLightColorScheme` in `Theme.kt`**

Keep `OLEDColors` temporarily as a `@Deprecated` alias or keep it until Task 3 completes so the project compiles cleanly between tasks. Set Warm Paper `heroGradientStart = Color(0xFFFAF9F6)`, `heroGradientEnd = Color(0xFFF0EFEA)`, `progressTrack = Color(0xFFE7E5DF)`, `fabGradientEnd = Color(0xFF0284C7)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ThemeAndStartupConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/ui/theme/Color.kt app/src/main/java/com/mcalonzo/personalfinanceos/ui/theme/Theme.kt app/src/test/java/com/mcalonzo/personalfinanceos/ThemeAndStartupConfigTest.kt
git commit -m "feat: add FinanceColors, Warm Paper light palette, and LocalFinanceColors"
```

---

### Task 3: Migrate Screens & Components from `OLEDColors` to `FinanceTheme.colors`

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/BudgetScreen.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MainScreen.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MotoCareScreen.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/RemindersScreen.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/BillPaymentAndCrudSheets.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/CurrencyFormatters.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/GasWatcherConfigDialog.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/IconPickerSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/LogExpenseSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/PdfViewerSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/ReminderCadenceSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/RoutineSheet.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/components/ScanSheets.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/theme/Color.kt` (remove `OLEDColors` once zero references remain)

**Interfaces:**
- Consumes: `FinanceTheme.colors` from Task 2.
- Produces: Zero remaining `OLEDColors` references or hardcoded dark surface `Color(0x...)` literals in `ui/screens/` and `ui/components/`.

- [ ] **Step 1: Replace `OLEDColors.*` with `FinanceTheme.colors.*` across the 13 UI files**

Update imports (`FinanceTheme` instead of `OLEDColors`) and replace `OLEDColors.` with `FinanceTheme.colors.`. If `CurrencyFormatters.kt` has any non-composable helper reading `OLEDColors`, pass `FinanceColors` or make the helper `@Composable` / accept `Color` parameters.

- [ ] **Step 2: Replace the 5 hardcoded `Color(0x...)` literals in `BudgetScreen.kt` and `MainScreen.kt`**

- `BudgetScreen.kt:503-504`: replace `Color(0xFF152238)`, `Color(0xFF0A111E)` with `FinanceTheme.colors.heroGradientStart`, `FinanceTheme.colors.heroGradientEnd`.
- `BudgetScreen.kt:626, 991`: replace `Color(0xFF1E293B)` with `FinanceTheme.colors.progressTrack`.
- `MainScreen.kt:284`: replace `Color(0xFF0EA5E9)` with `FinanceTheme.colors.fabGradientEnd`.

- [ ] **Step 3: Remove `OLEDColors` from `Color.kt` and verify zero references**

Run: `git grep -n "OLEDColors" app/src/`
Expected: No output.

- [ ] **Step 4: Run full unit test suite to verify compilation and all existing UI tests pass**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/ui/
git commit -m "refactor: migrate all UI screens and components to FinanceTheme.colors"
```

---

### Task 4: ViewModel Wiring, `SettingsScreen` & 5th Bottom Tab

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModel.kt`
- Create: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MainScreen.kt`
- Create: `app/src/test/java/com/mcalonzo/personalfinanceos/ui/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `ThemeMode`, `ThemePreferenceStore`, `InMemoryThemePreferenceStore` (Task 1); `FinanceTheme.colors` (Task 2).
- Produces:
  - `BottomTab.SETTINGS`
  - `MainUiState.themeMode: ThemeMode = ThemeMode.SYSTEM`
  - `MainViewModel.selectThemeMode(mode: ThemeMode)`
  - `@Composable fun SettingsScreen(themeMode: ThemeMode, onSelectThemeMode: (ThemeMode) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write failing tests in `SettingsScreenTest.kt`**

Write two tests using Robolectric + ComposeTestRule:
1. `SettingsScreen` displays `Settings` header, `Appearance` section, and `System` / `Light` / `Dark` options; tapping `Light` invokes `onSelectThemeMode(ThemeMode.LIGHT)` and tapping `Dark` invokes `onSelectThemeMode(ThemeMode.DARK)`.
2. `BottomNavigationBar` / `MainScreen` renders the 5th `Settings` tab and selecting it displays `SettingsScreen` without showing the Budget FAB.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.SettingsScreenTest"`
Expected: FAIL with unresolved reference `SettingsScreen` / `BottomTab.SETTINGS`.

- [ ] **Step 3: Wire `ThemePreferenceStore` into `MainViewModel.kt`, create `SettingsScreen.kt`, and add `Settings` tab to `MainScreen.kt`**

- Add `SETTINGS` to `BottomTab`.
- Add `val themeMode: ThemeMode = ThemeMode.SYSTEM` to `MainUiState`.
- Inject `themePreferenceStore: ThemePreferenceStore = InMemoryThemePreferenceStore()` into `MainViewModel` (defaulted in `internal constructor` so existing tests stay intact), collect `themePreferenceStore.observeThemeMode()` into `MainUiState.themeMode`, and add `fun selectThemeMode(mode: ThemeMode)` calling `themePreferenceStore.setThemeMode(mode)`.
- Implement `SettingsScreen` with an `Appearance` card containing a 3-way segmented control (`System`, `Light`, `Dark`) using `FinanceTheme.colors`.
- Add `BottomIconNavItem(icon = Icons.Rounded.Settings, label = "Settings", ...)` in `MainScreen.kt` and handle `BottomTab.SETTINGS` in the `when (state.selectedBottomTab)` block.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ui.SettingsScreenTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/ui/viewmodel/MainViewModel.kt app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/SettingsScreen.kt app/src/main/java/com/mcalonzo/personalfinanceos/ui/screens/MainScreen.kt app/src/test/java/com/mcalonzo/personalfinanceos/ui/SettingsScreenTest.kt
git commit -m "feat: add Settings bottom tab and Appearance theme selector"
```

---

### Task 5: Reactive Edge-to-Edge System Bars, Cold-Start Themes & Feature Map

**Files:**
- Modify: `app/src/main/java/com/mcalonzo/personalfinanceos/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Modify: `docs/FEATURE_MAP.md`
- Test: `app/src/test/java/com/mcalonzo/personalfinanceos/ThemeAndStartupConfigTest.kt`

**Interfaces:**
- Consumes: `MainUiState.themeMode`, `PersonalFinanceTheme(darkTheme = ...)`.
- Produces: Pure helper `fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean` tested across all 6 `(ThemeMode, systemInDarkTheme)` combinations.

- [ ] **Step 1: Add failing unit test for `resolveDarkTheme` in `ThemeAndStartupConfigTest.kt`**

Test all 6 combinations: `(SYSTEM, true) -> true`, `(SYSTEM, false) -> false`, `(LIGHT, true) -> false`, `(LIGHT, false) -> false`, `(DARK, true) -> true`, `(DARK, false) -> true`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.mcalonzo.personalfinanceos.ThemeAndStartupConfigTest"`
Expected: FAIL with unresolved reference `resolveDarkTheme`.

- [ ] **Step 3: Update `MainActivity.kt`, split `themes.xml` / `values-night/themes.xml`, and update `docs/FEATURE_MAP.md`**

- In `MainActivity.kt`: observe `uiState.themeMode`, compute `val isDarkTheme = resolveDarkTheme(uiState.themeMode, isSystemInDarkTheme())`, pass `darkTheme = isDarkTheme` to `PersonalFinanceTheme`, and update `enableEdgeToEdge` inside `SideEffect` (`SystemBarStyle.dark(AndroidColor.TRANSPARENT)` vs `SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)`).
- Move dark `#06090E` / `@android:color/black` attributes to `res/values-night/themes.xml` and set light `#F7F6F2` window/bar attributes in `res/values/themes.xml`.
- Add `FM-010 | App Settings & Light/Dark Theme | Core | BottomTab.SETTINGS -> SettingsScreen | ... | SharedPreferences, Compose M3 | verified` to `docs/FEATURE_MAP.md`.

- [ ] **Step 4: Run full test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass with 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mcalonzo/personalfinanceos/MainActivity.kt app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml docs/FEATURE_MAP.md app/src/test/java/com/mcalonzo/personalfinanceos/ThemeAndStartupConfigTest.kt
git commit -m "feat: wire reactive edge-to-edge bars, day/night splash theme, and feature map"
```
