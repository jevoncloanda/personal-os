package com.jevon.personalos

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.BudgetEntity
import com.jevon.personalos.data.CategoryEntity
import com.jevon.personalos.data.EventEntity
import com.jevon.personalos.data.ReminderEntity
import com.jevon.personalos.today.formatMoney
import com.jevon.personalos.widget.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Opt-in, short Samsung smoke test. Only its own temporary records are removed. */
class DashboardWidgetSmokeTest {
    @Test
    fun addResizeNavigateAndRefreshDashboards() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val visualOnly = InstrumentationRegistry.getArguments().getString("visualOnly") == "true"
        val app = instrumentation.targetContext.applicationContext as PersonalOsApplication
        val device = UiDevice.getInstance(instrumentation)
        val manager = AppWidgetManager.getInstance(app)
        val before = BackupStore(app.database).snapshot()
        val dao = app.database.personalDao()
        val token = "dashboard-smoke-${UUID.randomUUID()}"
        val now = ZonedDateTime.now()
        val month = YearMonth.from(now)
        val reminder = ReminderEntity(token, "Widget refresh check",
            now.plusMinutes(10).toInstant().toEpochMilli(), zoneId = now.zone.id)
        val folder = File(app.getExternalFilesDir(null),
            if (visualOnly) "review/phase-5.2a" else "review/phase-5.2").apply { mkdirs() }
        val report = StringBuilder()
        val oldIdle = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 100
        @Suppress("DEPRECATION")
        val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Poros:dashboard-smoke")
        device.wakeUp()
        wakeLock.acquire(600_000)

        val selectors = mutableMapOf<String, BySelector>()
        fun content(description: String) = requireNotNull(device.wait(
            Until.findObject(selectors.getValue(description)), 10_000,
        )) { "Missing widget $description" }
        fun node(description: String): androidx.test.uiautomator.UiObject2 {
            var candidate = content(description)
            while (!candidate.className.contains("AppWidgetHostView")) {
                candidate = candidate.parent ?: break
            }
            return candidate
        }
        suspend fun findWidget(title: String) {
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(500)
            if (device.wait(Until.hasObject(selectors.getValue(title)), 500)) return
            // Start from the leftmost page, then locate the newly pinned instance.
            repeat(6) { device.swipe(150, 1400, 950, 1400, 60) }
            repeat(7) {
                if (device.wait(Until.hasObject(selectors.getValue(title)), 500)) return
                device.swipe(950, 1400, 150, 1400, 60)
            }
            node(title)
        }
        suspend fun resize(title: String, id: Int, columns: Int, rows: Int) {
            var options = manager.getAppWidgetOptions(id)
            var bounds = node(title).visibleBounds
            val currentColumns = options.getInt("semAppWidgetColumnSpan")
            if (currentColumns != columns) {
                content(title).longClick()
                device.swipe(bounds.right - 2, bounds.centerY(),
                    bounds.left + bounds.width() * columns / currentColumns, bounds.centerY(), 70)
                device.pressBack()
                delay(600)
            }
            options = manager.getAppWidgetOptions(id)
            val currentRows = options.getInt("semAppWidgetRowSpan")
            bounds = node(title).visibleBounds
            if (currentRows != rows) {
                content(title).longClick()
                device.swipe(bounds.centerX(), bounds.bottom - 2, bounds.centerX(),
                    bounds.top + bounds.height() * rows / currentRows, 70)
                device.pressBack()
                delay(600)
            }
            options = manager.getAppWidgetOptions(id)
            assertEquals("$title columns", columns, options.getInt("semAppWidgetColumnSpan"))
            assertEquals("$title compact rows", rows, options.getInt("semAppWidgetRowSpan"))
            report.appendLine("$title columns x rows=" +
                "${options.getInt("semAppWidgetColumnSpan")} x ${options.getInt("semAppWidgetRowSpan")}")
        }

