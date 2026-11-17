package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jevon.personalos.data.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorFrame(title: String, busy: Boolean, error: String?, close: () -> Unit, save: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var validate by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun requestClose() { if (!busy) { if (dirty) confirmDiscard = true else close() } }
    val scroll = rememberScrollState()
    val message = inputError ?: error
    val focus = LocalFocusManager.current
    fun submit() {
        focus.clearFocus()
        validate = true
        inputError = null
        try { save() } catch (e: Exception) {
            inputError = when (e) { is IllegalArgumentException -> e.message; is java.time.DateTimeException -> "Use a valid date (YYYY-MM-DD), month (YYYY-MM), and time (HH:mm)"; else -> "Check the amount and required fields" }
        }
    }
    LaunchedEffect(message) { if (message != null) scroll.animateScrollTo(0) }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      PorosBackground {
        SystemBarAppearance()
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(), containerColor = Color.Transparent, topBar = {
            TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), navigationIcon = { IconButton(enabled = !busy, onClick = ::requestClose) { Icon(AppIcons.ArrowBack, "Cancel editing") } })
        }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(enabled = !busy, onClick = ::submit, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(AppIcons.CheckCircle, null, Modifier.size(20.dp))
                    Text(if (busy) "Saving…" else "Save", Modifier.padding(start = 8.dp))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.small) { Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
                CompositionLocalProvider(LocalMarkDraftDirty provides { dirty = true; inputError = null }, LocalValidateFields provides validate,
                    LocalEditorError provides message, LocalEditorDirty provides dirty) { body() }
                Spacer(Modifier.height(24.dp))
            }
        }
      }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard changes?") }, text = { Text("This draft has not been saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; close() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryDetail(entry: EntryEntity, data: MoneySnapshot, busy: Boolean, error: String?, close: () -> Unit,
    edit: () -> Unit, delete: (String, () -> Unit) -> Unit, openSubscription: (String) -> Unit) {
    var confirm by rememberSaveable(entry.id) { mutableStateOf(false) }
    val subscription = data.linkedSubscriptions[entry.id]
    val kind = entry.kind.name.lowercase().replaceFirstChar(Char::uppercase)
    val category = data.categories.firstOrNull { it.id == entry.categoryId }?.name ?: "Uncategorized"
    fun account(id: String?) = data.accounts.firstOrNull { it.account.id == id }?.account?.let { "${it.name}${if (it.archived) " · Archived" else ""}" } ?: "Account"
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PorosBackground {
            SystemBarAppearance()
            Scaffold(containerColor = Color.Transparent, topBar = {
                TopAppBar(title = { Text("Transaction detail", style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = { IconButton(enabled = !busy, onClick = close) { Icon(AppIcons.ArrowBack, "Close transaction") } })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PorosCard {
                        IconBadge(when (entry.kind) { EntryKind.EXPENSE -> AppIcons.NorthEast; EntryKind.INCOME -> AppIcons.SouthWest; EntryKind.TRANSFER -> AppIcons.SwapHoriz }, entry.kind == EntryKind.INCOME)
                        Text(entry.note.ifBlank { category }, style = MaterialTheme.typography.titleLarge)
                        Text(kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if (entry.kind == EntryKind.EXPENSE) "−" else if (entry.kind == EntryKind.INCOME) "+" else ""}${com.jevon.personalos.today.formatMoney(entry.amountMinor, entry.currencyCode)}",
                            style = MaterialTheme.typography.headlineLarge, color = if (entry.kind == EntryKind.INCOME) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.onSurface)
                        Text("${entry.currencyCode} · ${LocalDate.ofEpochDay(entry.occurredOnEpochDay)} · %02d:%02d".format(Locale.ROOT, entry.minuteOfDay / 60, entry.minuteOfDay % 60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PorosCard {
                        Text("Details & allocation", style = MaterialTheme.typography.titleMedium)
                        if (entry.kind != EntryKind.TRANSFER) DetailValue("Category", category)
                        if (entry.fromAccountId != null) DetailValue("From account", account(entry.fromAccountId))
                        if (entry.toAccountId != null) DetailValue(if (entry.kind == EntryKind.INCOME) "Account" else "To account", account(entry.toAccountId))
                        DetailValue("Notes", entry.note.ifBlank { "No notes" })
                    }
                    if (subscription != null) PorosCard {
                        Text("Subscription payment", style = MaterialTheme.typography.titleMedium)
                        Text("Correct or undo this payment in its subscription history. The linked expense and renewal stay consistent.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { close(); openSubscription(subscription) }) { Text("Open payment history") }
                    } else {
                        Button(enabled = !busy, onClick = edit, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Edit details") }
                        TextButton(enabled = !busy, onClick = { confirm = true }) { Text("Delete transaction", color = MaterialTheme.colorScheme.error) }
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirm) ConfirmDeletion("Delete transaction?", "This removes its balance effects from every account and its spending from budgets. A transfer is removed from both accounts together.", { confirm = false }) { confirm = false; delete(entry.id, close) }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable internal fun Field(label: String, value: String, enabled: Boolean, keyboard: KeyboardType = KeyboardType.Text, single: Boolean = true, error: String? = null,
    trailing: @Composable (() -> Unit)? = null, prominent: Boolean = false, change: (String) -> Unit) {
    val markDirty = LocalMarkDraftDirty.current
    val showError = LocalValidateFields.current && error != null
    val focus = LocalFocusManager.current
    OutlinedTextField(value, { if (it != value) markDirty(); change(it) }, enabled = enabled, label = { Text(label) }, singleLine = single, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small, isError = showError,
        textStyle = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
        supportingText = if (showError) { { Text(error!!) } } else null,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = if (prominent) Color.Transparent else LocalAtmosphere.current.panel,
            focusedContainerColor = if (prominent) Color.Transparent else LocalAtmosphere.current.panel,
            unfocusedBorderColor = if (prominent) Color.Transparent else LocalAtmosphere.current.border),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (prominent) ImeAction.Done else if (single) ImeAction.Next else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

private fun amountError(value: String, code: String, signed: Boolean = false): String? = runCatching { parseMoney(value, code.trim().uppercase(Locale.ROOT), signed) }.exceptionOrNull()?.message
private fun currencyError(code: String): String? = runCatching { currencyDigits(code.trim().uppercase(Locale.ROOT)) }.exceptionOrNull()?.let { "Use a valid ISO currency code" }

@Composable private fun ArchiveSwitch(archived: Boolean, enabled: Boolean, set: (Boolean) -> Unit) {
    val markDirty = LocalMarkDraftDirty.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Archived", Modifier.weight(1f))
        Switch(archived, { markDirty(); set(it) }, enabled = enabled, modifier = Modifier.semanticsForArchive())
    }
}

private fun Modifier.semanticsForArchive(): Modifier = this.then(Modifier.semantics { contentDescription = "Archived" })

@Composable
internal fun AccountEditor(old: AccountEntity?, id: String, busy: Boolean, error: String?, close: () -> Unit, save: (AccountEntity, Boolean, () -> Unit) -> Unit, preferredCurrency: String = DEFAULT_CURRENCY) {
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var code by rememberSaveable { mutableStateOf(old?.currencyCode ?: preferredCurrency) }
    var balance by rememberSaveable { mutableStateOf(old?.let { moneyInput(it.openingBalanceMinor, it.currencyCode) } ?: "0") }
    var type by rememberSaveable { mutableStateOf(old?.type ?: "BANK") }
    var notes by rememberSaveable { mutableStateOf(old?.notes ?: "") }
    var archived by rememberSaveable { mutableStateOf(old?.archived ?: false) }
    EditorFrame(if (old == null) "Create account" else "Edit account", busy, error, close, {
        require(name.isNotBlank()) { "Account name is required" }
        save(AccountEntity(id, name.trim(), code.trim().uppercase(Locale.ROOT), parseMoney(balance, code.trim().uppercase(Locale.ROOT), true), archived, type, notes.trim()), old == null, close)
    }) {
        Field("Account name", name, !busy, error = if (name.isBlank()) "Account name is required" else null) { name = it }
        Choice("Account type", type, accountTypes.map { it to it.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) }, !busy) { type = it }
        Field("Currency (ISO code, e.g. IDR)", code, !busy, error = currencyError(code)) { code = it.uppercase(Locale.ROOT) }
        Text("Currency can be corrected before the first transaction. With history, create a new IDR account instead; existing amounts are never converted or relabeled.", style = MaterialTheme.typography.bodySmall)
        Field("Opening balance", balance, !busy, KeyboardType.Decimal, error = amountError(balance, code, true)) { balance = it }
        val markDirty = LocalMarkDraftDirty.current
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = balance.startsWith("-"), enabled = !busy, role = androidx.compose.ui.semantics.Role.Checkbox,
            onValueChange = { negative -> markDirty(); balance = (if (negative) "-" else "") + balance.removePrefix("-") }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = balance.startsWith("-"), onCheckedChange = null, enabled = !busy)
            Text("Negative opening balance", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Text("Use a decimal point, without grouping. A negative opening balance is allowed. Editing it adjusts the current balance without creating income.", style = MaterialTheme.typography.bodySmall)
        Field("Account notes (optional)", notes, !busy, single = false) { notes = it }
        if (old != null) { ArchiveSwitch(archived, !busy) { archived = it }; Text("Archiving keeps every transaction and balance. Restore here to use this account again.") }
    }
}

@Composable
internal fun EntryEditor(old: EntryEntity?, id: String, initialKind: EntryKind, data: MoneySnapshot, busy: Boolean, error: String?, close: () -> Unit,
    save: (EntryEntity, Boolean, () -> Unit) -> Unit, delete: (String, () -> Unit) -> Unit,
    initialAccount: String = "", initialCategory: String = "") {
    val activeAccounts = data.accounts.map { it.account }.filter { !it.archived }
    val defaultAccount = if (initialKind == EntryKind.TRANSFER) "" else
        (activeAccounts.firstOrNull { it.id == initialAccount } ?: activeAccounts.singleOrNull())?.id.orEmpty()
    val defaultCategory = data.categories.firstOrNull { it.id == initialCategory && !it.archived && (it.kind == null || it.kind == initialKind.name) }?.id.orEmpty()
    var kind by rememberSaveable { mutableStateOf(old?.kind?.name ?: initialKind.name) }
    var source by rememberSaveable { mutableStateOf(old?.fromAccountId ?: if (initialKind != EntryKind.INCOME) defaultAccount else "") }
    var target by rememberSaveable { mutableStateOf(old?.toAccountId ?: if (initialKind == EntryKind.INCOME) defaultAccount else "") }
    var category by rememberSaveable { mutableStateOf(old?.categoryId ?: if (old == null) defaultCategory else "") }
    var amount by rememberSaveable { mutableStateOf(old?.let { moneyInput(it.amountMinor, it.currencyCode) } ?: "") }
    var date by rememberSaveable { mutableStateOf(old?.let { LocalDate.ofEpochDay(it.occurredOnEpochDay).toString() } ?: LocalDate.now().toString()) }
    var time by rememberSaveable { mutableStateOf(old?.let { "%02d:%02d".format(Locale.ROOT, it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var note by rememberSaveable { mutableStateOf(old?.note ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selectedKind = EntryKind.valueOf(kind)
    val accounts = data.accounts.map { it.account }.filter { !it.archived || it.id == old?.fromAccountId || it.id == old?.toAccountId }
    val primary = accounts.firstOrNull { it.id == if (selectedKind == EntryKind.INCOME) target else source }
    val categories = data.categories.filter { (!it.archived || it.id == old?.categoryId) && (it.kind == null || it.kind == kind) }
    EditorFrame(if (old == null) "Add ${kind.lowercase()}" else "Edit transaction", busy, error, close, {
        val currency = requireNotNull(primary) { "Choose an account" }.currencyCode
        if (selectedKind == EntryKind.TRANSFER) {
            require(source != target) { "Choose two different accounts" }
            require(accounts.firstOrNull { it.id == target }?.currencyCode == currency) { "Choose a destination with the same currency" }
        }
        val clock = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT))
        save(EntryEntity(id, selectedKind, parseMoney(amount, currency), currency, LocalDate.parse(date).toEpochDay(),
            if (selectedKind != EntryKind.INCOME) source.ifBlank { null } else null,
            if (selectedKind != EntryKind.EXPENSE) target.ifBlank { null } else null,
            if (selectedKind == EntryKind.TRANSFER) null else category.ifBlank { null }, note.trim(), clock.hour * 60 + clock.minute), old == null, close)
    }) {
        if (accounts.isEmpty()) Text("Create an account in Money before recording a transaction.", color = MaterialTheme.colorScheme.error)
        PorosCard(contentPadding = 16.dp, verticalSpacing = 4.dp) {
            AmountField("Amount${primary?.let { " (${it.currencyCode})" } ?: ""}", amount, !busy,
                amountError(amount, primary?.currencyCode ?: DEFAULT_CURRENCY)) { amount = it }
            Text(primary?.let { "${it.currencyCode} · ${it.name}" } ?: "Choose an account to set the currency.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selectedKind != EntryKind.INCOME) Choice("From account", source, accounts.map { it.id to "${it.name} · ${it.currencyCode}${if (it.archived) " (archived)" else ""}" }, !busy) { source = it; if (selectedKind == EntryKind.TRANSFER) target = "" }
        if (selectedKind != EntryKind.EXPENSE) Choice(if (selectedKind == EntryKind.INCOME) "Account" else "To account", target,
            accounts.filter { selectedKind != EntryKind.TRANSFER || it.id != source && it.currencyCode == primary?.currencyCode }.map { it.id to "${it.name} · ${it.currencyCode}" }, !busy) { target = it }
        if (selectedKind != EntryKind.TRANSFER) Choice("Category", category, listOf("" to "Uncategorized") + categories.map { it.id to it.name }, !busy) { category = it }
        val summary = listOfNotNull(
            runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM uuuu")) }.getOrNull(),
            if (note.isNotBlank()) "Description added" else null).joinToString(" · ")
        MoreOptions(summary) {
            Field("Transaction notes (optional)", note, !busy, single = false) { note = it }
            BillingDateField("Date", date, !busy, compact = true) { date = it }
            CalendarTimeField("Time", time, !busy, compact = true) { time = it }
            Choice("Transaction kind", kind, EntryKind.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) }, !busy) {
                kind = it
                if (data.categories.firstOrNull { c -> c.id == category }?.kind?.let { categoryKind -> categoryKind != it } == true || it == "TRANSFER") category = ""
            }
        }
        if (old != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete transaction", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) ConfirmDeletion("Delete transaction?", "This removes its balance effects from every account and its spending from budgets. A transfer is removed from both accounts together.", { confirmDelete = false }) { confirmDelete = false; delete(id, close) }
}

@Composable
internal fun AmountField(label: String, value: String, enabled: Boolean, error: String? = null, change: (String) -> Unit) {
    val dirty = LocalMarkDraftDirty.current
    val focus = LocalFocusManager.current
    val invalid = LocalValidateFields.current && error != null
    OutlinedTextField(value, { if (it != value) dirty(); change(it) }, enabled = enabled, label = { Text(label) },
        placeholder = { Text("0", style = MaterialTheme.typography.displaySmall) },
        textStyle = MaterialTheme.typography.displaySmall, singleLine = true, isError = invalid,
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        supportingText = if (invalid) { { Text(error!!, style = MaterialTheme.typography.bodySmall) } } else null,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

@Composable
internal fun CategoryEditor(old: CategoryEntity?, id: String, busy: Boolean, error: String?, close: () -> Unit, save: (CategoryEntity, Boolean, () -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(old?.kind ?: if (old == null) "EXPENSE" else "") }
    var archived by rememberSaveable { mutableStateOf(old?.archived ?: false) }
    EditorFrame(if (old == null) "Add category" else "Edit category", busy, error, close, {
        require(name.isNotBlank()) { "Category name is required" }
        save(CategoryEntity(id, name.trim(), kind.ifBlank { null }, archived), old == null, close)
    }) {
        Field("Category name", name, !busy, error = if (name.isBlank()) "Category name is required" else null) { name = it }
        Choice("Category type", kind, listOf("EXPENSE" to "Expense", "INCOME" to "Income") + (if (old != null && old.kind == null) listOf("" to "Expense / income (legacy)") else emptyList()), old == null && !busy) { kind = it }
        Text("Renaming updates the label on historical transactions. The type stays fixed; create another category to change it.")
        if (old != null) ArchiveSwitch(archived, !busy) { archived = it }
    }
}

@Composable
internal fun BudgetEditor(old: BudgetEntity?, id: String, selectedMonth: YearMonth, categories: List<CategoryEntity>, currencies: List<String>, busy: Boolean, error: String?, close: () -> Unit,
    save: (BudgetEntity, Boolean, () -> Unit) -> Unit, delete: (String, () -> Unit) -> Unit, preferredCurrency: String = DEFAULT_CURRENCY) {
    var name by rememberSaveable { mutableStateOf(old?.name ?: "Monthly budget") }
    var month by rememberSaveable { mutableStateOf(old?.let { YearMonth.from(LocalDate.ofEpochDay(it.startEpochDay)).toString() } ?: selectedMonth.toString()) }
    var code by rememberSaveable { mutableStateOf(old?.currencyCode ?: preferredCurrency) }
    var limit by rememberSaveable { mutableStateOf(old?.let { moneyInput(it.limitMinor, it.currencyCode) } ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.categoryId ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    EditorFrame(if (old == null) "Create budget" else "Edit budget", busy, error, close, {
        require(name.isNotBlank()) { "Budget name is required" }
        val period = YearMonth.parse(month)
        val currency = code.trim().uppercase(Locale.ROOT)
        save(BudgetEntity(id, name.trim(), currency, parseMoney(limit, currency), period.atDay(1).toEpochDay(), period.plusMonths(1).atDay(1).toEpochDay(), category.ifBlank { null }), old == null, close)
    }) {
        Field("Budget name", name, !busy, error = if (name.isBlank()) "Budget name is required" else null) { name = it }
        Field("Month (YYYY-MM)", month, !busy, error = if (runCatching { YearMonth.parse(month) }.isFailure) "Use a valid month, e.g. 2026-10" else null) { month = it }
        Field("Currency (ISO code)", code, !busy, error = currencyError(code)) { code = it.uppercase(Locale.ROOT) }
        Field("Budget limit", limit, !busy, KeyboardType.Decimal, error = amountError(limit, code)) { limit = it }
        Choice("Scope", category, listOf("" to "Overall") + categories.filter { it.kind != "INCOME" && (!it.archived || it.id == old?.categoryId) }.map { it.id to it.name }, !busy) { category = it }
        Text("One overall limit per currency and month. Category limits overlap the overall budget; they are never added together. Only recorded expenses count.")
        Text("Choose the current month's overall budget on Today. Automatic uses your preferred currency from Settings.", style = MaterialTheme.typography.bodySmall)
        if (old != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete budget", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) ConfirmDeletion("Delete budget?", "Transactions and account balances are kept.", { confirmDelete = false }) { confirmDelete = false; delete(id, close) }
}

@Composable private fun ConfirmDeletion(title: String, text: String, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
