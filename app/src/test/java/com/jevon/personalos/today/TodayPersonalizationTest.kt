package com.jevon.personalos.today

import com.jevon.personalos.data.*
import com.jevon.personalos.money.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TodayPersonalizationTest {
    private val now = ZonedDateTime.parse("2026-11-04T09:00:00+07:00[Asia/Jakarta]")
    private val start = LocalDate.of(2026, 11, 1).toEpochDay()
    private val end = LocalDate.of(2026, 12, 1).toEpochDay()
    private fun budget(id: String, code: String = "IDR") = BudgetEntity(id, id, code, 8000, start, end)
    private fun project(budgets: List<BudgetEntity>, settings: Settings = Settings()) = Snapshot(budgets = budgets).forToday(now, settings)

    @Test fun `IDR default cannot arbitrarily select AUD`() {
        assertEquals("IDR", Settings().preferredCurrency)
        assertNull(project(listOf(budget("first", "AUD"))).budget)
        assertEquals("rupiah", project(listOf(budget("aud", "AUD"), budget("rupiah"))).budget!!.budget.id)
    }
    @Test fun `explicit other currency survives preference and catalog changes`() {
        val prefs = Settings(todayBudgetId = "aud")
        assertEquals("aud", project(listOf(budget("aud", "AUD"), budget("z")), prefs).budget!!.budget.id)
        assertEquals("aud", project(listOf(budget("a"), budget("aud", "AUD"), budget("z")), prefs.copy(preferredCurrency = "USD")).budget!!.budget.id)
    }
    @Test fun `automatic multiple same currency choices are deterministic`() {
        val budgets = listOf(budget("z"), budget("a"), budget("aud", "AUD"))
        assertEquals("a", project(budgets).budget!!.budget.id)
        assertEquals("a", project(budgets.reversed()).budget!!.budget.id)
    }
    @Test fun `exact current month overall scope only`() {
        val b = budget("explicit")
        listOf(b.copy(categoryId = "food"), b.copy(startEpochDay = start - 1), b.copy(endExclusiveEpochDay = end + 1),
            b.copy(startEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(), endExclusiveEpochDay = start),
            b.copy(startEpochDay = end, endExclusiveEpochDay = LocalDate.of(2027, 1, 1).toEpochDay())).forEach {
            assertEquals("fallback", project(listOf(it, budget("fallback")), Settings(todayBudgetId = "explicit")).budget!!.budget.id)
        }
    }
    @Test fun `deleted or ineligible choice falls back without rewriting preference`() {
        val prefs = Settings(todayBudgetId = "deleted")
        assertEquals("idr", project(listOf(budget("idr")), prefs).budget!!.budget.id)
        assertNull(project(emptyList(), prefs).budget)
        assertNull(project(listOf(budget("aud", "AUD")), prefs).budget)
        assertEquals("deleted", prefs.todayBudgetId)
    }
    @Test fun `selection and overspending preserve foreign records`() {
        val budgets = listOf(budget("idr"), budget("aud", "AUD"))
        val entries = listOf(EntryEntity("a", EntryKind.EXPENSE, 10000, "AUD", start, "aud-account"),
            EntryEntity("i", EntryKind.EXPENSE, 3000, "IDR", start, "idr-account"))
        val source = Snapshot(budgets = budgets, entries = entries)
        val idr = source.forToday(now).budget!!
        assertEquals(3000L, idr.spentMinor)
        val aud = source.forToday(now, Settings(todayBudgetId = "aud")).budget!!
        assertEquals(-2000L, aud.remainingMinor)
        assertEquals(1f, aud.fraction)
        assertEquals(entries, source.entries)
        assertEquals(budgets, source.budgets)
    }
    @Test fun `test labels are conservative and review includes personal relationships`() {
        assertTrue(hasDevelopmentLabel("P1 test bank 402330"))
        assertTrue(hasDevelopmentLabel("P15 restore abc"))
        listOf("AUD", "Test savings", "P1 testing", "My P1 test bank", "P15 restored").forEach { assertFalse(hasDevelopmentLabel(it)) }
        val account = AccountEntity("a", "P1 test bank 402330", "AUD")
        val entry = EntryEntity("personal", EntryKind.EXPENSE, 10000, "AUD", start, "a", categoryId = "food", note = "Personal lunch")
        val b = budget("b", "AUD").copy(name = "P1 test budget 402330")
        val review = reviewTestRecords(listOf(account), listOf(entry), listOf(CategoryEntity("food", "Food")), listOf(b), emptyList(), emptyList())
        assertEquals(2, review.size)
        assertTrue(review.all { it.details.joinToString().contains("Personal lunch") })
        assertTrue(review.all { it.details.joinToString().contains("Food [food]") })
        assertTrue(review.none { it.id == "personal" })
    }
}
