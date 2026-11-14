package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine_envelope_templates")
data class RoutineEnvelopeTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconEmoji: String,
    val colorHex: String,
    val allocatedLimitCentavos: Long,
    val isGasEnvelope: Boolean = false,
    val sortOrder: Int
)
