package com.mcalonzo.personalfinanceos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedDatabaseTest {
    @Test
    fun `fresh database seeds routine, gas envelope tag, click 160 profile, and petron fairview baseline`() = runTest {
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
        assertThat(loans).isNotEmpty()
        assertThat(moto?.modelName).isEqualTo("Honda Click 160")
        assertThat(moto?.currentOdometerKm).isEqualTo(8450)
        assertThat(moto?.tankCapacityLiters).isEqualTo(5.5)
        assertThat(pmsIntervals).hasSize(3)
        assertThat(latestGas?.stationId).isEqualTo(621)
        assertThat(latestGas?.stationDisplayName).isEqualTo("Petron Fairview")
        assertThat(latestGas?.fuelType).isEqualTo("Premium 95")
        assertThat(latestGas?.pricePerLiterCentavos).isEqualTo(7840L)
        assertThat(latestGas?.previousPricePerLiterCentavos).isEqualTo(7795L)
        assertThat(latestGas?.diffCentavos).isEqualTo(45L)
        assertThat(latestGas?.isManualOverride).isFalse()
        db.close()
    }
}
