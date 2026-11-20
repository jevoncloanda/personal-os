package com.jevon.personalos.widget

import com.jevon.personalos.data.BudgetEntity
import com.jevon.personalos.data.BudgetProjection
import com.jevon.personalos.data.EventEntity
import com.jevon.personalos.data.ReminderEntity
import com.jevon.personalos.data.Settings
import com.jevon.personalos.schedule.EventOccurrence
import com.jevon.personalos.schedule.ReminderOccurrence
import com.jevon.personalos.today.BudgetSummary
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class DashboardDataTest {
    private val now = ZonedDateTime.parse("2026-11-18T10:00:00+07:00[Asia/Bangkok]")
    private val day = now.toLocalDate().toEpochDay()

    @Test
    fun selectedTodayBudgetWinsAndFallbackNeverCombinesCurrenciesOrScopes() {
        val selected = BudgetSummary(BudgetEntity("chosen", "USD overall", "USD", 10000,
            day, day + 30), 12000)
        val category = BudgetProjection(BudgetEntity("category", "Food", "IDR", 50000,
            day, day + 30, "food"), 10000)
        assertEquals(selected, dashboardBudget(selected, listOf(category), Settings()))
        assertEquals(-2000L, selected.remainingMinor)
        assertEquals(category.summary(), dashboardBudget(null, listOf(category), Settings()))
        assertNull(dashboardBudget(null, listOf(category), Settings(preferredCurrency = "EUR")))
    }

    @Test
    fun upcomingUsesOccurrenceTimesAndOmitsCompletedRemindersAndEndedEvents() {
        val start = now.plusHours(1).toInstant().toEpochMilli()
        val template = EventEntity("series", "Weekly event", start - 7 * 86_400_000,
            start - 7 * 86_400_000 + 3_600_000, "Asia/Bangkok")
        val future = EventOccurrence(template, day, start, start + 3_600_000)
        val ended = future.copy(event = template.copy(id = "ended"), end = start - 7_200_000)
        val reminder = ReminderEntity("reminder", "Read", start)
        val items = dashboardItems(listOf(ended, future), listOf(
            ReminderOccurrence(reminder, day, start, true),
            ReminderOccurrence(ReminderEntity("undated", "Anytime", null), null, null, false),
        ), now)
        assertEquals(listOf("series", "undated"), items.map { it.target.id })
        assertEquals(day, items.first().target.day)
        assertTrue(items.first().detail.contains("Today"))
        assertTrue(items.last().detail.contains("No due date"))
    }

    @Test
    fun dateOnlyReminderRetainsItsCalendarDayAcrossZonesAndDuplicateInputs() {
        val reminder = ReminderEntity("dated", "Pay", now.toInstant().toEpochMilli(),
            zoneId = "America/New_York", dateOnly = true)
        val occurrence = ReminderOccurrence(reminder, day, now.toLocalDate()
            .atStartOfDay(now.zone).toInstant().toEpochMilli(), false)
        val items = dashboardItems(emptyList(), listOf(occurrence, occurrence), now)
        assertEquals(1, items.size)
        assertTrue(items.single().detail.contains("Today · All day"))
        assertFalse(items.single().detail.contains("Overdue"))
    }

    @Test
    fun todayShowsFutureEventsOnlyAsUpNextWhenThereIsNoDatedCommitmentToday() {
        val futureStart = now.plusDays(2).toInstant().toEpochMilli()
        val currentStart = now.minusDays(1).toInstant().toEpochMilli()
        val future = EventOccurrence(
            EventEntity("future", "Friday event", futureStart, futureStart + 3_600_000, now.zone.id),
            day + 2, futureStart, futureStart + 3_600_000,
        )
        val ongoing = EventOccurrence(
            EventEntity("ongoing", "Overnight", currentStart, futureStart, now.zone.id),
            day - 1, currentStart, futureStart,
        )
        val overdue = ReminderOccurrence(
            ReminderEntity("overdue", "Overdue", currentStart), day - 1, currentStart, false,
        )
        val undated = ReminderOccurrence(ReminderEntity("anytime", "Anytime", null), null, null, false)
        val due = ReminderOccurrence(
            ReminderEntity("due", "Due today", now.toInstant().toEpochMilli()),
            day, now.toInstant().toEpochMilli(), false,
        )
        val empty = todayAgenda(dashboardItems(listOf(future), listOf(overdue, undated), now),
            now.toLocalDate())
        assertTrue(empty.dueToday.isEmpty())
        assertEquals("future", empty.upNext?.target?.id)
        assertEquals(now.toLocalDate().plusDays(2), empty.upNext?.date)

        val populated = todayAgenda(
            dashboardItems(listOf(future, ongoing), listOf(overdue, due, undated), now),
            now.toLocalDate(),
        )
        assertEquals(listOf("ongoing", "due"), populated.dueToday.map { it.target.id })
        assertNull(populated.upNext)
    }

    @Test
    fun agendaScrollsWhenTheWidgetHasRoomAtTheCurrentFontScale() {
        assertTrue(dashboardLayout(201f, 103f, 1f).short)
        assertFalse(dashboardLayout(140f, 110f, 1f).scrollAgenda)
        assertTrue(dashboardLayout(201f, 234f, 1f).scrollAgenda)
        assertTrue(dashboardLayout(429f, 234f, 1f).scrollAgenda)
        assertTrue(dashboardLayout(315f, 495f, 1f).scrollAgenda)
        assertFalse(dashboardLayout(429f, 234f, 1.5f).scrollAgenda)
        assertFalse(dashboardLayout(201f, 160f, 1f).showSecondary)
        assertTrue(dashboardLayout(140f, 110f, 1f).short)
    }
}
