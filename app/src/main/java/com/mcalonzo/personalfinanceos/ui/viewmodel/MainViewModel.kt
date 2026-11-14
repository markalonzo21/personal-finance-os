package com.mcalonzo.personalfinanceos.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineTemplateEntity
import com.mcalonzo.personalfinanceos.data.repository.BudgetRepository
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.domain.model.CycleOverview
import com.mcalonzo.personalfinanceos.domain.model.UndoAction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BottomTab {
    BUDGET,
    REMINDERS,
    MOTO
}

enum class BudgetSubTab {
    ENVELOPES,
    BILLS,
    LOANS,
    ACTIVITY
}

data class EnvelopeItemUi(
    val id: Long,
    val name: String,
    val iconEmoji: String,
    val colorHex: String,
    val allocatedLimitCentavos: Long,
    val spentCentavos: Long,
    val isGasEnvelope: Boolean = false
) {
    val remainingCentavos: Long
        get() = (allocatedLimitCentavos - spentCentavos).coerceAtLeast(0L)

    val overBudgetCentavos: Long
        get() = (spentCentavos - allocatedLimitCentavos).coerceAtLeast(0L)

    val progressRatio: Float
        get() = if (allocatedLimitCentavos <= 0L) {
            0f
        } else {
            (spentCentavos.toFloat() / allocatedLimitCentavos.toFloat()).coerceAtLeast(0f)
        }
}

data class BillItemUi(
    val id: Long,
    val name: String,
    val iconEmoji: String,
    val amountCentavos: Long,
    val dueDayOfMonth: Int,
    val dueDateLabel: String,
    val isPaid: Boolean,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val daysUntilDue: Long,
    val templateGroupId: String = "",
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true
)

data class LoanItemUi(
    val id: Long,
    val name: String,
    val iconEmoji: String,
    val monthlyAmortizationCentavos: Long,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val dueDayOfMonth: Int,
    val dueDateLabel: String,
    val nextDueYearMonth: String,
    val isPaidForCycle: Boolean,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val daysUntilDue: Long,
    val isActive: Boolean = true,
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true
)

data class TransactionItemUi(
    val id: Long,
    val title: String,
    val categoryLabel: String,
    val amountCentavos: Long,
    val timestampEpochMs: Long,
    val timeLabel: String,
    val envelopeId: Long? = null,
    val billId: Long? = null,
    val loanId: Long? = null
)

data class CycleOptionUi(
    val yearMonth: YearMonth,
    val label: String,
    val hasSoa: Boolean = false
)

data class RoutineUiState(
    val totalMonthlyBudgetCentavos: Long = 5_000_000L,
    val autoRolloverEnabled: Boolean = true,
    val envelopeTemplates: List<RoutineEnvelopeTemplateEntity> = emptyList(),
    val billTemplates: List<RoutineBillTemplateEntity> = emptyList()
)

data class BudgetUiState(
    val yearMonthLabel: String = "",
    val safeToSpendFormatted: String = "₱0.00",
    val dailyPaceFormatted: String = "₱0 / day",
    val totalBudgetFormatted: String = "₱0.00",
    val spentSoFarFormatted: String = "₱0.00",
    val committedLoansFormatted: String = "₱0.00",
    val overdueBillsCount: Int = 0,
    val envelopes: List<EnvelopeItemUi> = emptyList(),
    val bills: List<BillItemUi> = emptyList(),
    val loans: List<LoanItemUi> = emptyList(),
    val transactions: List<TransactionItemUi> = emptyList(),
    val selectedSubTab: BudgetSubTab = BudgetSubTab.ENVELOPES,
    val cycleId: Long = 0L,
    val selectedYearMonth: String = "",
    val remainingDaysInMonth: Int = 0,
    val spentPercent: Int = 0,
    val spentProgressFraction: Float = 0f,
    val committedLoansProgressFraction: Float = 0f,
    val activeLoansCount: Int = 0,
    val unpaidLoansCount: Int = 0,
    val isSafeToSpendNegative: Boolean = false,
    val isPastCycle: Boolean = false,
    val soaPdfLocalUri: String? = null,
    val soaFileName: String? = null,
    val availableCycles: List<CycleOptionUi> = emptyList()
)

