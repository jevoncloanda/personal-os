package com.jevon.personalos

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.jevon.personalos.backup.*
import com.jevon.personalos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.YearMonth
import java.time.ZonedDateTime

/** Never uses PersonalOsApplication.database. Every replacement targets a new in-memory database. */
class BackupDatabaseTest {
    private lateinit var database: PersonalDatabase
    private lateinit var store: BackupStore
    private val now = ZonedDateTime.now()
    private val fixture get() = backupReviewData(now)
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PersonalDatabase::class.java).build()
        store = BackupStore(database)
    }
    @After fun close() { database.close() }
    private fun prepared(data: BackupData) = BackupCodec.decode(ByteArrayInputStream(BackupCodec.encode(data, "isolated-test")))
    private fun assertSemantic(expected: BackupData, actual: BackupData) {
        assertEquals(expected.accounts.toSet(), actual.accounts.toSet()); assertEquals(expected.categories.toSet(), actual.categories.toSet())
        assertEquals(expected.entries.toSet(), actual.entries.toSet()); assertEquals(expected.budgets.toSet(), actual.budgets.toSet())
        assertEquals(expected.events.toSet(), actual.events.toSet()); assertEquals(expected.reminders.toSet(), actual.reminders.toSet())
        assertEquals(expected.subscriptions.toSet(), actual.subscriptions.toSet()); assertEquals(expected.payments.toSet(), actual.payments.toSet())
        assertEquals(expected.library.toSet(), actual.library.toSet())
    }
    @Test fun emptyDatabaseRoundTripAndFullReplacement() = runBlocking {
        val empty = prepared(store.snapshot())
        store.replace(prepared(fixture)); assertTrue(store.snapshot().entries.isNotEmpty())
        store.replace(empty); assertEquals(BackupData(), store.snapshot())
    }
    @Test fun populatedBackupRestoresFreshSchemaAndAllFinancialProjections() = runBlocking {
        store.replace(prepared(fixture)); val original = store.snapshot()
        val moneyBefore = database.observeMoney(YearMonth.from(now), "", "", "", "", "", 0).first()
        val todayBefore = database.observeToday(now).first()
        val backup = prepared(original)
        val fresh = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PersonalDatabase::class.java).build()
        try {
            BackupStore(fresh).replace(backup)
            assertSemantic(original, BackupStore(fresh).snapshot())
            val money = fresh.observeMoney(YearMonth.from(now), "", "", "", "", "", 0).first()
            assertEquals(moneyBefore, money); assertEquals(todayBefore, fresh.observeToday(now).first())
            assertEquals(12_674_500L, money.accounts.single { it.account.id == "bank" }.balance)
            assertEquals(250_000L, money.accounts.single { it.account.id == "cash" }.balance)
            assertEquals(-12_445L, money.accounts.single { it.account.id == "usd" }.balance)
            assertEquals(125_500L, money.budgets.single { it.budget.id == "overall" }.spent)
        } finally { fresh.close() }
    }
    @Test fun failedInsertRollsBackDeletesAndEarlierInsertsWithForeignKeysEnabled() = runBlocking {
        val old = BackupData(accounts = listOf(AccountEntity("old", "Existing disposable", "USD", 123)))
        store.replace(prepared(old))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_backup_entry BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT,'Injected isolated restore failure'); END")
        try { store.replace(prepared(fixture)); fail("Restore should fail") } catch (_: android.database.sqlite.SQLiteException) { }
        assertSemantic(old, store.snapshot())
        database.openHelper.readableDatabase.query("PRAGMA foreign_keys").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
    @Test fun invalidAndCanceledRestoresPreserveExistingRows() = runBlocking {
        store.replace(prepared(fixture)); val before = store.snapshot()
        val damaged = BackupCodec.encode(fixture, "test").also { it[it.size / 2] = 0 }
        try { store.replace(BackupCodec.decode(ByteArrayInputStream(damaged))); fail("Invalid backup accepted") } catch (_: Exception) { }
        assertSemantic(before, store.snapshot())
        val task = launch(start = CoroutineStart.LAZY) { store.replace(prepared(BackupData())) }
        task.cancelAndJoin(); assertSemantic(before, store.snapshot())
        // Bypass the preview constructor to prove the transaction owner validates independently.
        try { store.replace(PreparedBackup(fixture.copy(accounts = emptyList()), "", "")); fail("Broken references accepted") } catch (_: IllegalArgumentException) { }
        assertSemantic(before, store.snapshot())
    }
    @Test fun activeMoneyAndTodayCollectorsReceiveOnlyCommittedReplacement() = runBlocking {
        store.replace(prepared(BackupData(accounts = listOf(AccountEntity("old", "Old", "USD")))))
        val moneyUpdate = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { database.observeMoney(YearMonth.from(now), "", "", "", "", "", 0).first { it.accounts.any { a -> a.account.id == "bank" } } } }
        val todayUpdate = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { database.observeToday(now).first { it.budget?.budget?.id == "overall" } } }
        store.replace(prepared(fixture))
        assertEquals(12_674_500L, moneyUpdate.await().accounts.single { it.account.id == "bank" }.balance)
        assertEquals(125_500L, todayUpdate.await().budget!!.spentMinor)
    }
    @Test fun cancellationInsideTransactionRollsBackReplacement() = runBlocking {
        store.replace(prepared(fixture)); val before = store.snapshot()
        val cancellation = kotlinx.coroutines.CompletableDeferred<Unit>()
        val operation = launch(Dispatchers.IO) {
            database.withTransaction {
                database.backupDao().clearPayments(); database.backupDao().clearEntries()
                cancellation.complete(Unit)
                awaitCancellation()
            }
        }
        cancellation.await(); operation.cancelAndJoin()
        assertSemantic(before, store.snapshot())
    }
}
