package com.jevon.personalos

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.*
import com.jevon.personalos.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class QuickExpenseDatabaseTest {
    private lateinit var database: PersonalDatabase
    private lateinit var store: QuickExpenseStore
    private lateinit var recorder: QuickExpenseRecorder
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private val account = AccountEntity("a", "Widget test", "IDR", 1_000_000)
    private val category = CategoryEntity("c", "Test expense", "EXPENSE")
    private val preset = ExpensePreset("p", "Test preset", 250_000, "IDR", "a", "c")

    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "widget-test-${UUID.randomUUID()}.preferences_pb")
        store = QuickExpenseStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        recorder = QuickExpenseRecorder(database, store)
        database.personalDao().saveAccount(account, true)
        database.personalDao().saveCategory(category, true)
        store.update { it.copy(presets = listOf(preset), widgets = mapOf("1" to listOf("p"),
            "2" to listOf("p"))) }
    }

    @After fun close() {
        database.close()
        scope.cancel()
        file.delete()
    }

    private suspend fun count() = database.personalDao().entryCount(
        LocalDate.now().toEpochDay(), LocalDate.now().plusDays(1).toEpochDay())

    private suspend fun reject(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected safe rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun concurrentCallbacksAcrossInstancesWriteOnceAndLaterExpenseWorks() = runBlocking {
        coroutineScope {
            (1..12).map { index -> async(Dispatchers.IO) {
                recorder.recordPreset(if (index % 2 == 0) 1 else 2, "p")
            } }.awaitAll()
        }
        assertEquals(1, count())
        val first = store.state.first().feedback["1"]!!.entry!!
        assertEquals(750_000, database.personalDao().balances().single().balance)
        recorder.undo(1, first.id)
        assertEquals(0, count())
        reject { recorder.undo(1, first.id) }
        delay(ACCIDENTAL_TAP_MILLIS + 50)
        val second = recorder.recordPreset(1, "p")
        assertNotEquals(first.id, second.id)
        assertEquals(1, count())
    }

    @Test fun committedJournalRecoversAfterRecorderRestart() = runBlocking {
        val entry = preset.entry()
        store.update { it.copy(submissions = mapOf("preset-p" to ExpenseSubmission(entry, 0))) }
        database.personalDao().recordEntry(entry)
        val restarted = QuickExpenseRecorder(database, store)
        assertEquals(entry, restarted.recordPreset(1, "p"))
        assertEquals(1, count())
        assertEquals(entry.id, store.state.first().feedback["1"]!!.entry!!.id)
    }

    @Test fun continuousTapBurstNeedsAQuietPeriodBeforeAnotherExpense() = runBlocking {
        val first = recorder.recordPreset(1, "p")
        repeat(4) {
            delay(ACCIDENTAL_TAP_MILLIS / 2)
            assertEquals(first.id, recorder.recordPreset(1, "p").id)
        }
        assertEquals(1, count())
        delay(ACCIDENTAL_TAP_MILLIS + 50)
        assertNotEquals(first.id, recorder.recordPreset(1, "p").id)
        assertEquals(2, count())
    }

    @Test fun uncommittedJournalRetriesTheExactId() = runBlocking {
        val entry = preset.entry()
        store.update { it.copy(submissions = mapOf("preset-p" to ExpenseSubmission(entry, 0))) }
        assertEquals(entry.id, recorder.recordPreset(1, "p").id)
        assertEquals(1, count())
    }

    @Test fun staleReferencesAndConfirmationCannotWrite() = runBlocking {
        reject { recorder.recordPreset(1, "removed") }
        reject { recorder.recordPreset(999, "p") }
        reject { recorder.recordPreset(1, "p", expectedRevision = "stale-revision") }
        database.personalDao().saveAccount(account.copy(archived = true), false)
        reject { recorder.recordPreset(1, "p") }
        database.personalDao().saveAccount(account, false)
        database.personalDao().saveCategory(category.copy(archived = true), false)
        reject { recorder.recordPreset(1, "p") }
        database.personalDao().saveCategory(category, false)
        store.update { it.copy(presets = listOf(preset.copy(confirm = true))) }
        reject { recorder.recordPreset(1, "p") }
        assertEquals(0, count())
        recorder.recordPreset(1, "p", confirmed = true)
        assertEquals(1, count())
    }

    @Test fun undoRejectsUnrelatedAndEditedEntriesAndHandlesAlreadyDeleted() = runBlocking {
        val entry = recorder.recordPreset(1, "p")
        val unrelated = preset.entry()
        database.personalDao().recordEntry(unrelated)
        reject { recorder.undo(1, unrelated.id) }
        database.personalDao().saveEntry(entry.copy(note = "Edited in Money"), false)
        reject { recorder.undo(1, entry.id) }
        assertEquals(2, count())
        database.personalDao().deleteEntry(entry.id)
        recorder.undo(1, entry.id)
        assertNotNull(database.personalDao().findEntry(unrelated.id))
        assertEquals(1, count())
    }

    @Test fun customRequestIsSingleSubmitAndInstanceConfigurationIsIndependent() = runBlocking {
        val request = UUID.randomUUID().toString()
        val first = recorder.recordCustom(1, request, preset)
        assertEquals(first.id, recorder.recordCustom(1, request, preset).id)
        assertEquals(1, count())
        store.update { it.copy(widgets = it.widgets + ("2" to emptyList())) }
        assertEquals(listOf("p"), store.state.first().widgets["1"])
        assertTrue(store.state.first().widgets["2"]!!.isEmpty())
        reject { recorder.recordPreset(2, "p") }
        // Even after journal housekeeping, Room remains authoritative for a custom request.
        store.update { it.copy(submissions = emptyMap()) }
        assertEquals(first.id, recorder.recordCustom(1, request, preset).id)
        assertEquals(1, count())
    }

    @Test fun expenseUpdatesExistingBudgetAndUndoReversesProjection() = runBlocking {
        val month = YearMonth.now()
        database.personalDao().saveBudget(BudgetEntity("b", "Test budget", "IDR", 500_000,
            month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay()), true)
        val entry = recorder.recordPreset(1, "p")
        assertEquals(250_000, database.personalDao().budgetProjections(
            LocalDate.now().toEpochDay()).single().spent)
        recorder.undo(1, entry.id)
        assertEquals(0, database.personalDao().budgetProjections(
            LocalDate.now().toEpochDay()).single().spent)
    }

    @Test fun failedArithmeticRollsBackWithoutSuccessAndCanBeRepaired() = runBlocking {
        database.personalDao().saveAccount(account.copy(openingBalanceMinor = Long.MIN_VALUE), false)
        try {
            recorder.recordPreset(1, "p")
            fail("Overflow must roll back")
        } catch (_: ArithmeticException) { }
        assertEquals(0, count())
        assertNull(store.state.first().feedback["1"]?.entry)
        database.personalDao().saveAccount(account, false)
        recorder.recordPreset(1, "p")
        assertEquals(1, count())
    }
}
