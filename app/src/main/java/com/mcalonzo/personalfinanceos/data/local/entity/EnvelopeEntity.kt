package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "envelopes",
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
data class EnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val name: String,
    val iconEmoji: String,
    val colorHex: String,
    val allocatedLimitCentavos: Long,
    val spentCentavos: Long = 0L,
    val isGasEnvelope: Boolean = false
)
