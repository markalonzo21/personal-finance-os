package com.mcalonzo.personalfinanceos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mcalonzo.personalfinanceos.data.local.dao.BudgetDao
import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.dao.ObligationDao
import com.mcalonzo.personalfinanceos.data.local.dao.RoutineDao
import com.mcalonzo.personalfinanceos.data.local.dao.ScanDao
import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.data.local.entity.EnvelopeEntity
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.data.local.entity.MotoProfileEntity
import com.mcalonzo.personalfinanceos.data.local.entity.PmsIntervalEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineBillTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineEnvelopeTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.RoutineTemplateEntity
import com.mcalonzo.personalfinanceos.data.local.entity.ScanFormatProfileEntity
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
        GasPriceHistoryEntity::class,
        CardEntity::class,
        ScanFormatProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun budgetDao(): BudgetDao
    abstract fun obligationDao(): ObligationDao
    abstract fun motoDao(): MotoDao
    abstract fun scanDao(): ScanDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cards` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`cardType` TEXT NOT NULL, " +
                        "`billingCycleDay` INTEGER, " +
                        "`createdAtEpochMs` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_format_profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`storeKey` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`defaultEnvelopeName` TEXT, " +
                        "`ignoredKeywordsPipe` TEXT NOT NULL, " +
                        "`scanCount` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_scan_format_profiles_storeKey` " +
                        "ON `scan_format_profiles` (`storeKey`)"
                )
            }
        }
    }
}
