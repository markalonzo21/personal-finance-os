package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moto_profile")
data class MotoProfileEntity(
    @PrimaryKey val id: Int = 1,
    val modelName: String = "Honda Click 160",
    val currentOdometerKm: Int = 8450,
    val tankCapacityLiters: Double = 5.5
)
