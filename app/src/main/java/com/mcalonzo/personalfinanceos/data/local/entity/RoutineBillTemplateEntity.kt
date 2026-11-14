package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine_bill_templates")
data class RoutineBillTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconEmoji: String,
    val defaultAmountCentavos: Long,
    val dueDayOfMonth: Int,
    val isActive: Boolean = true
)
