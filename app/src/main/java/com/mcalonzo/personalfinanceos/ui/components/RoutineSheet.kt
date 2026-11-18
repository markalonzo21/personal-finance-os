package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.RoutineUiState

private data class EnvelopeDraft(
    val id: Long,
    val name: String,
    val iconEmoji: String,
    val colorHex: String,
    val limitText: String,
    val isGasEnvelope: Boolean
)

private data class BillDraft(
    val id: Long,
    val name: String,
    val iconEmoji: String,
    val amountText: String,
    val dueDayText: String,
    val isActive: Boolean
)

private sealed interface RoutineIconTarget {
    data class Envelope(val index: Int) : RoutineIconTarget
    data class Bill(val index: Int) : RoutineIconTarget
}

/**
 * Editable Monthly Baseline Routine sheet controlling automatic 1st-of-month rollover templates,
 * with an explicit opt-in checkbox to apply changes to the active cycle immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineSheet(
    routineState: RoutineUiState,
    currentMonthLabel: String,
    onSaveRoutine: (
        totalMonthlyBudgetCentavos: Long,
        autoRolloverEnabled: Boolean,
        envelopeTemplates: List<RoutineEnvelopeTemplateEntity>,
        billTemplates: List<RoutineBillTemplateEntity>,
        applyToCurrentMonth: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var totalBudgetText by remember(routineState) {
        mutableStateOf(
            CurrencyFormatters.formatCentavosForInput(routineState.totalMonthlyBudgetCentavos)
                .ifEmpty { "0" }
        )
    }
    var autoRolloverEnabled by remember(routineState) {
        mutableStateOf(routineState.autoRolloverEnabled)
    }
    var applyToCurrentMonth by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var iconPickerTarget by remember { mutableStateOf<RoutineIconTarget?>(null) }

    val envelopeDrafts = remember(routineState) {
        mutableStateListOf<EnvelopeDraft>().apply {
            addAll(
                routineState.envelopeTemplates.map { item ->
                    EnvelopeDraft(
                        id = item.id,
                        name = item.name,
                        iconEmoji = item.iconEmoji,
                        colorHex = item.colorHex,
                        limitText = CurrencyFormatters.formatCentavosForInput(item.allocatedLimitCentavos),
                        isGasEnvelope = item.isGasEnvelope
                    )
                }
            )
        }
    }

    val billDrafts = remember(routineState) {
        mutableStateListOf<BillDraft>().apply {
            addAll(
                routineState.billTemplates.map { item ->
                    BillDraft(
                        id = item.id,
                        name = item.name,
                        iconEmoji = item.iconEmoji,
                        amountText = CurrencyFormatters.formatCentavosForInput(item.defaultAmountCentavos),
                        dueDayText = item.dueDayOfMonth.toString(),
                        isActive = item.isActive
                    )
                }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FinanceTheme.colors.bgCard,
        contentColor = FinanceTheme.colors.textPrimary
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Routine Template",
                    style = FinanceTypography.titleLarge,
                    color = FinanceTheme.colors.textPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close routine editor",
                        tint = FinanceTheme.colors.textSecondary
                    )
                }
            }

            Text(
                text = "Baseline envelopes and recurring bills auto-clone on the 1st of each month.",
                style = FinanceTypography.bodyMedium,
                color = FinanceTheme.colors.textSecondary
            )

            OutlinedTextField(
                value = totalBudgetText,
                onValueChange = { raw ->
                    totalBudgetText = CurrencyFormatters.sanitizeDecimalInput(raw)
                    validationError = null
                },
                label = { Text("Current Routine Total Budget (PHP)") },
                prefix = {
                    Text(
                        text = "₱",
                        style = NumericTextStyles.cardAmount,
                        color = FinanceTheme.colors.accentSky
                    )
                },
                textStyle = NumericTextStyles.cardAmount.copy(color = FinanceTheme.colors.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = routineFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            val envelopesSumCentavos = envelopeDrafts.sumOf {
                CurrencyFormatters.parsePesoToCentavos(it.limitText) ?: 0L
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Envelopes sum: ${BudgetCalculator.formatPeso(envelopesSumCentavos, showDecimal = false)}",
                    style = FinanceTypography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                TextButton(
                    onClick = {
                        totalBudgetText = CurrencyFormatters.formatCentavosForInput(envelopesSumCentavos)
                        validationError = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Use Envelopes Sum",
                        style = FinanceTypography.labelLarge,
                        color = FinanceTheme.colors.accentSky
                    )
                }
            }

            Surface(
                onClick = { autoRolloverEnabled = !autoRolloverEnabled },
                color = FinanceTheme.colors.bgCardSubtle,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Rollover on 1st",
                            style = FinanceTypography.titleMedium,
                            color = FinanceTheme.colors.textPrimary
                        )
                        Text(
                            text = "Automatically start new month cycles from this template",
                            style = FinanceTypography.bodyMedium,
                            color = FinanceTheme.colors.textSecondary
                        )
                    }
                    Switch(
                        checked = autoRolloverEnabled,
                        onCheckedChange = { autoRolloverEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = FinanceTheme.colors.bgBase,
                            checkedTrackColor = FinanceTheme.colors.accentEmerald
                        )
                    )
                }
            }

            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENVELOPE BASELINES",
                    style = FinanceTypography.labelMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                TextButton(
                    onClick = {
                        envelopeDrafts.add(
                            EnvelopeDraft(
                                id = 0L,
                                name = "",
                                iconEmoji = "📦",
                                colorHex = "#38BDF8",
                                limitText = "2000",
                                isGasEnvelope = false
                            )
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = FinanceTheme.colors.accentSky,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Envelope",
                        style = FinanceTypography.labelLarge,
                        color = FinanceTheme.colors.accentSky
                    )
                }
            }

            envelopeDrafts.forEachIndexed { index, draft ->
                Surface(
                    color = FinanceTheme.colors.bgCardSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { iconPickerTarget = RoutineIconTarget.Envelope(index) },
                                color = FinanceTheme.colors.bgCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = draft.iconEmoji, fontSize = 20.sp)
                                }
                            }

                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { value ->
                                    envelopeDrafts[index] = draft.copy(name = value)
                                    validationError = null
                                },
                                label = { Text("Name") },
                                singleLine = true,
                                colors = routineFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { envelopeDrafts.removeAt(index) },
                                enabled = envelopeDrafts.size > 1,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove envelope template",
                                    tint = if (envelopeDrafts.size > 1) {
                                        FinanceTheme.colors.accentRose
                                    } else {
                                        FinanceTheme.colors.textSecondary.copy(alpha = 0.4f)
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = draft.limitText,
                            onValueChange = { raw ->
                                envelopeDrafts[index] = draft.copy(
                                    limitText = CurrencyFormatters.sanitizeDecimalInput(raw)
                                )
                                validationError = null
                            },
                            label = { Text("Monthly Limit (PHP)") },
                            prefix = { Text("₱", style = NumericTextStyles.bodyAmount, color = FinanceTheme.colors.accentSky) },
                            textStyle = NumericTextStyles.bodyAmount.copy(color = FinanceTheme.colors.textPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = routineFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECURRING BILL TEMPLATES",
                    style = FinanceTypography.labelMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                TextButton(
                    onClick = {
                        billDrafts.add(
                            BillDraft(
                                id = 0L,
                                name = "",
                                iconEmoji = "🧾",
                                amountText = "1000",
                                dueDayText = "15",
                                isActive = true
                            )
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = FinanceTheme.colors.accentSky,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Bill",
                        style = FinanceTypography.labelLarge,
                        color = FinanceTheme.colors.accentSky
                    )
                }
            }

            billDrafts.forEachIndexed { index, draft ->
                Surface(
                    color = FinanceTheme.colors.bgCardSubtle,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { iconPickerTarget = RoutineIconTarget.Bill(index) },
                                color = FinanceTheme.colors.bgCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = draft.iconEmoji, fontSize = 20.sp)
                                }
                            }

                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { value ->
                                    billDrafts[index] = draft.copy(name = value)
                                    validationError = null
                                },
                                label = { Text("Bill Name") },
                                singleLine = true,
                                colors = routineFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { billDrafts.removeAt(index) },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove bill template",
                                    tint = FinanceTheme.colors.accentRose
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = draft.amountText,
                                onValueChange = { raw ->
                                    billDrafts[index] = draft.copy(
                                        amountText = CurrencyFormatters.sanitizeDecimalInput(raw)
                                    )
                                    validationError = null
                                },
                                label = { Text("Amount") },
                                prefix = { Text("₱", style = NumericTextStyles.bodyAmount, color = FinanceTheme.colors.accentSky) },
                                textStyle = NumericTextStyles.bodyAmount.copy(color = FinanceTheme.colors.textPrimary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = routineFieldColors(),
                                modifier = Modifier.weight(1.4f)
                            )

                            OutlinedTextField(
                                value = draft.dueDayText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    billDrafts[index] = draft.copy(dueDayText = digits)
                                    validationError = null
                                },
                                label = { Text("Due Day") },
                                textStyle = NumericTextStyles.bodyAmount.copy(color = FinanceTheme.colors.textPrimary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = routineFieldColors(),
                                modifier = Modifier.weight(0.9f)
                            )

                            Switch(
                                checked = draft.isActive,
                                onCheckedChange = { checked ->
                                    billDrafts[index] = draft.copy(isActive = checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = FinanceTheme.colors.bgBase,
                                    checkedTrackColor = FinanceTheme.colors.accentSky
                                )
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = { applyToCurrentMonth = !applyToCurrentMonth },
                color = FinanceTheme.colors.bgCardElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (applyToCurrentMonth) FinanceTheme.colors.accentSky else FinanceTheme.colors.borderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = applyToCurrentMonth,
                        onCheckedChange = { applyToCurrentMonth = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = FinanceTheme.colors.accentSky,
                            checkmarkColor = FinanceTheme.colors.bgBase
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Also apply to current month",
                            style = FinanceTypography.labelLarge,
                            color = FinanceTheme.colors.textPrimary
                        )
                        Text(
                            text = "Updates $currentMonthLabel total budget, limits & unpaid bills while preserving spend",
                            style = FinanceTypography.bodyMedium,
                            color = FinanceTheme.colors.textSecondary
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = FinanceTypography.bodyMedium,
                    color = FinanceTheme.colors.accentRose
                )
            }

            Button(
                onClick = {
                    val totalCentavos = CurrencyFormatters.parsePesoToCentavos(totalBudgetText)
                    if (totalCentavos == null || totalCentavos < 0L) {
                        validationError = "Monthly total budget must be ₱0 or greater."
                        return@Button
                    }

                    val validatedEnvelopes = mutableListOf<RoutineEnvelopeTemplateEntity>()
                    envelopeDrafts.forEachIndexed { idx, draft ->
                        val trimmedName = draft.name.trim()
                        val limit = CurrencyFormatters.parsePesoToCentavos(draft.limitText)
                        if (trimmedName.isEmpty()) {
                            validationError = "Envelope template #${idx + 1} needs a name."
                            return@Button
                        }
                        if (limit == null || limit <= 0L) {
                            validationError = "$trimmedName limit must be greater than ₱0."
                            return@Button
                        }
                        validatedEnvelopes += RoutineEnvelopeTemplateEntity(
                            id = draft.id,
                            name = trimmedName,
                            iconEmoji = draft.iconEmoji.ifBlank { "📦" },
                            colorHex = draft.colorHex,
                            allocatedLimitCentavos = limit,
                            isGasEnvelope = draft.isGasEnvelope,
                            sortOrder = idx
                        )
                    }

                    val validatedBills = mutableListOf<RoutineBillTemplateEntity>()
                    billDrafts.forEachIndexed { idx, draft ->
                        val trimmedName = draft.name.trim()
                        val amount = CurrencyFormatters.parsePesoToCentavos(draft.amountText)
                        val dueDay = draft.dueDayText.toIntOrNull()
                        if (trimmedName.isEmpty()) {
                            validationError = "Bill template #${idx + 1} needs a name."
                            return@Button
                        }
                        if (amount == null || amount <= 0L) {
                            validationError = "$trimmedName amount must be greater than ₱0."
                            return@Button
                        }
                        if (dueDay == null || dueDay !in 1..31) {
                            validationError = "$trimmedName due day must be between 1 and 31."
                            return@Button
                        }
                        validatedBills += RoutineBillTemplateEntity(
                            id = draft.id,
                            name = trimmedName,
                            iconEmoji = draft.iconEmoji.ifBlank { "🧾" },
                            defaultAmountCentavos = amount,
                            dueDayOfMonth = dueDay,
                            isActive = draft.isActive
                        )
                    }

                    onSaveRoutine(
                        totalCentavos,
                        autoRolloverEnabled,
                        validatedEnvelopes,
                        validatedBills,
                        applyToCurrentMonth
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinanceTheme.colors.accentSky,
                    contentColor = FinanceTheme.colors.bgBase
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = "Save Routine Template",
                    style = FinanceTypography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    val activeTarget = iconPickerTarget
    if (activeTarget != null) {
        val currentEmoji = when (activeTarget) {
            is RoutineIconTarget.Envelope -> envelopeDrafts.getOrNull(activeTarget.index)?.iconEmoji ?: "📦"
            is RoutineIconTarget.Bill -> billDrafts.getOrNull(activeTarget.index)?.iconEmoji ?: "🧾"
        }
        IconPickerSheet(
            title = "Choose Template Icon",
            currentIcon = currentEmoji,
            onSelectIcon = { chosen ->
                when (activeTarget) {
                    is RoutineIconTarget.Envelope -> {
                        if (activeTarget.index in envelopeDrafts.indices) {
                            envelopeDrafts[activeTarget.index] =
                                envelopeDrafts[activeTarget.index].copy(iconEmoji = chosen)
                        }
                    }
                    is RoutineIconTarget.Bill -> {
                        if (activeTarget.index in billDrafts.indices) {
                            billDrafts[activeTarget.index] =
                                billDrafts[activeTarget.index].copy(iconEmoji = chosen)
                        }
                    }
                }
            },
            onDismiss = { iconPickerTarget = null }
        )
    }
}

@Composable
private fun routineFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FinanceTheme.colors.accentSky,
    unfocusedBorderColor = FinanceTheme.colors.borderSubtle,
    focusedTextColor = FinanceTheme.colors.textPrimary,
    unfocusedTextColor = FinanceTheme.colors.textPrimary,
    focusedLabelColor = FinanceTheme.colors.accentSky,
    unfocusedLabelColor = FinanceTheme.colors.textSecondary
)
