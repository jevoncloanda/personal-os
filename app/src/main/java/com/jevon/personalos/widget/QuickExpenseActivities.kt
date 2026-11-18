package com.jevon.personalos.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.data.*
import com.jevon.personalos.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class QuickExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PersonalOsApplication
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val recordedEntry = MutableStateFlow<String?>(null)
    private val revision = MutableStateFlow(0)
    val settings = app.settings.settings.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5_000), Settings())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val catalog = revision.flatMapLatest {
        combine(app.quickExpenses.state,
            app.database.invalidationTracker.createFlow("accounts", "categories")) { state, _ ->
            QuickExpenseCatalog(state, app.database.personalDao().balances().map { it.account },
                app.database.personalDao().categories())
        }.map<QuickExpenseCatalog, Result<QuickExpenseCatalog>> { Result.success(it) }
            .catch { emit(Result.failure(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry() { revision.value++ }

    fun perform(done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                block()
                try {
                    QuickExpenseWidget().updateAll(app)
                    scheduleFeedbackRefresh(app)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Stored data remains authoritative if the launcher is unavailable.
                }
                done()
            } catch (error: CancellationException) {
                throw error
            } catch (failure: Exception) {
                error.value = if (failure is IllegalArgumentException) failure.message
                    else "Could not finish. Retry or review Money before recording again."
            } finally {
                busy.value = false
            }
        }
    }

    fun savePreset(preset: ExpensePreset, done: () -> Unit) = perform(done) {
        val dao = app.database.personalDao()
        preset.validate(dao.balances().find { it.account.id == preset.accountId }?.account,
            dao.categories().find { it.id == preset.categoryId })
        app.quickExpenses.update { state ->
            val presets = if (state.presets.any { it.id == preset.id })
                state.presets.map { if (it.id == preset.id) preset else it }
            else state.presets + preset
            state.copy(presets = presets)
        }
    }

    fun deletePreset(id: String) = perform {
        app.quickExpenses.update { state -> state.copy(presets = state.presets.filter { it.id != id }) }
    }

    fun movePreset(id: String, offset: Int) = perform {
        app.quickExpenses.update { state ->
            val presets = state.presets.toMutableList()
            val index = presets.indexOfFirst { it.id == id }
            val destination = index + offset
            if (index >= 0 && destination in presets.indices) {
                presets.add(destination, presets.removeAt(index))
            }
            state.copy(presets = presets)
        }
    }

    fun configure(id: Int, selected: List<String>, done: () -> Unit) = perform(done) {
        require(selected.size <= 3 && selected.distinct().size == selected.size)
        app.quickExpenses.update { state ->
            require(selected.all { selectedId -> state.presets.any { it.id == selectedId } }) {
                "A selected preset was removed. Select your presets again"
            }
            state.copy(widgets = state.widgets + (id.toString() to selected))
        }
    }

    fun record(widgetId: Int, presetId: String?, requestId: String, draft: ExpensePreset) = perform {
        val entry = if (presetId != null)
            app.quickExpenseRecorder.recordPreset(widgetId, presetId, confirmed = true,
                expectedRevision = draft.revision)
        else app.quickExpenseRecorder.recordCustom(widgetId, requestId, draft)
        recordedEntry.value = entry.id
    }

    fun undo(widgetId: Int, entryId: String) = perform {
        app.quickExpenseRecorder.undo(widgetId, entryId)
    }
}

internal fun contextWidgetId(activity: ComponentActivity): Int =
    activity.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

internal fun validWidget(context: android.content.Context, id: Int): Boolean =
    AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ==
        ComponentName(context, QuickExpenseReceiver::class.java)

class QuickExpenseConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = contextWidgetId(this)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && !validWidget(this, id)) {
            finish()
            return
        }
        setContent {
            val model: QuickExpenseViewModel = viewModel()
            val settings by model.settings.collectAsStateWithLifecycle()
            PersonalOsTheme(settings) {
                QuickExpenseSurface(model) { catalog, busy, error ->
                    PresetConfiguration(catalog, id, model, busy, error, close = { finish() }) {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }
                }
            }
        }
    }
}

class QuickExpenseEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = contextWidgetId(this)
        if (!validWidget(this, id)) {
            finish()
            return
        }
        setContent {
            val model: QuickExpenseViewModel = viewModel()
            val settings by model.settings.collectAsStateWithLifecycle()
            PersonalOsTheme(settings) {
                QuickExpenseSurface(model) { catalog, busy, error ->
                    CustomExpense(catalog, id, intent.getStringExtra("preset"), model, busy, error) {
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickExpenseSurface(
    model: QuickExpenseViewModel,
    content: @Composable (QuickExpenseCatalog, Boolean, String?) -> Unit,
) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SystemBarAppearance()
        when {
            catalog == null -> Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                CircularProgressIndicator()
                Text("Loading Quick Expense…")
            }
            catalog?.isFailure == true -> Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                Text("Quick Expense unavailable. Retry loading your accounts and presets.")
                Button(onClick = model::retry) { Text("Retry") }
            }
            else -> content(catalog!!.getOrThrow(), busy, error)
        }
    }
}

