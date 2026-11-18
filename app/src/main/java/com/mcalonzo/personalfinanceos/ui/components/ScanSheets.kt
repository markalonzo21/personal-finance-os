package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.domain.model.ScanDocumentType
import com.mcalonzo.personalfinanceos.domain.model.ScannedExpenseDraft
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.ScanBatchUiState

private val CARD_TYPE_OPTIONS: List<Pair<String, String>> = listOf(
    "CREDIT" to "Credit",
    "DEBIT" to "Debit",
    "EWALLET" to "E-Wallet"
)

/**
 * Modal bottom sheet for choosing document type (Receipt vs Card / Statement),
 * selecting or adding a saved card when in Card mode, and launching image/PDF/camera capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSourceSheet(
    cards: List<CardEntity>,
    initialDocumentType: ScanDocumentType = ScanDocumentType.RECEIPT,
    initialSelectedCard: CardEntity? = null,
    onUploadImage: (ScanDocumentType, CardEntity?) -> Unit,
    onUploadPdf: (ScanDocumentType, CardEntity?) -> Unit,
    onTakePhoto: (ScanDocumentType, CardEntity?) -> Unit,
    onAddCardClick: () -> Unit,
    onEditCardClick: (CardEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var documentType by remember(initialDocumentType) { mutableStateOf(initialDocumentType) }
    var selectedCardId by remember(cards, initialSelectedCard) {
        mutableStateOf(
            initialSelectedCard?.id?.takeIf { id -> cards.any { it.id == id } }
                ?: cards.firstOrNull()?.id
        )
    }
    val selectedCard = cards.firstOrNull { it.id == selectedCardId }
    val effectiveCard = if (documentType == ScanDocumentType.CARD) selectedCard else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OLEDColors.bgCard,
        contentColor = OLEDColors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScanSheetHeader(
                title = "Scan Receipt / Screenshot",
                closeDescription = "Close scan source",
                onDismiss = onDismiss
            )

            Text(
                text = "DOCUMENT TYPE",
                style = FinanceTypography.labelMedium,
                color = OLEDColors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SelectableModeChip(
                    label = "Receipt",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    selected = documentType == ScanDocumentType.RECEIPT,
                    onClick = { documentType = ScanDocumentType.RECEIPT },
                    modifier = Modifier.weight(1f)
                )
                SelectableModeChip(
                    label = "Card / Statement",
                    icon = Icons.Rounded.CreditCard,
                    selected = documentType == ScanDocumentType.CARD,
                    onClick = {
                        documentType = ScanDocumentType.CARD
                        if (selectedCardId == null) {
                            selectedCardId = cards.firstOrNull()?.id
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (documentType == ScanDocumentType.CARD) {
                Text(
                    text = "SELECT CARD",
                    style = FinanceTypography.labelMedium,
                    color = OLEDColors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cards.forEach { card ->
                        val isSelected = card.id == selectedCardId
                        Surface(
                            onClick = { selectedCardId = card.id },
                            color = if (isSelected) {
                                OLEDColors.accentSky.copy(alpha = 0.16f)
                            } else {
                                OLEDColors.bgCardElevated
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) OLEDColors.accentSky else OLEDColors.borderSubtle
                            ),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = card.name,
                                    style = FinanceTypography.labelLarge,
                                    color = if (isSelected) OLEDColors.accentSky else OLEDColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (card.billingCycleDay != null) {
                                    Text(
                                        text = "D${card.billingCycleDay}",
                                        style = NumericTextStyles.captionAmount,
                                        color = OLEDColors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onAddCardClick,
                        color = OLEDColors.bgCardSubtle,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OLEDColors.accentSky.copy(alpha = 0.5f)),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ Add Card",
                                style = FinanceTypography.labelLarge,
                                color = OLEDColors.accentSky
                            )
                        }
                    }
                }
                if (selectedCard != null && onEditCardClick != {}) {
                    TextButton(
                        onClick = { onEditCardClick(selectedCard) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Edit ${selectedCard.name}",
                            style = FinanceTypography.labelMedium,
                            color = OLEDColors.textSecondary
                        )
                    }
                }
            }

            Text(
                text = "SOURCE",
                style = FinanceTypography.labelMedium,
                color = OLEDColors.textSecondary
            )

            SourceActionButton(
                label = "Upload Image / Screenshot",
                icon = Icons.Rounded.Image,
                accentColor = OLEDColors.accentSky,
                onClick = { onUploadImage(documentType, effectiveCard) }
            )
            SourceActionButton(
                label = "Upload PDF",
                icon = Icons.Rounded.PictureAsPdf,
                accentColor = OLEDColors.accentEmerald,
                onClick = { onUploadPdf(documentType, effectiveCard) }
            )
            SourceActionButton(
                label = "Take Photo",
                icon = Icons.Rounded.CameraAlt,
                accentColor = OLEDColors.accentAmber,
                onClick = { onTakePhoto(documentType, effectiveCard) }
            )
        }
    }
}

/**
 * Modal bottom sheet for creating or editing a saved payment card and its optional billing cycle day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCardSheet(
    editingCard: CardEntity? = null,
    onSave: (id: Long, name: String, cardType: String, billingCycleDay: Int?) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameText by remember(editingCard) { mutableStateOf(editingCard?.name.orEmpty()) }
    var selectedType by remember(editingCard) { mutableStateOf(editingCard?.cardType ?: "CREDIT") }
    var cycleDayText by remember(editingCard) {
        mutableStateOf(editingCard?.billingCycleDay?.toString().orEmpty())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OLEDColors.bgCard,
        contentColor = OLEDColors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScanSheetHeader(
                title = if (editingCard == null) "Add Card" else "Edit Card",
                closeDescription = "Close card editor",
                onDismiss = onDismiss
            )

            OutlinedTextField(
                value = nameText,
                onValueChange = {
                    nameText = it
                    validationError = null
                },
                label = { Text("Card Name") },
                placeholder = { Text("e.g. BPI Amore") },
                singleLine = true,
                colors = scanTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "CARD TYPE",
                style = FinanceTypography.labelMedium,
                color = OLEDColors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CARD_TYPE_OPTIONS.forEach { (typeKey, typeLabel) ->
                    val isSelected = selectedType == typeKey
                    Surface(
                        onClick = { selectedType = typeKey },
                        color = if (isSelected) {
                            OLEDColors.accentSky.copy(alpha = 0.16f)
                        } else {
                            OLEDColors.bgCardElevated
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) OLEDColors.accentSky else OLEDColors.borderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = typeLabel,
                                style = FinanceTypography.labelLarge,
                                color = if (isSelected) OLEDColors.accentSky else OLEDColors.textPrimary
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = cycleDayText,
                onValueChange = { raw ->
                    cycleDayText = raw.filter { it.isDigit() }.take(2)
                    validationError = null
                },
                label = { Text("Billing Cycle Day (1-31, optional)") },
                placeholder = { Text("e.g. 15") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = scanTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.accentRose
                )
            }

            Button(
                onClick = {
                    val trimmedName = nameText.trim()
                    val trimmedCycle = cycleDayText.trim()
                    val parsedCycle = if (trimmedCycle.isEmpty()) null else trimmedCycle.toIntOrNull()
                    when {
                        trimmedName.isEmpty() -> validationError = "Enter a card name."
                        trimmedCycle.isNotEmpty() && (parsedCycle == null || parsedCycle !in 1..31) -> {
                            validationError = "Billing cycle day must be between 1 and 31."
                        }
                        else -> {
                            onSave(editingCard?.id ?: 0L, trimmedName, selectedType, parsedCycle)
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = "Save Card",
                    style = FinanceTypography.titleMedium
                )
            }

            if (editingCard != null && onDelete != null) {
                TextButton(
                    onClick = {
                        onDelete(editingCard.id)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = OLEDColors.accentRose
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Delete Card",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentRose
                    )
                }
            }
        }
    }
}

/**
 * Modal bottom sheet for reviewing, editing, toggling, and confirming OCR-parsed expense drafts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewSheet(
    scanBatchState: ScanBatchUiState,
    envelopes: List<EnvelopeItemUi>,
    onUpdateStoreName: (String) -> Unit,
    onApplyDefaultEnvelope: (String) -> Unit,
    onUpdateDraft: (localId: String, title: String, amountCentavos: Long, envelopeName: String?) -> Unit,
    onToggleDraft: (localId: String) -> Unit,
    onScanAnotherPart: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val selectedCount = scanBatchState.drafts.count { it.isSelected && it.amountCentavos > 0L }

    ModalBottomSheet(
        onDismissRequest = { showDiscardConfirm = true },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = OLEDColors.bgCard,
        contentColor = OLEDColors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScanSheetHeader(
                title = "Review Scanned Expenses",
                closeDescription = "Close scan review",
                onDismiss = { showDiscardConfirm = true }
            )

            if (scanBatchState.isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = OLEDColors.accentSky,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Scanning document...",
                        style = FinanceTypography.bodyMedium,
                        color = OLEDColors.textSecondary
                    )
                }
            }

            OutlinedTextField(
                value = scanBatchState.storeName,
                onValueChange = onUpdateStoreName,
                label = { Text("Store / Statement") },
                singleLine = true,
                colors = scanTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (envelopes.isNotEmpty()) {
                Text(
                    text = "APPLY ENVELOPE TO ALL",
                    style = FinanceTypography.labelMedium,
                    color = OLEDColors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    envelopes.forEach { env ->
                        val isDefault = scanBatchState.defaultEnvelopeName == env.name
                        Surface(
                            onClick = { onApplyDefaultEnvelope(env.name) },
                            color = if (isDefault) {
                                OLEDColors.accentSky.copy(alpha = 0.16f)
                            } else {
                                OLEDColors.bgCardElevated
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isDefault) 1.5.dp else 1.dp,
                                color = if (isDefault) OLEDColors.accentSky else OLEDColors.borderSubtle
                            ),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = env.iconEmoji)
                                Text(
                                    text = env.name,
                                    style = FinanceTypography.labelLarge,
                                    color = if (isDefault) OLEDColors.accentSky else OLEDColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (scanBatchState.drafts.isEmpty() && !scanBatchState.isScanning) {
                Text(
                    text = "No expense items detected. Try scanning another part or clearer photo.",
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.accentAmber
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    scanBatchState.drafts.forEach { draft ->
                        ScanDraftCard(
                            draft = draft,
                            envelopes = envelopes,
                            onToggle = { onToggleDraft(draft.localId) },
                            onUpdate = { title, amountCentavos, envelopeName ->
                                onUpdateDraft(draft.localId, title, amountCentavos, envelopeName)
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onScanAnotherPart,
                border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = OLEDColors.accentSky
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "+ Scan Another Part",
                    style = FinanceTypography.labelLarge,
                    color = OLEDColors.accentSky
                )
            }

            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0 && !scanBatchState.isScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = "Log $selectedCount Expense${if (selectedCount == 1) "" else "s"}",
                    style = FinanceTypography.titleMedium
                )
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = OLEDColors.bgCard,
            titleContentColor = OLEDColors.textPrimary,
            textContentColor = OLEDColors.textSecondary,
            title = {
                Text(
                    text = "Discard Scanned Items?",
                    style = FinanceTypography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Closing will discard this scan batch without logging any expenses.",
                    style = FinanceTypography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Discard",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentRose
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Keep Editing",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentSky
                    )
                }
            }
        )
    }
}

@Composable
private fun ScanDraftCard(
    draft: ScannedExpenseDraft,
    envelopes: List<EnvelopeItemUi>,
    onToggle: () -> Unit,
    onUpdate: (title: String, amountCentavos: Long, envelopeName: String?) -> Unit
) {
    var amountText by remember(draft.localId) {
        mutableStateOf(CurrencyFormatters.formatCentavosForInput(draft.amountCentavos))
    }

    Surface(
        color = OLEDColors.bgCardSubtle,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (draft.isSelected) OLEDColors.borderSubtle else OLEDColors.borderSubtle.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = draft.isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = OLEDColors.accentSky,
                        uncheckedColor = OLEDColors.textSecondary,
                        checkmarkColor = OLEDColors.bgBase
                    ),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { newTitle ->
                            onUpdate(newTitle, draft.amountCentavos, draft.envelopeName)
                        },
                        singleLine = true,
                        colors = scanTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { raw ->
                            val sanitized = CurrencyFormatters.sanitizeDecimalInput(raw)
                            amountText = sanitized
                            val parsed = CurrencyFormatters.parsePesoToCentavos(sanitized)
                            if (parsed != null) {
                                onUpdate(draft.title, parsed, draft.envelopeName)
                            }
                        },
                        prefix = {
                            Text(
                                text = "₱",
                                style = NumericTextStyles.bodyAmount,
                                color = OLEDColors.accentSky
                            )
                        },
                        textStyle = NumericTextStyles.bodyAmount.copy(color = OLEDColors.accentSky),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = scanTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DraftBadge(
                    text = "${draft.dateLabel.ifBlank { "Today" }} • ${draft.targetYearMonth}",
                    color = OLEDColors.textSecondary
                )
                if (draft.isDuplicate) {
                    DraftBadge(text = "Duplicate", color = OLEDColors.accentAmber)
                }
                if (draft.isCredit) {
                    DraftBadge(text = "Credit", color = OLEDColors.accentEmerald)
                }
                if (envelopes.isNotEmpty()) {
                    val currentIndex = envelopes.indexOfFirst { it.name == draft.envelopeName }
                    Surface(
                        onClick = {
                            val nextEnv = envelopes[(currentIndex + 1) % envelopes.size]
                            onUpdate(draft.title, draft.amountCentavos, nextEnv.name)
                        },
                        color = OLEDColors.bgCardElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = draft.envelopeName ?: "Assign Envelope",
                                style = FinanceTypography.labelMedium,
                                color = OLEDColors.accentSky
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = FinanceTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ScanSheetHeader(
    title: String,
    closeDescription: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = FinanceTypography.titleLarge,
            color = OLEDColors.textPrimary
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = closeDescription,
                tint = OLEDColors.textSecondary
            )
        }
    }
}

@Composable
private fun SelectableModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) OLEDColors.accentSky.copy(alpha = 0.16f) else OLEDColors.bgCardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) OLEDColors.accentSky else OLEDColors.borderSubtle
        ),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) OLEDColors.accentSky else OLEDColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = FinanceTypography.labelLarge,
                color = if (selected) OLEDColors.accentSky else OLEDColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceActionButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = OLEDColors.bgCardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = FinanceTypography.titleSmall,
                color = OLEDColors.textPrimary
            )
        }
    }
}

@Composable
private fun scanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OLEDColors.accentSky,
    unfocusedBorderColor = OLEDColors.borderSubtle,
    focusedTextColor = OLEDColors.textPrimary,
    unfocusedTextColor = OLEDColors.textPrimary,
    focusedLabelColor = OLEDColors.accentSky,
    unfocusedLabelColor = OLEDColors.textSecondary
)
