package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budget_cycles",
    indices = [Index(value = ["yearMonth"], unique = true)]
)
data class BudgetCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String,
    val totalBudgetCentavos: Long,
    val soaPdfLocalUri: String? = null,
    val soaFileName: String? = null,
    val soaUploadedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long
)
