package com.mcalonzo.personalfinanceos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.domain.model.ScannedExpenseDraft
import com.mcalonzo.personalfinanceos.domain.usecase.MonthRolloverUseCase
import com.mcalonzo.personalfinanceos.worker.MonthRolloverWorker
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var rolloverUseCase: MonthRolloverUseCase
    private lateinit var repo: BudgetRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2026, 11) })
            .allowMainThreadQueries()
            .build()
        rolloverUseCase = MonthRolloverUseCase(db)
        repo = BudgetRepository(db, rolloverUseCase)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `undoing expense, bill settlement, and loan installment atomically restores prior state`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)
        val initial = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        val initialSafe = initial.safeToSpendCentavos

        // Variable gas expense & undo
        val gasUndo = repo.logGasFullTank(cycleId, 43120L, "Petron Fairview Full Tank")
        val afterGas = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(afterGas.safeToSpendCentavos).isEqualTo(initialSafe - 43120L)
        assertThat(afterGas.envelopes.first { it.isGasEnvelope }.spentCentavos).isEqualTo(43120L)

        repo.undoAction(gasUndo)
        val afterGasUndo = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(afterGasUndo.safeToSpendCentavos).isEqualTo(initialSafe)
        assertThat(afterGasUndo.envelopes.first { it.isGasEnvelope }.spentCentavos).isEqualTo(0L)
        assertThat(afterGasUndo.transactions).isEmpty()

        // Bill settlement & undo
        val bill = db.obligationDao().getBillsForCycleOnce(cycleId).first()
        val billUndo = repo.settleBill(bill.id, overrideAmountCentavos = bill.amountCentavos + 10_000L)
        val afterBill = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(afterBill.safeToSpendCentavos).isEqualTo(initialSafe - (bill.amountCentavos + 10_000L))
        assertThat(db.obligationDao().getBillById(bill.id)!!.isPaid).isTrue()

        repo.undoAction(billUndo)
        val revertedBill = db.obligationDao().getBillById(bill.id)!!
        assertThat(revertedBill.isPaid).isFalse()
        assertThat(revertedBill.amountCentavos).isEqualTo(bill.amountCentavos)
        assertThat(revertedBill.paidAtEpochMs).isNull()
        assertThat(repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first().safeToSpendCentavos)
            .isEqualTo(initialSafe)

        // Final loan installment -> paidInstallments reaches total, isActive becomes false -> undo restores active & prev cycle
        val loanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Short Loan",
                iconEmoji = "🏦",
                monthlyAmortizationCentavos = 100_000L,
                totalInstallments = 1,
                paidInstallments = 0,
                dueDayOfMonth = 20,
                nextDueYearMonth = "2026-11"
            )
        )
        val loanUndo = repo.settleLoan(loanId, cycleId)
        val settledLoan = db.obligationDao().getLoanById(loanId)!!
        assertThat(settledLoan.paidInstallments).isEqualTo(1)
        assertThat(settledLoan.isActive).isFalse()
        assertThat(settledLoan.nextDueYearMonth).isEqualTo("2026-12")
        assertThat(settledLoan.lastPaidCycleYearMonth).isEqualTo("2026-11")

        repo.undoAction(loanUndo)
        val revertedLoan = db.obligationDao().getLoanById(loanId)!!
        assertThat(revertedLoan.paidInstallments).isEqualTo(0)
        assertThat(revertedLoan.isActive).isTrue()
        assertThat(revertedLoan.nextDueYearMonth).isEqualTo("2026-11")
        assertThat(revertedLoan.lastPaidCycleYearMonth).isNull()
    }

    @Test
    fun `rollover creates only target month cycle and clones routine envelopes and bills without backfilling skipped months`() = runTest {
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val febCycleId = rolloverUseCase(YearMonth.of(2027, 2)) // skipped Dec, Jan
        val febCycleIdAgain = rolloverUseCase(YearMonth.of(2027, 2)) // idempotent re-entry

        assertThat(febCycleIdAgain).isEqualTo(febCycleId)
        assertThat(febCycleId).isNotEqualTo(novCycleId)

        val cycles = db.budgetDao().getAllCyclesOnce()
        assertThat(cycles.map { it.yearMonth }).containsExactly("2027-02", "2026-11")

        val febEnvelopes = db.budgetDao().getEnvelopesForCycleOnce(febCycleId)
        assertThat(febEnvelopes).hasSize(5)
        assertThat(febEnvelopes.count { it.isGasEnvelope }).isEqualTo(1)
        assertThat(febEnvelopes.all { it.spentCentavos == 0L }).isTrue()

        val febBills = db.obligationDao().getBillsForCycleOnce(febCycleId)
        assertThat(febBills).hasSize(4)
        assertThat(febBills.all { !it.isPaid }).isTrue()
    }

    @Test
    fun `settling active loan shifts amortization from committed loans to spent keeping safeToSpend constant`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)
        val before = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(before.committedUnpaidLoansCentavos).isEqualTo(835_000L) // 485,000 + 350,000
        assertThat(before.spentSoFarCentavos).isEqualTo(0L)
        assertThat(before.safeToSpendCentavos).isEqualTo(4_165_000L)

        val hondaLoan = db.obligationDao().getActiveLoansOnce().first { it.name.contains("Honda Click") }
        repo.settleLoan(hondaLoan.id, cycleId)

        val after = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(after.committedUnpaidLoansCentavos).isEqualTo(350_000L)
        assertThat(after.spentSoFarCentavos).isEqualTo(485_000L)
        assertThat(after.safeToSpendCentavos).isEqualTo(before.safeToSpendCentavos)

        val updatedLoan = db.obligationDao().getLoanById(hondaLoan.id)!!
        assertThat(updatedLoan.paidInstallments).isEqualTo(15)
        assertThat(updatedLoan.isActive).isTrue()
        assertThat(updatedLoan.nextDueYearMonth).isEqualTo("2026-12")
    }

    @Test
    fun `settleBill updates variable amount, blocks double settlement, and updates overdueBillsCount`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)
        val initial = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(initial.overdueBillsCount).isEqualTo(1) // Converge FiberX due day 11

        val meralco = db.obligationDao().getBillsForCycleOnce(cycleId).first { it.name == "Meralco" }
        repo.settleBill(meralco.id, overrideAmountCentavos = 425_000L)

        val settledMeralco = db.obligationDao().getBillById(meralco.id)!!
        assertThat(settledMeralco.isPaid).isTrue()
        assertThat(settledMeralco.amountCentavos).isEqualTo(425_000L)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.settleBill(meralco.id)
            }
        }

        val converge = db.obligationDao().getBillsForCycleOnce(cycleId).first { it.name == "Converge FiberX" }
        repo.settleBill(converge.id)
        val afterConverge = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(afterConverge.overdueBillsCount).isEqualTo(0)
        assertThat(afterConverge.spentSoFarCentavos).isEqualTo(425_000L + 150_000L)
    }

    @Test
    fun `deleteTransactionAndRevert reverses envelope expense, bill settlement, and loan settlement`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)
        val groceries = db.budgetDao().getEnvelopesForCycleOnce(cycleId).first { it.name == "Groceries" }

        val expUndo = repo.logExpense(cycleId, groceries.id, 125_000L, "SM Supermarket")
        assertThat(db.budgetDao().getEnvelopeById(groceries.id)!!.spentCentavos).isEqualTo(125_000L)
        repo.deleteTransactionAndRevert(expUndo.transactionId)
        assertThat(db.budgetDao().getEnvelopeById(groceries.id)!!.spentCentavos).isEqualTo(0L)

        val testBillId = repo.insertUnpaidTestBill(cycleId, "Water Utility", 45_000L, 12)
        val billUndo = repo.settleBill(testBillId)
        assertThat(db.obligationDao().getBillById(testBillId)!!.isPaid).isTrue()
        repo.deleteTransactionAndRevert(billUndo.transactionId)
        val revertedBill = db.obligationDao().getBillById(testBillId)!!
        assertThat(revertedBill.isPaid).isFalse()
        assertThat(revertedBill.paidAtEpochMs).isNull()

        val loan = db.obligationDao().getActiveLoansOnce().first()
        val loanUndo = repo.settleLoan(loan.id, cycleId)
        repo.deleteTransactionAndRevert(loanUndo.transactionId)
        val revertedLoan = db.obligationDao().getLoanById(loan.id)!!
        assertThat(revertedLoan.paidInstallments).isEqualTo(loan.paidInstallments)
        assertThat(revertedLoan.nextDueYearMonth).isEqualTo("2026-11")
        assertThat(revertedLoan.isActive).isTrue()

        val endOverview = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(endOverview.transactions).isEmpty()
        assertThat(endOverview.spentSoFarCentavos).isEqualTo(0L)
    }

    @Test
    fun `addEnvelope increments cycle totalBudget and optionally updates baseline routine`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)
        val before = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()

        repo.addEnvelope(
            cycleId = cycleId,
            name = "Medical",
            iconEmoji = "💊",
            limitCentavos = 250_000L,
            colorHex = "#38BDF8",
            addToRoutine = false
        )
        val afterOneOff = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(afterOneOff.totalBudgetCentavos).isEqualTo(before.totalBudgetCentavos + 250_000L)
        assertThat(afterOneOff.safeToSpendCentavos).isEqualTo(before.safeToSpendCentavos + 250_000L)
        assertThat(db.routineDao().getEnvelopeTemplatesOnce()).hasSize(5)

        repo.addEnvelope(
            cycleId = cycleId,
            name = "Travel",
            iconEmoji = "✈️",
            limitCentavos = 300_000L,
            colorHex = "#10B981",
            addToRoutine = true
        )
        assertThat(db.routineDao().getEnvelopeTemplatesOnce()).hasSize(6)
        assertThat(db.routineDao().getRoutineTemplateOnce()!!.totalMonthlyBudgetCentavos)
            .isEqualTo(5_300_000L)

        val decCycleId = rolloverUseCase(YearMonth.of(2026, 12))
        val decEnvelopes = db.budgetDao().getEnvelopesForCycleOnce(decCycleId)
        assertThat(decEnvelopes.map { it.name }).contains("Travel")
        assertThat(decEnvelopes.map { it.name }).doesNotContain("Medical")
    }

    @Test
    fun `logGasFullTank falls back cleanly to cycle-level transaction when gas envelope is missing`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)
        val gasEnvelope = db.budgetDao().getGasEnvelopeForCycle(cycleId)!!
        repo.deleteEnvelope(gasEnvelope.id)

        val before = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        val undo = repo.logGasFullTank(cycleId, 43120L, "Petron Fairview Full Tank")

        val after = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(after.spentSoFarCentavos).isEqualTo(43120L)
        assertThat(after.safeToSpendCentavos).isEqualTo(before.safeToSpendCentavos - 43120L)
        assertThat(after.transactions).hasSize(1)
        assertThat(after.transactions.first().envelopeId).isNull()
        assertThat(after.transactions.first().categoryLabel).isEqualTo("Motorcycle Gas")
        assertThat(after.envelopes.all { it.spentCentavos == 0L }).isTrue()

        repo.undoAction(undo)
        assertThat(repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first().spentSoFarCentavos)
            .isEqualTo(0L)
    }

    @Test
    fun `updateRoutineTemplate preserves bill template IDs and applies to current month only when requested`() = runTest {
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val envTemplates = db.routineDao().getEnvelopeTemplatesOnce()
        val billTemplates = db.routineDao().getBillTemplatesOnce()
        val meralcoTemplate = billTemplates.first { it.name == "Meralco" }

        // Pay Converge in current month and log spend in Groceries
        val novConverge = db.obligationDao().getBillsForCycleOnce(novCycleId).first { it.name == "Converge FiberX" }
        repo.settleBill(novConverge.id)
        val novGroceries = db.budgetDao().getEnvelopesForCycleOnce(novCycleId).first { it.name == "Groceries" }
        repo.logExpense(novCycleId, novGroceries.id, 100_000L, "Rice & Meat")

        // Edit routine for future cycles only (applyToCurrentMonth = false)
        val updatedBills = billTemplates.map {
            if (it.id == meralcoTemplate.id) it.copy(defaultAmountCentavos = 450_000L) else it
        }
        repo.updateRoutineTemplate(
            totalMonthlyBudgetCentavos = 5_500_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = envTemplates,
            billTemplates = updatedBills,
            applyToCurrentMonth = false,
            currentCycleMonth = YearMonth.of(2026, 11)
        )

        val novMeralcoBeforeApply = db.obligationDao().getBillsForCycleOnce(novCycleId).first { it.name == "Meralco" }
        assertThat(novMeralcoBeforeApply.amountCentavos).isEqualTo(380_000L)
        assertThat(db.budgetDao().getCycleById(novCycleId)!!.totalBudgetCentavos).isEqualTo(5_000_000L)

        val decCycleId = rolloverUseCase(YearMonth.of(2026, 12))
        val decMeralco = db.obligationDao().getBillsForCycleOnce(decCycleId).first { it.name == "Meralco" }
        assertThat(decMeralco.amountCentavos).isEqualTo(450_000L)
        assertThat(decMeralco.templateGroupId).isEqualTo(meralcoTemplate.id.toString())

        // Now apply routine changes to current month too
        val updatedEnvelopes = envTemplates.map {
            if (it.name == "Groceries") it.copy(allocatedLimitCentavos = 1_800_000L) else it
        }
        repo.updateRoutineTemplate(
            totalMonthlyBudgetCentavos = 5_800_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = updatedEnvelopes,
            billTemplates = updatedBills,
            applyToCurrentMonth = true,
            currentCycleMonth = YearMonth.of(2026, 11)
        )

        assertThat(db.budgetDao().getCycleById(novCycleId)!!.totalBudgetCentavos).isEqualTo(5_800_000L)
        val novGroceriesAfter = db.budgetDao().getEnvelopeById(novGroceries.id)!!
        assertThat(novGroceriesAfter.allocatedLimitCentavos).isEqualTo(1_800_000L)
        assertThat(novGroceriesAfter.spentCentavos).isEqualTo(100_000L) // Existing spend preserved!
        val novConvergeAfter = db.obligationDao().getBillById(novConverge.id)!!
        assertThat(novConvergeAfter.isPaid).isTrue() // Already-paid bill preserved!
        val novMeralcoAfter = db.obligationDao().getBillsForCycleOnce(novCycleId).first { it.name == "Meralco" }
        assertThat(novMeralcoAfter.amountCentavos).isEqualTo(450_000L)
    }

    @Test
    fun `MonthRolloverWorker creates current Asia Manila cycle when autoRolloverEnabled is true`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<MonthRolloverWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker = MonthRolloverWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        monthRolloverUseCase = rolloverUseCase,
                        routineDao = db.routineDao()
                    )
                }
            )
            .build()

        val result = worker.doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val expectedManilaMonth = YearMonth.now(ZoneId.of("Asia/Manila")).toString()
        assertThat(db.budgetDao().getCycleByYearMonth(expectedManilaMonth)).isNotNull()
    }

    @Test
    fun `envelope, bill, and loan CRUD methods update and delete entities cleanly`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val today = LocalDate.of(2026, 11, 14)

        val envId = repo.addEnvelope(cycleId, "Pets", "🐶", 200_000L, "#A855F7", addToRoutine = false)
        repo.updateEnvelopeIcon(envId, "🐱")
        repo.updateEnvelope(envId, "Cats", "🐱", 250_000L, "#38BDF8")
        val updatedEnv = db.budgetDao().getEnvelopeById(envId)!!
        assertThat(updatedEnv.name).isEqualTo("Cats")
        assertThat(updatedEnv.iconEmoji).isEqualTo("🐱")
        assertThat(updatedEnv.allocatedLimitCentavos).isEqualTo(250_000L)
        assertThat(db.budgetDao().getCycleById(cycleId)!!.totalBudgetCentavos).isEqualTo(5_250_000L)

        val billId = repo.addBill(cycleId, "iCloud+", "☁️", 14_900L, 15, addToRoutine = false)
        repo.updateBill(billId, "iCloud+ 200GB", "☁️", 17_900L, 16)
        val updatedBill = db.obligationDao().getBillById(billId)!!
        assertThat(updatedBill.name).isEqualTo("iCloud+ 200GB")
        assertThat(updatedBill.amountCentavos).isEqualTo(17_900L)
        assertThat(updatedBill.dueDayOfMonth).isEqualTo(16)
        repo.deleteBill(billId)
        assertThat(db.obligationDao().getBillById(billId)).isNull()

        val loanId = repo.addLoan(
            name = "Gadget Loan",
            iconEmoji = "📱",
            monthlyAmortizationCentavos = 150_000L,
            totalInstallments = 6,
            paidInstallments = 2,
            dueDayOfMonth = 18,
            nextDueYearMonth = YearMonth.of(2026, 11)
        )
        repo.updateLoan(
            loanId = loanId,
            name = "Phone Installment",
            iconEmoji = "📲",
            monthlyAmortizationCentavos = 160_000L,
            totalInstallments = 6,
            paidInstallments = 3,
            dueDayOfMonth = 19,
            nextDueYearMonth = YearMonth.of(2026, 12)
        )
        val updatedLoan = db.obligationDao().getLoanById(loanId)!!
        assertThat(updatedLoan.name).isEqualTo("Phone Installment")
        assertThat(updatedLoan.paidInstallments).isEqualTo(3)
        assertThat(updatedLoan.nextDueYearMonth).isEqualTo("2026-12")
        assertThat(updatedLoan.isActive).isTrue()

        val overview = repo.observeActiveCycleOverview(YearMonth.of(2026, 11), today).first()
        assertThat(overview.loans.first { it.loan.id == loanId }.isPaidForCycle).isTrue()

        repo.deleteLoan(loanId)
        assertThat(db.obligationDao().getLoanById(loanId)).isNull()
    }

    @Test
    fun `updateRoutineTemplate applyToCurrentMonth preserves unpaid bill IDs and cadence flags while syncing additions and removals`() = runTest {
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val envTemplates = db.routineDao().getEnvelopeTemplatesOnce()
        val billTemplates = db.routineDao().getBillTemplatesOnce()

        val novBillsBefore = db.obligationDao().getBillsForCycleOnce(novCycleId)
        val novMeralco = novBillsBefore.first { it.name == "Meralco" }
        repo.updateBillReminderCadence(
            billId = novMeralco.id,
            notify7d = false,
            notify3d = true,
            notify1d = false,
            notifyOnDue = true,
            notifyDailyOverdue = false
        )

        val novConverge = novBillsBefore.first { it.name == "Converge FiberX" }
        repo.settleBill(novConverge.id)
        val customBillId = repo.addBill(
            cycleId = novCycleId,
            name = "One-Off Repair",
            iconEmoji = "🔧",
            amountCentavos = 50_000L,
            dueDayOfMonth = 19,
            addToRoutine = false
        )

        val updatedBillTemplates = billTemplates
            .filterNot { it.name == "Spotify" }
            .map { template ->
                when (template.name) {
                    "Meralco" -> template.copy(
                        name = "Meralco Power",
                        iconEmoji = "💡",
                        defaultAmountCentavos = 460_000L,
                        dueDayOfMonth = 25
                    )
                    "Netflix" -> template.copy(isActive = false)
                    else -> template
                }
            } + RoutineBillTemplateEntity(
            name = "Water District",
            iconEmoji = "💧",
            defaultAmountCentavos = 65_000L,
            dueDayOfMonth = 15,
            isActive = true
        )

        repo.updateRoutineTemplate(
            totalMonthlyBudgetCentavos = 5_200_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = envTemplates,
            billTemplates = updatedBillTemplates,
            applyToCurrentMonth = true,
            currentCycleMonth = YearMonth.of(2026, 11)
        )

        val novBillsAfter = db.obligationDao().getBillsForCycleOnce(novCycleId)
        val meralcoAfter = novBillsAfter.first { it.name == "Meralco Power" }
        assertThat(meralcoAfter.id).isEqualTo(novMeralco.id)
        assertThat(meralcoAfter.iconEmoji).isEqualTo("💡")
        assertThat(meralcoAfter.amountCentavos).isEqualTo(460_000L)
        assertThat(meralcoAfter.dueDayOfMonth).isEqualTo(25)
        assertThat(meralcoAfter.notify7d).isFalse()
        assertThat(meralcoAfter.notify3d).isTrue()
        assertThat(meralcoAfter.notify1d).isFalse()
        assertThat(meralcoAfter.notifyOnDue).isTrue()
        assertThat(meralcoAfter.notifyDailyOverdue).isFalse()

        assertThat(novBillsAfter.none { it.name == "Spotify" }).isTrue()
        assertThat(novBillsAfter.none { it.name == "Netflix" }).isTrue()
        assertThat(novBillsAfter.first { it.id == novConverge.id }.isPaid).isTrue()
        assertThat(novBillsAfter.first { it.id == customBillId }.isPaid).isFalse()

        val waterTemplate = db.routineDao().getBillTemplatesOnce().first { it.name == "Water District" }
        val waterBill = novBillsAfter.first { it.name == "Water District" }
        assertThat(waterBill.templateGroupId).isEqualTo(waterTemplate.id.toString())
        assertThat(waterBill.amountCentavos).isEqualTo(65_000L)
        assertThat(waterBill.isPaid).isFalse()
    }

    @Test
    fun `settleLoan blocks duplicate settlement for already-satisfied cycle unless allowAdvancePayment is true`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val loanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Overdue Catch-Up Loan",
                iconEmoji = "🏦",
                monthlyAmortizationCentavos = 120_000L,
                totalInstallments = 12,
                paidInstallments = 1,
                dueDayOfMonth = 15,
                nextDueYearMonth = "2026-10"
            )
        )

        // First payment catches up 2026-10 -> nextDueYearMonth becomes 2026-11
        repo.settleLoan(loanId, cycleId)
        assertThat(db.obligationDao().getLoanById(loanId)!!.nextDueYearMonth).isEqualTo("2026-11")

        // Second payment settles 2026-11 -> nextDueYearMonth becomes 2026-12
        repo.settleLoan(loanId, cycleId)
        val settledForCycle = db.obligationDao().getLoanById(loanId)!!
        assertThat(settledForCycle.paidInstallments).isEqualTo(3)
        assertThat(settledForCycle.nextDueYearMonth).isEqualTo("2026-12")

        // Duplicate settlement for already-satisfied 2026-11 cycle throws IllegalStateException
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.settleLoan(loanId, cycleId)
            }
        }
        assertThat(db.obligationDao().getLoanById(loanId)!!.paidInstallments).isEqualTo(3)
        assertThat(db.budgetDao().getTransactionsForCycleOnce(cycleId).count { it.loanId == loanId })
            .isEqualTo(2)

        // Explicit advance payment succeeds
        repo.settleLoan(loanId, cycleId, allowAdvancePayment = true)
        val afterAdvance = db.obligationDao().getLoanById(loanId)!!
        assertThat(afterAdvance.paidInstallments).isEqualTo(4)
        assertThat(afterAdvance.nextDueYearMonth).isEqualTo("2027-01")
    }

    @Test
    fun `updateRoutineTemplate rejects invalid envelope and bill inputs and trims names`() = runTest {
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val envTemplates = db.routineDao().getEnvelopeTemplatesOnce()
        val billTemplates = db.routineDao().getBillTemplatesOnce()

        // Blank envelope name
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateRoutineTemplate(
                    totalMonthlyBudgetCentavos = 5_000_000L,
                    autoRolloverEnabled = true,
                    envelopeTemplates = envTemplates + RoutineEnvelopeTemplateEntity(
                        name = "   ",
                        iconEmoji = "📦",
                        colorHex = "#38BDF8",
                        allocatedLimitCentavos = 100_000L,
                        sortOrder = envTemplates.size
                    ),
                    billTemplates = billTemplates
                )
            }
        }

        // Non-positive envelope limit
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateRoutineTemplate(
                    totalMonthlyBudgetCentavos = 5_000_000L,
                    autoRolloverEnabled = true,
                    envelopeTemplates = listOf(envTemplates.first().copy(allocatedLimitCentavos = 0L)),
                    billTemplates = billTemplates
                )
            }
        }

        // Blank bill name
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateRoutineTemplate(
                    totalMonthlyBudgetCentavos = 5_000_000L,
                    autoRolloverEnabled = true,
                    envelopeTemplates = envTemplates,
                    billTemplates = billTemplates + RoutineBillTemplateEntity(
                        name = " \t ",
                        iconEmoji = "🧾",
                        defaultAmountCentavos = 50_000L,
                        dueDayOfMonth = 10
                    )
                )
            }
        }

        // Non-positive bill amount
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateRoutineTemplate(
                    totalMonthlyBudgetCentavos = 5_000_000L,
                    autoRolloverEnabled = true,
                    envelopeTemplates = envTemplates,
                    billTemplates = listOf(billTemplates.first().copy(defaultAmountCentavos = -500L))
                )
            }
        }

        // Due day outside 1..31
        for (invalidDay in listOf(0, 32)) {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repo.updateRoutineTemplate(
                        totalMonthlyBudgetCentavos = 5_000_000L,
                        autoRolloverEnabled = true,
                        envelopeTemplates = envTemplates,
                        billTemplates = listOf(billTemplates.first().copy(dueDayOfMonth = invalidDay))
                    )
                }
            }
        }

        // Valid input with surrounding whitespace trims stored names
        val untrimmedEnvelopes = envTemplates.mapIndexed { idx, item ->
            if (idx == 0) item.copy(name = "  Groceries  ") else item
        }
        val untrimmedBills = billTemplates.mapIndexed { idx, item ->
            if (idx == 0) item.copy(name = "  Converge FiberX  ") else item
        }
        repo.updateRoutineTemplate(
            totalMonthlyBudgetCentavos = 5_000_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = untrimmedEnvelopes,
            billTemplates = untrimmedBills,
            applyToCurrentMonth = true,
            currentCycleMonth = YearMonth.of(2026, 11)
        )

        assertThat(db.routineDao().getEnvelopeTemplatesOnce().first().name).isEqualTo("Groceries")
        assertThat(db.routineDao().getBillTemplatesOnce().first { it.id == untrimmedBills.first().id }.name)
            .isEqualTo("Converge FiberX")
        assertThat(db.obligationDao().getBillsForCycleOnce(novCycleId).any { it.name == "Converge FiberX" })
            .isTrue()
    }

    @Test
    fun `cycle deletion cascades to child bills and foreign key constraint is enforced`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val bills = db.obligationDao().getBillsForCycleOnce(cycleId)
        assertThat(bills).isNotEmpty()

        // Deleting the cycle cascades to all child bills
        db.budgetDao().deleteCycleById(cycleId)
        assertThat(db.obligationDao().getBillsForCycleOnce(cycleId)).isEmpty()
        assertThat(db.obligationDao().getBillById(bills.first().id)).isNull()
    }

    @Test
    fun `updateRoutineTemplate applyToCurrentMonth preserves zero-spend custom envelopes while pruning zero-spend routine envelopes removed from template`() = runTest {
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val envTemplates = db.routineDao().getEnvelopeTemplatesOnce()
        val billTemplates = db.routineDao().getBillTemplatesOnce()

        // 1. Add a custom envelope with addToRoutine = false and 0 spend
        val customEnvId = repo.addEnvelope(
            cycleId = novCycleId,
            name = "Concert Tickets",
            iconEmoji = "🎟️",
            colorHex = "#EC4899",
            limitCentavos = 500_000L,
            addToRoutine = false
        )
        val customEnv = db.budgetDao().getEnvelopeById(customEnvId)!!
        assertThat(customEnv.isCustom).isTrue()
        assertThat(customEnv.spentCentavos).isEqualTo(0L)

        // 2. Remove one routine envelope (e.g. non-gas routine template) from routine templates
        val removedRoutineName = envTemplates.first { !it.isGasEnvelope }.name
        val updatedEnvTemplates = envTemplates.filterNot { it.name == removedRoutineName }
        assertThat(updatedEnvTemplates.size).isEqualTo(envTemplates.size - 1)

        // 3. Update routine template with applyToCurrentMonth = true
        repo.updateRoutineTemplate(
            totalMonthlyBudgetCentavos = 5_000_000L,
            autoRolloverEnabled = true,
            envelopeTemplates = updatedEnvTemplates,
            billTemplates = billTemplates,
            applyToCurrentMonth = true,
            currentCycleMonth = YearMonth.of(2026, 11)
        )

        val envelopesAfter = db.budgetDao().getEnvelopesForCycleOnce(novCycleId)

        // Custom envelope with zero spend must still exist!
        val preservedCustomEnv = envelopesAfter.firstOrNull { it.id == customEnvId }
        assertThat(preservedCustomEnv).isNotNull()
        assertThat(preservedCustomEnv!!.name).isEqualTo("Concert Tickets")
        assertThat(preservedCustomEnv.isCustom).isTrue()

        // Zero-spend routine envelope removed from template must be pruned!
        assertThat(envelopesAfter.none { it.name == removedRoutineName }).isTrue()
    }

    @Test
    fun `updateTransaction updates title and adjusts linked envelope spentCentavos atomically`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val groceries = db.budgetDao().getEnvelopesForCycleOnce(cycleId).first { it.name == "Groceries" }

        val undo = repo.logExpense(
            cycleId = cycleId,
            envelopeId = groceries.id,
            amountCentavos = 50_000L,
            title = "Initial Store"
        )
        assertThat(db.budgetDao().getEnvelopeById(groceries.id)!!.spentCentavos).isEqualTo(50_000L)

        repo.updateTransaction(
            transactionId = undo.transactionId,
            title = "Updated Market",
            amountCentavos = 75_000L
        )

        val updatedTx = db.budgetDao().getTransactionById(undo.transactionId)!!
        assertThat(updatedTx.title).isEqualTo("Updated Market")
        assertThat(updatedTx.amountCentavos).isEqualTo(75_000L)
        assertThat(db.budgetDao().getEnvelopeById(groceries.id)!!.spentCentavos).isEqualTo(75_000L)
    }

    @Test
    fun `logScannedExpensesBatch inserts multi-cycle transactions, updates envelope spent, and auto-upserts ScanFormatProfileEntity`() = runTest {
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))

        val drafts = listOf(
            ScannedExpenseDraft(
                localId = "d-1",
                title = "SM Supermarket",
                amountCentavos = 120_000L,
                parsedDate = LocalDate.of(2026, 11, 10),
                dateLabel = "Nov 10",
                targetYearMonth = YearMonth.of(2026, 11),
                isCredit = false,
                isSummaryNoise = false,
                isDuplicate = false,
                isSelected = true,
                envelopeName = "Groceries"
            ),
            ScannedExpenseDraft(
                localId = "d-2",
                title = "Puregold",
                amountCentavos = 85_000L,
                parsedDate = LocalDate.of(2026, 12, 5),
                dateLabel = "Dec 5",
                targetYearMonth = YearMonth.of(2026, 12),
                isCredit = false,
                isSummaryNoise = false,
                isDuplicate = false,
                isSelected = true,
                envelopeName = "Groceries"
            ),
            ScannedExpenseDraft(
                localId = "d-3",
                title = "Total Due",
                amountCentavos = 205_000L,
                parsedDate = null,
                dateLabel = "",
                targetYearMonth = YearMonth.of(2026, 11),
                isCredit = false,
                isSummaryNoise = true,
                isDuplicate = false,
                isSelected = false,
                envelopeName = "Groceries"
            )
        )

        val loggedCount = repo.logScannedExpensesBatch(
            items = drafts,
            storeName = "BPI Credit Card",
            uncheckedKeywords = listOf("Total Due")
        )
        assertThat(loggedCount).isEqualTo(2)

        val novTransactions = db.budgetDao().getTransactionsForCycleOnce(novCycleId)
        assertThat(novTransactions.map { it.title }).containsExactly("SM Supermarket")
        val novGroceries = db.budgetDao().getEnvelopesForCycleOnce(novCycleId).first { it.name == "Groceries" }
        assertThat(novGroceries.spentCentavos).isEqualTo(120_000L)

        val decCycle = repo.getCycleByYearMonth(YearMonth.of(2026, 12))
        assertThat(decCycle).isNotNull()
        val decTransactions = db.budgetDao().getTransactionsForCycleOnce(decCycle!!.id)
        assertThat(decTransactions.map { it.title }).containsExactly("Puregold")
        val decGroceries = db.budgetDao().getEnvelopesForCycleOnce(decCycle.id).first { it.name == "Groceries" }
        assertThat(decGroceries.spentCentavos).isEqualTo(85_000L)

        val profile = repo.findMatchingFormatProfile("BPI Credit Card")
        assertThat(profile).isNotNull()
        assertThat(profile!!.defaultEnvelopeName).isEqualTo("Groceries")
        assertThat(profile.ignoredKeywordsPipe).contains("Total Due")
        assertThat(profile.scanCount).isEqualTo(1)

        // Second scan merges ignored keywords and increments scanCount
        repo.logScannedExpensesBatch(
            items = listOf(drafts.first().copy(localId = "d-4", title = "SM Hypermarket", amountCentavos = 30_000L)),
            storeName = "BPI Credit Card",
            uncheckedKeywords = listOf("Minimum Payment")
        )
        val updatedProfile = repo.findMatchingFormatProfile("BPI Credit Card")!!
        assertThat(updatedProfile.scanCount).isEqualTo(2)
        assertThat(updatedProfile.ignoredKeywordsPipe).contains("Total Due")
        assertThat(updatedProfile.ignoredKeywordsPipe).contains("Minimum Payment")
    }

    @Test
    fun `upsertCard, observeCards, and deleteCard manage saved cards`() = runTest {
        val cardId = repo.upsertCard(
            name = "BPI Rewards",
            cardType = "Credit",
            billingCycleDay = 15
        )
        val cards = repo.observeCards().first()
        assertThat(cards).hasSize(1)
        assertThat(cards.first().id).isEqualTo(cardId)
        assertThat(cards.first().billingCycleDay).isEqualTo(15)

        repo.deleteCard(cardId)
        assertThat(repo.observeCards().first()).isEmpty()
    }

    @Test
    fun `updateTransaction moves item to another envelope and transfers spentCentavos atomically`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val envelopes = db.budgetDao().getEnvelopesForCycleOnce(cycleId)
        val groceries = envelopes.first { it.name == "Groceries" }
        val targetEnvelope = envelopes.first { it.id != groceries.id }

        val undo = repo.logExpense(
            cycleId = cycleId,
            envelopeId = groceries.id,
            amountCentavos = 50_000L,
            title = "Petron Fuel"
        )
        assertThat(db.budgetDao().getEnvelopeById(groceries.id)!!.spentCentavos).isEqualTo(50_000L)
        assertThat(db.budgetDao().getEnvelopeById(targetEnvelope.id)!!.spentCentavos).isEqualTo(0L)

        repo.updateTransaction(
            transactionId = undo.transactionId,
            title = "Petron Blaze",
            amountCentavos = 65_000L,
            newEnvelopeId = targetEnvelope.id
        )

        val updatedTx = db.budgetDao().getTransactionById(undo.transactionId)!!
        assertThat(updatedTx.title).isEqualTo("Petron Blaze")
        assertThat(updatedTx.amountCentavos).isEqualTo(65_000L)
        assertThat(updatedTx.envelopeId).isEqualTo(targetEnvelope.id)
        assertThat(updatedTx.categoryLabel).isEqualTo(targetEnvelope.name)
        assertThat(db.budgetDao().getEnvelopeById(groceries.id)!!.spentCentavos).isEqualTo(0L)
        assertThat(db.budgetDao().getEnvelopeById(targetEnvelope.id)!!.spentCentavos).isEqualTo(65_000L)
    }

    @Test
    fun `settleBill logs transaction to utilities envelope and reverts both on delete`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val utilities = db.budgetDao().getEnvelopesForCycleOnce(cycleId)
            .first { it.name.contains("Utilities", ignoreCase = true) }
        val meralco = db.obligationDao().getBillsForCycleOnce(cycleId).first { it.name == "Meralco" }

        val undo = repo.settleBill(meralco.id, overrideAmountCentavos = 350_000L)

        val loggedTx = db.budgetDao().getTransactionById(undo.transactionId)!!
        assertThat(loggedTx.billId).isEqualTo(meralco.id)
        assertThat(loggedTx.envelopeId).isEqualTo(utilities.id)
        assertThat(loggedTx.categoryLabel).isEqualTo(utilities.name)
        assertThat(db.budgetDao().getEnvelopeById(utilities.id)!!.spentCentavos).isEqualTo(350_000L)

        repo.deleteTransactionAndRevert(loggedTx.id)
        assertThat(db.budgetDao().getEnvelopeById(utilities.id)!!.spentCentavos).isEqualTo(0L)
        assertThat(db.obligationDao().getBillById(meralco.id)!!.isPaid).isFalse()
    }
}
