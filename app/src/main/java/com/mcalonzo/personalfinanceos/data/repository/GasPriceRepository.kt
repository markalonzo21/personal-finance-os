package com.mcalonzo.personalfinanceos.data.repository

import com.mcalonzo.personalfinanceos.data.local.GasWatcherConfig
import com.mcalonzo.personalfinanceos.data.local.GasWatcherConfigStore
import com.mcalonzo.personalfinanceos.data.local.InMemoryGasWatcherConfigStore
import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.entity.GasPriceHistoryEntity
import com.mcalonzo.personalfinanceos.data.remote.GasFuelType
import com.mcalonzo.personalfinanceos.data.remote.GasWatchAllowlist
import com.mcalonzo.personalfinanceos.data.remote.GasWatchParser
import com.mcalonzo.personalfinanceos.data.remote.ParsedGasStation
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
 * Coordinates GasWatchPH scraping for the configured location, branch, and fuel type,
 * manual price overrides, and Room price-history caching while preserving prior trend diffs
 * on unchanged or failed syncs.
 */
@Singleton
class GasPriceRepository internal constructor(
    private val motoDao: MotoDao,
    private val okHttpClient: OkHttpClient,
    private val configStore: GasWatcherConfigStore,
    private val targetUrlOverride: String?,
    private val ioDispatcher: CoroutineDispatcher,
    private val clockMs: () -> Long
) {
    private val dbMutex = Mutex()

    @Inject
    constructor(
        motoDao: MotoDao,
        okHttpClient: OkHttpClient,
        configStore: GasWatcherConfigStore
    ) : this(
        motoDao = motoDao,
        okHttpClient = okHttpClient,
        configStore = configStore,
        targetUrlOverride = null,
        ioDispatcher = Dispatchers.IO,
        clockMs = System::currentTimeMillis
    )

    constructor(
        motoDao: MotoDao,
        okHttpClient: OkHttpClient
    ) : this(
        motoDao = motoDao,
        okHttpClient = okHttpClient,
        configStore = InMemoryGasWatcherConfigStore(),
        targetUrlOverride = null,
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
        configStore = InMemoryGasWatcherConfigStore(),
        targetUrlOverride = targetUrl,
        ioDispatcher = Dispatchers.IO,
        clockMs = System::currentTimeMillis
    )

    fun observeLatestPrice(): Flow<GasPriceHistoryEntity?> = motoDao.observeLatestGasPrice()

    fun observePriceHistory(): Flow<List<GasPriceHistoryEntity>> = motoDao.observeGasPriceHistory()

    fun observeConfig(): Flow<GasWatcherConfig> = configStore.observeConfig()

    fun getConfig(): GasWatcherConfig = configStore.getConfig()

    suspend fun fetchStationsForLocation(locationSlug: String): Result<List<ParsedGasStation>> {
        val url = targetUrlOverride ?: buildGasWatchUrl(locationSlug)
        val html = fetchHtml(url).getOrElse { return Result.failure(it) }
        val stations = GasWatchAllowlist.filterStations(GasWatchParser.parseStations(html))
        if (stations.isEmpty()) {
            return Result.failure(IllegalStateException("No matching stations found for $locationSlug"))
        }
        return Result.success(stations)
    }

    suspend fun updateWatcherConfig(config: GasWatcherConfig): Result<GasPriceHistoryEntity> {
        configStore.saveConfig(config)
        return syncFromRemote()
    }

    suspend fun syncFromRemote(): Result<GasPriceHistoryEntity> {
        val config = configStore.getConfig()
        val url = targetUrlOverride ?: buildGasWatchUrl(config.locationSlug)
        val html = fetchHtml(url).getOrElse { return Result.failure(it) }

        val fuelType = GasFuelType.fromKeyOrLabel(config.fuelKey)
        val parsed = GasWatchParser.parseStationPrice(
            html = html,
            targetStationId = config.stationId,
            fuelType = fuelType
        ) ?: return Result.failure(
            IllegalStateException("Station ${config.stationId} ${config.fuelDisplayName} price unavailable")
        )

        return dbMutex.withLock {
            val latest = motoDao.getLatestGasPriceOnce()
            if (latest != null &&
                !latest.isManualOverride &&
                latest.stationId == config.stationId &&
                latest.stationDisplayName == config.stationDisplayName &&
                latest.fuelType == config.fuelDisplayName &&
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
                stationId = config.stationId,
                stationDisplayName = config.stationDisplayName,
                fuelType = config.fuelDisplayName,
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
        val config = configStore.getConfig()
        dbMutex.withLock {
            val latest = motoDao.getLatestGasPriceOnce()
            val previousCentavos = latest?.pricePerLiterCentavos
            val diffCentavos = MotoCalculator.calculatePriceDiff(
                newPriceCentavos = priceCentavos,
                previousPriceCentavos = previousCentavos
            )
            motoDao.insertGasPrice(
                GasPriceHistoryEntity(
                    stationId = config.stationId,
                    stationDisplayName = config.stationDisplayName,
                    fuelType = config.fuelDisplayName,
                    pricePerLiterCentavos = priceCentavos,
                    previousPricePerLiterCentavos = previousCentavos,
                    diffCentavos = diffCentavos,
                    fetchedAtEpochMs = clockMs(),
                    isManualOverride = true
                )
            )
        }
    }

    private suspend fun fetchHtml(url: String): Result<String> {
        return try {
            withContext(ioDispatcher) {
                val request = Request.Builder()
                    .url(url)
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
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_LOCATION_SLUG = "quezon-city"
        const val DEFAULT_LOCATION_NAME = "Quezon City"
        const val DEFAULT_GASWATCH_URL = "https://gaswatchph.com/$DEFAULT_LOCATION_SLUG"
        const val STATION_ID = 874
        const val STATION_DISPLAY_NAME = "Petron Commonwealth D.A"
        const val FUEL_TYPE_PREMIUM_95 = "Premium 95"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun buildGasWatchUrl(locationSlug: String): String {
            val cleanSlug = locationSlug.trim().trim('/').ifEmpty { DEFAULT_LOCATION_SLUG }
            return "https://gaswatchph.com/$cleanSlug"
        }
    }
}