@Composable
private fun PresetConfiguration(
    catalog: QuickExpenseCatalog,
    widgetId: Int,
    model: QuickExpenseViewModel,
    busy: Boolean,
    error: String?,
    close: () -> Unit,
    done: () -> Unit,
) {
    var selection by rememberSaveable { mutableStateOf(
        catalog.state.widgets[widgetId.toString()].orEmpty().joinToString(",")) }
    val selected = selection.split(",").filter { it.isNotBlank() }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quick Expense", style = MaterialTheme.typography.headlineMedium)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Text("Choose up to three presets. Custom Amount occupies the fourth action slot.")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (catalog.state.presets.isEmpty()) Text("Create a preset for an expense you record often.")
        if (catalog.accounts.none { !it.archived }) Text("Add an account in Money to create a preset.")
        if (catalog.categories.none { !it.archived && it.kind != "INCOME" }) {
            Text("Add an expense category in Money to create a preset.")
        }
        catalog.state.presets.forEachIndexed { index, preset ->
            PorosCard(contentPadding = 12.dp) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Text("${moneyInput(preset.amountMinor, preset.currencyCode)} ${preset.currencyCode}")
                if (runCatching { preset.validate(
                    catalog.accounts.find { it.id == preset.accountId },
                    catalog.categories.find { it.id == preset.categoryId },
                ) }.isFailure) Text("Unavailable. Edit the account or category.",
                    color = MaterialTheme.colorScheme.error)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    FormToggle("Show ${preset.name}", preset.id in selected,
                        !busy && (preset.id in selected || selected.size < 3)) { checked ->
                        selection = (if (checked) selected + preset.id else selected - preset.id)
                            .joinToString(",")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { editing = preset.id }) { Text("Edit") }
                    TextButton(enabled = !busy, onClick = { deleting = preset.id }) { Text("Delete") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy && index > 0,
                        onClick = { model.movePreset(preset.id, -1) }) { Text("Move up") }
                    TextButton(enabled = !busy && index < catalog.state.presets.lastIndex,
                        onClick = { model.movePreset(preset.id, 1) }) { Text("Move down") }
                }
            }
        }
        val missing = selected.filter { id -> catalog.state.presets.none { it.id == id } }
        if (missing.isNotEmpty()) TextButton(onClick = {
            selection = (selected - missing.toSet()).joinToString(",")
        }) { Text("Remove unavailable selections") }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && selected.isNotEmpty()) {
            Text("Widget order", style = MaterialTheme.typography.titleMedium)
            selected.forEachIndexed { index, id ->
                Text(catalog.state.presets.find { it.id == id }?.name ?: "Removed preset")
                TextButton(enabled = !busy && index > 0, onClick = {
                    val order = selected.toMutableList()
                    order.add(index - 1, order.removeAt(index))
                    selection = order.joinToString(",")
                }) { Text("Move earlier") }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { editing = UUID.randomUUID().toString() },
            modifier = Modifier.fillMaxWidth()) { Text("Create preset") }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Button(enabled = !busy && missing.isEmpty(), modifier = Modifier.fillMaxWidth(),
                onClick = { model.configure(widgetId, selected, done) }) {
                Text(if (busy) "Saving…" else "Save widget")
            }
        }
        TextButton(enabled = !busy, onClick = close) { Text("Cancel") }
    }
    editing?.let { id ->
        key(id) {
            PresetEditor(catalog, catalog.state.presets.find { it.id == id }, id, busy, error,
                close = { editing = null }) { preset -> model.savePreset(preset) { editing = null } }
        }
    }
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete preset?") },
            text = { Text("Widgets using this preset will show it as unavailable.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                model.deletePreset(id)
                deleting = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun PresetEditor(
    catalog: QuickExpenseCatalog,
    old: ExpensePreset?,
    id: String,
    busy: Boolean,
    error: String?,
    close: () -> Unit,
    save: (ExpensePreset) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(old?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(old?.let {
        moneyInput(it.amountMinor, it.currencyCode)
    }.orEmpty()) }
    var account by rememberSaveable { mutableStateOf(old?.accountId.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(old?.categoryId.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(old?.note.orEmpty()) }
    var confirm by rememberSaveable { mutableStateOf(old?.confirm ?: false) }
    val currency = catalog.accounts.find { it.id == account }?.currencyCode
    EditorFrame(if (old == null) "Create preset" else "Edit preset", busy, error, close, save = {
        val code = requireNotNull(currency) { "Choose an active account" }
        val preset = ExpensePreset(id, name.trim(), parseMoney(amount, code), code,
            account, category, note.trim(), confirm)
        preset.validate(catalog.accounts.find { it.id == account },
            catalog.categories.find { it.id == category })
        save(preset)
    }, compactLayout = true, saveLabel = "Save preset") {
        Field("Preset name", name, !busy) { name = it }
        AmountField("Amount", amount, !busy, currency = currency) { amount = it }
        ExpenseChoices(catalog, account, category, !busy, { account = it }, { category = it })
        Field("Note (optional)", note, !busy) { note = it }
        FormToggle("Confirm before recording", confirm, !busy) { confirm = it }
    }
}

@Composable
private fun ExpenseChoices(
    catalog: QuickExpenseCatalog,
    account: String,
    category: String,
    enabled: Boolean,
    selectAccount: (String) -> Unit,
    selectCategory: (String) -> Unit,
) {
    FinancialChoice("Account", account, catalog.accounts.filter { !it.archived }
        .map { it.id to "${it.name} · ${it.currencyCode}" }, enabled, selectAccount)
    FinancialChoice("Category", category, catalog.categories.filter {
        !it.archived && it.kind != "INCOME"
    }.map { it.id to it.name }, enabled, selectCategory)
}

@Composable
private fun CustomExpense(
    catalog: QuickExpenseCatalog,
    widgetId: Int,
    presetId: String?,
    model: QuickExpenseViewModel,
    busy: Boolean,
    error: String?,
    close: () -> Unit,
) {
    val preset = catalog.state.presets.find { it.id == presetId }
    val requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf(catalog.state.lastAccount.takeIf { id ->
        catalog.accounts.any { it.id == id && !it.archived }
    }.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(catalog.state.lastCategory.takeIf { id ->
        catalog.categories.any { it.id == id && !it.archived && it.kind != "INCOME" }
    }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    val receipt = if (presetId == null) catalog.state.submissions["custom-$requestId"]
        ?.takeIf { it.completed } else null
    val confirmedEntry by model.recordedEntry.collectAsStateWithLifecycle()
    val feedback = catalog.state.feedback[widgetId.toString()]
    val recordedId = receipt?.entry?.id ?: confirmedEntry
    if (recordedId != null) {
        val submission = catalog.state.submissions.values.find { it.entry.id == recordedId }
        Column(Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (feedback?.entry?.id == recordedId) feedback.message else
                if (submission?.undone == true) "Expense undone" else "Expense saved",
                style = MaterialTheme.typography.headlineSmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (feedback?.entry?.id == recordedId) Button(enabled = !busy,
                onClick = { model.undo(widgetId, recordedId) }) { Text("Undo this expense") }
            Button(enabled = !busy, onClick = close) { Text("Done") }
        }
        return
    }
    if (presetId != null) {
        Column(Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Confirm expense", style = MaterialTheme.typography.headlineSmall)
            if (preset == null) Text("Preset removed. Open widget Configure.")
            else {
                Text(preset.name)
                Text("${moneyInput(preset.amountMinor, preset.currencyCode)} ${preset.currencyCode}")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !busy, onClick = {
                    model.record(widgetId, presetId, requestId, preset)
                }) { Text(if (busy) "Recording…" else "Record expense") }
            }
            TextButton(enabled = !busy, onClick = close) { Text("Cancel") }
        }
        return
    }
    val currency = catalog.accounts.find { it.id == account }?.currencyCode
    EditorFrame("Quick Expense", busy, error, close, save = {
        val code = requireNotNull(currency) { "Choose an active account" }
        val draft = ExpensePreset(requestId, "Custom expense", parseMoney(amount, code), code,
            account, category, note.trim())
        draft.validate(catalog.accounts.find { it.id == account },
            catalog.categories.find { it.id == category })
        model.record(widgetId, null, requestId, draft)
    }, moneyKind = EntryKind.EXPENSE, saveLabel = "Save expense") {
        AmountField("Amount", amount, !busy, currency = currency, autoFocus = true) { amount = it }
        ExpenseChoices(catalog, account, category, !busy, { account = it }, { category = it })
        if (catalog.accounts.none { !it.archived }) Text("Add an account in Money first.")
        if (catalog.categories.none { !it.archived && it.kind != "INCOME" }) {
            Text("Add an expense category in Money first.")
        }
        Field("Note (optional)", note, !busy) { note = it }
    }
}
