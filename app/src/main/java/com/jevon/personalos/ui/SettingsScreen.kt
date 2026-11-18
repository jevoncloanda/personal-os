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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
internal fun SettingsScreen(
    settings: Settings,
    content: TodayContent?,
    setTheme: (ThemeMode) -> Unit,
    setDynamicColor: (Boolean) -> Unit,
    setCurrency: (String) -> Unit,
    selectBudget: (String?) -> Unit,
    openBackup: () -> Unit,
    openBudgets: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PersonalOsApplication
    var currency by rememberSaveable(settings.preferredCurrency) { mutableStateOf(settings.preferredCurrency) }
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    var choosingBudget by rememberSaveable { mutableStateOf(false) }
    var alertStatus by remember { mutableStateOf(app.alerts.status("EVENT")) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) alertStatus = app.alerts.status("EVENT") }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        alertStatus = app.alerts.status("EVENT")
    }
    val valid = runCatching { currencyDigits(currency) }.isSuccess
    val currencyName = remember(settings.preferredCurrency) {
        runCatching { java.util.Currency.getInstance(settings.preferredCurrency).displayName }.getOrNull()
    }
    val themes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp).padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsSection("Appearance & theme", AppIcons.Settings) {
            if (LocalDensity.current.fontScale <= 1.15f) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themes.forEachIndexed { index, theme ->
                        SegmentedButton(
                            selected = settings.theme == theme,
                            onClick = { setTheme(theme) },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                activeBorderColor = Color.Transparent,
                                inactiveBorderColor = Color.Transparent,
                            ),
                            shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                        ) {
                            Text(when (theme) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            })
                        }
                    }
                }
            } else Column(Modifier.selectableGroup()) {
                themes.forEach { theme ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = theme == settings.theme,
                            role = Role.RadioButton,
                            onClick = { setTheme(theme) },
                        ).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == settings.theme, onClick = null)
                        Text(when (theme) {
                            ThemeMode.SYSTEM -> "Use device theme"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }, Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .toggleable(settings.dynamicColor, role = Role.Switch, onValueChange = setDynamicColor)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Settings)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Wallpaper colors", style = MaterialTheme.typography.titleSmall)
                        Text("Personalize supporting accents", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(settings.dynamicColor, onCheckedChange = null)
                }
            }
        }
        SettingsSection("Money & budgets", AppIcons.Payments) {
            SettingsRow(AppIcons.Payments, "Preferred currency",
                settings.preferredCurrency + currencyName?.let { " · $it" }.orEmpty(),
                onClick = { choosingCurrency = true })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                AppIcons.AccountBalanceWallet, "Today budget",
                content?.budget?.budget?.let { "${it.name} · ${it.currencyCode}" }
                    ?: "Automatic · ${settings.preferredCurrency}",
                enabled = content != null,
                onClick = { choosingBudget = true },
            )
        }
        SettingsSection("Notifications", AppIcons.Notifications) {
            SettingsRow(AppIcons.Notifications, "Android notifications", alertStatus.message, onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
            Text("Set lead times on each event, reminder or subscription. Alerts work locally with best-effort timing.",
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection("Data & storage", AppIcons.HardDrive) {
            SettingsRow(AppIcons.HardDrive, "Backup & restore", "Export a local JSON backup or review a restore", onClick = openBackup)
            Text("Your records work offline. Backups are unencrypted. Choose a trusted location. Appearance and Today preferences stay on this device.",
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection("About Poros", AppIcons.Info) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PorosMark()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Poros", style = MaterialTheme.typography.titleLarge)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Your day, money and downtime in one place.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Native Android · Works offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Inter · SIL Open Font License\nLucide icons · ISC license", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (choosingCurrency) AlertDialog(
        onDismissRequest = { choosingCurrency = false },
        title = { Text("Preferred currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(currency, { currency = it.uppercase(java.util.Locale.ROOT) },
                    label = { Text("Currency (ISO code)") },
                    singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    supportingText = { if (!valid) Text("Use a valid monetary ISO code, such as IDR, AUD or USD.") })
                Text("Defaults for new records and Today. Existing amounts and currencies stay unchanged.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && currency != settings.preferredCurrency, onClick = {
                setCurrency(currency)
                choosingCurrency = false
            }) { Text("Save currency") }
        },
        dismissButton = { TextButton(onClick = { choosingCurrency = false }) { Text("Close") } },
    )
    if (choosingBudget && content != null) TodayBudgetPicker(content, selectBudget, openBudgets) { choosingBudget = false }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PorosCard(contentPadding = 12.dp, verticalSpacing = 8.dp, content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    detail: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (enabled) 1f else 0.38f)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