data class MainUiState(
    val budgetState: BudgetUiState = BudgetUiState(),
    val selectedBottomTab: BottomTab = BottomTab.BUDGET,
    val activeUndo: UndoAction? = null,
    val routineState: RoutineUiState = RoutineUiState(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel internal constructor(
    private val budgetRepository: BudgetRepository,
    @Suppress("unused") private val gasPriceRepository: GasPriceRepository,
    private val todayProvider: () -> LocalDate,
    private val undoTimeoutMs: Long
) : ViewModel() {

    @Inject
    constructor(
        budgetRepository: BudgetRepository,
        gasPriceRepository: GasPriceRepository
    ) : this(
        budgetRepository = budgetRepository,
        gasPriceRepository = gasPriceRepository,
        todayProvider = { LocalDate.now(MANILA_ZONE) },
        undoTimeoutMs = DEFAULT_UNDO_TIMEOUT_MS
    )

    private val selectedYearMonth = MutableStateFlow(YearMonth.from(todayProvider()))
    private val selectedBottomTab = MutableStateFlow(BottomTab.BUDGET)
    private val selectedSubTab = MutableStateFlow(BudgetSubTab.ENVELOPES)
    private val activeUndo = MutableStateFlow<UndoAction?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val infoMessage = MutableStateFlow<String?>(null)

    private var undoTimerJob: Job? = null

    private val overviewFlow: Flow<CycleOverview?> = selectedYearMonth
        .flatMapLatest { ym ->
            budgetRepository.observeActiveCycleOverview(ym, todayProvider())
                .map<CycleOverview, CycleOverview?> { it }
                .onStart { emit(null) }
        }

    private val routineStateFlow: Flow<RoutineUiState> = combine(
        budgetRepository.observeRoutineTemplate(),
        budgetRepository.observeEnvelopeTemplates(),
        budgetRepository.observeBillTemplates()
    ) { template: RoutineTemplateEntity?, envelopes, bills ->
        RoutineUiState(
            totalMonthlyBudgetCentavos = template?.totalMonthlyBudgetCentavos
                ?: envelopes.sumOf { it.allocatedLimitCentavos },
            autoRolloverEnabled = template?.autoRolloverEnabled ?: true,
            envelopeTemplates = envelopes,
            billTemplates = bills
        )
    }

    private data class ChromeControlsState(
        val selectedMonth: YearMonth,
        val bottomTab: BottomTab,
        val subTab: BudgetSubTab,
        val undo: UndoAction?,
        val error: String?,
        val info: String?
    )

    private val chromeControlsFlow: Flow<ChromeControlsState> = combine(
        selectedYearMonth,
        selectedBottomTab,
        selectedSubTab,
        activeUndo,
        combine(errorMessage, infoMessage) { err, info -> err to info }
    ) { month, bottomTab, subTab, undo, messages ->
        ChromeControlsState(
            selectedMonth = month,
            bottomTab = bottomTab,
            subTab = subTab,
            undo = undo,
            error = messages.first,
            info = messages.second
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        overviewFlow,
        budgetRepository.observeAllCycles(),
        routineStateFlow,
        chromeControlsFlow
    ) { overview, allCycles, routineState, chrome ->
        val today = todayProvider()
        val budgetUi = if (overview != null) {
            mapOverviewToUi(
                overview = overview,
                allCycles = allCycles,
                selectedSubTab = chrome.subTab,
                today = today
            )
        } else {
            BudgetUiState(
                yearMonthLabel = formatYearMonthLabel(chrome.selectedMonth),
                selectedYearMonth = chrome.selectedMonth.toString(),
                selectedSubTab = chrome.subTab,
                availableCycles = buildCycleOptions(allCycles, chrome.selectedMonth, today)
            )
        }

        MainUiState(
            budgetState = budgetUi,
            selectedBottomTab = chrome.bottomTab,
            activeUndo = chrome.undo,
            routineState = routineState,
            errorMessage = chrome.error,
            infoMessage = chrome.info,
            isLoading = overview == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = formatYearMonthLabel(selectedYearMonth.value),
                selectedYearMonth = selectedYearMonth.value.toString()
            ),
            isLoading = true
        )
    )

    fun selectMonth(yearMonth: YearMonth) {
        selectedYearMonth.value = yearMonth
        viewModelScope.launch {
            runMutation("Unable to open month cycle") {
                budgetRepository.getOrCreateCycleId(yearMonth)
            }
        }
    }

    fun switchBottomTab(tab: BottomTab) {
        selectedBottomTab.value = tab
    }

    fun switchBudgetSubTab(subTab: BudgetSubTab) {
        selectedSubTab.value = subTab
    }

    fun logExpense(envelopeId: Long, amountCentavos: Long, title: String = "") {
        viewModelScope.launch {
            runMutation("Unable to log expense") {
                val cycleId = resolveActiveCycleId()
                val undo = budgetRepository.logExpense(
                    cycleId = cycleId,
                    envelopeId = envelopeId,
                    amountCentavos = amountCentavos,
                    title = title
                )
                startUndoWindow(undo)
            }
        }
    }

    fun settleBill(billId: Long, overrideAmountCentavos: Long? = null) {
        viewModelScope.launch {
            runMutation("Unable to settle bill") {
                val undo = budgetRepository.settleBill(
                    billId = billId,
                    overrideAmountCentavos = overrideAmountCentavos
                )
                startUndoWindow(undo)
            }
        }
    }

    fun settleLoan(loanId: Long, allowAdvancePayment: Boolean = false) {
        viewModelScope.launch {
            runMutation("Unable to settle loan") {
                val cycleId = resolveActiveCycleId()
                val undo = budgetRepository.settleLoan(
                    loanId = loanId,
                    cycleId = cycleId,
                    allowAdvancePayment = allowAdvancePayment
                )
                startUndoWindow(undo)
            }
        }
    }

    fun undoLast() {
        val pending = activeUndo.value ?: return
        undoTimerJob?.cancel()
        undoTimerJob = null
        activeUndo.value = null
        viewModelScope.launch {
            runMutation("Unable to undo action") {
                budgetRepository.undoAction(pending)
                infoMessage.value = "Undid ${pending.label}"
            }
        }
    }

    fun dismissUndo() {
        undoTimerJob?.cancel()
        undoTimerJob = null
        activeUndo.value = null
    }

    fun addEnvelope(
        name: String,
        iconEmoji: String,
        limitCentavos: Long,
        colorHex: String,
        addToRoutine: Boolean
    ) {
        viewModelScope.launch {
            runMutation("Unable to add envelope") {
                val cycleId = resolveActiveCycleId()
                budgetRepository.addEnvelope(
                    cycleId = cycleId,
                    name = name,
                    iconEmoji = iconEmoji,
                    limitCentavos = limitCentavos,
                    colorHex = colorHex,
                    addToRoutine = addToRoutine
                )
                infoMessage.value = "Added $name envelope"
            }
        }
    }

    fun updateEnvelopeIcon(envelopeId: Long, iconEmoji: String) {
        viewModelScope.launch {
            runMutation("Unable to update icon") {
                budgetRepository.updateEnvelopeIcon(envelopeId, iconEmoji)
            }
        }
    }

    fun updateEnvelope(
        envelopeId: Long,
        name: String,
        iconEmoji: String,
        limitCentavos: Long,
        colorHex: String
    ) {
        viewModelScope.launch {
            runMutation("Unable to update envelope") {
                budgetRepository.updateEnvelope(
                    envelopeId = envelopeId,
                    name = name,
                    iconEmoji = iconEmoji,
                    limitCentavos = limitCentavos,
                    colorHex = colorHex
                )
                infoMessage.value = "Updated $name"
            }
        }
    }

    fun deleteEnvelope(envelopeId: Long) {
        viewModelScope.launch {
            runMutation("Unable to delete envelope") {
                budgetRepository.deleteEnvelope(envelopeId)
                infoMessage.value = "Envelope removed"
            }
        }
    }

    fun addBill(
        name: String,
        iconEmoji: String,
        amountCentavos: Long,
        dueDayOfMonth: Int,
        addToRoutine: Boolean
    ) {
        viewModelScope.launch {
            runMutation("Unable to add bill") {
                val cycleId = resolveActiveCycleId()
                budgetRepository.addBill(
                    cycleId = cycleId,
                    name = name,
                    iconEmoji = iconEmoji,
                    amountCentavos = amountCentavos,
                    dueDayOfMonth = dueDayOfMonth,
                    addToRoutine = addToRoutine
                )
                infoMessage.value = "Added $name"
            }
        }
    }

    fun updateBill(
        billId: Long,
        name: String,
        iconEmoji: String,
        amountCentavos: Long,
        dueDayOfMonth: Int
    ) {
        viewModelScope.launch {
            runMutation("Unable to update bill") {
                budgetRepository.updateBill(
                    billId = billId,
                    name = name,
                    iconEmoji = iconEmoji,
                    amountCentavos = amountCentavos,
                    dueDayOfMonth = dueDayOfMonth
                )
                infoMessage.value = "Updated $name"
            }
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch {
            runMutation("Unable to delete bill") {
                budgetRepository.deleteBill(billId)
                infoMessage.value = "Bill removed"
            }
        }
    }

    fun addLoan(
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) {
        viewModelScope.launch {
            runMutation("Unable to add loan") {
                budgetRepository.addLoan(
                    name = name,
                    iconEmoji = iconEmoji,
                    monthlyAmortizationCentavos = monthlyAmortizationCentavos,
                    totalInstallments = totalInstallments,
                    paidInstallments = paidInstallments,
                    dueDayOfMonth = dueDayOfMonth,
                    nextDueYearMonth = nextDueYearMonth
                )
                infoMessage.value = "Added $name"
            }
        }
    }

    fun updateLoan(
        loanId: Long,
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) {
        viewModelScope.launch {
            runMutation("Unable to update loan") {
                budgetRepository.updateLoan(
                    loanId = loanId,
                    name = name,
                    iconEmoji = iconEmoji,
                    monthlyAmortizationCentavos = monthlyAmortizationCentavos,
                    totalInstallments = totalInstallments,
                    paidInstallments = paidInstallments,
                    dueDayOfMonth = dueDayOfMonth,
                    nextDueYearMonth = nextDueYearMonth
                )
                infoMessage.value = "Updated $name"
            }
        }
    }

    fun deleteLoan(loanId: Long) {
        viewModelScope.launch {
            runMutation("Unable to delete loan") {
                budgetRepository.deleteLoan(loanId)
                infoMessage.value = "Loan removed"
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            runMutation("Unable to revert transaction") {
                if (activeUndo.value?.transactionId == transactionId) {
                    dismissUndo()
                }
                budgetRepository.deleteTransactionAndRevert(transactionId)
                infoMessage.value = "Transaction deleted and balance restored"
            }
        }
    }

    fun saveRoutine(
        totalMonthlyBudgetCentavos: Long,
        autoRolloverEnabled: Boolean,
        envelopeTemplates: List<RoutineEnvelopeTemplateEntity>,
        billTemplates: List<RoutineBillTemplateEntity>,
        applyToCurrentMonth: Boolean
    ) {
        viewModelScope.launch {
            runMutation("Unable to save routine") {
                budgetRepository.updateRoutineTemplate(
                    totalMonthlyBudgetCentavos = totalMonthlyBudgetCentavos,
                    autoRolloverEnabled = autoRolloverEnabled,
                    envelopeTemplates = envelopeTemplates,
                    billTemplates = billTemplates,
                    applyToCurrentMonth = applyToCurrentMonth,
                    currentCycleMonth = selectedYearMonth.value
                )
                infoMessage.value = if (applyToCurrentMonth) {
                    "Routine updated and applied to ${formatYearMonthLabel(selectedYearMonth.value)}"
                } else {
                    "Routine saved for upcoming monthly rollovers"
                }
            }
        }
    }

    fun attachSoaPdf(uri: Uri) {
        viewModelScope.launch {
            runMutation("Unable to attach SOA PDF") {
                val month = selectedYearMonth.value
                val cycleId = resolveActiveCycleId()
                val stored = budgetRepository.attachSoaPdf(
                    cycleId = cycleId,
                    yearMonth = month.toString(),
                    uri = uri
                )
                infoMessage.value = "Attached ${stored.fileName}"
            }
        }
    }

    fun clearMessages() {
        errorMessage.value = null
        infoMessage.value = null
    }

    private suspend fun resolveActiveCycleId(): Long {
        val currentId = uiState.value.budgetState.cycleId
        val targetMonth = selectedYearMonth.value
        if (currentId > 0L && uiState.value.budgetState.selectedYearMonth == targetMonth.toString()) {
            return currentId
        }
        return budgetRepository.getOrCreateCycleId(targetMonth)
    }

    private fun startUndoWindow(action: UndoAction) {
        undoTimerJob?.cancel()
        activeUndo.value = action
        undoTimerJob = viewModelScope.launch {
            delay(undoTimeoutMs)
            if (activeUndo.value == action) {
                activeUndo.value = null
            }
        }
    }

    private suspend fun runMutation(fallbackError: String, block: suspend () -> Unit) {
        try {
            errorMessage.value = null
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            errorMessage.value = e.message?.takeIf { it.isNotBlank() } ?: fallbackError
        }
    }

    private fun mapOverviewToUi(
        overview: CycleOverview,
        allCycles: List<BudgetCycleEntity>,
        selectedSubTab: BudgetSubTab,
        today: LocalDate
    ): BudgetUiState {
        val total = overview.totalBudgetCentavos
        val spentFrac = if (total > 0L) {
            (overview.spentSoFarCentavos.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val loansFrac = if (total > 0L) {
            (overview.committedUnpaidLoansCentavos.toFloat() / total.toFloat())
                .coerceIn(0f, (1f - spentFrac).coerceAtLeast(0f))
        } else {
            0f
        }
        val spentPct = if (total > 0L) {
            ((overview.spentSoFarCentavos * 100.0) / total.toDouble()).roundToInt().coerceAtLeast(0)
        } else {
            0
        }

        val envelopesUi = overview.envelopes.map { env ->
            EnvelopeItemUi(
                id = env.id,
                name = env.name,
                iconEmoji = env.iconEmoji,
                colorHex = env.colorHex,
                allocatedLimitCentavos = env.allocatedLimitCentavos,
                spentCentavos = env.spentCentavos,
                isGasEnvelope = env.isGasEnvelope
            )
        }

        val billsUi = overview.bills
            .sortedWith(
                compareBy<com.mcalonzo.personalfinanceos.domain.model.BillWithStatus>(
                    { it.bill.isPaid },
                    { !it.isOverdue },
                    { it.bill.dueDayOfMonth },
                    { it.bill.id }
                )
            )
            .map { bws ->
                BillItemUi(
                    id = bws.bill.id,
                    name = bws.bill.name,
                    iconEmoji = bws.bill.iconEmoji,
                    amountCentavos = bws.bill.amountCentavos,
                    dueDayOfMonth = bws.bill.dueDayOfMonth,
                    dueDateLabel = bws.dueDate.format(SHORT_DATE_FORMATTER),
                    isPaid = bws.bill.isPaid,
                    isOverdue = bws.isOverdue,
                    overdueDays = bws.overdueDays,
                    daysUntilDue = bws.daysUntilDue,
                    templateGroupId = bws.bill.templateGroupId,
                    notify7d = bws.bill.notify7d,
                    notify3d = bws.bill.notify3d,
                    notify1d = bws.bill.notify1d,
                    notifyOnDue = bws.bill.notifyOnDue,
                    notifyDailyOverdue = bws.bill.notifyDailyOverdue
                )
            }

        val loansUi = overview.loans
            .sortedWith(
                compareBy<com.mcalonzo.personalfinanceos.domain.model.LoanWithStatus>(
                    { !it.loan.isActive },
                    { it.isPaidForCycle },
                    { !it.isOverdue },
                    { it.dueDate },
                    { it.loan.id }
                )
            )
            .map { lws ->
                LoanItemUi(
                    id = lws.loan.id,
                    name = lws.loan.name,
                    iconEmoji = lws.loan.iconEmoji,
                    monthlyAmortizationCentavos = lws.loan.monthlyAmortizationCentavos,
                    totalInstallments = lws.loan.totalInstallments,
                    paidInstallments = lws.loan.paidInstallments,
                    dueDayOfMonth = lws.loan.dueDayOfMonth,
                    dueDateLabel = lws.dueDate.format(SHORT_DATE_FORMATTER),
                    nextDueYearMonth = lws.loan.nextDueYearMonth,
                    isPaidForCycle = lws.isPaidForCycle,
                    isOverdue = lws.isOverdue,
                    overdueDays = lws.overdueDays,
                    daysUntilDue = lws.daysUntilDue,
                    isActive = lws.loan.isActive,
                    notify7d = lws.loan.notify7d,
                    notify3d = lws.loan.notify3d,
                    notify1d = lws.loan.notify1d,
                    notifyOnDue = lws.loan.notifyOnDue,
                    notifyDailyOverdue = lws.loan.notifyDailyOverdue
                )
            }

        val transactionsUi = overview.transactions.map { tx ->
            TransactionItemUi(
                id = tx.id,
                title = tx.title,
                categoryLabel = tx.categoryLabel,
                amountCentavos = tx.amountCentavos,
                timestampEpochMs = tx.timestampEpochMs,
                timeLabel = formatTransactionTime(tx.timestampEpochMs, today),
                envelopeId = tx.envelopeId,
                billId = tx.billId,
                loanId = tx.loanId
            )
        }

        val activeLoansCount = loansUi.count { it.isActive && it.paidInstallments < it.totalInstallments }
        val unpaidLoansCount = loansUi.count { it.isActive && !it.isPaidForCycle }
        val isPastCycle = overview.yearMonth.isBefore(YearMonth.from(today))

        return BudgetUiState(
            yearMonthLabel = formatYearMonthLabel(overview.yearMonth),
            safeToSpendFormatted = BudgetCalculator.formatPeso(overview.safeToSpendCentavos),
            dailyPaceFormatted = "${BudgetCalculator.formatPeso(overview.dailyPaceCentavos, showDecimal = false)} / day",
            totalBudgetFormatted = BudgetCalculator.formatPeso(overview.totalBudgetCentavos),
            spentSoFarFormatted = BudgetCalculator.formatPeso(overview.spentSoFarCentavos),
            committedLoansFormatted = BudgetCalculator.formatPeso(overview.committedUnpaidLoansCentavos),
            overdueBillsCount = overview.overdueBillsCount,
            envelopes = envelopesUi,
            bills = billsUi,
            loans = loansUi,
            transactions = transactionsUi,
            selectedSubTab = selectedSubTab,
            cycleId = overview.cycle.id,
            selectedYearMonth = overview.yearMonth.toString(),
            remainingDaysInMonth = overview.remainingDaysInMonth,
            spentPercent = spentPct,
            spentProgressFraction = spentFrac,
            committedLoansProgressFraction = loansFrac,
            activeLoansCount = activeLoansCount,
            unpaidLoansCount = unpaidLoansCount,
            isSafeToSpendNegative = overview.safeToSpendCentavos < 0L,
            isPastCycle = isPastCycle,
            soaPdfLocalUri = overview.cycle.soaPdfLocalUri,
            soaFileName = overview.cycle.soaFileName,
            availableCycles = buildCycleOptions(allCycles, overview.yearMonth, today)
        )
    }

    private fun buildCycleOptions(
        allCycles: List<BudgetCycleEntity>,
        selectedMonth: YearMonth,
        today: LocalDate
    ): List<CycleOptionUi> {
        val currentMonth = YearMonth.from(today)
        val nextMonth = currentMonth.plusMonths(1)
        val persistedByMonth = allCycles.associateBy { YearMonth.parse(it.yearMonth) }
        val months = (persistedByMonth.keys + setOf(currentMonth, nextMonth, selectedMonth))
            .distinct()
            .sortedDescending()
        return months.map { ym ->
            CycleOptionUi(
                yearMonth = ym,
                label = formatYearMonthLabel(ym),
                hasSoa = !persistedByMonth[ym]?.soaPdfLocalUri.isNullOrBlank()
            )
        }
    }

    private fun formatYearMonthLabel(yearMonth: YearMonth): String {
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "$monthName ${yearMonth.year}"
    }

    private fun formatTransactionTime(timestampEpochMs: Long, today: LocalDate): String {
        val dateTime = Instant.ofEpochMilli(timestampEpochMs).atZone(MANILA_ZONE)
        val txDate = dateTime.toLocalDate()
        val timePart = dateTime.format(TIME_FORMATTER)
        return when (txDate) {
            today -> "Today • $timePart"
            today.minusDays(1) -> "Yesterday • $timePart"
            else -> "${dateTime.format(SHORT_DATE_FORMATTER)} • $timePart"
        }
    }

    companion object {
        const val DEFAULT_UNDO_TIMEOUT_MS = 5_000L
        val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")
        private val SHORT_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        private val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}
