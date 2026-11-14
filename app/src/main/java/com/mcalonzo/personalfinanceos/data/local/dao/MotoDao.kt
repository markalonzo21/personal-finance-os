package com.mcalonzo.personalfinanceos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import com.mcalonzo.personalfinanceos.data.local.entity.MotoProfileEntity
import com.mcalonzo.personalfinanceos.data.local.entity.PmsIntervalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MotoDao {
    @Query("SELECT * FROM moto_profile WHERE id = 1 LIMIT 1")
    fun observeMotoProfile(): Flow<MotoProfileEntity?>

    @Query("SELECT * FROM moto_profile WHERE id = 1 LIMIT 1")
    suspend fun getMotoProfileOnce(): MotoProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMotoProfile(profile: MotoProfileEntity)

    @Query("UPDATE moto_profile SET currentOdometerKm = :odometerKm WHERE id = 1")
    suspend fun updateOdometerKm(odometerKm: Int)

    @Query("SELECT * FROM pms_intervals ORDER BY id ASC")
    fun observePmsIntervals(): Flow<List<PmsIntervalEntity>>

    @Query("SELECT * FROM pms_intervals ORDER BY id ASC")
    suspend fun getPmsIntervalsOnce(): List<PmsIntervalEntity>

    @Query("SELECT * FROM pms_intervals WHERE id = :id LIMIT 1")
    suspend fun getPmsIntervalById(id: Long): PmsIntervalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPmsInterval(interval: PmsIntervalEntity): Long

    @Update
    suspend fun updatePmsInterval(interval: PmsIntervalEntity)

    @Query("DELETE FROM pms_intervals WHERE id = :id")
    suspend fun deletePmsIntervalById(id: Long)

    @Query(
        "SELECT * FROM gas_price_history ORDER BY fetchedAtEpochMs DESC, id DESC LIMIT 1"
    )
    fun observeLatestGasPrice(): Flow<GasPriceHistoryEntity?>

    @Query(
        "SELECT * FROM gas_price_history ORDER BY fetchedAtEpochMs DESC, id DESC LIMIT 1"
    )
    suspend fun getLatestGasPriceOnce(): GasPriceHistoryEntity?

    @Query("SELECT * FROM gas_price_history ORDER BY fetchedAtEpochMs DESC, id DESC")
    fun observeGasPriceHistory(): Flow<List<GasPriceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGasPrice(history: GasPriceHistoryEntity): Long
}
