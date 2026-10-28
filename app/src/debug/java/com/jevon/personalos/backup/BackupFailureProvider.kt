package com.jevon.personalos.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/** Nonexported, debug-only I/O failure fixture. No roots, picker entry or personal-data access. */
class BackupFailureProvider : ContentProvider() {
    companion object { val deleted = ConcurrentHashMap.newKeySet<String>(); val opened = ConcurrentHashMap.newKeySet<String>() }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/json"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>("isolated-test.json", 0)) }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method == "android:deleteDocument") {
            val document = extras?.getParcelable<Uri>("uri") ?: error("Missing fixture URI")
            val id = document.lastPathSegment!!
            if (id.startsWith("refuse")) throw UnsupportedOperationException("Fixture rejects cleanup")
            deleted.add(id)
        }
        return Bundle.EMPTY
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment!!; opened.add(id)
        if (mode == "r") {
            val file = File(requireNotNull(context).cacheDir, "backup-review-input.json")
            file.writeBytes(if (id.startsWith("valid")) BackupCodec.encode(backupReviewData(), "isolated-provider") else "{".toByteArray())
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        if (id.startsWith("fail") || id.startsWith("refuse")) throw FileNotFoundException("Fixture refuses write")
        if (id.startsWith("partial")) {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            Thread({
                ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                    val buffer = ByteArray(256); input.read(buffer)
                    pipe[0].closeWithError("Fixture interrupted output")
                }
            }, "backup-failure-fixture").start()
            return pipe[1]
        }
        val target = File(requireNotNull(context).cacheDir, "backup-review-output.json")
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
    }
}
