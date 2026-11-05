package com.jevon.personalos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jevon.personalos.backup.BackupStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Installed-app navigation only; no fixture writes to personal storage. */
class LibraryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun quickAddAllTypesAndCancelPreservePersonalSnapshot() {
        val store = BackupStore((compose.activity.application as PersonalOsApplication).database)
        val before = runBlocking { store.snapshot() }
        try {
            for ((label, heading) in listOf("Movie" to "Add movie", "TV series" to "Add tv series", "Game" to "Add game", "Book" to "Add book")) {
                compose.onNodeWithContentDescription("Quick add").performClick()
                compose.onAllNodesWithText("Add to Library").onLast().performClick()
                compose.onNodeWithText(label).performClick()
                compose.onNodeWithText(heading).assertIsDisplayed()
                compose.onNodeWithContentDescription("Cancel editing").performClick()
                compose.onNodeWithText("Your Library").assertExists()
            }
            assertEquals(before, runBlocking { store.snapshot() })
        } finally { assertEquals(before, runBlocking { store.snapshot() }) }
    }
}
