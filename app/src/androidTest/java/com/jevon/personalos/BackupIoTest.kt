package com.jevon.personalos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.provider.DocumentsContract
import com.jevon.personalos.backup.*
import com.jevon.personalos.data.PersonalDatabase
import com.jevon.personalos.data.AccountEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class BackupIoTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun uri(id: String) = DocumentsContract.buildDocumentUri("${BuildConfig.APPLICATION_ID}.backup-review", id)
    private class QueuedDispatcher : CoroutineDispatcher() {
        val tasks = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun drain() { while (true) (tasks.poll() ?: break).run() }
    }
    @Test fun cancellationBeforeIoEntryStillDeletesCreatedDocument() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
        try {
            val dispatcher = QueuedDispatcher()
            val model = BackupViewModel(db, context.contentResolver, "test", dispatcher)
            val id = "queued-${System.nanoTime()}"
            model.export(uri(id)); assertTrue(dispatcher.tasks.isNotEmpty())
            model.cancel(); dispatcher.drain()
            withTimeout(5000) { model.state.first { it.working == null } }
            assertTrue(BackupFailureProvider.deleted.contains(id))
            assertFalse(BackupFailureProvider.opened.contains(id))
            assertTrue(model.state.value.message!!.startsWith("Canceled."))
            assertEquals(BackupData(), BackupStore(db).snapshot())
        } finally { db.close() }
    }
    @Test fun providerWriteFailureCleansUpOrExplainsRetainedOutput() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
        try {
            val original = backupReviewData()
            BackupStore(db).replace(PreparedBackup(original, "", ""))
            for (prefix in listOf("fail", "refuse", "partial")) {
                val expected = if (prefix == "partial") original.copy(accounts = original.accounts + (1..100).map {
                    AccountEntity("padding-$it", "Disposable output fixture", "IDR", notes = "x".repeat(8192))
                }) else original
                BackupStore(db).replace(PreparedBackup(expected, "", ""))
                val model = BackupViewModel(db, context.contentResolver, "test")
                val id = "$prefix-${System.nanoTime()}"
                model.export(uri(id))
                withTimeout(5000) { model.state.first { it.working == null } }
                assertTrue(model.state.value.error)
                if (prefix == "refuse") assertTrue(model.state.value.message!!.contains("incomplete file"))
                else assertTrue(BackupFailureProvider.deleted.contains(id))
                assertEquals(expected.accounts.toSet(), BackupStore(db).snapshot().accounts.toSet())
                assertEquals(expected.entries.toSet(), BackupStore(db).snapshot().entries.toSet())
            }
        } finally { db.close() }
    }
    @Test fun invalidInputPreviewDismissalAndImportCancellationNeverReplaceData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
        try {
            val store = BackupStore(db)
            val before = backupReviewData()
            store.replace(PreparedBackup(before, "", ""))
            val model = BackupViewModel(db, context.contentResolver, "test")
            model.inspect(uri("invalid-${System.nanoTime()}"))
            withTimeout(5000) { model.state.first { it.working == null } }
            assertTrue(model.state.value.error); assertNull(model.state.value.preview)
            model.inspect(uri("valid-${System.nanoTime()}"))
            withTimeout(5000) { model.state.first { it.preview != null } }
            model.dismissPreview(); assertNull(model.state.value.preview)
            val queued = QueuedDispatcher()
            val canceled = BackupViewModel(db, context.contentResolver, "test", queued)
            val id = "invalid-queued-${System.nanoTime()}"
            canceled.inspect(uri(id)); canceled.cancel(); queued.drain()
            withTimeout(5000) { canceled.state.first { it.working == null } }
            assertFalse(BackupFailureProvider.opened.contains(id))
            assertEquals(before.accounts.toSet(), store.snapshot().accounts.toSet())
            assertEquals(before.entries.toSet(), store.snapshot().entries.toSet())
        } finally { db.close() }
    }
}
