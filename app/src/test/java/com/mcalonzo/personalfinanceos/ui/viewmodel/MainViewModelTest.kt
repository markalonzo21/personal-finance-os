package com.mcalonzo.personalfinanceos.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.SynchronousExecutor
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import com.mcalonzo.personalfinanceos.data.repository.BudgetRepository
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository
import com.mcalonzo.personalfinanceos.domain.usecase.MonthRolloverUseCase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var db: AppDatabase
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var gasPriceRepository: GasPriceRepository
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var viewModel: MainViewModel

    private val fixedToday: LocalDate = LocalDate.of(2026, 11, 14)

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(testDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2026, 11) })
            .setQueryExecutor(SynchronousExecutor())
            .setTransactionExecutor(SynchronousExecutor())
            .allowMainThreadQueries()
            .build()

        val rolloverUseCase = MonthRolloverUseCase(db)
        budgetRepository = BudgetRepository(db, rolloverUseCase)
        gasPriceRepository = GasPriceRepository(
            motoDao = db.motoDao(),
            okHttpClient = OkHttpClient(),
            targetUrl = "https://localhost.invalid/gaswatch"
        )

        viewModelStore = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    budgetRepository = budgetRepository,
                    gasPriceRepository = gasPriceRepository,
                    todayProvider = { fixedToday },
                    undoTimeoutMs = 5_000L
                ) as T
            }
        }
        viewModel = ViewModelProvider(viewModelStore, factory)[MainViewModel::class.java]
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
        db.close()
    }

    private fun pumpUntil(
        maxSteps: Int = 60,
        predicate: (MainUiState) -> Boolean
    ): MainUiState {
        repeat(maxSteps) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            testDispatcher.scheduler.runCurrent()
            val current = viewModel.uiState.value
            if (predicate(current)) {
                return current
            }
        }
        error("Condition not satisfied after $maxSteps pump cycles. Last state: ${viewModel.uiState.value}")
    }

    @Test
    fun `initial uiState loads seeded november cycle, overdue convergebill, and loan commitments`() = runTest(testDispatcher.scheduler) {
        val state = pumpUntil { !it.isLoading && it.budgetState.envelopes.isNotEmpty() }

        assertThat(state.budgetState.yearMonthLabel).isEqualTo("November 2026")
        assertThat(state.budgetState.totalBudgetFormatted).isEqualTo("₱50,000.00")
        assertThat(state.budgetState.committedLoansFormatted).isEqualTo("₱8,350.00")
        assertThat(state.budgetState.safeToSpendFormatted).isEqualTo("₱41,650.00")
        assertThat(state.budgetState.overdueBillsCount).isEqualTo(1)
        assertThat(state.budgetState.envelopes.map { it.name }).contains("Groceries")
        assertThat(state.budgetState.envelopes.first { it.isGasEnvelope }.name).isEqualTo("Motorcycle Gas")
        assertThat(state.budgetState.bills.first { it.name == "Converge FiberX" }.isOverdue).isTrue()
        assertThat(state.budgetState.activeLoansCount).isEqualTo(2)
        assertThat(state.routineState.envelopeTemplates).hasSize(5)
        assertThat(state.routineState.billTemplates).hasSize(4)
    }

    @Test
    fun `logExpense updates safeToSpend and auto-clears undo snackbar state after 5 seconds`() = runTest(testDispatcher.scheduler) {
        val initial = pumpUntil { !it.isLoading && it.budgetState.envelopes.isNotEmpty() }
        val groceries = initial.budgetState.envelopes.first { it.name == "Groceries" }

        viewModel.logExpense(envelopeId = groceries.id, amountCentavos = 150_000L, title = "Puregold")

        val afterLog = pumpUntil {
            it.activeUndo != null && it.budgetState.spentSoFarFormatted == "₱1,500.00"
        }
        assertThat(afterLog.budgetState.safeToSpendFormatted).isEqualTo("₱40,150.00")
        assertThat(afterLog.budgetState.transactions.first().title).isEqualTo("Puregold")
        assertThat(afterLog.activeUndo?.label).contains("Puregold")

        // Before 5s elapsed, undo stays active
        advanceTimeBy(4_900L)
        runCurrent()
        assertThat(viewModel.uiState.value.activeUndo).isNotNull()

        // Crossing 5s clears activeUndo automatically
        advanceTimeBy(100L)
        val afterTimeout = pumpUntil { it.activeUndo == null }
        assertThat(afterTimeout.budgetState.spentSoFarFormatted).isEqualTo("₱1,500.00")
    }

    @Test
    fun `undoLast within 5s atomically restores balance and removes logged transaction`() = runTest(testDispatcher.scheduler) {
        val initial = pumpUntil { !it.isLoading && it.budgetState.envelopes.isNotEmpty() }
        val groceries = initial.budgetState.envelopes.first { it.name == "Groceries" }

        viewModel.logExpense(envelopeId = groceries.id, amountCentavos = 200_000L, title = "SnR")
        pumpUntil { it.activeUndo != null && it.budgetState.transactions.size == 1 }

        viewModel.undoLast()
        val restored = pumpUntil {
            it.activeUndo == null &&
                it.budgetState.transactions.isEmpty() &&
                it.budgetState.spentSoFarFormatted == "₱0.00"
        }

        assertThat(restored.budgetState.safeToSpendFormatted).isEqualTo("₱41,650.00")
        assertThat(restored.infoMessage).contains("Undid")
    }

    @Test
    fun `settleBill with override amount and settleLoan advance payment update metrics`() = runTest(testDispatcher.scheduler) {
        val initial = pumpUntil { !it.isLoading && it.budgetState.bills.isNotEmpty() }
        val meralco = initial.budgetState.bills.first { it.name == "Meralco" }
        val clickLoan = initial.budgetState.loans.first { it.name == "Honda Click 160 Loan" }

        // Variable bill override (₱4,250.00 instead of default ₱3,800.00)
        viewModel.settleBill(billId = meralco.id, overrideAmountCentavos = 425_000L)
        val afterBill = pumpUntil {
            it.budgetState.bills.first { b -> b.id == meralco.id }.isPaid
        }
        assertThat(afterBill.budgetState.spentSoFarFormatted).isEqualTo("₱4,250.00")
        assertThat(afterBill.budgetState.safeToSpendFormatted).isEqualTo("₱37,400.00")

        // Pay current cycle loan installment
        viewModel.settleLoan(loanId = clickLoan.id, allowAdvancePayment = false)
        val afterLoan = pumpUntil {
            it.budgetState.loans.first { l -> l.id == clickLoan.id }.isPaidForCycle
        }
        assertThat(afterLoan.budgetState.committedLoansFormatted).isEqualTo("₱3,500.00")
        assertThat(afterLoan.budgetState.spentSoFarFormatted).isEqualTo("₱9,100.00")

        // Advance payment pays next month's installment too
        viewModel.settleLoan(loanId = clickLoan.id, allowAdvancePayment = true)
        val afterAdvance = pumpUntil {
            it.budgetState.loans.first { l -> l.id == clickLoan.id }.paidInstallments == clickLoan.paidInstallments + 2
        }
        assertThat(afterAdvance.budgetState.spentSoFarFormatted).isEqualTo("₱13,950.00")
        assertThat(afterAdvance.budgetState.safeToSpendFormatted).isEqualTo("₱32,550.00")
    }

    @Test
    fun `saveRoutine respects applyToCurrentMonth opt-in and selectMonth switches cycle`() = runTest(testDispatcher.scheduler) {
        val initial = pumpUntil { !it.isLoading && it.routineState.envelopeTemplates.isNotEmpty() }
        val updatedEnvelopes = initial.routineState.envelopeTemplates.map { env ->
            if (env.name == "Groceries") env.copy(allocatedLimitCentavos = 2_000_000L) else env
        }

        // 1. Save routine without applying to current month
        viewModel.saveRoutine(
            totalMonthlyBudgetCentavos = 6_000_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = updatedEnvelopes,
            billTemplates = initial.routineState.billTemplates,
            applyToCurrentMonth = false
        )
        val afterTemplateOnly = pumpUntil { it.infoMessage?.contains("upcoming") == true }
        assertThat(afterTemplateOnly.budgetState.totalBudgetFormatted).isEqualTo("₱50,000.00")

        // 2. Save routine WITH applyToCurrentMonth = true
        viewModel.saveRoutine(
            totalMonthlyBudgetCentavos = 6_000_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = updatedEnvelopes,
            billTemplates = initial.routineState.billTemplates,
            applyToCurrentMonth = true
        )
        val afterApplyNow = pumpUntil { it.budgetState.totalBudgetFormatted == "₱60,000.00" }
        assertThat(afterApplyNow.budgetState.envelopes.first { it.name == "Groceries" }.allocatedLimitCentavos)
            .isEqualTo(2_000_000L)

        // 3. Switch to next cycle (December 2026) and assert fresh unspent month picks up 60k routine
        viewModel.selectMonth(YearMonth.of(2026, 12))
        val decState = pumpUntil { it.budgetState.selectedYearMonth == "2026-12" && !it.isLoading }
        assertThat(decState.budgetState.yearMonthLabel).isEqualTo("December 2026")
        assertThat(decState.budgetState.totalBudgetFormatted).isEqualTo("₱60,000.00")
        assertThat(decState.budgetState.spentSoFarFormatted).isEqualTo("₱0.00")
    }

    @Test
    fun `envelope CRUD and icon update mutate active cycle reactively`() = runTest(testDispatcher.scheduler) {
        val initial = pumpUntil { !it.isLoading && it.budgetState.envelopes.isNotEmpty() }
        val groceries = initial.budgetState.envelopes.first { it.name == "Groceries" }

        viewModel.updateEnvelopeIcon(groceries.id, "🥗")
        val afterIcon = pumpUntil {
            it.budgetState.envelopes.first { e -> e.id == groceries.id }.iconEmoji == "🥗"
        }
        assertThat(afterIcon.budgetState.envelopes.first { e -> e.id == groceries.id }.iconEmoji)
            .isEqualTo("🥗")

        viewModel.addEnvelope(
            name = "Emergency",
            iconEmoji = "🛡️",
            limitCentavos = 500_000L,
            colorHex = "#A855F7",
            addToRoutine = false
        )
        val afterAdd = pumpUntil {
            it.budgetState.envelopes.any { e -> e.name == "Emergency" }
        }
        assertThat(afterAdd.budgetState.envelopes.first { it.name == "Emergency" }.allocatedLimitCentavos)
            .isEqualTo(500_000L)
    }
}
