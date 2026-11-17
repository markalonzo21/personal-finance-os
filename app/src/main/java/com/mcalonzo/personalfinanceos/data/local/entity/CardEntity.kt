package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val cardType: String,
    val billingCycleDay: Int? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
