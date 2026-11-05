package com.jevon.personalos

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.library.importLibraryCover
import com.jevon.personalos.library.loadLibraryCover
import com.jevon.personalos.library.deleteUnusedLibraryCover
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Artwork fixtures only. No Library/financial rows or personal cover files are changed. */
class LibraryCoverTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture() = File(File(context.filesDir, "library-covers").apply { check(isDirectory || mkdirs()) }, "${UUID.randomUUID()}.jpg")
    private fun writeJpeg(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        } finally { bitmap.recycle() }
    }

    @Test fun missingAndInvalidReferencesUseFallback() = runBlocking {
        assertNull(loadLibraryCover(context, "${UUID.randomUUID()}.jpg"))
        assertNull(loadLibraryCover(context, "../personal-os.db"))
        assertNull(loadLibraryCover(context, "/sdcard/cover.jpg"))
        assertNull(loadLibraryCover(context, "not-a-cover.jpg"))
    }

    @Test fun validBoundedPrivateJpegLoadsAndRemovedFileFallsBack() = runBlocking {
        val file = fixture()
        try {
            writeJpeg(file, 384, 512)
            val bitmap = requireNotNull(loadLibraryCover(context, file.name))
            assertEquals(384, bitmap.width)
            assertEquals(512, bitmap.height)
            assertTrue(file.delete())
            // A cached bitmap must not conceal an artwork file that is now missing.
            assertNull(loadLibraryCover(context, file.name))
        } finally { file.delete() }
    }

    @Test fun corruptOversizedAndUnboundedPrivateImagesUseFallback() = runBlocking {
        val corrupt = fixture()
        val tooManyBytes = fixture()
        val tooManyPixels = fixture()
        try {
            corrupt.writeText("not an image")
            tooManyBytes.outputStream().use { stream ->
                repeat(1024) { stream.write(ByteArray(1024)) }
                stream.write(0)
            }
            writeJpeg(tooManyPixels, 1024, 1024)
            assertNull(loadLibraryCover(context, corrupt.name))
            assertNull(loadLibraryCover(context, tooManyBytes.name))
            assertNull(loadLibraryCover(context, tooManyPixels.name))
        } finally {
            corrupt.delete(); tooManyBytes.delete(); tooManyPixels.delete()
        }
    }

    @Test fun importerRejectsNonPickerUriAndInvalidProviderBytes() = runBlocking {
        try {
            importLibraryCover(context, Uri.parse("file:///sdcard/cover.jpg"))
            fail("A file URI must not be imported")
        } catch (_: IllegalArgumentException) { }
        // Reuse the debug-only provider's invalid JSON fixture; no new provider or permissions.
        val uri = DocumentsContract.buildDocumentUri("${BuildConfig.APPLICATION_ID}.backup-review", "invalid-cover-${UUID.randomUUID()}")
        try {
            importLibraryCover(context, uri)
            fail("Non-image provider bytes must not be imported")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun unusedCoverDeletionAcceptsOnlySafeBasenamesAndLeavesOtherFiles() = runBlocking {
        val unused = fixture()
        val otherCover = fixture()
        val outside = File(context.filesDir, "${UUID.randomUUID()}.jpg")
        try {
            writeJpeg(unused, 64, 96)
            writeJpeg(otherCover, 64, 96)
            outside.writeText("Unrelated isolated sentinel")
            assertNotNull(loadLibraryCover(context, unused.name))
            deleteUnusedLibraryCover(context, "../${outside.name}")
            deleteUnusedLibraryCover(context, outside.absolutePath)
            deleteUnusedLibraryCover(context, "not-a-cover.jpg")
            assertTrue(outside.isFile)
            assertTrue(unused.isFile)
            assertTrue(otherCover.isFile)
            deleteUnusedLibraryCover(context, unused.name)
            assertFalse(unused.exists())
            assertNull(loadLibraryCover(context, unused.name))
            assertNotNull(loadLibraryCover(context, otherCover.name))
            // Best-effort deletion is idempotent after the owned file is gone.
            deleteUnusedLibraryCover(context, unused.name)
            assertTrue(otherCover.isFile)
            assertEquals("Unrelated isolated sentinel", outside.readText())
        } finally {
            unused.delete(); otherCover.delete(); outside.delete()
        }
    }
}
