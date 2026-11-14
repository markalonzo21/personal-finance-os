package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pms_intervals")
data class PmsIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskName: String,
    val iconEmoji: String,
    val intervalKm: Int,
    val lastServiceOdometerKm: Int,
    val lastServiceEpochMs: Long
)
