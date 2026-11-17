package com.jevon.personalos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.backup.*
import com.jevon.personalos.subscriptions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.*

class SubscriptionDatabaseTest {
    private val jan = LocalDate.of(2026, 1, 31).toEpochDay()
    private suspend fun reject(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; assertTrue(failed) }
    private fun expense(id: String = "paid", amount: Long = 1500, date: Long = jan) = EntryEntity(id, EntryKind.EXPENSE, amount, "IDR", date, fromAccountId = "cash", categoryId = "service", note = "Actual plan payment")
    private fun plan(id: String = "plan") = SubscriptionEntity(id, "Monthly plan", 1200, "IDR", jan, recurrence = Recurrence(Frequency.MONTHLY, 1, jan), description = "Plan description", notes = "Notes", accountId = "cash", categoryId = "service")
    private fun isolated(test: suspend (PersonalDatabase, PersonalDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PersonalDatabase::class.java).build()
        try {
            val dao = db.personalDao()
            dao.saveAccount(AccountEntity("cash", "Cash", "IDR", 10000), true)
            dao.saveAccount(AccountEntity("usd", "Dollar", "USD", 10000), true)
            dao.saveCategory(CategoryEntity("service", "Services", "EXPENSE"), true)
            dao.saveCategory(CategoryEntity("income", "Salary", "INCOME"), true)
            test(db, dao)
        } finally { db.close() }
    }
    @Test fun managementAndCancellationRetainHistory() = isolated { _, dao ->
        dao.saveSubscription(plan(), true)
        dao.saveSubscription(plan().copy(name = "Renamed", amountMinor = 2000), false)
        assertEquals(2000L, dao.subscription("plan")!!.amountMinor)
        dao.recordSubscriptionPayment("plan", jan, expense())
        dao.setSubscriptionActive("plan", false)
        assertFalse(dao.subscription("plan")!!.active); assertEquals(1, dao.paymentsFor("plan").size)
        assertNotNull(dao.findEntry("paid")); dao.setSubscriptionActive("plan", true)
        assertTrue(dao.subscription("plan")!!.active)
    }
    @Test fun paymentIsOneExpenseInBalancesBudgetsAndTotals() = isolated { db, dao ->
        dao.saveSubscription(plan(), true)
        dao.saveBudget(BudgetEntity("b", "January", "IDR", 5000, LocalDate.of(2026,1,1).toEpochDay(), LocalDate.of(2026,2,1).toEpochDay(), "service"), true)
        assertTrue(dao.totals(jan, jan + 1).isEmpty())
        dao.recordSubscriptionPayment("plan", jan, expense())
        val money = db.observeMoney(java.time.YearMonth.of(2026, 1), "", "", "", "", "", 0).first()
        assertEquals("plan", money.linkedSubscriptions["paid"])
        assertEquals(8500L, dao.balances().first { it.account.id == "cash" }.balance)
        assertEquals(1500L, dao.budgetProjections(jan).single().spent)
        assertEquals(1500L, dao.totals(jan, jan + 1).single().amount)
        assertEquals(LocalDate.of(2026,2,28).toEpochDay(), dao.subscription("plan")!!.nextDueEpochDay)
        reject { dao.recordSubscriptionPayment("plan", jan, expense("duplicate")) }
        assertNull(dao.findEntry("duplicate")); assertEquals(1, dao.paymentsFor("plan").size)
    }
    @Test fun earlyLateAndMissedPeriodsKeepBillingAnchor() = isolated { _, dao ->
        dao.saveSubscription(plan(), true)
        val feb = LocalDate.of(2026,2,28).toEpochDay()
        dao.recordSubscriptionPayment("plan", feb, expense("early", date = jan - 2))
        assertEquals(jan, dao.subscription("plan")!!.nextDueEpochDay)
        dao.recordSubscriptionPayment("plan", jan, expense("late", date = jan + 40))
        assertEquals(LocalDate.of(2026,3,31).toEpochDay(), dao.subscription("plan")!!.nextDueEpochDay)
        assertEquals(2, dao.paymentsFor("plan").size)
    }
    @Test fun correctionsReuseExpenseAndBlockedMoneyEditsPreserveLink() = isolated { _, dao ->
        dao.saveSubscription(plan(), true); dao.recordSubscriptionPayment("plan", jan, expense())
        val link = dao.paymentsFor("plan").single()
        dao.correctSubscriptionPayment(link.id, expense(amount = 2000, date = jan + 3))
        assertEquals(8000L, dao.balances().first { it.account.id == "cash" }.balance)
        assertEquals(link, dao.paymentsFor("plan").single()); assertEquals(jan + 3, dao.findEntry("paid")!!.occurredOnEpochDay)
        reject { dao.saveEntry(expense(amount = 3000), false) }; reject { dao.deleteEntry("paid") }
        reject { dao.correctSubscriptionPayment(link.id, expense().copy(fromAccountId = "usd")) }
        assertEquals(2000L, dao.findEntry("paid")!!.amountMinor); assertEquals(link, dao.paymentsFor("plan").single())
    }
    @Test fun defaultsCurrencyAndArchivedAccountsAreGuarded() = isolated { _, dao ->
        reject { dao.saveSubscription(plan().copy(accountId = "usd"), true) }
        reject { dao.saveSubscription(plan().copy(categoryId = "income"), true) }
        dao.saveSubscription(plan(), true)
        reject { dao.saveAccount(AccountEntity("cash", "Cash", "USD"), false) }
        dao.saveAccount(AccountEntity("cash", "Cash", "IDR", 10000, archived = true), false)
        reject { dao.recordSubscriptionPayment("plan", jan, expense()) }
        assertTrue(dao.paymentsFor("plan").isEmpty()); assertNull(dao.findEntry("paid"))
        dao.saveAccount(AccountEntity("cash", "Cash", "IDR", 10000), false)
        dao.recordSubscriptionPayment("plan", jan, expense())
        reject { dao.saveSubscription(dao.subscription("plan")!!.copy(currencyCode = "USD", accountId = "usd"), false) }
    }
    @Test fun overflowAndDuplicateConcurrentAttemptsAreAtomic() = isolated { _, dao ->
        dao.saveSubscription(plan(), true)
        dao.recordEntry(expense("ceiling", Long.MAX_VALUE))
        reject { dao.recordSubscriptionPayment("plan", jan, expense()) }
        assertTrue(dao.paymentsFor("plan").isEmpty()); assertEquals(jan, dao.subscription("plan")!!.nextDueEpochDay)
        dao.deleteEntry("ceiling")
        coroutineScope { (1..2).map { i -> async { runCatching { dao.recordSubscriptionPayment("plan", jan, expense("race$i")) } } }.awaitAll() }
        assertEquals(1, dao.paymentsFor("plan").size); assertEquals(1, dao.paymentExpenses().size)
    }
    @Test fun backupWithDefaultsPaymentsAndReplacementKeepsAllFields() = isolated { db, dao ->
        dao.saveSubscription(plan(), true); dao.recordSubscriptionPayment("plan", jan, expense())
        val store = BackupStore(db); val before = store.snapshot()
        val bytes = BackupCodec.encode(before, "phase2")
        dao.saveSubscription(dao.subscription("plan")!!.copy(notes = "Changed"), false)
        store.replace(BackupCodec.decode(ByteArrayInputStream(bytes)))
        assertEquals(before, store.snapshot()) // also proves new FK-safe deletion order
    }
    @Test fun undoReopensOccurrenceButNeverReactivatesCanceledPlan() = isolated { _, dao ->
        dao.saveSubscription(plan(), true); dao.recordSubscriptionPayment("plan", jan, expense())
        val link = dao.paymentsFor("plan").single()
        dao.setSubscriptionActive("plan", false); dao.undoSubscriptionPayment(link.id)
        assertFalse(dao.subscription("plan")!!.active); assertEquals(jan, dao.subscription("plan")!!.nextDueEpochDay)
        assertTrue(dao.paymentsFor("plan").isEmpty()); assertNull(dao.findEntry("paid"))
        assertEquals(10000L, dao.balances().first { it.account.id == "cash" }.balance)
    }
    @Test fun todayReactivelyAdvancesAfterPaymentAndHidesCanceledPlan() = isolated { db, dao ->
        val now = ZonedDateTime.of(2026,1,30,9,0,0,0,ZoneId.of("Asia/Jakarta"))
        coroutineScope {
            val values = java.util.concurrent.CopyOnWriteArrayList<com.jevon.personalos.today.TodayContent>()
            val job = launch { db.observeToday(now).collect { values += it } }
            suspend fun waitFor(check: () -> Boolean) { withTimeout(5000) { while (!check()) delay(10) } }
            waitFor { values.isNotEmpty() }
            dao.saveSubscription(plan(), true); waitFor { values.last().subscriptions.size == 1 }
            dao.recordSubscriptionPayment("plan", jan, expense()); waitFor { values.last().subscriptions.isEmpty() }
            val next = jan + 1
            dao.saveSubscription(dao.subscription("plan")!!.copy(nextDueEpochDay = next, recurrence = Recurrence(Frequency.MONTHLY, 1, next)), false)
            waitFor { values.last().subscriptions.size == 1 }
            dao.setSubscriptionActive("plan", false); waitFor { values.last().subscriptions.isEmpty() }
            job.cancelAndJoin()
        }
    }
}
