package com.mcalonzo.personalfinanceos.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.domain.model.UndoAction
import com.mcalonzo.personalfinanceos.ui.screens.MainScreen
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.BillItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.BottomTab
import com.mcalonzo.personalfinanceos.ui.viewmodel.BudgetSubTab
import com.mcalonzo.personalfinanceos.ui.viewmodel.BudgetUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.CycleOptionUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.LoanItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.MainUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.RoutineUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.TransactionItemUi
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BudgetScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `budget screen renders safe to spend hero without dot, 4 equal tabs without top routine button, and peso nav glyph`() {
        val sampleState = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = "November 2026",
                safeToSpendFormatted = "₱18,650.00",
                dailyPaceFormatted = "₱1,097 / day",
                overdueBillsCount = 1,
                envelopes = listOf(
                    EnvelopeItemUi(1L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 880_000L)
                )
            )
        )
        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = sampleState,
                    onSelectBottomTab = {},
                    onOpenExpenseSheet = {},
                    onOpenRoutineSheet = {}
                )
            }
        }
        rule.onNodeWithText("SAFE-TO-SPEND BALANCE").assertIsDisplayed()
        rule.onNodeWithText("ENVELOPES").assertIsDisplayed()
        rule.onNodeWithText("Envelopes").assertIsDisplayed()
        rule.onNodeWithText("Bills").assertIsDisplayed()
        rule.onNodeWithText("Loans").assertIsDisplayed()
        rule.onNodeWithText("Activity").assertIsDisplayed()
        rule.onNodeWithText("1!").assertIsDisplayed()
        rule.onNodeWithText("₱").assertIsDisplayed()
        rule.onAllNodesWithText("Routine").assertCountEquals(1) // bottom nav bar only
    }

    @Test
    fun `four budget sub tabs switch views and wire bill settlement, loan advance, and transaction delete`() {
        var subTab by mutableStateOf(BudgetSubTab.ENVELOPES)
        var settledBillCall: Pair<Long, Long?>? = null
        var settledLoanCall: Pair<Long, Boolean>? = null
        var deletedTxId: Long? = null

        val bills = listOf(
            BillItemUi(
                id = 10L,
                name = "Meralco",
                iconEmoji = "⚡",
                amountCentavos = 450_000L,
                dueDayOfMonth = 11,
                dueDateLabel = "Nov 11",
                isPaid = false,
                isOverdue = true,
                overdueDays = 3,
                daysUntilDue = -3L
            )
        )
        val loans = listOf(
            LoanItemUi(
                id = 20L,
                name = "NMAX Installment",
                iconEmoji = "🛵",
                monthlyAmortizationCentavos = 850_000L,
                totalInstallments = 24,
                paidInstallments = 10,
                dueDayOfMonth = 20,
                dueDateLabel = "Nov 20",
                nextDueYearMonth = "2026-11",
                isPaidForCycle = false,
                isOverdue = false,
                overdueDays = 0,
                daysUntilDue = 6L
            ),
            LoanItemUi(
                id = 21L,
                name = "Credit Cash Loan",
                iconEmoji = "🏦",
                monthlyAmortizationCentavos = 420_000L,
                totalInstallments = 12,
                paidInstallments = 4,
                dueDayOfMonth = 5,
                dueDateLabel = "Nov 5",
                nextDueYearMonth = "2026-12",
                isPaidForCycle = true,
                isOverdue = false,
                overdueDays = 0,
                daysUntilDue = 21L
            )
        )
        val transactions = listOf(
            TransactionItemUi(
                id = 30L,
                title = "Petron Fuel",
                categoryLabel = "Gas & Fuel",
                amountCentavos = 25_000L,
                timestampEpochMs = 1_763_100_000_000L,
                timeLabel = "Nov 14"
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = MainUiState(
                        budgetState = BudgetUiState(
                            yearMonthLabel = "November 2026",
                            selectedYearMonth = "2026-11",
                            committedLoansFormatted = "₱8,500.00",
                            overdueBillsCount = 1,
                            bills = bills,
                            loans = loans,
                            transactions = transactions,
                            selectedSubTab = subTab
                        )
                    ),
                    onSelectBudgetSubTab = { subTab = it },
                    onSettleBill = { id, override -> settledBillCall = id to override },
                    onSettleLoan = { id, advance -> settledLoanCall = id to advance },
                    onDeleteTransaction = { deletedTxId = it }
                )
            }
        }

        // Switch to Bills sub-tab and confirm bill payment
        rule.onNodeWithText("Bills").performClick()
        rule.onNodeWithText("RECURRING BILLS").assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        rule.onNodeWithText("Overdue by 3 days").assertIsDisplayed()
        rule.onNodeWithText("Pay Now").performClick()
        rule.onNodeWithText("Confirm Bill Payment").assertIsDisplayed()
        rule.onNodeWithText("Confirm Payment (₱4,500.00)")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(settledBillCall).isEqualTo(10L to null)

        // Switch to Loans sub-tab and test Mark Paid + Pay Advance confirmation dialog
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.onNodeWithText("Loans").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        rule.onNodeWithText("MONTHLY DEBT & OBLIGATIONS").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        rule.onNodeWithText("Mark Paid").performClick()
        rule.onNodeWithText("Confirm Loan Payment").assertIsDisplayed()
        rule.onNodeWithText("Confirm Paid").performClick()
        assertThat(settledLoanCall).isEqualTo(20L to false)

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        rule.onNodeWithText("Pay Advance").performClick()
        rule.onNodeWithText("Confirm Advance Payment").assertIsDisplayed()
        rule.onAllNodesWithText("Pay Advance")[1]
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(settledLoanCall).isEqualTo(21L to true)

        // Switch to Activity sub-tab and trigger transaction delete with confirmation
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.onNodeWithText("Activity").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        rule.onNodeWithContentDescription("Delete Petron Fuel").performClick()
        rule.onNodeWithText("Delete Expense?").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        assertThat(deletedTxId).isEqualTo(30L)
    }

    @Test
    fun `fab opens expense sheet and submits quick increment expense with optional title`() {
        var fabOpened = false
        var loggedExpense: Triple<Long, Long, String>? = null

        val state = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = "November 2026",
                envelopes = listOf(
                    EnvelopeItemUi(1L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 0L),
                    EnvelopeItemUi(2L, "Gas & Fuel", "⛽", "#34D399", 300_000L, 0L, isGasEnvelope = true)
                )
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = state,
                    onOpenExpenseSheet = { fabOpened = true },
                    onLogExpense = { envId, amount, title ->
                        loggedExpense = Triple(envId, amount, title)
                    }
                )
            }
        }

        rule.onNodeWithContentDescription("Log Expense").performClick()
        assertThat(fabOpened).isTrue()
        rule.onNodeWithText("+ Log Daily Expense").assertIsDisplayed()

        rule.onNodeWithText("+₱250").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("+₱50").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(hasText("Note / Merchant (optional)") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("Full Tank")
        rule.onNodeWithText("Gas & Fuel").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Confirm & Deduct (₱300.00)")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(loggedExpense).isEqualTo(Triple(2L, 30_000L, "Full Tank"))
    }

    @Test
    fun `tapping envelope icon opens emoji picker supporting custom icon input`() {
        var updatedIcon: Pair<Long, String>? = null
        val state = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = "November 2026",
                envelopes = listOf(
                    EnvelopeItemUi(5L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 100_000L)
                )
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = state,
                    onUpdateEnvelopeIcon = { id, icon -> updatedIcon = id to icon }
                )
            }
        }

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        rule.onNodeWithText("🛒").performClick()
        rule.onNodeWithText("Choose Groceries Icon").assertIsDisplayed()
        rule.onNode(hasText("Custom emoji") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("🎁")
        rule.onNodeWithText("Apply")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(updatedIcon).isEqualTo(5L to "🎁")
    }

    @Test
    fun `routine bottom nav button opens routine sheet with default current month apply and editable total budget`() {
        var routineTriggered = false
        var savedApplyFlag: Boolean? = null
        var savedTotalBudget: Long? = null

        val state = MainUiState(
            budgetState = BudgetUiState(yearMonthLabel = "November 2026"),
            routineState = RoutineUiState(
                totalMonthlyBudgetCentavos = 5_000_000L,
                autoRolloverEnabled = true,
                envelopeTemplates = listOf(
                    RoutineEnvelopeTemplateEntity(
                        id = 1L,
                        name = "Groceries",
                        iconEmoji = "🛒",
                        colorHex = "#38BDF8",
                        allocatedLimitCentavos = 1_500_000L,
                        isGasEnvelope = false,
                        sortOrder = 0
                    )
                ),
                billTemplates = listOf(
                    RoutineBillTemplateEntity(
                        id = 1L,
                        name = "Converge FiberX",
                        iconEmoji = "📶",
                        defaultAmountCentavos = 150_000L,
                        dueDayOfMonth = 15,
                        isActive = true
                    )
                )
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = state,
                    onOpenRoutineSheet = { routineTriggered = true },
                    onSaveRoutine = { total, _, _, _, applyNow ->
                        savedTotalBudget = total
                        savedApplyFlag = applyNow
                    }
                )
            }
        }

        rule.onNodeWithText("Routine").performClick()
        assertThat(routineTriggered).isTrue()
        rule.onNodeWithText("Monthly Routine Template").assertIsDisplayed()

        rule.onNode(hasText("Current Routine Total Budget (PHP)") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("60000")
        rule.onNodeWithText("Save Routine Template")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedTotalBudget).isEqualTo(6_000_000L)
        assertThat(savedApplyFlag).isTrue()
    }

    @Test
    fun `soa pill opens pdf viewer sheet and active undo triggers undo callback`() {
        var pickerLaunched = false
        var undoClicked = false

        val state = MainUiState(
            budgetState = BudgetUiState(yearMonthLabel = "November 2026"),
            activeUndo = UndoAction.ExpenseLogged(
                transactionId = 99L,
                cycleId = 1L,
                envelopeId = 1L,
                amountCentavos = 50_000L,
                label = "Groceries ₱500.00"
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = state,
                    onPickSoaPdf = { pickerLaunched = true },
                    onUndoLast = { undoClicked = true }
                )
            }
        }

        rule.onNodeWithText("UNDO").assertIsDisplayed().performClick()
        assertThat(undoClicked).isTrue()

        rule.onNodeWithText("Attach SOA").performClick()
        rule.onNodeWithText("No SOA PDF attached yet").assertIsDisplayed()
        rule.onAllNodesWithText("Upload SOA PDF")[0]
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(pickerLaunched).isTrue()
    }

    @Test
    fun `bottom navigation switches between budget, reminders, and moto care screens`() {
        var bottomTab by mutableStateOf(BottomTab.BUDGET)

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = MainUiState(
                        selectedBottomTab = bottomTab,
                        budgetState = BudgetUiState(
                            yearMonthLabel = "November 2026",
                            overdueBillsCount = 2
                        )
                    ),
                    onSelectBottomTab = { bottomTab = it }
                )
            }
        }

        rule.onNodeWithText("Reminders").performClick()
        rule.onNodeWithText("Bill & Loan Reminders").assertIsDisplayed()

        rule.onNodeWithText("Moto Care").performClick()
        rule.onAllNodesWithText("Moto Care").assertCountEquals(2)
        rule.onNodeWithText("Honda Click 160").assertIsDisplayed()
    }

    @Test
    fun `month picker switches budget cycle and add category sheet submits new envelope`() {
        var selectedMonth: YearMonth? = null
        var addedEnvelope: Triple<String, Long, Boolean>? = null

        val state = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = "November 2026",
                selectedYearMonth = "2026-11",
                availableCycles = listOf(
                    CycleOptionUi(YearMonth.of(2026, 10), "October 2026", hasSoa = false),
                    CycleOptionUi(YearMonth.of(2026, 11), "November 2026", hasSoa = true)
                )
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = state,
                    onSelectMonth = { selectedMonth = it },
                    onAddEnvelope = { name, _, limit, _, addToRoutine ->
                        addedEnvelope = Triple(name, limit, addToRoutine)
                    }
                )
            }
        }

        rule.onNodeWithText("November 2026").performClick()
        rule.onNodeWithText("Select Budget Cycle").assertIsDisplayed()
        rule.onNodeWithText("October 2026")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(selectedMonth).isEqualTo(YearMonth.of(2026, 10))

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        rule.onNodeWithText("+ Add Category").performClick()
        rule.onNodeWithText("+ Add New Category").assertIsDisplayed()
        rule.onNode(hasText("Category Name") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("Health")
        rule.onNode(hasText("Monthly Limit (PHP)") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("4500")
        rule.onNodeWithText("Also save to Monthly Routine template")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Add Envelope")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(addedEnvelope).isEqualTo(Triple("Health", 450_000L, true))
    }

    @Test
    fun `eye icon toggles hiding and showing monetary amounts on budget screen`() {
        var hidden by mutableStateOf(false)

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = MainUiState(
                        budgetState = BudgetUiState(
                            yearMonthLabel = "November 2026",
                            safeToSpendFormatted = "₱18,650.00",
                            dailyPaceFormatted = "₱1,097 / day",
                            totalBudgetFormatted = "₱50,000.00",
                            spentSoFarFormatted = "₱12,000.00",
                            committedLoansFormatted = "₱8,500.00",
                            envelopes = listOf(
                                EnvelopeItemUi(1L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 500_000L)
                            ),
                            isAmountsHidden = hidden
                        )
                    ),
                    onToggleAmountsVisibility = { hidden = !hidden }
                )
            }
        }

        rule.onNodeWithText("₱18,650.00").assertIsDisplayed()
        rule.onNodeWithContentDescription("Hide amounts").performClick()
        assertThat(hidden).isTrue()

        rule.onNodeWithText("₱••••••").assertIsDisplayed()
        rule.onNodeWithText("₱•••• / day").assertIsDisplayed()
        rule.onNodeWithContentDescription("Show amounts").performClick()
        assertThat(hidden).isFalse()
        rule.onNodeWithText("₱18,650.00").assertIsDisplayed()
    }

    @Test
    fun `envelope card expands added items list and wires edit move envelope and delete per item`() {
        var editedItem: List<Any?>? = null
        var deletedItemId: Long? = null

        val state = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = "November 2026",
                envelopes = listOf(
                    EnvelopeItemUi(1L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 85_000L),
                    EnvelopeItemUi(2L, "Gas & Fuel", "⛽", "#34D399", 500_000L, 0L)
                ),
                transactions = listOf(
                    TransactionItemUi(
                        id = 101L,
                        title = "SM Supermarket",
                        categoryLabel = "Groceries",
                        amountCentavos = 85_000L,
                        timestampEpochMs = 1_763_100_000_000L,
                        timeLabel = "Nov 14",
                        envelopeId = 1L
                    )
                )
            )
        )

        rule.setContent {
            PersonalFinanceTheme {
                MainScreen(
                    state = state,
                    onUpdateTransaction = { id, title, amount, newEnvId ->
                        editedItem = listOf(id, title, amount, newEnvId)
                    },
                    onDeleteTransaction = { id ->
                        deletedItemId = id
                    }
                )
            }
        }

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        rule.onNodeWithText("1 added item").assertIsDisplayed()
        rule.onNodeWithContentDescription("Toggle Groceries items").performClick()

        rule.onNodeWithText("SM Supermarket").assertIsDisplayed()
        rule.onNodeWithContentDescription("Edit SM Supermarket").performClick()
        rule.onNodeWithText("Edit Envelope Item").assertIsDisplayed()
        rule.onNode(hasText("Item / Note") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("Puregold")
        rule.onNode(hasText("Amount (PHP)") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("950")
        rule.onAllNodesWithText("Gas & Fuel")[1]
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Save Item")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(editedItem).isEqualTo(listOf(101L, "Puregold", 95_000L, 2L))

        rule.onNodeWithContentDescription("Delete SM Supermarket")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Delete Expense?").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        assertThat(deletedItemId).isEqualTo(101L)
    }
}
