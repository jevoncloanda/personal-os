package com.jevon.personalos.widget

import androidx.room.withTransaction
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.data.BudgetProjection
import com.jevon.personalos.data.Settings
import com.jevon.personalos.data.observeToday
import com.jevon.personalos.schedule.EventOccurrence
import com.jevon.personalos.schedule.RecordTarget
import com.jevon.personalos.schedule.ReminderOccurrence
import com.jevon.personalos.schedule.RenewalForecast
import com.jevon.personalos.schedule.scheduleRange
import com.jevon.personalos.today.BudgetSummary
import com.jevon.personalos.today.TodayContent
import com.jevon.personalos.today.formatMoney
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class DashboardKind(val title: String, val destination: String) {
    BUDGET("Budget Tracker", "money-budgets"),
    TODAY("Today Overview", "today"),
    SCHEDULE("Upcoming Schedule", "schedule"),
}

internal data class DashboardItem(
    val title: String,
    val detail: String,
    val target: RecordTarget,
    val sortAt: Long?,
    val amount: String? = null,
)

internal data class DashboardData(
    val today: TodayContent,
    val budget: BudgetSummary?,
    val items: List<DashboardItem>,
    val renewal: RenewalForecast? = null,
)

internal fun dashboardRenewal(
    forecasts: List<RenewalForecast>,
    today: LocalDate,
): RenewalForecast? = forecasts
    .filter {
        it.subscription.active &&
            it.day in today.toEpochDay()..today.plusDays(7).toEpochDay()
    }
    .minWithOrNull(compareBy<RenewalForecast> { it.day }.thenBy { it.subscription.id })

internal fun renewalAmount(renewal: RenewalForecast): String = with(renewal.subscription) {
    "$currencyCode · ${formatMoney(amountMinor, currencyCode)}"
}

/** Today selection wins. Fallback budgets retain their own currency, period and category scope. */
internal fun dashboardBudget(
    today: BudgetSummary?,
    current: List<BudgetProjection>,
    settings: Settings,
): BudgetSummary? = today ?: current
    .filter { it.budget.currencyCode == settings.preferredCurrency }
    .sortedWith(compareBy<BudgetProjection> { it.budget.categoryId != null }.thenBy { it.budget.id })
    .firstOrNull()?.summary()

internal fun dashboardDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
}

/** Input is expanded by Schedule's occurrence logic, never by widget-specific recurrence code. */
internal fun dashboardItems(
    events: List<EventOccurrence>,
    reminders: List<ReminderOccurrence>,
    now: ZonedDateTime,
    forecasts: List<RenewalForecast> = emptyList(),
): List<DashboardItem> {
    val millis = now.toInstant().toEpochMilli()
    val eventItems = events.filter { it.end > millis }.map { occurrence ->
        val start = Instant.ofEpochMilli(occurrence.start).atZone(now.zone)
        val time = when {
            occurrence.event.allDay -> "All day"
            occurrence.start <= millis -> "Now"
            else -> start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
        DashboardItem(
            occurrence.event.title,
            "Event · ${dashboardDate(start.toLocalDate(), now.toLocalDate())} · $time",
            RecordTarget("EVENT", occurrence.event.id, occurrence.day),
            occurrence.start,
        )
    }
    val reminderItems = reminders.distinctBy { it.key }.filterNot { it.completed }.map { occurrence ->
        val due = occurrence.due?.let { Instant.ofEpochMilli(it).atZone(now.zone) }
        val date = if (occurrence.reminder.dateOnly) occurrence.day?.let(LocalDate::ofEpochDay)
            else due?.toLocalDate()
        val overdue = date != null && if (occurrence.reminder.dateOnly) date < now.toLocalDate()
            else occurrence.due!! < millis
        val timing = when {
            date == null -> "No due date"
            occurrence.reminder.dateOnly -> "${dashboardDate(date, now.toLocalDate())} · All day"
            else -> "${dashboardDate(date, now.toLocalDate())} · " +
                due!!.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
        DashboardItem(
            occurrence.reminder.title,
            "Reminder · ${if (overdue) "Overdue · " else ""}$timing",
            RecordTarget("REMINDER", occurrence.reminder.id, occurrence.day),
            occurrence.due,
        )
    }
    val renewalItems = forecasts
        .filter {
            it.subscription.active && it.day >= now.toLocalDate().toEpochDay() &&
                it.day < now.toLocalDate().plusDays(30).toEpochDay()
        }
        .distinctBy { it.subscription.id to it.day }
        .map { renewal ->
            val date = LocalDate.ofEpochDay(renewal.day)
            DashboardItem(
                renewal.subscription.name,
                "Subscription · ${dashboardDate(date, now.toLocalDate())} · Forecast",
                RecordTarget("SUBSCRIPTION", renewal.subscription.id, renewal.day),
                date.atStartOfDay(now.zone).toInstant().toEpochMilli(),
                renewalAmount(renewal),
            )
        }
    return (eventItems + reminderItems + renewalItems).sortedWith(
        compareBy<DashboardItem> { it.sortAt == null }
            .thenBy { it.sortAt }
            .thenBy { it.target.domain }
            .thenBy { it.target.id },
    )
}

internal suspend fun loadDashboard(
    app: PersonalOsApplication,
    kind: DashboardKind,
    now: ZonedDateTime,
): DashboardData {
    val settings = app.settings.settings.first()
    val today = app.database.observeToday(now, settings).first()
    return app.database.withTransaction {
        val dao = app.database.personalDao()
        val budget = if (kind == DashboardKind.BUDGET) {
            dashboardBudget(today.budget, dao.budgetProjections(now.toLocalDate().toEpochDay()), settings)
        } else today.budget
        val schedule = if (kind != DashboardKind.BUDGET) {
            val days = if (kind == DashboardKind.SCHEDULE) 30L else 8L
            dao.scheduleRange(now.toLocalDate(), now.toLocalDate().plusDays(days), now)
        } else null
        val items = if (kind == DashboardKind.SCHEDULE && schedule != null) {
            dashboardItems(schedule.events, schedule.overdue + schedule.reminders, now,
                schedule.forecasts)
        } else dashboardItems(today.scheduleEvents, today.scheduleReminders, now)
        DashboardData(today, budget, items,
            dashboardRenewal(schedule?.forecasts.orEmpty(), now.toLocalDate()))
    }
}
