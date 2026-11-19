package com.jevon.personalos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jevon.personalos.data.*
import com.jevon.personalos.money.*
import com.jevon.personalos.today.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MoneyScreen(model: MoneyViewModel, requested: String?, handled: () -> Unit, subscriptions: () -> Unit, preferredCurrency: String = DEFAULT_CURRENCY,
    openSubscription: (String) -> Unit = { subscriptions() }) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("Overview") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var newKind by rememberSaveable { mutableStateOf("EXPENSE") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var overviewCurrency by rememberSaveable(preferredCurrency) { mutableStateOf(preferredCurrency) }
    var selectedAccountCurrency by rememberSaveable(preferredCurrency) { mutableStateOf(preferredCurrency) }
    val moneyListState = rememberLazyListState()
    LaunchedEffect(section) { moneyListState.scrollToItem(0) }
    fun edit(type: String, id: String? = null) { model.clearError(); editId = id; editor = if (type == "entry" && id != null) "detail" else type }
    LaunchedEffect(requested) { requested?.let { if (it == "BUDGETS") { section = "Budgets"; model.setFilter(filter.copy(month = java.time.YearMonth.now(), page = 0)) } else { newKind = it; edit("entry") }; handled() } }
    when (val s = state) {
        MoneyState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        MoneyState.Error -> Column(Modifier.padding(24.dp)) {
            Text("Money couldn't load. Your records have not been changed.")
            Button(onClick = model::retry) { Text("Try again") }
        }
        is MoneyState.Ready -> {
            val data = s.snapshot
            val currencyBalances = activeCurrencyBalances(data.accounts)
            val budgetCurrencies = (listOf(preferredCurrency, overviewCurrency) + data.budgets.map { it.budget.currencyCode } +
                data.totals.map { it.currencyCode } + currencyBalances.map { it.code }).distinct().sorted()
            val displayCurrency = overviewCurrency
            val accountCurrency = selectedAccountCurrency.takeIf { code -> currencyBalances.any { it.code == code } }
                ?: currencyBalances.firstOrNull()?.code ?: preferredCurrency
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("MoneyList"),
                state = moneyListState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = if (section == "Overview") 88.dp else 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (section == "Overview") 16.dp else 10.dp,
                ),
            ) {
                item {
                    val sections = listOf("Overview", "History", "Accounts", "Budgets", "Categories")
                    FlowRow(
                        Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        sections.forEach { label ->
                            TextButton(
                                onClick = { section = label },
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    selected = section == label
                                    role = androidx.compose.ui.semantics.Role.Tab
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (section == label)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor = if (section == label)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (section == "Accounts") item {
                    BalanceHeader(
                        data,
                        accountCurrency,
                        { selectedAccountCurrency = it },
                        { edit("account") },
                        { kind ->
                            newKind = kind
                            edit("entry")
                        },
                    )
                }
                if (section == "Accounts" && currencyBalances.isNotEmpty()) item {
                    MonthSelector(s.month,
                        { model.setFilter(filter.copy(month = s.month.minusMonths(1), page = 0)) },
                        { model.setFilter(filter.copy(month = s.month.plusMonths(1), page = 0)) })
                    MoneyCashFlow(data, accountCurrency)
                }
                developerMoneyTools(section) { section = it }
                if (section in listOf("History", "Budgets")) item { MonthSelector(filter.month,
                    { model.setFilter(filter.copy(month = filter.month.minusMonths(1), page = 0)) }, { model.setFilter(filter.copy(month = filter.month.plusMonths(1), page = 0)) }) }
                if (error != null && editor == null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                when (section) {
                    "Overview" -> moneyOverview(
                        data, displayCurrency, budgetCurrencies, { overviewCurrency = it }, s.month,
                        addExpense = { newKind = "EXPENSE"; edit("entry") },
                        edit = { type, id -> edit(type, id) },
                        section = { section = it },
                        changeMonth = { model.setFilter(filter.copy(month = it, page = 0)) },
                        subscriptions = subscriptions,
                    )
                    "Accounts" -> {
                        item {
                            SectionHeading("Accounts", "Create account", { edit("account") })
                            Text("Balances include all dates. Archived accounts retain history and can be restored.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (data.accounts.isEmpty()) item {
                            FinancialEmptyState("No accounts yet", "Create an account to record your opening balance and transactions.")
                        }
                        items(data.accounts, key = { it.account.id }) { balance ->
                            PorosCard(contentPadding = 12.dp, verticalSpacing = 0.dp) {
                                AccountManagementRow(balance) { edit("account", balance.account.id) }
                            }
                        }
                    }
                    "Categories" -> {
                        item {
                            SectionHeading("Categories", "Add category", { edit("category") })
                            Text("Rename or archive without removing past transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (data.categories.isEmpty()) item {
                            FinancialEmptyState("No categories yet", "Add an expense or income category to organize your records.")
                        }
                        items(data.categories, key = { it.id }) { category ->
                            PorosCard(contentPadding = 12.dp, verticalSpacing = 0.dp) {
                                MoneyLedgerRow(
                                    AppIcons.Label, category.name,
                                    "${category.kind?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Expense / income"}${if (category.archived) " · Archived" else ""}",
                                    "Edit", category.kind == "INCOME",
                                ) { edit("category", category.id) }
                            }
                        }
                    }
                    "Budgets" -> {
                        item {
                            SectionHeading("Monthly budgets", "Create budget", { edit("budget") })
                            Text("Overall includes all expenses. Category limits sit within it and are never added together.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.month != filter.month) {
                            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        } else if (data.budgets.isEmpty()) {
                            item {
                                FinancialEmptyState(
                                    "No budgets this month",
                                    "Create an overall plan or a category limit for ${monthLabel(filter.month)}.",
                                )
                            }
                        } else {
                            val (overall, categoryLimits) = data.budgets.partition { it.budget.categoryId == null }
                            items(overall, key = { it.budget.id }) {
                                BudgetBlock(it, data.categories) { edit("budget", it.budget.id) }
                            }
                            if (categoryLimits.isNotEmpty()) item { SectionTitle("Category limits") }
                            items(categoryLimits, key = { it.budget.id }) {
                                BudgetBlock(it, data.categories) { edit("budget", it.budget.id) }
                            }
                        }
                    }
                    "History" -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeading("History · ${data.count} this month", "Filters", { showFilters = true })
                                SearchField("Search notes, account, category", filter.search) { model.setFilter(filter.copy(search = it, page = 0)) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (listOf("" to "All") + EntryKind.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) }).forEach { (kind, label) ->
                                        FilterChip(selected = filter.kind == kind, onClick = { model.setFilter(filter.copy(kind = kind, page = 0)) }, label = { Text(label) })
                                    }
                                }
                                val selected = listOfNotNull(filter.kind.ifBlank { null }?.lowercase()?.replaceFirstChar(Char::uppercase),
                                    data.accounts.firstOrNull { it.account.id == filter.account }?.account?.name,
                                    data.categories.firstOrNull { it.id == filter.category }?.name, filter.currency.ifBlank { null })
                                if (selected.isNotEmpty()) Text("Filtered by ${selected.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (data.entries.isEmpty()) item { FinancialEmptyState("No matching transactions", "Try another month, search or filter.") }
                        data.entries.take(50).groupBy { it.occurredOnEpochDay }.forEach { (date, entries) ->
                            item(key = "date-$date") {
                                Text(
                                    LocalDate.ofEpochDay(date).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                                )
                            }
                            item(key = "ledger-$date") {
                                PorosCard(contentPadding = 12.dp, verticalSpacing = 0.dp) {
                                    entries.forEachIndexed { index, entry ->
                                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        EntryRow(entry, data, overview = true) { edit("entry", entry.id) }
                                    }
                                }
                            }
                        }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(enabled = filter.page > 0, onClick = { model.setFilter(filter.copy(page = filter.page - 1)) }) { Text("Previous page") }
                            TextButton(enabled = data.entries.size > 50, onClick = { model.setFilter(filter.copy(page = filter.page + 1)) }) { Text("Next page") }
                        } }
                    }
                }
            }
            if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("History filters", style = MaterialTheme.typography.titleLarge)
                    Choice("Kind", filter.kind, listOf("" to "All kinds") + EntryKind.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) }) { model.setFilter(filter.copy(kind = it, page = 0)) }
                    Choice("Account", filter.account, listOf("" to "All accounts") + data.accounts.map { it.account.id to it.account.name }) { model.setFilter(filter.copy(account = it, page = 0)) }
                    Choice("Category", filter.category, listOf("" to "All categories") + data.categories.map { it.id to it.name }) { model.setFilter(filter.copy(category = it, page = 0)) }
                    Choice("Currency", filter.currency, listOf("" to "All currencies") + (data.accounts.map { it.account.currencyCode } + data.totals.map { it.currencyCode }).distinct().sorted().map { it to it }) { model.setFilter(filter.copy(currency = it, page = 0)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { model.setFilter(filter.copy(kind = "", account = "", category = "", currency = "", page = 0)) }) { Text("Reset filters") }
                        Button(onClick = { showFilters = false }) { Text("Show results") }
                    }
                }
            }
            val close = { editor = null; editId = null; model.clearError() }
            if (editor != null) key(editor, editId) {
                val id = rememberSaveable { editId ?: UUID.randomUUID().toString() }
                val oldAccount = remember { data.accounts.firstOrNull { it.account.id == editId }?.account }
                val oldEntry = remember { (data.entries + data.recent).firstOrNull { it.id == editId } }
                val oldCategory = remember { data.categories.firstOrNull { it.id == editId } }
                val oldBudget = remember { data.budgets.firstOrNull { it.budget.id == editId }?.budget }
                val missing = editId != null && when (editor) {
                    "account" -> oldAccount == null; "entry", "detail" -> oldEntry == null
                    "category" -> oldCategory == null; "budget" -> oldBudget == null; else -> false
                }
                if (missing) AlertDialog(onDismissRequest = close, title = { Text("Record unavailable") },
                    text = { Text("Close this draft and find the record again. Nothing has been saved.") }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
                else when (editor) {
                    "account" -> AccountEditor(oldAccount, id, busy, error, close, model::account, preferredCurrency)
                    "entry" -> EntryEditor(oldEntry, id, EntryKind.valueOf(newKind), data, busy, error, close, model::entry, model::deleteEntry,
                        model.lastEntryAccount(EntryKind.valueOf(newKind)), model.lastEntryCategory(EntryKind.valueOf(newKind)),
                        preferredCurrency = if (section == "Overview") displayCurrency else preferredCurrency)
                    "detail" -> oldEntry?.let { EntryDetail(it, data, busy, error, close, { editor = "entry" }, model::deleteEntry, openSubscription) }
                    "category" -> CategoryEditor(oldCategory, id, busy, error, close, model::category)
                    "budget" -> BudgetEditor(
                        old = oldBudget,
                        id = id,
                        selectedMonth = if (section == "Overview") s.month else filter.month,
                        categories = data.categories,
                        busy = busy,
                        error = error,
                        close = close,
                        save = { value, create ->
                            model.budget(value, create) {
                                if (value.period != oldBudget?.period) {
                                    model.setFilter(model.filter.value.copy(month = value.period.startMonth, page = 0))
                                }
                                close()
                            }
                        },
                        delete = model::deleteBudget,
                        preferredCurrency = if (section == "Overview") displayCurrency else preferredCurrency,
                    )
                }
            }
        }
    }
}

