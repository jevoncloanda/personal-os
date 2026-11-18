package com.jevon.personalos.widget

import androidx.room.withTransaction
import com.jevon.personalos.data.PersonalDatabase
import com.jevon.personalos.data.moneyInput
import kotlinx.coroutines.sync.withLock

class QuickExpenseRecorder(
    private val database: PersonalDatabase,
    private val store: QuickExpenseStore,
) {
    suspend fun recordPreset(
        widgetId: Int,
        presetId: String,
        confirmed: Boolean = false,
        requestedAt: Long = System.currentTimeMillis(),
        expectedRevision: String? = null,
    ) =
        store.mutex.withLock {
            val state = store.read()
            require(presetId in state.widgets[widgetId.toString()].orEmpty()) {
                "Preset removed from this widget. Open Configure"
            }
            val preset = requireNotNull(state.presets.find { it.id == presetId }) {
                "Preset removed. Open Configure"
            }
            require(!preset.confirm || confirmed) { "Open this preset to confirm" }
            require(expectedRevision == null || expectedRevision == preset.revision) {
                "Preset changed. Wait for the widget to refresh, then tap again"
            }
            recordLocked(widgetId, "preset-$presetId", preset, state, requestedAt)
        }

    suspend fun recordCustom(widgetId: Int, requestId: String, preset: ExpensePreset) =
        store.mutex.withLock {
            val state = store.read()
            // A custom draft's saved request ID survives rotation/recreation and is single-submit.
            val key = "custom-$requestId"
            val old = state.submissions[key]
            if (old?.completed == true) return@withLock old.entry
            if (old == null) {
                val existing = database.personalDao().findEntry(requestId)
                if (existing != null) return@withLock existing
            }
            recordLocked(widgetId, key, preset, state)
        }

    private suspend fun recordLocked(
        widgetId: Int,
        key: String,
        preset: ExpensePreset,
        initial: QuickExpenseState,
        requestedAt: Long = System.currentTimeMillis(),
    ): com.jevon.personalos.data.EntryEntity {
        val now = System.currentTimeMillis()
        val previous = initial.submissions[key]
        val queuedDuringPrevious = previous?.completed == true &&
            requestedAt <= previous.lastTapAt && now >= previous.lastTapAt
        if (previous?.suppressTap(requestedAt) == true || queuedDuringPrevious) {
            requireNotNull(previous)
            require(!previous.undone) { "Expense was undone. Tap again shortly" }
            require(database.personalDao().findEntry(previous.entry.id) == previous.entry) {
                "Expense changed or removed. Review Money"
            }
            val feedback = successFeedback(previous.entry, now)
            store.write(initial.copy(
                submissions = initial.submissions + (key to previous.copy(
                    lastTapAt = maxOf(previous.lastTapAt, requestedAt),
                )),
                feedback = initial.feedback + (widgetId.toString() to feedback),
            ))
            return previous.entry
        }
        val submission = previous?.takeUnless { it.completed }
            ?: ExpenseSubmission(
                if (key.startsWith("custom-")) preset.entry(id = key.removePrefix("custom-"))
                else preset.entry(),
                now,
            )
        val pending = initial.copy(submissions = initial.submissions + (key to submission))
        // Persist the exact attempt before Room. After process death, retry/recover the same ID.
        store.write(pending)
        val dao = database.personalDao()
        try {
            database.withTransaction {
                submission.persist(dao::findEntry) { entry ->
                    preset.validate(
                        dao.balances().find { it.account.id == preset.accountId }?.account,
                        dao.categories().find { it.id == preset.categoryId },
                    )
                    require(entry.amountMinor == preset.amountMinor &&
                        entry.currencyCode == preset.currencyCode &&
                        entry.fromAccountId == preset.accountId &&
                        entry.categoryId == preset.categoryId && entry.note == preset.note) {
                        "Preset changed during an interrupted attempt. Tap again to record its new values"
                    }
                    dao.recordEntry(entry)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            // Room rolled the failed transaction back. Let a corrected preset start a fresh attempt.
            store.write(initial.copy(submissions = initial.submissions - key))
            throw error
        }
        val entry = submission.entry
        val completedAt = System.currentTimeMillis()
        store.write(pending.copy(
            submissions = pending.submissions.filterValues {
                !it.completed || completedAt - it.lastTapAt < 86_400_000L
            } + (key to submission.copy(completed = true, lastTapAt = completedAt)),
            feedback = pending.feedback + (widgetId.toString() to successFeedback(entry, completedAt)),
            lastAccount = entry.fromAccountId.orEmpty(),
            lastCategory = entry.categoryId.orEmpty(),
        ))
        return entry
    }

    suspend fun undo(widgetId: Int, entryId: String) = store.mutex.withLock {
        val state = store.read()
        val receipt = state.feedback[widgetId.toString()]
        require(receipt?.entry?.id == entryId) { "Undo expired. Review the expense in Money" }
        database.withTransaction {
            val dao = database.personalDao()
            receipt.undo(entryId, dao::findEntry, dao::deleteEntry)
        }
        store.write(state.copy(
            submissions = state.submissions.mapValues { (_, submission) ->
                if (submission.entry.id == entryId) submission.copy(undone = true) else submission
            },
            feedback = state.feedback + (widgetId.toString() to ExpenseFeedback(
                "Expense undone", System.currentTimeMillis() + FEEDBACK_MILLIS,
            )),
        ))
    }

    private fun successFeedback(entry: com.jevon.personalos.data.EntryEntity, now: Long) =
        ExpenseFeedback(
            "Recorded ${moneyInput(entry.amountMinor, entry.currencyCode)} ${entry.currencyCode}",
            now + FEEDBACK_MILLIS,
            entry,
        )
}
