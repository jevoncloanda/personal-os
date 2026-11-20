package com.jevon.personalos.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jevon.personalos.MainActivity
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.R
import com.jevon.personalos.schedule.RecordTarget
import com.jevon.personalos.today.BudgetSummary
import com.jevon.personalos.today.formatMoney
import kotlinx.coroutines.CancellationException
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val surface = ColorProvider(R.color.widget_surface)
private val panel = ColorProvider(R.color.widget_panel)
private val text = ColorProvider(R.color.widget_text)
private val muted = ColorProvider(R.color.widget_muted)
private val indigo = ColorProvider(R.color.widget_accent)
private val error = ColorProvider(R.color.widget_error)
private val track = ColorProvider(R.color.widget_progress_track)
private val positive = ColorProvider(R.color.dashboard_positive)
private val warning = ColorProvider(R.color.dashboard_warning)

internal data class DashboardLayout(
    val compact: Boolean,
    val spacious: Boolean,
    val short: Boolean,
    val showSecondary: Boolean,
    val scrollAgenda: Boolean,
)

internal fun dashboardLayout(width: Float, height: Float, fontScale: Float): DashboardLayout {
    val scale = fontScale.coerceAtLeast(1f)
    return DashboardLayout(
        compact = width / scale < 280 || height / scale < 184,
        spacious = height / scale >= 300,
        short = height / scale < 140,
        showSecondary = height / scale >= 208,
        scrollAgenda = height / scale >= 184,
    )
}

abstract class DashboardWidget internal constructor(private val kind: DashboardKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PersonalOsApplication
        // A new session must have a result before it can replace the launcher's current views.
        val initialState = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initialRevision = initialState[dashboardRefreshKey]
        val initialResult = readDashboard(app)
        provideContent {
            val revision = currentState<Preferences>()[dashboardRefreshKey]
            var result by remember { mutableStateOf(initialResult) }
            var loadedRevision by remember { mutableStateOf(initialRevision) }
            LaunchedEffect(revision) {
                if (revision != loadedRevision) {
                    result = readDashboard(app)
                    loadedRevision = revision
                }
            }
            val size = LocalSize.current
            val layout = dashboardLayout(
                size.width.value, size.height.value, context.resources.configuration.fontScale,
            )
            val open = dashboardAction(context, kind.destination)
            Column(
                GlanceModifier.fillMaxSize().background(surface).cornerRadius(widgetFrameRadius)
                    .clickable(open).semantics { contentDescription = kind.title }
                    .padding(if (layout.short) 10.dp else 16.dp),
            ) {
                val data = result.getOrNull()
                if (data == null) {
                    if (!layout.short) WidgetHeader(kind.title)
                    Panel(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        WidgetText("Couldn't load", size = 20, bold = true)
                        WidgetText("Open Poros to retry", color = muted, size = 12, lines = 2)
                    }
                } else when (kind) {
                    DashboardKind.BUDGET -> BudgetContent(data, layout)
                    DashboardKind.TODAY -> TodayContent(context, data, layout)
                    DashboardKind.SCHEDULE -> ScheduleContent(context, data, layout)
                }
            }
        }
    }

    private suspend fun readDashboard(app: PersonalOsApplication): Result<DashboardData> = try {
        Result.success(loadDashboard(app, kind, ZonedDateTime.now()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

class BudgetTrackerWidget : DashboardWidget(DashboardKind.BUDGET)
class TodayOverviewWidget : DashboardWidget(DashboardKind.TODAY)
class UpcomingScheduleWidget : DashboardWidget(DashboardKind.SCHEDULE)

class BudgetTrackerReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BudgetTrackerWidget()
}

class TodayOverviewReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayOverviewWidget()
}

class UpcomingScheduleReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = UpcomingScheduleWidget()
}

private fun dashboardAction(
    context: Context,
    destination: String,
    target: RecordTarget? = null,
): Action {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("widgetDestination", destination)
    val identity = Uri.Builder().scheme("poros-widget").authority(destination)
    target?.let {
        identity.appendPath(it.domain).appendPath(it.id)
        it.day?.let { day -> identity.appendPath(day.toString()) }
        intent.putExtra("recordDomain", it.domain).putExtra("recordId", it.id)
        it.day?.let { day -> intent.putExtra("occurrenceDay", day) }
    }
    return actionStartActivity(intent.setData(identity.build()))
}

