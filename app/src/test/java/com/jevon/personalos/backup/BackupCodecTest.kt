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
        rejected(mutate { it["version"] = 4L }); rejected(mutate { it["schemaVersion"] = 5L })
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
