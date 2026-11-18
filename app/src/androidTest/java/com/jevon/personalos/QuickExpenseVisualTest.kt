package com.jevon.personalos

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.AccountEntity
import com.jevon.personalos.data.CategoryEntity
import com.jevon.personalos.widget.*
import com.jevon.personalos.today.formatMoney
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class QuickExpenseVisualTest {
    @Test fun launcherVisualStatesPreservePersonalData() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PersonalOsApplication
        val device = UiDevice.getInstance(instrumentation)
        val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(
            ComponentName(app, QuickExpenseReceiver::class.java))
        assertTrue("Place a Quick Expense widget before visual verification", ids.isNotEmpty())
        val widgetId = ids.last()
        val widgetKey = widgetId.toString()
        val before = BackupStore(app.database).snapshot()
        val previous = app.quickExpenses.state.first()
        val accountId = "widget-visual-${UUID.randomUUID()}"
        val categoryId = "widget-visual-category-${UUID.randomUUID()}"
        val presets = listOf(
            ExpensePreset(UUID.randomUUID().toString(), "Transit", 2_500_000, "IDR", accountId, categoryId),
            ExpensePreset(UUID.randomUUID().toString(), "Groceries", 8_500_000, "IDR", accountId, categoryId),
            ExpensePreset(UUID.randomUUID().toString(), "Takeaway", 1_800_000, "IDR", accountId, categoryId),
        )
        val dao = app.database.personalDao()
        val folder = File(app.getExternalFilesDir(null), "review/phase-5.1a").apply { mkdirs() }
        val oldIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 100
        device.wakeUp()
        @Suppress("DEPRECATION")
        val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Poros:widget-visual-verification")
        wakeLock.acquire(180_000)

        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        val originalMode = when (shell("settings get secure ui_night_mode")) {
            "1" -> "no"
            "2" -> "yes"
            else -> "auto"
        }
        fun node(selector: BySelector) = requireNotNull(
            device.wait(Until.findObject(selector), 8_000),
        ) { "Missing widget control $selector" }
        suspend fun capture(name: String) {
            delay(600)
            assertTrue(device.takeScreenshot(File(folder, "$name.png")))
        }
        suspend fun select(count: Int) {
            app.quickExpenses.update { state -> state.copy(
                widgets = state.widgets + (widgetKey to presets.take(count).map { it.id }),
                feedback = state.feedback - widgetKey,
            ) }
            QuickExpenseWidget().updateAll(app)
        }
        suspend fun theme(mode: String) {
            shell("cmd uimode night $mode")
            delay(1_000)
            QuickExpenseWidget().updateAll(app)
            delay(4_000) // Avoid capturing a transient notification over the widget header.
        }

        try {
            dao.saveAccount(AccountEntity(accountId, "Visual verification", "IDR"), true)
            dao.saveCategory(CategoryEntity(categoryId, "Visual verification", "EXPENSE"), true)
            app.quickExpenses.update { it.copy(presets = it.presets + presets) }
            select(0)
            device.pressHome()
            repeat(4) {
                if (!device.wait(Until.hasObject(By.desc("Custom Amount")), 500)) {
                    device.swipe(900, 900, 150, 900, 12)
                }
            }
            node(By.desc("Set up presets"))
            theme("yes")
            capture("empty-dark")
            theme("no")
            capture("empty-light")
            theme("yes")
            select(1)
            node(By.descStartsWith("Transit,"))
            capture("one-preset-dark")
            select(2)
            node(By.desc("Set up presets"))
            capture("two-presets-dark")
            select(3)
            node(By.descStartsWith("Takeaway,"))
            assertTrue(node(By.descStartsWith("Transit,")).contentDescription.contains(
                formatMoney(presets.first().amountMinor, "IDR")))
            capture("configured-dark")
            theme("no")
            capture("configured-light")
            theme("yes")
            node(By.descStartsWith("Transit,")).click()
            val receipt = withTimeout(8_000) {
                app.quickExpenses.state.first { state ->
                    state.feedback[widgetKey]?.entry?.fromAccountId == accountId &&
                        state.feedback[widgetKey]?.message?.startsWith("Recorded ") == true
                }.feedback[widgetKey]!!
            }
            val recordedEntry = requireNotNull(receipt.entry)
            node(By.desc("Undo"))
            capture("feedback-undo-dark")
            assertNotNull(dao.findEntry(recordedEntry.id))
            node(By.desc("Undo")).click()
            withTimeout(8_000) {
                while (dao.findEntry(recordedEntry.id) != null) delay(100)
            }
            capture("undone-dark")
        } finally {
            try {
                shell("cmd uimode night $originalMode")
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
                assertEquals(before, BackupStore(app.database).snapshot())
                File(folder, "verification.txt").writeText(
                    "Exact personal snapshot equality passed. Visual fixtures removed.\n")
            } finally {
                Configurator.getInstance().waitForIdleTimeout = oldIdleTimeout
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }
}
