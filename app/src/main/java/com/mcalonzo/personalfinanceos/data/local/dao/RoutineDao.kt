package com.mcalonzo.personalfinanceos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_templates WHERE id = 1 LIMIT 1")
    fun observeRoutineTemplate(): Flow<RoutineTemplateEntity?>

    @Query("SELECT * FROM routine_templates WHERE id = 1 LIMIT 1")
    suspend fun getRoutineTemplateOnce(): RoutineTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutineTemplate(template: RoutineTemplateEntity)

    @Query("SELECT * FROM routine_envelope_templates ORDER BY sortOrder ASC, id ASC")
    fun observeEnvelopeTemplates(): Flow<List<RoutineEnvelopeTemplateEntity>>

    @Query("SELECT * FROM routine_envelope_templates ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnvelopeTemplatesOnce(): List<RoutineEnvelopeTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnvelopeTemplate(template: RoutineEnvelopeTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnvelopeTemplates(templates: List<RoutineEnvelopeTemplateEntity>): List<Long>

    @Update
    suspend fun updateEnvelopeTemplate(template: RoutineEnvelopeTemplateEntity)

    @Query("DELETE FROM routine_envelope_templates WHERE id = :id")
    suspend fun deleteEnvelopeTemplateById(id: Long)

    @Query("DELETE FROM routine_envelope_templates")
    suspend fun deleteAllEnvelopeTemplates()

    @Transaction
    suspend fun replaceEnvelopeTemplates(templates: List<RoutineEnvelopeTemplateEntity>) {
        deleteAllEnvelopeTemplates()
        if (templates.isNotEmpty()) {
            insertEnvelopeTemplates(templates)
        }
    }

    @Query("SELECT * FROM routine_bill_templates ORDER BY dueDayOfMonth ASC, id ASC")
    fun observeBillTemplates(): Flow<List<RoutineBillTemplateEntity>>

    @Query("SELECT * FROM routine_bill_templates ORDER BY dueDayOfMonth ASC, id ASC")
    suspend fun getBillTemplatesOnce(): List<RoutineBillTemplateEntity>

    @Query("SELECT * FROM routine_bill_templates WHERE isActive = 1 ORDER BY dueDayOfMonth ASC, id ASC")
    suspend fun getActiveBillTemplatesOnce(): List<RoutineBillTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillTemplate(template: RoutineBillTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillTemplates(templates: List<RoutineBillTemplateEntity>): List<Long>

    @Update
    suspend fun updateBillTemplate(template: RoutineBillTemplateEntity)

    @Query("DELETE FROM routine_bill_templates WHERE id = :id")
    suspend fun deleteBillTemplateById(id: Long)

    @Query("DELETE FROM routine_bill_templates")
    suspend fun deleteAllBillTemplates()

    @Transaction
    suspend fun replaceBillTemplates(templates: List<RoutineBillTemplateEntity>) {
        deleteAllBillTemplates()
        if (templates.isNotEmpty()) {
            insertBillTemplates(templates)
        }
    }
}
