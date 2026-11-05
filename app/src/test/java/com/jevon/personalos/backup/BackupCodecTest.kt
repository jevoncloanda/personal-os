package com.jevon.personalos.backup

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZonedDateTime

class BackupCodecTest {
    private val original = backupReviewData(ZonedDateTime.parse("2026-10-26T09:00:00+07:00[Asia/Jakarta]"))
    private val created = Instant.parse("2026-10-26T02:00:00Z")
    private fun bytes(data: BackupData = original) = BackupCodec.encode(data, "test-1.7", created)
    private fun decode(bytes: ByteArray) = BackupCodec.decode(ByteArrayInputStream(bytes))
    @Suppress("UNCHECKED_CAST") private fun envelope(): MutableMap<String, Any?> = (BackupJson.read(ByteArrayInputStream(bytes())) as Map<String, Any?>).toMutableMap()
    @Suppress("UNCHECKED_CAST") private fun mutate(edit: (MutableMap<String, Any?>) -> Unit): ByteArray {
        val envelope = envelope(); edit(envelope)
        envelope["integrity"] = mapOf("algorithm" to "SHA-256", "digest" to BackupJson.hash(envelope - "integrity"))
        return BackupJson.canonical(envelope).toByteArray()
    }
    private fun rejected(bytes: ByteArray) { assertThrows(Exception::class.java) { decode(bytes) } }
    @Suppress("UNCHECKED_CAST")
    private fun stripNewLibraryFields(data: MutableMap<String, Any?>) {
        val legacy = setOf("id", "title", "kind", "status", "progress", "total", "updatedAtMillis")
        data["libraryItems"] = (data["libraryItems"] as List<Map<String, Any?>>).map { row -> row.filterKeys { it in legacy } }
    }

