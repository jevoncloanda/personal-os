package com.jevon.personalos.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jevon.personalos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class SubscriptionSnapshot(
    val subscriptions: List<SubscriptionEntity>, val payments: List<SubscriptionPaymentEntity>,
    val expenses: List<EntryEntity>, val accounts: List<AccountBalance>, val categories: List<CategoryEntity>,
)

class SubscriptionViewModel(private val database: PersonalDatabase) : ViewModel() {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private val retry = MutableStateFlow(0)
    val today = flow { while (true) { emit(LocalDate.now()); delay(30_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SubscriptionSnapshot?> = retry.flatMapLatest {
        database.invalidationTracker.createFlow("subscriptions", "subscription_payments", "entries", "accounts", "categories").map {
            database.withTransaction<SubscriptionSnapshot?> {
                val dao = database.personalDao()
                SubscriptionSnapshot(dao.subscriptions(), dao.subscriptionPayments(), dao.paymentExpenses(), dao.balances(), dao.categories())
            }
        }.catch { error.value = "Subscriptions couldn't load. Try again; your records have not changed."; emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun retry() { error.value = null; retry.value++ }
    fun clearError() { error.value = null }
    fun save(value: SubscriptionEntity, create: Boolean, done: () -> Unit) = write(done) { database.personalDao().saveSubscription(value, create) }
    fun active(id: String, active: Boolean) = write({}) { database.personalDao().setSubscriptionActive(id, active) }
    fun pay(id: String, due: Long, expense: EntryEntity, done: () -> Unit) = write(done) { database.personalDao().recordSubscriptionPayment(id, due, expense) }
    fun correct(id: String, expense: EntryEntity, done: () -> Unit) = write(done) { database.personalDao().correctSubscriptionPayment(id, expense) }
    fun undo(id: String, done: () -> Unit) = write(done) { database.personalDao().undoSubscriptionPayment(id) }
    private fun write(done: () -> Unit, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { action(); done() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "Check the subscription fields" }
            catch (_: Exception) { error.value = "Couldn't save. Check the records and try again; no partial payment was saved." }
            finally { busy.value = false }
        }
    }
}
