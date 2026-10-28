package com.jevon.personalos.today

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class TodayModelTest {
    private val day = LocalDate.of(2026, 10, 22)
    private val now = day.atTime(9, 0).atZone(ZoneId.of("Asia/Jakarta"))
    private val budget = BudgetEntity("b", "Monthly", "USD", 10_000, day.withDayOfMonth(1).toEpochDay(), day.withDayOfMonth(1).plusMonths(1).toEpochDay())
    private fun expense(id: String, amount: Long = 500, date: Long = day.toEpochDay(), currency: String = "USD", category: String? = null) =
        EntryEntity(id, EntryKind.EXPENSE, amount, currency, date, fromAccountId = "bank", categoryId = category)

    @Test fun `budget includes only matching expenses inside half open period`() {
        val entries = listOf(expense("a"), expense("b", date = budget.startEpochDay),
            expense("c", date = budget.endExclusiveEpochDay), expense("d", date = budget.startEpochDay - 1), expense("e", currency = "IDR"),
            EntryEntity("t", EntryKind.TRANSFER, 2000, "USD", day.toEpochDay(), "bank", "cash"),
            EntryEntity("i", EntryKind.INCOME, 2000, "USD", day.toEpochDay(), toAccountId = "bank"))
        assertEquals(1000, summarizeBudget(budget, entries).spentMinor)
        assertEquals(9000, summarizeBudget(budget, entries).remainingMinor)
    }
    @Test fun `category budget includes only its category`() {
        assertEquals(500, summarizeBudget(budget.copy(categoryId = "food"), listOf(expense("a", category = "food"), expense("b", category = "travel"))).spentMinor)
    }
    @Test fun `overspending remains visible and progress is bounded`() {
        val result = summarizeBudget(budget, listOf(expense("a", 12_000)))
        assertEquals(-2000, result.remainingMinor)
        assertEquals(1f, result.fraction)
    }
    @Test fun `budget overflow fails rather than wrapping`() {
        assertThrows(ArithmeticException::class.java) { summarizeBudget(budget, listOf(expense("a", Long.MAX_VALUE), expense("b"))) }
    }
    @Test fun `transfer requires distinct accounts and no expense category`() {
        assertThrows(IllegalArgumentException::class.java) { EntryEntity("t", EntryKind.TRANSFER, 100, "USD", 0, "bank", "bank") }
        assertThrows(IllegalArgumentException::class.java) { EntryEntity("t", EntryKind.TRANSFER, 100, "USD", 0, "bank", "cash", "food") }
        assertThrows(IllegalArgumentException::class.java) { expense("bad", 0) }
    }
    @Test fun `monthly recurrence retains original anchor across short months`() {
        val anchor = LocalDate.of(2027, 1, 31)
        assertEquals(LocalDate.of(2027, 2, 28), occurrenceDate(anchor, Frequency.MONTHLY, 1, 1))
        assertEquals(LocalDate.of(2027, 3, 31), occurrenceDate(anchor, Frequency.MONTHLY, 1, 2))
        assertEquals(LocalDate.of(2028, 2, 29), occurrenceDate(LocalDate.of(2024, 2, 29), Frequency.YEARLY, 1, 4))
    }
    @Test fun `today filters events reminders payments and active library`() {
        fun millis(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(now.zone).toInstant().toEpochMilli()
        val source = Snapshot(
            events = listOf(EventEntity("ended", "Past", millis(day, 7), millis(day, 8), now.zone.id),
                EventEntity("live", "Ongoing", millis(day, 8), millis(day, 10), now.zone.id),
                EventEntity("next", "Tomorrow", millis(day.plusDays(1), 8), millis(day.plusDays(1), 9), now.zone.id)),
            reminders = listOf(ReminderEntity("due", "Overdue", millis(day.minusDays(1), 8)), ReminderEntity("done", "Done", null, 1), ReminderEntity("future", "Future", millis(day.plusDays(1), 9))),
            subscriptions = listOf(SubscriptionEntity("soon", "Soon", 100, "USD", day.plusDays(7).toEpochDay(), recurrence = Recurrence(Frequency.MONTHLY)),
                SubscriptionEntity("later", "Later", 100, "USD", day.plusDays(8).toEpochDay(), recurrence = Recurrence(Frequency.MONTHLY))),
        ).forToday(now)
        assertEquals(listOf("live"), source.events.map { it.id })
        assertEquals(listOf("due"), source.reminders.map { it.id })
        assertEquals(listOf("soon"), source.subscriptions.map { it.id })
    }
    @Test fun `local date respects timezone at UTC date boundary`() {
        val content = Snapshot().forToday(ZonedDateTime.parse("2026-10-21T18:30:00Z").withZoneSameInstant(ZoneId.of("Asia/Jakarta")))
        assertEquals(day, content.date)
    }
    @Test fun `currency formats exact minor units for zero two and three decimal currencies`() {
        assertEquals("$12.34", formatMoney(1234, "USD", Locale.US))
        assertTrue(formatMoney(1234, "JPY", Locale.US).contains("1,234"))
        assertTrue(formatMoney(1234, "KWD", Locale.US).contains("1.234"))
        assertTrue(formatMoney(166_000_000, "IDR", Locale.US).contains("1,660,000"))
    }
    @Test fun `empty snapshot has no fabricated summaries`() {
        val content = Snapshot().forToday(now)
        assertNull(content.budget)
        assertTrue(content.events.isEmpty() && content.reminders.isEmpty() && content.subscriptions.isEmpty() && content.library.isEmpty())
    }
}
