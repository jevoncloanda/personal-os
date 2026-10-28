package com.jevon.personalos.subscriptions

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class SubscriptionModelTest {
    private fun day(s: String) = LocalDate.parse(s).toEpochDay()
    private fun plan(date: String = "2024-01-31", frequency: Frequency = Frequency.MONTHLY, interval: Int = 1, amount: Long = 1200, code: String = "USD") =
        SubscriptionEntity("plan", "Plan", amount, code, day(date), recurrence = Recurrence(frequency, interval, day(date)))
    @Test fun monthEndsReturnToOriginalDay() {
        for (d in listOf("29", "30", "31")) {
            val s = plan("2023-01-$d")
            assertEquals(day("2023-02-28"), renewalOnOrAfter(s, day("2023-02-01")))
            assertEquals(day("2023-03-$d"), renewalOnOrAfter(s, day("2023-03-01")))
        }
        assertEquals(day("2024-02-29"), renewalOnOrAfter(plan(), day("2024-02-01")))
    }
    @Test fun leapAnchorReturnsAfterNonLeapYears() {
        val s = plan("2024-02-29", Frequency.YEARLY)
        assertEquals(day("2025-02-28"), renewalOnOrAfter(s, day("2025-01-01")))
        assertEquals(day("2028-02-29"), renewalOnOrAfter(s, day("2028-01-01")))
    }
    @Test fun customIntervalsSeekWithoutIteratingMissedCycles() {
        val s = plan("2000-01-31", interval = 3)
        assertEquals(day("2026-10-31"), renewalOnOrAfter(s, day("2026-10-01")))
        assertEquals(day("2026-10-28"), renewalOnOrAfter(plan("2000-01-01", Frequency.DAILY), day("2026-10-28")))
        assertEquals(day("2024-01-15"), renewalOnOrAfter(plan("2024-01-01", Frequency.WEEKLY, 2), day("2024-01-03")))
    }
    @Test fun paidSkippedAndInactiveAndLimitsRespected() {
        val s = plan()
        val dates = upcomingRenewals(s, setOf(day("2024-02-29")), day("2024-02-01"), day("2024-12-31"), 2)
        assertEquals(listOf(day("2024-03-31"), day("2024-04-30")), dates)
        assertEquals(day("2024-01-31"), nextUnpaid(s, setOf(day("2024-02-29"))))
        assertTrue(upcomingRenewals(s.copy(active = false), emptySet(), 0, Long.MAX_VALUE).isEmpty())
        assertNull(renewalOnOrAfter(s.copy(recurrence = s.recurrence.copy(untilEpochDay = day("2024-02-29"))), day("2024-03-01")))
    }
    @Test fun singleOccurrenceAndUpperDateBoundaryAreBounded() {
        val s = plan().copy(recurrence = Recurrence(null))
        assertEquals(s.nextDueEpochDay, nextUnpaid(s, emptySet()))
        assertNull(nextUnpaid(s, setOf(s.nextDueEpochDay)))
        assertNull(renewalOnOrAfter(plan("9999-12-31"), LocalDate.parse("+10000-01-01").toEpochDay()))
    }
    @Test fun equivalentsSumExactlyBeforeHalfUpAndSeparateCurrencies() {
        val costs = recurringCosts(listOf(plan(frequency = Frequency.YEARLY, amount = 1), plan(frequency = Frequency.YEARLY, amount = 5), plan(amount = 10000, code = "IDR"), plan().copy(active = false)))
        assertEquals(BigInteger.ONE, costs.first { it.currency == "USD" }.monthlyMinor)
        assertEquals(BigInteger.valueOf(6), costs.first { it.currency == "USD" }.annualMinor)
        assertEquals(BigInteger.valueOf(120000), costs.first { it.currency == "IDR" }.annualMinor)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.valueOf(12), recurringCosts(listOf(plan(amount = Long.MAX_VALUE))).single().annualMinor)
    }
    @Test fun dailyWeeklyAndMultiYearCostPolicies() {
        assertEquals(BigInteger.valueOf(36524), recurringCosts(listOf(plan(frequency = Frequency.DAILY, amount = 100))).single().annualMinor)
        assertEquals(BigInteger.valueOf(5218), recurringCosts(listOf(plan(frequency = Frequency.WEEKLY, amount = 100))).single().annualMinor)
        assertEquals(BigInteger.valueOf(600), recurringCosts(listOf(plan(frequency = Frequency.YEARLY, interval = 2))).single().annualMinor)
    }
}
