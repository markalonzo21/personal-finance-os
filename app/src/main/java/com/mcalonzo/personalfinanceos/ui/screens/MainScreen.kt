package com.mcalonzo.personalfinanceos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.ui.components.AddEditBillSheet
import com.mcalonzo.personalfinanceos.ui.components.AddEditEnvelopeSheet
import com.mcalonzo.personalfinanceos.ui.components.AddEditLoanSheet
import com.mcalonzo.personalfinanceos.ui.components.AdvanceLoanPaymentDialog
import com.mcalonzo.personalfinanceos.ui.components.BillPaymentConfirmSheet
import com.mcalonzo.personalfinanceos.ui.components.IconPickerSheet
import com.mcalonzo.personalfinanceos.ui.components.LogExpenseSheet
import com.mcalonzo.personalfinanceos.ui.components.MonthPickerSheet
import com.mcalonzo.personalfinanceos.ui.components.PdfViewerSheet
import com.mcalonzo.personalfinanceos.ui.components.RoutineSheet
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.viewmodel.BillItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.BottomTab
import com.mcalonzo.personalfinanceos.ui.viewmodel.BudgetSubTab
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.LoanItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.MainUiState
import java.time.YearMonth

/**
 * Root OLED application shell hosting the active screen, elevated Log Expense FAB,
 * 4-item bottom navigation bar (Budget, Reminders, Moto Care, Routine trigger),
 * 5-second Undo Snackbar, and modal action sheets.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    modifier: Modifier = Modifier,
    onSelectBottomTab: (BottomTab) -> Unit = {},
    onSelectBudgetSubTab: (BudgetSubTab) -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    onOpenExpenseSheet: () -> Unit = {},
    onOpenRoutineSheet: () -> Unit = {},
    onLogExpense: (envelopeId: Long, amountCentavos: Long, title: String) -> Unit = { _, _, _ -> },
    onSettleBill: (billId: Long, overrideAmountCentavos: Long?) -> Unit = { _, _ -> },
    onSettleLoan: (loanId: Long, allowAdvancePayment: Boolean) -> Unit = { _, _ -> },
    onUndoLast: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
    onAddEnvelope: (name: String, iconEmoji: String, limitCentavos: Long, colorHex: String, addToRoutine: Boolean) -> Unit = { _, _, _, _, _ -> },
    onUpdateEnvelopeIcon: (envelopeId: Long, iconEmoji: String) -> Unit = { _, _ -> },
    onUpdateEnvelope: (envelopeId: Long, name: String, iconEmoji: String, limitCentavos: Long, colorHex: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteEnvelope: (envelopeId: Long) -> Unit = {},
    onAddBill: (name: String, iconEmoji: String, amountCentavos: Long, dueDayOfMonth: Int, addToRoutine: Boolean) -> Unit = { _, _, _, _, _ -> },
    onUpdateBill: (billId: Long, name: String, iconEmoji: String, amountCentavos: Long, dueDayOfMonth: Int) -> Unit = { _, _, _, _, _ -> },
    onDeleteBill: (billId: Long) -> Unit = {},
    onAddLoan: (
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdateLoan: (
        loanId: Long,
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteLoan: (loanId: Long) -> Unit = {},
    onDeleteTransaction: (transactionId: Long) -> Unit = {},
    onSaveRoutine: (
        totalMonthlyBudgetCentavos: Long,
        autoRolloverEnabled: Boolean,
        envelopeTemplates: List<RoutineEnvelopeTemplateEntity>,
        billTemplates: List<RoutineBillTemplateEntity>,
        applyToCurrentMonth: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    onPickSoaPdf: () -> Unit = {},
    onClearMessages: () -> Unit = {},
    onToggleGlobalPush: (Boolean) -> Unit = {},
    onPreviewDueAlert: () -> Unit = {},
    onUpdateReminderCadence: (
        itemKey: String,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateOdometer: (Int) -> Unit = {},
    onLogFullTank: () -> Unit = {},
    onRefreshGas: () -> Unit = {},
    onOverridePrice: (Long) -> Unit = {},
    onAddPms: () -> Unit = {},
    onSavePms: (taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int, costCentavos: Long) -> Unit = { _, _, _, _, _ -> },
    onLogPmsService: (id: Long, serviceKm: Int, costCentavos: Long) -> Unit = { _, _, _ -> },
    onUpdatePms: (id: Long, taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int) -> Unit = { _, _, _, _, _ -> },
    onDeletePms: (id: Long) -> Unit = {},
    motoContent: (@Composable () -> Unit)? = null,
    remindersContent: (@Composable () -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var showExpenseSheet by remember { mutableStateOf(false) }
    var expenseSheetInitialEnvelopeId by remember { mutableStateOf<Long?>(null) }
    var showRoutineSheet by remember { mutableStateOf(false) }
    var showSoaSheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    var iconPickerEnvelope by remember { mutableStateOf<EnvelopeItemUi?>(null) }
    var payingBill by remember { mutableStateOf<BillItemUi?>(null) }
    var showAddEditEnvelopeSheet by remember { mutableStateOf(false) }
    var editingEnvelope by remember { mutableStateOf<EnvelopeItemUi?>(null) }
    var showAddEditBillSheet by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<BillItemUi?>(null) }
    var showAddEditLoanSheet by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<LoanItemUi?>(null) }
    var advancingLoan by remember { mutableStateOf<LoanItemUi?>(null) }

    LaunchedEffect(state.activeUndo) {
        val undo = state.activeUndo
        if (undo == null) {
            snackbarHostState.currentSnackbarData?.dismiss()
        } else {
            val result = snackbarHostState.showSnackbar(
                message = "${undo.label} — Balance updated",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Indefinite
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onUndoLast()
                SnackbarResult.Dismissed -> onDismissUndo()
            }
        }
    }

    LaunchedEffect(state.errorMessage, state.infoMessage, state.activeUndo) {
        if (state.activeUndo != null) return@LaunchedEffect
        val message = state.errorMessage ?: state.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
        onClearMessages()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OLEDColors.bgBase,
        contentColor = OLEDColors.textPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val isError = state.errorMessage != null && state.activeUndo == null
                Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = OLEDColors.bgCardElevated,
                    contentColor = OLEDColors.textPrimary,
                    shape = RoundedCornerShape(14.dp),
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            TextButton(
                                onClick = { data.performAction() },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = FinanceTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = OLEDColors.accentSky
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        text = data.visuals.message,
                        style = FinanceTypography.bodyMedium,
                        color = if (isError) OLEDColors.accentRose else OLEDColors.textPrimary
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (state.selectedBottomTab == BottomTab.BUDGET) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(elevation = 10.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(OLEDColors.accentSky, Color(0xFF0EA5E9))
                            )
                        )
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                onOpenExpenseSheet()
                                expenseSheetInitialEnvelopeId = null
                                showExpenseSheet = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Log Expense",
                        tint = OLEDColors.bgBase,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = state.selectedBottomTab,
                overdueBillsCount = state.budgetState.overdueBillsCount,
                onSelectTab = onSelectBottomTab,
                onOpenRoutineSheet = {
                    onOpenRoutineSheet()
                    showRoutineSheet = true
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OLEDColors.bgBase)
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            when (state.selectedBottomTab) {
                BottomTab.BUDGET -> {
                    BudgetScreen(
                        state = state.budgetState,
                        onSelectSubTab = onSelectBudgetSubTab,
                        onOpenMonthPicker = { showMonthPicker = true },
                        onOpenSoaViewer = { showSoaSheet = true },
                        onTapEnvelopeIcon = { envelope -> iconPickerEnvelope = envelope },
                        onQuickLogEnvelope = { envelope ->
                            onOpenExpenseSheet()
                            expenseSheetInitialEnvelopeId = envelope.id
                            showExpenseSheet = true
                        },
                        onAddEnvelopeClick = {
                            editingEnvelope = null
                            showAddEditEnvelopeSheet = true
                        },
                        onEditEnvelopeClick = { envelope ->
                            editingEnvelope = envelope
                            showAddEditEnvelopeSheet = true
                        },
                        onAddBillClick = {
                            editingBill = null
                            showAddEditBillSheet = true
                        },
                        onPayBillClick = { bill -> payingBill = bill },
                        onEditBillClick = { bill ->
                            editingBill = bill
                            showAddEditBillSheet = true
                        },
                        onAddLoanClick = {
                            editingLoan = null
                            showAddEditLoanSheet = true
                        },
                        onSettleLoanClick = { loan -> onSettleLoan(loan.id, false) },
                        onAdvanceLoanClick = { loan -> advancingLoan = loan },
                        onEditLoanClick = { loan ->
                            editingLoan = loan
                            showAddEditLoanSheet = true
                        },
                        onDeleteTransactionClick = { tx -> onDeleteTransaction(tx.id) }
                    )
                }

                BottomTab.REMINDERS -> {
                    if (remindersContent != null) {
                        remindersContent()
                    } else {
                        RemindersScreen(
                            state = state.remindersState,
                            onToggleGlobalPush = onToggleGlobalPush,
                            onPreviewDueAlert = onPreviewDueAlert,
                            onSettleBill = { billId -> onSettleBill(billId, null) },
                            onSettleLoan = { loanId -> onSettleLoan(loanId, false) },
                            onUpdateCadence = onUpdateReminderCadence
                        )
                    }
                }

                BottomTab.MOTO -> {
                    if (motoContent != null) {
                        motoContent()
                    } else {
                        MotoCareScreen(
                            state = state.motoState,
                            availableEnvelopes = state.budgetState.envelopes,
                            onLogFullTank = onLogFullTank,
                            onRefreshGas = onRefreshGas,
                            onEditOdometer = onUpdateOdometer,
                            onOverridePrice = onOverridePrice,
                            onAddPms = onAddPms,
                            onSavePms = onSavePms,
                            onLogPmsService = onLogPmsService,
                            onUpdatePms = onUpdatePms,
                            onDeletePms = onDeletePms
                        )
                    }
                }
            }
        }
    }

    if (showExpenseSheet) {
        LogExpenseSheet(
            envelopes = state.budgetState.envelopes,
            initialEnvelopeId = expenseSheetInitialEnvelopeId,
            onConfirmExpense = onLogExpense,
            onDismiss = { showExpenseSheet = false }
        )
    }

    if (showRoutineSheet) {
        RoutineSheet(
            routineState = state.routineState,
            currentMonthLabel = state.budgetState.yearMonthLabel.ifBlank { "Current Month" },
            onSaveRoutine = onSaveRoutine,
            onDismiss = { showRoutineSheet = false }
        )
    }

    if (showSoaSheet) {
        PdfViewerSheet(
            localPath = state.budgetState.soaPdfLocalUri,
            fileName = state.budgetState.soaFileName,
            onUploadOrReplaceClick = onPickSoaPdf,
            onDismiss = { showSoaSheet = false }
        )
    }

    if (showMonthPicker) {
        MonthPickerSheet(
            selectedYearMonth = state.budgetState.selectedYearMonth,
            cycles = state.budgetState.availableCycles,
            onSelectMonth = onSelectMonth,
            onDismiss = { showMonthPicker = false }
        )
    }

    iconPickerEnvelope?.let { targetEnvelope ->
        IconPickerSheet(
            title = "Choose ${targetEnvelope.name} Icon",
            currentIcon = targetEnvelope.iconEmoji,
            onSelectIcon = { emoji -> onUpdateEnvelopeIcon(targetEnvelope.id, emoji) },
            onDismiss = { iconPickerEnvelope = null }
        )
    }

    payingBill?.let { bill ->
        BillPaymentConfirmSheet(
            bill = bill,
            onConfirmPayment = onSettleBill,
            onDismiss = { payingBill = null }
        )
    }

    if (showAddEditEnvelopeSheet) {
        AddEditEnvelopeSheet(
            editingEnvelope = editingEnvelope,
            onSaveNew = onAddEnvelope,
            onSaveEdit = onUpdateEnvelope,
            onDelete = onDeleteEnvelope,
            onDismiss = {
                showAddEditEnvelopeSheet = false
                editingEnvelope = null
            }
        )
    }

    if (showAddEditBillSheet) {
        AddEditBillSheet(
            editingBill = editingBill,
            onSaveNew = onAddBill,
            onSaveEdit = onUpdateBill,
            onDelete = onDeleteBill,
            onDismiss = {
                showAddEditBillSheet = false
                editingBill = null
            }
        )
    }

    if (showAddEditLoanSheet) {
        val defaultMonth = state.budgetState.selectedYearMonth.ifBlank { YearMonth.now().toString() }
        AddEditLoanSheet(
            editingLoan = editingLoan,
            defaultNextDueYearMonth = defaultMonth,
            onSaveNew = onAddLoan,
            onSaveEdit = onUpdateLoan,
            onDelete = onDeleteLoan,
            onDismiss = {
                showAddEditLoanSheet = false
                editingLoan = null
            }
        )
    }

    advancingLoan?.let { loan ->
        AdvanceLoanPaymentDialog(
            loan = loan,
            onConfirmAdvance = { onSettleLoan(loan.id, true) },
            onDismiss = { advancingLoan = null }
        )
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: BottomTab,
    overdueBillsCount: Int,
    onSelectTab: (BottomTab) -> Unit,
    onOpenRoutineSheet: () -> Unit
) {
    Surface(
        color = OLEDColors.bgCard,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = OLEDColors.borderSubtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomGlyphNavItem(
                    glyph = "₱",
                    label = "Budget",
                    selected = selectedTab == BottomTab.BUDGET,
                    onClick = { onSelectTab(BottomTab.BUDGET) },
                    modifier = Modifier.weight(1f)
                )
                BottomIconNavItem(
                    icon = Icons.Rounded.NotificationsActive,
                    label = "Reminders",
                    selected = selectedTab == BottomTab.REMINDERS,
                    showAlertDot = overdueBillsCount > 0,
                    onClick = { onSelectTab(BottomTab.REMINDERS) },
                    modifier = Modifier.weight(1f)
                )
                BottomIconNavItem(
                    icon = Icons.Rounded.TwoWheeler,
                    label = "Moto Care",
                    selected = selectedTab == BottomTab.MOTO,
                    showAlertDot = false,
                    onClick = { onSelectTab(BottomTab.MOTO) },
                    modifier = Modifier.weight(1f)
                )
                BottomIconNavItem(
                    icon = Icons.Rounded.EventRepeat,
                    label = "Routine",
                    selected = false,
                    showAlertDot = false,
                    onClick = onOpenRoutineSheet,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomGlyphNavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) OLEDColors.accentSky else OLEDColors.textSecondary
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = glyph,
            style = NumericTextStyles.sectionAmount.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = contentColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = FinanceTypography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomIconNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showAlertDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) OLEDColors.accentSky else OLEDColors.textSecondary
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(21.dp)
            )
            if (showAlertDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(OLEDColors.accentRose)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = FinanceTypography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabPlaceholderScreen(
    badge: String,
    title: String,
    subtitle: String,
    metricLabel: String,
    metricValue: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = OLEDColors.bgCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, OLEDColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = badge,
                    style = FinanceTypography.labelSmall,
                    color = OLEDColors.accentSky
                )
                Text(
                    text = title,
                    style = FinanceTypography.headlineMedium,
                    color = OLEDColors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = OLEDColors.bgCardSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = metricLabel,
                            style = FinanceTypography.labelSmall,
                            color = OLEDColors.textSecondary
                        )
                        Text(
                            text = metricValue,
                            style = NumericTextStyles.cardAmount,
                            color = OLEDColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
