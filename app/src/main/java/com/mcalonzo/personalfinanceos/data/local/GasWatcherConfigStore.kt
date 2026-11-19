package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import com.mcalonzo.personalfinanceos.data.remote.GasFuelType
import com.mcalonzo.personalfinanceos.data.remote.GasWatchAllowlist
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GasWatcherConfig(
    val locationSlug: String = GasPriceRepository.DEFAULT_LOCATION_SLUG,
    val locationDisplayName: String = GasPriceRepository.DEFAULT_LOCATION_NAME,
    val stationId: Int = GasPriceRepository.STATION_ID,
    val stationDisplayName: String = GasPriceRepository.STATION_DISPLAY_NAME,
    val fuelKey: String = GasFuelType.PREMIUM_95.jsonKey,
    val fuelDisplayName: String = GasFuelType.PREMIUM_95.displayName
)

interface GasWatcherConfigStore {
    fun observeConfig(): Flow<GasWatcherConfig>
    fun getConfig(): GasWatcherConfig
    fun saveConfig(config: GasWatcherConfig)
}

class InMemoryGasWatcherConfigStore(
    initial: GasWatcherConfig = GasWatcherConfig()
) : GasWatcherConfigStore {
    private val state = MutableStateFlow(initial)

    override fun observeConfig(): Flow<GasWatcherConfig> = state.asStateFlow()

    override fun getConfig(): GasWatcherConfig = state.value

    override fun saveConfig(config: GasWatcherConfig) {
        state.value = config
    }
}

@Singleton
class SharedPreferencesGasWatcherConfigStore @Inject constructor(
    @ApplicationContext context: Context
) : GasWatcherConfigStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readFromPrefs())

    override fun observeConfig(): Flow<GasWatcherConfig> = state.asStateFlow()

    override fun getConfig(): GasWatcherConfig = state.value

    override fun saveConfig(config: GasWatcherConfig) {
        prefs.edit()
            .putString(KEY_LOCATION_SLUG, config.locationSlug)
            .putString(KEY_LOCATION_NAME, config.locationDisplayName)
            .putInt(KEY_STATION_ID, config.stationId)
            .putString(KEY_STATION_NAME, config.stationDisplayName)
            .putString(KEY_FUEL_KEY, config.fuelKey)
            .putString(KEY_FUEL_NAME, config.fuelDisplayName)
            .apply()
        state.value = config
    }

    private fun readFromPrefs(): GasWatcherConfig {
        val rawFuelKey = prefs.getString(KEY_FUEL_KEY, null)
            ?.takeIf { it.isNotBlank() }
        val resolvedFuel = rawFuelKey
            ?.let { GasWatchAllowlist.coerceFuelType(GasFuelType.fromKeyOrLabel(it)) }
            ?: GasFuelType.PREMIUM_95
        return GasWatcherConfig(
            locationSlug = prefs.getString(KEY_LOCATION_SLUG, null)
                ?.takeIf { it.isNotBlank() }
                ?: GasPriceRepository.DEFAULT_LOCATION_SLUG,
            locationDisplayName = prefs.getString(KEY_LOCATION_NAME, null)
                ?.takeIf { it.isNotBlank() }
                ?: GasPriceRepository.DEFAULT_LOCATION_NAME,
            stationId = prefs.getInt(KEY_STATION_ID, GasPriceRepository.STATION_ID),
            stationDisplayName = prefs.getString(KEY_STATION_NAME, null)
                ?.takeIf { it.isNotBlank() }
                ?: GasPriceRepository.STATION_DISPLAY_NAME,
            fuelKey = resolvedFuel.jsonKey,
            fuelDisplayName = resolvedFuel.displayName
        )
    }

    private companion object {
        const val PREFS_NAME = "gas_watcher_prefs"
        const val KEY_LOCATION_SLUG = "location_slug"
        const val KEY_LOCATION_NAME = "location_name"
        const val KEY_STATION_ID = "station_id"
        const val KEY_STATION_NAME = "station_name"
        const val KEY_FUEL_KEY = "fuel_key"
        const val KEY_FUEL_NAME = "fuel_name"
    }
}
