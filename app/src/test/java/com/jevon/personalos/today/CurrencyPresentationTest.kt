package com.jevon.personalos.today

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CurrencyPresentationTest {
    @Test fun rupiahDefaultsDoNotDependOnDeviceLanguage() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Rp25.000", formatMoney(2_500_000, "IDR"))
            assertEquals("Rp1.500.000", formatMoney(150_000_000, "IDR"))
        } finally { Locale.setDefault(original) }
    }
    @Test fun rupiahKeepsMinorUnitsAndNegativeBalances() {
        assertEquals("Rp25.000,50", formatMoney(2_500_050, "IDR"))
        assertEquals("-Rp25.000", formatMoney(-2_500_000, "IDR"))
        assertEquals("Rp0", formatMoney(0, "IDR"))
        assertEquals("Rp0,01", formatMoney(1, "IDR"))
        assertEquals(2_500_050L, parseMoney("25000.50", "IDR"))
    }
    @Test fun dollarsStayDollarsAndLocaleOverridesAreExplicit() {
        assertEquals("$25,000", formatMoney(2_500_000, "USD"))
        assertEquals("$12.34", formatMoney(1234, "USD"))
        assertFalse(formatMoney(150_000_000, "USD").contains("Rp"))
        assertTrue(formatMoney(150_000_000, "IDR", Locale.US).contains("1,500,000"))
    }
    @Test fun newEntryDefaultsNeverRewriteAnExistingCurrency() {
        assertEquals("IDR", DEFAULT_CURRENCY)
        val existing = AccountEntity("usd", "Existing dollars", "USD", 10000)
        assertEquals("USD", existing.copy(name = "Renamed dollars").currencyCode)
        assertEquals(10000L, existing.openingBalanceMinor)
        assertThrows(IllegalArgumentException::class.java) { EntryEntity("mixed", EntryKind.TRANSFER, 100, "IDR", 0, "usd", "usd") }
    }
}
