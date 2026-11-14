package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconEmoji: String,
    val monthlyAmortizationCentavos: Long,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val dueDayOfMonth: Int,
    val nextDueYearMonth: String,
    val lastPaidCycleYearMonth: String? = null,
    val isActive: Boolean = true,
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true
)
