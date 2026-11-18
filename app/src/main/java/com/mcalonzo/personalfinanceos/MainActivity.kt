package com.mcalonzo.personalfinanceos

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcalonzo.personalfinanceos.data.local.ThemeMode
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.data.ocr.OcrImageScanner
import com.mcalonzo.personalfinanceos.domain.model.ScanDocumentType
import com.mcalonzo.personalfinanceos.ui.screens.MainScreen
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ocrImageScanner: OcrImageScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            val context = LocalContext.current
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isDarkTheme = resolveDarkTheme(uiState.themeMode, isSystemInDarkTheme())

            LaunchedEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            PersonalFinanceTheme(darkTheme = isDarkTheme) {

                var pendingScanDocType by remember { mutableStateOf(ScanDocumentType.RECEIPT) }
                var pendingScanCard by remember { mutableStateOf<CardEntity?>(null) }
                var pendingScanAppend by remember { mutableStateOf(false) }
                var pendingCameraFile by remember { mutableStateOf<File?>(null) }

                val soaPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.attachSoaPdf(uri)
                    }
                }

                val scanImagePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.scanImageUri(
                            uri = uri,
                            documentType = pendingScanDocType,
                            card = pendingScanCard,
                            append = pendingScanAppend
                        )
                    }
                }

                val scanPdfPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.scanPdfUri(
                            uri = uri,
                            documentType = pendingScanDocType,
                            card = pendingScanCard,
                            append = pendingScanAppend
                        )
                    }
                }

                val takePhotoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    val file = pendingCameraFile
                    pendingCameraFile = null
                    if (success && file != null) {
                        viewModel.scanCameraFile(
                            file = file,
                            documentType = pendingScanDocType,
                            card = pendingScanCard,
                            append = pendingScanAppend
                        )
                    } else {
                        ocrImageScanner.deleteTempFileQuietly(file)
                    }
                }

                MainScreen(
                    state = uiState,
                    onSelectBottomTab = viewModel::switchBottomTab,
                    onSelectThemeMode = viewModel::selectThemeMode,
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
                    onLoadGasStationsForLocation = viewModel::loadGasStationsForLocation,
                    onUpdateGasWatcherConfig = viewModel::updateGasWatcherConfig,
                    onSavePms = viewModel::addPmsInterval,
                    onLogPmsService = viewModel::logPmsService,
                    onUpdatePms = viewModel::updatePmsInterval,
                    onDeletePms = viewModel::deletePmsInterval,
                    onScanUploadImage = { docType, card, append ->
                        pendingScanDocType = docType
                        pendingScanCard = card
                        pendingScanAppend = append
                        scanImagePicker.launch(arrayOf("image/*"))
                    },
                    onScanUploadPdf = { docType, card, append ->
                        pendingScanDocType = docType
                        pendingScanCard = card
                        pendingScanAppend = append
                        scanPdfPicker.launch(arrayOf("application/pdf"))
                    },
                    onScanTakePhoto = { docType, card, append ->
                        pendingScanDocType = docType
                        pendingScanCard = card
                        pendingScanAppend = append
                        val tempFile = ocrImageScanner.createTempCameraFile(context)
                        pendingCameraFile = tempFile
                        val uri = ocrImageScanner.getTempCameraUri(context, tempFile)
                        takePhotoLauncher.launch(uri)
                    },
                    onUpsertCard = viewModel::upsertCard,
                    onDeleteCard = viewModel::deleteCard,
                    onUpdateScanBatchStoreName = viewModel::updateScanBatchStoreName,
                    onApplyBatchDefaultEnvelope = viewModel::applyBatchDefaultEnvelope,
                    onUpdateScanDraft = viewModel::updateScanDraft,
                    onToggleScanDraft = viewModel::toggleScanDraft,
                    onClearScanBatch = viewModel::clearScanBatch,
                    onConfirmScannedExpenses = viewModel::confirmScannedExpenses
                )
            }
        }
    }
}

internal fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean {
    return when (mode) {
        ThemeMode.SYSTEM -> systemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
