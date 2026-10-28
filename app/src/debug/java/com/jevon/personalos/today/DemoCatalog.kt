package com.jevon.personalos.today

import com.jevon.personalos.data.*
import java.time.LocalDate
import java.time.ZoneId

// Debug source set only. This catalog has no DAO and never writes to disk.
object DemoCatalog {
    const val available = true
    fun snapshot(date: LocalDate, zone: ZoneId): Snapshot {
        fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val first = date.withDayOfMonth(1)
        return Snapshot(
            events = listOf(
                EventEntity("demo-event-1", "Design catch-up", at(10), at(10, 45), zone.id, location = "Studio · 45 min"),
                EventEntity("demo-event-2", "Evening walk", at(18), at(18, 30), zone.id, location = "Neighborhood · 30 min"),
            ),
            reminders = listOf(
                ReminderEntity("demo-reminder-1", "Book a dentist appointment", at(17)),
                ReminderEntity("demo-reminder-2", "Pick up groceries", at(19)),
            ),
            budgets = listOf(BudgetEntity("demo-budget", "Monthly spending", "IDR", 450_000_000, first.toEpochDay(), first.plusMonths(1).toEpochDay())),
            entries = listOf(
                EntryEntity("demo-expense", EntryKind.EXPENSE, 284_000_000, "IDR", date.toEpochDay(), fromAccountId = "demo-bank"),
                EntryEntity("demo-transfer", EntryKind.TRANSFER, 50_000_000, "IDR", date.toEpochDay(), "demo-bank", "demo-cash"),
            ),
            subscriptions = listOf(
                SubscriptionEntity("demo-sub-1", "Spotify Premium", 5_499_000, "IDR", date.plusDays(1).toEpochDay(), recurrence = Recurrence(Frequency.MONTHLY, anchorEpochDay = date.toEpochDay())),
                SubscriptionEntity("demo-sub-2", "Google One", 2_690_000, "IDR", date.plusDays(4).toEpochDay(), recurrence = Recurrence(Frequency.MONTHLY, anchorEpochDay = date.toEpochDay())),
            ),
            library = listOf(
                LibraryItemEntity("demo-series", "Severance", LibraryKind.SERIES, LibraryStatus.ACTIVE, 4, 9, at(8)),
                LibraryItemEntity("demo-game", "Hollow Knight", LibraryKind.GAME, LibraryStatus.ACTIVE, 12, null, at(7)),
            ),
        )
    }
}
