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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi

private val QUICK_INCREMENT_CHIPS: List<Pair<String, Long>> = listOf(
    "+₱50" to 5_000L,
    "+₱100" to 10_000L,
    "+₱250" to 25_000L,
    "+₱500" to 50_000L,
    "+₱1,000" to 100_000L
)

/**
 * Quick expense logger modal bottom sheet supporting typed peso decimals, quick-increment
 * chips, optional merchant/title note, and 2-column envelope selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogExpenseSheet(
    envelopes: List<EnvelopeItemUi>,
    initialEnvelopeId: Long? = null,
    onConfirmExpense: (envelopeId: Long, amountCentavos: Long, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var selectedEnvelopeId by remember(envelopes, initialEnvelopeId) {
        mutableStateOf(
            initialEnvelopeId?.takeIf { id -> envelopes.any { it.id == id } }
                ?: envelopes.firstOrNull()?.id
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedCentavos = CurrencyFormatters.parsePesoToCentavos(amountText) ?: 0L

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+ Log Daily Expense",
                    style = FinanceTypography.titleLarge,
                    color = OLEDColors.textPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close expense logger",
                        tint = OLEDColors.textSecondary
                    )
                }
            }

            Surface(
                color = OLEDColors.bgCardSubtle,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AMOUNT (PHP)",
                        style = FinanceTypography.labelMedium,
                        color = OLEDColors.textSecondary
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { raw ->
                            amountText = CurrencyFormatters.sanitizeDecimalInput(raw)
                            validationError = null
                        },
                        prefix = {
                            Text(
                                text = "₱",
                                style = NumericTextStyles.sectionAmount,
                                color = OLEDColors.accentSky
                            )
                        },
                        placeholder = {
                            Text(
                                text = "0.00",
                                style = NumericTextStyles.sectionAmount,
                                color = OLEDColors.textSecondary
                            )
                        },
                        textStyle = NumericTextStyles.sectionAmount.copy(color = OLEDColors.accentSky),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OLEDColors.accentSky,
                            unfocusedBorderColor = OLEDColors.borderSubtle,
                            focusedTextColor = OLEDColors.accentSky,
                            unfocusedTextColor = OLEDColors.accentSky
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_INCREMENT_CHIPS.forEach { (label, deltaCentavos) ->
                    Surface(
                        onClick = {
                            val current = CurrencyFormatters.parsePesoToCentavos(amountText) ?: 0L
                            amountText = CurrencyFormatters.formatCentavosForInput(current + deltaCentavos)
                            validationError = null
                        },
                        color = OLEDColors.bgCardElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = NumericTextStyles.bodyAmount,
                                color = OLEDColors.textPrimary
                            )
                        }
                    }
                }
                Surface(
                    onClick = {
                        amountText = ""
                        validationError = null
                    },
                    color = OLEDColors.accentRose.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OLEDColors.accentRose.copy(alpha = 0.35f)),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reset",
                            style = FinanceTypography.labelLarge,
                            color = OLEDColors.accentRose
                        )
                    }
                }
            }

            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Note / Merchant (optional)") },
                placeholder = { Text("e.g. Robinsons Supermarket") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OLEDColors.accentSky,
                    unfocusedBorderColor = OLEDColors.borderSubtle,
                    focusedTextColor = OLEDColors.textPrimary,
                    unfocusedTextColor = OLEDColors.textPrimary,
                    focusedLabelColor = OLEDColors.accentSky,
                    unfocusedLabelColor = OLEDColors.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "SELECT CATEGORY",
                style = FinanceTypography.labelMedium,
                color = OLEDColors.textSecondary
            )

            if (envelopes.isEmpty()) {
                Text(
                    text = "Add a budget envelope first before logging expenses.",
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.accentAmber
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    envelopes.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { env ->
                                val isSelected = env.id == selectedEnvelopeId
                                Surface(
                                    onClick = {
                                        selectedEnvelopeId = env.id
                                        validationError = null
                                    },
                                    color = if (isSelected) {
                                        OLEDColors.accentSky.copy(alpha = 0.16f)
                                    } else {
                                        OLEDColors.bgCardElevated
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            OLEDColors.accentSky
                                        } else {
                                            OLEDColors.borderSubtle
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 56.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = env.iconEmoji, fontSize = 18.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = env.name,
                                                style = FinanceTypography.labelLarge,
                                                color = OLEDColors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${BudgetCalculator.formatPeso(env.remainingCentavos, showDecimal = false)} left",
                                                style = NumericTextStyles.captionAmount,
                                                color = OLEDColors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
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
                    val targetEnvId = selectedEnvelopeId
                    when {
                        parsedCentavos <= 0L -> validationError = "Enter an expense amount greater than ₱0."
                        targetEnvId == null -> validationError = "Select an envelope category."
                        else -> {
                            onConfirmExpense(targetEnvId, parsedCentavos, titleText.trim())
                            onDismiss()
                        }
                    }
                },
                enabled = envelopes.isNotEmpty(),
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
                val ctaSuffix = if (parsedCentavos > 0L) {
                    " (${BudgetCalculator.formatPeso(parsedCentavos)})"
                } else {
                    ""
                }
                Text(
                    text = "Confirm & Deduct$ctaSuffix",
                    style = FinanceTypography.titleMedium
                )
            }
        }
    }
}
