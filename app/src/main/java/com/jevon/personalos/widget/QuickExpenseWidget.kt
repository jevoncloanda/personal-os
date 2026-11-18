package com.jevon.personalos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.R
import com.jevon.personalos.today.formatMoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.util.concurrent.TimeUnit

private val operationKey = ActionParameters.Key<String>("operation")
private val targetKey = ActionParameters.Key<String>("target")
private val revisionKey = ActionParameters.Key<String>("revision")
private val refreshKey = longPreferencesKey("refresh")
private val canvas = ColorProvider(R.color.widget_surface)
private val panel = ColorProvider(R.color.widget_panel)
private val foreground = ColorProvider(R.color.widget_text)
private val accent = ColorProvider(R.color.widget_accent)
private val muted = ColorProvider(R.color.widget_muted)
private val actionSurface = ColorProvider(R.color.widget_action_surface)
private val onAction = ColorProvider(R.color.widget_on_action)
private val feedbackSurface = ColorProvider(R.color.widget_feedback_surface)

private data class WidgetTile(
    val title: String,
    val primaryText: String,
    val action: Action,
    val currency: String? = null,
    val emphasized: Boolean = false,
    val description: String = listOfNotNull(title, primaryText, currency).joinToString(", "),
)

class QuickExpenseWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId).toString()
        (context.applicationContext as PersonalOsApplication).quickExpenses.update {
            it.copy(widgets = it.widgets - widgetId, feedback = it.feedback - widgetId)
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PersonalOsApplication
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val refresh = currentState<Preferences>()[refreshKey]
            val source = androidx.compose.runtime.remember(refresh) {
                combine(app.quickExpenses.state,
                    app.database.invalidationTracker.createFlow("accounts", "categories", "entries")) {
                        state, _ ->
                    val dao = app.database.personalDao()
                    QuickExpenseCatalog(state, dao.balances().map { it.account }, dao.categories())
                }.map<QuickExpenseCatalog, Result<QuickExpenseCatalog>> { Result.success(it) }
                    .catch { emit(Result.failure(it)) }
            }
            val result by source.collectAsState(initial = null)
            val catalog = result?.getOrNull()
            if (catalog == null) {
                Content(context, widgetId, emptyList(), ExpenseFeedback(
                    if (result == null) "Loading…" else "Unavailable. Open Edit to retry.",
                    Long.MAX_VALUE,
                ))
            } else {
                val selected = catalog.state.widgets[widgetId.toString()].orEmpty().take(3)
                    .map { presetId ->
                        val preset = catalog.state.presets.find { it.id == presetId }
                        val available = preset != null && runCatching {
                            preset.validate(catalog.accounts.find { it.id == preset.accountId },
                                catalog.categories.find { it.id == preset.categoryId })
                        }.isSuccess
                        preset to available
                    }
                val feedback = catalog.state.feedback[widgetId.toString()]
                    ?.takeIf { it.expiresAt > System.currentTimeMillis() }
                Content(context, widgetId, selected, feedback)
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        widgetId: Int,
        selected: List<Pair<ExpensePreset?, Boolean>>,
        feedback: ExpenseFeedback?,
    ) {
        val size = LocalSize.current
        val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val layout = quickExpenseLayout(size.width.value, size.height.value, textScale)
        val configure = actionStartActivity(Intent(context, QuickExpenseConfigureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val custom = WidgetTile("Custom Amount", "Enter", entryAction(context, widgetId),
            emphasized = true, description = "Custom Amount")
        val tiles = selected.take(layout.presetCapacity).map { (preset, available) ->
            val action = when {
                preset == null || !available -> configure
                preset.confirm -> entryAction(context, widgetId, preset.id)
                else -> actionRunCallback<QuickExpenseAction>(actionParametersOf(
                    operationKey to "record",
                    targetKey to preset.id,
                    revisionKey to preset.revision,
                ))
            }
            WidgetTile(
                title = preset?.name ?: "Removed preset",
                primaryText = if (available && preset != null)
                    formatMoney(preset.amountMinor, preset.currencyCode) else "Unavailable",
                currency = preset?.currencyCode?.takeIf { available },
                action = action,
            )
        }.toMutableList()
        if (layout.actionSlots > 1 && (tiles.isEmpty() ||
            (layout.actionSlots == 4 && tiles.size == 2))) {
            tiles += WidgetTile("Presets", "Set up", configure, description = "Set up presets")
        }
        tiles += custom

        Column(GlanceModifier.fillMaxSize().background(canvas).cornerRadius(16.dp)
            .padding(if (layout.actionSlots == 1) 4.dp else 8.dp)) {
            if (!layout.showHeader && feedback != null) {
                CompactFeedback(feedback, custom, configure,
                    horizontal = layout.actionSlots == 1 || layout.columns == 2)
                return@Column
            }
            if (layout.showHeader) Row(
                GlanceModifier.fillMaxWidth().height((48 * textScale).dp)
                    .background(if (feedback == null) canvas else feedbackSurface)
                    .cornerRadius(10.dp).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Label("Poros", if (feedback == null) foreground else muted,
                        bold = true, fontSize = if (feedback == null) 14 else 11, maxLines = 1)
                    feedback?.let {
                        val entry = it.entry
                        val message = if (entry != null && it.message.startsWith("Recorded "))
                            "Saved ${formatMoney(entry.amountMinor, entry.currencyCode)}" else it.message
                        Label(message, accent, fontSize = 13, maxLines = 2)
                    }
                }
                feedback?.entry?.let { entry ->
                    ActionLabel("Undo", actionRunCallback<QuickExpenseAction>(actionParametersOf(
                        operationKey to "undo", targetKey to entry.id,
                    )), "Undo", 56)
                }
                ActionLabel("Edit", configure, "Configure", 48)
            }
            if (layout.columns == 1) {
                tiles.forEach { tile ->
                    Tile(tile, layout.compact,
                        GlanceModifier.fillMaxWidth().defaultWeight(),
                        minimal = layout.actionSlots == 1 && size.height < 88.dp)
                }
            } else tiles.chunked(layout.columns).forEach { row ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEach { tile ->
                        Tile(tile, layout.compact, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    tile: WidgetTile,
    compact: Boolean,
    modifier: GlanceModifier,
    minimal: Boolean = false,
) {
    // The outer container supplies a real gap in RemoteViews.
    Box(modifier.padding(if (minimal) 2.dp else 4.dp)) {
        Column(
            GlanceModifier.fillMaxSize()
                .background(if (tile.emphasized) actionSurface else panel)
                .cornerRadius(12.dp).clickable(tile.action)
                .semantics { contentDescription = tile.description }
                .padding(horizontal = if (minimal) 6.dp else 10.dp,
                    vertical = if (compact) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textColor = if (tile.emphasized) onAction else foreground
            val secondary = if (tile.emphasized) onAction else muted
            if (minimal) Label(tile.title, textColor, bold = true, fontSize = 14, maxLines = 2)
            else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(tile.title, secondary, GlanceModifier.defaultWeight(),
                        fontSize = 12, maxLines = 1)
                    tile.currency?.let { currency ->
                        Label(currency, secondary, fontSize = 10, maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(if (compact) 2.dp else 6.dp))
                Label(tile.primaryText, textColor, bold = true,
                    fontSize = when {
                        tile.primaryText.length > 10 -> 18
                        compact -> 20
                        else -> 24
                    }, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CompactFeedback(
    feedback: ExpenseFeedback,
    custom: WidgetTile,
    configure: Action,
    horizontal: Boolean,
) {
    val entry = feedback.entry
    val action = if (entry == null) configure else
        actionRunCallback<QuickExpenseAction>(actionParametersOf(
            operationKey to "undo", targetKey to entry.id,
        ))
    val message = if (entry != null && feedback.message.startsWith("Recorded "))
        formatMoney(entry.amountMinor, entry.currencyCode) else feedback.message

    @Composable
    fun FeedbackTile(modifier: GlanceModifier) {
        Box(modifier.padding(2.dp)) {
            Column(GlanceModifier.fillMaxSize().background(feedbackSurface).cornerRadius(12.dp)
                .clickable(action).semantics {
                    contentDescription = if (entry == null) feedback.message
                        else "Undo ${formatMoney(entry.amountMinor, entry.currencyCode)}"
                }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(message, foreground, fontSize = 11, maxLines = if (entry == null) 3 else 2)
                if (entry != null) Label("Undo", accent, bold = true, fontSize = 12, maxLines = 1)
            }
        }
    }
    if (horizontal) Row(GlanceModifier.fillMaxSize()) {
        FeedbackTile(GlanceModifier.defaultWeight().fillMaxHeight())
        Tile(custom, compact = true, modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            minimal = true)
    } else Column(GlanceModifier.fillMaxSize()) {
        FeedbackTile(GlanceModifier.fillMaxWidth().defaultWeight())
        Tile(custom, compact = true, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
    }
}

private fun entryAction(context: Context, widgetId: Int, presetId: String? = null): Action =
    actionStartActivity(Intent(context, QuickExpenseEntryActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        .putExtra("preset", presetId))

@Composable
private fun ActionLabel(text: String, action: Action, description: String, width: Int) {
    Box(GlanceModifier.width(width.dp).height(48.dp).clickable(action)
        .semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Label(text, accent, bold = true, fontSize = 12, maxLines = 1)
    }
}

@Composable
private fun Label(
    text: String,
    color: ColorProvider = foreground,
    modifier: GlanceModifier = GlanceModifier,
    bold: Boolean = false,
    maxLines: Int = 2,
    fontSize: Int = 14,
) {
    Text(text, modifier, style = TextStyle(color = color, fontSize = fontSize.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), maxLines = maxLines)
}

class QuickExpenseReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickExpenseWidget()
}

class QuickExpenseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val requestedAt = System.currentTimeMillis()
        val app = context.applicationContext as PersonalOsApplication
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val target = parameters[targetKey].orEmpty()
        try {
            when (parameters[operationKey]) {
                "record" -> {
                    app.quickExpenses.update { state ->
                        val old = state.feedback[widgetId.toString()]
                        state.copy(feedback = state.feedback + (widgetId.toString() to
                            ExpenseFeedback("Recording…", System.currentTimeMillis() + FEEDBACK_MILLIS,
                                old?.entry)))
                    }
                    QuickExpenseWidget().update(context, glanceId)
                    app.quickExpenseRecorder.recordPreset(widgetId, target, requestedAt = requestedAt,
                        expectedRevision = requireNotNull(parameters[revisionKey]) {
                            "Preset changed. Open Configure and retry"
                        })
                }
                "undo" -> app.quickExpenseRecorder.undo(widgetId, target)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            app.quickExpenses.update { state ->
                state.copy(feedback = state.feedback + (widgetId.toString() to ExpenseFeedback(
                    when {
                        error !is IllegalArgumentException -> "Could not finish. Tap again to recover."
                        parameters[operationKey] == "undo" -> "Undo unavailable. Review Money."
                        else -> "Preset unavailable. Open Configure."
                    },
                    System.currentTimeMillis() + FEEDBACK_MILLIS,
                    state.feedback[widgetId.toString()]?.entry,
                )))
            }
        } finally {
            updateAppWidgetState(context, glanceId) { it[refreshKey] = System.currentTimeMillis() }
            QuickExpenseWidget().updateAll(context)
            scheduleFeedbackRefresh(context)
        }
    }
}

internal fun scheduleFeedbackRefresh(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "quick-expense-feedback", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<QuickExpenseRefreshWorker>()
            .setInitialDelay(FEEDBACK_MILLIS, TimeUnit.MILLISECONDS).build(),
    )
}

class QuickExpenseRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PersonalOsApplication).quickExpenses.update { state ->
            state.copy(feedback = state.feedback.filterValues {
                it.expiresAt > System.currentTimeMillis()
            })
        }
        QuickExpenseWidget().updateAll(applicationContext)
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.retry()
    }
}
