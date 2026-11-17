package com.mcalonzo.personalfinanceos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_format_profiles",
    indices = [Index(value = ["storeKey"], unique = true)]
)
data class ScanFormatProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val storeKey: String,
    val displayName: String,
    val defaultEnvelopeName: String? = null,
    val ignoredKeywordsPipe: String = "",
    val scanCount: Int = 1,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
