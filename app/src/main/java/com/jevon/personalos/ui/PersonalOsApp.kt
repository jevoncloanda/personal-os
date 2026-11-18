package com.jevon.personalos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.jevon.personalos.data.*
import com.jevon.personalos.today.*
import com.jevon.personalos.money.MoneyViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.BuildConfig
import com.jevon.personalos.backup.BackupViewModel
import com.jevon.personalos.subscriptions.SubscriptionViewModel
import com.jevon.personalos.schedule.RecordTarget
import com.jevon.personalos.schedule.ScheduleViewModel
import kotlinx.coroutines.launch

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", AppIcons.Today),
    MONEY("money", "Money", AppIcons.AccountBalanceWallet),
    SCHEDULE("schedule", "Schedule", AppIcons.CalendarMonth),
    LIBRARY("library", "Library", AppIcons.MenuBook),
    SETTINGS("settings", "Settings", AppIcons.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalOsApp(
    model: TodayViewModel,
    settings: Settings,
    externalTarget: RecordTarget? = null,
    externalDestination: String? = null,
    externalHandled: () -> Unit = {},
) {
    SystemBarAppearance()
    val money: MoneyViewModel = viewModel()
    val application = LocalContext.current.applicationContext as PersonalOsApplication
    val library: com.jevon.personalos.library.LibraryViewModel = viewModel(factory = viewModelFactory { initializer {
        com.jevon.personalos.library.LibraryViewModel(application.database, createSavedStateHandle())
    } })
    val subscriptions: SubscriptionViewModel = viewModel(factory = viewModelFactory { initializer { SubscriptionViewModel(application.database) } })
    val schedule:ScheduleViewModel=viewModel(factory=viewModelFactory{initializer{ScheduleViewModel(application.database)}})
    val backup: BackupViewModel = viewModel(factory = viewModelFactory { initializer {
        BackupViewModel(application.database, application.contentResolver, BuildConfig.VERSION_NAME)
    } })
    val state by model.state.collectAsStateWithLifecycle()
    val mode by model.mode.collectAsStateWithLifecycle()
    val settingsError by model.settingsError.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "today"
    val current = Destination.entries.firstOrNull { it.route == route } ?: Destination.MONEY
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var showDeveloper by rememberSaveable { mutableStateOf(false) }
    var requestedMoney by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSubscription by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedLibrary by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleDomain by rememberSaveable{mutableStateOf<String?>(null)}
    var scheduleId by rememberSaveable{mutableStateOf("")}
    var scheduleDay by rememberSaveable{mutableStateOf<Long?>(null)}
    var alertDomain by rememberSaveable{mutableStateOf("REMINDER")}
    var alertId by rememberSaveable{mutableStateOf("")}
    fun requestSchedule(target:RecordTarget){scheduleDomain=target.domain;scheduleId=target.id;scheduleDay=target.day}
    fun configureAlerts(target:RecordTarget){alertDomain=target.domain;alertId=target.id;nav.navigate("alerts"){launchSingleTop=true}}
    fun navigate(destination: String) {
        val target = when {
            destination.startsWith("library/") -> { requestedLibrary = android.net.Uri.decode(destination.substringAfter('/')); "library" }
            destination == "money-budgets" -> { requestedMoney = "BUDGETS"; "money" }
            destination.startsWith("subscription/")-> {requestedSubscription=destination.substringAfter('/');"subscriptions"}
            destination.startsWith("event/")-> {requestSchedule(RecordTarget("EVENT",android.net.Uri.decode(destination.substringAfter('/').substringBefore('?')),destination.substringAfter("?day=","").toLongOrNull()));"schedule"}
            destination.startsWith("reminder/")-> {requestSchedule(RecordTarget("REMINDER",android.net.Uri.decode(destination.substringAfter('/').substringBefore('?')),destination.substringAfter("?day=","").toLongOrNull()));"schedule"}
            destination=="new-event"->{requestSchedule(RecordTarget("NEW_EVENT",""));"schedule"}
            destination=="new-reminder"->{requestSchedule(RecordTarget("NEW_REMINDER",""));"schedule"}
            else->destination
        }
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // A cold notification launch can compose before NavHost installs its graph.
    // Retain the target until the initial back-stack entry proves navigation is ready.
    LaunchedEffect(externalTarget, externalDestination, backStack) {
        if (backStack != null) {
            if (externalTarget != null) {
                if (externalTarget.domain == "SUBSCRIPTION") {
                    requestedSubscription = externalTarget.id
                    navigate("subscriptions")
                } else {
                    requestSchedule(externalTarget)
                    navigate("schedule")
                }
                externalHandled()
            } else if (externalDestination != null) {
                navigate(externalDestination)
                externalHandled()
            }
        }
    }
    LaunchedEffect(settingsError) { if (settingsError) snackbars.showSnackbar("Settings couldn’t be saved. Please try again.") }

    PorosBackground { BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (rail) NavigationRail(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars), containerColor = Color.Transparent) {
                Spacer(Modifier.height(12.dp))
                FloatingActionButton(onClick = { quickAdd = true }, containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(AppIcons.Add, "Quick add") }
                Destination.entries.forEach { destination ->
                    NavigationRailItem(selected = destination == current, onClick = { navigate(destination.route) },
                        icon = { Icon(destination.icon, null) }, label = { Text(destination.label) })
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                topBar = {
                    val nested = route in listOf("subscriptions", "backup", "alerts")
                    val today = route == "today"
                    val title = when (route) { "alerts" -> "Notifications"; "backup" -> "Backup & restore"; "subscriptions" -> "Subscriptions"; else -> current.label }
                    TopAppBar(title = {
                        if (nested) Text(title, style = MaterialTheme.typography.titleLarge)
                        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (today) 8.dp else 12.dp)) {
                            PorosMark()
                            Column {
                                Text(
                                    "Poros",
                                    style = if (today) MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                                        else MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    if (today) "TODAY" else title,
                                    style = if (today) MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp)
                                        else MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        navigationIcon = { if (nested) IconButton(onClick = { backDispatcher?.onBackPressed() }) { Icon(AppIcons.ArrowBack, "Back") } },
                        actions = {
                            if (DemoCatalog.available) IconButton(onClick = { showDeveloper = true }) { Icon(AppIcons.Code, "Developer previews") }
                            if (route != "settings") IconButton(onClick = { navigate("settings") }) { Icon(AppIcons.Settings, "Settings") }
                        })
                },
                bottomBar = {
                    if (!rail && route !in listOf("backup", "alerts")) NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        Destination.entries.forEach { destination ->
                            NavigationBarItem(selected = destination == current, onClick = { navigate(destination.route) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                icon = { Icon(destination.icon, null, Modifier.size(22.dp)) },
                                label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                },
                floatingActionButton = {
                    if (!rail && route !in listOf("backup", "alerts")) FloatingActionButton(onClick = { quickAdd = true },
                        containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.medium) { Icon(AppIcons.Add, "Quick add") }
                },
                snackbarHost = { SnackbarHost(snackbars) },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (DemoCatalog.available && mode != DataMode.PERSONAL && route == "today") Text("Developer preview · sample data, never saved", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelMedium)
                    NavHost(navController = nav, startDestination = "today", modifier = Modifier.weight(1f)) {
                        composable("today") { TodayScreen(state, model::retry, ::navigate, selectBudget = model::setTodayBudget) {schedule.complete(it,!it.completed)} }
                        composable("money") { MoneyScreen(money, requestedMoney, { requestedMoney = null }, { nav.navigate("subscriptions") }, settings.preferredCurrency, { navigate("subscription/$it") }) }
                        composable("settings") { SettingsScreen(settings, (state as? TodayState.Ready)?.content,
                            model::setTheme, model::setDynamicColor, model::setPreferredCurrency, model::setTodayBudget,
                            { nav.navigate("backup") { launchSingleTop = true } }, { navigate("money-budgets") }) }
                        composable("backup") { BackupScreen(backup) }
                        composable("subscriptions") { SubscriptionScreen(subscriptions, requestedSubscription, { requestedSubscription = null }, {configureAlerts(RecordTarget("SUBSCRIPTION",it))}, settings.preferredCurrency) }
                        composable("schedule") { ScheduleScreen(schedule,scheduleDomain?.let{RecordTarget(it,scheduleId,scheduleDay)},{scheduleDomain=null},{navigate("subscription/$it")},::configureAlerts) }
                        composable("alerts"){AlertSettingsScreen(application.database,application.alerts,RecordTarget(alertDomain,alertId)){nav.popBackStack()}}
                        composable("library") { LibraryScreen(library, requestedLibrary, { requestedLibrary = null }) }
                    }
                }
            }
        }
    } }
    if (quickAdd) QuickAddSheet({ quickAdd = false }) { destination ->
        quickAdd = false
        if (destination in listOf("EXPENSE", "INCOME", "TRANSFER")) { requestedMoney = destination; navigate("money") }
        else navigate(destination)
    }
    if (showDeveloper && DemoCatalog.available) AlertDialog(onDismissRequest = { showDeveloper = false }, title = { Text("Developer previews") },
        text = { DemoControls(mode) { model.setMode(it); showDeveloper = false } }, confirmButton = { TextButton(onClick = { showDeveloper = false }) { Text("Done") } })
}

@Composable
private fun DemoControls(mode: DataMode, setMode: (DataMode) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(if (mode == DataMode.PERSONAL) "Personal data" else "Demo · sample data, never saved", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DataMode.DEMO to "Populated", DataMode.EMPTY_DEMO to "Empty", DataMode.PERSONAL to "Personal").forEach { (value, label) ->
                    FilterChip(selected = mode == value, onClick = { setMode(value) }, label = { Text(label) })
                }
            }
        }
    }
}
