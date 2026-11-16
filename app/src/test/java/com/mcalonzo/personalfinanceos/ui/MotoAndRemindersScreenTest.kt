package com.mcalonzo.personalfinanceos.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.ui.screens.MotoCareScreen
import com.mcalonzo.personalfinanceos.ui.screens.RemindersScreen
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.DueReminderItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.MotoUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.PmsItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.RemindersUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MotoAndRemindersScreenTest {

    @get:Rule
    val rule = createComposeRule()

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
        rule.setContent {
            PersonalFinanceTheme {
                MotoCareScreen(
                    state = motoState,
                    onLogFullTank = {},
                    onRefreshGas = {},
                    onEditOdometer = {},
                    onOverridePrice = {},
                    onAddPms = {}
                )
            }
        }
        rule.onNodeWithText("Honda Click 160").assertIsDisplayed()
        rule.onNodeWithText("Petron Fairview").assertIsDisplayed()
        rule.onNodeWithText("Premium 95").assertIsDisplayed()
        rule.onNodeWithText("▲ +₱0.45").assertIsDisplayed()
        rule.onNodeWithText("₱431.20").assertIsDisplayed()
        rule.onNodeWithText("Commonwealth Ave", substring = true).assertDoesNotExist()
        rule.onNodeWithText("gaswatchph", substring = true).assertDoesNotExist()
    }

    @Test
    fun `moto care log full tank invokes callback`() {
        var logFullTankCalled = false
        val motoState = MotoUiState(
            modelName = "Honda Click 160",
            odometerKm = 8450,
            fullTankCostFormatted = "₱431.20"
        )
        rule.setContent {
            PersonalFinanceTheme {
                MotoCareScreen(
                    state = motoState,
                    onLogFullTank = { logFullTankCalled = true },
                    onRefreshGas = {},
                    onEditOdometer = {},
                    onOverridePrice = {},
                    onAddPms = {}
                )
            }
        }
        rule.onNodeWithText("Log Full Tank").performClick()
        assertThat(logFullTankCalled).isTrue()
    }

    @Test
    fun `moto care tapping odometer badge opens dialog and submits updated reading`() {
        var updatedOdo: Int? = null
        val motoState = MotoUiState(
            modelName = "Honda Click 160",
            odometerKm = 8450
        )
        rule.setContent {
            PersonalFinanceTheme {
                MotoCareScreen(
                    state = motoState,
                    onLogFullTank = {},
                    onRefreshGas = {},
                    onEditOdometer = { updatedOdo = it },
                    onOverridePrice = {},
                    onAddPms = {}
                )
            }
        }
        rule.onNodeWithText("8,450 km").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("8600")
        rule.onNodeWithText("Save").performClick()
        assertThat(updatedOdo).isEqualTo(8600)
    }

    @Test
    fun `moto care tapping fuel price opens override dialog and submits centavos`() {
        var overrideCentavos: Long? = null
        val motoState = MotoUiState(
            pricePerLiterFormatted = "₱78.40",
            pricePerLiterCentavos = 7840L
        )
        rule.setContent {
            PersonalFinanceTheme {
                MotoCareScreen(
                    state = motoState,
                    onLogFullTank = {},
                    onRefreshGas = {},
                    onEditOdometer = {},
                    onOverridePrice = { overrideCentavos = it },
                    onAddPms = {}
                )
            }
        }
        rule.onNodeWithText("₱78.40").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("79.50")
        rule.onNodeWithText("Save").performClick()
        assertThat(overrideCentavos).isEqualTo(7950L)
    }

    @Test
    fun `moto care displays PMS schedule cards and logs service`() {
        var servicedPmsId: Long? = null
        var servicedKm: Int? = null
        var serviceCost: Long? = null

        val pms = PmsItemUi(
            id = 101L,
            taskName = "Honda 4T SL 10W-30 Engine Oil",
            iconEmoji = "🛢️",
            intervalKm = 1500,
            lastServiceOdometerKm = 7500,
            remainingKm = 550,
            isOverdue = false,
            dueStatusText = "Due in 550 km"
        )
        val motoState = MotoUiState(
            odometerKm = 8450,
            pmsItems = listOf(pms)
        )
        rule.setContent {
            PersonalFinanceTheme {
                MotoCareScreen(
                    state = motoState,
                    onLogFullTank = {},
                    onRefreshGas = {},
                    onEditOdometer = {},
                    onOverridePrice = {},
                    onAddPms = {},
                    onLogPmsService = { id, km, cost ->
                        servicedPmsId = id
                        servicedKm = km
                        serviceCost = cost
                    }
                )
            }
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        rule.onNodeWithText("Honda 4T SL 10W-30 Engine Oil").assertIsDisplayed()
        rule.onNodeWithText("Due in 550 km").assertIsDisplayed()
        rule.onNodeWithText("Log Service").performClick()
        rule.onNodeWithText("Confirm Service").performClick()

        assertThat(servicedPmsId).isEqualTo(101L)
        assertThat(servicedKm).isEqualTo(8450)
        assertThat(serviceCost).isEqualTo(0L)
    }

    @Test
    fun `reminders screen displays global push switch and triggers toggle`() {
        var pushToggledValue: Boolean? = null
        val remindersState = RemindersUiState(
            globalPushEnabled = true,
            items = emptyList()
        )
        rule.setContent {
            PersonalFinanceTheme {
                RemindersScreen(
                    state = remindersState,
                    onToggleGlobalPush = { pushToggledValue = it },
                    onPreviewDueAlert = {},
                    onSettleBill = {},
                    onSettleLoan = {},
                    onUpdateCadence = { _, _, _, _, _, _ -> }
                )
            }
        }
        rule.onNodeWithText("Push Notifications").assertIsDisplayed()
        rule.onNodeWithContentDescription("Global Push Toggle").performClick()
        assertThat(pushToggledValue).isEqualTo(false)
    }

    @Test
    fun `reminders screen displays chronological overdue and upcoming timeline`() {
        val overdueItem = DueReminderItemUi(
            id = 1L,
            itemKey = "bill_1",
            title = "Converge FiberX",
            iconEmoji = "🌐",
            amountCentavos = 150_000L,
            amountFormatted = "₱1,500.00",
            isLoan = false,
            dueDateLabel = "Nov 10",
            dueDayOfMonth = 10,
            daysUntilDue = -4L,
            isOverdue = true,
            overdueDays = 4,
            isPaid = false,
            dueStatusText = "Overdue by 4 days"
        )
        val upcomingItem = DueReminderItemUi(
            id = 2L,
            itemKey = "loan_2",
            title = "Honda Click 160 Loan",
            iconEmoji = "🛵",
            amountCentavos = 485_000L,
            amountFormatted = "₱4,850.00",
            isLoan = true,
            dueDateLabel = "Nov 20",
            dueDayOfMonth = 20,
            daysUntilDue = 6L,
            isOverdue = false,
            overdueDays = 0,
            isPaid = false,
            dueStatusText = "Due in 6 days"
        )
        val remindersState = RemindersUiState(
            globalPushEnabled = true,
            items = listOf(overdueItem, upcomingItem)
        )
        rule.setContent {
            PersonalFinanceTheme {
                RemindersScreen(
                    state = remindersState,
                    onToggleGlobalPush = {},
                    onPreviewDueAlert = {},
                    onSettleBill = {},
                    onSettleLoan = {},
                    onUpdateCadence = { _, _, _, _, _, _ -> }
                )
            }
        }
        rule.onNodeWithText("Converge FiberX").assertIsDisplayed()
        rule.onNodeWithText("Overdue by 4 days").assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        rule.onNodeWithText("Honda Click 160 Loan").assertIsDisplayed()
        rule.onNodeWithText("Due in 6 days").assertIsDisplayed()
    }

    @Test
    fun `reminders screen tapping reminder opens cadence sheet and saves configuration`() {
        var updatedItemKey: String? = null
        var updated7d: Boolean? = null

        val item = DueReminderItemUi(
            id = 1L,
            itemKey = "bill_1",
            title = "Converge FiberX",
            iconEmoji = "🌐",
            amountCentavos = 150_000L,
            amountFormatted = "₱1,500.00",
            isLoan = false,
            dueDateLabel = "Nov 10",
            dueDayOfMonth = 10,
            daysUntilDue = -4L,
            isOverdue = true,
            overdueDays = 4,
            isPaid = false,
            dueStatusText = "Overdue by 4 days",
            notify7d = true,
            notify3d = true,
            notify1d = true,
            notifyOnDue = true,
            notifyDailyOverdue = true
        )
        val remindersState = RemindersUiState(
            globalPushEnabled = true,
            items = listOf(item)
        )
        rule.setContent {
            PersonalFinanceTheme {
                RemindersScreen(
                    state = remindersState,
                    onToggleGlobalPush = {},
                    onPreviewDueAlert = {},
                    onSettleBill = {},
                    onSettleLoan = {},
                    onUpdateCadence = { key, n7d, _, _, _, _ ->
                        updatedItemKey = key
                        updated7d = n7d
                    }
                )
            }
        }
        rule.onNodeWithText("Configure Alert").performClick()
        rule.onNodeWithText("Reminder Cadence").assertIsDisplayed()
        rule.onNodeWithText("7 days before")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Save Cadence")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(updatedItemKey).isEqualTo("bill_1")
        assertThat(updated7d).isEqualTo(false)
    }

    @Test
    fun `reminders screen preview alert triggers preview callback`() {
        var previewTriggered = false
        val remindersState = RemindersUiState(
            globalPushEnabled = true,
            items = emptyList()
        )
        rule.setContent {
            PersonalFinanceTheme {
                RemindersScreen(
                    state = remindersState,
                    onToggleGlobalPush = {},
                    onPreviewDueAlert = { previewTriggered = true },
                    onSettleBill = {},
                    onSettleLoan = {},
                    onUpdateCadence = { _, _, _, _, _, _ -> }
                )
            }
        }
        rule.onNodeWithText("Preview Alert").performClick()
        assertThat(previewTriggered).isTrue()
    }
}
