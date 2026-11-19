package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.data.remote.GasFuelType
import com.mcalonzo.personalfinanceos.data.remote.GasWatchAllowlist
import com.mcalonzo.personalfinanceos.data.remote.GasWatchLocations
import com.mcalonzo.personalfinanceos.data.remote.ParsedGasStation
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
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
    val locationListState = rememberLazyListState()

    var selectedLocationSlug by remember { mutableStateOf(currentState.locationSlug) }
    var selectedLocationName by remember { mutableStateOf(currentState.locationDisplayName) }
    var selectedStationId by remember { mutableStateOf(currentState.stationId) }
    var stationDisplayName by remember { mutableStateOf(currentState.stationDisplayName) }
    var hasCustomDisplayName by remember { mutableStateOf(false) }
    var selectedFuelType by remember {
        mutableStateOf(
            GasWatchAllowlist.coerceFuelType(GasFuelType.fromKeyOrLabel(currentState.fuelKey))
        )
    }
    var branchQuery by remember { mutableStateOf("") }

    // Scroll active city into view on first open so users can see their current location.
    LaunchedEffect(Unit) {
        val activeIndex = GasWatchLocations.ALL.indexOfFirst { it.slug == selectedLocationSlug }
        if (activeIndex > 0) {
            locationListState.scrollToItem(activeIndex)
        }
    }

    val allowedStations = remember(pickerState.stations) {
        GasWatchAllowlist.filterStations(pickerState.stations)
    }

    val filteredStations = remember(allowedStations, branchQuery) {
        val q = branchQuery.trim()
        if (q.isEmpty()) {
            allowedStations
        } else {
            allowedStations.filter {
                it.stationName.contains(q, ignoreCase = true) ||
                    it.brand.contains(q, ignoreCase = true)
            }
        }
    }

    val selectedStation = remember(allowedStations, selectedStationId) {
        allowedStations.firstOrNull { it.stationId == selectedStationId }
    }

    val displayStations = remember(filteredStations, selectedStationId, branchQuery) {
        if (branchQuery.isBlank() && selectedStationId > 0) {
            val selected = filteredStations.firstOrNull { it.stationId == selectedStationId }
            if (selected != null) {
                listOf(selected) + filteredStations.filterNot { it.stationId == selectedStationId }
            } else {
                filteredStations
            }
        } else {
            filteredStations
        }
    }

    val loadedCurrentLocationStations = !pickerState.isLoadingStations &&
        pickerState.errorMessage == null &&
        pickerState.selectedLocationSlug == selectedLocationSlug &&
        allowedStations.isNotEmpty()

    val needsBranchSelection = selectedStationId <= 0 ||
        (selectedLocationSlug != currentState.locationSlug && selectedStation == null) ||
        (loadedCurrentLocationStations && selectedStation == null)

    val canSave = !needsBranchSelection &&
        stationDisplayName.isNotBlank() &&
        !pickerState.isLoadingStations

    val availableFuelTypes = remember(selectedStation) {
        val stationPrices = selectedStation?.pricesCentavos
        if (stationPrices.isNullOrEmpty()) {
            GasWatchAllowlist.ALLOWED_FUEL_TYPES
        } else {
            GasWatchAllowlist.ALLOWED_FUEL_TYPES.filter { stationPrices.containsKey(it) }
        }
    }

    LaunchedEffect(availableFuelTypes) {
        if (availableFuelTypes.isNotEmpty() && selectedFuelType !in availableFuelTypes) {
            selectedFuelType = availableFuelTypes.first()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = FinanceTheme.colors.accentSky,
        unfocusedBorderColor = FinanceTheme.colors.borderSubtle,
        focusedTextColor = FinanceTheme.colors.textPrimary,
        unfocusedTextColor = FinanceTheme.colors.textPrimary,
        focusedLabelColor = FinanceTheme.colors.accentSky,
        unfocusedLabelColor = FinanceTheme.colors.textSecondary
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            color = FinanceTheme.colors.bgCard,
            contentColor = FinanceTheme.colors.textPrimary,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Text(
                text = "Gas Watcher Settings",
                style = FinanceTypography.titleLarge,
                color = FinanceTheme.colors.textPrimary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("LOCATION")
                LazyRow(
                    state = locationListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    if (!hasCustomDisplayName) {
                                        stationDisplayName = ""
                                    }
                                    branchQuery = ""
                                    onLocationSelected(loc.slug)
                                }
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = {
                                Text(loc.displayName, style = FinanceTypography.labelMedium)
                            },
                            border = chipBorder(isSelected),
                            colors = chipColors()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionLabel("BRANCH")
                    if (selectedStation != null) {
                        Text(
                            text = "Selected: ${selectedStation.stationName}",
                            style = FinanceTypography.labelMedium,
                            color = FinanceTheme.colors.accentSky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = branchQuery,
                    onValueChange = { branchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search branch or brand") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = FinanceTheme.colors.textSecondary
                        )
                    },
                    trailingIcon = if (branchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { branchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = FinanceTheme.colors.textSecondary
                                )
                            }
                        }
                    } else null,
                    colors = textFieldColors
                )

                StationListSection(
                    isLoading = pickerState.isLoadingStations,
                    loadingLocationName = selectedLocationName,
                    errorMessage = pickerState.errorMessage,
                    filteredStations = displayStations,
                    branchQuery = branchQuery,
                    selectedStationId = selectedStationId,
                    selectedFuelType = selectedFuelType,
                    onRetry = { onLocationSelected(selectedLocationSlug) },
                    onClearSearch = { branchQuery = "" },
                    onSelectStation = { station ->
                        selectedStationId = station.stationId
                        if (!hasCustomDisplayName || stationDisplayName.isBlank()) {
                            stationDisplayName = station.stationName
                        }
                        if (!station.pricesCentavos.containsKey(selectedFuelType)) {
                            GasWatchAllowlist.ALLOWED_FUEL_TYPES
                                .firstOrNull { station.pricesCentavos.containsKey(it) }
                                ?.let { selectedFuelType = it }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = stationDisplayName,
                    onValueChange = { next ->
                        stationDisplayName = next
                        hasCustomDisplayName = next.isNotBlank() &&
                            next != selectedStation?.stationName
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Card Display Name") },
                    supportingText = {
                        Text(
                            text = "Shown on the Gas Price Watcher card",
                            style = FinanceTypography.labelMedium,
                            color = FinanceTheme.colors.textSecondary
                        )
                    },
                    singleLine = true,
                    colors = textFieldColors
                )

                SectionLabel("FUEL TYPE")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = { Text(labelText, style = FinanceTypography.labelMedium) },
                            border = chipBorder(isSelected),
                            colors = chipColors()
                        )
                    }
                }
            }

            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!canSave) {
                    Text(
                        text = when {
                            pickerState.isLoadingStations -> "Loading stations..."
                            needsBranchSelection -> "Select a branch to save"
                            else -> "Enter a display name"
                        },
                        style = FinanceTypography.labelMedium,
                        color = FinanceTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Cancel", color = FinanceTheme.colors.textSecondary)
                    }
                    Button(
                        onClick = {
                            val cleanName = stationDisplayName.trim()
                                .ifEmpty { selectedStation?.stationName ?: currentState.stationDisplayName }
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
                            containerColor = FinanceTheme.colors.accentSky,
                            contentColor = FinanceTheme.colors.bgBase
                        )
                    ) {
                        Text("Save", style = FinanceTypography.labelLarge)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = FinanceTypography.labelMedium,
        color = FinanceTheme.colors.textSecondary,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = FinanceTheme.colors.accentSky.copy(alpha = 0.2f),
    selectedLabelColor = FinanceTheme.colors.accentSky,
    selectedLeadingIconColor = FinanceTheme.colors.accentSky,
    containerColor = FinanceTheme.colors.bgCardSubtle,
    labelColor = FinanceTheme.colors.textSecondary
)

@Composable
private fun chipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = FinanceTheme.colors.borderSubtle,
    selectedBorderColor = FinanceTheme.colors.accentSky,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp
)

