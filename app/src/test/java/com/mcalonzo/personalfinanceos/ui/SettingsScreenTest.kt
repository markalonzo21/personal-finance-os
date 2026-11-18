package com.mcalonzo.personalfinanceos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.ThemeMode
import com.mcalonzo.personalfinanceos.ui.screens.MainScreen
import com.mcalonzo.personalfinanceos.ui.screens.SettingsScreen
import com.mcalonzo.personalfinanceos.ui.theme.DarkOLEDColors
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.theme.LightWarmPaperColors
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.BottomTab
import com.mcalonzo.personalfinanceos.ui.viewmodel.MainUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `SettingsScreen renders Appearance options, exposes selected semantics, and invokes callback`() {
        val selectedModes = mutableListOf<ThemeMode>()

        composeRule.setContent {
            PersonalFinanceTheme(darkTheme = true) {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onSelectThemeMode = { selectedModes.add(it) }
                )
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("System").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("Light").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("Dark").assertIsNotSelected().performClick()

        assertThat(selectedModes).containsExactly(ThemeMode.LIGHT, ThemeMode.DARK).inOrder()
    }

    @Test
    fun `MainScreen renders all 5 bottom tabs and updates theme colors live on SettingsScreen`() {
        var currentState by mutableStateOf(
            MainUiState(
                selectedBottomTab = BottomTab.BUDGET,
                themeMode = ThemeMode.DARK
            )
        )
        var observedBg: Color? = null

        composeRule.setContent {
            val isDark = currentState.themeMode != ThemeMode.LIGHT
            PersonalFinanceTheme(darkTheme = isDark) {
                observedBg = FinanceTheme.colors.bgBase
                MainScreen(
                    state = currentState,
                    onSelectBottomTab = { tab -> currentState = currentState.copy(selectedBottomTab = tab) },
                    onSelectThemeMode = { mode -> currentState = currentState.copy(themeMode = mode) }
                )
            }
        }

        composeRule.onNodeWithText("Budget").assertIsDisplayed()
        composeRule.onNodeWithText("Reminders").assertIsDisplayed()
        composeRule.onNodeWithText("Moto Care").assertIsDisplayed()
        composeRule.onNodeWithText("Routine").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Log Expense").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        assertThat(currentState.selectedBottomTab).isEqualTo(BottomTab.SETTINGS)
        assertThat(observedBg).isEqualTo(DarkOLEDColors.bgBase)
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Log Expense").assertDoesNotExist()

        composeRule.onNodeWithText("Light").performClick()
        composeRule.waitForIdle()

        assertThat(currentState.selectedBottomTab).isEqualTo(BottomTab.SETTINGS)
        assertThat(currentState.themeMode).isEqualTo(ThemeMode.LIGHT)
        assertThat(observedBg).isEqualTo(LightWarmPaperColors.bgBase)
    }
}
