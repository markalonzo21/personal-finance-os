package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository

@Entity(tableName = "gas_price_history")
data class GasPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: Int = GasPriceRepository.STATION_ID,
    val stationDisplayName: String = GasPriceRepository.STATION_DISPLAY_NAME,
    val fuelType: String = "Premium 95",
    val pricePerLiterCentavos: Long,
    val previousPricePerLiterCentavos: Long?,
    val diffCentavos: Long,
    val fetchedAtEpochMs: Long,
    val isManualOverride: Boolean = false
)
