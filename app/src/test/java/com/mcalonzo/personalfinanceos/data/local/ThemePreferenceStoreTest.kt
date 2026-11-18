package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemePreferenceStoreTest {
    @Test
    fun `fresh SharedPreferencesThemePreferenceStore defaults to SYSTEM and persists updates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesThemePreferenceStore(context)

        assertThat(store.getThemeMode()).isEqualTo(ThemeMode.SYSTEM)
        assertThat(store.observeThemeMode().first()).isEqualTo(ThemeMode.SYSTEM)

        store.setThemeMode(ThemeMode.LIGHT)
        assertThat(store.getThemeMode()).isEqualTo(ThemeMode.LIGHT)

        val reloaded = SharedPreferencesThemePreferenceStore(context)
        assertThat(reloaded.getThemeMode()).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun `unknown stored preference value falls back to SYSTEM`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "CORRUPTED_VALUE")
            .commit()

        val store = SharedPreferencesThemePreferenceStore(context)
        assertThat(store.getThemeMode()).isEqualTo(ThemeMode.SYSTEM)
    }
}
