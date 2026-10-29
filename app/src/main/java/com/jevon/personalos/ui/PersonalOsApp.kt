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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.jevon.personalos.data.*
import com.jevon.personalos.today.*
import com.jevon.personalos.money.MoneyViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalOsApp(model: TodayViewModel, settings: Settings, externalTarget:RecordTarget?=null, externalHandled:()->Unit={}) {
    SystemBarAppearance()
    val money: MoneyViewModel = viewModel()
    val application = LocalContext.current.applicationContext as PersonalOsApplication
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
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDeveloper by rememberSaveable { mutableStateOf(false) }
    var requestedMoney by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSubscription by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleDomain by rememberSaveable{mutableStateOf<String?>(null)}
    var scheduleId by rememberSaveable{mutableStateOf("")}
    var scheduleDay by rememberSaveable{mutableStateOf<Long?>(null)}
    var alertDomain by rememberSaveable{mutableStateOf("REMINDER")}
    var alertId by rememberSaveable{mutableStateOf("")}
    fun requestSchedule(target:RecordTarget){scheduleDomain=target.domain;scheduleId=target.id;scheduleDay=target.day}
    fun configureAlerts(target:RecordTarget){alertDomain=target.domain;alertId=target.id;nav.navigate("alerts"){launchSingleTop=true}}
    fun navigate(destination: String) {
        val target = when {
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
    LaunchedEffect(externalTarget,backStack){if(backStack!=null)externalTarget?.let{target->if(target.domain=="SUBSCRIPTION"){requestedSubscription=target.id;navigate("subscriptions")}else{requestSchedule(target);navigate("schedule")};externalHandled()}}
    LaunchedEffect(settingsError) { if (settingsError) snackbars.showSnackbar("Appearance settings couldn’t be saved. Please try again.") }

    AtmosphericBackground { BoxWithConstraints(Modifier.fillMaxSize()) {
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
                topBar = { TopAppBar(title = { Text(when (route) { "alerts" -> "Notifications"; "backup" -> "Backup & restore"; "subscriptions" -> "Subscriptions"; else -> current.label }, style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = { if (route in listOf("subscriptions", "backup","alerts")) IconButton(onClick = { backDispatcher?.onBackPressed() }) { Icon(AppIcons.ArrowBack, "Back") } },
                    actions = {
                        if (DemoCatalog.available) IconButton(onClick = { showDeveloper = true }) { Icon(AppIcons.Code, "Developer previews") }
                        IconButton(onClick = { showSettings = true }) { Icon(AppIcons.Settings, "Settings") }
                    }) },
                bottomBar = {
                    if (!rail && route !in listOf("backup","alerts")) Surface(color = LocalAtmosphere.current.panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                      Column(Modifier.navigationBarsPadding()) {
                        val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                        if (largeText) FilledTonalButton(onClick = { quickAdd = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) { Icon(AppIcons.Add, null); Text("Quick add", Modifier.padding(start = 8.dp)) }
                        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.height(if (largeText) 80.dp else 72.dp)) {
                        Destination.entries.forEachIndexed { index, destination ->
                            if (index == 2 && !largeText) FloatingActionButton(onClick = { quickAdd = true },
                                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                                modifier = Modifier.padding(horizontal = 4.dp).size(48.dp)) { Icon(AppIcons.Add, "Quick add", Modifier.size(24.dp)) }
                            NavigationBarItem(selected = destination == current, onClick = { navigate(destination.route) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary),
                                icon = { Icon(destination.icon, null, Modifier.size(22.dp)) }, label = { Text(destination.label) })
                        }
                        }
                      }
                    }
                },
                snackbarHost = { SnackbarHost(snackbars) },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (DemoCatalog.available && mode != DataMode.PERSONAL && route == "today") Text("Developer preview · sample data, never saved", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelMedium)
                    NavHost(navController = nav, startDestination = "today", modifier = Modifier.weight(1f)) {
                        composable("today") { TodayScreen(state, model::retry, ::navigate) {schedule.complete(it,!it.completed)} }
                        composable("money") { MoneyScreen(money, requestedMoney, { requestedMoney = null }, { nav.navigate("subscriptions") }) }
                        composable("backup") { BackupScreen(backup) }
                        composable("subscriptions") { SubscriptionScreen(subscriptions, requestedSubscription, { requestedSubscription = null }, {configureAlerts(RecordTarget("SUBSCRIPTION",it))}) }
                        composable("schedule") { ScheduleScreen(schedule,scheduleDomain?.let{RecordTarget(it,scheduleId,scheduleDay)},{scheduleDomain=null},{navigate("subscription/$it")},::configureAlerts) }
                        composable("alerts"){AlertSettingsScreen(application.database,application.alerts,RecordTarget(alertDomain,alertId)){nav.popBackStack()}}
                        composable("library") { FoundationScreen("Make time for what you love", "Movies, series, games, and books will share one library. Collection and progress editing arrive in the next phase.", AppIcons.MenuBook) }
                    }
                }
            }
        }
    } }
    if (quickAdd) ModalBottomSheet(onDismissRequest = { quickAdd = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Quick add", style = MaterialTheme.typography.headlineSmall)
            Text("A small action, a clearer day.", Modifier.padding(top = 8.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(Triple("Add expense", "EXPENSE", AppIcons.NorthEast), Triple("Add income", "INCOME", AppIcons.SouthWest), Triple("Transfer", "TRANSFER", AppIcons.SwapHoriz),
                Triple("New event", "new-event", AppIcons.CalendarMonth), Triple("New reminder", "new-reminder", AppIcons.RadioButtonUnchecked), Triple("New subscription", "subscription/new", AppIcons.Autorenew),
                Triple("Library item · preview", "library", AppIcons.MenuBook)).forEach { (label, destination, icon) ->
                ListItem(headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) }, leadingContent = { IconBadge(icon, positive = destination == "INCOME") }, trailingContent = { Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { quickAdd = false
                        if (destination in listOf("EXPENSE", "INCOME", "TRANSFER")) { requestedMoney = destination; navigate("money") }
                        else navigate(destination)
                    }.heightIn(min = 56.dp))
            }
        }
    }
    if (showSettings) SettingsDialog(settings, model::setTheme, model::setDynamicColor,
        openBackup = { showSettings = false; nav.navigate("backup") { launchSingleTop = true } }) { showSettings = false }
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

@Composable
private fun FoundationScreen(title: String, body: String, icon: ImageVector, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(icon, modifier = Modifier.padding(top = 12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        FrostedPanel {
            Text("Foundation screen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        extra()
    }
}

@Composable
private fun SettingsDialog(settings: Settings, setTheme: (ThemeMode) -> Unit, setDynamicColor: (Boolean) -> Unit, openBackup: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Settings") }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ThemeMode.entries.forEach { theme ->
                    Row(Modifier.fillMaxWidth().selectable(selected = theme == settings.theme, role = Role.RadioButton,
                        onClick = { setTheme(theme) }).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == settings.theme, onClick = null)
                        Text(when (theme) { ThemeMode.SYSTEM -> "Use device theme"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }, Modifier.padding(start = 12.dp))
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Wallpaper colors", Modifier.weight(1f))
                        Switch(checked = settings.dynamicColor, onCheckedChange = setDynamicColor, modifier = Modifier.semantics { contentDescription = "Wallpaper colors" })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(onClick = openBackup) { Text("Backup & restore") }
            }
        })
}
