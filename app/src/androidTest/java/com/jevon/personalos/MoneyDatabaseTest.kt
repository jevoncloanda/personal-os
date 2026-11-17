package com.jevon.personalos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.time.YearMonth
import java.time.ZonedDateTime

class MoneyDatabaseTest {
    private lateinit var db: PersonalDatabase
    private lateinit var dao: PersonalDao
    private val month = YearMonth.of(2026, 10)
    private val day = month.atDay(1).toEpochDay()
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PersonalDatabase::class.java).build()
        dao = db.personalDao()
        dao.saveAccount(AccountEntity("a", "Bank", "USD", 10_000), true)
        dao.saveAccount(AccountEntity("b", "Cash", "USD", -500), true)
        dao.saveCategory(CategoryEntity("food", "Food", "EXPENSE"), true)
    }
    @After fun close() { db.close() }
    private fun expense(id: String = "e", amount: Long = 1200, date: Long = day) = EntryEntity(id, EntryKind.EXPENSE, amount, "USD", date, "a", categoryId = "food", minuteOfDay = 720)
    private suspend fun balance(id: String) = dao.balances().first { it.account.id == id }.balance
    private suspend fun fails(block: suspend () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }

    @Test fun openingIncomeExpenseAndCorrection() = runBlocking {
        dao.recordEntry(expense())
        dao.recordEntry(EntryEntity("income", EntryKind.INCOME, 5000, "USD", day, toAccountId = "a"))
        assertEquals(13800, balance("a"))
        dao.saveEntry(expense(amount = 2000).copy(fromAccountId = "b"), false)
        assertEquals(15000, balance("a")); assertEquals(-2500, balance("b"))
        dao.deleteEntry("e"); assertEquals(-500, balance("b"))
        dao.saveAccount(AccountEntity("a", "Renamed", "USD", -100), false)
        assertEquals(4900, balance("a"))
    }
    @Test fun transferEditsAndDeletionStayAtomic() = runBlocking {
        val transfer = EntryEntity("t", EntryKind.TRANSFER, 3000, "USD", day, "a", "b")
        dao.recordEntry(transfer)
        assertEquals(7000, balance("a")); assertEquals(2500, balance("b"))
        assertTrue(dao.totals(day, day + 31).isEmpty())
        dao.saveEntry(transfer.copy(amountMinor = 2000, fromAccountId = "b", toAccountId = "a"), false)
        assertEquals(12000, balance("a")); assertEquals(-2500, balance("b"))
        dao.deleteEntry("t"); assertEquals(10000, balance("a")); assertEquals(-500, balance("b"))
    }
    @Test fun archivedRecordsKeepHistoryAndAllowCorrections() = runBlocking {
        dao.recordEntry(expense())
        dao.saveAccount(AccountEntity("a", "Bank", "USD", 10000, true), false)
        dao.saveCategory(CategoryEntity("food", "Meals", "EXPENSE", true), false)
        fails { dao.recordEntry(expense("new")) }
        dao.saveEntry(expense(amount = 100), false)
        assertEquals(9900, balance("a"))
        assertEquals("Meals", dao.categories().first().name)
        assertEquals("food", dao.findEntry("e")!!.categoryId)
        dao.deleteEntry("e"); assertEquals(10000, balance("a"))
    }
    @Test fun invalidCurrencyCategoryAndTimeNeverWrite() = runBlocking {
        dao.saveAccount(AccountEntity("eur", "Euro", "EUR"), true)
        fails { dao.recordEntry(EntryEntity("t", EntryKind.TRANSFER, 100, "USD", day, "a", "eur")) }
        dao.saveCategory(CategoryEntity("salary", "Salary", "INCOME"), true)
        fails { dao.recordEntry(expense().copy(categoryId = "salary")) }
        fails { dao.recordEntry(expense().copy(minuteOfDay = 1440)) }
        assertEquals(0, dao.entryCount(day, day + 31))
        dao.recordEntry(expense())
        fails { dao.saveAccount(AccountEntity("a", "Bank", "EUR", 10000), false) }
        assertEquals("USD", dao.balances().first { it.account.id == "a" }.account.currencyCode)
    }
    @Test fun budgetsAreHalfOpenAndCategoryLimitsAreNotAdded() = runBlocking {
        val budget = BudgetEntity("overall", "October", "USD", 1000, day, day + 31)
        dao.saveBudget(budget, true)
        dao.saveBudget(budget.copy(id = "category", categoryId = "food", limitMinor = 500), true)
        dao.recordEntry(expense(amount = 1200))
        dao.recordEntry(expense("next", 999, day + 31))
        dao.recordEntry(expense("before", 999, day - 1))
        dao.recordEntry(EntryEntity("income", EntryKind.INCOME, 5000, "USD", day, toAccountId = "a"))
        val projections = dao.budgetProjections(day)
        assertEquals(2, projections.size)
        assertEquals(1200L, projections.first { it.budget.id == "overall" }.spent)
        assertEquals(-200L, projections.first { it.budget.id == "overall" }.summary().remainingMinor)
        assertEquals(1f, projections.first().summary().fraction)
        assertEquals(1200L, projections.first { it.budget.id == "category" }.spent)
        assertEquals(1200L, dao.categorySpending(day, day + 31).single().amount)
        fails { dao.saveBudget(budget.copy(id = "conflict"), true) }
        dao.saveEntry(expense().copy(categoryId = null), false)
        assertEquals(0L, dao.budgetProjections(day).first { it.budget.id == "category" }.spent)
    }
    @Test fun reactiveTodayAndMoneyEmitCommittedTotals() = runBlocking {
        dao.saveBudget(BudgetEntity("z", "October", "USD", 10000, day, day + 31), true)
        val now = ZonedDateTime.parse("2026-10-12T12:00:00+07:00[Asia/Jakarta]")
        val settings = Settings(preferredCurrency = "USD")
        assertNull(db.observeToday(now).first().budget) // IDR default does not select this USD fixture.
        assertEquals(0L, db.observeToday(now, settings).first().budget!!.spentMinor)
        val update = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10000) { db.observeToday(now, settings).first { it.budget?.spentMinor == 1200L } } }
        dao.recordEntry(expense())
        assertEquals(8800L, update.await().budget!!.remainingMinor)
        val money = db.observeMoney(month, "", "", "", "", "", 0).first()
        assertEquals(8800L, money.accounts.first { it.account.id == "a" }.balance)
        assertEquals(1200L, money.totals.single().amount)
    }
    @Test fun overflowRollsBackWholeWrite() = runBlocking {
        dao.saveAccount(AccountEntity("max", "Max", "USD", Long.MAX_VALUE), true)
        try { dao.recordEntry(EntryEntity("overflow", EntryKind.INCOME, 1, "USD", day, toAccountId = "max")); fail("Must reject overflow") } catch (_: ArithmeticException) { }
        assertNull(dao.findEntry("overflow")); assertEquals(Long.MAX_VALUE, balance("max"))
    }
    @Test fun searchFiltersAndPaging() = runBlocking {
        repeat(52) { dao.recordEntry(expense("row-$it", 1).copy(note = "Lunch $it")) }
        assertEquals(51, dao.history(day, day + 31, "EXPENSE", "a", "food", "USD", "Lunch", 51, 0).size)
        assertEquals(2, dao.history(day, day + 31, "EXPENSE", "a", "food", "USD", "Lunch", 51, 50).size)
        assertTrue(dao.history(day, day + 31, "INCOME", "", "", "", "", 51, 0).isEmpty())
        assertEquals(52, dao.history(day, day + 31, "", "", "", "", "Food", 60, 0).size)
    }
    @Test fun defaultsAreIdempotentAndNeverRewritten() = runBlocking {
        // A legacy/custom catalog is preserved rather than supplemented silently.
        dao.ensureDefaultCategories(); assertEquals(1, dao.categories().size)
    }
    @Test fun entrySelectionsAreRememberedOnlyAfterSuccessfulCreation() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val saved = androidx.lifecycle.SavedStateHandle()
        val model = com.jevon.personalos.money.MoneyViewModel(instrument.targetContext.applicationContext as android.app.Application, saved, db)
        val models = androidx.lifecycle.ViewModelStore().apply { put("review", model) }
        try {
            withTimeout(5000) { model.busy.first { !it } }
            val done = CompletableDeferred<Unit>()
            model.entry(expense(), true) { done.complete(Unit) }
            withTimeout(5000) { done.await(); model.busy.first { !it } }
            assertEquals("a", model.lastEntryAccount(EntryKind.EXPENSE))
            assertEquals("food", model.lastEntryCategory(EntryKind.EXPENSE))
            assertEquals("", model.lastEntryAccount(EntryKind.INCOME))
            model.entry(expense("invalid", 100).copy(fromAccountId = "b", categoryId = "missing"), true) {}
            withTimeout(5000) { model.busy.first { !it } }
            assertNotNull(model.error.value)
            assertEquals("a", model.lastEntryAccount(EntryKind.EXPENSE))
            assertEquals("food", model.lastEntryCategory(EntryKind.EXPENSE))
        } finally { instrument.runOnMainSync { models.clear() } }
    }
    @Test fun explicitEmptyAccountCurrencyCorrectionPreservesDollarHistory() = runBlocking {
        dao.recordEntry(expense())
        dao.saveAccount(AccountEntity("b", "Corrected empty account", "IDR", -500), false)
        assertEquals("IDR", dao.balances().first { it.account.id == "b" }.account.currencyCode)
        assertEquals(-500L, balance("b"))
        assertEquals("USD", dao.findEntry("e")!!.currencyCode)
        assertEquals(1200L, dao.findEntry("e")!!.amountMinor)
        fails { dao.saveAccount(AccountEntity("a", "Bank", "IDR", 10000), false) }
        assertEquals(8800L, balance("a"))
    }
}
