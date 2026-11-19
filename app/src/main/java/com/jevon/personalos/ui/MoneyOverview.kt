package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jevon.personalos.data.*
import com.jevon.personalos.money.*
import com.jevon.personalos.today.formatMoney
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
private fun MoneyPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = LocalAtmosphere.current.panel, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun BalanceHeader(
    data: MoneySnapshot,
    currency: String,
    choose: (String) -> Unit,
    create: () -> Unit,
    add: (String) -> Unit,
) {
    val balances = activeCurrencyBalances(data.accounts)
    val selected = balances.firstOrNull { it.code == currency }
    val balance = selected?.minor ?: BigInteger.ZERO
    MoneyPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CurrencyPicker(currency, balances.map { it.code }, choose)
            Spacer(Modifier.weight(1f))
            if (selected == null) TextButton(onClick = create) {
                Text("Create account", style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TOTAL BALANCE · $currency",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMoney(balance, currency),
                style = MaterialTheme.typography.displaySmall,
                color = if (balance.signum() < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = "$currency total balance ${formatMoney(balance, currency)}"
                    heading()
                },
            )
            val count = selected?.count ?: 0
            Text(
                "$count active ${if (count == 1) "account" else "accounts"} · all recorded dates",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyActions(add)
    }
}

@Composable
private fun CurrencyPicker(code: String, currencies: List<String>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = currencies.isNotEmpty(),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = "Selected currency $code"
            },
        ) {
            Text(code, style = MaterialTheme.typography.labelMedium)
            Icon(AppIcons.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency) },
                    onClick = {
                        expanded = false
                        select(currency)
                    },
                )
            }
        }
    }
}

internal fun monthLabel(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL uuuu"))

@Composable internal fun MonthSelector(month: YearMonth, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = previous) { Icon(AppIcons.ChevronLeft, "Previous month") }
        Text(monthLabel(month), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = next) { Icon(AppIcons.ChevronRight, "Next month") }
    }
}

