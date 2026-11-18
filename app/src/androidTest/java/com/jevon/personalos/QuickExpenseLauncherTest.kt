package com.jevon.personalos

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.jevon.personalos.backup.BackupStore
import com.jevon.personalos.data.*
import com.jevon.personalos.widget.*
import com.jevon.personalos.today.formatMoney
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in verification against a placed widget. All test-owned financial/catalog data is removed. */
class QuickExpenseLauncherTest {
    @Test fun placedWidgetRecordsCustomizesAndUndoesWithoutChangingPersonalData() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PersonalOsApplication
        val device = UiDevice.getInstance(instrumentation)
        val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(
            ComponentName(app, QuickExpenseReceiver::class.java))
        assertTrue("Place the Quick Expense widget before this opt-in test", ids.isNotEmpty())
        val widgetId = ids.last()
        app.quickExpenses.update { state -> state.copy(presets = state.presets.filterNot {
            it.accountId.startsWith("widget-verification-") && it.name.startsWith("Widget test ")
        }) }
        val before = BackupStore(app.database).snapshot()
        val beforeWidget = app.quickExpenses.state.first()
        val token = UUID.randomUUID().toString()
        val accountId = "widget-verification-$token"
        val categoryId = "widget-verification-category-$token"
        val name = "Widget test ${token.take(8)}"
        val dao = app.database.personalDao()
        val folder = File(app.getExternalFilesDir(null), "review/phase-5.1").apply { mkdirs() }
        val oldIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 100
        device.wakeUp()
        @Suppress("DEPRECATION")
        val wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Poros:widget-verification")
        wakeLock.acquire(600_000)
        var presetId: String? = null

        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        val originalNightMode = shell("settings get secure ui_night_mode")
        val restoreNightMode = when (originalNightMode) {
            "1" -> "no"
            "2" -> "yes"
            else -> "auto"
        }

