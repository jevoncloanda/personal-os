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
    LaunchedEffect(message) { if (message != null) scroll.animateScrollTo(0) }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      AtmosphericBackground {
        SystemBarAppearance()
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(), containerColor = Color.Transparent, topBar = {
            TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent), navigationIcon = { IconButton(enabled = !busy, onClick = ::requestClose) { Icon(AppIcons.ArrowBack, "Cancel editing") } },
                actions = { Button(enabled = !busy, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), modifier = Modifier.padding(end = 16.dp), onClick = {
                    validate = true
                    inputError = null
                    try { save() } catch (e: Exception) {
                        inputError = when (e) { is IllegalArgumentException -> e.message; is java.time.DateTimeException -> "Use a valid date (YYYY-MM-DD), month (YYYY-MM), and time (HH:mm)"; else -> "Check the amount and required fields" }
                    }
                }) { Text(if (busy) "Saving…" else "Save") } })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.small) { Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
                CompositionLocalProvider(LocalMarkDraftDirty provides { dirty = true; inputError = null }, LocalValidateFields provides validate) { body() }
                Spacer(Modifier.height(24.dp))
            }
        }
      }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard changes?") }, text = { Text("This draft has not been saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; close() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
}

@Composable internal fun Field(label: String, value: String, enabled: Boolean, keyboard: KeyboardType = KeyboardType.Text, single: Boolean = true, error: String? = null, change: (String) -> Unit) {
    val markDirty = LocalMarkDraftDirty.current
    val showError = LocalValidateFields.current && error != null
    OutlinedTextField(value, { if (it != value) markDirty(); change(it) }, enabled = enabled, label = { Text(label) }, singleLine = single, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small, isError = showError,
        supportingText = if (showError) { { Text(error!!) } } else null,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = LocalAtmosphere.current.panel, focusedContainerColor = LocalAtmosphere.current.panel, unfocusedBorderColor = LocalAtmosphere.current.border),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (single) ImeAction.Next else ImeAction.Default))
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
    save: (EntryEntity, Boolean, () -> Unit) -> Unit, delete: (String, () -> Unit) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(old?.kind?.name ?: initialKind.name) }
    var source by rememberSaveable { mutableStateOf(old?.fromAccountId ?: "") }
    var target by rememberSaveable { mutableStateOf(old?.toAccountId ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.categoryId ?: "") }
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
        Choice("Transaction kind", kind, EntryKind.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) }, !busy) { kind = it; category = "" }
        if (selectedKind != EntryKind.INCOME) Choice("From account", source, accounts.map { it.id to "${it.name} · ${it.currencyCode}${if (it.archived) " (archived)" else ""}" }, !busy) { source = it; if (selectedKind == EntryKind.TRANSFER) target = "" }
        if (selectedKind != EntryKind.EXPENSE) Choice(if (selectedKind == EntryKind.INCOME) "Account" else "To account", target,
            accounts.filter { selectedKind != EntryKind.TRANSFER || it.id != source && it.currencyCode == primary?.currencyCode }.map { it.id to "${it.name} · ${it.currencyCode}" }, !busy) { target = it }
        Field("Amount${primary?.let { " (${it.currencyCode})" } ?: ""}", amount, !busy, KeyboardType.Decimal, error = amountError(amount, primary?.currencyCode ?: DEFAULT_CURRENCY)) { amount = it }
        Text("Use a decimal point, without grouping. ${primary?.let { "${currencyDigits(it.currencyCode)} decimal places maximum." } ?: "Choose an account to set the currency."}", style = MaterialTheme.typography.bodySmall)
        if (selectedKind != EntryKind.TRANSFER) Choice("Category", category, listOf("" to "Uncategorized") + categories.map { it.id to it.name }, !busy) { category = it }
        Field("Date (YYYY-MM-DD)", date, !busy, error = if (runCatching { LocalDate.parse(date) }.isFailure) "Use a valid date, e.g. 2026-10-24" else null) { date = it }
        Field("Time (HH:mm)", time, !busy, KeyboardType.Ascii, error = if (runCatching { LocalTime.parse(time) }.isFailure) "Use 24-hour time, e.g. 09:30" else null) { time = it }
        Text("The effective date assigns spending to a month. Time is local; changing timezone does not move historical spending.", style = MaterialTheme.typography.bodySmall)
        Field("Transaction notes (optional)", note, !busy, single = false) { note = it }
        if (old != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete transaction", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) ConfirmDeletion("Delete transaction?", "This removes its balance effects from every account and its spending from budgets. A transfer is removed from both accounts together.", { confirmDelete = false }) { confirmDelete = false; delete(id, close) }
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