    @Test fun emptyRoundTripHasEveryRequiredSection() {
        val empty = decode(bytes(BackupData()))
        assertEquals(BackupData(), empty.data); assertEquals(created.toString(), empty.createdAt); assertEquals(0, empty.data.counts.values.sum())
    }
    @Test fun completeRoundTripPreservesEveryFieldAndExactMoney() {
        val restored = decode(bytes()).data
        assertEquals(original.accounts.toSet(), restored.accounts.toSet()); assertEquals(original.categories.toSet(), restored.categories.toSet())
        assertEquals(original.entries.toSet(), restored.entries.toSet()); assertEquals(original.budgets.toSet(), restored.budgets.toSet())
        assertEquals(original.events, restored.events); assertEquals(original.reminders.toSet(), restored.reminders.toSet())
        assertEquals(original.subscriptions.toSet(), restored.subscriptions.toSet()); assertEquals(original.payments, restored.payments)
        assertEquals(original.library.toSet(), restored.library.toSet())
        assertEquals(setOf("IDR", "USD"), restored.accounts.map { it.currencyCode }.toSet())
        assertTrue(restored.accounts.any { it.archived }); assertTrue(restored.reminders.any { it.completedAtMillis != null })
    }
    @Test fun deterministicEncodingIgnoresRecordAndObjectKeyOrder() {
        assertArrayEquals(bytes(), bytes(original.copy(accounts = original.accounts.reversed(), entries = original.entries.reversed())))
        val reversed = envelope().entries.reversed().associate { it.key to it.value }
        assertEquals(original.entries.toSet(), decode(BackupJson.canonical(reversed).toByteArray()).data.entries.toSet())
    }
    @Test fun checksumCoversMetadataAndData() {
        rejected(String(bytes()).replace("test-1.7", "changed").toByteArray())
        rejected(String(bytes()).replace("Lunch", "Other").toByteArray())
        rejected(mutate { it["integrity"] = "wrong type" }.let { String(it).replace("SHA-256", "OTHER").toByteArray() })
    }
    @Test fun missingUnknownAndWrongTypesAreRejectedEvenWithRecomputedHash() {
        rejected(mutate { it.remove("createdAt") })
        rejected(mutate { it["extra"] = true })
        rejected(mutate { it["schemaVersion"] = "2" })
        rejected(mutate { it["data"] = emptyMap<String, Any?>() })
        rejected(mutate { it["createdAt"] = "2026-99-99T00:00:00Z" })
    }
    @Test fun futureFormatAndSchemaAreRejected() {
        rejected(mutate { it["version"] = 5L }); rejected(mutate { it["schemaVersion"] = 6L })
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun legacyV1ReadsWithExplicitDefaultsAndNewFieldsRoundTrip() {
        val legacy = mutate { root ->
            root["version"] = 1L; root["schemaVersion"] = 2L
            val data = (root["data"] as Map<String, Any?>).toMutableMap()
            data.remove("reminderCompletions"); data.remove("alertPreferences")
            data["events"] = (data["events"] as List<Map<String,Any?>>).map { it-setOf("notes","revision") }
            data["reminders"] = (data["reminders"] as List<Map<String,Any?>>).map { it-setOf("notes","zoneId","dateOnly","revision") }
            data["subscriptions"] = (data["subscriptions"] as List<Map<String, Any?>>).map { it - setOf("description", "notes", "accountId", "categoryId") }
            stripNewLibraryFields(data)
            root["data"] = data
        }
        assertEquals(original.subscriptions.toSet(), decode(legacy).data.subscriptions.toSet())
        val enriched = original.copy(subscriptions = original.subscriptions.map { it.copy(description = "Plan details", notes = "A private note", accountId = original.accounts.first { a -> a.currencyCode == it.currencyCode }.id, categoryId = original.categories.first { c -> c.kind != "INCOME" }.id) })
        assertEquals(enriched.subscriptions.toSet(), decode(bytes(enriched)).data.subscriptions.toSet())
        assertThrows(IllegalArgumentException::class.java) { bytes(enriched.copy(subscriptions = enriched.subscriptions.map { it.copy(accountId = "missing") })) }
        rejected(mutate { it["version"] = 1L; it["schemaVersion"] = 2L }) // v1 cannot silently absorb new fields
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun formatTwoDefaultsAndFormatThreeSchedulingHistoryAreComplete() {
        val legacy=mutate { root ->
            root["version"]=2L; root["schemaVersion"]=3L
            val data=(root["data"] as Map<String,Any?>).toMutableMap()
            data.remove("reminderCompletions"); data.remove("alertPreferences")
            data["events"]=(data["events"] as List<Map<String,Any?>>).map { it-setOf("notes","revision") }
            data["reminders"]=(data["reminders"] as List<Map<String,Any?>>).map { it-setOf("notes","zoneId","dateOnly","revision") }
            stripNewLibraryFields(data)
            root["data"]=data
        }
        assertEquals(original.events,decode(legacy).data.events)
        assertEquals(original.reminders.toSet(),decode(legacy).data.reminders.toSet())
        val reminder=original.reminders.first { it.dueAtMillis!=null }.let { it.copy(notes="Repeat notes",zoneId="Asia/Jakarta",dateOnly=true,revision=2,completedAtMillis=null,recurrence=Recurrence(Frequency.DAILY,1,java.time.Instant.ofEpochMilli(it.dueAtMillis!!).atZone(java.time.ZoneId.of("Asia/Jakarta")).toLocalDate().toEpochDay())) }
        val data=original.copy(events=original.events.map { it.copy(notes="Event details",revision=4) },reminders=listOf(reminder),
            completions=listOf(ReminderCompletionEntity(reminder.id,2,reminder.recurrence.anchorEpochDay,123)),
            alerts=listOf(AlertPreferenceEntity("alert",reminderId=reminder.id,leadMinutes=15,minuteOfDay=600)))
        val restored=decode(bytes(data)).data
        assertEquals(data.events,restored.events); assertEquals(data.reminders,restored.reminders)
        assertEquals(data.completions,restored.completions); assertEquals(data.alerts,restored.alerts)
        assertThrows(IllegalArgumentException::class.java) { bytes(data.copy(completions=data.completions+data.completions)) }
        assertThrows(IllegalArgumentException::class.java) { bytes(data.copy(alerts=data.alerts.map { it.copy(eventId=original.events.first().id) })) }
        assertThrows(IllegalArgumentException::class.java) { bytes(data.copy(completions=data.completions.map { it.copy(revision=3) })) }
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun legacyFormatThreeKeepsLibraryProgressAndSchedulingFields() {
        val legacy = mutate { root ->
            root["version"] = 3L; root["schemaVersion"] = 4L
            val data = (root["data"] as Map<String, Any?>).toMutableMap()
            stripNewLibraryFields(data)
            data["events"] = (data["events"] as List<Map<String, Any?>>).map { it + mapOf("notes" to "Legacy event note", "revision" to 4L) }
            data["reminders"] = (data["reminders"] as List<Map<String, Any?>>).map { it + mapOf("notes" to "Legacy reminder note", "zoneId" to "Asia/Jakarta", "revision" to 2L) }
            root["data"] = data
        }
        val restored = decode(legacy).data
        assertEquals(original.library.toSet(), restored.library.toSet())
        assertEquals(original.events.map { it.copy(notes = "Legacy event note", revision = 4L) }, restored.events)
        assertEquals(original.reminders.map { it.copy(notes = "Legacy reminder note", zoneId = "Asia/Jakarta", revision = 2L) }.toSet(), restored.reminders.toSet())
        assertEquals(original.completions, restored.completions)
        assertEquals(original.alerts, restored.alerts)
        assertTrue(restored.library.all { it.subtitle.isEmpty() && it.rating == null && it.coverPath == null && it.addedAtMillis == 0L })
    }
    @Test fun formatFourPreservesEveryPortableLibraryFieldAcrossMedia() {
        val start = java.time.LocalDate.of(2026, 10, 1).toEpochDay()
        fun item(id: String, kind: LibraryKind) = LibraryItemEntity(id, "Title $id", kind, LibraryStatus.ON_HOLD,
            updatedAtMillis = 123456789L, subtitle = "Subtitle", description = "Synopsis", releaseYear = 2025,
            rating = 4, notes = "Personal review", addedAtMillis = 123L, startedEpochDay = start,
            completedEpochDay = start + 4, favorite = true)
        val items = listOf(
            item("movie", LibraryKind.MOVIE),
            item("series", LibraryKind.SERIES).copy(progress = 12, total = 24, season = 2, episode = 4, totalSeasons = 3),
            item("game", LibraryKind.GAME).copy(platform = "PC", hoursPlayedMinutes = 735, completionPercent = 42),
            item("book", LibraryKind.BOOK).copy(progress = 160, total = 320, author = "An author", isbn = "9780140328721",
                provider = "openlibrary", providerId = "/works/OL123W"),
        )
        val source = BackupData(library = items)
        val restored = decode(bytes(source)).data
        assertEquals(items.toSet(), restored.library.toSet())
        val duplicate = items.last().copy(id = "same-provider")
        assertThrows(IllegalArgumentException::class.java) { bytes(source.copy(library = items + duplicate)) }
        assertThrows(IllegalArgumentException::class.java) { bytes(source.copy(library = listOf(items.last().copy(providerId = "invalid")))) }
        assertThrows(IllegalArgumentException::class.java) { bytes(source.copy(library = listOf(items.last().copy(rating = 6)))) }
    }
    @Test fun localArtworkPathAndBytesAreExcludedFromPortableBackup() {
        val artwork = "12345678-1234-1234-1234-123456789abc.jpg"
        val item = LibraryItemEntity("book", "Offline cover", LibraryKind.BOOK, LibraryStatus.ACTIVE,
            updatedAtMillis = 100, coverPath = artwork, notes = "Keep this review")
        val encoded = bytes(BackupData(library = listOf(item)))
        assertFalse(String(encoded, Charsets.UTF_8).contains(artwork))
        assertFalse(String(encoded, Charsets.UTF_8).contains("coverPath"))
        assertEquals(item.copy(coverPath = null), decode(encoded).data.library.single())
    }
    @Test fun largeValidLibraryExportCanBeRestoredWithinTheByteLimit() {
        // v4 has 26 values per Library item: this exceeds the old 400,000-node
        // parser limit while remaining within both advertised record/byte limits.
        val library = List(15_000) { index -> LibraryItemEntity(index.toString(), "B", LibraryKind.BOOK,
            LibraryStatus.PLANNED, updatedAtMillis = 0) }
        val encoded = bytes(BackupData(library = library))
        assertTrue(encoded.size <= MAX_BACKUP_BYTES)
        assertEquals(library.sortedBy { it.id }, decode(encoded).data.library)
    }
    @Test fun acceptedLibraryTextFitsThePortableBackupBoundary() {
        val item = LibraryItemEntity("book", "Book", LibraryKind.BOOK, LibraryStatus.PLANNED,
            updatedAtMillis = 100, notes = "n".repeat(MAX_TEXT))
        com.jevon.personalos.library.validateLibrary(item)
        assertEquals(item, decode(bytes(BackupData(library = listOf(item)))).data.library.single())
        assertThrows(IllegalArgumentException::class.java) {
            com.jevon.personalos.library.validateLibrary(item.copy(notes = "n".repeat(MAX_TEXT + 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.jevon.personalos.library.validateLibrary(item.copy(notes = "\uD800"))
        }
    }
    @Test fun duplicatesBrokenReferencesAndInvalidDomainFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { bytes(original.copy(accounts = original.accounts + original.accounts.first())) }
        assertThrows(IllegalArgumentException::class.java) { bytes(original.copy(accounts = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { bytes(original.copy(accounts = original.accounts.map { it.copy(currencyCode = "ZZZ") })) }
        assertThrows(Exception::class.java) { bytes(original.copy(events = original.events.map { it.copy(zoneId = "invalid/zone") })) }
        assertThrows(IllegalArgumentException::class.java) { bytes(original.copy(payments = original.payments + original.payments.first().copy(id = "duplicate-link"))) }
    }
    @Test fun aggregateAndBalanceOverflowAreRejected() {
        val account = AccountEntity("a", "Account", "IDR", Long.MAX_VALUE)
        val entry = EntryEntity("e", EntryKind.INCOME, 1, "IDR", 0, toAccountId = "a")
        assertThrows(IllegalArgumentException::class.java) { bytes(BackupData(accounts = listOf(account), entries = listOf(entry))) }
        assertThrows(IllegalArgumentException::class.java) { bytes(BackupData(accounts = listOf(account.copy(openingBalanceMinor = 0)), entries = listOf(entry.copy(amountMinor = Long.MAX_VALUE), entry.copy(id = "second")))) }
    }
    @Test fun strictParserRejectsDuplicateFieldsFloatsOverflowAndTruncation() {
        listOf("{\"x\":1,\"x\":2}", "{\"x\":1.0}", "{\"x\":1e2}", "{\"x\":9223372036854775808}", "{\"x\":01}", "{\"x\":true,}", "{\"x\":").forEach {
            assertThrows(Exception::class.java) { BackupJson.read(ByteArrayInputStream(it.toByteArray())) }
        }
        rejected(bytes().copyOf(bytes().size / 2))
    }
    @Test fun rejectsOversizedDeepComplexAndMalformedUtf8Input() {
        assertThrows(IllegalArgumentException::class.java) { BackupJson.read(ByteArrayInputStream(ByteArray(MAX_BACKUP_BYTES + 1) { 32 })) }
        assertThrows(Exception::class.java) { BackupJson.read(ByteArrayInputStream(("[".repeat(14) + "0" + "]".repeat(14)).toByteArray())) }
        assertThrows(Exception::class.java) { BackupJson.read(ByteArrayInputStream(byteArrayOf(0x22, 0xC3.toByte(), 0x28, 0x22))) }
        assertThrows(Exception::class.java) { BackupJson.read(ByteArrayInputStream(("\"" + "x".repeat(MAX_TEXT + 1) + "\"").toByteArray())) }
        assertThrows(Exception::class.java) { BackupJson.read(ByteArrayInputStream(("[" + List(MAX_RECORDS + 1) { "0" }.joinToString(",") + "]").toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { bytes(BackupData(accounts = listOf(AccountEntity("id", "\uD800", "IDR")))) }
    }
    @Test fun signedLongExtremesRemainExact() {
        val restored = decode(bytes(BackupData(accounts = listOf(AccountEntity("a", "Minimum", "USD", Long.MIN_VALUE), AccountEntity("b", "Maximum", "IDR", Long.MAX_VALUE))))).data
        assertEquals(Long.MIN_VALUE, restored.accounts.first().openingBalanceMinor)
        assertEquals(Long.MAX_VALUE, restored.accounts.last().openingBalanceMinor)
    }
}
