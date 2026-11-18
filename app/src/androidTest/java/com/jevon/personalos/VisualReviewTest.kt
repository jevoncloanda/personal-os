package com.jevon.personalos

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File
import com.jevon.personalos.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Captures the installed native app, including bars and real device font scaling. */
class VisualReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun keepTestWindowAwake() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Compose idleness can precede WindowManager's dialog removal / final frame.
        instrumentation.uiAutomation.waitForIdle(600, 5_000)
        android.os.SystemClock.sleep(1500)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "review").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun appearance(theme: String, dynamic: Boolean = false) {
        val expectedTheme = ThemeMode.valueOf(theme.uppercase(java.util.Locale.ROOT))
        val store = (compose.activity.application as PersonalOsApplication).settings
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText(theme).performClick()
        // Advance Compose frames while awaiting asynchronous preference/UI updates.
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.settings.first().theme == expectedTheme } }
        val toggle = compose.onNode(hasText("Wallpaper colors") and isToggleable())
        val savedDynamic = runBlocking { store.settings.first().dynamicColor }
        compose.waitUntil(timeoutMillis = 5_000) {
            val rendered = toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
            (rendered == androidx.compose.ui.state.ToggleableState.On) == savedDynamic
        }
        if (savedDynamic != dynamic) toggle.performClick()
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { store.settings.first().let { it.theme == expectedTheme && it.dynamicColor == dynamic } } }
        compose.onAllNodesWithText("Today").filter(hasClickAction()).onLast().performClick()
    }
    private fun preview(label: String) {
        compose.onNodeWithContentDescription("Developer previews").performClick()
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
    }
    @Test fun captureReviewStates() {
        val store = (compose.activity.application as PersonalOsApplication).settings
        val original = runBlocking { store.settings.first() }
        try {
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "phone"
        preview("Populated")
        appearance("Light")
        compose.onNodeWithText("Good", substring = true).performScrollTo()
        capture("$prefix-light")
        compose.onNodeWithText("Hollow Knight").performScrollTo()
        capture("$prefix-light-lower")
        compose.onNodeWithText("Good", substring = true).performScrollTo()
        appearance("Dark")
        capture("$prefix-dark")
        preview("Empty")
        compose.onNodeWithText("Nothing urgent today").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Good", substring = true).performScrollTo()
        capture("$prefix-empty")
        preview("Personal")
        compose.onNodeWithText("Priorities").assertExists()
        capture("$prefix-personal")
        preview("Populated")
        appearance("Light", dynamic = true)
        compose.onNodeWithText("Good", substring = true).performScrollTo()
        capture("$prefix-dynamic")
        appearance("Light")
        compose.onNodeWithContentDescription("Quick add").performClick()
        capture("$prefix-quick-add")
        } finally {
            runBlocking { store.setTheme(original.theme); store.setDynamicColor(original.dynamicColor) }
        }
    }
}
