package com.jevon.personalos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val surface = ColorProvider(R.color.widget_panel)
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
    val scheduleCount: Int,
)

internal fun dashboardLayout(width: Float, height: Float, fontScale: Float): DashboardLayout {
    val scale = fontScale.coerceAtLeast(1f)
    return DashboardLayout(
        compact = width / scale < 280 || height / scale < 184,
        spacious = height / scale >= 300,
        short = height / scale < 140,
        showSecondary = height / scale >= 208,
        scheduleCount = ((height / scale - 44) / if (width / scale >= 280) 88 else 104)
            .toInt().coerceIn(1, 4),
    )
}

abstract class DashboardWidget internal constructor(private val kind: DashboardKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PersonalOsApplication
        provideContent {
            val revision = currentState<Preferences>()[dashboardRefreshKey]
            val source = remember(revision) {
                flow<Result<DashboardData>?> {
                    emit(Result.success(loadDashboard(app, kind, ZonedDateTime.now())))
                }.catch { emit(Result.failure(it)) }
            }
            val result by source.collectAsState(initial = null)
            val size = LocalSize.current
            val layout = dashboardLayout(
                size.width.value, size.height.value, context.resources.configuration.fontScale,
            )
            val open = dashboardAction(context, kind.destination)
            Column(
                GlanceModifier.fillMaxSize().background(surface).cornerRadius(24.dp)
                    .clickable(open).semantics { contentDescription = kind.title }
                    .padding(if (layout.short) 10.dp else 16.dp),
            ) {
                val data = result?.getOrNull()
                if (data == null) {
                    if (!layout.short) WidgetHeader(kind.title)
                    Panel(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        WidgetText(if (result == null) "Loading…" else "Couldn't load",
                            size = 20, bold = true)
                        WidgetText(if (result == null) kind.title else "Open Poros to retry",
                            color = muted, size = 12, lines = 2)
                    }
                } else when (kind) {
                    DashboardKind.BUDGET -> BudgetContent(data, layout)
                    DashboardKind.TODAY -> TodayContent(context, data, layout)
                    DashboardKind.SCHEDULE -> ScheduleContent(context, data, layout)
                }
            }
        }
    }
}

class BudgetTrackerWidget : DashboardWidget(DashboardKind.BUDGET)
class TodayOverviewWidget : DashboardWidget(DashboardKind.TODAY)
class UpcomingScheduleWidget : DashboardWidget(DashboardKind.SCHEDULE)

abstract class DashboardReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DashboardRefresh.request(context)
    }
}

class BudgetTrackerReceiver : DashboardReceiver() {
    override val glanceAppWidget = BudgetTrackerWidget()
}

class TodayOverviewReceiver : DashboardReceiver() {
    override val glanceAppWidget = TodayOverviewWidget()
}

class UpcomingScheduleReceiver : DashboardReceiver() {
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
    target?.let {
        intent.putExtra("recordDomain", it.domain).putExtra("recordId", it.id)
        it.day?.let { day -> intent.putExtra("occurrenceDay", day) }
    }
    return actionStartActivity(intent)
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
    WidgetText(summary.budget.name, color = muted, size = 12, bold = true)
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
private fun ColumnScope.TodayContent(context: Context, data: DashboardData, layout: DashboardLayout) {
    val date = data.today.date
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WidgetText(date.dayOfMonth.toString(), size = if (layout.short) 32 else 44, bold = true)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            WidgetText(date.format(DateTimeFormatter.ofPattern(if (layout.compact) "EEE" else "EEEE")),
                color = indigo, size = 14, bold = true)
            WidgetText(date.format(DateTimeFormatter.ofPattern("MMM yyyy")), color = muted, size = 12)
        }
    }
    val next = data.items.firstOrNull()
    val budget = data.today.budget
    val renewal = data.renewal
    Column(GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically) {
        if (next != null) {
            // Keep the existing priority selection; only its visual prominence changes.
            Column(GlanceModifier.fillMaxWidth()
                .clickable(dashboardAction(context, "schedule", next.target)).padding(vertical = 4.dp)) {
                WidgetText(next.detail, color = indigo, size = 12,
                    lines = if (layout.short) 1 else 2)
                WidgetText(next.title, size = if (layout.short) 16 else 22, bold = true,
                    lines = if (layout.short) 1 else 2)
            }
        } else if (renewal != null) {
            Renewal(context, data, GlanceModifier.fillMaxWidth(), hero = !layout.short)
        } else {
            WidgetText("Your day is clear", size = if (layout.short) 16 else 22, bold = true,
                lines = if (layout.short) 1 else 2)
            if (layout.showSecondary) {
                Gap(6)
                WidgetText("No events or reminders due", color = muted, size = 14, lines = 2)
            }
        }
    }
    if (layout.showSecondary) {
        if (next != null && renewal != null && (!layout.compact || layout.spacious)) {
            Renewal(context, data, GlanceModifier.fillMaxWidth())
            Gap(8)
        }
        if (budget != null) {
            Column(GlanceModifier.fillMaxWidth()
                .clickable(dashboardAction(context, "money-budgets")).padding(vertical = 6.dp)) {
                WidgetText(budget.budget.name, color = muted, size = 12)
                val over = budget.remainingMinor < 0
                WidgetText("${formatMoney(BigInteger.valueOf(budget.remainingMinor).abs(), budget.budget.currencyCode)} " +
                    if (over) "over budget" else "left", color = if (over) error else positive,
                    size = 14, bold = true)
            }
        } else if (next != null && renewal != null && layout.compact && !layout.spacious) {
            Renewal(context, data, GlanceModifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Renewal(
    context: Context,
    data: DashboardData,
    modifier: GlanceModifier,
    hero: Boolean = false,
) {
    val renewal = requireNotNull(data.renewal)
    val subscription = renewal.subscription
    val due = LocalDate.ofEpochDay(renewal.day)
    Column(modifier.clickable(dashboardAction(
        context, "subscriptions", RecordTarget("SUBSCRIPTION", subscription.id)))
        .padding(vertical = 6.dp)) {
        WidgetText("Renewal · ${dashboardDate(due, data.today.date)}",
            color = positive, size = 12)
        WidgetText(subscription.name, bold = true, size = if (hero) 24 else 14,
            lines = if (hero) 2 else 1)
        WidgetText(renewalAmount(renewal), size = if (hero) 20 else 13, bold = true)
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
    } else {
        val items = data.items.take(layout.scheduleCount)
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(track)) {}
            }
            AgendaRow(context, item, GlanceModifier.fillMaxWidth().defaultWeight(),
                compact = layout.compact, minimal = layout.short)
        }
    }
}

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
        Column(GlanceModifier.width(if (compact) 58.dp else 92.dp)) {
            WidgetText(date, color = text, size = if (compact) 14 else 16,
                bold = true, lines = if (minimal) 1 else 3)
            if (time.isNotEmpty()) WidgetText(time, color = accent, size = 12, bold = true,
                lines = if (minimal) 1 else 2)
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(GlanceModifier.width(2.dp).height(if (minimal) 32.dp else 48.dp).background(accent)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            WidgetText(item.title, size = if (compact) 16 else 18, bold = true,
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
    WidgetText(detail, color = indigo, bold = true, size = 14)
    Gap(8)
}

@Composable
private fun Panel(
    modifier: GlanceModifier,
    action: Action? = null,
    padding: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = modifier.background(panel).cornerRadius(12.dp)
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
