package com.jevon.personalos.data

import java.time.LocalDate
import java.time.YearMonth

internal data class BudgetPeriod(val startEpochDay: Long, val endExclusiveEpochDay: Long) {
    val startMonth: YearMonth get() = YearMonth.from(LocalDate.ofEpochDay(startEpochDay))

    companion object {
        fun forMonth(month: YearMonth) = BudgetPeriod(
            startEpochDay = month.atDay(1).toEpochDay(),
            endExclusiveEpochDay = month.plusMonths(1).atDay(1).toEpochDay(),
        )
    }
}

internal val BudgetEntity.period: BudgetPeriod
    get() = BudgetPeriod(startEpochDay, endExclusiveEpochDay)

internal fun initialBudgetMonth(
    existing: BudgetPeriod?,
    viewedMonth: YearMonth?,
    currentMonth: YearMonth = YearMonth.now(),
): YearMonth = existing?.startMonth ?: viewedMonth ?: currentMonth

internal fun editedBudgetPeriod(month: YearMonth, existing: BudgetPeriod?): BudgetPeriod =
    existing?.takeIf { it.startMonth == month } ?: BudgetPeriod.forMonth(month)
