package com.jevon.personalos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class Settings(val theme: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)

class SettingsStore(context: Context) {
    private val store = context.settingsDataStore
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    val settings = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { prefs ->
        Settings(ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.SYSTEM, prefs[dynamicKey] ?: true)
    }
    suspend fun setTheme(theme: ThemeMode) { store.edit { it[themeKey] = theme.name } }
    suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[dynamicKey] = enabled } }
}
