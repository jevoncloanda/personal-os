package com.jevon.personalos.money

import com.jevon.personalos.data.*
import java.math.BigInteger

data class CurrencyBalance(val code: String, val minor: BigInteger, val count: Int)
fun activeCurrencyBalances(accounts: List<AccountBalance>): List<CurrencyBalance> = accounts.filter { !it.account.archived }
    .groupBy { it.account.currencyCode }.toSortedMap().map { (code, values) ->
        CurrencyBalance(code, values.fold(BigInteger.ZERO) { total, value -> total.add(BigInteger.valueOf(value.balance)) }, values.size)
    }

data class SpendingSlice(val label: String, val amount: Long, val total: Long) {
    val fraction: Float get() = if (total == 0L) 0f else (amount.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
}
fun spendingSlices(values: List<CategorySpending>, categories: List<CategoryEntity>, currency: String): List<SpendingSlice> {
    val selected = values.filter { it.currencyCode == currency && it.amount > 0 }.sortedWith(compareByDescending<CategorySpending> { it.amount }.thenBy { it.categoryId })
    val total = selected.fold(0L) { sum, row -> Math.addExact(sum, row.amount) }
    val slices = selected.take(4).map { row -> SpendingSlice(categories.firstOrNull { it.id == row.categoryId }?.name ?: "Uncategorized", row.amount, total) }
    val rest = selected.drop(4).fold(0L) { sum, row -> Math.addExact(sum, row.amount) }
    return slices + if (rest > 0) listOf(SpendingSlice("Other categories", rest, total)) else emptyList()
}
