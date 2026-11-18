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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
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
            .background(OLEDColors.bgBase)
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
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = "FLEET & MAINTENANCE",
                            style = FinanceTypography.labelSmall,
                            color = OLEDColors.accentSky,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Moto Care",
                            style = FinanceTypography.headlineMedium,
                            color = OLEDColors.textPrimary
                        )
                        Text(
                            text = "Preventive Maintenance Schedule & Fuel Price Watcher",
                            style = FinanceTypography.bodyMedium,
                            color = OLEDColors.textSecondary
                        )
                    }
                }

                item(key = "hero_moto_card") {
                    MotoHeroCard(
                        modelName = state.modelName,
                        odometerKm = state.odometerKm,
                        tankCapacityLiters = state.tankCapacityLiters,
                        onEditOdometerClick = { showOdometerDialog = true }
                    )
                }

                item(key = "gas_price_card") {
                    GasPriceWatcherCard(
                        state = state,
                        onLogFullTank = onLogFullTank,
                        onRefreshGas = onRefreshGas,
                        onTapPrice = { showOverridePriceDialog = true },
                        onConfigureWatcher = {
                            showGasWatcherConfigDialog = true
                            onLoadGasStationsForLocation(state.locationSlug)
                        }
                    )
                }

                item(key = "pms_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PREVENTIVE MAINTENANCE (PMS)",
                                style = FinanceTypography.labelSmall,
                                color = OLEDColors.textSecondary,
                                letterSpacing = 1.1.sp
                            )
                            Text(
                                text = "Service Intervals",
                                style = FinanceTypography.titleMedium,
                                color = OLEDColors.textPrimary
                            )
                        }
                        Button(
                            onClick = {
                                editingPmsItem = null
                                showAddPmsDialog = true
                                onAddPms()
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OLEDColors.bgCardElevated,
                                contentColor = OLEDColors.accentSky
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Log PMS", style = FinanceTypography.labelMedium)
                        }
                    }
                }

                if (state.pmsItems.isEmpty()) {
                    item(key = "empty_pms") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = OLEDColors.bgCardSubtle),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No PMS schedules configured",
                                    style = FinanceTypography.bodyMedium,
                                    color = OLEDColors.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = state.pmsItems,
                        key = { it.id }
                    ) { pms ->
                        PmsCard(
                            item = pms,
                            onEditClick = { editingPmsItem = pms },
                            onLogServiceClick = { servicingPmsItem = pms }
                        )
                    }
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
private fun MotoHeroCard(
    modelName: String,
    odometerKm: Int,
    tankCapacityLiters: Double,
    onEditOdometerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OLEDColors.bgCard),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OLEDColors.borderSubtle))
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(OLEDColors.accentSky.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TwoWheeler,
                        contentDescription = null,
                        tint = OLEDColors.accentSky,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelName,
                        style = FinanceTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OLEDColors.textPrimary
                    )
                    Text(
                        text = "${tankCapacityLiters}L Fuel Tank • Daily Driver",
                        style = FinanceTypography.labelSmall,
                        color = OLEDColors.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OLEDColors.bgCardElevated)
                        .clickable(
                            role = Role.Button,
                            onClick = onEditOdometerClick
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "Edit Odometer",
                            tint = OLEDColors.accentSky,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("%,d km", odometerKm),
                            style = NumericTextStyles.cardAmount,
                            color = OLEDColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GasPriceWatcherCard(
    state: MotoUiState,
    onLogFullTank: () -> Unit,
    onRefreshGas: () -> Unit,
    onTapPrice: () -> Unit,
    onConfigureWatcher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trendColor = when {
        state.isPriceUp -> OLEDColors.accentRose
        state.isPriceDown -> OLEDColors.accentEmerald
        else -> OLEDColors.textSecondary
    }

    val trendBg = when {
        state.isPriceUp -> OLEDColors.accentRose.copy(alpha = 0.15f)
        state.isPriceDown -> OLEDColors.accentEmerald.copy(alpha = 0.15f)
        else -> OLEDColors.bgCardSubtle
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OLEDColors.bgCard),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OLEDColors.borderSubtle))
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OLEDColors.accentAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalGasStation,
                        contentDescription = null,
                        tint = OLEDColors.accentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onConfigureWatcher)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.stationDisplayName,
                            style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = OLEDColors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Configure Gas Watcher",
                            tint = OLEDColors.accentSky,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = state.fuelType,
                        style = FinanceTypography.labelSmall,
                        color = OLEDColors.textSecondary
                    )
                }

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

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(OLEDColors.bgCardElevated)
                        .clickable(
                            role = Role.Button,
                            onClick = onRefreshGas
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Sync Gas Price",
                        tint = OLEDColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onTapPrice)
                ) {
                    Text(
                        text = "PUMP PRICE (TAP TO EDIT)",
                        style = FinanceTypography.labelSmall,
                        color = OLEDColors.textTertiary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = state.pricePerLiterFormatted,
                        style = NumericTextStyles.heroBalance.copy(fontSize = 32.sp),
                        color = OLEDColors.accentSky
                    )
                    if (state.prevPriceFormatted.isNotEmpty()) {
                        Text(
                            text = state.prevPriceFormatted,
                            style = FinanceTypography.labelSmall,
                            color = OLEDColors.textSecondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "FULL TANK (5.5L)",
                        style = FinanceTypography.labelSmall,
                        color = OLEDColors.textTertiary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = state.fullTankCostFormatted,
                        style = NumericTextStyles.cardAmount.copy(fontSize = 20.sp),
                        color = OLEDColors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLogFullTank,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log Full Tank",
                    style = FinanceTypography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun PmsCard(
    item: PmsItemUi,
    onEditClick: () -> Unit,
    onLogServiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (item.isOverdue) OLEDColors.accentRose else OLEDColors.accentEmerald
    val statusBg = if (item.isOverdue) OLEDColors.accentRose.copy(alpha = 0.15f) else OLEDColors.accentEmerald.copy(alpha = 0.15f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OLEDColors.bgCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OLEDColors.borderSubtle))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OLEDColors.bgCardSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = item.iconEmoji, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.taskName,
                        style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = OLEDColors.textPrimary
                    )
                    Text(
                        text = "Every ${String.format("%,d", item.intervalKm)} km • Last: ${String.format("%,d", item.lastServiceOdometerKm)} km",
                        style = FinanceTypography.labelSmall,
                        color = OLEDColors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.dueStatusText,
                        style = FinanceTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OLEDColors.textSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit PMS Task",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", style = FinanceTypography.labelMedium)
                }

                Button(
                    onClick = onLogServiceClick,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OLEDColors.bgCardElevated,
                        contentColor = OLEDColors.accentSky
                    )
                ) {
                    Text(
                        text = "Log Service",
                        style = FinanceTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditOdometerDialog(
    currentOdometerKm: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var rawKm by remember { mutableStateOf(currentOdometerKm.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OLEDColors.bgCard,
        title = {
            Text(
                "Update Odometer",
                style = FinanceTypography.headlineSmall,
                color = OLEDColors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Enter the latest odometer reading for Honda Click 160 in kilometers.",
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawKm,
                    onValueChange = { rawKm = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = rawKm.toIntOrNull()
                    if (parsed != null && parsed >= 0) {
                        onConfirm(parsed)
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
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
                Text("Cancel", color = OLEDColors.textSecondary)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OLEDColors.bgCard,
        title = {
            Text(
                "Override Fuel Price",
                style = FinanceTypography.headlineSmall,
                color = OLEDColors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Set manual price per liter for $stationDisplayName ($fuelType).",
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = CurrencyFormatters.sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Price per Liter (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val centavos = CurrencyFormatters.parsePesoToCentavos(rawInput)
                    if (centavos != null && centavos > 0L) {
                        onConfirm(centavos)
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
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
                Text("Cancel", color = OLEDColors.textSecondary)
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
            color = OLEDColors.accentSky
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableEnvelopes.take(3).forEach { env ->
                val isSelected = env.id == selectedEnvId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) OLEDColors.accentSky.copy(alpha = 0.2f) else OLEDColors.bgCardSubtle)
                        .border(
                            1.dp,
                            if (isSelected) OLEDColors.accentSky else OLEDColors.borderSubtle,
                            RoundedCornerShape(8.dp)
                        )
                        .semantics(mergeDescendants = true) {}
                        .clickable(role = Role.Button) { onSelectEnvelope(env.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${env.iconEmoji} ${env.name}",
                        style = FinanceTypography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) OLEDColors.accentSky else OLEDColors.textSecondary
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
        containerColor = OLEDColors.bgCard,
        title = {
            Text(
                "Log PMS Service",
                style = FinanceTypography.headlineSmall,
                color = OLEDColors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${item.iconEmoji} ${item.taskName}",
                    style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = OLEDColors.textPrimary
                )
                Text(
                    text = "Update last service odometer and optionally charge maintenance cost to an envelope.",
                    style = FinanceTypography.bodySmall,
                    color = OLEDColors.textSecondary
                )
                OutlinedTextField(
                    value = rawKm,
                    onValueChange = { rawKm = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Service Odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary
                    )
                )
                OutlinedTextField(
                    value = rawCost,
                    onValueChange = { rawCost = CurrencyFormatters.sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional Cost (₱)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary
                    )
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
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
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
                Text("Cancel", color = OLEDColors.textSecondary)
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
        containerColor = OLEDColors.bgCard,
        title = {
            Text(
                if (isEditing) "Edit PMS Schedule" else "Add PMS Schedule",
                style = FinanceTypography.headlineSmall,
                color = OLEDColors.textPrimary
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = iconEmoji,
                        onValueChange = { iconEmoji = it.take(2) },
                        modifier = Modifier.width(72.dp),
                        label = { Text("Icon") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OLEDColors.accentSky,
                            unfocusedBorderColor = OLEDColors.borderSubtle,
                            focusedTextColor = OLEDColors.textPrimary,
                            unfocusedTextColor = OLEDColors.textPrimary
                        )
                    )
                    OutlinedTextField(
                        value = intervalKm,
                        onValueChange = { intervalKm = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Interval (km)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OLEDColors.accentSky,
                            unfocusedBorderColor = OLEDColors.borderSubtle,
                            focusedTextColor = OLEDColors.textPrimary,
                            unfocusedTextColor = OLEDColors.textPrimary
                        )
                    )
                }
                OutlinedTextField(
                    value = lastServiceKm,
                    onValueChange = { lastServiceKm = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Last Serviced at (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary
                    )
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OLEDColors.accentSky,
                            unfocusedBorderColor = OLEDColors.borderSubtle,
                            focusedTextColor = OLEDColors.textPrimary,
                            unfocusedTextColor = OLEDColors.textPrimary
                        )
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
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OLEDColors.accentSky,
                    contentColor = OLEDColors.bgBase
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
                            tint = OLEDColors.accentRose,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = OLEDColors.accentRose)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", color = OLEDColors.textSecondary)
                }
            }
        }
    )
}
