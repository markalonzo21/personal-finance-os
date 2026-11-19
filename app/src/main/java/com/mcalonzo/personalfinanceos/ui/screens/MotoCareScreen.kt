package com.mcalonzo.personalfinanceos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.ui.components.CurrencyFormatters
import com.mcalonzo.personalfinanceos.ui.components.GasWatcherConfigDialog
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.MotoUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.PmsItemUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoCareScreen(
    state: MotoUiState,
    modifier: Modifier = Modifier,
    availableEnvelopes: List<EnvelopeItemUi> = emptyList(),
    onLogFullTank: () -> Unit = {},
    onRefreshGas: () -> Unit = {},
    onEditOdometer: (Int) -> Unit = {},
    onOverridePrice: (Long) -> Unit = {},
    onLoadGasStationsForLocation: (String) -> Unit = {},
    onUpdateGasWatcherConfig: (
        locationSlug: String,
        locationDisplayName: String,
        stationId: Int,
        stationDisplayName: String,
        fuelKey: String,
        fuelDisplayName: String
    ) -> Unit = { _, _, _, _, _, _ -> },
    onAddPms: () -> Unit = {},
    onSavePms: (taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int, costCentavos: Long) -> Unit = { _, _, _, _, _ -> },
    onSavePmsWithEnvelope: (taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int, costCentavos: Long, envelopeId: Long?) -> Unit = { taskName, icon, interval, lastKm, cost, _ ->
        onSavePms(taskName, icon, interval, lastKm, cost)
    },
    onLogPmsService: (id: Long, serviceKm: Int, costCentavos: Long) -> Unit = { _, _, _ -> },
    onLogPmsServiceWithEnvelope: (id: Long, serviceKm: Int, costCentavos: Long, envelopeId: Long?) -> Unit = { id, km, cost, _ ->
        onLogPmsService(id, km, cost)
    },
    onUpdatePms: (id: Long, taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int) -> Unit = { _, _, _, _, _ -> },
    onDeletePms: (id: Long) -> Unit = {}
) {
    var showOdometerDialog by remember { mutableStateOf(false) }
    var showOverridePriceDialog by remember { mutableStateOf(false) }
    var showGasWatcherConfigDialog by remember { mutableStateOf(false) }
    var showAddPmsDialog by remember { mutableStateOf(false) }
    var editingPmsItem by remember { mutableStateOf<PmsItemUi?>(null) }
    var servicingPmsItem by remember { mutableStateOf<PmsItemUi?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.bgBase)
    ) {
        PullToRefreshBox(
            isRefreshing = state.isSyncingGas,
            onRefresh = onRefreshGas,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "screen_header") {
                    Text(
                        text = "Moto Care",
                        style = FinanceTypography.headlineMedium,
                        color = FinanceTheme.colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                item(key = "gas_price_card") {
                    GasPriceWatcherCard(
                        state = state,
                        onTapPrice = { showOverridePriceDialog = true },
                        onConfigureWatcher = {
                            showGasWatcherConfigDialog = true
                            onLoadGasStationsForLocation(state.locationSlug)
                        }
                    )
                }

                item(key = "moto_pms_card") {
                    MotoAndPmsCard(
                        state = state,
                        onEditOdometerClick = { showOdometerDialog = true },
                        onAddPmsClick = {
                            editingPmsItem = null
                            showAddPmsDialog = true
                            onAddPms()
                        },
                        onEditPmsClick = { editingPmsItem = it },
                        onLogServiceClick = { servicingPmsItem = it }
                    )
                }
            }
        }
    }

    if (showOdometerDialog) {
        EditOdometerDialog(
            currentOdometerKm = state.odometerKm,
            onDismiss = { showOdometerDialog = false },
            onConfirm = { newKm ->
                onEditOdometer(newKm)
                showOdometerDialog = false
            }
        )
    }

    if (showOverridePriceDialog) {
        OverrideGasPriceDialog(
            stationDisplayName = state.stationDisplayName,
            fuelType = state.fuelType,
            currentPriceCentavos = state.pricePerLiterCentavos,
            onDismiss = { showOverridePriceDialog = false },
            onConfirm = { newCentavos ->
                onOverridePrice(newCentavos)
                showOverridePriceDialog = false
            }
        )
    }

    if (showGasWatcherConfigDialog) {
        GasWatcherConfigDialog(
            currentState = state,
            pickerState = state.watcherPickerState,
            onLocationSelected = onLoadGasStationsForLocation,
            onDismiss = { showGasWatcherConfigDialog = false },
            onConfirm = { locSlug, locName, stationId, stationName, fuelKey, fuelName ->
                onUpdateGasWatcherConfig(locSlug, locName, stationId, stationName, fuelKey, fuelName)
                showGasWatcherConfigDialog = false
            }
        )
    }

    if (showAddPmsDialog) {
        AddEditPmsDialog(
            editingItem = null,
            currentOdometerKm = state.odometerKm,
            availableEnvelopes = availableEnvelopes,
            onDismiss = { showAddPmsDialog = false },
            onSaveNew = { name, emoji, intervalKm, lastServiceKm, costCentavos, envelopeId ->
                onSavePmsWithEnvelope(name, emoji, intervalKm, lastServiceKm, costCentavos, envelopeId)
                showAddPmsDialog = false
            },
            onUpdateExisting = { _, _, _, _, _ -> },
            onDelete = {}
        )
    }

    editingPmsItem?.let { pms ->
        AddEditPmsDialog(
            editingItem = pms,
            currentOdometerKm = state.odometerKm,
            availableEnvelopes = availableEnvelopes,
            onDismiss = { editingPmsItem = null },
            onSaveNew = { _, _, _, _, _, _ -> },
            onUpdateExisting = { id, name, emoji, intervalKm, lastServiceKm ->
                onUpdatePms(id, name, emoji, intervalKm, lastServiceKm)
                editingPmsItem = null
            },
            onDelete = { id ->
                onDeletePms(id)
                editingPmsItem = null
            }
        )
    }

    servicingPmsItem?.let { pms ->
        LogPmsServiceDialog(
            item = pms,
            currentOdometerKm = state.odometerKm,
            availableEnvelopes = availableEnvelopes,
            onDismiss = { servicingPmsItem = null },
            onConfirm = { serviceKm, costCentavos, envelopeId ->
                onLogPmsServiceWithEnvelope(pms.id, serviceKm, costCentavos, envelopeId)
                servicingPmsItem = null
            }
        )
    }
}

