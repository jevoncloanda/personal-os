package com.jevon.personalos

import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

/** Read-only personal audit. No restore and no entity write is performed. */
class PersonalDataAuditTest {
    @Test fun upgradedPersonalRecordsHaveValidReferencesAndPortableMemoryRoundTrip()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val app=context.applicationContext as PersonalOsApplication
        val before=BackupStore(app.database).snapshot()
        val decoded=BackupCodec.decode(ByteArrayInputStream(BackupCodec.encode(before,"read-only-audit",Instant.EPOCH))).data
        assertEquals(before,decoded)
        app.database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        val baseline=File(context.getExternalFilesDir(null),"review/phase-2/personal-table-counts.txt")
        if(baseline.exists())baseline.readLines().forEach{line->val key=line.substringBefore(':');val count=line.substringAfter(':').trim().toIntOrNull();if(count!=null)assertEquals("Prior local count: $key",count,before.counts[key])}
        val folder=File(context.getExternalFilesDir(null),"review/phase-3").apply{mkdirs()}
        File(folder,"personal-audit.txt").writeText("Read-only FK and complete in-memory backup round-trip passed.\nPrior local counts compared where available; no before/after UI semantic audit claimed.\n"+before.counts.entries.joinToString("\n"){"${it.key}: ${it.value}"})
    }
}