        try {
            dao.saveCategory(CategoryEntity(token, "Widget smoke category", "EXPENSE"), true)
            dao.saveBudget(BudgetEntity(token, "Widget smoke budget", "IDR", 10_000_000,
                month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay(), token), true)
            dao.saveReminder(reminder, true)
            val receivers = listOf(
                Triple(BudgetTrackerReceiver::class.java, DashboardKind.BUDGET, 1),
                Triple(TodayOverviewReceiver::class.java, DashboardKind.TODAY, 2),
                Triple(UpcomingScheduleReceiver::class.java, DashboardKind.SCHEDULE, 2),
            )
            for ((receiver, kind, rows) in receivers) {
                val component = ComponentName(app, receiver)
                var ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) {
                    app.startActivity(Intent(app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    delay(800)
                    assertTrue(manager.requestPinAppWidget(component, null, null))
                    requireNotNull(device.wait(Until.findObject(By.text("Add")), 8_000)).click()
                    delay(1_000)
                    ids = manager.getAppWidgetIds(component)
                }
                assertTrue(ids.isNotEmpty())
                val expected = loadDashboard(app, kind, ZonedDateTime.now())
                selectors[kind.title] = By.text(when (kind) {
                    DashboardKind.BUDGET -> expected.budget?.budget?.name ?: "Budget Tracker"
                    DashboardKind.TODAY -> expected.today.date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
                    DashboardKind.SCHEDULE -> "Upcoming"
                })
                findWidget(kind.title)
                if (!visualOnly && kind == DashboardKind.BUDGET && expected.budget != null) {
                    assertTrue(device.wait(Until.hasObject(By.text(expected.budget!!.budget.name)), 8_000))
                    assertTrue(device.hasObject(By.text(formatMoney(expected.budget!!.remainingMinor,
                        expected.budget!!.budget.currencyCode))))
                }
                if (!visualOnly && kind == DashboardKind.SCHEDULE) {
                    assertTrue(device.wait(Until.hasObject(By.text(reminder.title)), 8_000))
                    dao.saveReminder(reminder.copy(title = "Widget refresh verified"), false)
                    assertTrue("Database write must refresh the visible widget",
                        device.wait(Until.hasObject(By.text("Widget refresh verified")), 20_000))
                    report.appendLine("Schedule edit refreshed automatically.")
                }
                resize(kind.title, ids.last(), 2, rows)
                delay(800)
                device.takeScreenshot(File(folder, "${kind.name.lowercase()}-compact.png"))
                if (visualOnly) {
                    when (kind) {
                        DashboardKind.BUDGET -> {
                            // Make temporary room using only the placed Today test widget.
                            val dateSelector = By.text(now.toLocalDate()
                                .format(DateTimeFormatter.ofPattern("EEE, d MMM")))
                            var today = requireNotNull(device.findObject(dateSelector))
                            while (!today.className.contains("AppWidgetHostView")) {
                                today = today.parent ?: break
                            }
                            val original = today.visibleBounds
                            device.drag(original.centerX(), original.centerY(),
                                device.displayWidth - original.centerX(), original.centerY(), 80)
                            delay(500)
                            try {
                                resize(kind.title, ids.last(), 2, 2)
                                delay(600)
                                device.takeScreenshot(File(folder, "budget-larger.png"))
                            } finally {
                                resize(kind.title, ids.last(), 2, 1)
                                device.drag(device.displayWidth - original.centerX(), original.centerY(),
                                    original.centerX(), original.centerY(), 80)
                                delay(500)
                            }
                        }
                        DashboardKind.TODAY -> {
                            resize(kind.title, ids.last(), 4, 2)
                            delay(600)
                            device.takeScreenshot(File(folder, "today-larger.png"))
                            resize(kind.title, ids.last(), 2, 2)
                        }
                        DashboardKind.SCHEDULE -> {
                            dao.saveEvent(EventEntity("$token-event", "Team catch-up",
                                now.plusHours(1).toInstant().toEpochMilli(),
                                now.plusHours(2).toInstant().toEpochMilli(), now.zone.id), true)
                            dao.saveReminder(reminder.copy(id = "$token-later", title = "Review tomorrow",
                                dueAtMillis = now.plusDays(1).toInstant().toEpochMilli()), true)
                            resize(kind.title, ids.last(), 3, 4)
                            assertTrue(device.wait(Until.hasObject(By.text("Team catch-up")), 10_000))
                            delay(600)
                            device.takeScreenshot(File(folder, "schedule-larger.png"))
                            resize(kind.title, ids.last(), 2, 2)
                        }
                    }
                    continue
                }
                val bounds = node(kind.title).visibleBounds
                device.click(bounds.left + 20, bounds.top + 20)
                assertTrue(device.wait(Until.hasObject(By.pkg(app.packageName)), 8_000))
                val destination = when (kind) {
                    DashboardKind.BUDGET -> "Budgets"
                    DashboardKind.TODAY -> "TODAY"
                    DashboardKind.SCHEDULE -> "Schedule"
                }
                assertTrue("Tap opens $destination",
                    device.wait(Until.hasObject(By.text(destination)), 8_000))
                report.appendLine("${kind.title} added, data checked, compact capture and navigation passed.")
            }
        } catch (error: Throwable) {
            device.takeScreenshot(File(folder, "failure.png"))
            device.dumpWindowHierarchy(File(folder, "failure.xml"))
            throw error
        } finally {
            try {
                dao.deleteReminder(token)
                dao.deleteReminder("$token-later")
                dao.deleteEvent("$token-event")
                dao.deleteBudget(token)
                app.database.withTransaction {
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM categories WHERE id = ?", arrayOf(token))
                }
                DashboardRefresh.request(app)
                assertEquals(before, BackupStore(app.database).snapshot())
                report.appendLine("Exact personal snapshot equality passed. Temporary records removed.")
                delay(1_000)
                if (visualOnly) {
                    selectors[DashboardKind.BUDGET.title] = By.text("Set up a budget")
                    findWidget(DashboardKind.BUDGET.title)
                    delay(600)
                    device.takeScreenshot(File(folder, "empty-widgets.png"))
                }
            } finally {
                File(folder, "smoke-results.txt").writeText(report.toString())
                Configurator.getInstance().waitForIdleTimeout = oldIdle
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }
}
