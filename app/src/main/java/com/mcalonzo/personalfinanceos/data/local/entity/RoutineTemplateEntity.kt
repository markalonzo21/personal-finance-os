package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey val id: Int = 1,
    val totalMonthlyBudgetCentavos: Long,
    val autoRolloverEnabled: Boolean = true,
    val updatedAtEpochMs: Long
)
