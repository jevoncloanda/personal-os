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

private val surface = ColorProvider(R.color.widget_surface)
private val panel = ColorProvider(R.color.widget_panel)
private val text = ColorProvider(R.color.widget_text)
private val muted = ColorProvider(R.color.widget_muted)
private val indigo = ColorProvider(R.color.widget_accent)
private val error = ColorProvider(R.color.widget_error)
private val track = ColorProvider(R.color.widget_progress_track)

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
                GlanceModifier.fillMaxSize().background(surface).cornerRadius(16.dp)
                    .clickable(open).semantics { contentDescription = kind.title }
                    .padding(if (layout.short) 4.dp else 8.dp),
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
    val fontScale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
    val availableHeight = LocalSize.current.height.value / fontScale
    val compact = availableHeight < 184
    if (!compact) WidgetHeader("Budget")
    if (summary == null) {
        Panel(GlanceModifier.fillMaxWidth().defaultWeight(), padding = if (compact) 8 else 16) {
            if (compact) WidgetText("Budget", color = muted, size = 11)
            WidgetText("Set up a budget", color = indigo,
                size = if (compact) 16 else 24, bold = true, lines = 2)
            if (!compact) {
                Gap(8)
                WidgetText("Choose your spending limit in Budget Management.",
                    color = muted, size = 13, lines = 3)
            }
        }
        return
    }
    val tiny = availableHeight < 88
    Panel(GlanceModifier.fillMaxWidth().defaultWeight(), padding = when {
        tiny -> 4
        compact -> 8
        else -> 12
    }) {
        WidgetText(summary.budget.name, color = muted,
            size = if (compact) 11 else 14, bold = true)
        if (compact) {
            Remaining(summary, if (tiny) 18 else 26, compact = true)
        } else {
            WidgetText(budgetPeriod(summary), color = muted, size = 11)
            Column(GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically) {
                Remaining(summary, if (layout.compact) 28 else if (layout.spacious) 36 else 32)
            }
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    WidgetText("Spent", color = muted, size = 11)
                    MoneyValue(summary.spentMinor, summary.budget.currencyCode, 16)
                }
                Column(GlanceModifier.defaultWeight()) {
                    WidgetText("Limit", color = muted, size = 11)
                    MoneyValue(summary.budget.limitMinor, summary.budget.currencyCode, 16)
                }
            }
        }
        if (!compact || availableHeight >= 96) {
            Gap(if (compact) 4 else 10)
            BudgetProgress(summary)
        }
        if (layout.spacious) {
            Gap(10)
            val scope = if (summary.budget.categoryId == null) "Overall budget" else "Category budget"
            WidgetText("$scope · ${summary.budget.currencyCode}", color = muted, size = 11)
        }
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
    WidgetText(label, color = if (over) error else indigo, size = if (compact) 10 else 12)
}

@Composable
private fun BudgetProgress(summary: BudgetSummary) {
    LinearProgressIndicator(
        progress = summary.fraction,
        modifier = GlanceModifier.fillMaxWidth().height(7.dp),
        color = if (summary.remainingMinor < 0) error else indigo,
        backgroundColor = track,
    )
}

