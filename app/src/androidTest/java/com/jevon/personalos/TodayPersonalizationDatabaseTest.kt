package com.jevon.personalos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.backup.*
import com.jevon.personalos.data.*
import com.jevon.personalos.money.reviewTestRecords
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** All writes, including removal/restore, are restricted to a disposable in-memory database. */
class TodayPersonalizationDatabaseTest {
    @Test fun selectionAndReviewPreserveFinancialGraph() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PersonalDatabase::class.java).build()
        try {
            val dao = db.personalDao()
            val now = ZonedDateTime.parse("2026-11-04T09:00:00+07:00[Asia/Jakarta]")
            val start = LocalDate.of(2026, 11, 1).toEpochDay()
            val end = LocalDate.of(2026, 12, 1).toEpochDay()
            dao.saveAccount(AccountEntity("aud", "P1 test bank fixture", "AUD"), true)
            dao.saveAccount(AccountEntity("idr", "Personal IDR", "IDR"), true)
            dao.saveBudget(BudgetEntity("aud", "P1 test budget fixture", "AUD", 8000, start, end), true)
            dao.saveBudget(BudgetEntity("idr", "Monthly plan", "IDR", 8000, start, end), true)
            dao.recordEntry(EntryEntity("expense", EntryKind.EXPENSE, 10000, "AUD", start, "aud"))
            val store = BackupStore(db)
            val before = store.snapshot()
            val automatic = db.observeToday(now).first()
            assertEquals("idr", automatic.budget!!.budget.id)
            assertEquals(0L, automatic.budget!!.spentMinor)
            val prefs = Settings(todayBudgetId = "aud")
            val explicit = db.observeToday(now, prefs).first()
            assertEquals(-2000L, explicit.budget!!.remainingMinor)
            assertEquals(2, explicit.eligibleBudgets.size)
            val candidates = reviewTestRecords(before.accounts, before.entries, before.categories, before.budgets, before.subscriptions, before.payments)
            assertEquals(2, candidates.size)
            assertEquals(before, store.snapshot())
            dao.deleteBudget("aud")
            assertEquals("idr", db.observeToday(now, prefs).first().budget!!.budget.id)
            dao.deleteBudget("idr")
            assertNull(db.observeToday(now, prefs).first().budget)
            assertEquals(before.entries, store.snapshot().entries)
            assertEquals(before.accounts, store.snapshot().accounts)
            val prepared = BackupCodec.decode(java.io.ByteArrayInputStream(BackupCodec.encode(before, "phase35", Instant.EPOCH)))
            store.replace(prepared)
            assertEquals(before, store.snapshot())
        } finally { db.close() }
    }
}
