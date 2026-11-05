package com.jevon.personalos.library

import com.jevon.personalos.data.*
import java.time.LocalDate

val gamePlatforms = listOf("PC", "PlayStation", "Xbox", "Nintendo", "Other")
fun mediaLabel(kind: LibraryKind) = when (kind) { LibraryKind.MOVIE -> "Movie"; LibraryKind.SERIES -> "TV series"; LibraryKind.GAME -> "Game"; LibraryKind.BOOK -> "Book" }
fun statusLabel(kind: LibraryKind, status: LibraryStatus): String = when (status) {
    LibraryStatus.PLANNED -> when (kind) { LibraryKind.MOVIE, LibraryKind.SERIES -> "Watchlist"; LibraryKind.GAME -> "Backlog"; LibraryKind.BOOK -> "Want to Read" }
    LibraryStatus.ACTIVE -> when (kind) { LibraryKind.MOVIE, LibraryKind.SERIES -> "Watching"; LibraryKind.GAME -> "Playing"; LibraryKind.BOOK -> "Reading" }
    LibraryStatus.COMPLETED -> when (kind) { LibraryKind.MOVIE -> "Watched"; LibraryKind.BOOK -> "Finished"; else -> "Completed" }
    LibraryStatus.ON_HOLD -> "On Hold"
    LibraryStatus.DROPPED -> "Dropped"
}
fun LibraryItemEntity.withStatus(next: LibraryStatus, date: LocalDate = LocalDate.now()) = copy(status = next,
    startedEpochDay = if (next == LibraryStatus.ACTIVE && startedEpochDay == null) minOf(date.toEpochDay(), completedEpochDay ?: date.toEpochDay()) else startedEpochDay,
    completedEpochDay = if (next == LibraryStatus.COMPLETED && completedEpochDay == null) date.toEpochDay() else completedEpochDay)

fun progressLabel(item: LibraryItemEntity): String = when (item.kind) {
    LibraryKind.BOOK -> "Page ${item.progress}${item.total?.let { " of $it · ${item.progress.toLong() * 100 / it}%" } ?: " · total unknown"}"
    LibraryKind.SERIES -> if (item.season != null || item.episode != null) "Season ${item.season ?: "?"}, Episode ${item.episode ?: "?"}" else "Episode ${item.progress}${item.total?.let { " of $it" } ?: " · total unknown"}"
    LibraryKind.GAME -> (item.hoursPlayedMinutes?.let { "${it / 60}h ${it % 60}m played" } ?: "${item.progress} hours played") + (item.completionPercent?.let { " · $it%" } ?: "")
    LibraryKind.MOVIE -> if (item.progress > 0) "${item.progress} minutes watched" else statusLabel(item.kind, item.status)
}

/** Shared boundary for editors, quick progress, imports and portable recovery. */
fun validateLibrary(item: LibraryItemEntity) {
    require(item.id.isNotBlank() && item.id.length <= 200 && item.title.isNotBlank()) { "A title is required" }
    listOf(item.id, item.title, item.subtitle, item.description, item.notes, item.author, item.isbn, item.platform).forEach { com.jevon.personalos.backup.checkedBackupText(it) }
    require(item.releaseYear == null || item.releaseYear in 1..9999) { "Use a year from 1 to 9999" }
    require(item.rating == null || item.rating in 1..5) { "Rating must be from 1 to 5" }
    require(item.progress >= 0 && (item.total == null || item.total > 0 && item.progress <= item.total)) { "Progress cannot exceed the known total" }
    require(item.season == null || item.season > 0) { "Season must be positive" }
    require(item.episode == null || item.episode >= 0) { "Episode cannot be negative" }
    require(item.totalSeasons == null || item.totalSeasons > 0 && (item.season == null || item.season <= item.totalSeasons)) { "Season cannot exceed total seasons" }
    require(item.hoursPlayedMinutes == null || item.hoursPlayedMinutes in 0..60000000) { "Playtime is outside the supported range" }
    require(item.completionPercent == null || item.completionPercent in 0..100) { "Completion must be from 0 to 100" }
    require(item.platform.isEmpty() || item.platform in gamePlatforms) { "Choose a supported platform" }
    require(item.kind == LibraryKind.SERIES || item.season == null && item.episode == null && item.totalSeasons == null) { "Season and episode fields belong to TV series" }
    require(item.kind == LibraryKind.GAME || item.platform.isEmpty() && item.hoursPlayedMinutes == null && item.completionPercent == null) { "Playtime and platform belong to games" }
    require(item.kind == LibraryKind.BOOK || item.author.isEmpty() && item.isbn.isEmpty()) { "Author and ISBN belong to books" }
    listOfNotNull(item.startedEpochDay, item.completedEpochDay).forEach { require(LocalDate.ofEpochDay(it).year in 1..9999) { "Use a date from year 1 to 9999" } }
    require(item.startedEpochDay == null || item.completedEpochDay == null || item.completedEpochDay >= item.startedEpochDay) { "Completion cannot precede the start date" }
    require(item.addedAtMillis >= 0 && item.updatedAtMillis >= 0) { "Invalid record timestamp" }
    require((item.provider == null) == (item.providerId == null)) { "Metadata source and identity must be provided together" }
    require(item.provider == null || item.provider == "openlibrary" && item.kind == LibraryKind.BOOK && item.providerId!!.matches(Regex("/works/OL[0-9]+W"))) { "Unsupported metadata identity" }
    require(item.coverPath == null || item.coverPath.matches(Regex("[a-f0-9-]{36}\\.jpg"))) { "Invalid local cover reference" }
}
