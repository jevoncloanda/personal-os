package com.jevon.personalos.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.quickExpenseDataStore by preferencesDataStore("quick_expense")

class QuickExpenseStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.quickExpenseDataStore)
    private val key = stringPreferencesKey("configuration")
    private val gson = Gson()
    internal val mutex = Mutex()
    val state = dataStore.data.map { preferences ->
        preferences[key]?.let { gson.fromJson(it, QuickExpenseState::class.java) }
            ?: QuickExpenseState()
    }

    internal suspend fun read(): QuickExpenseState = state.first()

    // Callers hold mutex across journal + Room + receipt writes as well as configuration edits.
    internal suspend fun write(value: QuickExpenseState) {
        dataStore.edit { it[key] = gson.toJson(value) }
    }

    suspend fun update(change: (QuickExpenseState) -> QuickExpenseState) = mutex.withLock {
        write(change(read()))
    }
}
