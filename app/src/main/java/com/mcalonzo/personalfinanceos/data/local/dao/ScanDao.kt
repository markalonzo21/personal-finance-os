package com.mcalonzo.personalfinanceos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.data.local.entity.ScanFormatProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM cards ORDER BY createdAtEpochMs DESC, id DESC")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity): Long

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: Long)

    @Query("SELECT * FROM scan_format_profiles ORDER BY updatedAtEpochMs DESC, id DESC")
    suspend fun getAllFormatProfilesOnce(): List<ScanFormatProfileEntity>

    @Query("SELECT * FROM scan_format_profiles WHERE storeKey = :storeKey LIMIT 1")
    suspend fun getFormatProfileByKey(storeKey: String): ScanFormatProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFormatProfile(profile: ScanFormatProfileEntity): Long
}
