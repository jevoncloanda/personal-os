package com.jevon.personalos.data

import androidx.room.withTransaction
import com.jevon.personalos.today.TodayContent
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZonedDateTime
import com.jevon.personalos.subscriptions.nextUnpaid

private val moneyTables = arrayOf("accounts", "categories", "entries", "budgets")
fun PersonalDatabase.observeMoney(month: YearMonth, kind: String, account: String, category: String, currency: String, search: String, page: Int) =
    invalidationTracker.createFlow(*moneyTables).map {
        withTransaction {
            val dao = personalDao()
            val start = month.atDay(1).toEpochDay()
            val end = month.plusMonths(1).atDay(1).toEpochDay()
            MoneySnapshot(dao.balances(), dao.categories(), dao.budgetProjections(start), dao.totals(start, end),
                dao.history(start, end, kind, account, category, currency, search, 51, page * 50), dao.entryCount(start, end),
                dao.history(start, end, "", "", "", "", "", 5, 0), dao.categorySpending(start, end))
        }
    }

fun PersonalDatabase.observeToday(now: ZonedDateTime) =
    invalidationTracker.createFlow(*moneyTables, "events", "reminders", "subscriptions", "subscription_payments", "library_items").map {
        withTransaction {
            val dao = personalDao()
            val date = now.toLocalDate()
            val end = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val month = YearMonth.from(date)
            val payments = dao.subscriptionPayments().groupBy { it.subscriptionId }
            val renewals = dao.subscriptions().asSequence().filter { it.active }.mapNotNull { s ->
                nextUnpaid(s, payments[s.id].orEmpty().map { it.dueEpochDay }.toSet())?.let { s.copy(nextDueEpochDay = it) }
            }.filter { it.nextDueEpochDay <= date.plusDays(7).toEpochDay() }.sortedWith(compareBy<SubscriptionEntity> { it.nextDueEpochDay }.thenBy { it.id }).take(4).toList()
            TodayContent(date, now, dao.todayEvents(now.toInstant().toEpochMilli(), end), dao.todayReminders(end),
                dao.todayBudget(month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay())?.summary(),
                renewals, dao.todayLibrary())
        }
    }
