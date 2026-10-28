package com.jevon.personalos.today

import com.jevon.personalos.data.*
import com.jevon.personalos.money.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class MoneyVisualizationTest {
    @Test fun balanceTotalsKeepCurrenciesSeparateAndExcludeArchived() {
        val values = listOf(AccountBalance(AccountEntity("a", "Cash", "IDR", 10000),0,0),
            AccountBalance(AccountEntity("b", "Bank", "USD", 500),0,0),
            AccountBalance(AccountEntity("c", "Old", "IDR", 90000, true),0,0),
            AccountBalance(AccountEntity("d", "Debt", "IDR", -1000),0,0))
        assertEquals(BigInteger.valueOf(9000), activeCurrencyBalances(values).first { it.code == "IDR" }.minor)
        assertEquals(2, activeCurrencyBalances(values).first { it.code == "IDR" }.count)
        assertEquals(BigInteger.valueOf(500), activeCurrencyBalances(values).first { it.code == "USD" }.minor)
    }
    @Test fun presentationSumCanExceedOneStoredLongWithoutWrapping() {
        val values = (1..2).map { AccountBalance(AccountEntity("$it", "$it", "IDR", Long.MAX_VALUE),0,0) }
        val sum = activeCurrencyBalances(values).single().minor
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), sum)
        assertTrue(formatMoney(sum, "IDR").startsWith("Rp"))
    }
    @Test fun categorySharesUseAllActualSpendingAndFoldTheTail() {
        val values = (1..6).map { CategorySpending("$it", "IDR", 100) } + CategorySpending("dollars", "USD", 10000)
        val slices = spendingSlices(values, emptyList(), "IDR")
        assertEquals(5, slices.size)
        assertEquals(600L, slices.sumOf { it.amount })
        assertEquals(200L, slices.last().amount)
        assertEquals("Other categories", slices.last().label)
        assertEquals(1f / 3f, slices.last().fraction)
    }
    @Test fun emptyAndZeroDataDoNotInventAChart() {
        assertTrue(spendingSlices(emptyList(), emptyList(), "IDR").isEmpty())
        assertTrue(spendingSlices(listOf(CategorySpending(null,"IDR",0)), emptyList(), "IDR").isEmpty())
        assertEquals(0f, SpendingSlice("Empty",0,0).fraction)
        assertTrue(activeCurrencyBalances(emptyList()).isEmpty())
    }
}
