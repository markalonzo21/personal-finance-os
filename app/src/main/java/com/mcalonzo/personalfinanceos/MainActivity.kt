package com.mcalonzo.personalfinanceos

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcalonzo.personalfinanceos.ui.screens.MainScreen
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            PersonalFinanceTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val soaPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.attachSoaPdf(uri)
                    }
                }

                MainScreen(
                    state = uiState,
                    onSelectBottomTab = viewModel::switchBottomTab,
                    onSelectBudgetSubTab = viewModel::switchBudgetSubTab,
                    onSelectMonth = viewModel::selectMonth,
                    onLogExpense = viewModel::logExpense,
                    onSettleBill = viewModel::settleBill,
                    onSettleLoan = viewModel::settleLoan,
                    onUndoLast = viewModel::undoLast,
                    onDismissUndo = viewModel::dismissUndo,
                    onAddEnvelope = viewModel::addEnvelope,
                    onUpdateEnvelopeIcon = viewModel::updateEnvelopeIcon,
                    onUpdateEnvelope = viewModel::updateEnvelope,
                    onDeleteEnvelope = viewModel::deleteEnvelope,
                    onAddBill = viewModel::addBill,
                    onUpdateBill = viewModel::updateBill,
                    onDeleteBill = viewModel::deleteBill,
                    onAddLoan = viewModel::addLoan,
                    onUpdateLoan = viewModel::updateLoan,
                    onDeleteLoan = viewModel::deleteLoan,
                    onToggleAmountsVisibility = viewModel::toggleAmountsVisibility,
                    onUpdateTransaction = viewModel::updateTransaction,
                    onDeleteTransaction = viewModel::deleteTransaction,
                    onSaveRoutine = viewModel::saveRoutine,
                    onPickSoaPdf = { soaPdfPicker.launch(arrayOf("application/pdf")) },
                    onClearMessages = viewModel::clearMessages,
                    onToggleGlobalPush = viewModel::toggleGlobalPush,
                    onPreviewDueAlert = { viewModel.previewDueNotification() },
                    onUpdateReminderCadence = viewModel::updateReminderCadence,
                    onUpdateOdometer = viewModel::updateOdometer,
                    onLogFullTank = viewModel::logClick160FullTank,
                    onRefreshGas = viewModel::triggerManualGasSync,
                    onOverridePrice = viewModel::overrideGasPrice,
                    onSavePms = viewModel::addPmsInterval,
                    onLogPmsService = viewModel::logPmsService,
                    onUpdatePms = viewModel::updatePmsInterval,
                    onDeletePms = viewModel::deletePmsInterval
                )
            }
        }
    }
}
