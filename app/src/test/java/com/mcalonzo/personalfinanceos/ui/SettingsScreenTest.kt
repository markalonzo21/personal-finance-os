package com.mcalonzo.personalfinanceos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.ThemeMode
import com.mcalonzo.personalfinanceos.ui.screens.MainScreen
import com.mcalonzo.personalfinanceos.ui.screens.SettingsScreen
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
    fun `SettingsScreen renders Appearance options and invokes callback on selection`() {
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
        composeRule.onNodeWithText("System").assertIsDisplayed()
        composeRule.onNodeWithText("Light").performClick()
        composeRule.onNodeWithText("Dark").performClick()

        assertThat(selectedModes).containsExactly(ThemeMode.LIGHT, ThemeMode.DARK).inOrder()
    }

    @Test
    fun `MainScreen shows Settings tab in bottom bar and switches to SettingsScreen`() {
        var currentState by mutableStateOf(MainUiState(selectedBottomTab = BottomTab.BUDGET))

        composeRule.setContent {
            PersonalFinanceTheme(darkTheme = false) {
                MainScreen(
                    state = currentState,
                    onSelectBottomTab = { tab -> currentState = currentState.copy(selectedBottomTab = tab) },
                    onSelectThemeMode = { mode -> currentState = currentState.copy(themeMode = mode) }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Log Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        assertThat(currentState.selectedBottomTab).isEqualTo(BottomTab.SETTINGS)
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Log Expense").assertDoesNotExist()
    }
}
