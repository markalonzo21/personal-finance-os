package com.mcalonzo.personalfinanceos.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcalonzo.personalfinanceos.data.local.GasWatcherConfig
import com.mcalonzo.personalfinanceos.data.local.InMemoryThemePreferenceStore
import com.mcalonzo.personalfinanceos.data.local.ThemeMode
import com.mcalonzo.personalfinanceos.data.local.ThemePreferenceStore
import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import com.mcalonzo.personalfinanceos.data.local.entity.MotoProfileEntity
import com.mcalonzo.personalfinanceos.data.local.entity.PmsIntervalEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineTemplateEntity
import com.mcalonzo.personalfinanceos.data.ocr.OcrImageScanner
import com.mcalonzo.personalfinanceos.data.remote.GasFuelType
import com.mcalonzo.personalfinanceos.data.remote.ParsedGasStation
import com.mcalonzo.personalfinanceos.data.repository.BudgetRepository
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.domain.calculator.MotoCalculator
import com.mcalonzo.personalfinanceos.domain.calculator.OcrExpenseParser
import com.mcalonzo.personalfinanceos.domain.model.CycleOverview
import com.mcalonzo.personalfinanceos.domain.model.OcrLineBox
import com.mcalonzo.personalfinanceos.domain.model.ScanDocumentType
import com.mcalonzo.personalfinanceos.domain.model.ScannedExpenseDraft
import com.mcalonzo.personalfinanceos.domain.model.UndoAction
import com.mcalonzo.personalfinanceos.worker.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BottomTab {
    BUDGET,
    REMINDERS,
    MOTO,
    SETTINGS
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
    val availableCycles: List<CycleOptionUi> = emptyList(),
    val isAmountsHidden: Boolean = false
)

data class PmsItemUi(
    val id: Long = 0L,
    val taskName: String = "",
    val iconEmoji: String = "🔧",
    val intervalKm: Int = 3000,
    val lastServiceOdometerKm: Int = 0,
    val remainingKm: Int = 0,
    val isOverdue: Boolean = false,
    val dueStatusText: String = "",
    val lastServiceEpochMs: Long = 0L
)

data class GasWatcherPickerUiState(
    val isLoadingStations: Boolean = false,
    val errorMessage: String? = null,
    val selectedLocationSlug: String = GasPriceRepository.DEFAULT_LOCATION_SLUG,
    val stations: List<ParsedGasStation> = emptyList()
)

data class MotoUiState(
    val modelName: String = "Honda Click 160",
    val odometerKm: Int = 8450,
    val tankCapacityLiters: Double = 5.5,
    val locationSlug: String = GasPriceRepository.DEFAULT_LOCATION_SLUG,
    val locationDisplayName: String = GasPriceRepository.DEFAULT_LOCATION_NAME,
    val stationId: Int = GasPriceRepository.STATION_ID,
    val stationDisplayName: String = GasPriceRepository.STATION_DISPLAY_NAME,
    val fuelKey: String = GasFuelType.PREMIUM_95.jsonKey,
    val fuelType: String = GasFuelType.PREMIUM_95.displayName,
    val pricePerLiterFormatted: String = "₱78.40",
    val prevPriceFormatted: String = "",
    val trendBadgeText: String = "— ₱0.00",
    val isPriceUp: Boolean = false,
    val isPriceDown: Boolean = false,
    val fullTankCostFormatted: String = "₱431.20",
    val pmsItems: List<PmsItemUi> = emptyList(),
    val pricePerLiterCentavos: Long = 7840L,
    val isSyncing: Boolean = false,
    val isSyncingGas: Boolean = isSyncing,
    val watcherPickerState: GasWatcherPickerUiState = GasWatcherPickerUiState()
)

data class DueReminderItemUi(
    val id: Long,
    val itemKey: String,
    val title: String,
    val iconEmoji: String,
    val amountCentavos: Long,
    val amountFormatted: String,
    val isLoan: Boolean,
    val dueDateLabel: String,
    val dueDayOfMonth: Int,
    val daysUntilDue: Long,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val isPaid: Boolean,
    val dueStatusText: String,
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true,
    val dueYearMonth: String? = null,
    val categoryLabel: String = if (isLoan) "Loan" else "Bill"
)

data class RemindersUiState(
    val globalPushEnabled: Boolean = true,
    val items: List<DueReminderItemUi> = emptyList()
)

data class ScanBatchUiState(
    val isScanning: Boolean = false,
    val isSheetVisible: Boolean = false,
    val documentType: ScanDocumentType = ScanDocumentType.RECEIPT,
    val selectedCard: CardEntity? = null,
    val storeName: String = "",
    val defaultEnvelopeName: String? = null,
    val drafts: List<ScannedExpenseDraft> = emptyList()
)

