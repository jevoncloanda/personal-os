package com.jevon.personalos.today

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MoneyInputTest {
    @Test fun exactDecimalCurrencies() {
        assertEquals(12345L, parseMoney("123.45", "USD"))
        assertEquals(12345L, parseMoney("123.45", "IDR"))
        assertEquals(123L, parseMoney("123", "JPY"))
        assertEquals(1234L, parseMoney("1.234", "KWD"))
        assertEquals(-125L, parseMoney("-1.25", "USD", true))
        assertEquals(0L, parseMoney("0", "IDR", true))
    }
    @Test fun rejectsAmbiguousAndInvalidInput() {
        listOf("", "0", "-1", "1,000", "1e3", "NaN", " 1 ", ".5", "1.001").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { parseMoney(value, "USD") }
        }
        assertThrows(IllegalArgumentException::class.java) { parseMoney("1.5", "JPY") }
        assertThrows(IllegalArgumentException::class.java) { parseMoney("1", "ZZZ") }
    }
    @Test fun longBoundariesRoundTrip() {
        assertEquals(Long.MAX_VALUE, parseMoney("92233720368547758.07", "USD"))
        assertEquals(Long.MIN_VALUE, parseMoney("-92233720368547758.08", "USD", true))
        assertThrows(IllegalArgumentException::class.java) { parseMoney("92233720368547758.08", "USD") }
        assertEquals("-92233720368547758.08", moneyInput(Long.MIN_VALUE, "USD"))
    }
    @Test fun balanceUsesExactSignedArithmetic() {
        assertEquals(-50L, AccountBalance(AccountEntity("a", "Bank", "USD", -100), 100, 50).balance)
        assertEquals(Long.MAX_VALUE, AccountBalance(AccountEntity("a", "Bank", "USD", Long.MAX_VALUE), 100, 100).balance)
        assertThrows(ArithmeticException::class.java) { AccountBalance(AccountEntity("a", "Bank", "USD", Long.MAX_VALUE), 1, 0).balance }
    }
    @Test fun formattingRetainsCurrencyPrecision() {
        assertTrue(formatMoney(12345, "USD", Locale.US).contains("123.45"))
        assertTrue(formatMoney(1234, "KWD", Locale.US).contains("1.234"))
        assertTrue(formatMoney(12345, "IDR", Locale.US).contains("123.45"))
    }
}
