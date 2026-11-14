package com.mcalonzo.personalfinanceos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mcalonzo.personalfinanceos.data.local.dao.BudgetDao
import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.dao.ObligationDao
import com.mcalonzo.personalfinanceos.data.local.dao.RoutineDao
import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.EnvelopeEntity
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.data.local.entity.MotoProfileEntity
import com.mcalonzo.personalfinanceos.data.local.entity.PmsIntervalEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.TransactionEntity

@Database(
    entities = [
        RoutineTemplateEntity::class,
        RoutineEnvelopeTemplateEntity::class,
        RoutineBillTemplateEntity::class,
        BudgetCycleEntity::class,
        EnvelopeEntity::class,
        TransactionEntity::class,
        BillEntity::class,
        LoanEntity::class,
        MotoProfileEntity::class,
        PmsIntervalEntity::class,
        GasPriceHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun budgetDao(): BudgetDao
    abstract fun obligationDao(): ObligationDao
    abstract fun motoDao(): MotoDao
}
