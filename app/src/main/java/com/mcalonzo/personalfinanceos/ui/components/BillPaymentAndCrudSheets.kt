package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.viewmodel.BillItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.CycleOptionUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.LoanItemUi
import java.time.YearMonth

/**
 * Confirms or overrides payment amount for a recurring or variable utility bill (e.g., Meralco).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillPaymentConfirmSheet(
    bill: BillItemUi,
    onConfirmPayment: (billId: Long, overrideAmountCentavos: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember(bill.id) {
        mutableStateOf(CurrencyFormatters.formatCentavosForInput(bill.amountCentavos))
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
            SheetHeader(
                title = "Confirm Bill Payment",
                onDismiss = onDismiss
            )

            Surface(
                color = OLEDColors.bgCardSubtle,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (bill.isOverdue) OLEDColors.accentRose.copy(alpha = 0.45f) else OLEDColors.borderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = bill.iconEmoji, fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bill.name,
                            style = FinanceTypography.titleMedium,
                            color = OLEDColors.textPrimary
                        )
                        val statusText = if (bill.isOverdue) {
                            val unit = if (bill.overdueDays == 1) "day" else "days"
                            "Due ${bill.dueDateLabel} • Overdue by ${bill.overdueDays} $unit"
                        } else {
                            "Due ${bill.dueDateLabel}"
                        }
                        Text(
                            text = statusText,
                            style = FinanceTypography.bodyMedium,
                            color = if (bill.isOverdue) OLEDColors.accentRose else OLEDColors.textSecondary
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    amountText = CurrencyFormatters.sanitizeDecimalInput(raw)
                    validationError = null
                },
                label = { Text("Payment Amount (editable for variable bills)") },
                prefix = {
                    Text(
                        text = "₱",
                        style = NumericTextStyles.sectionAmount,
                        color = OLEDColors.accentSky
                    )
                },
                textStyle = NumericTextStyles.sectionAmount.copy(color = OLEDColors.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = sheetFieldColors(),
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
                    val centavos = CurrencyFormatters.parsePesoToCentavos(amountText)
                    if (centavos == null || centavos <= 0L) {
                        validationError = "Enter a payment amount greater than ₱0."
                    } else {
                        val override = if (centavos != bill.amountCentavos) centavos else null
                        onConfirmPayment(bill.id, override)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentEmerald,
                    contentColor = OLEDColors.bgBase
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                val preview = CurrencyFormatters.parsePesoToCentavos(amountText)
                    ?.let { " (${BudgetCalculator.formatPeso(it)})" }
                    ?: ""
                Text(
                    text = "Confirm Payment$preview",
                    style = FinanceTypography.titleMedium
                )
            }
        }
    }
}

/**
 * Add or Edit/Delete modal sheet for a budget Envelope category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEnvelopeSheet(
    editingEnvelope: EnvelopeItemUi?,
    onSaveNew: (name: String, iconEmoji: String, limitCentavos: Long, colorHex: String, addToRoutine: Boolean) -> Unit,
    onSaveEdit: (envelopeId: Long, name: String, iconEmoji: String, limitCentavos: Long, colorHex: String) -> Unit,
    onDelete: (envelopeId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = editingEnvelope != null

    var name by remember(editingEnvelope) { mutableStateOf(editingEnvelope?.name ?: "") }
    var limitText by remember(editingEnvelope) {
        mutableStateOf(
            editingEnvelope?.let { CurrencyFormatters.formatCentavosForInput(it.allocatedLimitCentavos) }
                ?: ""
        )
    }
    var iconEmoji by remember(editingEnvelope) { mutableStateOf(editingEnvelope?.iconEmoji ?: "🏷️") }
    var colorHex by remember(editingEnvelope) { mutableStateOf(editingEnvelope?.colorHex ?: "#38BDF8") }
    var addToRoutine by remember(editingEnvelope) { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
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
            SheetHeader(
                title = if (isEditing) "Edit Envelope" else "+ Add New Category",
                onDismiss = onDismiss
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showIconPicker = true },
                    color = OLEDColors.bgCardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = iconEmoji, fontSize = 24.sp)
                    }
                }

                OutlinedButton(
                    onClick = { showIconPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Change Icon",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentSky
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Category Name") },
                placeholder = { Text("e.g. Health & Fitness") },
                singleLine = true,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = limitText,
                onValueChange = { raw ->
                    limitText = CurrencyFormatters.sanitizeDecimalInput(raw)
                    validationError = null
                },
                label = { Text("Monthly Limit (PHP)") },
                placeholder = { Text("5000") },
                prefix = {
                    Text(
                        text = "₱",
                        style = NumericTextStyles.cardAmount,
                        color = OLEDColors.accentSky
                    )
                },
                textStyle = NumericTextStyles.cardAmount.copy(color = OLEDColors.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "ACCENT COLOR",
                style = FinanceTypography.labelMedium,
                color = OLEDColors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CurrencyFormatters.ENVELOPE_COLOR_PALETTE.forEach { (hex, label) ->
                    val swatchColor = CurrencyFormatters.parseColorHex(hex)
                    val selected = hex.equals(colorHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(swatchColor.copy(alpha = 0.22f))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) swatchColor else OLEDColors.borderSubtle,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "$label selected",
                                tint = swatchColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                            )
                        }
                    }
                }
            }

            if (!isEditing) {
                Surface(
                    onClick = { addToRoutine = !addToRoutine },
                    color = OLEDColors.bgCardSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addToRoutine,
                            onCheckedChange = { addToRoutine = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = OLEDColors.accentSky,
                                checkmarkColor = OLEDColors.bgBase
                            )
                        )
                        Text(
                            text = "Also save to Monthly Routine template",
                            style = FinanceTypography.bodyMedium,
                            color = OLEDColors.textPrimary
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.accentRose
                )
            }

            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val limitCentavos = CurrencyFormatters.parsePesoToCentavos(limitText)
                    when {
                        trimmedName.isEmpty() -> validationError = "Category name cannot be blank."
                        limitCentavos == null || limitCentavos <= 0L ->
                            validationError = "Monthly limit must be greater than ₱0."
                        else -> {
                            if (editingEnvelope != null) {
                                onSaveEdit(editingEnvelope.id, trimmedName, iconEmoji, limitCentavos, colorHex)
                            } else {
                                onSaveNew(trimmedName, iconEmoji, limitCentavos, colorHex, addToRoutine)
                            }
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
                    text = if (isEditing) "Save Envelope" else "Add Envelope",
                    style = FinanceTypography.titleMedium
                )
            }

            if (editingEnvelope != null) {
                OutlinedButton(
                    onClick = {
                        onDelete(editingEnvelope.id)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, OLEDColors.accentRose.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = OLEDColors.accentRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete Envelope",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentRose
                    )
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerSheet(
            title = "Choose Envelope Icon",
            currentIcon = iconEmoji,
            onSelectIcon = { iconEmoji = it },
            onDismiss = { showIconPicker = false }
        )
    }
}

/**
 * Add or Edit/Delete modal sheet for a recurring or one-off Bill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBillSheet(
    editingBill: BillItemUi?,
    onSaveNew: (name: String, iconEmoji: String, amountCentavos: Long, dueDayOfMonth: Int, addToRoutine: Boolean) -> Unit,
    onSaveEdit: (billId: Long, name: String, iconEmoji: String, amountCentavos: Long, dueDayOfMonth: Int) -> Unit,
    onDelete: (billId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = editingBill != null

    var name by remember(editingBill) { mutableStateOf(editingBill?.name ?: "") }
    var amountText by remember(editingBill) {
        mutableStateOf(
            editingBill?.let { CurrencyFormatters.formatCentavosForInput(it.amountCentavos) } ?: ""
        )
    }
    var dueDayText by remember(editingBill) {
        mutableStateOf(editingBill?.dueDayOfMonth?.toString() ?: "15")
    }
    var iconEmoji by remember(editingBill) { mutableStateOf(editingBill?.iconEmoji ?: "🧾") }
    var addToRoutine by remember(editingBill) { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
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
            SheetHeader(
                title = if (isEditing) "Edit Bill" else "+ Add Bill",
                onDismiss = onDismiss
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showIconPicker = true },
                    color = OLEDColors.bgCardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = iconEmoji, fontSize = 24.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showIconPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Change Icon",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentSky
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Bill Name") },
                placeholder = { Text("e.g. Water District") },
                singleLine = true,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = CurrencyFormatters.sanitizeDecimalInput(raw)
                        validationError = null
                    },
                    label = { Text("Amount (PHP)") },
                    prefix = { Text("₱", style = NumericTextStyles.cardAmount, color = OLEDColors.accentSky) },
                    textStyle = NumericTextStyles.cardAmount.copy(color = OLEDColors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1.4f)
                )

                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { raw ->
                        dueDayText = raw.filter { it.isDigit() }.take(2)
                        validationError = null
                    },
                    label = { Text("Due Day (1-31)") },
                    textStyle = NumericTextStyles.cardAmount.copy(color = OLEDColors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            if (!isEditing) {
                Surface(
                    onClick = { addToRoutine = !addToRoutine },
                    color = OLEDColors.bgCardSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addToRoutine,
                            onCheckedChange = { addToRoutine = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = OLEDColors.accentSky,
                                checkmarkColor = OLEDColors.bgBase
                            )
                        )
                        Text(
                            text = "Repeat monthly in Baseline Routine",
                            style = FinanceTypography.bodyMedium,
                            color = OLEDColors.textPrimary
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.accentRose
                )
            }

            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val centavos = CurrencyFormatters.parsePesoToCentavos(amountText)
                    val dueDay = dueDayText.toIntOrNull()
                    when {
                        trimmedName.isEmpty() -> validationError = "Bill name cannot be blank."
                        centavos == null || centavos <= 0L ->
                            validationError = "Bill amount must be greater than ₱0."
                        dueDay == null || dueDay !in 1..31 ->
                            validationError = "Due day must be between 1 and 31."
                        else -> {
                            if (editingBill != null) {
                                onSaveEdit(editingBill.id, trimmedName, iconEmoji, centavos, dueDay)
                            } else {
                                onSaveNew(trimmedName, iconEmoji, centavos, dueDay, addToRoutine)
                            }
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
                    text = if (isEditing) "Save Bill" else "Add Bill",
                    style = FinanceTypography.titleMedium
                )
            }

            if (editingBill != null) {
                OutlinedButton(
                    onClick = {
                        onDelete(editingBill.id)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, OLEDColors.accentRose.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = OLEDColors.accentRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete Bill",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentRose
                    )
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerSheet(
            title = "Choose Bill Icon",
            currentIcon = iconEmoji,
            onSelectIcon = { iconEmoji = it },
            onDismiss = { showIconPicker = false }
        )
    }
}

/**
 * Add or Edit/Delete modal sheet for an active installment Loan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLoanSheet(
    editingLoan: LoanItemUi?,
    defaultNextDueYearMonth: String,
    onSaveNew: (
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) -> Unit,
    onSaveEdit: (
        loanId: Long,
        name: String,
        iconEmoji: String,
        monthlyAmortizationCentavos: Long,
        totalInstallments: Int,
        paidInstallments: Int,
        dueDayOfMonth: Int,
        nextDueYearMonth: YearMonth
    ) -> Unit,
    onDelete: (loanId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = editingLoan != null

    var name by remember(editingLoan) { mutableStateOf(editingLoan?.name ?: "") }
    var amortizationText by remember(editingLoan) {
        mutableStateOf(
            editingLoan?.let { CurrencyFormatters.formatCentavosForInput(it.monthlyAmortizationCentavos) }
                ?: ""
        )
    }
    var totalInstallmentsText by remember(editingLoan) {
        mutableStateOf(editingLoan?.totalInstallments?.toString() ?: "12")
    }
    var paidInstallmentsText by remember(editingLoan) {
        mutableStateOf(editingLoan?.paidInstallments?.toString() ?: "0")
    }
    var dueDayText by remember(editingLoan) {
        mutableStateOf(editingLoan?.dueDayOfMonth?.toString() ?: "20")
    }
    var nextDueMonthText by remember(editingLoan, defaultNextDueYearMonth) {
        mutableStateOf(editingLoan?.nextDueYearMonth ?: defaultNextDueYearMonth)
    }
    var iconEmoji by remember(editingLoan) { mutableStateOf(editingLoan?.iconEmoji ?: "🏦") }
    var showIconPicker by remember { mutableStateOf(false) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(
                title = if (isEditing) "Edit Loan" else "+ Add Loan",
                onDismiss = onDismiss
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showIconPicker = true },
                    color = OLEDColors.bgCardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = iconEmoji, fontSize = 24.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showIconPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Change Icon",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentSky
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Loan Name") },
                placeholder = { Text("e.g. Honda Click 160 Loan") },
                singleLine = true,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amortizationText,
                onValueChange = { raw ->
                    amortizationText = CurrencyFormatters.sanitizeDecimalInput(raw)
                    validationError = null
                },
                label = { Text("Monthly Amortization (PHP)") },
                prefix = { Text("₱", style = NumericTextStyles.cardAmount, color = OLEDColors.accentSky) },
                textStyle = NumericTextStyles.cardAmount.copy(color = OLEDColors.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = paidInstallmentsText,
                    onValueChange = { raw ->
                        paidInstallmentsText = raw.filter { it.isDigit() }.take(3)
                        validationError = null
                    },
                    label = { Text("Paid Count") },
                    textStyle = NumericTextStyles.bodyAmount.copy(color = OLEDColors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = totalInstallmentsText,
                    onValueChange = { raw ->
                        totalInstallmentsText = raw.filter { it.isDigit() }.take(3)
                        validationError = null
                    },
                    label = { Text("Total Months") },
                    textStyle = NumericTextStyles.bodyAmount.copy(color = OLEDColors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { raw ->
                        dueDayText = raw.filter { it.isDigit() }.take(2)
                        validationError = null
                    },
                    label = { Text("Due Day (1-31)") },
                    textStyle = NumericTextStyles.bodyAmount.copy(color = OLEDColors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = nextDueMonthText,
                    onValueChange = { raw ->
                        nextDueMonthText = raw.take(7)
                        validationError = null
                    },
                    label = { Text("Next Due (YYYY-MM)") },
                    placeholder = { Text("2026-11") },
                    textStyle = NumericTextStyles.bodyAmount.copy(color = OLEDColors.textPrimary),
                    singleLine = true,
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1.3f)
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.accentRose
                )
            }

            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val amortization = CurrencyFormatters.parsePesoToCentavos(amortizationText)
                    val totalInst = totalInstallmentsText.toIntOrNull()
                    val paidInst = paidInstallmentsText.toIntOrNull()
                    val dueDay = dueDayText.toIntOrNull()
                    val nextDueYm = runCatching { YearMonth.parse(nextDueMonthText.trim()) }.getOrNull()

                    when {
                        trimmedName.isEmpty() -> validationError = "Loan name cannot be blank."
                        amortization == null || amortization <= 0L ->
                            validationError = "Monthly amortization must be greater than ₱0."
                        totalInst == null || totalInst <= 0 ->
                            validationError = "Total installments must be at least 1."
                        paidInst == null || paidInst !in 0..totalInst ->
                            validationError = "Paid installments must be between 0 and $totalInst."
                        dueDay == null || dueDay !in 1..31 ->
                            validationError = "Due day must be between 1 and 31."
                        nextDueYm == null ->
                            validationError = "Next due month must use YYYY-MM format (e.g. 2026-11)."
                        else -> {
                            if (editingLoan != null) {
                                onSaveEdit(
                                    editingLoan.id,
                                    trimmedName,
                                    iconEmoji,
                                    amortization,
                                    totalInst,
                                    paidInst,
                                    dueDay,
                                    nextDueYm
                                )
                            } else {
                                onSaveNew(
                                    trimmedName,
                                    iconEmoji,
                                    amortization,
                                    totalInst,
                                    paidInst,
                                    dueDay,
                                    nextDueYm
                                )
                            }
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
                    text = if (isEditing) "Save Loan" else "Add Loan",
                    style = FinanceTypography.titleMedium
                )
            }

            if (editingLoan != null) {
                OutlinedButton(
                    onClick = {
                        onDelete(editingLoan.id)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, OLEDColors.accentRose.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = OLEDColors.accentRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete Loan",
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.accentRose
                    )
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerSheet(
            title = "Choose Loan Icon",
            currentIcon = iconEmoji,
            onSelectIcon = { iconEmoji = it },
            onDismiss = { showIconPicker = false }
        )
    }
}

/**
 * Confirmation prompt when paying an advance loan installment beyond the currently selected cycle.
 */
