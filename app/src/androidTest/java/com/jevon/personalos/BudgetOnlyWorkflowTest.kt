package com.jevon.personalos

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.backup.BackupCodec
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.*
import com.jevon.personalos.widget.ExpensePreset
import com.jevon.personalos.widget.QuickExpenseRecorder
import com.jevon.personalos.widget.QuickExpenseStore
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BudgetOnlyWorkflowTest {
    private val month = YearMonth.now()
    private val day = LocalDate.now().toEpochDay()
    private val currency = "IDR"
    private val bookkeeping = budgetAccountId(currency)

    @Test
    fun budgetExpensesAndFutureAllocationsStaySeparateFromRealBalances() = isolated { db, dao ->
        plan(dao)
        assertTrue(dao.balances().isEmpty())
        assertTrue(dao.totals(start(month), start(month.plusMonths(1))).isEmpty())

        val expense = expense("groceries", 200)
        dao.saveEntry(expense, true)
        plan(dao, month.plusMonths(1))
        assertEquals(200L, dao.budgetProjections(day).single().spent)
        assertEquals(800L, dao.budgetProjections(day).single().summary().remainingMinor)
        assertEquals(0L, dao.budgetProjections(start(month.plusMonths(1))).single().spent)
        assertTrue(dao.balances().isEmpty())

        dao.saveEntry(expense.copy(amountMinor = 1_200), false)
        assertEquals(-200L, dao.budgetProjections(day).single().summary().remainingMinor)
        val prepared = BackupCodec.decode(BackupCodec.encode(BackupStore(db).snapshot(), "0.1.1").inputStream())
        BackupStore(db).replace(prepared)
        assertEquals(bookkeeping, dao.findEntry(expense.id)?.fromAccountId)
        assertTrue(dao.balances().isEmpty())

        dao.deleteEntry(expense.id)
        assertEquals(1_000L, dao.budgetProjections(day).single().summary().remainingMinor)
        dao.saveAccount(AccountEntity("real", "Real account", currency, 1_500), true)
        dao.saveEntry(expense.copy(fromAccountId = "real"), true)
        assertEquals(1_300L, dao.balances().single().balance)
        assertEquals(200L, dao.budgetProjections(day).single().spent)
        reject { dao.saveAccount(AccountEntity(bookkeeping, "Rename", currency, archived = true), false) }
        reject { dao.saveEntry(EntryEntity("income", EntryKind.INCOME, 50, currency, day, toAccountId = bookkeeping), true) }
    }

    @Test
    fun widgetPresetRecordsDeduplicatesAndUndoesWithoutARealAccount() = isolated { db, dao ->
        plan(dao)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "budget-widget-${UUID.randomUUID()}.preferences_pb")
        try {
            val store = QuickExpenseStore(PreferenceDataStoreFactory.create(scope = scope) { file })
            val preset = ExpensePreset("preset", "Lunch", 100, currency, bookkeeping, "food", confirm = true)
            store.update { it.copy(presets = listOf(preset), widgets = mapOf("1" to listOf(preset.id))) }
            val recorder = QuickExpenseRecorder(db, store)
            reject { recorder.recordPreset(1, preset.id) }
            val expense = recorder.recordPreset(1, preset.id, confirmed = true)
            assertEquals(expense, recorder.recordPreset(1, preset.id, confirmed = true))
            assertEquals(100L, dao.budgetProjections(day).single().spent)
            recorder.undo(1, expense.id)
            assertNull(dao.findEntry(expense.id))
            assertEquals(0L, dao.budgetProjections(day).single().spent)
            assertTrue(dao.balances().isEmpty())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun subscriptionPaymentCorrectionAndUndoUseTheSameLedger() = isolated { _, dao ->
        plan(dao)
        val subscription = SubscriptionEntity(
            "plan", "Monthly service", 150, currency, day,
            recurrence = Recurrence(Frequency.MONTHLY, anchorEpochDay = day),
        )
        dao.saveSubscription(subscription, true)
        val payment = expense("payment", 150)
        dao.recordSubscriptionPayment(subscription.id, day, payment)
        val link = dao.paymentsFor(subscription.id).single()
        reject { dao.deleteEntry(payment.id) }
        dao.correctSubscriptionPayment(link.id, payment.copy(amountMinor = 250))
        assertEquals(250L, dao.budgetProjections(day).single().spent)
        dao.undoSubscriptionPayment(link.id)
        assertNull(dao.findEntry(payment.id))
        assertEquals(0L, dao.budgetProjections(day).single().spent)
    }

    private fun isolated(block: suspend (PersonalDatabase, PersonalDao) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
        try {
            val dao = db.personalDao()
            dao.saveCategory(CategoryEntity("food", "Food", "EXPENSE"), true)
            block(db, dao)
        } finally {
            db.close()
        }
    }

    private suspend fun reject(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a validation error")
        } catch (_: IllegalArgumentException) {
            // Expected domain rejection.
        }
    }

    private suspend fun plan(dao: PersonalDao, period: YearMonth = month) {
        dao.saveBudget(BudgetEntity(period.toString(), "Monthly plan", currency, 1_000,
            start(period), start(period.plusMonths(1))), true)
    }

    private fun start(value: YearMonth) = value.atDay(1).toEpochDay()

    private fun expense(id: String, amount: Long) =
        EntryEntity(id, EntryKind.EXPENSE, amount, currency, day, fromAccountId = bookkeeping, categoryId = "food")
}