@Composable
private fun ColumnScope.BudgetContent(data: DashboardData, layout: DashboardLayout) {
    val summary = data.budget
    if (summary == null) {
        WidgetHeader("Budget")
        Column(GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically) {
            WidgetText("Set up a budget", color = indigo, size = 22, bold = true, lines = 2)
            if (!layout.short) {
                Gap(8)
                WidgetText("Choose your spending limit in Budget Management.",
                    color = muted, size = 14, lines = 3)
            }
        }
        return
    }
    WidgetTitle(summary.budget.name)
    if (layout.showSecondary) WidgetText(budgetPeriod(summary), color = muted, size = 12)
    Column(GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically) {
        Remaining(summary, if (layout.short) 26 else if (layout.spacious) 36 else 32)
    }
    BudgetProgress(summary)
    if (!layout.short) {
        Gap(10)
        if (!layout.compact || layout.spacious) {
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    WidgetText("Spent", color = muted, size = 12)
                    MoneyValue(summary.spentMinor, summary.budget.currencyCode, 16)
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    WidgetText("Limit", color = muted, size = 12)
                    MoneyValue(summary.budget.limitMinor, summary.budget.currencyCode, 16)
                }
            }
        } else {
            WidgetText("Spent ${formatMoney(summary.spentMinor, summary.budget.currencyCode)}",
                color = muted, size = 12)
            WidgetText("of ${formatMoney(summary.budget.limitMinor, summary.budget.currencyCode)}",
                color = muted, size = 12)
        }
    }
    if (layout.spacious) {
        Gap(12)
        val scope = if (summary.budget.categoryId == null) "Overall budget" else "Category budget"
        WidgetText("$scope · ${summary.budget.currencyCode}", color = muted, size = 12)
    }
}

private fun budgetPeriod(summary: BudgetSummary): String {
    val start = LocalDate.ofEpochDay(summary.budget.startEpochDay)
    val end = LocalDate.ofEpochDay(summary.budget.endExclusiveEpochDay).minusDays(1)
    return "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
        end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}

@Composable
private fun Remaining(summary: BudgetSummary, size: Int, compact: Boolean = false) {
    val over = summary.remainingMinor < 0
    val amount = formatMoney(BigInteger.valueOf(summary.remainingMinor).abs(), summary.budget.currencyCode)
    WidgetText(amount, color = if (over) error else text,
        size = if (amount.length > 13) minOf(size, 20) else size, bold = true)
    val label = when {
        over -> "Over budget"
        summary.spentMinor == 0L -> "Remaining · Unspent"
        else -> "Remaining"
    }
    WidgetText(label, color = if (over) error else positive, size = 12)
}

@Composable
private fun BudgetProgress(summary: BudgetSummary) {
    LinearProgressIndicator(
        progress = summary.fraction,
        modifier = GlanceModifier.fillMaxWidth().height(12.dp),
        color = if (summary.remainingMinor < 0) error else indigo,
        backgroundColor = track,
    )
}

@Composable
private fun TodayContent(context: Context, data: DashboardData, layout: DashboardLayout) {
    val date = data.today.date
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WidgetText(date.dayOfMonth.toString(), size = if (layout.short) 32 else 44, bold = true)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            WidgetTitle(date.format(DateTimeFormatter.ofPattern(if (layout.compact) "EEE" else "EEEE")))
            WidgetText(date.format(DateTimeFormatter.ofPattern("MMM yyyy")), color = muted, size = 12)
        }
    }
    val agenda = todayAgenda(data.items, date)
    val budget = data.today.budget
    Gap(6)
    if (agenda.dueToday.isEmpty()) {
        WidgetText("Your day is clear", size = if (layout.short) 16 else 20, bold = true)
        val next = agenda.upNext
        if (next != null && !layout.short) {
            Gap(6)
            val actualDate = requireNotNull(next.date).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
            TodayItem(context, next, "Up next · $actualDate", titleSize = 14)
        }
    } else {
        agenda.dueToday.take(if (layout.spacious) 2 else 1).forEach { item ->
            TodayItem(context, item, item.detail, titleSize = if (layout.short) 16 else 20)
        }
    }
    if (data.renewal != null && (layout.showSecondary ||
            (!layout.short && agenda.dueToday.isEmpty() && agenda.upNext == null))
    ) {
        Gap(4)
        Renewal(context, data)
    }
    if (layout.showSecondary && budget != null) {
        Column(GlanceModifier.fillMaxWidth()
            .clickable(dashboardAction(context, "money-budgets")).padding(vertical = 4.dp)) {
            WidgetText(budget.budget.name, color = muted, size = 12)
            val over = budget.remainingMinor < 0
            val amount = formatMoney(
                BigInteger.valueOf(budget.remainingMinor).abs(), budget.budget.currencyCode,
            )
            val label = if (over) "over budget" else "left"
            WidgetText("$amount $label", color = if (over) error else positive, size = 14, bold = true)
        }
    }
}

@Composable
private fun TodayItem(
    context: Context,
    item: DashboardItem,
    detail: String,
    titleSize: Int,
) {
    Column(GlanceModifier.fillMaxWidth()
        .clickable(dashboardAction(context, "schedule", item.target))) {
        WidgetText(detail, color = indigo, size = 12)
        WidgetText(item.title, size = titleSize, bold = true)
    }
}

