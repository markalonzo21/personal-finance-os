package com.mcalonzo.personalfinanceos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import java.io.IOException
import java.time.YearMonth
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GasPriceRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2026, 11) })
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `syncFromRemote sends realistic User-Agent and records changed price without street address or URL`() = runTest {
        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":79.25}}</script>"""
                            .toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client, targetUrl = "https://gaswatchph.com/quezon-city")
        val result = repo.syncFromRemote()

        assertThat(result.isSuccess).isTrue()
        val userAgent = capturedRequest!!.header("User-Agent")
        assertThat(userAgent).isNotNull()
        assertThat(userAgent).contains("Mozilla/5.0")

        val saved = repo.observeLatestPrice().first()!!
        assertThat(saved.stationId).isEqualTo(874)
        assertThat(saved.stationDisplayName).isEqualTo("Petron Commonwealth D.A")
        assertThat(saved.stationDisplayName).doesNotContain("Ave")
        assertThat(saved.stationDisplayName).doesNotContain("gaswatchph")
        assertThat(saved.fuelType).isEqualTo("Premium 95")
        assertThat(saved.pricePerLiterCentavos).isEqualTo(7925L)
        assertThat(saved.previousPricePerLiterCentavos).isEqualTo(7840L)
        assertThat(saved.diffCentavos).isEqualTo(85L)
        assertThat(saved.isManualOverride).isFalse()
    }

    @Test
    fun `syncFromRemote preserves existing weekly trend diff when remote price is unchanged`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":78.4}}</script>"""
                            .toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client)
        val result = repo.syncFromRemote()

        assertThat(result.isSuccess).isTrue()
        val latest = db.motoDao().getLatestGasPriceOnce()!!
        assertThat(latest.pricePerLiterCentavos).isEqualTo(7840L)
        assertThat(latest.previousPricePerLiterCentavos).isEqualTo(7795L)
        assertThat(latest.diffCentavos).isEqualTo(45L)
        assertThat(db.motoDao().observeGasPriceHistory().first()).hasSize(1)
    }

    @Test
    fun `syncFromRemote preserves cached price on HTTP failure, malformed HTML, empty body, and network IOException`() = runTest {
        val failureClients = listOf(
            // Non-200 HTTP status
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("error".toResponseBody("text/plain".toMediaType()))
                    .build()
            }.build(),
            // 200 with malformed / blocked HTML
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html><body>Cloudflare check</body></html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }.build(),
            // 200 with empty body
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/html".toMediaType()))
                    .build()
            }.build(),
            // Network IOException
            OkHttpClient.Builder().addInterceptor {
                throw IOException("timeout")
            }.build()
        )

        failureClients.forEach { client ->
            val repo = GasPriceRepository(db.motoDao(), client)
            val result = repo.syncFromRemote()

            assertThat(result.isFailure).isTrue()
            val cached = db.motoDao().getLatestGasPriceOnce()!!
            assertThat(cached.pricePerLiterCentavos).isEqualTo(7840L)
            assertThat(cached.diffCentavos).isEqualTo(45L)
            assertThat(db.motoDao().observeGasPriceHistory().first()).hasSize(1)
        }
    }

    @Test
    fun `syncFromRemote sets diffCentavos to 0 when no cached history exists`() = runTest {
        val emptyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":78.4}}</script>"""
                                .toResponseBody("text/html".toMediaType())
                        )
                        .build()
                }
                .build()

            val repo = GasPriceRepository(emptyDb.motoDao(), client)
            val result = repo.syncFromRemote()

            assertThat(result.isSuccess).isTrue()
            val saved = emptyDb.motoDao().getLatestGasPriceOnce()!!
            assertThat(saved.pricePerLiterCentavos).isEqualTo(7840L)
            assertThat(saved.previousPricePerLiterCentavos).isNull()
            assertThat(saved.diffCentavos).isEqualTo(0L)
            assertThat(saved.stationDisplayName).isEqualTo("Petron Commonwealth D.A")
        } finally {
            emptyDb.close()
        }
    }

    @Test
    fun `setManualPriceOverride enforces positive price and subsequent remote sync updates override flag`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":80.5}}</script>"""
                            .toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client)

        assertThat(runCatching { repo.setManualPriceOverride(0L) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { repo.setManualPriceOverride(-100L) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)

        repo.setManualPriceOverride(8050L)
        val manual = db.motoDao().getLatestGasPriceOnce()!!
        assertThat(manual.isManualOverride).isTrue()
        assertThat(manual.pricePerLiterCentavos).isEqualTo(8050L)
        assertThat(manual.previousPricePerLiterCentavos).isEqualTo(7840L)
        assertThat(manual.diffCentavos).isEqualTo(210L)

        // When latest is a manual override, matching remote sync records a fresh non-manual row
        val syncResult = repo.syncFromRemote()
        assertThat(syncResult.isSuccess).isTrue()
        val synced = db.motoDao().getLatestGasPriceOnce()!!
        assertThat(synced.isManualOverride).isFalse()
        assertThat(synced.pricePerLiterCentavos).isEqualTo(8050L)
        assertThat(synced.diffCentavos).isEqualTo(0L)
    }

    @Test
    fun `concurrent syncFromRemote calls run network in parallel and avoid duplicate unchanged inserts`() = runTest {
        val inFlightNetworkBarrier = CyclicBarrier(2)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                inFlightNetworkBarrier.await(5, TimeUnit.SECONDS)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":79.25}}</script>"""
                            .toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client)
        val results = awaitAll(
            async(Dispatchers.Default) { repo.syncFromRemote() },
            async(Dispatchers.Default) { repo.syncFromRemote() }
        )

        assertThat(results.all { it.isSuccess }).isTrue()
        val history = db.motoDao().observeGasPriceHistory().first()
        assertThat(history).hasSize(2)
        assertThat(history[0].pricePerLiterCentavos).isEqualTo(7925L)
        assertThat(history[0].previousPricePerLiterCentavos).isEqualTo(7840L)
        assertThat(history[0].diffCentavos).isEqualTo(85L)
        assertThat(history[1].pricePerLiterCentavos).isEqualTo(7840L)
    }

    @Test
    fun `concurrent syncFromRemote and setManualPriceOverride serialize without stale diff`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":79.25}}</script>"""
                            .toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val delegate = db.motoDao()
        val readCount = AtomicInteger(0)
        val firstReadEntered = CompletableDeferred<Unit>()
        val allowFirstReadToFinish = CompletableDeferred<Unit>()
        val slowDao = object : MotoDao by delegate {
            override suspend fun getLatestGasPriceOnce(): GasPriceHistoryEntity? {
                val readNumber = readCount.incrementAndGet()
                val snapshot = delegate.getLatestGasPriceOnce()
                if (readNumber == 1) {
                    firstReadEntered.complete(Unit)
                    allowFirstReadToFinish.await()
                }
                return snapshot
            }
        }

        val repo = GasPriceRepository(slowDao, client)
        val syncJob = async(Dispatchers.Default) { repo.syncFromRemote() }
        firstReadEntered.await()

        val overrideAttemptStarted = CompletableDeferred<Unit>()
        val overrideJob = async(Dispatchers.Default) {
            overrideAttemptStarted.complete(Unit)
            repo.setManualPriceOverride(8100L)
        }
        overrideAttemptStarted.await()
        Thread.sleep(50)
        assertThat(readCount.get()).isEqualTo(1)

        allowFirstReadToFinish.complete(Unit)
        assertThat(syncJob.await().isSuccess).isTrue()
        overrideJob.await()

        assertThat(readCount.get()).isEqualTo(2)
        val history = delegate.observeGasPriceHistory().first()
        assertThat(history).hasSize(3)

        val manual = history[0]
        assertThat(manual.isManualOverride).isTrue()
        assertThat(manual.pricePerLiterCentavos).isEqualTo(8100L)
        assertThat(manual.previousPricePerLiterCentavos).isEqualTo(7925L)
        assertThat(manual.diffCentavos).isEqualTo(175L)

        val synced = history[1]
        assertThat(synced.isManualOverride).isFalse()
        assertThat(synced.pricePerLiterCentavos).isEqualTo(7925L)
        assertThat(synced.previousPricePerLiterCentavos).isEqualTo(7840L)
        assertThat(synced.diffCentavos).isEqualTo(85L)

        val seeded = history[2]
        assertThat(seeded.pricePerLiterCentavos).isEqualTo(7840L)
        assertThat(seeded.diffCentavos).isEqualTo(45L)
    }

    @Test
    fun `fetchStationsForLocation and updateWatcherConfig switch location, branch, and fuel type`() = runTest {
        var lastRequestedUrl: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                lastRequestedUrl = chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        <script>
                          [{"id": 901, "brand": "shell", "name": "Shell Ayala Ave", "prices": {"unleaded": 69.8, "premium95": 75.5, "diesel": 62.4}}]
                        </script>
                        """.trimIndent().toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client)
        val stationsResult = repo.fetchStationsForLocation("makati")
        assertThat(stationsResult.isSuccess).isTrue()
        assertThat(lastRequestedUrl).isEqualTo("https://gaswatchph.com/makati")
        val stations = stationsResult.getOrThrow()
        assertThat(stations).hasSize(1)
        assertThat(stations.first().stationId).isEqualTo(901)

        val updateResult = repo.updateWatcherConfig(
            com.mcalonzo.personalfinanceos.data.local.GasWatcherConfig(
                locationSlug = "makati",
                locationDisplayName = "Makati",
                stationId = 901,
                stationDisplayName = "Shell Ayala Ave",
                fuelKey = "unleaded",
                fuelDisplayName = "Unleaded"
            )
        )
        assertThat(updateResult.isSuccess).isTrue()
        val saved = repo.observeLatestPrice().first()!!
        assertThat(saved.stationId).isEqualTo(901)
        assertThat(saved.stationDisplayName).isEqualTo("Shell Ayala Ave")
        assertThat(saved.fuelType).isEqualTo("Unleaded")
        assertThat(saved.pricePerLiterCentavos).isEqualTo(6980L)
    }

    @Test
    fun `fetchStationsForLocation filters to allowed brands and allowed fuel types`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        <script>
                          [
                            {"id": 101, "brand": "Petron", "name": "Petron QC", "prices": {"unleaded": 68.5, "premium95": 74.2, "diesel": 60.1}},
                            {"id": 102, "brand": " Shell ", "name": "Shell Katipunan", "prices": {"unleaded": 69.0}},
                            {"id": 105, "brand": "Seaoil", "name": "Seaoil Commonwealth", "prices": {"premium95": 73.1}},
                            {"id": 106, "brand": "Phoenix", "name": "Phoenix Regalado", "prices": {"unleaded": 68.2}},
                            {"id": 103, "brand": "Cleanfuel", "name": "Cleanfuel QC", "prices": {"premium95": 71.0}},
                            {"id": 104, "brand": "Caltex", "name": "Caltex Diesel Only", "prices": {"diesel": 61.5, "kerosene": 70.0}}
                          ]
                        </script>
                        """.trimIndent().toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client)
        val result = repo.fetchStationsForLocation("quezon-city")

        assertThat(result.isSuccess).isTrue()
        val stations = result.getOrThrow()
        assertThat(stations.map { it.stationId }).containsExactly(101, 102, 105, 106).inOrder()
        assertThat(stations[0].pricesCentavos.keys).containsExactly(
            com.mcalonzo.personalfinanceos.data.remote.GasFuelType.UNLEADED,
            com.mcalonzo.personalfinanceos.data.remote.GasFuelType.PREMIUM_95
        )
    }

    @Test
    fun `fetchStationsForLocation fails when no stations match allowlist`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        <script>
                          [{"id": 201, "brand": "Cleanfuel", "name": "Cleanfuel EDSA", "prices": {"diesel": 60.0}}]
                        </script>
                        """.trimIndent().toResponseBody("text/html".toMediaType())
                    )
                    .build()
            }
            .build()

        val repo = GasPriceRepository(db.motoDao(), client)
        val result = repo.fetchStationsForLocation("quezon-city")

        assertThat(result.isFailure).isTrue()
    }
}
