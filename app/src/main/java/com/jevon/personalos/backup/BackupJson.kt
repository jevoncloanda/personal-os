package com.jevon.personalos.backup

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

internal const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
internal const val MAX_RECORDS = 20_000
internal const val MAX_TEXT = 8_192

internal fun checkedBackupText(text: String): String {
    require(text.length <= MAX_TEXT) { "Backup text field is too long." }
    var index = 0
    while (index < text.length) {
        if (text[index].isHighSurrogate()) { require(index + 1 < text.length && text[index + 1].isLowSurrogate()) { "Invalid Unicode." }; index++ }
        else require(!text[index].isLowSurrogate()) { "Invalid Unicode." }
        index++
    }
    return text
}

/** Strict streaming parser with explicit limits; no entity reflection or numeric coercion. */
internal object BackupJson {
    fun read(input: InputStream): Any? {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BACKUP_BYTES) { "Backup exceeds the 8 MiB limit." }
            bytes.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(StringReader(decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString())).use { reader ->
            reader.strictness = Strictness.STRICT
            val result = Parser(reader).value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after JSON." }
            result
        }
    }

    fun canonical(value: Any?): String = buildString { appendJson(value) }
    fun hash(value: Any?): String = MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun StringBuilder.appendJson(value: Any?) {
        require(length <= MAX_BACKUP_BYTES) { "Backup exceeds the 8 MiB limit." }
        when (value) {
            null -> append("null")
            is Boolean, is Long, is Int -> append(value)
            is String -> {
                append('"')
                value.forEach { c -> when (c) {
                    '"' -> append("\\\""); '\\' -> append("\\\\")
                    else -> if (c.code < 32 || c.isSurrogate()) append("\\u%04x".format(c.code)) else append(c)
                } }
                append('"')
            }
            is Map<*, *> -> {
                append('{')
                value.keys.map { it as String }.sorted().forEachIndexed { index, key ->
                    if (index > 0) append(',')
                    appendJson(key); append(':'); appendJson(value[key])
                }
                append('}')
            }
            is List<*> -> { append('['); value.forEachIndexed { index, item -> if (index > 0) append(','); appendJson(item) }; append(']') }
            else -> error("Unsupported backup value")
        }
        require(length <= MAX_BACKUP_BYTES) { "Backup exceeds the 8 MiB limit." }
    }

    private class Parser(private val reader: JsonReader) {
        private var nodes = 0
        fun value(depth: Int): Any? {
            require(depth <= 12 && ++nodes <= 400_000) { "Backup is too complex." }
            return when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject(); val map = linkedMapOf<String, Any?>()
                    while (reader.hasNext()) {
                        val key = checkedBackupText(reader.nextName())
                        require(!map.containsKey(key)) { "Duplicate JSON field." }
                        map[key] = value(depth + 1)
                    }
                    reader.endObject(); map
                }
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray(); val list = ArrayList<Any?>()
                    while (reader.hasNext()) {
                        require(list.size < MAX_RECORDS) { "Too many backup records." }
                        list.add(value(depth + 1))
                    }
                    reader.endArray(); list
                }
                JsonToken.STRING -> checkedBackupText(reader.nextString())
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.NULL -> { reader.nextNull(); null }
                JsonToken.NUMBER -> {
                    val number = reader.nextString()
                    require(number.length <= 20 && number.matches(Regex("-?(0|[1-9][0-9]*)"))) { "Amounts and dates must be integer values." }
                    number.toLongOrNull() ?: throw IllegalArgumentException("Integer outside supported range.")
                }
                else -> throw IllegalArgumentException("Malformed or truncated JSON.")
            }
        }
    }
}
