package com.jevon.personalos

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.PowerManager
import android.util.SizeF
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.AccountEntity
import com.jevon.personalos.data.CategoryEntity
import com.jevon.personalos.widget.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in Samsung launcher resize verification. Resizes the placed widget through its handles. */
class QuickExpenseResizeTest {
    @Test
    fun actualLauncherResizingPreservesSelectionsAndExactUndo() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PersonalOsApplication
        val device = UiDevice.getInstance(instrumentation)
        val manager = AppWidgetManager.getInstance(app)
        val widgetId = manager.getAppWidgetIds(ComponentName(app, QuickExpenseReceiver::class.java)).last()
        val widgetKey = widgetId.toString()
        val before = BackupStore(app.database).snapshot()
        val previous = app.quickExpenses.state.first()
        val accountId = "widget-resize-${UUID.randomUUID()}"
        val categoryId = "widget-resize-category-${UUID.randomUUID()}"
        val presets = listOf(
            ExpensePreset(UUID.randomUUID().toString(), "Transit", 2_500_000, "IDR", accountId, categoryId),
            ExpensePreset(UUID.randomUUID().toString(), "Groceries", 8_500_000, "IDR", accountId, categoryId),
            ExpensePreset(UUID.randomUUID().toString(), "Takeaway", 1_800_000, "IDR", accountId, categoryId),
        )
        val order = presets.map { it.id }
        val dao = app.database.personalDao()
        val folder = File(app.getExternalFilesDir(null), "review/phase-5.1b").apply { mkdirs() }
        val report = StringBuilder()
        val oldIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 100
        device.wakeUp()
        @Suppress("DEPRECATION")
        val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Poros:widget-resize-verification")
        wakeLock.acquire(600_000)

        fun span(): Pair<Int, Int> {
            val options = manager.getAppWidgetOptions(widgetId)
            return options.getInt("semAppWidgetColumnSpan") to options.getInt("semAppWidgetRowSpan")
        }
        val originalSpan = span()
        var widgetLocated = false
        fun node(selector: BySelector): UiObject2 = requireNotNull(
            device.wait(Until.findObject(selector), 8_000),
        ) { "Missing control $selector" }
        fun currentBounds(): Rect {
            var candidate = node(By.desc("Custom Amount"))
            var result = candidate.visibleBounds
            while (candidate.parent != null) {
                candidate = candidate.parent ?: break
                val rectangle = candidate.visibleBounds
                if (candidate.className.contains("AppWidgetHostView")) return rectangle
                if (rectangle.height() < device.displayHeight * 0.75 &&
                    rectangle.width() <= device.displayWidth) result = rectangle
            }
            return result
        }
        suspend fun bounds(): Rect {
            repeat(5) { attempt ->
                try {
                    return currentBounds()
                } catch (error: StaleObjectException) {
                    if (attempt == 4) throw error
                    delay(250)
                }
            }
            error("Widget bounds unavailable")
        }
        suspend fun resize(columns: Int, rows: Int): Pair<Int, Int> {
            var current = span()
            require(current.first > 0 && current.second > 0) { "Samsung grid spans unavailable" }
            if (current.first != columns) {
                val rectangle = bounds()
                report.appendLine("Horizontal handle ${rectangle.toShortString()} from $current to $columns columns")
                node(By.desc("Custom Amount")).longClick()
                device.swipe(rectangle.right - 2, rectangle.centerY(),
                    rectangle.left + rectangle.width() * columns / current.first,
                    rectangle.centerY(), 70)
                device.pressBack()
                delay(800)
            }
            current = span()
            if (current.second != rows) {
                val rectangle = bounds()
                report.appendLine("Vertical handle ${rectangle.toShortString()} from $current to $rows rows")
                node(By.desc("Custom Amount")).longClick()
                device.swipe(rectangle.centerX(), rectangle.bottom - 2,
                    rectangle.centerX(), rectangle.top + rectangle.height() * rows / current.second, 70)
                device.pressBack()
                delay(800)
            }
            node(By.desc("Custom Amount"))
            return span()
        }
        @Suppress("DEPRECATION")
        fun availableSize(): SizeF {
            val options = manager.getAppWidgetOptions(widgetId)
            return options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.firstOrNull() ?: SizeF(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat(),
                )
        }
        suspend fun capture(name: String) {
            delay(600)
            assertTrue(device.takeScreenshot(File(folder, "$name.png")))
            val size = availableSize()
            report.appendLine("$name actual columns x rows=${span()}, Glance bounds=$size")
        }
        suspend fun clearFeedback() {
            app.quickExpenses.update { it.copy(feedback = it.feedback - widgetKey) }
            QuickExpenseWidget().updateAll(app)
            delay(600)
        }
        suspend fun verifyCapacity(name: String, columns: Int, rows: Int) {
            clearFeedback()
            val actual = resize(columns, rows)
            val size = availableSize()
            val layout = quickExpenseLayout(size.width, size.height, app.resources.configuration.fontScale)
            report.appendLine("Requested $columns x $rows, actual $actual, capacity=${layout.presetCapacity}")
            node(By.desc("Custom Amount"))
            presets.forEachIndexed { index, preset ->
                if (index < layout.presetCapacity) node(By.descStartsWith("${preset.name},"))
                else assertFalse("Hidden preset ${preset.name}",
                    device.hasObject(By.descStartsWith("${preset.name},")))
            }
            assertEquals(order, app.quickExpenses.state.first().widgets[widgetKey])
            capture(name)
        }

