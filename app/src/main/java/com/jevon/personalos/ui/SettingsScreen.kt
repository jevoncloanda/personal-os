package com.jevon.personalos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jevon.personalos.BuildConfig
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.data.*
import com.jevon.personalos.today.TodayContent

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(settings: Settings, content: TodayContent?, setTheme: (ThemeMode) -> Unit,
    setDynamicColor: (Boolean) -> Unit, setCurrency: (String) -> Unit, selectBudget: (String?) -> Unit,
    openBackup: () -> Unit, openBudgets: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PersonalOsApplication
    var currency by rememberSaveable(settings.preferredCurrency) { mutableStateOf(settings.preferredCurrency) }
    var choosingBudget by rememberSaveable { mutableStateOf(false) }
    var alertStatus by remember { mutableStateOf(app.alerts.status("EVENT")) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) alertStatus = app.alerts.status("EVENT") }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { alertStatus = app.alerts.status("EVENT") }
    val valid = runCatching { currencyDigits(currency) }.isSuccess
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Appearance & theme")
        PorosCard {
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, theme ->
                    SegmentedButton(selected = settings.theme == theme, onClick = { setTheme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)) {
                        Text(when (theme) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" })
                    }
                }
            } else ThemeMode.entries.forEach { theme ->
                Row(Modifier.fillMaxWidth().selectable(selected = theme == settings.theme, role = Role.RadioButton,
                    onClick = { setTheme(theme) }).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == settings.theme, onClick = null)
                    Text(when (theme) { ThemeMode.SYSTEM -> "Use device theme"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }, Modifier.padding(start = 12.dp))
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wallpaper colors", style = MaterialTheme.typography.titleSmall)
                        Text("Personalize supporting accents", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(settings.dynamicColor, setDynamicColor, modifier = Modifier.semantics { contentDescription = "Wallpaper colors" })
                }
            }
        }
        SectionTitle("Money & budgets")
        PorosCard {
            OutlinedTextField(currency, { currency = it.uppercase(java.util.Locale.ROOT) }, label = { Text("Preferred currency (ISO code)") },
                singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                supportingText = { if (!valid) Text("Use a valid monetary ISO code, such as IDR, AUD or USD.") })
            Text("Defaults for new records and Today. Existing amounts and currencies stay unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = valid && currency != settings.preferredCurrency, onClick = { setCurrency(currency) }) { Text("Save currency") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow("Today budget", content?.budget?.budget?.let { "${it.name} · ${it.currencyCode}" } ?: "Automatic · ${settings.preferredCurrency}",
                enabled = content != null, onClick = { choosingBudget = true })
        }
        SectionTitle("Notifications")
        PorosCard {
            Text(alertStatus.message, style = MaterialTheme.typography.bodyMedium)
            Text("Set lead times on each event, reminder or subscription. Alerts work locally with best-effort timing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Android notification settings") }
        }
        SectionTitle("Data & storage")
        PorosCard {
            SettingsRow("Backup & restore", "Export a local JSON backup or review a restore", onClick = openBackup)
            Text("Your records work offline. Backups are unencrypted; choose a trusted location. Appearance and Today preferences stay on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionTitle("About Poros")
        PorosCard {
            Text("Poros", style = MaterialTheme.typography.titleLarge)
            Text("Your day, money and downtime in one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            Text("Native Android · Works offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Inter · SIL Open Font License\nLucide icons · ISC license", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (choosingBudget && content != null) TodayBudgetPicker(content, selectBudget, openBudgets) { choosingBudget = false }
}

@Composable
private fun SettingsRow(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp))
    }
}