@Composable
fun AdvanceLoanPaymentDialog(
    loan: LoanItemUi,
    onConfirmAdvance: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OLEDColors.bgCard,
        titleContentColor = OLEDColors.textPrimary,
        textContentColor = OLEDColors.textSecondary,
        title = {
            Text(
                text = "Confirm Advance Payment",
                style = FinanceTypography.titleLarge
            )
        },
        text = {
            Text(
                text = "${loan.name} is already settled for this month. Pay the ${loan.nextDueYearMonth} installment (${BudgetCalculator.formatPeso(loan.monthlyAmortizationCentavos)}) in advance?",
                style = FinanceTypography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmAdvance()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentAmber,
                    contentColor = OLEDColors.bgBase
                ),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Pay Advance", style = FinanceTypography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel", color = OLEDColors.textSecondary, style = FinanceTypography.labelLarge)
            }
        }
    )
}

/**
 * Cycle switcher modal bottom sheet supporting active, past, and next month cycles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    selectedYearMonth: String,
    cycles: List<CycleOptionUi>,
    onSelectMonth: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetHeader(
                title = "Select Budget Cycle",
                onDismiss = onDismiss
            )

            cycles.forEach { option ->
                val isSelected = option.yearMonth.toString() == selectedYearMonth
                Surface(
                    onClick = {
                        onSelectMonth(option.yearMonth)
                        onDismiss()
                    },
                    color = if (isSelected) {
                        OLEDColors.accentSky.copy(alpha = 0.14f)
                    } else {
                        OLEDColors.bgCardSubtle
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) OLEDColors.accentSky else OLEDColors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = if (isSelected) OLEDColors.accentSky else OLEDColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = option.label,
                                style = FinanceTypography.titleMedium,
                                color = OLEDColors.textPrimary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (option.hasSoa) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = "SOA attached",
                                    tint = OLEDColors.accentSky,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Current cycle",
                                    tint = OLEDColors.accentSky,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
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
                contentDescription = "Close sheet",
                tint = OLEDColors.textSecondary
            )
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OLEDColors.accentSky,
    unfocusedBorderColor = OLEDColors.borderSubtle,
    focusedTextColor = OLEDColors.textPrimary,
    unfocusedTextColor = OLEDColors.textPrimary,
    focusedLabelColor = OLEDColors.accentSky,
    unfocusedLabelColor = OLEDColors.textSecondary
)
