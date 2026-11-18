package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

interface ThemePreferenceStore {
    fun observeThemeMode(): Flow<ThemeMode>
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
}

class InMemoryThemePreferenceStore(
    initial: ThemeMode = ThemeMode.SYSTEM
) : ThemePreferenceStore {
    private val state = MutableStateFlow(initial)

    override fun observeThemeMode(): Flow<ThemeMode> = state.asStateFlow()

    override fun getThemeMode(): ThemeMode = state.value

    override fun setThemeMode(mode: ThemeMode) {
        state.value = mode
    }
}

@Singleton
class SharedPreferencesThemePreferenceStore @Inject constructor(
    @ApplicationContext context: Context
) : ThemePreferenceStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readFromPrefs())

    override fun observeThemeMode(): Flow<ThemeMode> = state.asStateFlow()

    override fun getThemeMode(): ThemeMode = state.value

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
        state.value = mode
    }

    private fun readFromPrefs(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val PREFS_NAME = "theme_preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
