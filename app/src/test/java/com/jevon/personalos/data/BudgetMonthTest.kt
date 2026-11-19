package com.jevon.personalos.data

import com.jevon.personalos.today.Snapshot
import com.jevon.personalos.today.forToday
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetMonthTest {
    private val november = YearMonth.of(2026, 11)
    private val december = november.plusMonths(1)
    private val january = december.plusMonths(1)

    @Test
    fun defaultsToViewedOrCurrentMonthAndOpensAnEditInItsSavedMonth() {
        assertEquals(november, initialBudgetMonth(null, null, november))
        assertEquals(december, initialBudgetMonth(null, december, november))
        assertEquals(january, initialBudgetMonth(BudgetPeriod.forMonth(january), december, november))
    }

    @Test
    fun explicitMonthChangeUsesCalendarBoundsAndPreservesAnUnchangedLegacyRange() {
        val legacy = BudgetPeriod(november.atDay(12).toEpochDay(), december.atDay(4).toEpochDay())
        assertEquals(legacy, editedBudgetPeriod(november, legacy))
        assertEquals(
            BudgetPeriod(december.atDay(1).toEpochDay(), january.atDay(1).toEpochDay()),
            editedBudgetPeriod(december, legacy),
        )
        assertEquals(
            LocalDate.of(2028, 3, 1).toEpochDay(),
            BudgetPeriod.forMonth(YearMonth.of(2028, 2)).endExclusiveEpochDay,
        )
    }

    @Test
    fun futureBudgetSelectionFallsBackUntilItsMonthAndOnlyExpensesInThatMonthCount() {
        val current = budget("november", november)
        val future = budget("december", december)
        val source = Snapshot(
            budgets = listOf(future, current),
            entries = listOf(
                moneyEntry("allowance", EntryKind.INCOME, november.atDay(15), 50_000),
                moneyEntry("november expense", EntryKind.EXPENSE, november.atEndOfMonth(), 1_000),
                moneyEntry("december expense", EntryKind.EXPENSE, december.atDay(1), 2_000),
            ),
        )
        val prefs = Settings(todayBudgetId = future.id)
        val novemberToday = source.forToday(november.atEndOfMonth().atStartOfDay(zone), prefs)
        assertEquals(listOf(current), novemberToday.eligibleBudgets)
        assertEquals(current, novemberToday.budget?.budget)
        assertEquals(1_000L, novemberToday.budget?.spentMinor)

        val decemberToday = source.forToday(december.atDay(1).atStartOfDay(zone), prefs)
        assertEquals(future, decemberToday.budget?.budget)
        assertEquals(2_000L, decemberToday.budget?.spentMinor)
        assertNull(source.forToday(january.atDay(1).atStartOfDay(zone), prefs).budget)
    }

    private val zone = ZoneId.of("Asia/Bangkok")

    private fun budget(id: String, month: YearMonth): BudgetEntity {
        val (start, end) = BudgetPeriod.forMonth(month)
        return BudgetEntity(id, id, DEFAULT_CURRENCY, 10_000, start, end)
    }

    private fun moneyEntry(id: String, kind: EntryKind, date: LocalDate, amount: Long) = EntryEntity(
        id = id,
        kind = kind,
        occurredOnEpochDay = date.toEpochDay(),
        minuteOfDay = 0,
        amountMinor = amount,
        currencyCode = DEFAULT_CURRENCY,
        fromAccountId = if (kind == EntryKind.EXPENSE) "cash" else null,
        toAccountId = if (kind == EntryKind.INCOME) "cash" else null,
        categoryId = if (kind == EntryKind.INCOME) "income" else "food",
    )
}
