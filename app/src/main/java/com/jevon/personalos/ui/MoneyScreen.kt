package com.jevon.personalos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
fun MoneyScreen(model: MoneyViewModel, requested: String?, handled: () -> Unit, subscriptions: () -> Unit, preferredCurrency: String = DEFAULT_CURRENCY) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("Overview") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var newKind by rememberSaveable { mutableStateOf("EXPENSE") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showTestReview by rememberSaveable { mutableStateOf(false) }
    var expandedCandidate by rememberSaveable { mutableStateOf<String?>(null) }
    var overviewCurrency by rememberSaveable(preferredCurrency) { mutableStateOf(preferredCurrency) }
    fun edit(type: String, id: String? = null) { model.clearError(); editId = id; editor = type }
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
            val displayCurrency = overviewCurrency.takeIf { code -> currencyBalances.any { it.code == code } } ?: currencyBalances.firstOrNull()?.code ?: preferredCurrency
            LazyColumn(Modifier.fillMaxSize().testTag("MoneyList"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (section == "Overview") item { BalanceHeader(data, displayCurrency, { overviewCurrency = it }, { edit("account") }) }
                item {
                    val sections = listOf("Overview", "History", "Accounts", "Budgets", "Categories")
                    PrimaryScrollableTabRow(selectedTabIndex = sections.indexOf(section), edgePadding = 0.dp,
                        minTabWidth = 64.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent, divider = {}) {
                        sections.forEach { label ->
                            Tab(selected = section == label, onClick = { section = label },
                                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (section in listOf("Accounts", "History", "Budgets", "Categories")) item {
                    TextButton(onClick = { showTestReview = true }) { Text("Review possible test records") }
                }
                if (section in listOf("History", "Budgets")) item { MonthSelector(filter.month,
                    { model.setFilter(filter.copy(month = filter.month.minusMonths(1), page = 0)) }, { model.setFilter(filter.copy(month = filter.month.plusMonths(1), page = 0)) }) }
                if (error != null && editor == null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                when (section) {
                    "Overview" -> moneyOverview(data, displayCurrency, filter.month, { type, id -> edit(type, id) },
                        { kind -> newKind = kind; edit("entry") }, { section = it }, { model.setFilter(filter.copy(month = it, page = 0)) }, subscriptions)
                    "Accounts" -> {
                        item { SectionHeading("Accounts", "Create account", { edit("account") }); Text("Balances include all dates, including future entries. Archived accounts retain history and can be restored.") }
                        items(data.accounts, key = { it.account.id }) { AccountRow(it) { edit("account", it.account.id) } }
                    }
                    "Categories" -> {
                        item { SectionHeading("Categories", "Add category", { edit("category") }); Text("Rename or archive without removing past transactions. Archived categories remain visible in history.") }
                        items(data.categories, key = { it.id }) { category ->
                            LedgerRow(AppIcons.Label, category.name, "${category.kind?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Expense / income"}${if (category.archived) " · Archived" else ""}", "Edit", onClick = { edit("category", category.id) })
                        }
                    }
                    "Budgets" -> {
                        item { SectionHeading("Monthly budgets", "Create budget", { edit("budget") }); Text("The overall budget includes all expenses. Category budgets are separate limits within it; they are never added to the overall limit.") }
                        if (data.budgets.isEmpty()) item { Text("No budget for ${filter.month}. Create an overall plan or a category limit.") }
                        items(data.budgets, key = { it.budget.id }) { BudgetBlock(it, data.categories) { edit("budget", it.budget.id) } }
                    }
                    "History" -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeading("History · ${data.count} this month", "Filters", { showFilters = true })
                                OutlinedTextField(filter.search, { model.setFilter(filter.copy(search = it, page = 0)) }, label = { Text("Search notes, account, category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                val selected = listOfNotNull(filter.kind.ifBlank { null }?.lowercase()?.replaceFirstChar(Char::uppercase),
                                    data.accounts.firstOrNull { it.account.id == filter.account }?.account?.name,
                                    data.categories.firstOrNull { it.id == filter.category }?.name, filter.currency.ifBlank { null })
                                if (selected.isNotEmpty()) Text("Filtered by ${selected.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (data.entries.isEmpty()) item { Text("No transactions match this month and these filters.") }
                        data.entries.take(50).groupBy { it.occurredOnEpochDay }.forEach { (date, entries) ->
                            item(key = "date-$date") { Text(LocalDate.ofEpochDay(date).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
                            items(entries, key = { it.id }) { EntryRow(it, data) { edit("entry", it.id) } }
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
            if (showTestReview) ModalBottomSheet(onDismissRequest = { showTestReview = false }) {
                val review by model.testReview.collectAsStateWithLifecycle()
                LazyColumn(Modifier.fillMaxWidth().testTag("TestRecordReviewList"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("Possible test records", style = MaterialTheme.typography.titleLarge)
                        Text("Only exact P1 test / P15 restore labels from earlier device tests are flagged. A label is a hint, not proof. Related records may be personal. Nothing is deleted here.")
                        Text("Use the existing editors to archive accounts/categories or review individual deletions. Subscription payments must be corrected in Subscriptions. There is no bulk cleanup.", style = MaterialTheme.typography.bodyMedium)
                    }
                    val result = review
                    when {
                        result == null -> item { CircularProgressIndicator() }
                        result.isFailure -> item { Text("Could not load the review. Close and reopen it to try again. Your records are unchanged.") }
                        result.getOrThrow().isEmpty() -> item { Text("No records with these known development labels.") }
                        else -> result.getOrThrow().forEach { candidate ->
                            val candidateKey = "${candidate.section}/${candidate.id}"
                            item(key = candidateKey) {
                                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                                Text("${candidate.section} · ${candidate.id}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { expandedCandidate = if (expandedCandidate == candidateKey) null else candidateKey }) {
                                    Text(if (expandedCandidate == candidateKey) "Hide details" else "Review details and relationships (${candidate.details.size})")
                                }
                            }
                            if (expandedCandidate == candidateKey) {
                                items(candidate.details.size, key = { "$candidateKey/detail/$it" }) { index ->
                                    Text(candidate.details[index], style = MaterialTheme.typography.bodyMedium)
                                }
                                item(key = "$candidateKey/open") {
                                    TextButton(onClick = { section = candidate.section; showTestReview = false }) { Text("Open ${candidate.section}") }
                                }
                            }
                            item(key = "$candidateKey/divider") { HorizontalDivider() }
                        }
                    }
                    item { TextButton(onClick = { showTestReview = false }) { Text("Close review") } }
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
                    "account" -> oldAccount == null; "entry" -> oldEntry == null
                    "category" -> oldCategory == null; "budget" -> oldBudget == null; else -> false
                }
                if (missing) AlertDialog(onDismissRequest = close, title = { Text("Record unavailable") },
                    text = { Text("Close this draft and find the record again. Nothing has been saved.") }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
                else when (editor) {
                    "account" -> AccountEditor(oldAccount, id, busy, error, close, model::account, preferredCurrency)
                    "entry" -> EntryEditor(oldEntry, id, EntryKind.valueOf(newKind), data, busy, error, close, model::entry, model::deleteEntry)
                    "category" -> CategoryEditor(oldCategory, id, busy, error, close, model::category)
                    "budget" -> BudgetEditor(oldBudget, id, filter.month, data.categories, data.accounts.map { it.account.currencyCode }.distinct().sorted(), busy, error, close, model::budget, model::deleteBudget, preferredCurrency)
                }
            }
        }
    }
}

@Composable internal fun MoneyHeading(text: String) { Text(text, Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge) }
@Composable internal fun AccountRow(balance: AccountBalance, edit: () -> Unit) {
    LedgerRow(when (balance.account.type) { "CASH" -> AppIcons.Payments; "BANK" -> AppIcons.AccountBalance; "INVESTMENT" -> AppIcons.TrendingUp; else -> AppIcons.AccountBalanceWallet },
        balance.account.name, "${balance.account.type.lowercase().replace('_', ' ')} · ${balance.account.currencyCode}${if (balance.account.archived) " · Archived" else ""}", formatMoney(balance.balance, balance.account.currencyCode), onClick = edit)
}
@Composable internal fun EntryRow(entry: EntryEntity, data: MoneySnapshot, edit: () -> Unit) {
    val accounts = listOfNotNull(entry.fromAccountId, entry.toAccountId).map { id -> data.accounts.firstOrNull { it.account.id == id }?.account?.name ?: "Account" }
    val category = data.categories.firstOrNull { it.id == entry.categoryId }?.name
    LedgerRow(when (entry.kind) { EntryKind.EXPENSE -> AppIcons.NorthEast; EntryKind.INCOME -> AppIcons.SouthWest; EntryKind.TRANSFER -> AppIcons.SwapHoriz },
        entry.note.ifBlank { category ?: entry.kind.name.lowercase().replaceFirstChar(Char::uppercase) },
        "${entry.kind.name.lowercase().replaceFirstChar(Char::uppercase)} · ${entry.currencyCode} · ${accounts.joinToString(" → ")}${category?.let { " · $it" } ?: ""}\n${LocalDate.ofEpochDay(entry.occurredOnEpochDay)} %02d:%02d".format(entry.minuteOfDay / 60, entry.minuteOfDay % 60),
        "${if (entry.kind == EntryKind.EXPENSE) "−" else if (entry.kind == EntryKind.INCOME) "+" else ""}${formatMoney(entry.amountMinor, entry.currencyCode)}", positive = entry.kind == EntryKind.INCOME, onClick = edit)
}
@Composable internal fun BudgetBlock(value: BudgetProjection, categories: List<CategoryEntity>, edit: () -> Unit) {
    val summary = value.summary()
    FrostedPanel(Modifier.clickable(onClick = edit)) {
            Text("${value.budget.name} · ${value.budget.currencyCode}", style = MaterialTheme.typography.titleMedium)
            Text(value.budget.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name ?: "Category" } ?: "Overall monthly budget", style = MaterialTheme.typography.bodyMedium)
            Text("${formatMoney(kotlin.math.abs(summary.remainingMinor), value.budget.currencyCode)} ${if (summary.remainingMinor < 0) "over budget" else "remaining"}", style = MaterialTheme.typography.headlineSmall,
                color = if (summary.remainingMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            FinancialProgress(summary.fraction, summary.remainingMinor < 0, "${formatMoney(value.spent, value.budget.currencyCode)} spent of ${formatMoney(value.budget.limitMinor, value.budget.currencyCode)}")
    }
}

@Composable internal fun Choice(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean = true, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val markDirty = LocalMarkDraftDirty.current
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Choose"}", Modifier.weight(1f))
            Icon(AppIcons.ExpandMore, null, Modifier.padding(start = 8.dp).size(18.dp))
        }
        DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; if (key != value) markDirty(); choose(key) }) }
        }
    }
}