@Composable
private fun GasPriceWatcherCard(
    state: MotoUiState,
    onTapPrice: () -> Unit,
    onConfigureWatcher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trendColor = when {
        state.isPriceUp -> FinanceTheme.colors.accentRose
        state.isPriceDown -> FinanceTheme.colors.accentEmerald
        else -> FinanceTheme.colors.textSecondary
    }

    val trendBg = when {
        state.isPriceUp -> FinanceTheme.colors.accentRose.copy(alpha = 0.15f)
        state.isPriceDown -> FinanceTheme.colors.accentEmerald.copy(alpha = 0.15f)
        else -> FinanceTheme.colors.bgCardSubtle
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FinanceTheme.colors.bgCard),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(FinanceTheme.colors.borderSubtle)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onConfigureWatcher),
                verticalArrangement = Arrangement.Center
            ) {
                if (state.locationDisplayName.isNotBlank()) {
                    Text(
                        text = state.locationDisplayName.uppercase(),
                        style = FinanceTypography.labelMedium,
                        color = FinanceTheme.colors.textSecondary,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.stationDisplayName,
                        style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = FinanceTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Configure Gas Watcher",
                        tint = FinanceTheme.colors.accentSky,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onTapPrice),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.pricePerLiterFormatted,
                        style = NumericTextStyles.heroBalance.copy(fontSize = 34.sp),
                        color = FinanceTheme.colors.accentSky
                    )
                    Text(
                        text = "/L",
                        style = FinanceTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FinanceTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(trendBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = state.trendBadgeText,
                        style = FinanceTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = trendColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.fuelType,
                    style = FinanceTypography.labelLarge,
                    color = FinanceTheme.colors.textPrimary
                )
                if (state.prevPriceFormatted.isNotEmpty()) {
                    Text(
                        text = state.prevPriceFormatted,
                        style = FinanceTypography.labelMedium,
                        color = FinanceTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MotoAndPmsCard(
    state: MotoUiState,
    onEditOdometerClick: () -> Unit,
    onAddPmsClick: () -> Unit,
    onEditPmsClick: (PmsItemUi) -> Unit,
    onLogServiceClick: (PmsItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FinanceTheme.colors.bgCard),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(FinanceTheme.colors.borderSubtle)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.modelName,
                        style = FinanceTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = FinanceTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Full tank (${state.tankCapacityLiters}L) · ${state.fullTankCostFormatted}",
                        style = FinanceTypography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary
                    )
                }
                OutlinedButton(
                    onClick = onEditOdometerClick,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FinanceTheme.colors.textPrimary
                    )
                ) {
                    Text(
                        text = String.format("%,d km", state.odometerKm),
                        style = NumericTextStyles.bodyAmount,
                        color = FinanceTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Odometer",
                        tint = FinanceTheme.colors.accentSky,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SERVICE INTERVALS",
                    style = FinanceTypography.labelMedium,
                    color = FinanceTheme.colors.textSecondary,
                    letterSpacing = 0.8.sp
                )
                TextButton(
                    onClick = onAddPmsClick,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "+ Log PMS",
                        style = FinanceTypography.labelLarge,
                        color = FinanceTheme.colors.accentSky
                    )
                }
            }

            if (state.pmsItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No PMS schedules configured",
                        style = FinanceTypography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary
                    )
                }
            } else {
                state.pmsItems.forEach { pms ->
                    HorizontalDivider(color = FinanceTheme.colors.borderSubtle)
                    PmsRow(
                        item = pms,
                        onEditClick = { onEditPmsClick(pms) },
                        onLogServiceClick = { onLogServiceClick(pms) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PmsRow(
    item: PmsItemUi,
    onEditClick: () -> Unit,
    onLogServiceClick: () -> Unit
) {
    val statusColor = if (item.isOverdue) {
        FinanceTheme.colors.accentRose
    } else {
        FinanceTheme.colors.accentEmerald
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onEditClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.taskName,
                style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = FinanceTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${String.format("%,d", item.intervalKm)} km interval · ",
                    style = FinanceTypography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                Text(
                    text = item.dueStatusText,
                    style = FinanceTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onLogServiceClick,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = FinanceTheme.colors.accentSky
            )
        ) {
            Text("Log", style = FinanceTypography.labelLarge)
        }
    }
}

@Composable
private fun motoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FinanceTheme.colors.accentSky,
    unfocusedBorderColor = FinanceTheme.colors.borderSubtle,
    focusedTextColor = FinanceTheme.colors.textPrimary,
    unfocusedTextColor = FinanceTheme.colors.textPrimary,
    focusedLabelColor = FinanceTheme.colors.accentSky,
    unfocusedLabelColor = FinanceTheme.colors.textSecondary
)

@Composable
private fun EditOdometerDialog(
    currentOdometerKm: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var rawKm by remember { mutableStateOf(currentOdometerKm.toString()) }
    val parsedKm = rawKm.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FinanceTheme.colors.bgCard,
        title = {
            Text(
                "Update Odometer",
                style = FinanceTypography.titleLarge,
                color = FinanceTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Enter the latest odometer reading for Honda Click 160 in kilometers.",
                    style = FinanceTypography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawKm,
                    onValueChange = { rawKm = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = motoTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedKm != null && parsedKm >= 0) {
                        onConfirm(parsedKm)
                    }
                },
                enabled = parsedKm != null && parsedKm >= 0,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinanceTheme.colors.accentSky,
                    contentColor = FinanceTheme.colors.bgBase
                )
            ) {
                Text("Save", style = FinanceTypography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel", color = FinanceTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
private fun OverrideGasPriceDialog(
    stationDisplayName: String,
    fuelType: String,
    currentPriceCentavos: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var rawInput by remember {
        mutableStateOf(CurrencyFormatters.formatCentavosForInput(currentPriceCentavos))
    }
    val parsedCentavos = CurrencyFormatters.parsePesoToCentavos(rawInput)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FinanceTheme.colors.bgCard,
        title = {
            Text(
                "Override Fuel Price",
                style = FinanceTypography.titleLarge,
                color = FinanceTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Set manual price per liter for $stationDisplayName ($fuelType).",
                    style = FinanceTypography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = CurrencyFormatters.sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Price per Liter (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = motoTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedCentavos != null && parsedCentavos > 0L) {
                        onConfirm(parsedCentavos)
                    }
                },
                enabled = parsedCentavos != null && parsedCentavos > 0L,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinanceTheme.colors.accentSky,
                    contentColor = FinanceTheme.colors.bgBase
                )
            ) {
                Text("Save", style = FinanceTypography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel", color = FinanceTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
private fun EnvelopeSelectorChips(
    availableEnvelopes: List<EnvelopeItemUi>,
    selectedEnvId: Long?,
    onSelectEnvelope: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableEnvelopes.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Charge Expense To:",
            style = FinanceTypography.labelMedium,
            color = FinanceTheme.colors.accentSky
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableEnvelopes.take(3).forEach { env ->
                val isSelected = env.id == selectedEnvId
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) FinanceTheme.colors.accentSky.copy(alpha = 0.2f) else FinanceTheme.colors.bgCardSubtle)
                        .border(
                            1.dp,
                            if (isSelected) FinanceTheme.colors.accentSky else FinanceTheme.colors.borderSubtle,
                            RoundedCornerShape(8.dp)
                        )
                        .semantics(mergeDescendants = true) {}
                        .clickable(role = Role.Button) { onSelectEnvelope(env.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${env.iconEmoji} ${env.name}",
                        style = FinanceTypography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) FinanceTheme.colors.accentSky else FinanceTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LogPmsServiceDialog(
    item: PmsItemUi,
    currentOdometerKm: Int,
    availableEnvelopes: List<EnvelopeItemUi> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (serviceKm: Int, costCentavos: Long, envelopeId: Long?) -> Unit
) {
    var rawKm by remember { mutableStateOf(currentOdometerKm.toString()) }
    var rawCost by remember { mutableStateOf("") }
    val defaultEnvId = remember(availableEnvelopes) {
        availableEnvelopes.firstOrNull { it.isGasEnvelope }?.id
            ?: availableEnvelopes.firstOrNull { it.name.contains("Personal", ignoreCase = true) }?.id
            ?: availableEnvelopes.firstOrNull()?.id
    }
    var selectedEnvId by remember { mutableStateOf(defaultEnvId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FinanceTheme.colors.bgCard,
        title = {
            Text(
                "Log PMS Service",
                style = FinanceTypography.titleLarge,
                color = FinanceTheme.colors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${item.iconEmoji} ${item.taskName}",
                    style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = FinanceTheme.colors.textPrimary
                )
                Text(
                    text = "Update last service odometer and optionally charge maintenance cost to an envelope.",
                    style = FinanceTypography.bodySmall,
                    color = FinanceTheme.colors.textSecondary
                )
                OutlinedTextField(
                    value = rawKm,
                    onValueChange = { rawKm = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Service Odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = motoTextFieldColors()
                )
                OutlinedTextField(
                    value = rawCost,
                    onValueChange = { rawCost = CurrencyFormatters.sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional Cost (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = motoTextFieldColors()
                )

                val parsedCost = CurrencyFormatters.parsePesoToCentavos(rawCost) ?: 0L
                if (parsedCost > 0L) {
                    EnvelopeSelectorChips(
                        availableEnvelopes = availableEnvelopes,
                        selectedEnvId = selectedEnvId,
                        onSelectEnvelope = { selectedEnvId = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val km = rawKm.toIntOrNull() ?: currentOdometerKm
                    val cost = CurrencyFormatters.parsePesoToCentavos(rawCost) ?: 0L
                    onConfirm(km, cost, if (cost > 0L) selectedEnvId else null)
                },
                enabled = rawKm.toIntOrNull() != null,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinanceTheme.colors.accentSky,
                    contentColor = FinanceTheme.colors.bgBase
                )
            ) {
                Text("Confirm Service", style = FinanceTypography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel", color = FinanceTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
private fun AddEditPmsDialog(
    editingItem: PmsItemUi?,
    currentOdometerKm: Int,
    availableEnvelopes: List<EnvelopeItemUi> = emptyList(),
    onDismiss: () -> Unit,
    onSaveNew: (taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int, costCentavos: Long, envelopeId: Long?) -> Unit,
    onUpdateExisting: (id: Long, taskName: String, iconEmoji: String, intervalKm: Int, lastServiceKm: Int) -> Unit,
    onDelete: (id: Long) -> Unit
) {
    val isEditing = editingItem != null
    var taskName by remember(editingItem) { mutableStateOf(editingItem?.taskName ?: "") }
    var iconEmoji by remember(editingItem) { mutableStateOf(editingItem?.iconEmoji ?: "🔧") }
    var intervalKm by remember(editingItem) { mutableStateOf(editingItem?.intervalKm?.toString() ?: "3000") }
    var lastServiceKm by remember(editingItem) {
        mutableStateOf(editingItem?.lastServiceOdometerKm?.toString() ?: currentOdometerKm.toString())
    }
    var rawCost by remember { mutableStateOf("") }
    val defaultEnvId = remember(availableEnvelopes) {
        availableEnvelopes.firstOrNull { it.isGasEnvelope }?.id ?: availableEnvelopes.firstOrNull()?.id
    }
    var selectedEnvId by remember { mutableStateOf(defaultEnvId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FinanceTheme.colors.bgCard,
        title = {
            Text(
                if (isEditing) "Edit PMS Schedule" else "Add PMS Schedule",
                style = FinanceTypography.titleLarge,
                color = FinanceTheme.colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task Name") },
                    placeholder = { Text("e.g. Brake Fluid Flush") },
                    singleLine = true,
                    colors = motoTextFieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = iconEmoji,
                        onValueChange = { iconEmoji = it.take(2) },
                        modifier = Modifier.width(72.dp),
                        label = { Text("Icon") },
                        singleLine = true,
                        colors = motoTextFieldColors()
                    )
                    OutlinedTextField(
                        value = intervalKm,
                        onValueChange = { intervalKm = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Interval (km)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = motoTextFieldColors()
                    )
                }
                OutlinedTextField(
                    value = lastServiceKm,
                    onValueChange = { lastServiceKm = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Last Serviced at (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = motoTextFieldColors()
                )
                if (!isEditing) {
                    OutlinedTextField(
                        value = rawCost,
                        onValueChange = { rawCost = CurrencyFormatters.sanitizeDecimalInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional Cost (₱)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = motoTextFieldColors()
                    )

                    val parsedCost = CurrencyFormatters.parsePesoToCentavos(rawCost) ?: 0L
                    if (parsedCost > 0L) {
                        EnvelopeSelectorChips(
                            availableEnvelopes = availableEnvelopes,
                            selectedEnvId = selectedEnvId,
                            onSelectEnvelope = { selectedEnvId = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (taskName.isNotBlank()) {
                        val interval = intervalKm.toIntOrNull() ?: 3000
                        val lastKm = lastServiceKm.toIntOrNull() ?: currentOdometerKm
                        if (editingItem != null) {
                            onUpdateExisting(editingItem.id, taskName.trim(), iconEmoji.ifBlank { "🔧" }, interval, lastKm)
                        } else {
                            val cost = CurrencyFormatters.parsePesoToCentavos(rawCost) ?: 0L
                            onSaveNew(taskName.trim(), iconEmoji.ifBlank { "🔧" }, interval, lastKm, cost, if (cost > 0L) selectedEnvId else null)
                        }
                    }
                },
                enabled = taskName.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinanceTheme.colors.accentSky,
                    contentColor = FinanceTheme.colors.bgBase
                )
            ) {
                Text(if (isEditing) "Save Changes" else "Save", style = FinanceTypography.labelLarge)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editingItem != null) {
                    TextButton(
                        onClick = { onDelete(editingItem.id) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = FinanceTheme.colors.accentRose,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = FinanceTheme.colors.accentRose)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", color = FinanceTheme.colors.textSecondary)
                }
            }
        }
    )
}
