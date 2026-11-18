package com.mcalonzo.personalfinanceos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.EnvelopeEntity
import com.mcalonzo.personalfinanceos.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_cycles ORDER BY yearMonth DESC")
    fun observeAllCycles(): Flow<List<BudgetCycleEntity>>

    @Query("SELECT * FROM budget_cycles ORDER BY yearMonth DESC")
    suspend fun getAllCyclesOnce(): List<BudgetCycleEntity>

    @Query("SELECT * FROM budget_cycles WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeCycleByYearMonth(yearMonth: String): Flow<BudgetCycleEntity?>

    @Query("SELECT * FROM budget_cycles WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getCycleByYearMonth(yearMonth: String): BudgetCycleEntity?

    @Query("SELECT * FROM budget_cycles WHERE id = :cycleId LIMIT 1")
    suspend fun getCycleById(cycleId: Long): BudgetCycleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCycle(cycle: BudgetCycleEntity): Long

    @Update
    suspend fun updateCycle(cycle: BudgetCycleEntity)

    @Query("DELETE FROM budget_cycles WHERE id = :cycleId")
    suspend fun deleteCycleById(cycleId: Long)

    @Query("UPDATE budget_cycles SET totalBudgetCentavos = :totalBudgetCentavos WHERE id = :cycleId")
    suspend fun updateCycleTotalBudget(cycleId: Long, totalBudgetCentavos: Long)

    @Query(
        "UPDATE budget_cycles SET soaPdfLocalUri = :localUri, soaFileName = :fileName, " +
            "soaUploadedAtEpochMs = :uploadedAtEpochMs WHERE id = :cycleId"
    )
    suspend fun updateCycleSoa(
        cycleId: Long,
        localUri: String?,
        fileName: String?,
        uploadedAtEpochMs: Long?
    )

    @Query("SELECT * FROM envelopes WHERE cycleId = :cycleId ORDER BY id ASC")
    fun observeEnvelopesForCycle(cycleId: Long): Flow<List<EnvelopeEntity>>

    @Query("SELECT * FROM envelopes WHERE cycleId = :cycleId ORDER BY id ASC")
    suspend fun getEnvelopesForCycleOnce(cycleId: Long): List<EnvelopeEntity>

    @Query("SELECT * FROM envelopes WHERE id = :envelopeId LIMIT 1")
    suspend fun getEnvelopeById(envelopeId: Long): EnvelopeEntity?

    @Query("SELECT * FROM envelopes WHERE cycleId = :cycleId AND isGasEnvelope = 1 LIMIT 1")
    suspend fun getGasEnvelopeForCycle(cycleId: Long): EnvelopeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEnvelope(envelope: EnvelopeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEnvelopes(envelopes: List<EnvelopeEntity>): List<Long>

    @Update
    suspend fun updateEnvelope(envelope: EnvelopeEntity)

    @Query(
        "UPDATE envelopes SET spentCentavos = MAX(0, spentCentavos + :deltaCentavos) " +
            "WHERE id = :envelopeId"
    )
    suspend fun incrementEnvelopeSpent(envelopeId: Long, deltaCentavos: Long)

    @Query("UPDATE envelopes SET iconEmoji = :iconEmoji WHERE id = :envelopeId")
    suspend fun updateEnvelopeIcon(envelopeId: Long, iconEmoji: String)

    @Query("DELETE FROM envelopes WHERE id = :envelopeId")
    suspend fun deleteEnvelopeById(envelopeId: Long)

    @Query(
        "SELECT * FROM transactions WHERE cycleId = :cycleId " +
            "ORDER BY timestampEpochMs DESC, id DESC"
    )
    fun observeTransactionsForCycle(cycleId: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE cycleId = :cycleId " +
            "ORDER BY timestampEpochMs DESC, id DESC"
    )
    suspend fun getTransactionsForCycleOnce(cycleId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query(
        "UPDATE transactions SET title = :title, amountCentavos = :amountCentavos, " +
            "envelopeId = :envelopeId, categoryLabel = :categoryLabel WHERE id = :transactionId"
    )
    suspend fun updateTransaction(
        transactionId: Long,
        title: String,
        amountCentavos: Long,
        envelopeId: Long?,
        categoryLabel: String
    )

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)
}
