package com.jevon.personalos

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.library.LibraryReviewActivity
import com.jevon.personalos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun waitFor(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun tap(text: String) {
        val target = when {
            text == "Save" -> hasText("Save", substring = true)
            text.startsWith("Status:") -> hasContentDescription("Choose status")
            else -> hasText(text)
        } and hasClickAction()
        if (compose.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
            val tag = if (compose.onAllNodesWithTag("LibraryDetail").fetchSemanticsNodes().isNotEmpty()) "LibraryDetail" else "LibraryList"
            compose.onNodeWithTag(tag).performScrollToNode(target)
        }
        val node = compose.onAllNodes(target).onLast()
        try { node.performScrollTo() } catch (_: AssertionError) {}
        node.performClick()
        if (text == "Save") compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription("Cancel editing").fetchSemanticsNodes().isEmpty()
        }
    }
    private fun field(label: String, value: String) {
        val target = if (label == "Search titles") hasContentDescription(label) else hasText(label)
        compose.onNode(target).performScrollTo().performTextReplacement(value)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.waitForIdle(600, 5000)
        android.os.SystemClock.sleep(1200)
        val bitmap = requireNotNull(i.uiAutomation.takeScreenshot())
        val folder = File(i.targetContext.getExternalFilesDir(null), "review/${InstrumentationRegistry.getArguments().getString("captureFolder") ?: "phase-4"}").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun longTitlesLocalArtworkAndMissingCoversRemainReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val coverDirectory = File(context.filesDir, "library-covers").apply { mkdirs() }
        val cover = File(coverDirectory, "${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(39, 49, 93))
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28f
                isAntiAlias = true
            }
            canvas.drawText("LOCAL COVER", 40f, 150f, paint)
            cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally { bitmap.recycle() }
        val scenario = ActivityScenario.launch<LibraryReviewActivity>(
            Intent(context, LibraryReviewActivity::class.java).putExtra("reviewId", id)
        )
        val title = "A deliberately long Library title for reading at larger text sizes"
        try {
            scenario.onActivity { activity -> runBlocking {
                val dao = activity.databaseForTest().personalDao()
                dao.saveLibrary(LibraryItemEntity("cover", title, LibraryKind.BOOK, LibraryStatus.ACTIVE,
                    progress = 125, total = 500, coverPath = cover.name, author = "Review author",
                    rating = 4, notes = "Preserved personal notes", updatedAtMillis = 3), true)
                dao.saveLibrary(LibraryItemEntity("fallback", "Missing cover fixture", LibraryKind.GAME,
                    LibraryStatus.ACTIVE, hoursPlayedMinutes = 750, completionPercent = 40,
                    platform = "PC", updatedAtMillis = 2), true)
                dao.saveLibrary(LibraryItemEntity("completed", "Completed movie fixture", LibraryKind.MOVIE,
                    LibraryStatus.COMPLETED, rating = 5, updatedAtMillis = 1), true)
            } }
            waitFor(title)
            tap("Review dark")
            capture("library-artwork-dark")
            tap(title)
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithText("Page 125 of 500 · 25%").assertExists()
            capture("library-long-detail-dark")
            tap("Review light")
            capture("library-long-detail-light")
            tap("All Library")
            compose.onNodeWithTag("LibraryList").performScrollToNode(hasText("Recently completed"))
            capture("library-completed-light")
        } finally {
            scenario.close()
            context.deleteDatabase("phase4-review-$id.db")
            cover.delete()
        }
    }
    @Test fun manualCollectionProgressHistoryThemesAndPersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val scenario = ActivityScenario.launch<LibraryReviewActivity>(Intent(context, LibraryReviewActivity::class.java).putExtra("reviewId", id))
        var activity: LibraryReviewActivity? = null
        scenario.onActivity { activity = it }
        val dao = activity!!.databaseForTest().personalDao()
        try {
            waitFor("Your Library")
            for ((kind, title) in listOf("Movie" to "Review movie", "TV series" to "Review series", "Game" to "Review game", "Book" to "Review book")) {
                tap("Add to Library"); tap(kind); field("Title", title)
                field("Personal rating (1–5, optional)", "4"); field("Notes / review (optional)", "Personal review for $kind")
                tap("Save"); waitFor(title)
                tap("All Library"); waitFor("Your Library")
            }
            tap("Review series"); tap("Update progress")
            field("Current season (optional)", "2"); field("Current episode (optional)", "4")
            tap("Status: Watchlist"); tap("Watching"); tap("Save"); waitFor("Season 2, Episode 4")
            tap("All Library"); tap("Review game"); tap("Update progress")
            field("Hours played (optional)", "12.5"); tap("Status: Backlog"); tap("Playing"); tap("Save"); waitFor("12h 30m played")
            tap("All Library"); tap("Review book"); tap("Update progress")
            field("Current page", "160"); field("Total pages (optional)", "320")
            tap("Status: Want to Read"); tap("Reading"); tap("Save"); waitFor("Page 160 of 320 · 50%")
            compose.waitUntil(10000) { runBlocking { dao.libraryCount("", "", "ACTIVE") == 3 } }
            capture("book-detail-light"); tap("Review dark"); capture("book-detail-dark"); tap("Review light")
            tap("All Library"); capture("collection-light")
            field("Search titles", "Review movie")
            compose.waitUntil(10000) { compose.onAllNodesWithText("Review book").fetchSemanticsNodes().isEmpty() }
            field("Search titles", "")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("LibraryList").performScrollToIndex(0)
            compose.onNode(hasText("Book") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
            waitFor("Review book")
            compose.onNode(hasText("All media") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
            scenario.onActivity { host -> host.currentFocus?.let { view ->
                (host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            } }
            tap("Review Today"); waitFor("Review book")
            compose.onNodeWithText("Review book").performScrollTo()
            capture("today-library")
            tap("Review Library"); scenario.recreate(); scenario.onActivity { activity = it }; waitFor("Your Library")
            assertEquals(4, runBlocking { activity!!.databaseForTest().personalDao().libraryCount("", "", "") })
            val records = runBlocking { dao.libraryPage("", "", "", "TITLE") }
            assertTrue(records.all { it.rating == 4 && it.notes.startsWith("Personal review") })
            tap("Review movie"); tap("Delete item"); tap("Delete"); waitFor("Your Library")
            compose.waitUntil(10000) { runBlocking { dao.libraryCount("", "", "") == 3 } }
            tap("Add to Library"); tap("Movie"); field("Title", "Unsaved draft")
            scenario.recreate(); compose.onNodeWithText("Title").assertTextContains("Unsaved draft")
            compose.onNodeWithContentDescription("Cancel editing").performClick(); tap("Discard")
            assertEquals(3, runBlocking { dao.libraryCount("", "", "") })
            scenario.close()
            val reopened = ActivityScenario.launch<LibraryReviewActivity>(Intent(context, LibraryReviewActivity::class.java).putExtra("reviewId", id))
            try {
                waitFor("Your Library")
                reopened.onActivity { fresh -> assertEquals(3, runBlocking { fresh.databaseForTest().personalDao().libraryCount("", "", "ACTIVE") }) }
            } finally { reopened.close() }
        } finally { scenario.close(); context.deleteDatabase("phase4-review-$id.db") }
    }
}