@Composable
private fun ColumnScope.TodayContent(context: Context, data: DashboardData, layout: DashboardLayout) {
    val date = data.today.date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    if (layout.short) {
        WidgetText(date, color = muted, size = 11, bold = true)
        Gap(4)
    } else WidgetHeader(date)
    val budget = data.today.budget
    val next = data.items.firstOrNull()
    val renewal = data.renewal
    val showNext = budget != null && next != null && layout.showSecondary
    val showRenewal = renewal != null && (budget != null || next != null) &&
        layout.showSecondary && (budget == null || next == null || !layout.compact || layout.spacious)
    val primary = GlanceModifier.fillMaxWidth().defaultWeight()
    if (budget != null) {
        Panel(primary, dashboardAction(context, "money-budgets"), padding = 8) {
            WidgetText("${budget.budget.name} · This month", color = muted, size = 11, bold = true)
            Remaining(budget, if (layout.short) 20 else if (layout.spacious) 34 else 26)
        }
    } else if (next != null) {
        AgendaTile(context, next, primary, wide = !layout.compact,
            hero = !showRenewal, minimal = layout.short)
    } else if (renewal != null) {
        Renewal(context, data, primary, hero = true)
    } else {
        Panel(primary) {
            WidgetText("Your day is clear", size = if (layout.short) 18 else 24,
                bold = true, lines = 2)
            Gap(8)
            WidgetText("No events or reminders due", color = muted, size = 13, lines = 2)
        }
    }
    if (showNext || showRenewal) {
        Gap(8)
        if (!layout.compact && showNext && showRenewal) {
            Row(GlanceModifier.fillMaxWidth().height(80.dp)) {
                AgendaTile(context, next!!, GlanceModifier.defaultWeight().fillMaxHeight(), minimal = true)
                Spacer(GlanceModifier.width(8.dp))
                Renewal(context, data, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        } else {
            if (showNext) AgendaTile(context, next!!,
                GlanceModifier.fillMaxWidth().height(80.dp), minimal = true)
            if (showRenewal) {
                if (showNext) Gap(8)
                Renewal(context, data, GlanceModifier.fillMaxWidth().height(80.dp))
            }
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
    Panel(modifier, dashboardAction(
        context, "subscriptions", RecordTarget("SUBSCRIPTION", subscription.id)),
        padding = if (hero) 16 else 10,
    ) {
        WidgetText("Renewal · ${dashboardDate(due, data.today.date)}",
            color = indigo, size = 11)
        WidgetText(subscription.name, bold = true, size = if (hero) 24 else 14,
            lines = if (hero) 2 else 1)
        WidgetText(renewalAmount(renewal), size = if (hero) 20 else 13, bold = true)
    }
}

@Composable
private fun ColumnScope.ScheduleContent(context: Context, data: DashboardData, layout: DashboardLayout) {
    if (!layout.short) WidgetHeader("Upcoming")
    if (data.items.isEmpty()) {
        Panel(GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetText("Nothing coming up", size = if (layout.short) 18 else 24,
                bold = true, lines = 2)
            Gap(8)
            WidgetText("No events or renewals in the next 30 days, or open reminders.",
                color = muted, size = 12, lines = 3)
        }
    } else {
        val items = data.items.take(layout.scheduleCount)
        items.forEachIndexed { index, item ->
            if (index > 0) Gap(8)
            AgendaTile(context, item, GlanceModifier.fillMaxWidth().defaultWeight(),
                wide = !layout.compact, hero = items.size == 1, minimal = layout.short)
        }
    }
}

@Composable
private fun AgendaTile(
    context: Context,
    item: DashboardItem,
    modifier: GlanceModifier,
    wide: Boolean = false,
    hero: Boolean = false,
    minimal: Boolean = false,
) {
    val subscription = item.target.domain == "SUBSCRIPTION"
    val destination = if (subscription) "subscriptions" else "schedule"
    Panel(modifier, dashboardAction(context, destination, item.target),
        padding = if (minimal) 8 else 12,
    ) {
        val kind = when (item.target.domain) {
            "EVENT" -> "Event"
            "SUBSCRIPTION" -> "Subscription"
            else -> "Reminder"
        }
        if (wide) {
            // The existing display metadata already carries all-day, overdue and viewing-zone time.
            val timing = item.detail.substringAfter(" · ").split(" · ")
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.width(70.dp)) {
                    WidgetText(timing.dropLast(1).joinToString(" · "), color = muted,
                        size = 11, lines = 2)
                    WidgetText(timing.last(), color = indigo, size = if (hero) 18 else 15,
                        bold = true, lines = 2)
                }
                Spacer(GlanceModifier.width(12.dp))
                Box(GlanceModifier.width(1.dp).height(if (hero) 64.dp else 40.dp)
                    .background(track)) {}
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    WidgetText(kind, color = indigo, size = 11)
                    Gap(4)
                    WidgetText(item.title, size = if (hero) 24 else 18,
                        bold = true, lines = if (subscription && !hero) 1 else 2)
                    item.amount?.let { WidgetText(it, size = 12, bold = true) }
                }
            }
        } else {
            WidgetText(item.detail, color = indigo, size = 11, lines = if (minimal) 1 else 2)
            Gap(if (minimal) 2 else 6)
            WidgetText(item.title, size = when {
                minimal -> 16
                hero -> 24
                else -> 18
            }, bold = true, lines = if (subscription && !hero) 1 else 2)
            item.amount?.let { WidgetText(it, size = 12, bold = true) }
        }
    }
}

@Composable
private fun WidgetHeader(detail: String) {
    Row(GlanceModifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        WidgetText("Poros", modifier = GlanceModifier.defaultWeight(), bold = true, size = 14)
        WidgetText(detail, color = muted, size = 11)
    }
    Gap(4)
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
