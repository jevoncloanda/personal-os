package com.jevon.personalos.backup

import androidx.room.withTransaction
import com.jevon.personalos.data.PersonalDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class BackupStore(private val database: PersonalDatabase) {
    suspend fun snapshot(): BackupData = database.withTransaction {
        val dao = database.backupDao()
        require(dao.recordCount() <= MAX_RECORDS && dao.textBytes() + dao.subscriptionTextBytes() + dao.scheduleTextBytes() <= MAX_BACKUP_BYTES) { "Data exceeds the backup limits (20,000 records / 8 MiB). Nothing was changed." }
        BackupData(dao.accounts(), dao.categories(), dao.entries(), dao.budgets(), dao.events(), dao.reminders(), dao.subscriptions(), dao.payments(), dao.library(),dao.completions(),dao.alerts())
    }

    suspend fun replace(prepared: PreparedBackup) {
        currentCoroutineContext().ensureActive()
        prepared.data.validate()
        database.withTransaction {
            currentCoroutineContext().ensureActive()
            val dao = database.backupDao()
            dao.clearAlertRuntime(); dao.clearAlerts(); dao.clearCompletions(); dao.clearPayments(); dao.clearEntries(); dao.clearBudgets(); dao.clearSubscriptions()
            dao.clearAccounts(); dao.clearCategories(); dao.clearEvents(); dao.clearReminders(); dao.clearLibrary()
            val data = prepared.data
            dao.accounts(data.accounts); dao.categories(data.categories); dao.subscriptions(data.subscriptions)
            dao.entries(data.entries); dao.budgets(data.budgets); dao.events(data.events); dao.reminders(data.reminders)
            dao.library(data.library); dao.payments(data.payments)
            dao.completions(data.completions); dao.alerts(data.alerts)
            currentCoroutineContext().ensureActive()
            // Room invalidates subscribed projections only after this transaction commits.
        }
    }
}
