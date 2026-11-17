package com.jevon.personalos.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import androidx.room.withTransaction

data class MoneyFilter(val month: YearMonth = YearMonth.now(), val kind: String = "", val account: String = "", val category: String = "", val currency: String = "", val search: String = "", val page: Int = 0)
sealed interface MoneyState {
    data object Loading : MoneyState
    data class Ready(val snapshot: MoneySnapshot) : MoneyState
    data object Error : MoneyState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyViewModel @JvmOverloads constructor(app: Application, private val saved: SavedStateHandle,
    private val db: PersonalDatabase = (app as PersonalOsApplication).database) : AndroidViewModel(app) {
    private val revision = MutableStateFlow(0)
    val filter = MutableStateFlow(MoneyFilter(
        month = saved.get<String>("money-month")?.let(YearMonth::parse) ?: YearMonth.now(),
        kind = saved["money-kind"] ?: "", account = saved["money-account"] ?: "", category = saved["money-category"] ?: "",
        currency = saved["money-currency"] ?: "", search = saved["money-search"] ?: "", page = saved["money-page"] ?: 0))
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val testReview = db.invalidationTracker.createFlow("accounts", "entries", "categories", "budgets", "subscriptions", "subscription_payments").map {
        db.withTransaction {
            val dao = db.backupDao()
            reviewTestRecords(dao.accounts(), dao.entries(), dao.categories(), dao.budgets(), dao.subscriptions(), dao.payments())
        }
    }.map<List<TestRecordCandidate>, Result<List<TestRecordCandidate>>> { Result.success(it) }
        .catch { emit(Result.failure(it)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)
    val state: StateFlow<MoneyState> = combine(filter, revision) { f, _ -> f }.flatMapLatest { f ->
        db.observeMoney(f.month, f.kind, f.account, f.category, f.currency, f.search, f.page)
            .map<MoneySnapshot, MoneyState> { MoneyState.Ready(it) }
            .catch { emit(MoneyState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyState.Loading)

    init { write { db.personalDao().ensureDefaultCategories() } }
    fun setFilter(value: MoneyFilter) {
        filter.value = value
        saved["money-month"] = value.month.toString(); saved["money-kind"] = value.kind
        saved["money-account"] = value.account; saved["money-category"] = value.category
        saved["money-currency"] = value.currency; saved["money-search"] = value.search
        saved["money-page"] = value.page
    }
    fun retry() { revision.value++ }
    fun clearError() { error.value = null }
    fun write(done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { block(); done() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "Check the entered values" }
            catch (_: ArithmeticException) { error.value = "The resulting total is outside the supported monetary range. Nothing was saved." }
            catch (_: Exception) { error.value = "Could not save this change. Check the values and try again; your previous data is preserved." }
            finally { busy.value = false }
        }
    }
    fun account(value: AccountEntity, create: Boolean, done: () -> Unit) = write(done) { db.personalDao().saveAccount(value, create) }
    fun lastEntryAccount(kind: EntryKind): String = saved["entry-account-${kind.name}"] ?: ""
    fun lastEntryCategory(kind: EntryKind): String = saved["entry-category-${kind.name}"] ?: ""
    fun entry(value: EntryEntity, create: Boolean, done: () -> Unit) = write(done) {
        db.personalDao().saveEntry(value, create)
        if (create && value.kind != EntryKind.TRANSFER) {
            saved["entry-account-${value.kind.name}"] = if (value.kind == EntryKind.INCOME) value.toAccountId else value.fromAccountId
            saved["entry-category-${value.kind.name}"] = value.categoryId
        }
    }
    fun deleteEntry(id: String, done: () -> Unit) = write(done) { db.personalDao().deleteEntry(id) }
    fun category(value: CategoryEntity, create: Boolean, done: () -> Unit) = write(done) { db.personalDao().saveCategory(value, create) }
    fun budget(value: BudgetEntity, create: Boolean, done: () -> Unit) = write(done) { db.personalDao().saveBudget(value, create) }
    fun deleteBudget(id: String, done: () -> Unit) = write(done) { db.personalDao().deleteBudget(id) }
}
