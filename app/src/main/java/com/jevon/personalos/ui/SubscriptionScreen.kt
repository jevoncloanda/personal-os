package com.jevon.personalos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jevon.personalos.data.*
import com.jevon.personalos.subscriptions.*
import com.jevon.personalos.today.formatMoney
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@Composable
fun SubscriptionScreen(model: SubscriptionViewModel, requested: String?, handled: () -> Unit, configureAlerts:(String)->Unit={}, preferredCurrency: String = DEFAULT_CURRENCY) {
    val data by model.state.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var correction by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    var cancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requested) { if (requested != null) {
        model.clearError()
        if (requested == "new") { selected = null; editor = "subscription" } else selected = requested
        handled()
    } }
    BackHandler(selected != null && editor == null) { selected = null; model.clearError() }
    val snapshot = data
    if (snapshot == null) {
        Column(Modifier.padding(20.dp)) {
            if (error == null) CircularProgressIndicator() else { Text(error!!); Button(onClick = model::retry) { Text("Try again") } }
        }
        return
    }
    val value = snapshot.subscriptions.firstOrNull { it.id == selected }
    val payments = snapshot.payments.filter { it.subscriptionId == selected }
    val paid = payments.map { it.dueEpochDay }.toSet()
    val nextDue = value?.let { nextUnpaid(it, paid) }
    val paidBySubscription = snapshot.payments.groupBy { it.subscriptionId }.mapValues { (_, rows) -> rows.map { it.dueEpochDay }.toSet() }
    LazyColumn(Modifier.fillMaxSize().testTag("SubscriptionList"), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selected == null) {
            item {
                SectionHeading("Your recurring plans", "New subscription", { model.clearError(); editor = "subscription" })
                Text("Forecasts help you plan. Only recorded payments affect Money.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { PorosCard {
                Text("Recurring cost equivalents", style = MaterialTheme.typography.titleMedium)
                val costs = recurringCosts(snapshot.subscriptions)
                if (costs.isEmpty()) Text("No active recurring plans yet.")
                costs.forEach { cost ->
                    Text("${cost.currency} · ${formatMoney(cost.monthlyMinor, cost.currency)} / month", style = MaterialTheme.typography.headlineSmall)
                    Text("${formatMoney(cost.annualMinor, cost.currency)} / year", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Estimates, not recorded spending. Rounded once per currency; daily/weekly plans use an average calendar year.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            val upcoming = snapshot.subscriptions.filter { it.active }.mapNotNull { plan ->
                nextUnpaid(plan, paidBySubscription[plan.id].orEmpty())?.takeIf { it <= today.plusDays(7).toEpochDay() }?.let { plan to it }
            }.sortedWith(compareBy<Pair<SubscriptionEntity, Long>> { it.second }.thenBy { it.first.id })
            if (upcoming.isNotEmpty()) {
                item { SectionTitle("Due soon & overdue") }
                items(upcoming, key = { "upcoming/${it.first.id}" }) { (plan, due) ->
                    LedgerRow(AppIcons.Autorenew, plan.name,
                        "${if (due < today.toEpochDay()) "Overdue" else if (due == today.toEpochDay()) "Due today" else "Due ${LocalDate.ofEpochDay(due)}"} · Unpaid forecast",
                        formatMoney(plan.amountMinor, plan.currencyCode), onClick = { selected = plan.id; model.clearError() })
                }
            }
            item {
                SectionTitle("All plans")
                Field("Search subscriptions", search, true) { search = it }
                Choice("Show", filter, listOf("Active", "Inactive", "All").map { it to it }) { filter = it }
            }
            val rows = snapshot.subscriptions.filter { (filter == "All" || it.active == (filter == "Active")) && (it.name.contains(search, true) || it.description.contains(search, true)) }
            if (rows.isEmpty()) item { Text(if (snapshot.subscriptions.isEmpty()) "Add a subscription to track its renewal and record payments." else "No subscriptions match. Try another search or filter.") }
            items(rows, key = { it.id }) { s ->
                val next = nextUnpaid(s, paidBySubscription[s.id].orEmpty())
                val status = if (!s.active) "Inactive · history retained" else next?.let { "Active · Unpaid ${LocalDate.ofEpochDay(it)}" } ?: "Active · No unpaid renewals"
                LedgerRow(AppIcons.Autorenew, s.name, "$status\n${billingLabel(s)}", formatMoney(s.amountMinor, s.currencyCode), onClick = { selected = s.id; model.clearError() })
            }
        } else if (value == null) {
            item { Text("This subscription is no longer available."); TextButton(onClick = { selected = null }) { Text("All subscriptions") } }
        } else {
            item { TextButton(onClick = { selected = null; model.clearError() }) { Text("All subscriptions") } }
            item { Text(value.name, style = MaterialTheme.typography.headlineMedium); if (value.description.isNotBlank()) Text(value.description) }
            item { PorosCard {
                Text(formatMoney(value.amountMinor, value.currencyCode), style = MaterialTheme.typography.headlineLarge)
                Text("${billingLabel(value)} · ${if (value.active) "Active" else "Inactive"}")
                Text("Next unpaid: ${nextDue?.let(LocalDate::ofEpochDay) ?: "None"}${if (!value.active) " (forecast stopped)" else ""}")
                Text("Account: ${snapshot.accounts.firstOrNull { it.account.id == value.accountId }?.account?.let { it.name + if (it.archived) " (archived)" else "" } ?: "Choose when paying"}")
                Text("Category: ${snapshot.categories.firstOrNull { it.id == value.categoryId }?.name ?: "Uncategorized"}")
                if (value.notes.isNotBlank()) Text(value.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && value.active && nextDue != null, onClick = { model.clearError(); correction = null; editor = "payment" }) { Text("Record payment") }
                    OutlinedButton(enabled = !busy, onClick = { model.clearError(); editor = "subscription" }) { Text("Edit subscription") }
                }
                TextButton(enabled = !busy, onClick = { if (value.active) cancel = true else model.active(value.id, true) }) { Text(if (value.active) "Cancel subscription" else "Reactivate subscription") }
                OutlinedButton(onClick={configureAlerts(value.id)}){Text("Notification timing")}
            } }
            item { Text("Upcoming renewals", style = MaterialTheme.typography.titleLarge)
                Text("Unpaid forecasts only. Missed cycles remain unpaid; edit the next date to explicitly reschedule them.", style = MaterialTheme.typography.bodySmall) }
            val dates = upcomingRenewals(value, paid, today.toEpochDay(), today.plusYears(1).toEpochDay(), 12)
            if (value.active && nextDue != null && nextDue < today.toEpochDay()) item { Text("Overdue · ${LocalDate.ofEpochDay(nextDue)} remains unpaid", color = MaterialTheme.colorScheme.error) }
            if (dates.isEmpty()) item { Text(if (value.active) "No future unpaid renewals in the next year." else "Inactive subscriptions produce no forecasts.") }
            items(dates) { date -> Text("${LocalDate.ofEpochDay(date)} · ${formatMoney(value.amountMinor, value.currencyCode)} forecast") }
            item { Text("Payment history", style = MaterialTheme.typography.titleLarge); Text("Paid expenses are already included in Money. Tap to correct the same expense.", style = MaterialTheme.typography.bodySmall) }
            if (payments.isEmpty()) item { Text("No recorded payments.") }
            items(payments, key = { it.id }) { payment ->
                val expense = snapshot.expenses.firstOrNull { it.id == payment.entryId }
                if (expense != null) LedgerRow(AppIcons.CheckCircle, "Paid · ${LocalDate.ofEpochDay(payment.dueEpochDay)}", "Payment date ${LocalDate.ofEpochDay(expense.occurredOnEpochDay)}\n${snapshot.accounts.firstOrNull { it.account.id == expense.fromAccountId }?.account?.name ?: "Account"}", formatMoney(expense.amountMinor, expense.currencyCode), onClick = { model.clearError(); correction = payment.id; editor = "payment" })
            }
        }
        if (error != null && editor == null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
    }
    if (cancel && value != null) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("Cancel ${value.name}?") }, text = { Text("Upcoming forecasts stop. Paid expenses and payment history remain. This does not cancel the service with its provider.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { cancel = false; model.active(value.id, false) }) { Text("Mark inactive") } }, dismissButton = { TextButton(onClick = { cancel = false }) { Text("Keep active") } })
    if (editor == "subscription") key(selected) { SubscriptionEditor(value?.copy(nextDueEpochDay = nextDue ?: value.nextDueEpochDay), snapshot, busy, error, { editor = null }, preferredCurrency) { s, create -> model.save(s, create) { editor = null; selected = s.id } } }
    if (editor == "payment" && value != null) key(value.id, correction) {
        val link = snapshot.payments.firstOrNull { it.id == correction }
        PaymentEditor(value.copy(nextDueEpochDay = nextDue ?: value.nextDueEpochDay), link, snapshot, today, busy, error, { editor = null }, { if (link != null) model.undo(link.id) { editor = null } }) { due, expense ->
            if (link == null) model.pay(value.id, due, expense) { editor = null }
            else model.correct(link.id, expense) { editor = null }
        }
    }
}

@Composable
private fun SubscriptionEditor(old: SubscriptionEntity?, data: SubscriptionSnapshot, busy: Boolean, error: String?, close: () -> Unit, preferredCurrency: String, save: (SubscriptionEntity, Boolean) -> Unit) {
    val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(old?.description ?: "") }
    var notes by rememberSaveable { mutableStateOf(old?.notes ?: "") }
    var currency by rememberSaveable { mutableStateOf(old?.currencyCode ?: preferredCurrency) }
    var amount by rememberSaveable { mutableStateOf(old?.let { moneyInput(it.amountMinor, it.currencyCode) } ?: "") }
    var frequency by rememberSaveable { mutableStateOf(old?.recurrence?.frequency?.name ?: "MONTHLY") }
    var interval by rememberSaveable { mutableStateOf((old?.recurrence?.interval ?: 1).toString()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.ofEpochDay(old?.nextDueEpochDay ?: LocalDate.now().toEpochDay()).toString()) }
    var account by rememberSaveable { mutableStateOf(old?.accountId ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.categoryId ?: "") }
    val history = data.payments.any { it.subscriptionId == id }
    EditorFrame(if (old == null) "New subscription" else "Edit subscription", busy, error, close, {
        val code = currency.trim().uppercase(Locale.ROOT)
        val due = LocalDate.parse(date).toEpochDay()
        val freq = Frequency.valueOf(frequency)
        val count = interval.toIntOrNull() ?: throw IllegalArgumentException("Use a whole interval from 1 to 1000")
        val unchanged = old != null && due == old.nextDueEpochDay && freq == old.recurrence.frequency && count == old.recurrence.interval
        val recurrence = if (unchanged) old!!.recurrence else Recurrence(freq, count, due)
        save(SubscriptionEntity(id, name.trim(), parseMoney(amount, code), code, due, old?.active ?: true, recurrence, description.trim(), notes.trim(), account.ifBlank { null }, category.ifBlank { null }), old == null)
    }) {
        Field("Subscription name", name, !busy) { name = it }
        Field("Description (optional)", description, !busy, single = false) { description = it }
        Field("Currency code", currency, !busy && !history) { currency = it; account = "" }
        Field("Price", amount, !busy, KeyboardType.Decimal) { amount = it }
        Text("Use a decimal point without grouping; IDR supports two decimal places. Price changes affect forecasts only.", style = MaterialTheme.typography.bodySmall)
        Choice("Billing frequency", frequency, Frequency.entries.map { it.name to when (it) { Frequency.DAILY -> "Daily"; Frequency.WEEKLY -> "Weekly"; Frequency.MONTHLY -> "Monthly"; Frequency.YEARLY -> "Yearly" } }, !busy) { frequency = it }
        Field("Interval", interval, !busy, KeyboardType.Number) { interval = it }
        BillingDateField("Next renewal date", date, !busy) { date = it }
        Text("Changing the date or frequency starts a new schedule at that date. Earlier unpaid cycles are omitted from future forecasts, without creating expenses. Month-end renewals return to the original day when possible.", style = MaterialTheme.typography.bodySmall)
        Choice("Default payment account", account, listOf("" to "Choose when paying") + data.accounts.filter { (!it.account.archived || it.account.id == old?.accountId) && it.account.currencyCode == currency.trim().uppercase(Locale.ROOT) }.map { it.account.id to it.account.name }, !busy) { account = it }
        Choice("Default expense category", category, listOf("" to "Uncategorized") + data.categories.filter { it.kind != "INCOME" && (!it.archived || it.id == old?.categoryId) }.map { it.id to it.name }, !busy) { category = it }
        Field("Notes (optional)", notes, !busy, single = false) { notes = it }
        if (history) Text("Currency is fixed because payment history exists. Existing payments keep their original amounts and dates.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PaymentEditor(value: SubscriptionEntity, link: SubscriptionPaymentEntity?, data: SubscriptionSnapshot, today: LocalDate, busy: Boolean, error: String?, close: () -> Unit, undo: () -> Unit, save: (Long, EntryEntity) -> Unit) {
    val old = data.expenses.firstOrNull { it.id == link?.entryId }
    val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
    var due by rememberSaveable { mutableStateOf(LocalDate.ofEpochDay(link?.dueEpochDay ?: value.nextDueEpochDay).toString()) }
    var date by rememberSaveable { mutableStateOf(old?.let { LocalDate.ofEpochDay(it.occurredOnEpochDay).toString() } ?: today.toString()) }
    var amount by rememberSaveable { mutableStateOf(moneyInput(old?.amountMinor ?: value.amountMinor, value.currencyCode)) }
    var account by rememberSaveable { mutableStateOf(old?.fromAccountId ?: value.accountId ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.categoryId ?: value.categoryId ?: "") }
    var note by rememberSaveable { mutableStateOf(old?.note ?: value.name) }
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    EditorFrame(if (link == null) "Record payment" else "Correct payment", busy, error, close, {
        save(LocalDate.parse(due).toEpochDay(), EntryEntity(id, EntryKind.EXPENSE, parseMoney(amount, value.currencyCode), value.currencyCode, LocalDate.parse(date).toEpochDay(), fromAccountId = account.ifBlank { null } ?: throw IllegalArgumentException("Choose a payment account"), categoryId = category.ifBlank { null }, note = note.trim(), minuteOfDay = old?.minuteOfDay ?: 0))
    }) {
        Text(value.name, style = MaterialTheme.typography.headlineSmall)
        BillingDateField("Renewal occurrence", due, !busy && link == null) { due = it }
        Text(if (link == null) "Choose the billed renewal date. Early or late payment does not move the billing anchor; missed earlier renewals remain unpaid." else "Correction keeps this paid occurrence and updates its existing Money expense.", style = MaterialTheme.typography.bodySmall)
        BillingDateField("Payment date", date, !busy) { date = it }
        Field("Actual paid amount (${value.currencyCode})", amount, !busy, KeyboardType.Decimal) { amount = it }
        Choice("Payment account", account, data.accounts.filter { it.account.currencyCode == value.currencyCode && (!it.account.archived || it.account.id == old?.fromAccountId) }.map { it.account.id to "${it.account.name}${if (it.account.archived) " (archived)" else ""}" }, !busy) { account = it }
        Choice("Expense category", category, listOf("" to "Uncategorized") + data.categories.filter { it.kind != "INCOME" && (!it.archived || it.id == old?.categoryId) }.map { it.id to it.name }, !busy) { category = it }
        Field("Payment note", note, !busy, single = false) { note = it }
        Text("Saving records one real expense in Money. Forecasts never count as spending. Manage accounts and categories in Money.", style = MaterialTheme.typography.bodySmall)
        if (link != null) TextButton(enabled = !busy, onClick = { confirmUndo = true }) { Text("Undo recorded payment", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmUndo) AlertDialog(onDismissRequest = { if (!busy) confirmUndo = false }, title = { Text("Undo this payment?") }, text = { Text("Remove its Money expense and payment link together. Balances and budgets will change. If the occurrence still belongs to the current schedule, it becomes unpaid again. The subscription stays in its current active/inactive state.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirmUndo = false; undo() }) { Text("Undo payment") } }, dismissButton = { TextButton(enabled = !busy, onClick = { confirmUndo = false }) { Text("Keep payment") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillingDateField(label: String, value: String, enabled: Boolean, compact: Boolean = false, change: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(false) }
    val dirty = LocalMarkDraftDirty.current
    if (compact) PickerTrigger(label, runCatching { LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }.getOrDefault("Choose date"), AppIcons.CalendarMonth, enabled) { picker = true }
    else Field("$label (YYYY-MM-DD)", value, enabled, trailing = {
        IconButton(enabled = enabled, onClick = { picker = true }) { Icon(AppIcons.CalendarMonth, "Choose ${label.lowercase()}") }
    }) { change(it) }
    if (picker) {
        val initial = runCatching { LocalDate.parse(value).takeIf { it.year in 1900..2100 }?.toEpochDay()?.times(86_400_000) }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = {
            state.selectedDateMillis?.let { dirty(); change(LocalDate.ofEpochDay(it / 86_400_000).toString()) }; picker = false
        }) { Text("Use date") } }, dismissButton = { Row {
            if (compact) TextButton(onClick = { picker = false; typed = true }) { Text("Type date") }
            TextButton(onClick = { picker = false }) { Text("Cancel") }
        } }) { DatePicker(state) }
    }
    if (typed) {
        var input by rememberSaveable { mutableStateOf(value) }
        val valid = runCatching { LocalDate.parse(input) }.isSuccess
        AlertDialog(onDismissRequest = { typed = false }, title = { Text(label) }, text = {
            OutlinedTextField(input, { input = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = !valid,
                supportingText = { if (!valid) Text("Enter a valid date.") })
        }, confirmButton = { TextButton(enabled = valid, onClick = { dirty(); change(input); typed = false }) { Text("Use date") } },
            dismissButton = { TextButton(onClick = { typed = false }) { Text("Cancel") } })
    }
}
