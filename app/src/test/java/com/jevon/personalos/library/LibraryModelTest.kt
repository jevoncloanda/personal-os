package com.jevon.personalos.library

import com.jevon.personalos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LibraryModelTest {
    private val date = LocalDate.of(2026, 11, 5)
    private fun item(kind: LibraryKind = LibraryKind.BOOK) = LibraryItemEntity("item", "Manual title", kind,
        LibraryStatus.PLANNED, updatedAtMillis = 100, notes = "A private review", rating = 4)
    private fun invalid(create: () -> LibraryItemEntity) {
        assertThrows(IllegalArgumentException::class.java) { validateLibrary(create()) }
    }

    @Test fun statusReversalRetainsProgressNotesRatingAndHistoricalDates() {
        val planned = item().copy(progress = 160, total = 320)
        val reading = planned.withStatus(LibraryStatus.ACTIVE, date)
        assertEquals(date.toEpochDay(), reading.startedEpochDay)
        val finished = reading.withStatus(LibraryStatus.COMPLETED, date.plusDays(2))
        assertEquals(date.plusDays(2).toEpochDay(), finished.completedEpochDay)
        LibraryStatus.entries.forEach { status ->
            val changed = finished.withStatus(status, date.plusDays(5))
            validateLibrary(changed)
            assertEquals(finished.copy(status = status), changed)
        }
    }

    @Test fun reversingCompletionWithUnknownStartKeepsAValidHistoricalCompletion() {
        val completed = item().copy(status = LibraryStatus.COMPLETED, completedEpochDay = date.minusDays(5).toEpochDay())
        val active = completed.withStatus(LibraryStatus.ACTIVE, date)
        validateLibrary(active)
        assertEquals(completed.completedEpochDay, active.completedEpochDay)
        assertEquals(completed.notes, active.notes)
        assertEquals(completed.rating, active.rating)
    }

    @Test fun ratingsDatesAndProviderReferencesAreValidatedAtTheBoundary() {
        listOf(1, 5).forEach { validateLibrary(item().copy(rating = it)) }
        invalid { item().copy(rating = 0) }; invalid { item().copy(rating = 6) }
        invalid { item().copy(title = " ") }; invalid { item().copy(releaseYear = 0) }
        invalid { item().copy(startedEpochDay = date.toEpochDay(), completedEpochDay = date.minusDays(1).toEpochDay()) }
        invalid { item().copy(addedAtMillis = -1) }
        invalid { item().copy(provider = "openlibrary") }
        invalid { item().copy(provider = "openlibrary", providerId = "not-a-work") }
        invalid { item().copy(coverPath = "../../outside.jpg") }
        validateLibrary(item().copy(provider = "openlibrary", providerId = "/works/OL123W"))
    }

    @Test fun mediaSpecificFieldsCannotLeakIntoOtherTypes() {
        invalid { item(LibraryKind.MOVIE).copy(author = "Author") }
        invalid { item(LibraryKind.BOOK).copy(season = 1) }
        invalid { item(LibraryKind.SERIES).copy(platform = "PC") }
        invalid { item(LibraryKind.MOVIE).copy(hoursPlayedMinutes = 100) }
        invalid { item(LibraryKind.GAME).copy(provider = "openlibrary", providerId = "/works/OL123W") }
    }

    @Test fun bookPagesAndUnknownTotalsHaveHonestProgress() {
        val reading = item().copy(progress = 160, total = 320)
        validateLibrary(reading)
        assertTrue(progressLabel(reading).contains("160 of 320"))
        assertTrue(progressLabel(reading).contains("50%"))
        val unknown = reading.copy(total = null)
        validateLibrary(unknown)
        assertTrue(progressLabel(unknown).contains("total unknown"))
        invalid { reading.copy(progress = 321) }
        invalid { reading.copy(total = 0) }
    }

    @Test fun seriesAndGameProgressRetainLegacyMeaningWithoutAutoCompletion() {
        val legacySeries = item(LibraryKind.SERIES).copy(status = LibraryStatus.ACTIVE, progress = 12, total = 24)
        validateLibrary(legacySeries)
        assertEquals("Episode 12 of 24", progressLabel(legacySeries))
        val currentSeries = legacySeries.copy(season = 2, episode = 4, totalSeasons = 2)
        validateLibrary(currentSeries)
        assertEquals("Season 2, Episode 4", progressLabel(currentSeries))
        assertEquals(LibraryStatus.ACTIVE, currentSeries.status)
        invalid { currentSeries.copy(season = 3) }
        invalid { currentSeries.copy(episode = -1) }
        val legacyGame = item(LibraryKind.GAME).copy(progress = 12)
        validateLibrary(legacyGame)
        assertEquals("12 hours played", progressLabel(legacyGame))
        val game = legacyGame.copy(hoursPlayedMinutes = 735, completionPercent = 100, platform = "Nintendo")
        validateLibrary(game)
        assertTrue(progressLabel(game).contains("12h 15m"))
        assertEquals(LibraryStatus.PLANNED, game.status)
        invalid { game.copy(hoursPlayedMinutes = -1) }
        invalid { game.copy(completionPercent = 101) }
        invalid { game.copy(platform = "Unsupported") }
    }
}
