package com.jevon.personalos.data

import androidx.room.withTransaction
import com.jevon.personalos.today.TodayContent
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZonedDateTime
import com.jevon.personalos.subscriptions.nextUnpaid
import com.jevon.personalos.schedule.scheduleRange

private val moneyTables = arrayOf("accounts", "categories", "entries", "budgets")
fun PersonalDatabase.observeMoney(month: YearMonth, kind: String, account: String, category: String, currency: String, search: String, page: Int) =
    invalidationTracker.createFlow(*moneyTables, "subscription_payments").map {
        withTransaction {
            val dao = personalDao()
            val start = month.atDay(1).toEpochDay()
            val end = month.plusMonths(1).atDay(1).toEpochDay()
            MoneySnapshot(dao.balances(), dao.categories(), dao.budgetProjections(start), dao.totals(start, end),
                dao.history(start, end, kind, account, category, currency, search, 51, page * 50), dao.entryCount(start, end),
                dao.history(start, end, "", "", "", "", "", 5, 0), dao.categorySpending(start, end),
                dao.subscriptionPayments().associate { it.entryId to it.subscriptionId })
        }
    }

fun PersonalDatabase.observeToday(now: ZonedDateTime, settings: Settings = Settings()) =
    invalidationTracker.createFlow(*moneyTables, "events", "reminders", "reminder_completions", "subscriptions", "subscription_payments", "library_items").map {
        withTransaction {
            val dao = personalDao()
            val date = now.toLocalDate()
            val month = YearMonth.from(date)
            val budgets = dao.budgetProjections(month.atDay(1).toEpochDay())
            val eligible = com.jevon.personalos.today.eligibleTodayBudgets(budgets.map { it.budget }, date)
            val chosen = com.jevon.personalos.today.selectTodayBudget(eligible, settings)
            val schedule=dao.scheduleRange(date,date.plusDays(7),now)
            val due=(schedule.overdue+schedule.reminders.filter {!it.completed && (it.due==null || java.time.Instant.ofEpochMilli(it.due).atZone(now.zone).toLocalDate()<=date)})
                .distinctBy{it.key}.sortedWith(compareBy<com.jevon.personalos.schedule.ReminderOccurrence>{it.due==null}.thenBy{it.due}.thenBy{it.reminder.id}).take(4)
            val events=schedule.events.filter{it.end>now.toInstant().toEpochMilli()}.take(4).map{it.event.copy(startsAtMillis=it.start,endsAtMillis=it.end)}
            val payments = dao.subscriptionPayments().groupBy { it.subscriptionId }
            val renewals = dao.subscriptions().asSequence().filter { it.active }.mapNotNull { s ->
                nextUnpaid(s, payments[s.id].orEmpty().map { it.dueEpochDay }.toSet())?.let { s.copy(nextDueEpochDay = it) }
            }.filter { it.nextDueEpochDay <= date.plusDays(7).toEpochDay() }.sortedWith(compareBy<SubscriptionEntity> { it.nextDueEpochDay }.thenBy { it.id }).take(4).toList()
            TodayContent(date, now, events, due.map{it.reminder.copy(dueAtMillis=it.due)},
                budgets.firstOrNull { it.budget.id == chosen?.id }?.summary(),
                renewals, dao.todayLibrary(),due,schedule.events.filter{it.end>now.toInstant().toEpochMilli()}.take(4),
                eligible, settings.preferredCurrency, settings.todayBudgetId)
        }
    }
