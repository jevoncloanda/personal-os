package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jevon.personalos.backup.BackupReviewActivity
import com.jevon.personalos.backup.BackupStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** SAF round trip and replacement ONLY in BackupReviewActivity's in-memory database. */
class BackupUiTest {
    @get:Rule val compose = createAndroidComposeRule<BackupReviewActivity>()
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrument)
    private fun click(label: String) { val node = compose.onNodeWithText(label); if (!node.isDisplayed()) node.performScrollTo(); node.performClick() }
    private fun capture(name: String, nativePicker: Boolean = false) {
        if (!nativePicker) compose.waitForIdle()
        instrument.waitForIdleSync()
        device.waitForIdle(); android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "review/phase-1.7").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun isolatedDocumentPickerRoundTripRequiresConfirmationAndKeepsMoney() {
        val store = BackupStore(compose.activity.databaseForTest())
        val before = runBlocking { store.snapshot() }
        capture("backup-light")
        click("Export backup")
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000))
        capture("document-create", nativePicker = true)
        device.pressBack()
        compose.onNodeWithText("Canceled.", substring = true).assertExists()
        assertEquals(before, runBlocking { store.snapshot() })
        click("Export backup")
        val name = "personal-os-review-${System.currentTimeMillis()}.json"
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000).text = name
        val save = device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))), 10000)
        requireNotNull(save) { "Native document Save button unavailable" }.click()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Backup saved.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        capture("backup-export-success")
        click("Use empty test data")
        compose.waitUntil(5000) { runBlocking { store.snapshot().counts.values.sum() == 0 } }
        click("Restore backup")
        val file = device.wait(Until.findObject(By.text(name)), 10000)
        requireNotNull(file) { "Created backup is not visible in the document picker" }.click()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Restore preview").fetchSemanticsNodes().isNotEmpty() }
        click("Review replacement")
        compose.onNodeWithText("Replace local data").assertIsNotEnabled()
        capture("restore-confirmation")
        click("Keep current data")
        compose.onNodeWithText("Restore preview").performScrollTo(); capture("restore-preview-light")
        click("Review dark theme")
        compose.onNodeWithText("Restore preview").performScrollTo(); capture("restore-preview-dark")
        click("Review replacement")
        click("I understand this replaces my local data")
        click("Replace local data")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Restore complete.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        capture("restore-success")
        val after = runBlocking { store.snapshot() }
        assertEquals(before, after)
        val bank = runBlocking { compose.activity.databaseForTest().personalDao().balances().single { it.account.id == "bank" } }
        assertEquals(12_674_500L, bank.balance)
    }
}
