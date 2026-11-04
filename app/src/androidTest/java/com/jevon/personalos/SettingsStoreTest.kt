package com.jevon.personalos

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jevon.personalos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class SettingsStoreTest {
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "phase35-settings-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
    @Test fun preferencesPersistAcrossStoreRecreationWithoutLosingAppearance() = runBlocking {
        val directory = directory()
        val file = directory.resolve("settings.preferences_pb")
        suspend fun session(block: suspend (SettingsStore) -> Unit) {
            val job = SupervisorJob()
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            try { block(SettingsStore(store)) } finally { job.cancelAndJoin() }
        }
        try {
            session { store ->
                assertEquals(Settings(), store.settings.first())
                store.setTheme(ThemeMode.DARK); store.setDynamicColor(false)
                store.setPreferredCurrency("USD"); store.setTodayBudget("explicit-aud")
            }
            session { store ->
                assertEquals(Settings(ThemeMode.DARK, false, "USD", "explicit-aud"), store.settings.first())
                store.setPreferredCurrency("IDR")
                assertEquals("explicit-aud", store.settings.first().todayBudgetId)
                store.setTodayBudget(null)
                assertNull(store.settings.first().todayBudgetId)
            }
            session { assertEquals(Settings(ThemeMode.DARK, false), it.settings.first()) }
        } finally { file.delete(); directory.delete() }
    }

    @Test fun legacyAndUnsupportedStoredCurrencyUseIdrWithoutResettingAppearance() = runBlocking {
        val directory = directory()
        val file = directory.resolve("settings.preferences_pb")
        val job = SupervisorJob()
        val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        try {
            val settings = SettingsStore(data)
            settings.setTheme(ThemeMode.LIGHT)
            assertEquals(Settings(ThemeMode.LIGHT), settings.settings.first())
            data.edit { it[stringPreferencesKey("preferred_currency")] = "invalid" }
            assertEquals(Settings(ThemeMode.LIGHT), settings.settings.first())
            try { settings.setPreferredCurrency("invalid"); fail("Unsupported currency accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(Settings(ThemeMode.LIGHT), settings.settings.first())
        } finally { job.cancelAndJoin(); file.delete(); directory.delete() }
    }
}
