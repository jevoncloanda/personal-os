package com.jevon.personalos.widget

import com.jevon.personalos.data.AccountEntity
import com.jevon.personalos.data.CategoryEntity
import com.jevon.personalos.data.EntryEntity
import com.jevon.personalos.data.EntryKind
import com.jevon.personalos.data.currencyDigits
import java.time.LocalDateTime
import java.util.UUID
import com.google.gson.annotations.SerializedName

data class QuickExpenseCatalog(
    val state: QuickExpenseState,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
)

data class ExpensePreset(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: String,
    val categoryId: String,
    val note: String = "",
    val confirm: Boolean = false,
    val revision: String = UUID.randomUUID().toString(),
) {
    fun validate(account: AccountEntity?, category: CategoryEntity?) {
        require(id.isNotBlank() && name.isNotBlank()) { "Enter a preset name" }
        require(amountMinor > 0) { "Amount must be greater than zero" }
        currencyDigits(currencyCode)
        require(account != null && account.id == accountId && !account.archived) {
            "Choose an active account in Configure"
        }
        require(account.currencyCode == currencyCode) { "Account currency changed. Edit this preset" }
        require(category != null && category.id == categoryId && !category.archived &&
            (category.kind == null || category.kind == "EXPENSE")) {
            "Choose an active expense category in Configure"
        }
    }

    fun entry(id: String = UUID.randomUUID().toString(), now: LocalDateTime = LocalDateTime.now()) =
        EntryEntity(
            id = id,
            kind = EntryKind.EXPENSE,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            occurredOnEpochDay = now.toLocalDate().toEpochDay(),
            fromAccountId = accountId,
            categoryId = categoryId,
            note = note,
            minuteOfDay = now.hour * 60 + now.minute,
        )
}

const val ACCIDENTAL_TAP_MILLIS = 2_000L
const val FEEDBACK_MILLIS = 30_000L

data class ExpenseSubmission(
    val entry: EntryEntity,
    @SerializedName(value = "lastTapAt", alternate = ["startedAt"])
    val lastTapAt: Long,
    val completed: Boolean = false,
    val undone: Boolean = false,
) {
    fun suppressTap(now: Long): Boolean =
        completed && now >= lastTapAt && now - lastTapAt < ACCIDENTAL_TAP_MILLIS

    suspend fun persist(
        find: suspend (String) -> EntryEntity?,
        record: suspend (EntryEntity) -> Unit,
    ) {
        val existing = find(entry.id)
        require(existing == null || existing == entry) { "Recorded expense changed. Review it in Money" }
        if (existing == null) record(entry)
    }
}

data class ExpenseFeedback(
    val message: String,
    val expiresAt: Long,
    val entry: EntryEntity? = null,
) {
    suspend fun undo(
        entryId: String,
        find: suspend (String) -> EntryEntity?,
        delete: suspend (String) -> Unit,
    ) {
        require(entry?.id == entryId) { "Undo expired. Review the expense in Money" }
        val current = find(entryId)
        require(current == null || current == entry) { "Expense was edited. Review it in Money" }
        if (current != null) delete(entryId)
    }
}

data class QuickExpenseState(
    val presets: List<ExpensePreset> = emptyList(),
    val widgets: Map<String, List<String>> = emptyMap(),
    val submissions: Map<String, ExpenseSubmission> = emptyMap(),
    val feedback: Map<String, ExpenseFeedback> = emptyMap(),
    val lastAccount: String = "",
    val lastCategory: String = "",
)
