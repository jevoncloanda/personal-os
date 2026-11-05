package com.jevon.personalos.library

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.jevon.personalos.data.*
import com.jevon.personalos.today.TodayState
import com.jevon.personalos.ui.*
import java.time.ZonedDateTime
import java.util.UUID

/** Disposable on-device review host; never opens the personal database. */
class LibraryReviewActivity : ComponentActivity() {
    private val host: LibraryReviewHost by viewModels { viewModelFactory { initializer { LibraryReviewHost(application, requireNotNull(intent.getStringExtra("reviewId"))) } } }
    private val model: LibraryViewModel by viewModels { viewModelFactory { initializer { LibraryViewModel(host.database, createSavedStateHandle()) } } }
    internal fun databaseForTest() = host.database
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by rememberSaveable { mutableStateOf(false) }
            var todayRoute by rememberSaveable { mutableStateOf(false) }
            var request by rememberSaveable { mutableStateOf<String?>(null) }
            val today by remember { host.database.observeToday(ZonedDateTime.now()) }.collectAsState(initial = null)
            PersonalOsTheme(Settings(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false)) {
                AtmosphericBackground { SystemBarAppearance()
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        Row {
                            TextButton(onClick = { dark = !dark }) { Text(if (dark) "Review light" else "Review dark") }
                            TextButton(onClick = { todayRoute = !todayRoute }) { Text(if (todayRoute) "Review Library" else "Review Today") }
                        }
                        Box(Modifier.weight(1f)) {
                            if (todayRoute) TodayScreen(today?.let { TodayState.Ready(it) } ?: TodayState.Loading, {}, { path -> request = android.net.Uri.decode(path.substringAfter('/')); todayRoute = false }) {}
                            else LibraryScreen(model, request) { request = null }
                        }
                    }
                }
            }
        }
    }
}
class LibraryReviewHost(app: Application, id: String) : AndroidViewModel(app) {
    init { UUID.fromString(id) }
    val database = Room.databaseBuilder(app, PersonalDatabase::class.java, "phase4-review-$id.db")
        .addMigrations(PersonalDatabase.MIGRATION_1_2, PersonalDatabase.MIGRATION_2_3, PersonalDatabase.MIGRATION_3_4, PersonalDatabase.MIGRATION_4_5).build()
    override fun onCleared() { database.close() }
}