@Composable
private fun StationListSection(
    isLoading: Boolean,
    loadingLocationName: String,
    errorMessage: String?,
    filteredStations: List<ParsedGasStation>,
    branchQuery: String,
    selectedStationId: Int,
    selectedFuelType: GasFuelType,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    onSelectStation: (ParsedGasStation) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            isLoading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = FinanceTheme.colors.accentSky,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading $loadingLocationName stations...",
                        style = FinanceTypography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary
                    )
                }
            }

            errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FinanceTheme.colors.bgCardSubtle)
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = FinanceTypography.bodyMedium,
                        color = FinanceTheme.colors.accentRose
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Retry", color = FinanceTheme.colors.accentSky)
                    }
                }
            }

            filteredStations.isEmpty() -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FinanceTheme.colors.bgCardSubtle)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "No matching branches found.",
                        style = FinanceTypography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (branchQuery.isNotBlank()) {
                        TextButton(
                            onClick = onClearSearch,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Clear search", color = FinanceTheme.colors.accentSky)
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredStations.forEach { station ->
                        val isSelected = station.stationId == selectedStationId
                        val previewPrice = station.pricesCentavos[selectedFuelType]
                            ?: station.pricesCentavos.values.firstOrNull()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) FinanceTheme.colors.accentSky.copy(alpha = 0.16f)
                                    else FinanceTheme.colors.bgCardSubtle
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) FinanceTheme.colors.accentSky else FinanceTheme.colors.borderSubtle,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelectStation(station) }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = station.stationName,
                                    style = FinanceTypography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = FinanceTheme.colors.textPrimary
                                )
                                if (station.brand.isNotBlank()) {
                                    Text(
                                        text = station.brand.uppercase(),
                                        style = FinanceTypography.labelMedium,
                                        color = FinanceTheme.colors.textSecondary
                                    )
                                }
                            }
                            if (previewPrice != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = BudgetCalculator.formatPeso(previewPrice),
                                    style = NumericTextStyles.bodyAmount,
                                    color = if (isSelected) FinanceTheme.colors.accentSky else FinanceTheme.colors.textSecondary
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = FinanceTheme.colors.accentSky,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
