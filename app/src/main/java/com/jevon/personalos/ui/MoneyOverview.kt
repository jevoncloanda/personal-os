package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.jevon.personalos.data.*
import com.jevon.personalos.money.*
import com.jevon.personalos.today.formatMoney
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun BalanceHeader(data: MoneySnapshot, currency: String, choose: (String) -> Unit, create: () -> Unit) {
    val balances = activeCurrencyBalances(data.accounts)
    val selected = balances.firstOrNull { it.code == currency }
    Column(Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selected == null) {
            Text("A clearer view of your money.", style = MaterialTheme.typography.headlineLarge)
            Text("Start with an account. Your balances and spending stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = create) { Icon(AppIcons.Add, null); Text("Create account", Modifier.padding(start = 8.dp)) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total balance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CurrencyPicker(currency, balances.map { it.code }, choose)
            }
            Text(formatMoney(selected.minor, selected.code), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { contentDescription = "${selected.code} total balance ${formatMoney(selected.minor, selected.code)}" })
            Text("${selected.count} active ${if (selected.count == 1) "account" else "accounts"} · all recorded dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CurrencyPicker(code: String, currencies: List<String>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = LocalAtmosphere.current.panel)) {
            Text(code); Icon(AppIcons.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            currencies.forEach { currency -> DropdownMenuItem(text = { Text(currency) }, onClick = { expanded = false; select(currency) }) }
        }
    }
}

@Composable internal fun MonthSelector(month: YearMonth, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = previous) { Icon(AppIcons.ChevronLeft, "Previous month") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = next) { Icon(AppIcons.ChevronRight, "Next month") }
    }
}

internal fun LazyListScope.moneyOverview(data: MoneySnapshot, currency: String, month: YearMonth, edit: (String, String?) -> Unit,
    add: (String) -> Unit, section: (String) -> Unit, changeMonth: (YearMonth) -> Unit, subscriptions: () -> Unit) {
    item {
        MoneyActions(add)
    }
    item {
        val spent = data.totals.firstOrNull { it.currencyCode == currency && it.kind == EntryKind.EXPENSE }?.amount ?: 0L
        val income = data.totals.firstOrNull { it.currencyCode == currency && it.kind == EntryKind.INCOME }?.amount ?: 0L
        FrostedPanel(contentPadding = 16.dp) {
            MonthSelector(month, { changeMonth(month.minusMonths(1)) }, { changeMonth(month.plusMonths(1)) })
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Monthly spending · $currency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(spent, currency), style = MaterialTheme.typography.headlineLarge)
                }
                Column(Modifier.weight(1f).widthIn(min = 110.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Income · $currency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(income, currency), style = MaterialTheme.typography.titleLarge, color = LocalAtmosphere.current.positive)
                }
            }
            Text("Recorded expenses only. Transfers and forecasts excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val budgets = data.budgets.filter { it.budget.currencyCode == currency }
    item { SectionHeading("Budgets", "Manage", { section("Budgets") }) }
    if (budgets.isEmpty()) item {
        FrostedPanel { EmptyMessage("Make room for what matters", "Set a monthly limit in $currency and see what remains."); TextButton(onClick = { edit("budget", null) }) { Text("Create monthly budget") } }
    }
    items(budgets, key = { "overview-budget-${it.budget.id}" }) { BudgetBlock(it, data.categories) { edit("budget", it.budget.id) } }
    item {
        val slices = spendingSlices(data.categorySpending, data.categories, currency)
        if (slices.isNotEmpty()) FrostedPanel {
            Text("Where it went", style = MaterialTheme.typography.titleMedium)
            Text("Share of ${month.format(DateTimeFormatter.ofPattern("MMMM"))} spending · $currency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            slices.forEach { slice ->
                Column(Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(slice.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(slice.amount, currency), style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(progress = { slice.fraction }, modifier = Modifier.fillMaxWidth().height(5.dp), trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), drawStopIndicator = {})
                }
            }
        }
    }
    item { SectionHeading("Account balances", "All accounts", { section("Accounts") }) }
    items(data.accounts.filter { !it.account.archived && it.account.currencyCode == currency }.take(3), key = { "overview-account-${it.account.id}" }) { AccountRow(it) { edit("account", it.account.id) } }
    item { SectionHeading("Recent transactions", "View history", { section("History") }) }
    items(data.recent, key = { "recent-${it.id}" }) { EntryRow(it, data) { edit("entry", it.id) } }
    if (data.recent.isEmpty()) item { EmptyMessage("A fresh start", "Recorded transactions will appear here. Choose an account to add your first entry.") }
    item { OutlinedButton(onClick = subscriptions) { Icon(AppIcons.Autorenew, null); Text("Subscriptions", Modifier.padding(start = 8.dp)) } }
}

@Composable private fun MoneyActions(add: (String) -> Unit) {
    val actions = listOf(Triple("EXPENSE", "Expense", AppIcons.NorthEast), Triple("INCOME", "Income", AppIcons.SouthWest), Triple("TRANSFER", "Transfer", AppIcons.SwapHoriz))
    BoxWithConstraints {
        if (maxWidth >= 300.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (kind, label, icon) -> MoneyAction(kind, label, icon, Modifier.weight(1f), add) }
            }
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (kind, label, icon) -> MoneyAction(kind, label, icon, Modifier, add) }
        }
    }
}

@Composable private fun MoneyAction(kind: String, label: String, icon: ImageVector, modifier: Modifier, add: (String) -> Unit) {
    FilledTonalButton(onClick = { add(kind) }, modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = LocalAtmosphere.current.panel, contentColor = MaterialTheme.colorScheme.primary)) {
        Icon(icon, null, Modifier.size(18.dp)); Text(label, Modifier.padding(start = 4.dp), maxLines = 1)
    }
}