        try {
            dao.saveAccount(AccountEntity(accountId, "Resize verification", "IDR"), true)
            dao.saveCategory(CategoryEntity(categoryId, "Resize verification", "EXPENSE"), true)
            app.quickExpenses.update { state -> state.copy(
                presets = state.presets + presets,
                widgets = state.widgets + (widgetKey to order),
                feedback = state.feedback - widgetKey,
            ) }
            QuickExpenseWidget().updateAll(app)
            if (!device.hasObject(By.desc("Custom Amount"))) device.pressHome()
            repeat(4) {
                if (!device.wait(Until.hasObject(By.desc("Custom Amount")), 500))
                    device.swipe(950, 700, 100, 700, 80)
            }
            node(By.desc("Custom Amount"))
            widgetLocated = true
            device.dumpWindowHierarchy(File(folder, "initial-hierarchy.xml"))
            capture("original-populated")
            verifyCapacity("minimum-populated", 1, 1)
            node(By.desc("Custom Amount")).click()
            node(By.descStartsWith("Amount"))
            device.pressBack()
            device.pressBack()
            node(By.desc("Custom Amount"))
            verifyCapacity("two-by-two-populated", 2, 2)
            node(By.descStartsWith("Transit,")).click()
            val entry = withTimeout(8_000) {
                app.quickExpenses.state.first {
                    it.feedback[widgetKey]?.entry?.fromAccountId == accountId &&
                        it.feedback[widgetKey]?.message?.startsWith("Recorded ") == true
                }.feedback[widgetKey]!!.entry!!
            }
            resize(2, 1)
            node(By.descStartsWith("Undo"))
            capture("minimum-feedback-undo")
            node(By.descStartsWith("Undo")).click()
            withTimeout(8_000) { while (dao.findEntry(entry.id) != null) delay(100) }
            assertEquals(order, app.quickExpenses.state.first().widgets[widgetKey])
            capture("minimum-undone")
            verifyCapacity("four-by-two-populated", 4, 2)
            verifyCapacity("three-by-four-populated", 3, 4)
            verifyCapacity("intermediate-three-by-two", 3, 2)
            verifyCapacity("tall-two-by-four", 2, 4)
            app.quickExpenses.update { it.copy(widgets = it.widgets + (widgetKey to emptyList())) }
            clearFeedback()
            resize(2, 1)
            capture("minimum-empty")
            resize(2, 2)
            capture("two-by-two-empty")
            resize(4, 2)
            capture("four-by-two-empty")
            app.quickExpenses.update { it.copy(widgets = it.widgets + (widgetKey to order)) }
            clearFeedback()
            node(By.descStartsWith("Takeaway,"))
            assertEquals(order, app.quickExpenses.state.first().widgets[widgetKey])
        } finally {
            try {
                device.takeScreenshot(File(folder, "last-state.png"))
                device.dumpWindowHierarchy(File(folder, "last-hierarchy.xml"))
                if (widgetLocated) resize(originalSpan.first, originalSpan.second)
            } finally {
                try {
                    dao.history(0, Long.MAX_VALUE, "", accountId, "", "", "", 100, 0)
                        .forEach { dao.deleteEntry(it.id) }
                    app.quickExpenses.update { state -> state.copy(
                        presets = state.presets.filter { it.accountId != accountId },
                        widgets = state.widgets + (widgetKey to previous.widgets[widgetKey].orEmpty()),
                        feedback = (state.feedback - widgetKey) + previous.feedback.filterKeys { it == widgetKey },
                        submissions = state.submissions.filterValues { it.entry.fromAccountId != accountId },
                        lastAccount = previous.lastAccount,
                        lastCategory = previous.lastCategory,
                    ) }
                    app.database.withTransaction {
                        app.database.openHelper.writableDatabase.execSQL(
                            "DELETE FROM categories WHERE id = ?", arrayOf(categoryId))
                        app.database.openHelper.writableDatabase.execSQL(
                            "DELETE FROM accounts WHERE id = ?", arrayOf(accountId))
                    }
                    QuickExpenseWidget().updateAll(app)
                    delay(1_000)
                    assertEquals(before, BackupStore(app.database).snapshot())
                    report.appendLine("Exact before/after personal snapshot equality passed. Fixtures removed.")
                } finally {
                    File(folder, "resize-results.txt").writeText(report.toString())
                    Configurator.getInstance().waitForIdleTimeout = oldIdleTimeout
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }
        }
    }
}