data class MainUiState(
    val budgetState: BudgetUiState = BudgetUiState(),
    val selectedBottomTab: BottomTab = BottomTab.BUDGET,
    val activeUndo: UndoAction? = null,
    val routineState: RoutineUiState = RoutineUiState(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoading: Boolean = false,
    val motoState: MotoUiState = MotoUiState(),
    val remindersState: RemindersUiState = RemindersUiState(),
    val cards: List<CardEntity> = emptyList(),
    val scanBatchState: ScanBatchUiState = ScanBatchUiState(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel internal constructor(
    private val budgetRepository: BudgetRepository,
    private val gasPriceRepository: GasPriceRepository,
    private val motoDao: MotoDao? = null,
    @ApplicationContext private val context: Context? = null,
    private val todayProvider: () -> LocalDate = { LocalDate.now(MANILA_ZONE) },
    private val undoTimeoutMs: Long = DEFAULT_UNDO_TIMEOUT_MS,
    private val ocrImageScanner: OcrImageScanner? = null,
    private val themePreferenceStore: ThemePreferenceStore = InMemoryThemePreferenceStore()
) : ViewModel() {

    @Inject
    constructor(
        budgetRepository: BudgetRepository,
        gasPriceRepository: GasPriceRepository,
        motoDao: MotoDao,
        @ApplicationContext context: Context,
        ocrImageScanner: OcrImageScanner,
        themePreferenceStore: ThemePreferenceStore
    ) : this(
        budgetRepository = budgetRepository,
        gasPriceRepository = gasPriceRepository,
        motoDao = motoDao,
        context = context,
        todayProvider = { LocalDate.now(MANILA_ZONE) },
        undoTimeoutMs = DEFAULT_UNDO_TIMEOUT_MS,
        ocrImageScanner = ocrImageScanner,
        themePreferenceStore = themePreferenceStore
    )

    internal constructor(
        budgetRepository: BudgetRepository,
        gasPriceRepository: GasPriceRepository,
        todayProvider: () -> LocalDate,
        undoTimeoutMs: Long
    ) : this(
        budgetRepository = budgetRepository,
        gasPriceRepository = gasPriceRepository,
        motoDao = null,
        context = null,
        todayProvider = todayProvider,
        undoTimeoutMs = undoTimeoutMs,
        ocrImageScanner = null
    )

    private val selectedYearMonth = MutableStateFlow(YearMonth.from(todayProvider()))
    private val selectedBottomTab = MutableStateFlow(BottomTab.BUDGET)
    private val selectedSubTab = MutableStateFlow(BudgetSubTab.ENVELOPES)
    private val activeUndo = MutableStateFlow<UndoAction?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val infoMessage = MutableStateFlow<String?>(null)
    private val isAmountsHidden = MutableStateFlow(false)
    private val isGasSyncing = MutableStateFlow(false)
    private val gasWatcherPickerState = MutableStateFlow(
        GasWatcherPickerUiState(selectedLocationSlug = gasPriceRepository.getConfig().locationSlug)
    )
    private val scanBatchState = MutableStateFlow(ScanBatchUiState())
    private val globalPushEnabledFlow = MutableStateFlow(
        context?.let { NotificationHelper.isGlobalPushEnabled(it) } ?: true
    )

    private var undoTimerJob: Job? = null
    private var stationLoadJob: Job? = null

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
        val info: String?,
        val isAmountsHidden: Boolean
    )

    private val chromeControlsFlow: Flow<ChromeControlsState> = combine(
        selectedYearMonth,
        selectedBottomTab,
        selectedSubTab,
        activeUndo,
        combine(errorMessage, infoMessage, isAmountsHidden) { err, info, hidden ->
            Triple(err, info, hidden)
        }
    ) { month, bottomTab, subTab, undo, messages ->
        ChromeControlsState(
            selectedMonth = month,
            bottomTab = bottomTab,
            subTab = subTab,
            undo = undo,
            error = messages.first,
            info = messages.second,
            isAmountsHidden = messages.third
        )
    }

    private val motoProfileFlow: Flow<MotoProfileEntity?> =
        motoDao?.observeMotoProfile() ?: flowOf(MotoProfileEntity())

    private val pmsIntervalsFlow: Flow<List<PmsIntervalEntity>> =
        motoDao?.observePmsIntervals() ?: flowOf(emptyList())

    private val gasPriceFlow: Flow<GasPriceHistoryEntity?> =
        gasPriceRepository.observeLatestPrice()

    private val gasConfigFlow: Flow<GasWatcherConfig> =
        gasPriceRepository.observeConfig()

    private val motoUiStateFlow: Flow<MotoUiState> = combine(
        motoProfileFlow,
        gasPriceFlow,
        pmsIntervalsFlow,
        isGasSyncing,
        combine(gasConfigFlow, gasWatcherPickerState) { cfg, picker -> cfg to picker }
    ) { profile, gasPrice, pmsList, isSyncing, (config, picker) ->
        mapMotoToUi(profile, gasPrice, pmsList, isSyncing, config, picker)
    }

    private val remindersUiStateFlow: Flow<RemindersUiState> = combine(
        overviewFlow,
        globalPushEnabledFlow
    ) { overview, pushEnabled ->
        if (overview == null) {
            RemindersUiState(globalPushEnabled = pushEnabled, items = emptyList())
        } else {
            mapRemindersToUi(overview, pushEnabled)
        }
    }

    private val budgetDataFlow = combine(
        overviewFlow,
        budgetRepository.observeAllCycles(),
        routineStateFlow
    ) { overview, allCycles, routineState ->
        Triple(overview, allCycles, routineState)
    }

    private val scanAndCardsFlow: Flow<Triple<List<CardEntity>, ScanBatchUiState, ThemeMode>> = combine(
        budgetRepository.observeCards(),
        scanBatchState,
        themePreferenceStore.observeThemeMode()
    ) { cards, batch, themeMode ->
        Triple(cards, batch, themeMode)
    }

    val uiState: StateFlow<MainUiState> = combine(
        budgetDataFlow,
        chromeControlsFlow,
        motoUiStateFlow,
        remindersUiStateFlow,
        scanAndCardsFlow
    ) { budgetData, chrome, motoState, remindersState, scanAndCards ->
        val (overview, allCycles, routineState) = budgetData
        val (cards, batchState, themeMode) = scanAndCards
        val today = todayProvider()
        val budgetUi = if (overview != null) {
            mapOverviewToUi(
                overview = overview,
                allCycles = allCycles,
                selectedSubTab = chrome.subTab,
                today = today
            ).copy(isAmountsHidden = chrome.isAmountsHidden)
        } else {
            BudgetUiState(
                yearMonthLabel = formatYearMonthLabel(chrome.selectedMonth),
                selectedYearMonth = chrome.selectedMonth.toString(),
                selectedSubTab = chrome.subTab,
                availableCycles = buildCycleOptions(allCycles, chrome.selectedMonth, today),
                isAmountsHidden = chrome.isAmountsHidden
            )
        }

        MainUiState(
            budgetState = budgetUi,
            selectedBottomTab = chrome.bottomTab,
            activeUndo = chrome.undo,
            routineState = routineState,
            errorMessage = chrome.error,
            infoMessage = chrome.info,
            isLoading = overview == null,
            motoState = motoState,
            remindersState = remindersState,
            cards = cards,
            scanBatchState = batchState,
            themeMode = themeMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState(
            budgetState = BudgetUiState(
                yearMonthLabel = formatYearMonthLabel(selectedYearMonth.value),
                selectedYearMonth = selectedYearMonth.value.toString()
            ),
            isLoading = true,
            themeMode = themePreferenceStore.getThemeMode()
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

    fun selectThemeMode(mode: ThemeMode) {
        themePreferenceStore.setThemeMode(mode)
    }

    fun switchBudgetSubTab(subTab: BudgetSubTab) {
        selectedSubTab.value = subTab
    }

    fun toggleAmountsVisibility() {
        isAmountsHidden.value = !isAmountsHidden.value
    }

    fun upsertCard(
        id: Long = 0L,
        name: String,
        cardType: String,
        billingCycleDay: Int?
    ) {
        viewModelScope.launch {
            runMutation("Unable to save card") {
                budgetRepository.upsertCard(
                    id = id,
                    name = name,
                    cardType = cardType,
                    billingCycleDay = billingCycleDay
                )
                infoMessage.value = "Saved ${name.trim()}"
            }
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            runMutation("Unable to delete card") {
                budgetRepository.deleteCard(cardId)
                infoMessage.value = "Card removed"
            }
        }
    }

    fun processScannedLines(
        lines: List<OcrLineBox>,
        documentType: ScanDocumentType,
        card: CardEntity?,
        append: Boolean
    ) {
        viewModelScope.launch {
            runMutation("Unable to parse scanned lines") {
                applyScannedLines(
                    lines = lines,
                    documentType = documentType,
                    card = card,
                    append = append
                )
            }
        }
    }

    fun scanImageUri(
        uri: Uri,
        documentType: ScanDocumentType,
        card: CardEntity?,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            scanBatchState.value = scanBatchState.value.copy(isScanning = true)
            try {
                runMutation("Unable to scan image") {
                    val appContext = context
                        ?: throw IllegalStateException("Application context is required to scan image")
                    val scanner = ocrImageScanner ?: OcrImageScanner()
                    val lines = scanner.scanImageUri(appContext, uri)
                    applyScannedLines(
                        lines = lines,
                        documentType = documentType,
                        card = card,
                        append = append
                    )
                }
            } finally {
                scanBatchState.value = scanBatchState.value.copy(isScanning = false)
            }
        }
    }

    fun scanPdfUri(
        uri: Uri,
        documentType: ScanDocumentType,
        card: CardEntity?,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            scanBatchState.value = scanBatchState.value.copy(isScanning = true)
            try {
                runMutation("Unable to scan PDF") {
                    val appContext = context
                        ?: throw IllegalStateException("Application context is required to scan PDF")
                    val scanner = ocrImageScanner ?: OcrImageScanner()
                    val lines = scanner.scanPdfUri(appContext, uri)
                    applyScannedLines(
                        lines = lines,
                        documentType = documentType,
                        card = card,
                        append = append
                    )
                }
            } finally {
                scanBatchState.value = scanBatchState.value.copy(isScanning = false)
            }
        }
    }

    fun scanCameraFile(
        file: File,
        documentType: ScanDocumentType,
        card: CardEntity?,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            scanBatchState.value = scanBatchState.value.copy(isScanning = true)
            val scanner = ocrImageScanner ?: OcrImageScanner()
            try {
                runMutation("Unable to scan captured photo") {
                    val appContext = context
                        ?: throw IllegalStateException("Application context is required to scan photo")
                    val uri = scanner.getTempCameraUri(appContext, file)
                    val lines = scanner.scanImageUri(appContext, uri)
                    applyScannedLines(
                        lines = lines,
                        documentType = documentType,
                        card = card,
                        append = append
                    )
                }
            } finally {
                scanner.deleteTempFileQuietly(file)
                scanBatchState.value = scanBatchState.value.copy(isScanning = false)
            }
        }
    }

    fun updateScanBatchStoreName(name: String) {
        scanBatchState.value = scanBatchState.value.copy(storeName = name)
    }

    fun applyBatchDefaultEnvelope(envelopeName: String) {
        val current = scanBatchState.value
        scanBatchState.value = current.copy(
            defaultEnvelopeName = envelopeName,
            drafts = current.drafts.map { it.copy(envelopeName = envelopeName) }
        )
    }

    fun updateScanDraft(
        localId: String,
        title: String,
        amountCentavos: Long,
        envelopeName: String?
    ) {
        val current = scanBatchState.value
        scanBatchState.value = current.copy(
            drafts = current.drafts.map { draft ->
                if (draft.localId == localId) {
                    draft.copy(
                        title = title,
                        amountCentavos = amountCentavos.coerceAtLeast(0L),
                        envelopeName = envelopeName
                    )
                } else {
                    draft
                }
            }
        )
    }

    fun toggleScanDraft(localId: String) {
        val current = scanBatchState.value
        scanBatchState.value = current.copy(
            drafts = current.drafts.map { draft ->
                if (draft.localId == localId) {
                    draft.copy(isSelected = !draft.isSelected)
                } else {
                    draft
                }
            }
        )
    }

    fun clearScanBatch() {
        scanBatchState.value = ScanBatchUiState()
    }

    fun confirmScannedExpenses() {
        val current = scanBatchState.value
        if (current.drafts.none { it.isSelected && it.amountCentavos > 0L }) {
            clearScanBatch()
            return
        }
        viewModelScope.launch {
            runMutation("Unable to log scanned expenses") {
                val uncheckedKeywords = current.drafts
                    .filter { !it.isSelected && !it.isSummaryNoise && !it.isCredit }
                    .map { it.title }
                val loggedCount = budgetRepository.logScannedExpensesBatch(
                    items = current.drafts,
                    storeName = current.storeName,
                    uncheckedKeywords = uncheckedKeywords
                )
                clearScanBatch()
                if (loggedCount > 0) {
                    infoMessage.value = "Logged $loggedCount scanned expense${if (loggedCount == 1) "" else "s"}"
                }
            }
        }
    }

    private suspend fun applyScannedLines(
        lines: List<OcrLineBox>,
        documentType: ScanDocumentType,
        card: CardEntity?,
        append: Boolean
    ) {
        val storeCandidate = OcrExpenseParser.detectStoreCandidate(lines)
        val matchedProfile = budgetRepository.findMatchingFormatProfile(
            storeCandidate = storeCandidate,
            cardName = card?.name
        )
        val activeMonth = selectedYearMonth.value
        val parsed = OcrExpenseParser.parse(
            lines = lines,
            documentType = documentType,
            billingCycleDay = card?.billingCycleDay,
            activeMonth = activeMonth,
            matchedProfile = matchedProfile,
            cardDisplayName = card?.name,
            referenceYear = todayProvider().year
        )

        if (append) {
            val current = scanBatchState.value
            val idOffset = current.drafts.size
            val reindexed = parsed.drafts.mapIndexed { idx, draft ->
                draft.copy(localId = "draft-${idOffset + idx}")
            }
            val flagged = OcrExpenseParser.flagDuplicates(current.drafts, reindexed)
            scanBatchState.value = current.copy(
                isScanning = false,
                isSheetVisible = true,
                documentType = documentType,
                selectedCard = card ?: current.selectedCard,
                storeName = current.storeName.ifBlank { parsed.detectedStoreName },
                defaultEnvelopeName = current.defaultEnvelopeName ?: matchedProfile?.defaultEnvelopeName,
                drafts = current.drafts + flagged
            )
        } else {
            scanBatchState.value = ScanBatchUiState(
                isScanning = false,
                isSheetVisible = true,
                documentType = documentType,
                selectedCard = card,
                storeName = parsed.detectedStoreName,
                defaultEnvelopeName = matchedProfile?.defaultEnvelopeName,
                drafts = parsed.drafts
            )
        }
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

    fun updateTransaction(
        transactionId: Long,
        title: String,
        amountCentavos: Long,
        newEnvelopeId: Long? = null
    ) {
        viewModelScope.launch {
            runMutation("Unable to update item") {
                if (activeUndo.value?.transactionId == transactionId) {
                    dismissUndo()
                }
                budgetRepository.updateTransaction(
                    transactionId = transactionId,
                    title = title,
                    amountCentavos = amountCentavos,
                    newEnvelopeId = newEnvelopeId
                )
                infoMessage.value = "Updated ${title.trim()}"
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

    // --- Task 8 Moto Care & Reminders Methods ---

    fun updateOdometer(newKm: Int) {
        viewModelScope.launch {
            runMutation("Unable to update odometer") {
                motoDao?.updateOdometerKm(newKm)
                infoMessage.value = "Odometer updated to $newKm km"
            }
        }
    }

    fun logClick160FullTank() {
        viewModelScope.launch {
            runMutation("Unable to log full tank") {
                val cycleId = resolveActiveCycleId()
                val latestPrice = motoDao?.getLatestGasPriceOnce()
                val priceCentavos = latestPrice?.pricePerLiterCentavos ?: 7840L
                val profile = motoDao?.getMotoProfileOnce()
                val tankCapacity = profile?.tankCapacityLiters ?: 5.5
                val cost = MotoCalculator.calculateFullTankCentavos(priceCentavos, tankCapacity)
                val undo = budgetRepository.logGasFullTank(
                    cycleId = cycleId,
                    amountCentavos = cost,
                    label = "Honda Click 160 Full Tank"
                )
                startUndoWindow(undo)
            }
        }
    }

    fun triggerManualGasSync() {
        viewModelScope.launch {
            runMutation("Unable to sync fuel price") {
                isGasSyncing.value = true
                val result = try {
                    gasPriceRepository.syncFromRemote()
                } finally {
                    isGasSyncing.value = false
                }
                result.onSuccess {
                    infoMessage.value = "Gas price synced: ${BudgetCalculator.formatPeso(it.pricePerLiterCentavos)}/L"
                }.onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to sync fuel price"
                }
            }
        }
    }

    fun overrideGasPrice(priceCentavos: Long) {
        viewModelScope.launch {
            runMutation("Unable to override gas price") {
                gasPriceRepository.setManualPriceOverride(priceCentavos)
                infoMessage.value = "Fuel price updated to ${BudgetCalculator.formatPeso(priceCentavos)}/L"
            }
        }
    }

    fun loadGasStationsForLocation(locationSlug: String) {
        stationLoadJob?.cancel()
        stationLoadJob = viewModelScope.launch {
            gasWatcherPickerState.value = gasWatcherPickerState.value.copy(
                isLoadingStations = true,
                errorMessage = null,
                selectedLocationSlug = locationSlug
            )
            val result = gasPriceRepository.fetchStationsForLocation(locationSlug)
            if (gasWatcherPickerState.value.selectedLocationSlug != locationSlug) return@launch
            result.onSuccess { stations ->
                gasWatcherPickerState.value = GasWatcherPickerUiState(
                    isLoadingStations = false,
                    errorMessage = null,
                    selectedLocationSlug = locationSlug,
                    stations = stations
                )
            }.onFailure { err ->
                gasWatcherPickerState.value = GasWatcherPickerUiState(
                    isLoadingStations = false,
                    errorMessage = err.message ?: "Unable to load stations for $locationSlug",
                    selectedLocationSlug = locationSlug,
                    stations = emptyList()
                )
            }
        }
    }

    fun updateGasWatcherConfig(
        locationSlug: String,
        locationDisplayName: String,
        stationId: Int,
        stationDisplayName: String,
        fuelKey: String,
        fuelDisplayName: String
    ) {
        val cleanName = stationDisplayName.trim().ifEmpty { GasPriceRepository.STATION_DISPLAY_NAME }
        val config = GasWatcherConfig(
            locationSlug = locationSlug,
            locationDisplayName = locationDisplayName,
            stationId = stationId,
            stationDisplayName = cleanName,
            fuelKey = fuelKey,
            fuelDisplayName = fuelDisplayName
        )
        viewModelScope.launch {
            runMutation("Unable to update gas watcher") {
                isGasSyncing.value = true
                val result = try {
                    gasPriceRepository.updateWatcherConfig(config)
                } finally {
                    isGasSyncing.value = false
                }
                result.onSuccess {
                    infoMessage.value = "Watching $cleanName ($fuelDisplayName): ${BudgetCalculator.formatPeso(it.pricePerLiterCentavos)}/L"
                }.onFailure { err ->
                    errorMessage.value = "Saved watcher ($cleanName), sync failed: ${err.message ?: "offline"}"
                }
            }
        }
    }

    fun logPmsService(
        pmsId: Long,
        serviceKm: Int,
        costCentavos: Long = 0L,
        envelopeId: Long? = null
    ) {
        viewModelScope.launch {
            runMutation("Unable to log PMS service") {
                val existing = motoDao?.getPmsIntervalById(pmsId)
                    ?: throw IllegalArgumentException("PMS item $pmsId not found")

                if (costCentavos > 0L) {
                    val cycleId = resolveActiveCycleId()
                    val activeEnvelopes = uiState.value.budgetState.envelopes
                    val targetEnv = if (envelopeId != null) {
                        activeEnvelopes.firstOrNull { it.id == envelopeId }
                            ?: throw IllegalArgumentException("Selected envelope $envelopeId not found in cycle")
                    } else {
                        activeEnvelopes.firstOrNull { it.isGasEnvelope }
                            ?: activeEnvelopes.firstOrNull { it.name.contains("Personal", ignoreCase = true) }
                            ?: activeEnvelopes.firstOrNull()
                            ?: throw IllegalStateException("No budget envelope available to charge PMS cost")
                    }

                    val undo = budgetRepository.logExpense(
                        cycleId = cycleId,
                        envelopeId = targetEnv.id,
                        amountCentavos = costCentavos,
                        title = "PMS: ${existing.taskName}"
                    )
                    startUndoWindow(undo)
                }

                motoDao?.updatePmsInterval(
                    existing.copy(
                        lastServiceOdometerKm = serviceKm,
                        lastServiceEpochMs = System.currentTimeMillis()
                    )
                )
                val profile = motoDao?.getMotoProfileOnce()
                if (profile != null && serviceKm > profile.currentOdometerKm) {
                    motoDao.updateOdometerKm(serviceKm)
                }
                if (costCentavos <= 0L) {
                    infoMessage.value = "Logged service for ${existing.taskName}"
                }
            }
        }
    }

    fun addPmsInterval(
        taskName: String,
        iconEmoji: String,
        intervalKm: Int,
        lastServiceKm: Int,
        costCentavos: Long = 0L,
        envelopeId: Long? = null
    ) {
        viewModelScope.launch {
            runMutation("Unable to add PMS schedule") {
                if (costCentavos > 0L) {
                    val cycleId = resolveActiveCycleId()
                    val activeEnvelopes = uiState.value.budgetState.envelopes
                    val targetEnv = if (envelopeId != null) {
                        activeEnvelopes.firstOrNull { it.id == envelopeId }
                            ?: throw IllegalArgumentException("Selected envelope $envelopeId not found in cycle")
                    } else {
                        activeEnvelopes.firstOrNull { it.isGasEnvelope }
                            ?: activeEnvelopes.firstOrNull { it.name.contains("Personal", ignoreCase = true) }
                            ?: activeEnvelopes.firstOrNull()
                            ?: throw IllegalStateException("No budget envelope available to charge PMS cost")
                    }

                    val undo = budgetRepository.logExpense(
                        cycleId = cycleId,
                        envelopeId = targetEnv.id,
                        amountCentavos = costCentavos,
                        title = "PMS: $taskName"
                    )
                    startUndoWindow(undo)
                }

                motoDao?.insertPmsInterval(
                    PmsIntervalEntity(
                        taskName = taskName,
                        iconEmoji = iconEmoji,
                        intervalKm = intervalKm,
                        lastServiceOdometerKm = lastServiceKm,
                        lastServiceEpochMs = System.currentTimeMillis()
                    )
                )
                if (costCentavos <= 0L) {
                    infoMessage.value = "Added PMS task $taskName"
                }
            }
        }
    }

    fun updatePmsInterval(
        id: Long,
        taskName: String,
        iconEmoji: String,
        intervalKm: Int,
        lastServiceOdometerKm: Int
    ) {
        viewModelScope.launch {
            runMutation("Unable to update PMS task") {
                val existing = motoDao?.getPmsIntervalById(id)
                    ?: throw IllegalArgumentException("PMS item $id not found")
                motoDao.updatePmsInterval(
                    existing.copy(
                        taskName = taskName,
                        iconEmoji = iconEmoji,
                        intervalKm = intervalKm,
                        lastServiceOdometerKm = lastServiceOdometerKm
                    )
                )
                infoMessage.value = "Updated $taskName"
            }
        }
    }

    fun deletePmsInterval(id: Long) {
        viewModelScope.launch {
            runMutation("Unable to delete PMS task") {
                motoDao?.deletePmsIntervalById(id)
                infoMessage.value = "PMS task removed"
            }
        }
    }

    fun toggleGlobalPush(enabled: Boolean) {
        context?.let { ctx ->
            NotificationHelper.setGlobalPushEnabled(ctx, enabled)
        }
        globalPushEnabledFlow.value = enabled
        infoMessage.value = if (enabled) "Push notifications enabled" else "Push notifications disabled"
    }

    fun previewDueNotification(item: DueReminderItemUi? = null) {
        val target = item ?: uiState.value.remindersState.items.firstOrNull { !it.isPaid }
        val ctx = context
        if (ctx == null) {
            infoMessage.value = "Notification preview triggered"
            return
        }
        if (target != null && target.id > 0L) {
            val notifId = NotificationHelper.stableNotificationId(target.itemKey)
            NotificationHelper.showDueAlert(
                context = ctx,
                itemKey = target.itemKey,
                notificationId = notifId,
                title = target.title,
                body = "${BudgetCalculator.formatPeso(target.amountCentavos)} due ${target.dueDateLabel} (${target.dueStatusText})",
                entityType = if (target.isLoan) "loan" else "bill",
                entityId = target.id,
                amountCentavos = target.amountCentavos,
                dueYearMonth = target.dueYearMonth,
                includeActions = true
            )
            infoMessage.value = "Preview notification sent for ${target.title}"
        } else {
            NotificationHelper.showDueAlert(
                context = ctx,
                itemKey = "preview_test",
                notificationId = 9999,
                title = "Due Date Alert Preview",
                body = "Converge FiberX ₱1,500.00 is due today (Sample Preview)",
                entityType = "preview",
                entityId = 0L,
                amountCentavos = 150_000L,
                dueYearMonth = null,
                includeActions = false
            )
            infoMessage.value = "Test notification sent"
        }
    }

    fun updateReminderCadence(
        itemKey: String,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) {
        viewModelScope.launch {
            runMutation("Unable to update reminder cadence") {
                if (itemKey.startsWith("bill_")) {
                    val id = itemKey.removePrefix("bill_").toLongOrNull() ?: return@runMutation
                    budgetRepository.updateBillReminderCadence(
                        billId = id,
                        notify7d = notify7d,
                        notify3d = notify3d,
                        notify1d = notify1d,
                        notifyOnDue = notifyOnDue,
                        notifyDailyOverdue = notifyDailyOverdue
                    )
                } else if (itemKey.startsWith("loan_")) {
                    val id = itemKey.removePrefix("loan_").toLongOrNull() ?: return@runMutation
                    budgetRepository.updateLoanReminderCadence(
                        loanId = id,
                        notify7d = notify7d,
                        notify3d = notify3d,
                        notify1d = notify1d,
                        notifyOnDue = notifyOnDue,
                        notifyDailyOverdue = notifyDailyOverdue
                    )
                }
                infoMessage.value = "Updated reminder cadence"
            }
        }
    }

    fun updateBillCadence(
        billId: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) = updateReminderCadence("bill_$billId", notify7d, notify3d, notify1d, notifyOnDue, notifyDailyOverdue)

    fun updateLoanCadence(
        loanId: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) = updateReminderCadence("loan_$loanId", notify7d, notify3d, notify1d, notifyOnDue, notifyDailyOverdue)

    private fun mapMotoToUi(
        profile: MotoProfileEntity?,
        gasPrice: GasPriceHistoryEntity?,
        pmsList: List<PmsIntervalEntity>,
        isSyncing: Boolean,
        config: GasWatcherConfig,
        pickerState: GasWatcherPickerUiState
    ): MotoUiState {
        val modelName = profile?.modelName ?: "Honda Click 160"
        val odoKm = profile?.currentOdometerKm ?: 8450
        val tankLiters = profile?.tankCapacityLiters ?: 5.5
        val stationName = config.stationDisplayName
        val fuelType = config.fuelDisplayName
        val matchingGasPrice = gasPrice?.takeIf {
            it.stationId == config.stationId && it.fuelType == config.fuelDisplayName
        }
        val priceCentavos = matchingGasPrice?.pricePerLiterCentavos ?: if (gasPrice == null) 7840L else 0L
        val priceFormatted = if (priceCentavos > 0L) BudgetCalculator.formatPeso(priceCentavos) else "—"
        val fullTankCostCentavos = if (priceCentavos > 0L) {
            MotoCalculator.calculateFullTankCentavos(priceCentavos, tankLiters)
        } else {
            0L
        }
        val fullTankFormatted = if (priceCentavos > 0L) BudgetCalculator.formatPeso(fullTankCostCentavos) else "—"

        val diff = matchingGasPrice?.diffCentavos ?: 0L
        val isPriceUp = diff > 0L
        val isPriceDown = diff < 0L
        val trendText = when {
            isPriceUp -> "▲ +${BudgetCalculator.formatPeso(diff)}"
            isPriceDown -> "▼ -${BudgetCalculator.formatPeso(kotlin.math.abs(diff))}"
            else -> "— ₱0.00"
        }
        val prevPriceFormatted = if (matchingGasPrice?.previousPricePerLiterCentavos != null) {
            val prevFormatted = BudgetCalculator.formatPeso(matchingGasPrice.previousPricePerLiterCentavos)
            when {
                isPriceUp -> "Prev: $prevFormatted/L (+${BudgetCalculator.formatPeso(diff)})"
                isPriceDown -> "Prev: $prevFormatted/L (-${BudgetCalculator.formatPeso(kotlin.math.abs(diff))})"
                else -> "Prev: $prevFormatted/L (— ₱0.00)"
            }
        } else {
            ""
        }

        val pmsItems = pmsList.map { pms ->
            val remainingKm = MotoCalculator.calculatePmsRemainingKm(
                currentOdometerKm = odoKm,
                intervalKm = pms.intervalKm,
                lastServiceOdometerKm = pms.lastServiceOdometerKm
            )
            val isOverdue = remainingKm < 0
            val statusText = when {
                isOverdue -> "Overdue by ${kotlin.math.abs(remainingKm)} km"
                remainingKm == 0 -> "Due now"
                else -> "Due in $remainingKm km"
            }
            PmsItemUi(
                id = pms.id,
                taskName = pms.taskName,
                iconEmoji = pms.iconEmoji,
                intervalKm = pms.intervalKm,
                lastServiceOdometerKm = pms.lastServiceOdometerKm,
                remainingKm = remainingKm,
                isOverdue = isOverdue,
                dueStatusText = statusText,
                lastServiceEpochMs = pms.lastServiceEpochMs
            )
        }

        return MotoUiState(
            modelName = modelName,
            odometerKm = odoKm,
            tankCapacityLiters = tankLiters,
            locationSlug = config.locationSlug,
            locationDisplayName = config.locationDisplayName,
            stationId = config.stationId,
            stationDisplayName = stationName,
            fuelKey = config.fuelKey,
            fuelType = fuelType,
            pricePerLiterFormatted = priceFormatted,
            prevPriceFormatted = prevPriceFormatted,
            trendBadgeText = trendText,
            isPriceUp = isPriceUp,
            isPriceDown = isPriceDown,
            fullTankCostFormatted = fullTankFormatted,
            pmsItems = pmsItems,
            pricePerLiterCentavos = priceCentavos,
            isSyncing = isSyncing,
            watcherPickerState = pickerState
        )
    }

    private fun mapRemindersToUi(
        overview: CycleOverview,
        pushEnabled: Boolean
    ): RemindersUiState {
        val billItems = overview.bills.map { bws ->
            val dueStatus = when {
                bws.bill.isPaid -> "Paid"
                bws.isOverdue -> "Overdue by ${bws.overdueDays} day${if (bws.overdueDays == 1) "" else "s"}"
                bws.daysUntilDue == 0L -> "Due today"
                bws.daysUntilDue == 1L -> "Due tomorrow"
                else -> "Due in ${bws.daysUntilDue} days"
            }
            DueReminderItemUi(
                id = bws.bill.id,
                itemKey = "bill_${bws.bill.id}",
                title = bws.bill.name,
                iconEmoji = bws.bill.iconEmoji,
                amountCentavos = bws.bill.amountCentavos,
                amountFormatted = BudgetCalculator.formatPeso(bws.bill.amountCentavos),
                isLoan = false,
                dueDateLabel = bws.dueDate.format(SHORT_DATE_FORMATTER),
                dueDayOfMonth = bws.bill.dueDayOfMonth,
                daysUntilDue = bws.daysUntilDue,
                isOverdue = bws.isOverdue,
                overdueDays = bws.overdueDays,
                isPaid = bws.bill.isPaid,
                dueStatusText = dueStatus,
                notify7d = bws.bill.notify7d,
                notify3d = bws.bill.notify3d,
                notify1d = bws.bill.notify1d,
                notifyOnDue = bws.bill.notifyOnDue,
                notifyDailyOverdue = bws.bill.notifyDailyOverdue,
                dueYearMonth = null,
                categoryLabel = "Bill"
            )
        }

        val loanItems = overview.loans
            .filter { it.loan.isActive }
            .map { lws ->
                val dueStatus = when {
                    lws.isPaidForCycle -> "Paid"
                    lws.isOverdue -> "Overdue by ${lws.overdueDays} day${if (lws.overdueDays == 1) "" else "s"}"
                    lws.daysUntilDue == 0L -> "Due today"
                    lws.daysUntilDue == 1L -> "Due tomorrow"
                    else -> "Due in ${lws.daysUntilDue} days"
                }
                DueReminderItemUi(
                    id = lws.loan.id,
                    itemKey = "loan_${lws.loan.id}",
                    title = lws.loan.name,
                    iconEmoji = lws.loan.iconEmoji,
                    amountCentavos = lws.loan.monthlyAmortizationCentavos,
                    amountFormatted = BudgetCalculator.formatPeso(lws.loan.monthlyAmortizationCentavos),
                    isLoan = true,
                    dueDateLabel = lws.dueDate.format(SHORT_DATE_FORMATTER),
                    dueDayOfMonth = lws.loan.dueDayOfMonth,
                    daysUntilDue = lws.daysUntilDue,
                    isOverdue = lws.isOverdue,
                    overdueDays = lws.overdueDays,
                    isPaid = lws.isPaidForCycle,
                    dueStatusText = dueStatus,
                    notify7d = lws.loan.notify7d,
                    notify3d = lws.loan.notify3d,
                    notify1d = lws.loan.notify1d,
                    notifyOnDue = lws.loan.notifyOnDue,
                    notifyDailyOverdue = lws.loan.notifyDailyOverdue,
                    dueYearMonth = lws.loan.nextDueYearMonth,
                    categoryLabel = "Loan"
                )
            }

        val sortedItems = (billItems + loanItems).sortedWith(
            compareBy(
                { it.isPaid },
                { !it.isOverdue },
                { it.daysUntilDue },
                { it.dueDayOfMonth },
                { it.title }
            )
        )

        return RemindersUiState(
            globalPushEnabled = pushEnabled,
            items = sortedItems
        )
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
