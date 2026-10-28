package com.jevon.personalos.backup

import com.jevon.personalos.data.*
import java.io.InputStream
import java.time.Instant
import java.security.MessageDigest

internal data class PreparedBackup(val data: BackupData, val createdAt: String, val appVersion: String)

/** Explicit v2 mapping; v1 is translated with safe defaults, independently of Room migrations. */
internal object BackupCodec {
    private const val FORMAT = "com.jevon.personalos.backup"
    fun encode(data: BackupData, appVersion: String, created: Instant = Instant.now()): ByteArray {
        data.validate()
        checkedBackupText(appVersion)
        val content = linkedMapOf<String, Any?>("format" to FORMAT, "version" to 2L, "schemaVersion" to 3L,
            "createdAt" to created.toString(), "appVersion" to appVersion, "data" to encodeData(data))
        val bytes = BackupJson.canonical(content + ("integrity" to record("algorithm" to "SHA-256", "digest" to BackupJson.hash(content)))).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "Backup exceeds the 8 MiB limit. No data was changed." }
        return bytes
    }
    fun decode(input: InputStream): PreparedBackup {
        val root = objectValue(BackupJson.read(input))
        root.fields("format", "version", "schemaVersion", "createdAt", "appVersion", "data", "integrity")
        require(root.string("format") == FORMAT) { "This is not a Personal OS backup." }
        val version = root.long("version")
        require(version in 1L..2L) { "This backup format is not supported. Use an app version that supports it." }
        require(root.long("schemaVersion") == if (version == 1L) 2L else 3L) { "This backup uses an unsupported data schema." }
        val integrity = root.obj("integrity").also { it.fields("algorithm", "digest") }
        require(integrity.string("algorithm") == "SHA-256") { "Unsupported backup checksum." }
        val digest = integrity.string("digest")
        require(digest.matches(Regex("[0-9a-f]{64}")) && MessageDigest.isEqual(digest.toByteArray(Charsets.US_ASCII), BackupJson.hash(root - "integrity").toByteArray(Charsets.US_ASCII))) { "Backup checksum failed. Choose an undamaged backup." }
        val createdAt = root.string("createdAt")
        require(createdAt.endsWith("Z") && Instant.parse(createdAt).atOffset(java.time.ZoneOffset.UTC).year in 1..9999) { "Invalid backup creation time." }
        val data = decodeData(root.obj("data"), version); data.validate()
        return PreparedBackup(data, createdAt, root.string("appVersion"))
    }

    private fun record(vararg fields: Pair<String, Any?>) = linkedMapOf(*fields)
    private fun repeat(value: Recurrence) = record("frequency" to value.frequency?.name, "interval" to value.interval,
        "anchorEpochDay" to value.anchorEpochDay, "untilEpochDay" to value.untilEpochDay)
    private fun encodeData(data: BackupData): Map<String, Any?> = record(
        "accounts" to data.accounts.sortedBy { it.id }.map { record("id" to it.id, "name" to it.name, "currencyCode" to it.currencyCode, "openingBalanceMinor" to it.openingBalanceMinor, "archived" to it.archived, "type" to it.type, "notes" to it.notes) },
        "categories" to data.categories.sortedBy { it.id }.map { record("id" to it.id, "name" to it.name, "kind" to it.kind, "archived" to it.archived) },
        "entries" to data.entries.sortedBy { it.id }.map { record("id" to it.id, "kind" to it.kind.name, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "occurredOnEpochDay" to it.occurredOnEpochDay, "fromAccountId" to it.fromAccountId, "toAccountId" to it.toAccountId, "categoryId" to it.categoryId, "note" to it.note, "minuteOfDay" to it.minuteOfDay) },
        "budgets" to data.budgets.sortedBy { it.id }.map { record("id" to it.id, "name" to it.name, "currencyCode" to it.currencyCode, "limitMinor" to it.limitMinor, "startEpochDay" to it.startEpochDay, "endExclusiveEpochDay" to it.endExclusiveEpochDay, "categoryId" to it.categoryId) },
        "events" to data.events.sortedBy { it.id }.map { record("id" to it.id, "title" to it.title, "startsAtMillis" to it.startsAtMillis, "endsAtMillis" to it.endsAtMillis, "zoneId" to it.zoneId, "allDay" to it.allDay, "location" to it.location, "recurrence" to repeat(it.recurrence)) },
        "reminders" to data.reminders.sortedBy { it.id }.map { record("id" to it.id, "title" to it.title, "dueAtMillis" to it.dueAtMillis, "completedAtMillis" to it.completedAtMillis, "recurrence" to repeat(it.recurrence)) },
        "subscriptions" to data.subscriptions.sortedBy { it.id }.map { record("id" to it.id, "name" to it.name, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "nextDueEpochDay" to it.nextDueEpochDay, "active" to it.active, "recurrence" to repeat(it.recurrence), "description" to it.description, "notes" to it.notes, "accountId" to it.accountId, "categoryId" to it.categoryId) },
        "subscriptionPayments" to data.payments.sortedBy { it.id }.map { record("id" to it.id, "subscriptionId" to it.subscriptionId, "dueEpochDay" to it.dueEpochDay, "entryId" to it.entryId) },
        "libraryItems" to data.library.sortedBy { it.id }.map { record("id" to it.id, "title" to it.title, "kind" to it.kind.name, "status" to it.status.name, "progress" to it.progress, "total" to it.total, "updatedAtMillis" to it.updatedAtMillis) },
    )
    private fun decodeData(data: Map<String, Any?>, version: Long): BackupData {
        data.fields("accounts", "categories", "entries", "budgets", "events", "reminders", "subscriptions", "subscriptionPayments", "libraryItems")
        require(data.values.sumOf { (it as? List<*>)?.size ?: throw IllegalArgumentException("Missing backup data section.") } <= MAX_RECORDS) { "Too many backup records." }
        return BackupData(
            accounts = data.rows("accounts", "id,name,currencyCode,openingBalanceMinor,archived,type,notes") { AccountEntity(it.string("id"), it.string("name"), it.string("currencyCode"), it.long("openingBalanceMinor"), it.bool("archived"), it.string("type"), it.string("notes")) },
            categories = data.rows("categories", "id,name,kind,archived") { CategoryEntity(it.string("id"), it.string("name"), it.nullString("kind"), it.bool("archived")) },
            entries = data.rows("entries", "id,kind,amountMinor,currencyCode,occurredOnEpochDay,fromAccountId,toAccountId,categoryId,note,minuteOfDay") { EntryEntity(it.string("id"), EntryKind.valueOf(it.string("kind")), it.long("amountMinor"), it.string("currencyCode"), it.long("occurredOnEpochDay"), it.nullString("fromAccountId"), it.nullString("toAccountId"), it.nullString("categoryId"), it.string("note"), it.int("minuteOfDay")) },
            budgets = data.rows("budgets", "id,name,currencyCode,limitMinor,startEpochDay,endExclusiveEpochDay,categoryId") { BudgetEntity(it.string("id"), it.string("name"), it.string("currencyCode"), it.long("limitMinor"), it.long("startEpochDay"), it.long("endExclusiveEpochDay"), it.nullString("categoryId")) },
            events = data.rows("events", "id,title,startsAtMillis,endsAtMillis,zoneId,allDay,location,recurrence") { EventEntity(it.string("id"), it.string("title"), it.long("startsAtMillis"), it.long("endsAtMillis"), it.string("zoneId"), it.bool("allDay"), it.string("location"), recurrence(it.obj("recurrence"))) },
            reminders = data.rows("reminders", "id,title,dueAtMillis,completedAtMillis,recurrence") { ReminderEntity(it.string("id"), it.string("title"), it.nullLong("dueAtMillis"), it.nullLong("completedAtMillis"), recurrence(it.obj("recurrence"))) },
            subscriptions = data.rows("subscriptions", "id,name,amountMinor,currencyCode,nextDueEpochDay,active,recurrence" + if (version == 2L) ",description,notes,accountId,categoryId" else "") { SubscriptionEntity(it.string("id"), it.string("name"), it.long("amountMinor"), it.string("currencyCode"), it.long("nextDueEpochDay"), it.bool("active"), recurrence(it.obj("recurrence")), if (version == 2L) it.string("description") else "", if (version == 2L) it.string("notes") else "", if (version == 2L) it.nullString("accountId") else null, if (version == 2L) it.nullString("categoryId") else null) },
            payments = data.rows("subscriptionPayments", "id,subscriptionId,dueEpochDay,entryId") { SubscriptionPaymentEntity(it.string("id"), it.string("subscriptionId"), it.long("dueEpochDay"), it.string("entryId")) },
            library = data.rows("libraryItems", "id,title,kind,status,progress,total,updatedAtMillis") { LibraryItemEntity(it.string("id"), it.string("title"), LibraryKind.valueOf(it.string("kind")), LibraryStatus.valueOf(it.string("status")), it.int("progress"), it.nullLong("total")?.let { n -> exactInt(n) }, it.long("updatedAtMillis")) },
        )
    }
    private fun recurrence(obj: Map<String, Any?>): Recurrence {
        obj.fields("frequency", "interval", "anchorEpochDay", "untilEpochDay")
        return Recurrence(obj.nullString("frequency")?.let(Frequency::valueOf), obj.int("interval"), obj.long("anchorEpochDay"), obj.nullLong("untilEpochDay"))
    }
    @Suppress("UNCHECKED_CAST") private fun objectValue(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: throw IllegalArgumentException("Expected backup object.")
    private fun Map<String, Any?>.fields(vararg expected: String) { require(keys == expected.toSet()) { "Missing or unsupported backup fields." } }
    private fun Map<String, Any?>.string(key: String): String = get(key) as? String ?: throw IllegalArgumentException("Invalid text field.")
    private fun Map<String, Any?>.long(key: String): Long = get(key) as? Long ?: throw IllegalArgumentException("Invalid integer field.")
    private fun Map<String, Any?>.bool(key: String): Boolean = get(key) as? Boolean ?: throw IllegalArgumentException("Invalid boolean field.")
    private fun Map<String, Any?>.nullString(key: String): String? = if (get(key) == null) null else string(key)
    private fun Map<String, Any?>.nullLong(key: String): Long? = if (get(key) == null) null else long(key)
    private fun exactInt(value: Long): Int { require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer outside supported range." }; return value.toInt() }
    private fun Map<String, Any?>.int(key: String): Int = exactInt(long(key))
    private fun Map<String, Any?>.obj(key: String): Map<String, Any?> = objectValue(get(key))
    private fun <T> Map<String, Any?>.rows(key: String, fields: String, decode: (Map<String, Any?>) -> T): List<T> =
        (get(key) as? List<*> ?: throw IllegalArgumentException("Missing backup section.")).map { objectValue(it).also { row -> row.fields(*fields.split(',').toTypedArray()) }.let(decode) }
}
