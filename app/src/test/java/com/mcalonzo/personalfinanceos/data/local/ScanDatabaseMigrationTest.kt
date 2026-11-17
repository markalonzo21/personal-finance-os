package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.data.local.entity.ScanFormatProfileEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private var roomDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(TEST_DB_NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        roomDb?.close()
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun `MIGRATION_1_2 preserves v1 budget_cycles data and enables ScanDao card and profile CRUD`() = runTest {
        createSeededV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
            .also { roomDb = it }

        val cycles = db.budgetDao().getAllCyclesOnce()
        assertThat(cycles).hasSize(1)
        assertThat(cycles.first().yearMonth).isEqualTo("2026-11")
        assertThat(cycles.first().totalBudgetCentavos).isEqualTo(5_000_000L)

        val scanDao = db.scanDao()
        val cardId = scanDao.upsertCard(
            CardEntity(
                name = "BPI Amore",
                cardType = "CREDIT",
                billingCycleDay = 15,
                createdAtEpochMs = 1_000L
            )
        )
        assertThat(cardId).isGreaterThan(0L)
        val loadedCard = scanDao.getCardById(cardId)
        assertThat(loadedCard?.name).isEqualTo("BPI Amore")
        assertThat(loadedCard?.cardType).isEqualTo("CREDIT")
        assertThat(loadedCard?.billingCycleDay).isEqualTo(15)
        assertThat(scanDao.observeCards().first().map { it.id }).contains(cardId)

        scanDao.deleteCardById(cardId)
        assertThat(scanDao.getCardById(cardId)).isNull()

        val profileId = scanDao.upsertFormatProfile(
            ScanFormatProfileEntity(
                storeKey = "mercury_drug",
                displayName = "Mercury Drug",
                defaultEnvelopeName = "Health",
                ignoredKeywordsPipe = "VAT|CHANGE",
                scanCount = 2,
                updatedAtEpochMs = 2_000L
            )
        )
        assertThat(profileId).isGreaterThan(0L)
        val loadedProfile = scanDao.getFormatProfileByKey("mercury_drug")
        assertThat(loadedProfile?.displayName).isEqualTo("Mercury Drug")
        assertThat(loadedProfile?.defaultEnvelopeName).isEqualTo("Health")
        assertThat(loadedProfile?.ignoredKeywordsPipe).isEqualTo("VAT|CHANGE")
        assertThat(loadedProfile?.scanCount).isEqualTo(2)
        assertThat(scanDao.getAllFormatProfilesOnce().map { it.storeKey }).containsExactly("mercury_drug")
    }

    private fun createSeededV1Database() {
        val helper = object : SQLiteOpenHelper(context, TEST_DB_NAME, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_templates` (`id` INTEGER NOT NULL, `totalMonthlyBudgetCentavos` INTEGER NOT NULL, `autoRolloverEnabled` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_envelope_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconEmoji` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `allocatedLimitCentavos` INTEGER NOT NULL, `isGasEnvelope` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_bill_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconEmoji` TEXT NOT NULL, `defaultAmountCentavos` INTEGER NOT NULL, `dueDayOfMonth` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `budget_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `yearMonth` TEXT NOT NULL, `totalBudgetCentavos` INTEGER NOT NULL, `soaPdfLocalUri` TEXT, `soaFileName` TEXT, `soaUploadedAtEpochMs` INTEGER, `createdAtEpochMs` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_cycles_yearMonth` ON `budget_cycles` (`yearMonth`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `envelopes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleId` INTEGER NOT NULL, `name` TEXT NOT NULL, `iconEmoji` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `allocatedLimitCentavos` INTEGER NOT NULL, `spentCentavos` INTEGER NOT NULL, `isGasEnvelope` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, FOREIGN KEY(`cycleId`) REFERENCES `budget_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_envelopes_cycleId` ON `envelopes` (`cycleId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleId` INTEGER NOT NULL, `envelopeId` INTEGER, `billId` INTEGER, `loanId` INTEGER, `title` TEXT NOT NULL, `categoryLabel` TEXT NOT NULL, `amountCentavos` INTEGER NOT NULL, `timestampEpochMs` INTEGER NOT NULL, FOREIGN KEY(`cycleId`) REFERENCES `budget_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_cycleId` ON `transactions` (`cycleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_envelopeId` ON `transactions` (`envelopeId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleId` INTEGER NOT NULL, `templateGroupId` TEXT NOT NULL, `name` TEXT NOT NULL, `iconEmoji` TEXT NOT NULL, `amountCentavos` INTEGER NOT NULL, `dueDayOfMonth` INTEGER NOT NULL, `isPaid` INTEGER NOT NULL, `paidAtEpochMs` INTEGER, `notify7d` INTEGER NOT NULL, `notify3d` INTEGER NOT NULL, `notify1d` INTEGER NOT NULL, `notifyOnDue` INTEGER NOT NULL, `notifyDailyOverdue` INTEGER NOT NULL, FOREIGN KEY(`cycleId`) REFERENCES `budget_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_cycleId` ON `bills` (`cycleId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconEmoji` TEXT NOT NULL, `monthlyAmortizationCentavos` INTEGER NOT NULL, `totalInstallments` INTEGER NOT NULL, `paidInstallments` INTEGER NOT NULL, `dueDayOfMonth` INTEGER NOT NULL, `nextDueYearMonth` TEXT NOT NULL, `lastPaidCycleYearMonth` TEXT, `isActive` INTEGER NOT NULL, `notify7d` INTEGER NOT NULL, `notify3d` INTEGER NOT NULL, `notify1d` INTEGER NOT NULL, `notifyOnDue` INTEGER NOT NULL, `notifyDailyOverdue` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `moto_profile` (`id` INTEGER NOT NULL, `modelName` TEXT NOT NULL, `currentOdometerKm` INTEGER NOT NULL, `tankCapacityLiters` REAL NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pms_intervals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskName` TEXT NOT NULL, `iconEmoji` TEXT NOT NULL, `intervalKm` INTEGER NOT NULL, `lastServiceOdometerKm` INTEGER NOT NULL, `lastServiceEpochMs` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `gas_price_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stationId` INTEGER NOT NULL, `stationDisplayName` TEXT NOT NULL, `fuelType` TEXT NOT NULL, `pricePerLiterCentavos` INTEGER NOT NULL, `previousPricePerLiterCentavos` INTEGER, `diffCentavos` INTEGER NOT NULL, `fetchedAtEpochMs` INTEGER NOT NULL, `isManualOverride` INTEGER NOT NULL)")
                db.execSQL(
                    "INSERT INTO `budget_cycles` (`yearMonth`, `totalBudgetCentavos`, `createdAtEpochMs`) " +
                        "VALUES ('2026-11', 5000000, 1700000000000)"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()
        helper.close()
    }

    companion object {
        private const val TEST_DB_NAME = "scan_migration_test.db"
    }
}
