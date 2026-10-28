package com.jevon.personalos.backup

import com.jevon.personalos.data.*
import java.time.ZonedDateTime
import java.time.YearMonth

/** Used only by isolated backup tests/review host. Never inserted into the personal database. */
internal fun backupReviewData(now: ZonedDateTime = ZonedDateTime.now()): BackupData {
    val date = now.toLocalDate().toEpochDay()
    val month = YearMonth.from(now)
    val instant = now.toInstant().toEpochMilli()
    val recurrence = Recurrence(Frequency.MONTHLY, 1, date, date + 365)
    return BackupData(
        accounts = listOf(AccountEntity("bank", "Backup review bank", "IDR", 10_000_000, type = "BANK", notes = "Exact minor units · café 🌤"),
            AccountEntity("cash", "Archived cash", "IDR", 50_000, archived = true, type = "CASH"),
            AccountEntity("usd", "Dollar account", "USD", -12_345, type = "E_WALLET")),
        categories = listOf(CategoryEntity("food", "Food", "EXPENSE", archived = true), CategoryEntity("salary", "Salary", "INCOME"), CategoryEntity("legacy", "Legacy", null)),
        entries = listOf(EntryEntity("expense", EntryKind.EXPENSE, 125_500, "IDR", date, "bank", categoryId = "food", note = "Lunch\nreceipt", minuteOfDay = 745),
            EntryEntity("income", EntryKind.INCOME, 3_000_000, "IDR", date, toAccountId = "bank", categoryId = "salary", minuteOfDay = 540),
            EntryEntity("transfer", EntryKind.TRANSFER, 200_000, "IDR", date, "bank", "cash", minuteOfDay = 900),
            EntryEntity("usd-expense", EntryKind.EXPENSE, 100, "USD", date, "usd", categoryId = "legacy", minuteOfDay = 1439)),
        budgets = listOf(BudgetEntity("overall", "Monthly plan", "IDR", 5_000_000, month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay()),
            BudgetEntity("food-budget", "Food plan", "IDR", 100_000, month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay(), "food"),
            BudgetEntity("legacy-budget", "Legacy interval", "USD", 1000, date - 2, date + 4)),
        events = listOf(EventEntity("event", "Review appointment", instant + 3_600_000, instant + 7_200_000, now.zone.id, location = "Studio", recurrence = recurrence)),
        reminders = listOf(ReminderEntity("pending", "Review reminder", instant + 1000), ReminderEntity("complete", "Completed reminder", instant - 2000, instant - 1000, recurrence), ReminderEntity("undated", "Undated reminder", null)),
        subscriptions = listOf(SubscriptionEntity("subscription", "Review renewal", 50_000, "IDR", date, recurrence = recurrence), SubscriptionEntity("inactive", "Inactive renewal", 100, "USD", date - 30, false, recurrence)),
        payments = listOf(SubscriptionPaymentEntity("payment", "subscription", date, "expense")),
        library = listOf(LibraryItemEntity("book", "Review book", LibraryKind.BOOK, LibraryStatus.ACTIVE, 42, 200, instant),
            LibraryItemEntity("film", "Review film", LibraryKind.MOVIE, LibraryStatus.COMPLETED, 120, 120, instant - 1),
            LibraryItemEntity("game", "Review game", LibraryKind.GAME, LibraryStatus.DROPPED, 4, null, instant - 2),
            LibraryItemEntity("series", "Review series", LibraryKind.SERIES, LibraryStatus.PLANNED, 0, 10, instant - 3)),
    )
}
