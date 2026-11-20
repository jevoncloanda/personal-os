package com.jevon.personalos.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jevon.personalos.PersonalOsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal val dashboardRefreshKey = longPreferencesKey("dashboard_refresh")

internal object DashboardRefresh {
    @OptIn(FlowPreview::class)
    fun observe(app: PersonalOsApplication, scope: CoroutineScope) {
        // Persisted periodic work recovers after process death and refreshes time-sensitive items.
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "dashboard-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DashboardRefreshWorker>(30, TimeUnit.MINUTES).build(),
        )
        scope.launch {
            merge(
                app.database.invalidationTracker.createFlow(
                    "entries", "budgets", "accounts", "categories", "events", "reminders",
                    "reminder_completions", "subscriptions", "subscription_payments",
                    emitInitialState = false,
                ).map { Unit },
                app.settings.settings.drop(1).map { Unit },
            ).debounce(250).collect { request(app) }
        }
    }

    fun request(context: Context) {
        // Queue behind a running refresh so an in-flight query cannot swallow a later write.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "dashboard-data-refresh", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DashboardRefreshWorker>().build(),
        )
    }

    fun scheduleMidnight(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "dashboard-midnight-${next.toLocalDate()}", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DashboardRefreshWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}

class DashboardRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val manager = GlanceAppWidgetManager(applicationContext)
        val widgets = listOf(BudgetTrackerWidget(), TodayOverviewWidget(), UpcomingScheduleWidget())
        for (widget in widgets) {
            for (id in manager.getGlanceIds(widget.javaClass)) {
                updateAppWidgetState(applicationContext, id) {
                    it[dashboardRefreshKey] = (it[dashboardRefreshKey] ?: 0L) + 1L
                }
                widget.update(applicationContext, id)
            }
        }
        DashboardRefresh.scheduleMidnight(applicationContext)
        Result.success()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.retry()
    }
}

class DashboardTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            )) DashboardRefresh.request(context)
    }
}
