package com.jevon.personalos.backup

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.jevon.personalos.data.*
import com.jevon.personalos.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Nonexported debug-only host. All data is disposable and separate from Application.database. */
class BackupReviewActivity : ComponentActivity() {
    private val host: BackupReviewHost by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            var dark by remember { mutableStateOf(false) }
            PersonalOsTheme(Settings(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false)) {
                PorosBackground {
                    SystemBarAppearance()
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        Text("Isolated backup review · disposable data", Modifier.padding(20.dp), style = MaterialTheme.typography.titleSmall)
                        FlowRow {
                            TextButton(onClick = { dark = !dark }) { Text(if (dark) "Review light theme" else "Review dark theme") }
                            TextButton(onClick = { host.clearDisposable() }) { Text("Use empty test data") }
                        }
                        BackupScreen(host.backup)
                    }
                }
            }
        }
    }
    internal fun databaseForTest() = host.database
}

class BackupReviewHost(application: Application) : AndroidViewModel(application) {
    internal val database = Room.inMemoryDatabaseBuilder(application, PersonalDatabase::class.java).build()
    internal val backup = BackupViewModel(database, application.contentResolver, "isolated-review")
    init { runBlocking { BackupStore(database).replace(PreparedBackup(backupReviewData(), "", "")) } }
    internal fun clearDisposable() { viewModelScope.launch { BackupStore(database).replace(PreparedBackup(BackupData(), "", "")) } }
    override fun onCleared() { database.close() }
}
