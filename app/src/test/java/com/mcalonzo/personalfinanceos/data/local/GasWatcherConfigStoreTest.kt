package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GasWatcherConfigStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("gas_watcher_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `readFromPrefs coerces disallowed legacy fuelKey to Premium 95`() {
        context.getSharedPreferences("gas_watcher_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fuel_key", "diesel")
            .putString("fuel_name", "Diesel")
            .commit()

        val store = SharedPreferencesGasWatcherConfigStore(context)
        val config = store.getConfig()

        assertThat(config.fuelKey).isEqualTo("premium95")
        assertThat(config.fuelDisplayName).isEqualTo("Premium 95")
    }
}
