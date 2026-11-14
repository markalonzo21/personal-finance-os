package com.mcalonzo.personalfinanceos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import java.io.IOException
import java.time.YearMonth
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
                        """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":79.25}}</script>"""
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
        assertThat(saved.stationId).isEqualTo(621)
        assertThat(saved.stationDisplayName).isEqualTo("Petron Fairview")
        assertThat(saved.stationDisplayName).doesNotContain("Commonwealth")
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
                        """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":78.4}}</script>"""
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
                            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":78.4}}</script>"""
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
            assertThat(saved.stationDisplayName).isEqualTo("Petron Fairview")
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
                        """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":80.5}}</script>"""
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
}
