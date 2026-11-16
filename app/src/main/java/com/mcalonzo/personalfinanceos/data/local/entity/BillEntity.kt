package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = BudgetCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cycleId")]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val templateGroupId: String,
    val name: String,
    val iconEmoji: String,
    val amountCentavos: Long,
    val dueDayOfMonth: Int,
    val isPaid: Boolean = false,
    val paidAtEpochMs: Long? = null,
    val notify7d: Boolean = true,
    val notify3d: Boolean = true,
    val notify1d: Boolean = true,
    val notifyOnDue: Boolean = true,
    val notifyDailyOverdue: Boolean = true
)
