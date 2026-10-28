package com.jevon.personalos.subscriptions

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.jevon.personalos.backup.BackupViewModel
import com.jevon.personalos.data.*
import com.jevon.personalos.money.MoneyViewModel
import com.jevon.personalos.today.TodayState
import com.jevon.personalos.ui.*
import kotlinx.coroutines.runBlocking
import java.time.*
import java.util.UUID

/** A nonexported debug host with a separately named disposable database; never Application.database. */
class SubscriptionReviewActivity : ComponentActivity() {
    private val host: SubscriptionReviewHost by viewModels { viewModelFactory { initializer {
        SubscriptionReviewHost(application, intent.getStringExtra("reviewId") ?: UUID.randomUUID().toString())
    } } }
    private val subscriptions: SubscriptionViewModel by viewModels { viewModelFactory { initializer { SubscriptionViewModel(host.database) } } }
    private val money: MoneyViewModel by viewModels { viewModelFactory { initializer { MoneyViewModel(application, SavedStateHandle(), host.database) } } }
    private val backup: BackupViewModel by viewModels { viewModelFactory { initializer { BackupViewModel(host.database, contentResolver, "phase2-review") } } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            var dark by rememberSaveable { mutableStateOf(false) }
            var route by rememberSaveable { mutableStateOf("subscriptions") }
            var request by rememberSaveable { mutableStateOf<String?>(null) }
            val today by remember { host.database.observeToday(ZonedDateTime.now()) }.collectAsState(initial = null)
            PersonalOsTheme(Settings(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false)) {
                AtmosphericBackground { SystemBarAppearance()
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        Text("Isolated subscription review · disposable data", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                        FlowRow {
                            TextButton(onClick = { dark = !dark }) { Text(if (dark) "Review light" else "Review dark") }
                            listOf("subscriptions", "money", "today", "backup").forEach { target -> TextButton(onClick = { route = target }) { Text(target.replaceFirstChar(Char::uppercase)) } }
                        }
                        Box(Modifier.weight(1f)) { when (route) {
                            "subscriptions" -> SubscriptionScreen(subscriptions, request) { request = null }
                            "money" -> MoneyScreen(money, null, {}, { route = "subscriptions" })
                            "today" -> TodayScreen(today?.let { TodayState.Ready(it) } ?: TodayState.Loading, {}, { target ->
                                route = "subscriptions"; request = target.takeIf { it.startsWith("subscription/") }?.substringAfter('/')
                            })
                            "backup" -> BackupScreen(backup)
                        } }
                    }
                }
            }
        }
    }
    internal fun databaseForTest() = host.database
}

class SubscriptionReviewHost(app: Application, id: String) : AndroidViewModel(app) {
    init { require(runCatching { UUID.fromString(id) }.isSuccess) }
    internal val database = Room.databaseBuilder(app, PersonalDatabase::class.java, "phase2-review-$id.db")
        .addMigrations(PersonalDatabase.MIGRATION_1_2, PersonalDatabase.MIGRATION_2_3, PersonalDatabase.MIGRATION_3_4).build()
    init { runBlocking {
        val dao = database.personalDao()
        if (dao.balances().isEmpty()) {
            dao.saveAccount(AccountEntity("review-idr", "Review bank", "IDR", 100_000_000), true)
            dao.saveAccount(AccountEntity("review-usd", "Review dollars", "USD", 100_000), true)
            dao.saveCategory(CategoryEntity("review-category", "Services", "EXPENSE"), true)
            val month = YearMonth.now()
            dao.saveBudget(BudgetEntity("review-budget", "Review monthly plan", "IDR", 5_000_000, month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay()), true)
        }
    } }
    override fun onCleared() { database.close() }
}
