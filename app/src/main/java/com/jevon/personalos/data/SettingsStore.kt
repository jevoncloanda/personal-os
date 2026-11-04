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
data class Settings(val theme: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true,
    val preferredCurrency: String = DEFAULT_CURRENCY, val todayBudgetId: String? = null)

class SettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val currencyKey = stringPreferencesKey("preferred_currency")
    private val budgetKey = stringPreferencesKey("today_budget_id")
    val settings = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { prefs ->
        Settings(ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.SYSTEM, prefs[dynamicKey] ?: true,
            prefs[currencyKey]?.takeIf { runCatching { currencyDigits(it) }.isSuccess } ?: DEFAULT_CURRENCY,
            prefs[budgetKey]?.takeIf { it.isNotBlank() })
    }
    suspend fun setTheme(theme: ThemeMode) { store.edit { it[themeKey] = theme.name } }
    suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[dynamicKey] = enabled } }
    suspend fun setPreferredCurrency(code: String) {
        currencyDigits(code)
        store.edit { it[currencyKey] = code }
    }
    suspend fun setTodayBudget(id: String?) {
        require(id == null || id.isNotBlank())
        store.edit { if (id == null) it.remove(budgetKey) else it[budgetKey] = id }
    }
}
