package com.mcalonzo.personalfinanceos.data.repository

import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import com.mcalonzo.personalfinanceos.data.remote.GasWatchParser
import com.mcalonzo.personalfinanceos.domain.calculator.MotoCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Coordinates GasWatchPH scraping for Petron Fairview (station 621), manual price overrides,
 * and Room price-history caching while preserving prior trend diffs on unchanged or failed syncs.
 */
@Singleton
class GasPriceRepository internal constructor(
    private val motoDao: MotoDao,
    private val okHttpClient: OkHttpClient,
    private val targetUrl: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val clockMs: () -> Long
) {
    private val dbMutex = Mutex()

    @Inject
    constructor(
        motoDao: MotoDao,
        okHttpClient: OkHttpClient
    ) : this(
        motoDao = motoDao,
        okHttpClient = okHttpClient,
        targetUrl = DEFAULT_GASWATCH_URL,
        ioDispatcher = Dispatchers.IO,
        clockMs = System::currentTimeMillis
    )

    constructor(
        motoDao: MotoDao,
        okHttpClient: OkHttpClient,
        targetUrl: String
    ) : this(
        motoDao = motoDao,
        okHttpClient = okHttpClient,
        targetUrl = targetUrl,
        ioDispatcher = Dispatchers.IO,
        clockMs = System::currentTimeMillis
    )

    fun observeLatestPrice(): Flow<GasPriceHistoryEntity?> = motoDao.observeLatestGasPrice()

    fun observePriceHistory(): Flow<List<GasPriceHistoryEntity>> = motoDao.observeGasPriceHistory()

    suspend fun syncFromRemote(): Result<GasPriceHistoryEntity> {
        val html = try {
            withContext(ioDispatcher) {
                val request = Request.Builder()
                    .url(targetUrl)
                    .get()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure<String>(
                            IllegalStateException("GasWatchPH HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        return@withContext Result.failure<String>(
                            IllegalStateException("GasWatchPH returned an empty body")
                        )
                    }
                    Result.success(body)
                }
            }.getOrElse { error ->
                return Result.failure(error)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val parsed = GasWatchParser.parseStationPremium95(html, targetStationId = STATION_ID)
            ?: return Result.failure(
                IllegalStateException("Station $STATION_ID Premium 95 price unavailable")
            )

        return dbMutex.withLock {
            val latest = motoDao.getLatestGasPriceOnce()
            if (latest != null &&
                !latest.isManualOverride &&
                latest.pricePerLiterCentavos == parsed.premium95Centavos
            ) {
                // Preserve the most recent price movement badge instead of overwriting diff with 0L.
                return@withLock Result.success(latest)
            }

            val previousCentavos = latest?.pricePerLiterCentavos
            val diffCentavos = MotoCalculator.calculatePriceDiff(
                newPriceCentavos = parsed.premium95Centavos,
                previousPriceCentavos = previousCentavos
            )
            val entry = GasPriceHistoryEntity(
                stationId = STATION_ID,
                stationDisplayName = STATION_DISPLAY_NAME,
                fuelType = FUEL_TYPE_PREMIUM_95,
                pricePerLiterCentavos = parsed.premium95Centavos,
                previousPricePerLiterCentavos = previousCentavos,
                diffCentavos = diffCentavos,
                fetchedAtEpochMs = clockMs(),
                isManualOverride = false
            )
            val insertedId = motoDao.insertGasPrice(entry)
            Result.success(entry.copy(id = insertedId))
        }
    }

    suspend fun setManualPriceOverride(priceCentavos: Long) {
        require(priceCentavos > 0L) { "Manual fuel price override must be positive" }
        dbMutex.withLock {
            val latest = motoDao.getLatestGasPriceOnce()
            val previousCentavos = latest?.pricePerLiterCentavos
            val diffCentavos = MotoCalculator.calculatePriceDiff(
                newPriceCentavos = priceCentavos,
                previousPriceCentavos = previousCentavos
            )
            motoDao.insertGasPrice(
                GasPriceHistoryEntity(
                    stationId = STATION_ID,
                    stationDisplayName = STATION_DISPLAY_NAME,
                    fuelType = FUEL_TYPE_PREMIUM_95,
                    pricePerLiterCentavos = priceCentavos,
                    previousPricePerLiterCentavos = previousCentavos,
                    diffCentavos = diffCentavos,
                    fetchedAtEpochMs = clockMs(),
                    isManualOverride = true
                )
            )
        }
    }

    companion object {
        const val DEFAULT_GASWATCH_URL = "https://gaswatchph.com/quezon-city"
        const val STATION_ID = 621
        const val STATION_DISPLAY_NAME = "Petron Fairview"
        const val FUEL_TYPE_PREMIUM_95 = "Premium 95"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
