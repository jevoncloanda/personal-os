package com.jevon.personalos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jevon.personalos.backup.*
import com.jevon.personalos.data.*
import com.jevon.personalos.library.withStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime

/** Every write/delete/restore targets disposable in-memory storage, never the personal application DB. */
class LibraryDatabaseTest {
    private lateinit var db: PersonalDatabase
    private val dao get() = db.personalDao()
    private val now = ZonedDateTime.parse("2026-11-05T09:00:00+07:00[Asia/Jakarta]")
    private val day = now.toLocalDate()
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PersonalDatabase::class.java).build() }
    @After fun close() { db.close() }
    private fun item(id: String, kind: LibraryKind = LibraryKind.BOOK) = LibraryItemEntity(id, "Title $id", kind,
        LibraryStatus.PLANNED, updatedAtMillis = 100, addedAtMillis = 50, notes = "Private notes", rating = 4)
    private fun prepared(data: BackupData) = BackupCodec.decode(ByteArrayInputStream(BackupCodec.encode(data, "phase4-isolated")))
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected validation rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun allMediaCrudAndStatusReversalPreserveOtherDomains() = runBlocking {
        val store = BackupStore(db)
        store.replace(prepared(backupReviewData(now).copy(library = emptyList())))
        val before = store.snapshot()
        val items = LibraryKind.entries.map { kind -> item(kind.name, kind) }
        items.forEach { dao.saveLibrary(it, true) }
        items.forEach { original ->
            val active = original.withStatus(LibraryStatus.ACTIVE, day)
            val completed = active.withStatus(LibraryStatus.COMPLETED, day.plusDays(1))
            dao.saveLibrary(completed, false)
            dao.saveLibrary(completed.withStatus(LibraryStatus.ON_HOLD, day.plusDays(2)), false)
            assertEquals(completed.copy(status = LibraryStatus.ON_HOLD), dao.libraryItem(original.id))
            rejected { dao.saveLibrary(completed.copy(kind = if (original.kind == LibraryKind.BOOK) LibraryKind.MOVIE else LibraryKind.BOOK), false) }
            dao.deleteLibrary(original.id)
            assertNull(dao.libraryItem(original.id))
        }
        assertEquals(before, store.snapshot())
    }

    @Test fun manualDuplicateTitlesAreAllowedButProviderDuplicatesAreRejected() = runBlocking {
        val first = item("manual-one").copy(title = "Same title")
        val second = first.copy(id = "manual-two")
        dao.saveLibrary(first, true); dao.saveLibrary(second, true)
        assertEquals(2, dao.libraryCount("same TITLE", "BOOK", ""))
        val imported = first.copy(id = "imported", provider = "openlibrary", providerId = "/works/OL123W")
        dao.saveLibrary(imported, true)
        rejected { dao.saveLibrary(imported.copy(id = "duplicate"), true) }
        rejected { dao.saveLibrary(second.copy(provider = imported.provider, providerId = imported.providerId), false) }
        assertEquals(second, dao.libraryItem(second.id))
        val edited = imported.copy(title = "Updated metadata title", notes = "Personal review stays", rating = 5)
        dao.saveLibrary(edited, false)
        assertEquals(edited, dao.libraryItem(imported.id))
        assertEquals(3, dao.libraryCount("", "", ""))
    }

    @Test fun searchFiltersSortingAndPagingRemainBounded() = runBlocking {
        repeat(75) { index ->
            dao.saveLibrary(item("row-%03d".format(index), if (index % 2 == 0) LibraryKind.BOOK else LibraryKind.MOVIE).copy(
                title = "Orbit %03d".format(index), status = if (index % 3 == 0) LibraryStatus.ACTIVE else LibraryStatus.PLANNED,
                addedAtMillis = 1000L + index, updatedAtMillis = 2000L + 74 - index), true)
        }
        assertEquals(75, dao.libraryCount("oRbIt", "", ""))
        assertEquals(38, dao.libraryCount("orbit", "BOOK", ""))
        assertEquals(13, dao.libraryCount("orbit", "BOOK", "ACTIVE"))
        assertTrue(dao.libraryPage("missing", "", "", "TITLE").isEmpty())
        val first = dao.libraryPage("ORBIT", "", "", "TITLE")
        assertEquals(61, first.size)
        assertEquals("row-000", first.first().id)
        val second = dao.libraryPage("orbit", "", "", "TITLE", offset = 60)
        assertEquals(15, second.size)
        assertEquals(75, (first.take(60) + second).map { it.id }.toSet().size)
        assertEquals("row-074", dao.libraryPage("", "", "", "ADDED").first().id)
        assertEquals("row-000", dao.libraryPage("", "", "", "UPDATED").first().id)
        assertEquals(4, dao.libraryHighlights("ACTIVE", "").size)
        assertTrue(dao.libraryPage("", "MOVIE", "PLANNED", "TITLE").all { it.kind == LibraryKind.MOVIE && it.status == LibraryStatus.PLANNED })
    }

    @Test fun quickProgressAndCompletionUpdateTodayReactivelyWithoutChangingNotes() = runBlocking {
        val book = item("reading").copy(progress = 12, total = 320)
        dao.saveLibrary(book, true)
        assertTrue(db.observeToday(now).first().library.isEmpty())
        val active = book.withStatus(LibraryStatus.ACTIVE, day)
        dao.saveLibrary(active, false)
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { db.observeToday(now).first { content -> content.library.any { it.id == book.id && it.progress == 160 } } }
        }
        val progressed = active.copy(progress = 160, updatedAtMillis = 200)
        dao.saveLibrary(progressed, false)
        assertEquals(progressed, update.await().library.single())
        val removed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { db.observeToday(now).first { it.library.isEmpty() } } }
        dao.saveLibrary(progressed.withStatus(LibraryStatus.COMPLETED, day.plusDays(1)), false)
        assertTrue(removed.await().library.isEmpty())
        assertEquals(book.notes, dao.libraryItem(book.id)!!.notes)
        assertEquals(book.rating, dao.libraryItem(book.id)!!.rating)
        assertEquals(160, dao.libraryItem(book.id)!!.progress)
        assertNotNull(dao.libraryItem(book.id)!!.completedEpochDay)
        repeat(5) { index -> dao.saveLibrary(item("recent-$index", LibraryKind.MOVIE).copy(status = LibraryStatus.ACTIVE, updatedAtMillis = 1000L + index), true) }
        assertEquals(listOf("recent-4", "recent-3", "recent-2"), db.observeToday(now).first().library.map { it.id })
    }

    @Test fun portableRestoreKeepsLibraryFieldsAndFailureRollsBackAllDomains() = runBlocking {
        val store = BackupStore(db)
        store.replace(prepared(backupReviewData(now).copy(library = emptyList())))
        val items = listOf(
            item("movie", LibraryKind.MOVIE).copy(status = LibraryStatus.COMPLETED, completedEpochDay = day.toEpochDay()),
            item("series", LibraryKind.SERIES).copy(season = 2, episode = 4, totalSeasons = 5, progress = 10, total = 50),
            item("game", LibraryKind.GAME).copy(platform = "PC", hoursPlayedMinutes = 735, completionPercent = 40),
            item("book").copy(author = "Author", isbn = "9780140328721", progress = 160, total = 320,
                provider = "openlibrary", providerId = "/works/OL123W", coverPath = "12345678-1234-1234-1234-123456789abc.jpg"),
        )
        items.forEach { dao.saveLibrary(it, true) }
        val before = store.snapshot()
        val portable = before.copy(library = before.library.map { it.copy(coverPath = null) })
        val fresh = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PersonalDatabase::class.java).build()
        try { BackupStore(fresh).replace(prepared(before)); assertEquals(portable, BackupStore(fresh).snapshot()) }
        finally { fresh.close() }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_library_restore BEFORE INSERT ON library_items BEGIN SELECT RAISE(ABORT,'Isolated restore failure'); END")
        try { store.replace(prepared(before)); fail("Restore must fail") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, store.snapshot())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
