package com.mcalonzo.personalfinanceos.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SoaStorageManager
import com.mcalonzo.personalfinanceos.data.local.StoredSoaFile
import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.EnvelopeEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.TransactionEntity
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.domain.calculator.DueDateCalculator
import com.mcalonzo.personalfinanceos.domain.model.BillWithStatus
import com.mcalonzo.personalfinanceos.domain.model.CycleOverview
import com.mcalonzo.personalfinanceos.domain.model.LoanWithStatus
import com.mcalonzo.personalfinanceos.domain.model.UndoAction
import com.mcalonzo.personalfinanceos.domain.usecase.MonthRolloverUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

/**
 * Coordinates atomic Room budget cycle queries, expense logging, variable bill and loan
 * settlement, 5-second undo reversal, and baseline monthly routine updates.
 */
@Singleton
class BudgetRepository(
    private val db: AppDatabase,
    private val monthRolloverUseCase: MonthRolloverUseCase
) {
    private var soaStorageManager: SoaStorageManager? = null
    private var appContext: Context? = null

    @Inject
    constructor(
        db: AppDatabase,
        monthRolloverUseCase: MonthRolloverUseCase,
        soaStorageManager: SoaStorageManager,
        @ApplicationContext appContext: Context
    ) : this(db, monthRolloverUseCase) {
        this.soaStorageManager = soaStorageManager
        this.appContext = appContext
    }

    constructor(db: AppDatabase) : this(db, MonthRolloverUseCase(db))

    private val routineDao = db.routineDao()
    private val budgetDao = db.budgetDao()
    private val obligationDao = db.obligationDao()

    fun observeAllCycles(): Flow<List<BudgetCycleEntity>> = budgetDao.observeAllCycles()

    fun observeRoutineTemplate(): Flow<RoutineTemplateEntity?> = routineDao.observeRoutineTemplate()

    fun observeEnvelopeTemplates(): Flow<List<RoutineEnvelopeTemplateEntity>> =
        routineDao.observeEnvelopeTemplates()

    fun observeBillTemplates(): Flow<List<RoutineBillTemplateEntity>> =
        routineDao.observeBillTemplates()

    fun observeAllLoans(): Flow<List<LoanEntity>> = obligationDao.observeAllLoans()

    fun observeActiveLoans(): Flow<List<LoanEntity>> = obligationDao.observeActiveLoans()

    suspend fun getOrCreateCycleId(
        targetMonth: YearMonth = YearMonth.now(MANILA_ZONE)
    ): Long = monthRolloverUseCase(targetMonth)

    suspend fun getCycleByYearMonth(yearMonth: YearMonth): BudgetCycleEntity? =
        budgetDao.getCycleByYearMonth(yearMonth.toString())

    suspend fun getCycleById(cycleId: Long): BudgetCycleEntity? =
        budgetDao.getCycleById(cycleId)

    suspend fun attachSoaPdf(
        cycleId: Long,
        yearMonth: String,
        uri: Uri,
        contextOverride: Context? = null,
        storageOverride: SoaStorageManager? = null
    ): StoredSoaFile {
        SoaStorageManager.validateYearMonth(yearMonth)
        val resolvedContext = contextOverride ?: appContext
            ?: throw IllegalStateException("Application context is required to attach SOA PDF")
        val resolvedStorage = storageOverride ?: soaStorageManager
            ?: SoaStorageManager(File(resolvedContext.filesDir, "soa"))

        val cycle = budgetDao.getCycleById(cycleId)
            ?: throw IllegalArgumentException("Budget cycle $cycleId not found")

        val stored = withContext(Dispatchers.IO) {
            val contentResolver = resolvedContext.contentResolver
            val displayName = resolveSoaDisplayName(
                contentResolver = contentResolver,
                uri = uri,
                fallback = "SOA_${yearMonth}.pdf"
            )
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open SOA PDF stream for $uri")
            inputStream.use { stream ->
                resolvedStorage.saveOrReplaceSoa(
                    yearMonth = yearMonth,
                    inputStream = stream,
                    originalFileName = displayName,
                    previousLocalPath = cycle.soaPdfLocalUri
                )
            }
        }

        budgetDao.updateCycleSoa(
            cycleId = cycleId,
            localUri = stored.localPath,
            fileName = stored.fileName,
            uploadedAtEpochMs = stored.savedAtEpochMs
        )
        return stored
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveCycleOverview(
        yearMonth: YearMonth,
        today: LocalDate? = null
    ): Flow<CycleOverview> {
        return budgetDao.observeCycleByYearMonth(yearMonth.toString())
            .flatMapLatest { cycle ->
                if (cycle == null) {
                    monthRolloverUseCase(yearMonth)
                    emptyFlow()
                } else {
                    combine(
                        budgetDao.observeEnvelopesForCycle(cycle.id),
                        obligationDao.observeBillsForCycle(cycle.id),
                        obligationDao.observeAllLoans(),
                        budgetDao.observeTransactionsForCycle(cycle.id)
                    ) { envelopes, bills, loans, transactions ->
                        val effectiveToday = today ?: LocalDate.now(MANILA_ZONE)
                        buildCycleOverview(
                            cycle = cycle,
                            yearMonth = yearMonth,
                            today = effectiveToday,
                            envelopes = envelopes,
                            bills = bills,
                            loans = loans,
                            transactions = transactions
                        )
                    }
                }
            }
            .distinctUntilChanged()
    }

    suspend fun logExpense(
        cycleId: Long,
        envelopeId: Long,
        amountCentavos: Long,
        title: String
    ): UndoAction {
        require(amountCentavos > 0L) { "Expense amount must be positive" }
        return db.withTransaction {
            budgetDao.getCycleById(cycleId)
                ?: throw IllegalArgumentException("Budget cycle $cycleId not found")
            val envelope = budgetDao.getEnvelopeById(envelopeId)
                ?: throw IllegalArgumentException("Envelope $envelopeId not found")
            require(envelope.cycleId == cycleId) {
                "Envelope $envelopeId belongs to cycle ${envelope.cycleId}, not $cycleId"
            }

            val txTitle = title.trim().ifEmpty { envelope.name }
            val txId = budgetDao.insertTransaction(
                TransactionEntity(
                    cycleId = cycleId,
                    envelopeId = envelope.id,
                    billId = null,
                    loanId = null,
                    title = txTitle,
                    categoryLabel = envelope.name,
                    amountCentavos = amountCentavos,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            budgetDao.incrementEnvelopeSpent(envelope.id, amountCentavos)

            UndoAction.ExpenseLogged(
                transactionId = txId,
                cycleId = cycleId,
                envelopeId = envelope.id,
                amountCentavos = amountCentavos,
                label = txTitle
            )
        }
    }

    suspend fun logGasFullTank(
        cycleId: Long,
        amountCentavos: Long,
        label: String
    ): UndoAction {
        require(amountCentavos > 0L) { "Gas expense amount must be positive" }
        return db.withTransaction {
            budgetDao.getCycleById(cycleId)
                ?: throw IllegalArgumentException("Budget cycle $cycleId not found")
            val gasEnvelope = budgetDao.getGasEnvelopeForCycle(cycleId)
            val txTitle = label.trim().ifEmpty { "Motorcycle Gas Full Tank" }
            val categoryLabel = gasEnvelope?.name ?: "Motorcycle Gas"

            val txId = budgetDao.insertTransaction(
                TransactionEntity(
                    cycleId = cycleId,
                    envelopeId = gasEnvelope?.id,
                    billId = null,
                    loanId = null,
                    title = txTitle,
                    categoryLabel = categoryLabel,
                    amountCentavos = amountCentavos,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            if (gasEnvelope != null) {
                budgetDao.incrementEnvelopeSpent(gasEnvelope.id, amountCentavos)
            }

            UndoAction.ExpenseLogged(
                transactionId = txId,
                cycleId = cycleId,
                envelopeId = gasEnvelope?.id,
                amountCentavos = amountCentavos,
                label = txTitle
            )
        }
    }

    suspend fun settleBill(
        billId: Long,
        overrideAmountCentavos: Long? = null
    ): UndoAction {
        if (overrideAmountCentavos != null) {
            require(overrideAmountCentavos > 0L) { "Bill payment amount must be positive" }
        }
        return db.withTransaction {
            val bill = obligationDao.getBillById(billId)
                ?: throw IllegalArgumentException("Bill $billId not found")
            check(!bill.isPaid) { "Bill ${bill.id} (${bill.name}) is already paid" }

            val paidAmount = overrideAmountCentavos ?: bill.amountCentavos
            require(paidAmount > 0L) { "Bill payment amount must be positive" }
            val paidAtMs = System.currentTimeMillis()

            obligationDao.updateBill(
                bill.copy(
                    amountCentavos = paidAmount,
                    isPaid = true,
                    paidAtEpochMs = paidAtMs
                )
            )
            val txId = budgetDao.insertTransaction(
                TransactionEntity(
                    cycleId = bill.cycleId,
                    envelopeId = null,
                    billId = bill.id,
                    loanId = null,
                    title = bill.name,
                    categoryLabel = "Bills",
                    amountCentavos = paidAmount,
                    timestampEpochMs = paidAtMs
                )
            )

            UndoAction.BillSettled(
                transactionId = txId,
                billId = bill.id,
                previousAmountCentavos = bill.amountCentavos,
                previousIsPaid = bill.isPaid,
                previousPaidAtEpochMs = bill.paidAtEpochMs,
                label = bill.name
            )
        }
    }

    suspend fun settleLoan(
        loanId: Long,
        cycleId: Long,
        allowAdvancePayment: Boolean = false
    ): UndoAction {
        return db.withTransaction {
            val loan = obligationDao.getLoanById(loanId)
                ?: throw IllegalArgumentException("Loan $loanId not found")
            val cycle = budgetDao.getCycleById(cycleId)
                ?: throw IllegalArgumentException("Budget cycle $cycleId not found")
            check(loan.isActive && loan.paidInstallments < loan.totalInstallments) {
                "Loan ${loan.id} (${loan.name}) is already completed or inactive"
            }
            check(
                allowAdvancePayment ||
                    !YearMonth.parse(loan.nextDueYearMonth).isAfter(YearMonth.parse(cycle.yearMonth))
            ) {
                "Loan ${loan.id} (${loan.name}) is already settled for cycle ${cycle.yearMonth}"
            }

            val updatedPaid = loan.paidInstallments + 1
            val updatedNextDue = YearMonth.parse(loan.nextDueYearMonth).plusMonths(1).toString()
            val updatedActive = updatedPaid < loan.totalInstallments

            obligationDao.updateLoan(
                loan.copy(
                    paidInstallments = updatedPaid,
                    nextDueYearMonth = updatedNextDue,
                    lastPaidCycleYearMonth = cycle.yearMonth,
                    isActive = updatedActive
                )
            )

            val txTitle = "${loan.name} ($updatedPaid/${loan.totalInstallments})"
            val txId = budgetDao.insertTransaction(
                TransactionEntity(
                    cycleId = cycleId,
                    envelopeId = null,
                    billId = null,
                    loanId = loan.id,
                    title = txTitle,
                    categoryLabel = "Loans",
                    amountCentavos = loan.monthlyAmortizationCentavos,
                    timestampEpochMs = System.currentTimeMillis()
                )
            )

            UndoAction.LoanSettled(
                transactionId = txId,
                loanId = loan.id,
                previousPaidInstallments = loan.paidInstallments,
                previousNextDueYearMonth = loan.nextDueYearMonth,
                previousLastPaidCycleYearMonth = loan.lastPaidCycleYearMonth,
                previousIsActive = loan.isActive,
                label = loan.name
            )
        }
    }

    suspend fun undoAction(action: UndoAction) {
        db.withTransaction {
            // Guard against double-tap undo or concurrent activity deletion.
            budgetDao.getTransactionById(action.transactionId) ?: return@withTransaction
            budgetDao.deleteTransactionById(action.transactionId)

            when (action) {
                is UndoAction.ExpenseLogged -> {
                    if (action.envelopeId != null) {
                        budgetDao.incrementEnvelopeSpent(action.envelopeId, -action.amountCentavos)
                    }
                }
                is UndoAction.BillSettled -> {
                    obligationDao.getBillById(action.billId)?.let { bill ->
                        obligationDao.updateBill(
                            bill.copy(
                                amountCentavos = action.previousAmountCentavos,
                                isPaid = action.previousIsPaid,
                                paidAtEpochMs = action.previousPaidAtEpochMs
                            )
                        )
                    }
                }
                is UndoAction.LoanSettled -> {
                    obligationDao.getLoanById(action.loanId)?.let { loan ->
                        obligationDao.updateLoan(
                            loan.copy(
                                paidInstallments = action.previousPaidInstallments,
                                nextDueYearMonth = action.previousNextDueYearMonth,
                                lastPaidCycleYearMonth = action.previousLastPaidCycleYearMonth,
                                isActive = action.previousIsActive
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun deleteTransactionAndRevert(transactionId: Long) {
        db.withTransaction {
            val tx = budgetDao.getTransactionById(transactionId) ?: return@withTransaction

            when {
                tx.envelopeId != null -> {
                    budgetDao.incrementEnvelopeSpent(tx.envelopeId, -tx.amountCentavos)
                }
                tx.billId != null -> {
                    obligationDao.getBillById(tx.billId)?.let { bill ->
                        obligationDao.updateBill(
                            bill.copy(
                                isPaid = false,
                                paidAtEpochMs = null
                            )
                        )
                    }
                }
                tx.loanId != null -> {
                    obligationDao.getLoanById(tx.loanId)?.let { loan ->
                        val revertedPaid = (loan.paidInstallments - 1).coerceAtLeast(0)
                        val revertedNextDue = YearMonth.parse(loan.nextDueYearMonth)
                            .minusMonths(1)
                            .toString()
                        val txCycle = budgetDao.getCycleById(tx.cycleId)
                        val revertedLastPaid = if (
                            revertedPaid == 0 ||
                            loan.lastPaidCycleYearMonth == txCycle?.yearMonth
                        ) {
                            null
                        } else {
                            loan.lastPaidCycleYearMonth
                        }
                        obligationDao.updateLoan(
                            loan.copy(
                                paidInstallments = revertedPaid,
                                nextDueYearMonth = revertedNextDue,
                                lastPaidCycleYearMonth = revertedLastPaid,
                                isActive = revertedPaid < loan.totalInstallments
                            )
                        )
                    }
                }
            }

            budgetDao.deleteTransactionById(tx.id)
        }
    }

    suspend fun addEnvelope(
        cycleId: Long,
        name: String,
        iconEmoji: String,
        limitCentavos: Long,
        colorHex: String,
        addToRoutine: Boolean,
        isGasEnvelope: Boolean = false
    ): Long {
        require(limitCentavos > 0L) { "Envelope limit must be positive" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Envelope name cannot be blank" }

        return db.withTransaction {
            val cycle = budgetDao.getCycleById(cycleId)
                ?: throw IllegalArgumentException("Budget cycle $cycleId not found")

            val envelopeId = budgetDao.insertEnvelope(
                EnvelopeEntity(
                    cycleId = cycleId,
                    name = trimmedName,
                    iconEmoji = iconEmoji,
                    colorHex = colorHex,
                    allocatedLimitCentavos = limitCentavos,
                    spentCentavos = 0L,
                    isGasEnvelope = isGasEnvelope
                )
            )
            budgetDao.updateCycleTotalBudget(cycleId, cycle.totalBudgetCentavos + limitCentavos)

            if (addToRoutine) {
                val existingTemplates = routineDao.getEnvelopeTemplatesOnce()
                val nextSortOrder = (existingTemplates.maxOfOrNull { it.sortOrder } ?: -1) + 1
                routineDao.insertEnvelopeTemplate(
                    RoutineEnvelopeTemplateEntity(
                        name = trimmedName,
                        iconEmoji = iconEmoji,
                        colorHex = colorHex,
                        allocatedLimitCentavos = limitCentavos,
                        isGasEnvelope = isGasEnvelope,
                        sortOrder = nextSortOrder
                    )
                )
                val currentRoutine = routineDao.getRoutineTemplateOnce()
                routineDao.upsertRoutineTemplate(
                    RoutineTemplateEntity(
                        id = 1,
                        totalMonthlyBudgetCentavos = (currentRoutine?.totalMonthlyBudgetCentavos ?: 0L) +
                            limitCentavos,
                        autoRolloverEnabled = currentRoutine?.autoRolloverEnabled ?: true,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                )
            }

            envelopeId
        }
    }

    suspend fun updateEnvelopeIcon(envelopeId: Long, iconEmoji: String) {
        budgetDao.updateEnvelopeIcon(envelopeId, iconEmoji)
    }

    suspend fun updateEnvelope(
        envelopeId: Long,
        name: String,
        iconEmoji: String,
        limitCentavos: Long,
        colorHex: String
    ) {
        require(limitCentavos > 0L) { "Envelope limit must be positive" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Envelope name cannot be blank" }

        db.withTransaction {
            val existing = budgetDao.getEnvelopeById(envelopeId)
                ?: throw IllegalArgumentException("Envelope $envelopeId not found")
            val cycle = budgetDao.getCycleById(existing.cycleId)
            val delta = limitCentavos - existing.allocatedLimitCentavos

            budgetDao.updateEnvelope(
                existing.copy(
                    name = trimmedName,
                    iconEmoji = iconEmoji,
                    allocatedLimitCentavos = limitCentavos,
                    colorHex = colorHex
                )
            )
            if (cycle != null && delta != 0L) {
                budgetDao.updateCycleTotalBudget(
                    cycle.id,
                    (cycle.totalBudgetCentavos + delta).coerceAtLeast(0L)
                )
            }
        }
    }

    suspend fun deleteEnvelope(envelopeId: Long) {
        db.withTransaction {
            val existing = budgetDao.getEnvelopeById(envelopeId) ?: return@withTransaction
            val cycle = budgetDao.getCycleById(existing.cycleId)
            budgetDao.deleteEnvelopeById(envelopeId)
            if (cycle != null) {
                budgetDao.updateCycleTotalBudget(
                    cycle.id,
                    (cycle.totalBudgetCentavos - existing.allocatedLimitCentavos).coerceAtLeast(0L)
                )
            }
        }
    }

    suspend fun addBill(
        cycleId: Long,
        name: String,
        iconEmoji: String,
        amountCentavos: Long,
        dueDayOfMonth: Int,
        addToRoutine: Boolean = false
    ): Long {
        require(amountCentavos > 0L) { "Bill amount must be positive" }
        require(dueDayOfMonth in 1..31) { "Due day must be in 1..31" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Bill name cannot be blank" }

        return db.withTransaction {
            budgetDao.getCycleById(cycleId)
                ?: throw IllegalArgumentException("Budget cycle $cycleId not found")
            val templateGroupId = if (addToRoutine) {
                routineDao.insertBillTemplate(
                    RoutineBillTemplateEntity(
                        name = trimmedName,
                        iconEmoji = iconEmoji,
                        defaultAmountCentavos = amountCentavos,
                        dueDayOfMonth = dueDayOfMonth,
                        isActive = true
                    )
                ).toString()
            } else {
                "custom_${UUID.randomUUID()}"
            }

            obligationDao.insertBill(
                BillEntity(
                    cycleId = cycleId,
                    templateGroupId = templateGroupId,
                    name = trimmedName,
                    iconEmoji = iconEmoji,
                    amountCentavos = amountCentavos,
                    dueDayOfMonth = dueDayOfMonth,
                    isPaid = false
                )
            )
        }
    }

    suspend fun updateBill(
        billId: Long,
        name: String,
        iconEmoji: String,
        amountCentavos: Long,
        dueDayOfMonth: Int
    ) {
        require(amountCentavos > 0L) { "Bill amount must be positive" }
        require(dueDayOfMonth in 1..31) { "Due day must be in 1..31" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Bill name cannot be blank" }

        db.withTransaction {
            val existing = obligationDao.getBillById(billId)
                ?: throw IllegalArgumentException("Bill $billId not found")
            obligationDao.updateBill(
                existing.copy(
                    name = trimmedName,
                    iconEmoji = iconEmoji,
                    amountCentavos = amountCentavos,
                    dueDayOfMonth = dueDayOfMonth
                )
            )
        }
    }

    suspend fun updateBillReminderCadence(
        billId: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) {
        obligationDao.updateBillReminderCadence(
            billId = billId,
            notify7d = notify7d,
            notify3d = notify3d,
            notify1d = notify1d,
            notifyOnDue = notifyOnDue,
            notifyDailyOverdue = notifyDailyOverdue
        )
    }

    suspend fun deleteBill(billId: Long) {
        obligationDao.deleteBillById(billId)
    }

    suspend fun insertUnpaidTestBill(
        cycleId: Long,
        name: String,
        amountCentavos: Long,
        dueDay: Int
    ): Long {
        return obligationDao.insertBill(
            BillEntity(
                cycleId = cycleId,
                templateGroupId = "test_${UUID.randomUUID()}",
                name = name,
                iconEmoji = "🧾",
                amountCentavos = amountCentavos,
                dueDayOfMonth = dueDay,
                isPaid = false
            )
        )
    }

    suspend fun addLoan(
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int = 0,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ): Long {
        require(monthlyAmortizationCentavos > 0L) { "Monthly amortization must be positive" }
        require(totalInstallments > 0) { "Total installments must be positive" }
        require(paidInstallments in 0..totalInstallments) {
            "Paid installments must be in 0..totalInstallments"
        }
        require(dueDayOfMonth in 1..31) { "Due day must be in 1..31" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Loan name cannot be blank" }

        return obligationDao.insertLoan(
            LoanEntity(
                name = trimmedName,
                iconEmoji = iconEmoji,
                monthlyAmortizationCentavos = monthlyAmortizationCentavos,
                totalInstallments = totalInstallments,
                paidInstallments = paidInstallments,
                dueDayOfMonth = dueDayOfMonth,
                nextDueYearMonth = nextDueYearMonth.toString(),
                lastPaidCycleYearMonth = null,
                isActive = paidInstallments < totalInstallments
            )
        )
    }

    suspend fun updateLoan(
        loanId: Long,
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) {
        require(monthlyAmortizationCentavos > 0L) { "Monthly amortization must be positive" }
        require(totalInstallments > 0) { "Total installments must be positive" }
        require(paidInstallments in 0..totalInstallments) {
            "Paid installments must be in 0..totalInstallments"
        }
        require(dueDayOfMonth in 1..31) { "Due day must be in 1..31" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Loan name cannot be blank" }

        db.withTransaction {
            val existing = obligationDao.getLoanById(loanId)
                ?: throw IllegalArgumentException("Loan $loanId not found")
            obligationDao.updateLoan(
                existing.copy(
                    name = trimmedName,
                    iconEmoji = iconEmoji,
                    monthlyAmortizationCentavos = monthlyAmortizationCentavos,
                    totalInstallments = totalInstallments,
                    paidInstallments = paidInstallments,
                    dueDayOfMonth = dueDayOfMonth,
                    nextDueYearMonth = nextDueYearMonth.toString(),
                    isActive = paidInstallments < totalInstallments
                )
            )
        }
    }

    suspend fun updateLoanReminderCadence(
        loanId: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) {
        obligationDao.updateLoanReminderCadence(
            loanId = loanId,
            notify7d = notify7d,
            notify3d = notify3d,
            notify1d = notify1d,
            notifyOnDue = notifyOnDue,
            notifyDailyOverdue = notifyDailyOverdue
        )
    }

    suspend fun deleteLoan(loanId: Long) {
        obligationDao.deleteLoanById(loanId)
    }

    suspend fun updateRoutineTemplate(
        totalMonthlyBudgetCentavos: Long,
        autoRolloverEnabled: Boolean,
        envelopeTemplates: List<RoutineEnvelopeTemplateEntity>,
        billTemplates: List<RoutineBillTemplateEntity>,
        applyToCurrentMonth: Boolean = false,
        currentCycleMonth: YearMonth = YearMonth.now(MANILA_ZONE)
    ) {
        require(totalMonthlyBudgetCentavos >= 0L) { "Monthly budget cannot be negative" }
        val validatedEnvelopes = envelopeTemplates.map { envelope ->
            val trimmedName = envelope.name.trim()
            require(trimmedName.isNotEmpty()) { "Envelope template name cannot be blank" }
            require(envelope.allocatedLimitCentavos > 0L) {
                "Envelope template limit must be positive"
            }
            envelope.copy(name = trimmedName)
        }
        val validatedBills = billTemplates.map { bill ->
            val trimmedName = bill.name.trim()
            require(trimmedName.isNotEmpty()) { "Bill template name cannot be blank" }
            require(bill.defaultAmountCentavos > 0L) {
                "Bill template default amount must be positive"
            }
            require(bill.dueDayOfMonth in 1..31) {
                "Bill template due day must be in 1..31"
            }
            bill.copy(name = trimmedName)
        }

        db.withTransaction {
            routineDao.upsertRoutineTemplate(
                RoutineTemplateEntity(
                    id = 1,
                    totalMonthlyBudgetCentavos = totalMonthlyBudgetCentavos,
                    autoRolloverEnabled = autoRolloverEnabled,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )

            syncEnvelopeTemplatesInPlace(validatedEnvelopes)
            syncBillTemplatesInPlace(validatedBills)

            if (applyToCurrentMonth) {
                applyRoutineToCycle(currentCycleMonth, totalMonthlyBudgetCentavos)
            }
        }
    }

    private suspend fun syncEnvelopeTemplatesInPlace(
        incoming: List<RoutineEnvelopeTemplateEntity>
    ) {
        val existing = routineDao.getEnvelopeTemplatesOnce()
        val existingIds = existing.map { it.id }.toSet()
        val keptIds = incoming.mapNotNull { it.id.takeIf { id -> id > 0L } }.toSet()

        existing.filter { it.id !in keptIds }
            .forEach { routineDao.deleteEnvelopeTemplateById(it.id) }

        incoming.forEachIndexed { index, item ->
            val ordered = item.copy(sortOrder = index)
            if (ordered.id > 0L && ordered.id in existingIds) {
                routineDao.updateEnvelopeTemplate(ordered)
            } else {
                routineDao.insertEnvelopeTemplate(ordered.copy(id = 0L))
            }
        }
    }

    private suspend fun syncBillTemplatesInPlace(
        incoming: List<RoutineBillTemplateEntity>
    ) {
        val existing = routineDao.getBillTemplatesOnce()
        val existingIds = existing.map { it.id }.toSet()
        val keptIds = incoming.mapNotNull { it.id.takeIf { id -> id > 0L } }.toSet()

        existing.filter { it.id !in keptIds }
            .forEach { routineDao.deleteBillTemplateById(it.id) }

        incoming.forEach { item ->
            // Preserve existing IDs so BillEntity.templateGroupId stays stable across cycles.
            if (item.id > 0L && item.id in existingIds) {
                routineDao.updateBillTemplate(item)
            } else {
                routineDao.insertBillTemplate(item.copy(id = 0L))
            }
        }
    }

    private suspend fun applyRoutineToCycle(
        cycleMonth: YearMonth,
        totalMonthlyBudgetCentavos: Long
    ) {
        val existingCycle = budgetDao.getCycleByYearMonth(cycleMonth.toString())
        if (existingCycle == null) {
            monthRolloverUseCase(cycleMonth)
            return
        }

        budgetDao.updateCycleTotalBudget(existingCycle.id, totalMonthlyBudgetCentavos)

        val savedEnvelopeTemplates = routineDao.getEnvelopeTemplatesOnce()
        val currentEnvelopes = budgetDao.getEnvelopesForCycleOnce(existingCycle.id).toMutableList()
        val matchedEnvelopeIds = mutableSetOf<Long>()

        savedEnvelopeTemplates.forEach { template ->
            val match = currentEnvelopes.firstOrNull { env ->
                env.id !in matchedEnvelopeIds && (
                    (template.isGasEnvelope && env.isGasEnvelope) ||
                        env.name.equals(template.name, ignoreCase = true)
                    )
            }
            if (match != null) {
                matchedEnvelopeIds.add(match.id)
                budgetDao.updateEnvelope(
                    match.copy(
                        name = template.name,
                        iconEmoji = template.iconEmoji,
                        colorHex = template.colorHex,
                        allocatedLimitCentavos = template.allocatedLimitCentavos,
                        isGasEnvelope = template.isGasEnvelope
                    )
                )
            } else {
                budgetDao.insertEnvelope(
                    EnvelopeEntity(
                        cycleId = existingCycle.id,
                        name = template.name,
                        iconEmoji = template.iconEmoji,
                        colorHex = template.colorHex,
                        allocatedLimitCentavos = template.allocatedLimitCentavos,
                        spentCentavos = 0L,
                        isGasEnvelope = template.isGasEnvelope
                    )
                )
            }
        }

        currentEnvelopes
            .filter { it.id !in matchedEnvelopeIds && it.spentCentavos == 0L }
            .forEach { budgetDao.deleteEnvelopeById(it.id) }

        val savedActiveBills = routineDao.getActiveBillTemplatesOnce()
        val activeBillsByGroupId = savedActiveBills.associateBy { it.id.toString() }
        val currentBills = obligationDao.getBillsForCycleOnce(existingCycle.id)
        val presentGroupIds = mutableSetOf<String>()

        currentBills.forEach { existing ->
            val isRoutineBill = !existing.templateGroupId.startsWith("custom_") &&
                !existing.templateGroupId.startsWith("test_")
            if (!isRoutineBill) return@forEach

            if (existing.isPaid) {
                presentGroupIds.add(existing.templateGroupId)
            } else {
                val template = activeBillsByGroupId[existing.templateGroupId]
                if (template != null) {
                    presentGroupIds.add(existing.templateGroupId)
                    obligationDao.updateBill(
                        existing.copy(
                            name = template.name,
                            iconEmoji = template.iconEmoji,
                            amountCentavos = template.defaultAmountCentavos,
                            dueDayOfMonth = template.dueDayOfMonth
                        )
                    )
                } else {
                    obligationDao.deleteBillById(existing.id)
                }
            }
        }

        val billsToInsert = savedActiveBills
            .filter { it.id.toString() !in presentGroupIds }
            .map { template ->
                BillEntity(
                    cycleId = existingCycle.id,
                    templateGroupId = template.id.toString(),
                    name = template.name,
                    iconEmoji = template.iconEmoji,
                    amountCentavos = template.defaultAmountCentavos,
                    dueDayOfMonth = template.dueDayOfMonth,
                    isPaid = false,
                    paidAtEpochMs = null
                )
            }
        if (billsToInsert.isNotEmpty()) {
            obligationDao.insertBills(billsToInsert)
        }
    }

    private fun buildCycleOverview(
        cycle: BudgetCycleEntity,
        yearMonth: YearMonth,
        today: LocalDate,
        envelopes: List<EnvelopeEntity>,
        bills: List<BillEntity>,
        loans: List<LoanEntity>,
        transactions: List<TransactionEntity>
    ): CycleOverview {
        val spentSoFarCentavos = transactions.sumOf { it.amountCentavos }

        val committedUnpaidLoansCentavos = loans
            .filter { loan ->
                loan.isActive &&
                    loan.paidInstallments < loan.totalInstallments &&
                    !YearMonth.parse(loan.nextDueYearMonth).isAfter(yearMonth)
            }
            .sumOf { it.monthlyAmortizationCentavos }

        val unpaidBillsCentavos = bills
            .filter { !it.isPaid }
            .sumOf { it.amountCentavos }

        val safeToSpendCentavos = BudgetCalculator.calculateSafeToSpend(
            totalBudgetCentavos = cycle.totalBudgetCentavos,
            variableAndBillSpentCentavos = spentSoFarCentavos,
            committedUnpaidLoansCentavos = committedUnpaidLoansCentavos
        )

        val currentManilaMonth = YearMonth.from(today)
        val remainingDaysInMonth = when {
            yearMonth.isBefore(currentManilaMonth) -> 0
            yearMonth == currentManilaMonth ->
                (yearMonth.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
            else -> yearMonth.lengthOfMonth()
        }

        val dailyPaceCentavos = BudgetCalculator.calculateDailyPace(
            safeToSpendCentavos = safeToSpendCentavos,
            today = today,
            cycleMonth = yearMonth
        )

        val billsWithStatus = bills.map { bill ->
            val dueDate = DueDateCalculator.resolveDueDate(yearMonth, bill.dueDayOfMonth)
            val daysUntilDue = DueDateCalculator.daysUntilDue(yearMonth, bill.dueDayOfMonth, today)
            val isOverdue = !bill.isPaid && daysUntilDue < 0L
            val overdueDays = if (isOverdue) (-daysUntilDue).toInt() else 0
            BillWithStatus(
                bill = bill,
                dueDate = dueDate,
                daysUntilDue = daysUntilDue,
                isOverdue = isOverdue,
                overdueDays = overdueDays
            )
        }

        val loansWithStatus = loans.map { loan ->
            val nextDueMonth = YearMonth.parse(loan.nextDueYearMonth)
            val dueDate = DueDateCalculator.resolveDueDate(nextDueMonth, loan.dueDayOfMonth)
            val daysUntilDue = DueDateCalculator.daysUntilDue(
                nextDueMonth,
                loan.dueDayOfMonth,
                today
            )
            val isPaidForCycle = !loan.isActive ||
                loan.paidInstallments >= loan.totalInstallments ||
                nextDueMonth.isAfter(yearMonth)
            val isOverdue = loan.isActive &&
                loan.paidInstallments < loan.totalInstallments &&
                !nextDueMonth.isAfter(yearMonth) &&
                daysUntilDue < 0L
            val overdueDays = if (isOverdue) (-daysUntilDue).toInt() else 0
            LoanWithStatus(
                loan = loan,
                dueDate = dueDate,
                daysUntilDue = daysUntilDue,
                isPaidForCycle = isPaidForCycle,
                isOverdue = isOverdue,
                overdueDays = overdueDays
            )
        }

        return CycleOverview(
            cycle = cycle,
            yearMonth = yearMonth,
            totalBudgetCentavos = cycle.totalBudgetCentavos,
            spentSoFarCentavos = spentSoFarCentavos,
            committedUnpaidLoansCentavos = committedUnpaidLoansCentavos,
            unpaidBillsCentavos = unpaidBillsCentavos,
            safeToSpendCentavos = safeToSpendCentavos,
            dailyPaceCentavos = dailyPaceCentavos,
            remainingDaysInMonth = remainingDaysInMonth,
            envelopes = envelopes,
            bills = billsWithStatus,
            overdueBillsCount = billsWithStatus.count { it.isOverdue },
            loans = loansWithStatus,
            overdueLoansCount = loansWithStatus.count { it.isOverdue },
            transactions = transactions
        )
    }

    private fun resolveSoaDisplayName(
        contentResolver: ContentResolver,
        uri: Uri,
        fallback: String
    ): String {
        val queried = runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }.getOrNull()
        return queried ?: fallback
    }

    companion object {
        val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")
    }
}
