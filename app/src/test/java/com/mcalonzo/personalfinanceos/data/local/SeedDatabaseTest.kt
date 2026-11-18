package com.mcalonzo.personalfinanceos.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedDatabaseTest {
    @Test
    fun `fresh database seeds routine, gas envelope tag, click 160 profile, and petron commonwealth baseline`() = runTest {
        val expectedYearMonth = YearMonth.now(ZoneId.of("Asia/Manila")).toString()
        lateinit var db: AppDatabase
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .addCallback(SeedDatabaseCallback { db })
            .allowMainThreadQueries()
            .build()

        val routine = db.routineDao().getRoutineTemplateOnce()
        val envelopes = db.routineDao().getEnvelopeTemplatesOnce()
        val billTemplates = db.routineDao().getBillTemplatesOnce()
        val loans = db.obligationDao().getActiveLoansOnce()
        val moto = db.motoDao().getMotoProfileOnce()
        val pmsIntervals = db.motoDao().getPmsIntervalsOnce()
        val latestGas = db.motoDao().getLatestGasPriceOnce()

        assertThat(routine?.totalMonthlyBudgetCentavos).isEqualTo(5_000_000L)
        assertThat(routine?.autoRolloverEnabled).isTrue()
        assertThat(envelopes).hasSize(5)
        assertThat(envelopes.count { it.isGasEnvelope }).isEqualTo(1)
        assertThat(billTemplates).isNotEmpty()
        assertThat(loans).hasSize(2)
        loans.forEach { loan ->
            assertThat(loan.nextDueYearMonth).isEqualTo(expectedYearMonth)
        }
        assertThat(moto?.modelName).isEqualTo("Honda Click 160")
        assertThat(moto?.currentOdometerKm).isEqualTo(8450)
        assertThat(moto?.tankCapacityLiters).isEqualTo(5.5)
        assertThat(pmsIntervals).hasSize(3)
        assertThat(latestGas?.stationId).isEqualTo(874)
        assertThat(latestGas?.stationDisplayName).isEqualTo("Petron Commonwealth D.A")
        assertThat(latestGas?.fuelType).isEqualTo("Premium 95")
        assertThat(latestGas?.pricePerLiterCentavos).isEqualTo(7840L)
        assertThat(latestGas?.previousPricePerLiterCentavos).isEqualTo(7795L)
        assertThat(latestGas?.diffCentavos).isEqualTo(45L)
        assertThat(latestGas?.isManualOverride).isFalse()
        db.close()
    }

    @Test
    fun `seeded loans use injected yearMonthProvider when supplied`() = runTest {
        lateinit var db: AppDatabase
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2027, 3) })
            .allowMainThreadQueries()
            .build()

        val loans = db.obligationDao().getActiveLoansOnce()
        assertThat(loans).hasSize(2)
        loans.forEach { loan ->
            assertThat(loan.nextDueYearMonth).isEqualTo("2027-03")
        }
        db.close()
    }

    @Test
    fun `budget_cycles enforces unique index on yearMonth`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 5_000_000L,
                createdAtEpochMs = 1_000L
            )
        )

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.budgetDao().insertCycle(
                    BudgetCycleEntity(
                        yearMonth = "2026-11",
                        totalBudgetCentavos = 6_000_000L,
                        createdAtEpochMs = 2_000L
                    )
                )
            }
        }
        db.close()
    }
}
