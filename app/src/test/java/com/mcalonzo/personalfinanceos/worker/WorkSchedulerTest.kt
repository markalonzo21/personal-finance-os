package com.mcalonzo.personalfinanceos.worker

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkSchedulerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2026, 11) })
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
        db.close()
    }

    @Test
    fun `calculates initial delay to upcoming Saturday 12pm Asia Manila including exact noon`() {
        val manila = ZoneId.of("Asia/Manila")

        val fridayMorning = ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(fridayMorning))
            .isEqualTo(Duration.ofHours(27))

        val saturdayMorning = ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(saturdayMorning))
            .isEqualTo(Duration.ofHours(3))

        val oneNanoBeforeNoon = ZonedDateTime.of(2026, 11, 14, 11, 59, 59, 999_999_999, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(oneNanoBeforeNoon))
            .isEqualTo(Duration.ofNanos(1))

        val exactSaturdayNoon = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(exactSaturdayNoon))
            .isEqualTo(Duration.ofDays(7))

        val saturdayOneSecondPastNoon = ZonedDateTime.of(2026, 11, 14, 12, 0, 1, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(saturdayOneSecondPastNoon))
            .isEqualTo(Duration.ofDays(7).minusSeconds(1))

        val saturdayAfternoon = ZonedDateTime.of(2026, 11, 14, 15, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(saturdayAfternoon))
            .isEqualTo(Duration.ofDays(7).minusHours(3))

        val sundayMorning = ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextSaturdayNoonDelay(sundayMorning))
            .isEqualTo(Duration.ofDays(6).plusHours(2))

        val utcFriday = ZonedDateTime.of(2026, 11, 13, 18, 0, 0, 0, ZoneId.of("UTC"))
        assertThat(WorkScheduler.nextSaturdayNoonDelay(utcFriday))
            .isEqualTo(Duration.ofHours(10))

        // 2026-11-13T20:00-08:00 is already Saturday 12:00:00 in Asia/Manila -> rolls to next Saturday
        val acrossDateBoundaryExactManilaNoon = ZonedDateTime.of(
            2026, 11, 13, 20, 0, 0, 0, ZoneOffset.ofHours(-8)
        )
        assertThat(WorkScheduler.nextSaturdayNoonDelay(acrossDateBoundaryExactManilaNoon))
            .isEqualTo(Duration.ofDays(7))

        // 2026-11-13T21:00-08:00 is Saturday 13:00:00 in Asia/Manila -> 6 days 23 hours
        val acrossDateBoundaryPastManilaNoon = ZonedDateTime.of(
            2026, 11, 13, 21, 0, 0, 0, ZoneOffset.ofHours(-8)
        )
        assertThat(WorkScheduler.nextSaturdayNoonDelay(acrossDateBoundaryPastManilaNoon))
            .isEqualTo(Duration.ofDays(7).minusHours(1))
    }

    @Test
    fun `enqueueWeeklyGasWatcher and enqueueColdStartGasSync register unique work with network constraint`() {
        val workManager = WorkManager.getInstance(context)

        WorkScheduler.enqueueWeeklyGasWatcher(workManager)
        WorkScheduler.enqueueColdStartGasSync(workManager)

        val weeklyInfos = workManager
            .getWorkInfosForUniqueWork(WorkScheduler.WEEKLY_GAS_SYNC_WORK_NAME)
            .get()
        assertThat(weeklyInfos).hasSize(1)
        val weeklyInfo = weeklyInfos.first()
        assertThat(weeklyInfo.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(weeklyInfo.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(weeklyInfo.periodicityInfo).isNotNull()
        assertThat(weeklyInfo.periodicityInfo!!.repeatIntervalMillis)
            .isEqualTo(Duration.ofDays(7).toMillis())
        assertThat(weeklyInfo.initialDelayMillis).isGreaterThan(0L)
        assertThat(weeklyInfo.initialDelayMillis).isAtMost(Duration.ofDays(7).toMillis())

        val coldStartInfos = workManager
            .getWorkInfosForUniqueWork(WorkScheduler.COLD_START_GAS_SYNC_WORK_NAME)
            .get()
        assertThat(coldStartInfos).hasSize(1)
        val coldStartInfo = coldStartInfos.first()
        assertThat(coldStartInfo.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(coldStartInfo.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(coldStartInfo.periodicityInfo).isNull()

        // ExistingPeriodicWorkPolicy.KEEP / ExistingWorkPolicy.KEEP preserve existing work IDs
        WorkScheduler.enqueueWeeklyGasWatcher(workManager)
        WorkScheduler.enqueueColdStartGasSync(workManager)
        assertThat(
            workManager.getWorkInfosForUniqueWork(WorkScheduler.WEEKLY_GAS_SYNC_WORK_NAME).get().single().id
        ).isEqualTo(weeklyInfo.id)
        assertThat(
            workManager.getWorkInfosForUniqueWork(WorkScheduler.COLD_START_GAS_SYNC_WORK_NAME).get().single().id
        ).isEqualTo(coldStartInfo.id)
    }

    @Test
    fun `GasPriceSyncWorker returns success on valid scrape and retry preserving cache on failure`() = runTest {
        val successClient = fakeHttpClient(
            code = 200,
            body = """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":79.15}}</script>"""
        )
        val successWorker = buildWorker(GasPriceRepository(db.motoDao(), successClient))

        val successResult = successWorker.doWork()
        assertThat(successResult).isEqualTo(ListenableWorker.Result.success())
        val updated = db.motoDao().getLatestGasPriceOnce()!!
        assertThat(updated.pricePerLiterCentavos).isEqualTo(7915L)
        assertThat(updated.diffCentavos).isEqualTo(75L)

        val failClient = fakeHttpClient(code = 503, body = "<html>blocked</html>")
        val failWorker = buildWorker(GasPriceRepository(db.motoDao(), failClient))

        val failResult = failWorker.doWork()
        assertThat(failResult).isEqualTo(ListenableWorker.Result.retry())
        val cached = db.motoDao().getLatestGasPriceOnce()!!
        assertThat(cached.pricePerLiterCentavos).isEqualTo(7915L)
        assertThat(cached.diffCentavos).isEqualTo(75L)
    }

    private fun buildWorker(repository: GasPriceRepository): GasPriceSyncWorker {
        return TestListenableWorkerBuilder<GasPriceSyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker = GasPriceSyncWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        gasPriceRepository = repository
                    )
                }
            )
            .build()
    }

    private fun fakeHttpClient(code: Int, body: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
    }
}
