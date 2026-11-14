package com.mcalonzo.personalfinanceos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObligationDao {
    @Query("SELECT * FROM bills WHERE cycleId = :cycleId ORDER BY dueDayOfMonth ASC, id ASC")
    fun observeBillsForCycle(cycleId: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE cycleId = :cycleId ORDER BY dueDayOfMonth ASC, id ASC")
    suspend fun getBillsForCycleOnce(cycleId: Long): List<BillEntity>

    @Query(
        "SELECT * FROM bills WHERE cycleId = :cycleId AND isPaid = 0 " +
            "ORDER BY dueDayOfMonth ASC, id ASC"
    )
    suspend fun getUnpaidBillsForCycleOnce(cycleId: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
    suspend fun getBillById(billId: Long): BillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBills(bills: List<BillEntity>): List<Long>

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteBillById(billId: Long)

    @Query("DELETE FROM bills WHERE cycleId = :cycleId AND isPaid = 0")
    suspend fun deleteUnpaidBillsForCycle(cycleId: Long)

    @Query(
        "UPDATE bills SET notify7d = :notify7d, notify3d = :notify3d, notify1d = :notify1d, " +
            "notifyOnDue = :notifyOnDue, notifyDailyOverdue = :notifyDailyOverdue WHERE id = :billId"
    )
    suspend fun updateBillReminderCadence(
        billId: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    )

    @Query("SELECT * FROM loans ORDER BY isActive DESC, dueDayOfMonth ASC, id ASC")
    fun observeAllLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE isActive = 1 ORDER BY dueDayOfMonth ASC, id ASC")
    fun observeActiveLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans ORDER BY isActive DESC, dueDayOfMonth ASC, id ASC")
    suspend fun getAllLoansOnce(): List<LoanEntity>

    @Query("SELECT * FROM loans WHERE isActive = 1 ORDER BY dueDayOfMonth ASC, id ASC")
    suspend fun getActiveLoansOnce(): List<LoanEntity>

    @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
    suspend fun getLoanById(loanId: Long): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :loanId")
    suspend fun deleteLoanById(loanId: Long)

    @Query(
        "UPDATE loans SET notify7d = :notify7d, notify3d = :notify3d, notify1d = :notify1d, " +
            "notifyOnDue = :notifyOnDue, notifyDailyOverdue = :notifyDailyOverdue WHERE id = :loanId"
    )
    suspend fun updateLoanReminderCadence(
        loanId: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    )
}
