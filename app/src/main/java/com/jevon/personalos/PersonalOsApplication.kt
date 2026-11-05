package com.jevon.personalos

import android.app.Application
import androidx.room.Room
import com.jevon.personalos.data.PersonalDatabase
import com.jevon.personalos.data.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch

class PersonalOsApplication : Application() {
    val alerts by lazy { com.jevon.personalos.alerts.LocalAlerts(this,database) }
    private val alertScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO)
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        alerts.createChannels()
        alertScope.launchAlerts()
    }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun kotlinx.coroutines.CoroutineScope.launchAlerts() = launch {
        database.invalidationTracker.createFlow("events","reminders","reminder_completions","alert_preferences","subscriptions","subscription_payments")
            .debounce(150).catch { alerts.reconcile() }.collect { alerts.reconcile() }
    }
    val database by lazy {
        Room.databaseBuilder(this, PersonalDatabase::class.java, "personal-os.db").addMigrations(PersonalDatabase.MIGRATION_1_2, PersonalDatabase.MIGRATION_2_3, PersonalDatabase.MIGRATION_3_4, PersonalDatabase.MIGRATION_4_5).build()
    }
    val settings by lazy { SettingsStore(this) }
}
