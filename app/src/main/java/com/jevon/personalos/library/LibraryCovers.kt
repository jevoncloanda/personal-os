package com.jevon.personalos.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_SOURCE_BYTES = 5 * 1024 * 1024
private const val MAX_STORED_BYTES = 1024 * 1024
private val coverName = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.jpg")
private val coverCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

private fun coverFile(context: Context, name: String): File {
    require(coverName.matches(name)) { "Invalid local cover reference" }
    val folder = File(context.filesDir, "library-covers").canonicalFile
    val file = File(folder, name).canonicalFile
    require(file.parentFile == folder) { "Invalid local cover reference" }
    return file
}

/** Call only after checking that no stored item references the image. */
internal fun deleteUnusedLibraryCover(context: Context, name: String) {
    runCatching { coverFile(context, name).delete() }
}

private fun sampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while ((max(width, height) + sample - 1) / sample > target) sample *= 2
    return sample
}

/** Copies the selected document immediately; no temporary URI or gallery permission is retained. */
suspend fun importLibraryCover(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    require(uri.scheme == "content") { "Choose an image through the document picker" }
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            count += read
            require(count <= MAX_SOURCE_BYTES) { "Choose an image smaller than 5 MiB" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: throw IOException("The selected image could not be opened")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPEG, PNG or WebP image" }
    require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384 &&
        bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000) { "The selected image dimensions are too large" }
    currentCoroutineContext().ensureActive()
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1024)
    }) ?: throw IllegalArgumentException("The selected image could not be decoded")
    var scaled: Bitmap? = null
    try {
        val ratio = minOf(1.0, 512.0 / max(decoded.width, decoded.height))
        val bitmap = if (ratio < 1.0) Bitmap.createScaledBitmap(decoded,
            (decoded.width * ratio).roundToInt().coerceAtLeast(1), (decoded.height * ratio).roundToInt().coerceAtLeast(1), true)
            .also { scaled = it } else decoded
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "The cover could not be saved" }
        require(output.size() <= MAX_STORED_BYTES) { "The compressed cover is too large" }
        val name = "${UUID.randomUUID()}.jpg"
        val target = coverFile(context, name)
        val folder = target.parentFile!!
        check(folder.isDirectory || folder.mkdirs()) { "The cover folder could not be created" }
        val temporary = File(folder, "$name.tmp")
        try {
            temporary.outputStream().use { stream -> output.writeTo(stream); stream.fd.sync() }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(target)) { "The cover could not be saved" }
            currentCoroutineContext().ensureActive()
            name
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        }
    } finally {
        scaled?.takeIf { it !== decoded }?.recycle()
        decoded.recycle()
    }
}

/** Missing, inaccessible or invalid artwork uses the media fallback; never blocks local records. */
suspend fun loadLibraryCover(context: Context, name: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val file = coverFile(context, name)
        if (!file.isFile || file.length() !in 1..MAX_STORED_BYTES.toLong()) return@withContext null
        val key = "${file.path}:${file.length()}:${file.lastModified()}"
        coverCache.get(key)?.let { return@withContext it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512 || bounds.outMimeType != "image/jpeg") return@withContext null
        currentCoroutineContext().ensureActive()
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 512)
        })?.also { coverCache.put(key, it) }
    } catch (_: IOException) { null }
    catch (_: SecurityException) { null }
    catch (_: IllegalArgumentException) { null }
}
