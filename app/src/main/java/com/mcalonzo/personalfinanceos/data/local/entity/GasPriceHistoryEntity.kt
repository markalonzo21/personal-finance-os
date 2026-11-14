package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gas_price_history")
data class GasPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: Int = 621,
    val stationDisplayName: String = "Petron Fairview",
    val fuelType: String = "Premium 95",
    val pricePerLiterCentavos: Long,
    val previousPricePerLiterCentavos: Long?,
    val diffCentavos: Long,
    val fetchedAtEpochMs: Long,
    val isManualOverride: Boolean = false
)