        fun node(selector: BySelector): UiObject2 =
            requireNotNull(device.wait(Until.findObject(selector), 8_000)) {
                "Missing UI control $selector"
            }
        fun click(text: String) { node(By.text(text)).click() }
        fun enterAmount(value: Int) {
            node(By.desc("Amount in IDR")).click()
            device.pressKeyCode(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
            device.pressKeyCode(KeyEvent.KEYCODE_DEL)
            device.pressKeyCode(KeyEvent.KEYCODE_0 + value)
        }
        fun screenshot(name: String) { device.takeScreenshot(File(folder, "$name.png")) }
        fun openConfiguration() {
            app.startActivity(Intent(app, QuickExpenseConfigureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            node(By.text("Create preset"))
        }
        fun showWidget() {
            device.pressHome()
            repeat(4) {
                if (device.wait(Until.hasObject(By.text("Custom Amount")), 500)) return
                device.swipe(900, 900, 150, 900, 12)
            }
            node(By.text("Custom Amount"))
        }
        suspend fun testEntries() = dao.history(0, Long.MAX_VALUE, "", accountId,
            "", "", "", 100, 0)
        suspend fun awaitCount(expected: Int) {
            repeat(80) {
                if (testEntries().size == expected) return
                delay(100)
            }
            assertEquals(expected, testEntries().size)
        }

        try {
            dao.saveAccount(AccountEntity(accountId, "Widget verification account", "IDR"), true)
            dao.saveCategory(CategoryEntity(categoryId, "Widget verification category", "EXPENSE"), true)
            openConfiguration()
            click("Create preset")
            node(By.clazz("android.widget.EditText")).text = name
            node(By.desc("Choose account")).click()
            click("Widget verification account · IDR")
            enterAmount(1)
            node(By.desc("Choose category")).click()
            click("Widget verification category")
            screenshot("preset-editor")
            click("Save preset")
            node(By.desc("Show $name")).click()
            presetId = app.quickExpenses.state.first { state ->
                state.presets.any { it.name == name }
            }.presets.single { it.name == name }.id
            click("Save widget")
            showWidget()
            screenshot("widget-configured-dark")
            try {
                shell("cmd uimode night no")
                delay(1_000)
                QuickExpenseWidget().updateAll(app)
                delay(1_000)
                screenshot("widget-configured-light")
            } finally {
                shell("cmd uimode night $restoreNightMode")
                delay(1_000)
            }

            node(By.descStartsWith("$name,")).click()
            awaitCount(1)
            node(By.text("Undo"))
            val first = testEntries().single()
            screenshot("widget-success")
            // Rapid direct launcher taps exercise PendingIntent/Glance callbacks as well as the guard.
            val tile = node(By.descStartsWith("$name,")).visibleBounds
            repeat(8) { device.click(tile.centerX(), tile.centerY()) }
            delay(700)
            assertEquals(listOf(first.id), testEntries().map { it.id })
            click("Undo")
            awaitCount(0)
            delay(ACCIDENTAL_TAP_MILLIS + 100)
            node(By.descStartsWith("$name,")).click()
            awaitCount(1)
            assertNotEquals(first.id, testEntries().single().id)
            click("Undo")
            awaitCount(0)

            click("Custom Amount")
            enterAmount(2)
            screenshot("custom-entry")
            click("Save expense")
            awaitCount(1)
            assertEquals(200, testEntries().single().amountMinor)
            click("Undo this expense")
            awaitCount(0)
            click("Done")

            // Reconfigure through the placed widget's normal entry point.
            showWidget()
            delay(100)
            if (!device.hasObject(By.desc("Configure"))) {
                app.quickExpenses.update { it.copy(feedback = it.feedback - widgetId.toString()) }
                QuickExpenseWidget().updateAll(app)
            }
            node(By.desc("Configure")).click()
            click("Edit")
            enterAmount(3)
            click("Save preset")
            click("Save widget")
            showWidget()
            node(By.descContains(formatMoney(300, "IDR")))

            // Fill all four action slots at the host's resized allocation without writing expenses.
            val original = app.quickExpenses.state.first().presets.single { it.id == presetId }
            val additional = listOf(
                original.copy(id = UUID.randomUUID().toString(), name = "Second preset"),
                original.copy(id = UUID.randomUUID().toString(), name = "Third preset"),
            )
            app.quickExpenses.update { state -> state.copy(
                presets = state.presets + additional,
                widgets = state.widgets + (widgetId.toString() to
                    (listOf(original.id) + additional.map { it.id })),
            ) }
            QuickExpenseWidget().updateAll(app)
            node(By.text("Third preset"))
            screenshot("widget-four-actions-dark")
            try {
                shell("cmd uimode night no")
                delay(1_000)
                QuickExpenseWidget().updateAll(app)
                delay(5_000) // Let transient heads-up overlays finish before the theme capture.
                screenshot("widget-four-actions-light")
            } finally {
                shell("cmd uimode night $restoreNightMode")
                delay(1_000)
            }
            app.quickExpenses.update { state -> state.copy(
                presets = state.presets.filter { candidate -> additional.none { it.id == candidate.id } },
                widgets = state.widgets + (widgetId.toString() to listOf(original.id)),
            ) }

            dao.saveAccount(AccountEntity(accountId, "Widget verification account", "IDR",
                archived = true), false)
            QuickExpenseWidget().updateAll(app)
            node(By.descContains("Unavailable"))
            screenshot("widget-unavailable")
            assertTrue(testEntries().isEmpty())
            dao.saveAccount(AccountEntity(accountId, "Widget verification account", "IDR"), false)

            openConfiguration()
            click("Delete")
            node(By.text("Delete preset?"))
            click("Delete")
            node(By.text("Remove unavailable selections"))
            device.pressBack() // Configuration cancellation preserves the placed widget's selection.
            showWidget()
            node(By.descStartsWith("Removed preset,"))
            screenshot("widget-removed-preset")
        } finally {
            device.takeScreenshot(File(folder, "last-state.png"))
            // Undo/delete only entries owned by this test account using Money's normal deletion path.
            testEntries().forEach { dao.deleteEntry(it.id) }
            app.quickExpenses.update { state ->
                state.copy(
                    presets = state.presets.filter { it.accountId != accountId },
                    widgets = state.widgets + (widgetId.toString() to
                        beforeWidget.widgets[widgetId.toString()].orEmpty()),
                    submissions = state.submissions.filterValues {
                        it.entry.fromAccountId != accountId
                    },
                    feedback = state.feedback - widgetId.toString(),
                    lastAccount = beforeWidget.lastAccount,
                    lastCategory = beforeWidget.lastCategory,
                )
            }
            app.database.withTransaction {
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM categories WHERE id = ?", arrayOf(categoryId))
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM accounts WHERE id = ?", arrayOf(accountId))
            }
            QuickExpenseWidget().updateAll(app)
            assertEquals("Personal snapshot must match exactly after verification", before,
                BackupStore(app.database).snapshot())
            File(folder, "personal-data-preserved.txt").writeText(
                "Exact before/after personal snapshot equality passed. Test account, category, " +
                    "preset and expenses removed. Placed widget retained.\n")
            Configurator.getInstance().waitForIdleTimeout = oldIdleTimeout
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