internal fun LazyListScope.moneyOverview(
    data: MoneySnapshot,
    currency: String,
    currencies: List<String>,
    chooseCurrency: (String) -> Unit,
    month: YearMonth,
    addExpense: () -> Unit,
    edit: (String, String?) -> Unit,
    section: (String) -> Unit,
    changeMonth: (YearMonth) -> Unit,
    subscriptions: () -> Unit,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthSelector(month, { changeMonth(month.minusMonths(1)) }, {
                changeMonth(month.plusMonths(1))
            })
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrencyPicker(currency, currencies, chooseCurrency)
                Button(onClick = addExpense) { Text("Add expense") }
            }
        }
    }
    val budgets = data.budgets.filter { it.budget.currencyCode == currency }
    val overall = budgets.filter { it.budget.categoryId == null }.take(1)
    val categories = budgets.filter { it.budget.categoryId != null }.take(3)
    item {
        MoneyPanel {
            MoneySectionHeading(
                "Monthly budget",
                if (budgets.isEmpty()) "Create budget" else "Manage",
                {
                    if (budgets.isEmpty()) edit("budget", null) else section("Budgets")
                },
            )
            if (budgets.isEmpty()) {
                Text("No $currency budget this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + " · " + currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (overall + categories).forEach { value ->
                MoneyBudgetGauge(value, data.categories) { edit("budget", value.budget.id) }
            }
        }
    }
    val slices = spendingSlices(data.categorySpending, data.categories, currency)
    if (slices.isNotEmpty()) item {
        MoneyPanel {
            Text("Where it went", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text("Share of ${month.format(DateTimeFormatter.ofPattern("MMMM"))} spending · $currency",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            slices.forEach { slice ->
                Column(
                    Modifier.semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(slice.label, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(slice.amount, currency),
                            style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(
                        progress = { slice.fraction },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
    item {
        Surface(
            onClick = subscriptions,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(AppIcons.Autorenew)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Manage subscriptions", style = MaterialTheme.typography.titleMedium)
                    Text("Recurring plans & payment history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item {
        MoneyPanel {
            MoneySectionHeading("Recent expenses", "View all", { section("History") })
            Surface(
                onClick = { section("History") },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Search & filter history", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(AppIcons.Settings, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (data.recent.isEmpty()) Text(
                "Recorded expenses will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val today = LocalDate.now()
            data.recent.groupBy { it.occurredOnEpochDay }.forEach { (day, entries) ->
                val date = LocalDate.ofEpochDay(day)
                Text(
                    when (date) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                )
                entries.forEach { entry ->
                    EntryRow(entry, data, overview = true) { edit("entry", entry.id) }
                }
            }
        }
    }
}

@Composable
private fun MoneySectionHeading(title: String, action: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = open, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(action, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun MoneyCashFlow(data: MoneySnapshot, currency: String) {
    val spent = data.totals.firstOrNull {
        it.currencyCode == currency && it.kind == EntryKind.EXPENSE
    }?.amount ?: 0L
    val income = data.totals.firstOrNull {
        it.currencyCode == currency && it.kind == EntryKind.INCOME
    }?.amount ?: 0L
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CashFlowCard("Income", income, currency, positive = true)
                CashFlowCard("Expenses", spent, currency, positive = false)
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CashFlowCard("Income", income, currency, true, Modifier.weight(1f).fillMaxHeight())
                CashFlowCard("Expenses", spent, currency, false, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CashFlowCard(
    title: String,
    amount: Long,
    currency: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (positive) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.error
    Surface(modifier, color = LocalAtmosphere.current.panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
                    Icon(if (positive) AppIcons.SouthWest else AppIcons.NorthEast, null,
                        Modifier.padding(5.dp).size(16.dp), tint = color)
                }
            }
            Text(formatMoney(amount, currency), style = MaterialTheme.typography.titleLarge, color = color)
            Text("$currency · recorded", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoneyBudgetGauge(
    value: BudgetProjection,
    categories: List<CategoryEntity>,
    edit: () -> Unit,
) {
    val budget = value.budget
    val summary = value.summary()
    val remaining = summary.remainingMinor
    val overall = budget.categoryId == null
    val color = when {
        remaining < 0 -> MaterialTheme.colorScheme.error
        summary.fraction >= 0.8f -> LocalAtmosphere.current.warning
        overall -> MaterialTheme.colorScheme.primary
        else -> LocalAtmosphere.current.positive
    }
    Surface(onClick = edit, color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(budget.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (remaining < 0) "Over budget" else "${(summary.fraction * 100).toInt()}% used",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
            Text(
                if (overall) "Overall monthly budget" else
                    categories.firstOrNull { it.id == budget.categoryId }?.name ?: "Category limit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { summary.fraction },
                modifier = Modifier.fillMaxWidth().height(if (overall) 9.dp else 5.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawStopIndicator = {},
            )
            Text(
                "Spent ${formatMoney(value.spent, budget.currencyCode)} of ${formatMoney(budget.limitMinor, budget.currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatMoney(BigInteger.valueOf(remaining).abs(), budget.currencyCode)} " +
                    when {
                        remaining < 0 -> "over budget"
                        value.spent == 0L -> "unspent"
                        else -> "remaining"
                    },
                style = if (overall) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall,
                color = if (remaining < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MoneyLedgerRow(
    icon: ImageVector,
    title: String,
    detail: String,
    amount: String,
    positive: Boolean,
    open: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Surface(
        onClick = open,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, positive)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (largeText) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(amount, style = MaterialTheme.typography.titleSmall,
                        color = if (positive) LocalAtmosphere.current.positive
                            else MaterialTheme.colorScheme.onSurface)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(amount, Modifier.widthIn(max = 140.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (positive) LocalAtmosphere.current.positive
                            else MaterialTheme.colorScheme.onSurface)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (largeText) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MoneyActions(add: (String) -> Unit) {
    val actions = listOf(
        Triple("EXPENSE", "Add Expense", AppIcons.Add),
        Triple("INCOME", "Income", AppIcons.SouthWest),
        Triple("TRANSFER", "Transfer", AppIcons.SwapHoriz),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.15f) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (kind, label, icon) ->
                    MoneyAction(kind, label, icon,
                        Modifier.weight(if (kind == "EXPENSE") 1.4f else 1f), add)
                }
            }
        } else FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { (kind, label, icon) ->
                MoneyAction(kind, label, icon, Modifier, add)
            }
        }
    }
}

@Composable
private fun MoneyAction(
    kind: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    add: (String) -> Unit,
) {
    FilledTonalButton(
        onClick = { add(kind) },
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (kind == "EXPENSE") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (kind == "EXPENSE") MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}
