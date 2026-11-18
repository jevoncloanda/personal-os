package com.jevon.personalos.widget

import com.jevon.personalos.data.EventEntity
import com.jevon.personalos.data.Frequency
import com.jevon.personalos.data.Recurrence
import com.jevon.personalos.data.ReminderEntity
import com.jevon.personalos.data.SubscriptionEntity
import com.jevon.personalos.schedule.EventOccurrence
import com.jevon.personalos.schedule.ReminderOccurrence
import com.jevon.personalos.schedule.RenewalForecast
import com.jevon.personalos.subscriptions.upcomingRenewals
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class DashboardRenewalTest {
    private val now = ZonedDateTime.parse("2026-11-18T10:00:00+07:00[Asia/Bangkok]")
    private val today = now.toLocalDate()
    private val day = today.toEpochDay()

    private fun subscription(id: String, currency: String = "USD") = SubscriptionEntity(
        id = id,
        name = "Plan $id",
        amountMinor = 1200,
        currencyCode = currency,
        nextDueEpochDay = day,
        recurrence = Recurrence(Frequency.MONTHLY, 1, day),
    )

    @Test
    fun todaySelectsNextActiveRenewalWithinInclusiveSevenDaysWithStableTies() {
        val plan = subscription("a")
        val forecasts = listOf(
            RenewalForecast(plan, day - 1),
            RenewalForecast(plan.copy(active = false), day),
            RenewalForecast(plan, day + 8),
            RenewalForecast(subscription("b"), day + 7),
            RenewalForecast(plan, day + 7),
        )
        assertEquals(RenewalForecast(plan, day + 7), dashboardRenewal(forecasts, today))
        assertEquals(RenewalForecast(plan, day),
            dashboardRenewal(forecasts + RenewalForecast(plan, day), today))
        assertNull(dashboardRenewal(forecasts.take(3), today))
    }

    @Test
    fun scheduleMergesChronologicallyWithStableTiesAndSeparateCurrencies() {
        val eventStart = now.plusHours(1).toInstant().toEpochMilli()
        val event = EventEntity("event", "Meeting", eventStart, eventStart + 3_600_000,
            now.zone.id)
        val reminder = ReminderEntity("reminder", "Read", eventStart)
        val forecasts = listOf(
            RenewalForecast(subscription("b", "EUR"), day + 1),
            RenewalForecast(subscription("a"), day + 1),
            RenewalForecast(subscription("due"), day),
        )
        val items = dashboardItems(
            listOf(EventOccurrence(event, day, eventStart, eventStart + 3_600_000)),
            listOf(ReminderOccurrence(reminder, day, eventStart, false)),
            now,
            forecasts,
        )
        assertEquals(listOf("due", "event", "reminder", "a", "b"),
            items.map { it.target.id })
        assertTrue(items[3].amount!!.startsWith("USD · "))
        assertTrue(items[4].amount!!.startsWith("EUR · "))
        assertTrue(items[3].detail.contains("Subscription · Tomorrow · Forecast"))
        assertEquals(day + 1, items[3].target.day)
        assertEquals(today.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli(),
            items[3].sortAt)
    }

    @Test
    fun scheduleKeepsThirtyDayWindowAndDeduplicatesRenewalOccurrences() {
        val plan = subscription("plan")
        val forecasts = listOf(-1L, 0L, 0L, 29L, 30L).map { RenewalForecast(plan, day + it) } +
            RenewalForecast(plan.copy(id = "inactive", active = false), day)
        val items = dashboardItems(emptyList(), emptyList(), now, forecasts)
        assertEquals(listOf(day, day + 29), items.map { it.target.day })
    }

    @Test
    fun renewalSelectionUsesForecastDayAndSkipsSettledMonthEndInstallments() {
        val anchor = LocalDate.parse("2024-01-31").toEpochDay()
        val leapDay = LocalDate.parse("2024-02-29").toEpochDay()
        val date = LocalDate.parse("2024-02-28")
        val plan = subscription("month-end").copy(
            nextDueEpochDay = anchor,
            recurrence = Recurrence(Frequency.MONTHLY, 1, anchor),
        )
        val forecasts = upcomingRenewals(plan, setOf(anchor), date.toEpochDay(),
            date.plusDays(30).toEpochDay()).map { RenewalForecast(plan, it) }
        val selected = requireNotNull(dashboardRenewal(forecasts, date))
        assertEquals(leapDay, selected.day)
        val items = dashboardItems(emptyList(), emptyList(), date.atTime(10, 0).atZone(now.zone),
            forecasts)
        assertEquals(leapDay, items.single().target.day)
        val settled = upcomingRenewals(plan, setOf(anchor, leapDay), date.toEpochDay(),
            date.plusDays(7).toEpochDay()).map { RenewalForecast(plan, it) }
        assertNull(dashboardRenewal(settled, date))
        assertTrue(upcomingRenewals(plan.copy(active = false), emptySet(), date.toEpochDay(),
            date.plusDays(7).toEpochDay()).isEmpty())
    }
}