@Composable internal fun MoneyHeading(text: String) { Text(text, Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge) }
@Composable
internal fun EntryRow(
    entry: EntryEntity,
    data: MoneySnapshot,
    overview: Boolean = false,
    edit: () -> Unit,
) {
    val accounts = listOfNotNull(entry.fromAccountId, entry.toAccountId).map { id ->
        if (isBudgetAccountId(id, entry.currencyCode)) BUDGET_ONLY_LABEL
        else data.accounts.firstOrNull { it.account.id == id }?.account?.name ?: "Account"
    }
    val category = data.categories.firstOrNull { it.id == entry.categoryId }?.name
    val kind = entry.kind.name.lowercase().replaceFirstChar(Char::uppercase)
    val icon = when (entry.kind) {
        EntryKind.EXPENSE -> AppIcons.NorthEast
        EntryKind.INCOME -> AppIcons.SouthWest
        EntryKind.TRANSFER -> AppIcons.SwapHoriz
    }
    val title = entry.note.ifBlank { category ?: kind }
    val context = "$kind · ${entry.currencyCode} · ${accounts.joinToString(" → ")}${category?.let { " · $it" } ?: ""}"
    val time = "%02d:%02d".format(entry.minuteOfDay / 60, entry.minuteOfDay % 60)
    val sign = when (entry.kind) {
        EntryKind.EXPENSE -> "−"
        EntryKind.INCOME -> "+"
        EntryKind.TRANSFER -> ""
    }
    val amount = sign + formatMoney(entry.amountMinor, entry.currencyCode)
    if (overview) {
        MoneyLedgerRow(icon, title, "$context · $time", amount, entry.kind == EntryKind.INCOME, edit)
    } else {
        LedgerRow(
            icon, title,
            "$context\n${LocalDate.ofEpochDay(entry.occurredOnEpochDay)} $time",
            amount,
            positive = entry.kind == EntryKind.INCOME,
            dense = true,
            onClick = edit,
        )
    }
}
@Composable internal fun BudgetBlock(value: BudgetProjection, categories: List<CategoryEntity>, edit: () -> Unit) {
    val summary = value.summary()
    val currency = value.budget.currencyCode
    val over = summary.remainingMinor < 0
    PorosCard(Modifier.clickable(onClick = edit), contentPadding = 16.dp, verticalSpacing = 10.dp) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconBadge(if (value.budget.categoryId == null) AppIcons.AccountBalanceWallet else AppIcons.Label)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(value.budget.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    value.budget.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name ?: "Category" }
                        ?: "Overall budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val period = value.budget.period
                if (period != BudgetPeriod.forMonth(period.startMonth)) {
                    Text(
                        budgetPeriodLabel(period),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("Edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("${formatMoney(value.spent, currency)} spent", style = MaterialTheme.typography.titleLarge)
        FinancialProgress(summary.fraction, over, "Limit · ${formatMoney(value.budget.limitMinor, currency)} · $currency")
        Text(
            "${formatMoney(java.math.BigInteger.valueOf(summary.remainingMinor).abs(), currency)} ${if (over) "over budget" else "remaining"}",
            style = MaterialTheme.typography.labelLarge,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

internal fun budgetPeriodLabel(period: BudgetPeriod): String {
    val dateFormat = DateTimeFormatter.ofPattern("d MMM uuuu")
    val start = LocalDate.ofEpochDay(period.startEpochDay).format(dateFormat)
    val end = LocalDate.ofEpochDay(period.endExclusiveEpochDay - 1).format(dateFormat)
    return "$start – $end"
}

@Composable
private fun AccountManagementRow(balance: AccountBalance, edit: () -> Unit) {
    val account = balance.account
    MoneyLedgerRow(
        when (account.type) {
            "CASH" -> AppIcons.Payments
            "BANK" -> AppIcons.AccountBalance
            "INVESTMENT" -> AppIcons.TrendingUp
            else -> AppIcons.AccountBalanceWallet
        },
        account.name,
        "${account.type.lowercase().replace('_', ' ')} · ${account.currencyCode}${if (account.archived) " · Archived" else ""} · Edit",
        formatMoney(balance.balance, account.currencyCode), false, edit,
    )
}

@Composable
internal fun FinancialEmptyState(title: String, detail: String) {
    PorosCard(contentPadding = 16.dp) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun Choice(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean = true, compact: Boolean = false, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val markDirty = LocalMarkDraftDirty.current
    Box {
        FilledTonalButton(onClick = { expanded = true }, enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
            modifier = (if (compact) Modifier else Modifier.fillMaxWidth()).heightIn(min = 48.dp)) {
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Choose"}", if (compact) Modifier else Modifier.weight(1f))
            Icon(AppIcons.ExpandMore, null, Modifier.padding(start = 8.dp).size(18.dp))
        }
        DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; if (key != value) markDirty(); choose(key) }) }
        }
    }
}
