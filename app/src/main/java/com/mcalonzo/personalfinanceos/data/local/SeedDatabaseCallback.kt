package com.mcalonzo.personalfinanceos.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Populates initial Philippine Peso routine, obligations, Honda Click 160 profile,
 * PMS schedule, and Petron Fairview fuel baseline synchronously during database creation.
 */
class SeedDatabaseCallback(
    @Suppress("unused") private val dbProvider: () -> AppDatabase
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val nowMs = System.currentTimeMillis()
        db.beginTransaction()
        try {
            seedRoutineTemplate(db, nowMs)
            seedEnvelopeTemplates(db)
            seedBillTemplates(db)
            seedInitialLoans(db)
            seedMotoProfile(db)
            seedPmsIntervals(db, nowMs)
            seedInitialGasPrice(db, nowMs)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedRoutineTemplate(db: SupportSQLiteDatabase, nowMs: Long) {
        val values = ContentValues().apply {
            put("id", 1)
            put("totalMonthlyBudgetCentavos", 5_000_000L)
            put("autoRolloverEnabled", 1)
            put("updatedAtEpochMs", nowMs)
        }
        db.insert("routine_templates", SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private fun seedEnvelopeTemplates(db: SupportSQLiteDatabase) {
        val envelopes = listOf(
            EnvelopeSeed("Groceries", "\uD83D\uDED2", "#38BDF8", 1_500_000L, false, 0),
            EnvelopeSeed("Dining Out", "\uD83C\uDF5C", "#10B981", 600_000L, false, 1),
            EnvelopeSeed("Motorcycle Gas", "\u26FD", "#F59E0B", 350_000L, true, 2),
            EnvelopeSeed("Utilities & Net", "\u26A1", "#A855F7", 800_000L, false, 3),
            EnvelopeSeed("Personal & Misc", "\uD83C\uDFA7", "#F43F5E", 400_000L, false, 4)
        )
        envelopes.forEach { item ->
            val values = ContentValues().apply {
                put("name", item.name)
                put("iconEmoji", item.iconEmoji)
                put("colorHex", item.colorHex)
                put("allocatedLimitCentavos", item.allocatedLimitCentavos)
                put("isGasEnvelope", if (item.isGasEnvelope) 1 else 0)
                put("sortOrder", item.sortOrder)
            }
            db.insert("routine_envelope_templates", SQLiteDatabase.CONFLICT_REPLACE, values)
        }
    }

    private fun seedBillTemplates(db: SupportSQLiteDatabase) {
        val bills = listOf(
            BillSeed("Converge FiberX", "\uD83D\uDCF6", 150_000L, 11),
            BillSeed("Netflix", "\uD83C\uDFAC", 54_900L, 18),
            BillSeed("Meralco", "\u26A1", 380_000L, 24),
            BillSeed("Spotify", "\uD83C\uDFB5", 14_900L, 28)
        )
        bills.forEach { bill ->
            val values = ContentValues().apply {
                put("name", bill.name)
                put("iconEmoji", bill.iconEmoji)
                put("defaultAmountCentavos", bill.defaultAmountCentavos)
                put("dueDayOfMonth", bill.dueDayOfMonth)
                put("isActive", 1)
            }
            db.insert("routine_bill_templates", SQLiteDatabase.CONFLICT_REPLACE, values)
        }
    }

    private fun seedInitialLoans(db: SupportSQLiteDatabase) {
        val loans = listOf(
            LoanSeed(
                name = "Honda Click 160 Loan",
                iconEmoji = "\uD83D\uDEF5",
                monthlyAmortizationCentavos = 485_000L,
                totalInstallments = 24,
                paidInstallments = 14,
                dueDayOfMonth = 20,
                nextDueYearMonth = "2026-11"
            ),
            LoanSeed(
                name = "Personal Bank Loan",
                iconEmoji = "\uD83C\uDFE6",
                monthlyAmortizationCentavos = 350_000L,
                totalInstallments = 12,
                paidInstallments = 6,
                dueDayOfMonth = 25,
                nextDueYearMonth = "2026-11"
            )
        )
        loans.forEach { loan ->
            val values = ContentValues().apply {
                put("name", loan.name)
                put("iconEmoji", loan.iconEmoji)
                put("monthlyAmortizationCentavos", loan.monthlyAmortizationCentavos)
                put("totalInstallments", loan.totalInstallments)
                put("paidInstallments", loan.paidInstallments)
                put("dueDayOfMonth", loan.dueDayOfMonth)
                put("nextDueYearMonth", loan.nextDueYearMonth)
                putNull("lastPaidCycleYearMonth")
                put("isActive", 1)
                put("notify7d", 1)
                put("notify3d", 1)
                put("notify1d", 1)
                put("notifyOnDue", 1)
                put("notifyDailyOverdue", 1)
            }
            db.insert("loans", SQLiteDatabase.CONFLICT_REPLACE, values)
        }
    }

    private fun seedMotoProfile(db: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", 1)
            put("modelName", "Honda Click 160")
            put("currentOdometerKm", 8450)
            put("tankCapacityLiters", 5.5)
        }
        db.insert("moto_profile", SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private fun seedPmsIntervals(db: SupportSQLiteDatabase, nowMs: Long) {
        val intervals = listOf(
            PmsSeed("Honda 4T SL 10W-30 Engine Oil", "\uD83D\uDEE2\uFE0F", 1500, 7500),
            PmsSeed("CVT Cleaning & Belt Inspection", "\uD83D\uDD27", 3000, 6000),
            PmsSeed("Final Gear Oil", "\u2699\uFE0F", 3000, 6000)
        )
        intervals.forEach { pms ->
            val values = ContentValues().apply {
                put("taskName", pms.taskName)
                put("iconEmoji", pms.iconEmoji)
                put("intervalKm", pms.intervalKm)
                put("lastServiceOdometerKm", pms.lastServiceOdometerKm)
                put("lastServiceEpochMs", nowMs)
            }
            db.insert("pms_intervals", SQLiteDatabase.CONFLICT_REPLACE, values)
        }
    }

    private fun seedInitialGasPrice(db: SupportSQLiteDatabase, nowMs: Long) {
        val values = ContentValues().apply {
            put("stationId", 621)
            put("stationDisplayName", "Petron Fairview")
            put("fuelType", "Premium 95")
            put("pricePerLiterCentavos", 7840L)
            put("previousPricePerLiterCentavos", 7795L)
            put("diffCentavos", 45L)
            put("fetchedAtEpochMs", nowMs)
            put("isManualOverride", 0)
        }
        db.insert("gas_price_history", SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private data class EnvelopeSeed(
        val name: String,
        val iconEmoji: String,
        val colorHex: String,
        val allocatedLimitCentavos: Long,
        val isGasEnvelope: Boolean,
        val sortOrder: Int
    )

    private data class BillSeed(
        val name: String,
        val iconEmoji: String,
        val defaultAmountCentavos: Long,
        val dueDayOfMonth: Int
    )

    private data class LoanSeed(
        val name: String,
        val iconEmoji: String,
        val monthlyAmortizationCentavos: Long,
        val totalInstallments: Int,
        val paidInstallments: Int,
        val dueDayOfMonth: Int,
        val nextDueYearMonth: String
    )

    private data class PmsSeed(
        val taskName: String,
        val iconEmoji: String,
        val intervalKm: Int,
        val lastServiceOdometerKm: Int
    )
}
