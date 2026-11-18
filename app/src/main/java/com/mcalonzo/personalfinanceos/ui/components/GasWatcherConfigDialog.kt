package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.data.remote.GasFuelType
import com.mcalonzo.personalfinanceos.data.remote.GasWatchLocations
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.viewmodel.GasWatcherPickerUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.MotoUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GasWatcherConfigDialog(
    currentState: MotoUiState,
    pickerState: GasWatcherPickerUiState,
    onLocationSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        locationSlug: String,
        locationDisplayName: String,
        stationId: Int,
        stationDisplayName: String,
        fuelKey: String,
        fuelDisplayName: String
    ) -> Unit
) {
    var selectedLocationSlug by remember { mutableStateOf(currentState.locationSlug) }
    var selectedLocationName by remember { mutableStateOf(currentState.locationDisplayName) }
    var selectedStationId by remember { mutableStateOf(currentState.stationId) }
    var stationDisplayName by remember { mutableStateOf(currentState.stationDisplayName) }
    var selectedFuelType by remember {
        mutableStateOf(GasFuelType.fromKeyOrLabel(currentState.fuelKey))
    }
    var branchQuery by remember { mutableStateOf("") }

    LaunchedEffect(pickerState.stations, pickerState.selectedLocationSlug, selectedLocationSlug) {
        if (!pickerState.isLoadingStations &&
            pickerState.selectedLocationSlug == selectedLocationSlug &&
            pickerState.stations.isNotEmpty() &&
            pickerState.stations.none { it.stationId == selectedStationId }
        ) {
            val first = pickerState.stations.first()
            selectedStationId = first.stationId
            stationDisplayName = first.stationName
            if (!first.pricesCentavos.containsKey(selectedFuelType)) {
                first.pricesCentavos.keys.firstOrNull()?.let { selectedFuelType = it }
            }
        }
    }

    val filteredStations = remember(pickerState.stations, branchQuery) {
        val q = branchQuery.trim()
        if (q.isEmpty()) {
            pickerState.stations
        } else {
            pickerState.stations.filter {
                it.stationName.contains(q, ignoreCase = true) ||
                    it.brand.contains(q, ignoreCase = true)
            }
        }
    }

    val selectedStation = remember(pickerState.stations, selectedStationId) {
        pickerState.stations.firstOrNull { it.stationId == selectedStationId }
    }

    val canSave = selectedStationId > 0 &&
        stationDisplayName.isNotBlank() &&
        (selectedLocationSlug == currentState.locationSlug || selectedStation != null)

    val availableFuelTypes = remember(selectedStation) {
        val stationPrices = selectedStation?.pricesCentavos
        if (stationPrices.isNullOrEmpty()) {
            GasFuelType.entries
        } else {
            GasFuelType.entries.filter { stationPrices.containsKey(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OLEDColors.bgCard,
        title = {
            Text(
                "Gas Watcher Settings",
                style = FinanceTypography.headlineSmall,
                color = OLEDColors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "LOCATION",
                    style = FinanceTypography.labelSmall,
                    color = OLEDColors.textTertiary,
                    letterSpacing = 0.8.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GasWatchLocations.ALL, key = { it.slug }) { loc ->
                        val isSelected = loc.slug == selectedLocationSlug
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (loc.slug != selectedLocationSlug) {
                                    selectedLocationSlug = loc.slug
                                    selectedLocationName = loc.displayName
                                    selectedStationId = -1
                                    stationDisplayName = ""
                                    branchQuery = ""
                                    onLocationSelected(loc.slug)
                                }
                            },
                            label = { Text(loc.displayName, style = FinanceTypography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OLEDColors.accentSky.copy(alpha = 0.2f),
                                selectedLabelColor = OLEDColors.accentSky,
                                containerColor = OLEDColors.bgCardSubtle,
                                labelColor = OLEDColors.textSecondary
                            )
                        )
                    }
                }

                Text(
                    "BRANCH",
                    style = FinanceTypography.labelSmall,
                    color = OLEDColors.textTertiary,
                    letterSpacing = 0.8.sp
                )
                OutlinedTextField(
                    value = branchQuery,
                    onValueChange = { branchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search branch or brand") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary,
                        focusedLabelColor = OLEDColors.accentSky,
                        unfocusedLabelColor = OLEDColors.textSecondary
                    )
                )

                when {
                    pickerState.isLoadingStations -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = OLEDColors.accentSky,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Loading $selectedLocationName stations...",
                                style = FinanceTypography.bodySmall,
                                color = OLEDColors.textSecondary
                            )
                        }
                    }

                    pickerState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(OLEDColors.bgCardSubtle)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = pickerState.errorMessage,
                                style = FinanceTypography.bodySmall,
                                color = OLEDColors.accentRose
                            )
                            TextButton(onClick = { onLocationSelected(selectedLocationSlug) }) {
                                Text("Retry", color = OLEDColors.accentSky)
                            }
                        }
                    }

                    filteredStations.isEmpty() -> {
                        Text(
                            "No matching branches found.",
                            style = FinanceTypography.bodySmall,
                            color = OLEDColors.textSecondary
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 170.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredStations, key = { it.stationId }) { station ->
                                val isSelected = station.stationId == selectedStationId
                                val previewPrice = station.pricesCentavos[selectedFuelType]
                                    ?: station.pricesCentavos.values.firstOrNull()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) OLEDColors.accentSky.copy(alpha = 0.16f)
                                            else OLEDColors.bgCardSubtle
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) OLEDColors.accentSky else OLEDColors.borderSubtle,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedStationId = station.stationId
                                            stationDisplayName = station.stationName
                                            if (!station.pricesCentavos.containsKey(selectedFuelType)) {
                                                station.pricesCentavos.keys.firstOrNull()?.let {
                                                    selectedFuelType = it
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = station.stationName,
                                            style = FinanceTypography.bodySmall.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = OLEDColors.textPrimary
                                        )
                                        if (station.brand.isNotBlank()) {
                                            Text(
                                                text = station.brand.uppercase(),
                                                style = FinanceTypography.labelSmall,
                                                color = OLEDColors.textTertiary
                                            )
                                        }
                                    }
                                    if (previewPrice != null) {
                                        Text(
                                            text = BudgetCalculator.formatPeso(previewPrice),
                                            style = FinanceTypography.labelMedium,
                                            color = if (isSelected) OLEDColors.accentSky else OLEDColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = stationDisplayName,
                    onValueChange = { stationDisplayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Card Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary,
                        focusedLabelColor = OLEDColors.accentSky,
                        unfocusedLabelColor = OLEDColors.textSecondary
                    )
                )

                Text(
                    "GAS TYPE",
                    style = FinanceTypography.labelSmall,
                    color = OLEDColors.textTertiary,
                    letterSpacing = 0.8.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableFuelTypes.forEach { fuel ->
                        val isSelected = fuel == selectedFuelType
                        val priceCentavos = selectedStation?.pricesCentavos?.get(fuel)
                        val labelText = if (priceCentavos != null) {
                            "${fuel.displayName} (${BudgetCalculator.formatPeso(priceCentavos)})"
                        } else {
                            fuel.displayName
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFuelType = fuel },
                            label = { Text(labelText, style = FinanceTypography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OLEDColors.accentSky.copy(alpha = 0.2f),
                                selectedLabelColor = OLEDColors.accentSky,
                                containerColor = OLEDColors.bgCardSubtle,
                                labelColor = OLEDColors.textSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = stationDisplayName.trim().ifEmpty { currentState.stationDisplayName }
                    onConfirm(
                        selectedLocationSlug,
                        selectedLocationName,
                        selectedStationId,
                        cleanName,
                        selectedFuelType.jsonKey,
                        selectedFuelType.displayName
                    )
                },
                enabled = canSave,
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
