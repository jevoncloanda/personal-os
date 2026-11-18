package com.jevon.personalos.widget

import com.jevon.personalos.data.AccountEntity
import com.jevon.personalos.data.CategoryEntity
import com.jevon.personalos.data.EntryEntity
import com.jevon.personalos.data.EntryKind
import com.jevon.personalos.data.parseMoney
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class QuickExpenseTest {
    private val account = AccountEntity("a", "Cash", "IDR")
    private val category = CategoryEntity("c", "Transport", "EXPENSE")
    private val preset = ExpensePreset("p", "Ride", 250000, "IDR", "a", "c")
    private val entry = preset.entry("entry", LocalDateTime.of(2026, 11, 18, 9, 30))

    @Test fun validatesPositiveAmountAndExplicitReferences() {
        preset.validate(account, category)
        listOf(
            preset.copy(name = ""), preset.copy(amountMinor = 0), preset.copy(amountMinor = -1),
            preset.copy(accountId = "other"), preset.copy(categoryId = "other"),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid.validate(account, category) }
        }
        assertThrows(IllegalArgumentException::class.java) { preset.validate(null, category) }
        assertThrows(IllegalArgumentException::class.java) { preset.validate(account, null) }
    }

    @Test fun rejectsArchivedWrongKindAndChangedCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            preset.validate(account.copy(archived = true), category)
        }
        assertThrows(IllegalArgumentException::class.java) {
            preset.validate(account, category.copy(archived = true))
        }
        assertThrows(IllegalArgumentException::class.java) {
            preset.validate(account, category.copy(kind = "INCOME"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            preset.validate(account.copy(currencyCode = "USD"), category)
        }
        preset.validate(account, category.copy(kind = null))
    }

    @Test fun entryRetainsCurrencyNoteAndExactMoneyRepresentation() {
        val value = preset.copy(note = "A note", amountMinor = parseMoney("2500.50", "IDR"))
            .entry("id", LocalDateTime.of(2026, 11, 18, 9, 30))
        assertEquals(250050, value.amountMinor)
        assertEquals("IDR", value.currencyCode)
        assertEquals(EntryKind.EXPENSE, value.kind)
        assertEquals("a", value.fromAccountId)
        assertEquals("c", value.categoryId)
        assertEquals("A note", value.note)
        assertEquals(570, value.minuteOfDay)
        assertNull(value.toAccountId)
    }

    @Test fun guardSuppressesOnlyTheShortCompletedAttemptWindow() {
        val submission = ExpenseSubmission(entry, 1000, completed = true)
        assertTrue(submission.suppressTap(1000))
        assertTrue(submission.suppressTap(1000 + ACCIDENTAL_TAP_MILLIS - 1))
        assertFalse(submission.suppressTap(1000 + ACCIDENTAL_TAP_MILLIS))
        assertFalse(submission.suppressTap(999))
        assertFalse(submission.copy(completed = false).suppressTap(1000))
        assertNotEquals(preset.entry().id, preset.entry().id)
    }

    @Test fun interruptedCommittedAttemptRecoversWithoutAnotherWrite() = runBlocking {
        var writes = 0
        ExpenseSubmission(entry, 0).persist({ entry }) { writes++ }
        assertEquals(0, writes)
    }

    @Test fun newAttemptWritesTheExactFinancialEntryAndPropagatesFailures() = runBlocking {
        var saved: EntryEntity? = null
        val submission = ExpenseSubmission(entry, 0)
        submission.persist({ null }) { saved = it }
        assertEquals(entry, saved)
        try {
            submission.persist({ null }) { throw IllegalStateException("Room failure") }
            fail("Persistence failure must propagate")
        } catch (_: IllegalStateException) {
            assertFalse(submission.completed)
        }
    }

    @Test fun changedRecoveredEntryNeverOverwritesOrReportsSuccess() = runBlocking {
        try {
            ExpenseSubmission(entry, 0).persist({ entry.copy(note = "edited") }) {
                fail("Must not overwrite existing transaction")
            }
            fail("Changed entry must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun undoDeletesOnlyTheSpecificUnchangedEntry() = runBlocking {
        var deleted: String? = null
        ExpenseFeedback("Recorded", 100, entry).undo(entry.id, { entry }) { deleted = it }
        assertEquals(entry.id, deleted)
        try {
            ExpenseFeedback("Recorded", 100, entry).undo("unrelated", { entry }) {
                fail("Must not delete another transaction")
            }
            fail("Wrong ID must fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun undoMissingIsHarmlessAndEditedIsRejected() = runBlocking {
        val feedback = ExpenseFeedback("Recorded", 100, entry)
        feedback.undo(entry.id, { null }) { fail("Already deleted") }
        try {
            feedback.undo(entry.id, { entry.copy(amountMinor = 1) }) { fail("Edited transaction") }
            fail("Changed expense must require review")
        } catch (_: IllegalArgumentException) { }
    }
}
