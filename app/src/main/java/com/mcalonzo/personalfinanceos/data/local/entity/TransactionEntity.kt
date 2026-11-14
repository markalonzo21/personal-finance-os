package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = BudgetCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cycleId"), Index("envelopeId")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val envelopeId: Long? = null,
    val billId: Long? = null,
    val loanId: Long? = null,
    val title: String,
    val categoryLabel: String,
    val amountCentavos: Long,
    val timestampEpochMs: Long
)