@Composable
private fun Renewal(context: Context, data: DashboardData) {
    val renewal = requireNotNull(data.renewal)
    val subscription = renewal.subscription
    val due = LocalDate.ofEpochDay(renewal.day)
    Column(GlanceModifier.fillMaxWidth().clickable(dashboardAction(
        context, "subscriptions", RecordTarget("SUBSCRIPTION", subscription.id)))
        .padding(vertical = 4.dp)) {
        WidgetText("Renewal · ${dashboardDate(due, data.today.date)}",
            color = positive, size = 12)
        WidgetText(subscription.name, bold = true, size = 14)
        WidgetText(renewalAmount(renewal), size = 13, bold = true)
    }
}

@Composable
private fun ColumnScope.ScheduleContent(context: Context, data: DashboardData, layout: DashboardLayout) {
    if (!layout.short) WidgetHeader("Upcoming")
    if (data.items.isEmpty()) {
        Column(GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically) {
            WidgetText("Nothing coming up", size = if (layout.short) 18 else 24,
                bold = true, lines = 2)
            if (!layout.short) {
                Gap(8)
                WidgetText("No events or renewals in the next 30 days, or open reminders.",
                    color = muted, size = 14, lines = 3)
            }
        }
    } else if (layout.scrollAgenda) {
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(data.items, itemId = { agendaItemId(it.target) }) { item ->
                Column(GlanceModifier.fillMaxWidth()) {
                    AgendaRow(context, item, GlanceModifier.fillMaxWidth(),
                        compact = layout.compact, minimal = false)
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(track)) {}
                }
            }
        }
    } else {
        AgendaRow(context, data.items.first(), GlanceModifier.fillMaxWidth().defaultWeight(),
            compact = layout.compact, minimal = layout.short)
    }
}

private fun agendaItemId(target: RecordTarget): Long =
    java.util.UUID.nameUUIDFromBytes("${target.domain}/${target.id}/${target.day}".toByteArray())
        .mostSignificantBits and Long.MAX_VALUE

@Composable
private fun AgendaRow(
    context: Context,
    item: DashboardItem,
    modifier: GlanceModifier,
    compact: Boolean,
    minimal: Boolean,
) {
    val subscription = item.target.domain == "SUBSCRIPTION"
    val accent = when (item.target.domain) {
        "EVENT" -> indigo
        "SUBSCRIPTION" -> positive
        else -> warning
    }
    // Use the existing viewing-zone/date-only/overdue display metadata unchanged.
    val timing = item.detail.substringAfter(" · ").split(" · ")
    val date = timing.dropLast(1).joinToString(" · ").ifEmpty { timing.last() }
    val time = if (timing.size > 1) timing.last() else ""
    val kind = when (item.target.domain) {
        "EVENT" -> "Event"
        "SUBSCRIPTION" -> "Renewal · Forecast"
        else -> "Reminder"
    }
    Row(modifier.clickable(dashboardAction(context,
        if (subscription) "subscriptions" else "schedule", item.target)).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.width(if (compact) 62.dp else 92.dp)) {
            WidgetText(date, color = text, size = if (compact) 13 else 14,
                bold = true, lines = if (minimal) 1 else 3)
            if (time.isNotEmpty()) WidgetText(time, color = accent, size = 12, bold = true,
                lines = if (minimal) 1 else 2)
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(GlanceModifier.width(2.dp).height(32.dp).background(accent)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            WidgetText(item.title, size = if (compact) 15 else 16, bold = true,
                lines = if (minimal) 1 else 2)
            if (!minimal) WidgetText(kind, color = accent, size = 12,
                lines = if (compact) 2 else 1)
            item.amount?.let { WidgetText(it, color = accent, size = 12, bold = true,
                lines = if (minimal) 1 else 2) }
        }
    }
}

@Composable
private fun WidgetHeader(detail: String) {
    WidgetTitle(detail)
    Gap(8)
}

@Composable
private fun Panel(
    modifier: GlanceModifier,
    action: Action? = null,
    padding: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = modifier.background(panel).cornerRadius(widgetPanelRadius)
    Column(
        (if (action == null) container else container.clickable(action)).padding(padding.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun MoneyValue(amount: Long, currency: String, size: Int) {
    val value = formatMoney(amount, currency)
    WidgetText(value, size = if (value.length > 13) minOf(size, 13) else size, bold = true)
}

@Composable
private fun Gap(height: Int) = Spacer(GlanceModifier.height(height.dp))

@Composable
private fun WidgetText(
    value: String,
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = text,
    size: Int = 14,
    bold: Boolean = false,
    lines: Int = 1,
) {
    Text(value, modifier = modifier, maxLines = lines, style = TextStyle(
        color = color, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    ))
}
