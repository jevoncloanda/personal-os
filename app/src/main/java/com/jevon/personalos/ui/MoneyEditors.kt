package com.jevon.personalos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error as fieldError
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
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorFrame(
    title: String,
    busy: Boolean,
    error: String?,
    close: () -> Unit,
    save: () -> Unit,
    moneyKind: EntryKind? = null,
    revealOptionsError: Boolean = false,
    compactLayout: Boolean = false,
    saveLabel: String? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var validate by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun requestClose() { if (!busy) { if (dirty) confirmDiscard = true else close() } }
    val scroll = rememberScrollState()
    val message = inputError ?: error
    val focus = LocalFocusManager.current
    val compact = moneyKind != null || compactLayout
    fun submit() {
        focus.clearFocus()
        validate = true
        inputError = null
        try { save() } catch (e: Exception) {
            inputError = when (e) { is IllegalArgumentException -> e.message; is java.time.DateTimeException -> "Use a valid date (YYYY-MM-DD), month (YYYY-MM), and time (HH:mm)"; else -> "Check the amount and required fields" }
        }
    }
    LaunchedEffect(message, revealOptionsError) {
        if (message != null && !revealOptionsError) scroll.animateScrollTo(0)
    }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      PorosBackground {
        SystemBarAppearance()
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(), containerColor = Color.Transparent, topBar = {
            TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), navigationIcon = { IconButton(enabled = !busy, onClick = ::requestClose) { Icon(AppIcons.ArrowBack, "Cancel editing") } })
        }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    enabled = !busy,
                    onClick = ::submit,
                    shape = if (compact) RoundedCornerShape(12.dp)
                        else MaterialTheme.shapes.medium,
                    colors = if (moneyKind == null) ButtonDefaults.buttonColors()
                        else ButtonDefaults.buttonColors(
                            containerColor = if (moneyKind == EntryKind.INCOME)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (moneyKind == EntryKind.INCOME)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    modifier = Modifier.navigationBarsPadding()
                        .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = 12.dp)
                        .fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(AppIcons.CheckCircle, null, Modifier.size(20.dp))
                    Text(
                        when {
                            busy -> "Saving…"
                            saveLabel != null -> saveLabel
                            moneyKind != null -> "Save ${moneyKind.name.lowercase()}"
                            else -> "Save"
                        },
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(scroll)
                    .padding(
                        horizontal = if (compact) 16.dp else 20.dp,
                        vertical = if (compact) 8.dp else 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 12.dp),
            ) {
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
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconBadge(when (entry.kind) { EntryKind.EXPENSE -> AppIcons.NorthEast; EntryKind.INCOME -> AppIcons.SouthWest; EntryKind.TRANSFER -> AppIcons.SwapHoriz }, entry.kind == EntryKind.INCOME)
                        Text(kind.uppercase(), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if (entry.kind == EntryKind.EXPENSE) "−" else if (entry.kind == EntryKind.INCOME) "+" else ""}${com.jevon.personalos.today.formatMoney(entry.amountMinor, entry.currencyCode)}",
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                            color = when (entry.kind) {
                                EntryKind.INCOME -> LocalAtmosphere.current.positive
                                EntryKind.EXPENSE -> MaterialTheme.colorScheme.error
                                EntryKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
                            })
                        Text("${entry.currencyCode} · ${LocalDate.ofEpochDay(entry.occurredOnEpochDay)} · %02d:%02d".format(Locale.ROOT, entry.minuteOfDay / 60, entry.minuteOfDay % 60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PorosCard(contentPadding = 16.dp, verticalSpacing = 4.dp) {
                        Text("Details & allocation", style = MaterialTheme.typography.titleMedium)
                        DetailValue("Type", kind)
                        if (entry.kind != EntryKind.TRANSFER) DetailValue("Category", category)
                        if (isBudgetAccountId(entry.fromAccountId, entry.currencyCode)) {
                            DetailValue("Recorded against", BUDGET_ONLY_LABEL)
                        } else if (entry.fromAccountId != null) {
                            DetailValue("From account", account(entry.fromAccountId))
                        }
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
internal fun DetailValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    single: Boolean = true,
    error: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    prominent: Boolean = false,
    autoFocus: Boolean = false,
    change: (String) -> Unit,
) {
    val markDirty = LocalMarkDraftDirty.current
    val showError = LocalValidateFields.current && error != null
    val focus = LocalFocusManager.current
    if (prominent) {
        val requester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        LaunchedEffect(autoFocus) {
            if (autoFocus && enabled) requester.requestFocus()
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = {
                    if (it != value) markDirty()
                    change(it)
                },
                enabled = enabled,
                singleLine = single,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (enabled) 1f else 0.38f,
                    ),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics {
                        contentDescription = label
                        if (showError) fieldError(requireNotNull(error))
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboard,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                decorationBox = { input ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) Text("Add ${label.lowercase(Locale.ROOT)}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            input()
                        }
                        trailing?.invoke()
                    }
                },
            )
            Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(
                when {
                    showError -> MaterialTheme.colorScheme.error
                    focused -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
            ))
            if (showError) Text(
                requireNotNull(error), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }
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
    }, compactLayout = true, saveLabel = "Save account") {
        Field("Account name", name, !busy, prominent = true, error = if (name.isBlank()) "Account name is required" else null) { name = it }
        FinancialChoice(
            "Account type", type,
            accountTypes.map {
                it to it.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
            },
            !busy,
        ) { type = it }
        Field("Currency (ISO code, e.g. IDR)", code, !busy, error = currencyError(code)) { code = it.uppercase(Locale.ROOT) }
        Text("Currency can be corrected before the first transaction. With history, create a new IDR account instead; existing amounts are never converted or relabeled.", style = MaterialTheme.typography.bodySmall)
        AmountField("Opening balance", balance, !busy, error = amountError(balance, code, true), currency = code) { balance = it }
        val markDirty = LocalMarkDraftDirty.current
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = balance.startsWith("-"), enabled = !busy, role = androidx.compose.ui.semantics.Role.Checkbox,
            onValueChange = { negative -> markDirty(); balance = (if (negative) "-" else "") + balance.removePrefix("-") }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = balance.startsWith("-"), onCheckedChange = null, enabled = !busy)
            Text("Negative opening balance", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Text("Use a decimal point, without grouping. A negative opening balance is allowed. Editing it adjusts the current balance without creating income.", style = MaterialTheme.typography.bodySmall)
        MoreOptions(
            listOfNotNull(
                if (notes.isNotBlank()) "Notes added" else null,
                if (archived) "Archived" else null,
            ).joinToString(" · ").ifBlank { "Notes & archive" },
            enabled = !busy,
        ) {
            Field("Account notes (optional)", notes, !busy, single = false) { notes = it }
            if (old != null) {
                ArchiveSwitch(archived, !busy) { archived = it }
                Text("Archiving keeps every transaction and balance. Restore here to use this account again.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun EntryEditor(
    old: EntryEntity?,
    id: String,
    initialKind: EntryKind,
    data: MoneySnapshot,
    busy: Boolean,
    error: String?,
    close: () -> Unit,
    save: (EntryEntity, Boolean, () -> Unit) -> Unit,
    delete: (String, () -> Unit) -> Unit,
    initialAccount: String = "",
    initialCategory: String = "",
    preferredCurrency: String = DEFAULT_CURRENCY,
) {
    val activeAccounts = data.accounts.map { it.account }.filter { !it.archived }
    val defaultAccount = if (initialKind == EntryKind.TRANSFER) "" else
        (activeAccounts.firstOrNull { it.id == initialAccount } ?: activeAccounts.singleOrNull())
            ?.id.orEmpty()
    val defaultCategory = data.categories.firstOrNull {
        it.id == initialCategory && !it.archived &&
            (it.kind == null || it.kind == initialKind.name)
    }?.id.orEmpty()
    var kind by rememberSaveable { mutableStateOf(old?.kind?.name ?: initialKind.name) }
    // Existing records retain their stored account IDs, including IDs that need correction.
    var source by rememberSaveable {
        mutableStateOf(
            if (old != null) {
                old.fromAccountId.orEmpty().takeUnless { isBudgetAccountId(it, old.currencyCode) }.orEmpty()
            } else if (initialKind == EntryKind.TRANSFER) defaultAccount else "",
        )
    }
    var target by rememberSaveable {
        mutableStateOf(
            if (old != null) old.toAccountId.orEmpty()
            else if (initialKind == EntryKind.INCOME) defaultAccount else "",
        )
    }
    var category by rememberSaveable {
        mutableStateOf(old?.categoryId ?: if (old == null) defaultCategory else "")
    }
    var amount by rememberSaveable {
        mutableStateOf(old?.let { moneyInput(it.amountMinor, it.currencyCode) } ?: "")
    }
    var date by rememberSaveable {
        mutableStateOf(
            old?.let { LocalDate.ofEpochDay(it.occurredOnEpochDay).toString() }
                ?: LocalDate.now().toString(),
        )
    }
    var time by rememberSaveable {
        mutableStateOf(
            old?.let { "%02d:%02d".format(Locale.ROOT, it.minuteOfDay / 60, it.minuteOfDay % 60) }
                ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
        )
    }
    var note by rememberSaveable { mutableStateOf(old?.note ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selectedKind = EntryKind.valueOf(kind)
    var budgetCurrency by rememberSaveable { mutableStateOf(old?.currencyCode ?: preferredCurrency) }
    val budgetOnly = selectedKind == EntryKind.EXPENSE && source.isBlank()
    val accounts = data.accounts.map { it.account }.filter {
        !it.archived || it.id == old?.fromAccountId || it.id == old?.toAccountId
    }
    val primaryId = if (selectedKind == EntryKind.INCOME) target else source
    val primary = accounts.firstOrNull { it.id == primaryId }
    val entryCurrency = if (budgetOnly) budgetCurrency else primary?.currencyCode
    val categories = data.categories.filter {
        (!it.archived || it.id == old?.categoryId) && (it.kind == null || it.kind == kind)
    }
    val timeFormat = remember {
        DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT)
    }
    val dateError = if (runCatching { LocalDate.parse(date) }.isFailure) "Choose a valid date" else null
    val timeError = if (runCatching { LocalTime.parse(time, timeFormat) }.isFailure)
        "Choose a valid time" else null
    val dateRequest = remember { BringIntoViewRequester() }
    val timeRequest = remember { BringIntoViewRequester() }
    EditorFrame(
        title = if (old == null) "Add ${kind.lowercase()}" else "Edit ${kind.lowercase()}",
        busy = busy,
        error = error,
        close = close,
        save = {
            val currency = requireNotNull(entryCurrency) { "Choose an account" }
            if (selectedKind == EntryKind.TRANSFER) {
                require(source != target) { "Choose two different accounts" }
                require(accounts.firstOrNull { it.id == target }?.currencyCode == currency) {
                    "Choose a destination with the same currency"
                }
            } else require(category.isBlank() || categories.any { it.id == category }) {
                "Choose an available category or Uncategorized"
            }
            val clock = LocalTime.parse(time, timeFormat)
            save(
                EntryEntity(
                    id,
                    selectedKind,
                    parseMoney(amount, currency),
                    currency,
                    LocalDate.parse(date).toEpochDay(),
                    when (selectedKind) {
                        EntryKind.EXPENSE -> if (budgetOnly) budgetAccountId(currency) else source
                        EntryKind.TRANSFER -> source
                        EntryKind.INCOME -> null
                    },
                    if (selectedKind != EntryKind.EXPENSE) target.ifBlank { null } else null,
                    if (selectedKind == EntryKind.TRANSFER) null else category.ifBlank { null },
                    note.trim(),
                    clock.hour * 60 + clock.minute,
                ),
                old == null,
                close,
            )
        },
        moneyKind = selectedKind,
        revealOptionsError = dateError != null || timeError != null,
    ) {
        AmountField(
            label = "Amount",
            value = amount,
            enabled = !busy,
            error = amountError(amount, entryCurrency ?: old?.currencyCode ?: DEFAULT_CURRENCY),
            currency = entryCurrency ?: old?.currencyCode,
            positive = selectedKind == EntryKind.INCOME,
            autoFocus = old == null,
        ) { amount = it }
        if (accounts.isEmpty() && selectedKind != EntryKind.EXPENSE) Text(
            "Create an account in Money before recording a transaction.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (selectedKind == EntryKind.TRANSFER) EntryAccountChoice(
            label = "FROM ACCOUNT",
            value = source,
            selected = accounts.firstOrNull { it.id == source },
            options = accounts,
            enabled = !busy,
        ) { source = it }
        if (selectedKind == EntryKind.TRANSFER) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.SouthWest, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    primary?.let { "${it.currencyCode} transfer · same currency only" }
                        ?: "Choose the source account first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selectedKind != EntryKind.EXPENSE) {
            val destination = accounts.firstOrNull { it.id == target }
            val destinationError = when {
                selectedKind != EntryKind.TRANSFER || target.isBlank() -> null
                target == source -> "Choose two different accounts"
                primary != null && destination != null &&
                    destination.currencyCode != primary.currencyCode ->
                    "Choose a destination with the same currency"
                else -> null
            }
            EntryAccountChoice(
                label = if (selectedKind == EntryKind.INCOME) "RECEIVING ACCOUNT" else "TO ACCOUNT",
                value = target,
                selected = destination,
                options = accounts.filter {
                    selectedKind != EntryKind.TRANSFER ||
                        it.id != source && it.currencyCode == primary?.currencyCode
                },
                enabled = !busy,
                error = destinationError,
            ) { target = it }
        }
        if (selectedKind != EntryKind.TRANSFER) EntryCategoryChoice(
            value = category,
            categories = categories,
            enabled = !busy,
        ) { category = it }
        val summary = listOfNotNull(
            runCatching {
                val selectedDate = LocalDate.parse(date)
                if (selectedDate == LocalDate.now()) "Today"
                else selectedDate.format(DateTimeFormatter.ofPattern("d MMM uuuu"))
            }.getOrDefault("Date needs attention"),
            if (timeError == null) time else "Time needs attention",
            if (note.isNotBlank()) "Description added" else null,
            if (selectedKind == EntryKind.EXPENSE) primary?.name else null,
        ).joinToString(" · ")
        val validate = LocalValidateFields.current
        val revealOptions = validate && (dateError != null || timeError != null)
        MoreOptions(summary, validationError = revealOptions, enabled = !busy) {
            if (selectedKind == EntryKind.EXPENSE && (accounts.isNotEmpty() || source.isNotBlank())) {
                FinancialChoice(
                    "Record against",
                    source,
                    listOf("" to BUDGET_ONLY_LABEL) + accounts.map { it.id to "${it.name} · ${it.currencyCode}" },
                    !busy,
                ) { source = it }
            }
            if (budgetOnly) {
                Field("Currency (ISO code)", budgetCurrency, !busy, error = currencyError(budgetCurrency)) {
                    budgetCurrency = it.trim().uppercase(Locale.ROOT)
                }
            }
            Field("Description / notes (optional)", note, !busy, single = false) { note = it }
            Column(
                Modifier.fillMaxWidth().bringIntoViewRequester(dateRequest),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BillingDateField("Date", date, !busy, compact = true) { date = it }
                if (validate && dateError != null) Text(
                    dateError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { fieldError(dateError) },
                )
            }
            Column(
                Modifier.fillMaxWidth().bringIntoViewRequester(timeRequest),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CalendarTimeField("Time", time, !busy, compact = true) { time = it }
                if (validate && timeError != null) Text(
                    timeError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { fieldError(timeError) },
                )
            }
            Choice(
                "Transaction kind",
                kind,
                EntryKind.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) },
                !busy,
            ) {
                kind = it
                if (data.categories.firstOrNull { c -> c.id == category }?.kind?.let {
                        categoryKind -> categoryKind != it
                    } == true || it == "TRANSFER") category = ""
            }
            LaunchedEffect(revealOptions, dateError, timeError) {
                if (revealOptions) {
                    // Let the expanded controls reach layout before requesting their visibility.
                    withFrameNanos { }
                    if (dateError != null) dateRequest.bringIntoView()
                    else timeRequest.bringIntoView()
                }
            }
        }
        if (old != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
            Text("Delete transaction", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete) ConfirmDeletion(
        "Delete transaction?",
        "This removes its balance effects from every account and its spending from budgets. A transfer is removed from both accounts together.",
        { confirmDelete = false },
    ) {
        confirmDelete = false
        delete(id, close)
    }
}

@Composable
private fun EntryAccountChoice(
    label: String,
    value: String,
    selected: AccountEntity?,
    options: List<AccountEntity>,
    enabled: Boolean,
    error: String? = null,
    change: (String) -> Unit,
) {
    var choosing by rememberSaveable(label) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val message = when {
        value.isNotBlank() && selected == null -> "This account is unavailable. Choose an account."
        error != null -> error
        LocalValidateFields.current && value.isBlank() -> "Choose an account"
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PickerTrigger(
            label = label,
            value = selected?.let {
                "${it.name} · ${it.currencyCode}${if (it.archived) " · Archived" else ""}"
            } ?: if (value.isBlank()) "Choose an account" else "Account unavailable",
            icon = AppIcons.AccountBalanceWallet,
            enabled = enabled && options.isNotEmpty(),
            containerColor = LocalAtmosphere.current.panel,
        ) {
            focus.clearFocus()
            choosing = true
        }
        if (message != null) Text(
            message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { fieldError(message) },
        )
    }
    if (choosing) EntrySelectionSheet(
        title = label.lowercase().replaceFirstChar(Char::uppercase),
        selected = value,
        options = options.map {
            it.id to "${it.name} · ${it.currencyCode}${if (it.archived) " · Archived" else ""}"
        },
        enabled = enabled,
        dismiss = { choosing = false },
        choose = change,
    )
}

@Composable
private fun EntryCategoryChoice(
    value: String,
    categories: List<CategoryEntity>,
    enabled: Boolean,
    change: (String) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val dirty = LocalMarkDraftDirty.current
    val options = listOf("" to "Uncategorized") + categories.map {
        it.id to (it.name + if (it.archived) " · Archived" else "")
    }
    val visible = (options.take(4) + options.filter { it.first == value }).distinctBy { it.first }
    Surface(color = LocalAtmosphere.current.panel, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CATEGORY", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = enabled, onClick = {
                    focus.clearFocus()
                    choosing = true
                }) { Text("All categories", style = MaterialTheme.typography.labelMedium) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visible.forEach { (id, label) ->
                    FilterChip(
                        selected = id == value,
                        enabled = enabled,
                        onClick = {
                            if (id != value) {
                                dirty()
                                change(id)
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (id == value) {
                            { Icon(AppIcons.CheckCircle, null, Modifier.size(16.dp)) }
                        } else null,
                        shape = CircleShape,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            if (options.none { it.first == value }) Text(
                "Selected category is unavailable. Choose a category or Uncategorized.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (choosing) EntrySelectionSheet(
        title = "Category",
        selected = value,
        options = options,
        enabled = enabled,
        dismiss = { choosing = false },
        choose = change,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySelectionSheet(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    dismiss: () -> Unit,
    choose: (String) -> Unit,
    header: @Composable () -> Unit = {},
) {
    val dirty = LocalMarkDraftDirty.current
    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(title, Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            header()
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).selectableGroup(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(options, key = { it.first }) { (id, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = id == selected, onClick = null, enabled = enabled)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(
                                selected = id == selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = {
                                    if (id != selected) {
                                        dirty()
                                        choose(id)
                                    }
                                    dismiss()
                                },
                            ),
                        colors = ListItemDefaults.colors(
                            containerColor = if (id == selected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FinancialChoice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    choose: (String) -> Unit,
) {
    var choosing by rememberSaveable(label) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val selected = options.firstOrNull { it.first == value }?.second
    PickerTrigger(
        label = label,
        value = selected ?: if (value.isBlank()) "Choose" else "Unavailable · $value",
        icon = AppIcons.RadioButtonUnchecked,
        enabled = enabled && options.isNotEmpty(),
        containerColor = LocalAtmosphere.current.panel,
    ) {
        focus.clearFocus()
        choosing = true
    }
    if (choosing) EntrySelectionSheet(
        title = label,
        selected = value,
        options = options,
        enabled = enabled,
        dismiss = { choosing = false },
        choose = choose,
    )
}

@Composable
internal fun AmountField(
    label: String,
    value: String,
    enabled: Boolean,
    error: String? = null,
    currency: String? = null,
    positive: Boolean = false,
    autoFocus: Boolean = false,
    change: (String) -> Unit,
) {
    val dirty = LocalMarkDraftDirty.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val invalid = LocalValidateFields.current && error != null
    val accent = if (positive) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.primary
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val digits = currency?.let { runCatching { currencyDigits(it) }.getOrNull() }
    LaunchedEffect(autoFocus) {
        if (autoFocus && enabled) requester.requestFocus()
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label.uppercase() + currency?.let { " · $it" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Keep input unformatted so parseMoney owns exact precision and validation.
        BasicTextField(
            value = value,
            onValueChange = {
                if (it != value) dirty()
                change(it)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.displayMedium.copy(
                textAlign = TextAlign.Center,
                color = textColor,
            ),
            cursorBrush = SolidColor(if (invalid) MaterialTheme.colorScheme.error else accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label + currency?.let { " in $it" }.orEmpty()
                    if (invalid) fieldError(requireNotNull(error))
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (digits == 0) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            decorationBox = { input ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text(
                        "0",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    input()
                }
            },
        )
        Box(
            Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(
                when {
                    invalid -> MaterialTheme.colorScheme.error
                    focused -> accent
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
            ),
        )
        if (invalid) Text(
            requireNotNull(error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun CategoryEditor(old: CategoryEntity?, id: String, busy: Boolean, error: String?, close: () -> Unit, save: (CategoryEntity, Boolean, () -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(old?.kind ?: if (old == null) "EXPENSE" else "") }
    var archived by rememberSaveable { mutableStateOf(old?.archived ?: false) }
    EditorFrame(if (old == null) "Add category" else "Edit category", busy, error, close, {
        require(name.isNotBlank()) { "Category name is required" }
        save(CategoryEntity(id, name.trim(), kind.ifBlank { null }, archived), old == null, close)
    }, compactLayout = true, saveLabel = "Save category") {
        Field("Category name", name, !busy, prominent = true, error = if (name.isBlank()) "Category name is required" else null) { name = it }
        FinancialChoice(
            "Category type", kind,
            listOf("EXPENSE" to "Expense", "INCOME" to "Income") +
                if (old != null && old.kind == null) listOf("" to "Expense / income (legacy)")
                else emptyList(),
            old == null && !busy,
        ) { kind = it }
        Text("Renaming updates historical labels. The type stays fixed; create another category to change it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (old != null) MoreOptions(if (archived) "Archived" else "Archive category", enabled = !busy) {
            ArchiveSwitch(archived, !busy) { archived = it }
            Text("Archiving keeps linked transactions and budgets.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun BudgetEditor(
    old: BudgetEntity?,
    id: String,
    selectedMonth: YearMonth? = null,
    categories: List<CategoryEntity>,
    busy: Boolean,
    error: String?,
    close: () -> Unit,
    save: (BudgetEntity, Boolean) -> Unit,
    delete: (String, () -> Unit) -> Unit,
    preferredCurrency: String = DEFAULT_CURRENCY,
) {
    val previous = old?.period
    var name by rememberSaveable { mutableStateOf(old?.name ?: "Monthly budget") }
    var monthKey by rememberSaveable {
        mutableStateOf(initialBudgetMonth(previous, selectedMonth).toString())
    }
    val month = YearMonth.parse(monthKey)
    var code by rememberSaveable { mutableStateOf(old?.currencyCode ?: preferredCurrency) }
    var limit by rememberSaveable { mutableStateOf(old?.let { moneyInput(it.limitMinor, it.currencyCode) } ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.categoryId ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    EditorFrame(if (old == null) "Create budget" else "Edit budget", busy, error, close, {
        require(name.isNotBlank()) { "Budget name is required" }
        val period = editedBudgetPeriod(month, previous)
        val currency = code.trim().uppercase(Locale.ROOT)
        save(
            BudgetEntity(
                id, name.trim(), currency, parseMoney(limit, currency),
                period.startEpochDay, period.endExclusiveEpochDay, category.ifBlank { null },
            ),
            old == null,
        )
    }, compactLayout = true, saveLabel = "Save budget") {
        Field("Budget name", name, !busy, prominent = true, error = if (name.isBlank()) "Budget name is required" else null) { name = it }
        BudgetMonthField(month, !busy) { monthKey = it.toString() }
        if (previous != null && previous.startMonth != month) {
            Text(
                "Saving moves this budget from ${monthLabel(previous.startMonth)} to ${monthLabel(month)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (previous != null && previous != BudgetPeriod.forMonth(month)) {
            Text("Saved period: ${budgetPeriodLabel(previous)}", style = MaterialTheme.typography.bodySmall)
        }
        Field("Currency (ISO code)", code, !busy, error = currencyError(code)) { code = it.uppercase(Locale.ROOT) }
        AmountField("Budget limit", limit, !busy, error = amountError(limit, code), currency = code) { limit = it }
        FinancialChoice(
            "Scope", category,
            listOf("" to "Overall") + categories.filter {
                it.kind != "INCOME" && (!it.archived || it.id == old?.categoryId)
            }.map { it.id to it.name },
            !busy,
        ) { category = it }
        Text("One overall limit per currency and month. Category limits overlap the overall budget; they are never added together. Only recorded expenses count.")
        Text("Choose the current month's overall budget on Today. Automatic uses your preferred currency from Settings.", style = MaterialTheme.typography.bodySmall)
        if (old != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete budget", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) ConfirmDeletion("Delete budget?", "Transactions and account balances are kept.", { confirmDelete = false }) { confirmDelete = false; delete(id, close) }
}

@Composable
private fun BudgetMonthField(month: YearMonth, enabled: Boolean, choose: (YearMonth) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    PickerTrigger(
        label = "Budget month",
        value = monthLabel(month),
        icon = AppIcons.CalendarMonth,
        enabled = enabled,
        containerColor = LocalAtmosphere.current.panel,
    ) {
        focus.clearFocus()
        choosing = true
    }
    if (choosing) {
        var year by rememberSaveable { mutableIntStateOf(month.year) }
        val options = Month.values().map { option ->
            YearMonth.of(year, option).toString() to
                option.getDisplayName(DateTextStyle.FULL_STANDALONE, Locale.getDefault())
        }
        EntrySelectionSheet(
            title = "Choose budget month",
            selected = month.toString(),
            options = options,
            enabled = enabled,
            dismiss = { choosing = false },
            choose = { choose(YearMonth.parse(it)) },
            header = {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { year-- }, enabled = enabled && year > Year.MIN_VALUE) {
                        Icon(AppIcons.ChevronLeft, "Previous year")
                    }
                    Text(year.toString(), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { year++ }, enabled = enabled && year < Year.MAX_VALUE - 1) {
                        Icon(AppIcons.ChevronRight, "Next year")
                    }
                }
            },
        )
    }
}

@Composable private fun ConfirmDeletion(title: String, text: String, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
