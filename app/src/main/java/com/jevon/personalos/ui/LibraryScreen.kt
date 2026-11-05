package com.jevon.personalos.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jevon.personalos.data.*
import com.jevon.personalos.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.*
import java.util.UUID

@Composable
fun LibraryScreen(model: LibraryViewModel, requested: String? = null, handled: () -> Unit = {}) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val item by model.selected.collectAsStateWithLifecycle()
    val selectedId by model.selectedId.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var newKind by rememberSaveable { mutableStateOf("MOVIE") }
    var chooseType by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    fun create(kind: String) { model.select(null); newKind = kind; editor = "new"; chooseType = false }
    LaunchedEffect(requested) { requested?.let { if (it == "new") { model.select(null); chooseType = true } else if (it.startsWith("new-")) create(it.removePrefix("new-")) else model.select(it); handled() } }
    BackHandler(selectedId != null && editor == null) { model.select(null) }
    if (selectedId != null) {
        LazyColumn(Modifier.fillMaxSize().testTag("LibraryDetail"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton(onClick = { model.select(null) }) { Text("All Library") } }
            val current = item
            if (current == null) item { Text("Item unavailable. It may have been removed. Return to your Library.") }
            else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LibraryCover(current, Modifier.width(100.dp).height(150.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(current.title, style = MaterialTheme.typography.headlineSmall)
                            if (current.subtitle.isNotBlank()) Text(current.subtitle)
                            Text("${mediaLabel(current.kind)}${current.releaseYear?.let { " · $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (current.favorite) Text("Favorite", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Choice("Status", current.status.name, LibraryStatus.entries.map { it.name to statusLabel(current.kind, it) }, !busy) { model.save(current.withStatus(LibraryStatus.valueOf(it)), false) {} }
                }
                item { FrostedPanel {
                    Text(progressLabel(current), style = MaterialTheme.typography.titleMedium)
                    if (current.kind != LibraryKind.MOVIE) TextButton(enabled = !busy, onClick = { model.clearError(); editor = "progress" }) { Text("Update progress") }
                    current.totalSeasons?.let { Text("Total seasons: $it") }
                    if (current.platform.isNotEmpty()) Text("Platform: ${current.platform}")
                } }
                item {
                    Text(current.rating?.let { "Your rating: $it / 5" } ?: "Not rated", style = MaterialTheme.typography.titleMedium)
                    if (current.author.isNotEmpty()) Text("Author: ${current.author}")
                    if (current.isbn.isNotEmpty()) Text("ISBN: ${current.isbn}")
                    Text("Added: ${if (current.addedAtMillis == 0L) "Not recorded (legacy item)" else Instant.ofEpochMilli(current.addedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()}")
                    Text("Started: ${current.startedEpochDay?.let(LocalDate::ofEpochDay) ?: "Not recorded"}")
                    Text("Completed: ${current.completedEpochDay?.let(LocalDate::ofEpochDay) ?: "Not recorded"}")
                    Text("Updated: ${Instant.ofEpochMilli(current.updatedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()}")
                }
                if (current.description.isNotBlank()) item { Text("Synopsis", style = MaterialTheme.typography.titleMedium); Text(current.description) }
                item { Text("Your notes", style = MaterialTheme.typography.titleMedium); Text(current.notes.ifBlank { "No notes yet" }) }
                if (current.provider != null) item { Text("Metadata: ${current.provider} · ${current.providerId}", style = MaterialTheme.typography.bodySmall) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy, onClick = { model.clearError(); editor = "edit" }) { Text("Edit item") }
                    TextButton(enabled = !busy, onClick = { deleting = true }) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
                } }
                if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
        }
    } else when (val s = state) {
        LibraryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LibraryState.Error -> Column(Modifier.padding(20.dp)) { Text("Library couldn't load. Your records are unchanged."); Button(onClick = model::retry) { Text("Try again") } }
        is LibraryState.Ready -> {
            val data = s.content
            LazyColumn(Modifier.fillMaxSize().testTag("LibraryList"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionHeading("Your Library", "Add to Library", { chooseType = true }) }
                item { OutlinedTextField(filter.search, { model.setFilter(filter.copy(search = it, page = 0)) }, label = { Text("Search titles") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Choice("Media", filter.kind, listOf("" to "All media") + LibraryKind.entries.map { it.name to mediaLabel(it) }) { model.setFilter(filter.copy(kind = it, page = 0)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Choice("Status", filter.status, listOf("" to "All statuses", "PLANNED" to "Planned / Backlog", "ACTIVE" to "In progress", "COMPLETED" to "Completed", "ON_HOLD" to "On Hold", "DROPPED" to "Dropped")) { model.setFilter(filter.copy(status = it, page = 0)) } }
                    Box(Modifier.weight(1f)) { Choice("Sort", filter.sort, listOf("UPDATED" to "Recently updated", "ADDED" to "Recently added", "TITLE" to "Title")) { model.setFilter(filter.copy(sort = it, page = 0)) } }
                } }
                if (filter.search.isBlank() && filter.status.isBlank() && filter.page == 0) {
                    if (data.active.isNotEmpty()) {
                        item { SectionHeading("Currently enjoying", "View all", { model.setFilter(filter.copy(status = "ACTIVE", page = 0)) }) }
                        items(data.active, key = { "active/${it.id}" }) { LibraryRow(it) { model.select(it.id) } }
                    }
                    if (data.planned.isNotEmpty()) item { SectionHeading("Pick next", "Backlog", { model.setFilter(filter.copy(status = "PLANNED", page = 0)) }); Text(data.planned.joinToString(" · ") { it.title }, maxLines = 3) }
                    if (data.completed.isNotEmpty()) item { SectionHeading("Recently completed", "View completed", { model.setFilter(filter.copy(status = "COMPLETED", page = 0)) }); Text(data.completed.joinToString(" · ") { it.title }, maxLines = 3) }
                }
                item { Text("Collection · ${data.count}", style = MaterialTheme.typography.titleMedium) }
                if (data.items.isEmpty()) item {
                    if (filter.search.isNotBlank() || filter.kind.isNotBlank() || filter.status.isNotBlank()) EmptyMessage("No matching titles", "Try another title or change your media and status filters.")
                    else EmptyMessage("Nothing here yet", "Add a movie, series, game or book manually. Saved titles, progress and reviews work fully offline.")
                }
                items(data.items.take(60), key = { "collection/${it.id}" }) { LibraryRow(it) { model.select(it.id) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(enabled = filter.page > 0, onClick = { model.setFilter(filter.copy(page = filter.page - 1)) }) { Text("Previous page") }
                    TextButton(enabled = data.items.size > 60, onClick = { model.setFilter(filter.copy(page = filter.page + 1)) }) { Text("Next page") }
                } }
            }
        }
    }
    if (chooseType) AlertDialog(onDismissRequest = { chooseType = false }, title = { Text("Add to Library") }, text = {
        Column { LibraryKind.entries.forEach { kind -> TextButton(onClick = { create(kind.name) }, modifier = Modifier.fillMaxWidth()) { Text(mediaLabel(kind)) } } }
    }, confirmButton = { TextButton(onClick = { chooseType = false }) { Text("Cancel") } })
    if (editor != null && (editor == "new" || item != null)) key(editor, selectedId, newKind) {
        val draftId = rememberSaveable { UUID.randomUUID().toString() }
        val added = rememberSaveable { System.currentTimeMillis() }
        val base = remember { item?.takeIf { editor != "new" } ?: LibraryItemEntity(draftId, "", LibraryKind.valueOf(newKind), LibraryStatus.PLANNED, updatedAtMillis = added, addedAtMillis = added) }
        LibraryEditor(base, editor == "new", editor == "progress", busy, error, { model.cleanupCovers(context, it) }, { editor = null; model.clearError() }) { value, cleanup -> model.save(value, editor == "new") { cleanup(); editor = null; model.select(value.id) } }
    }
    if (deleting && item != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete this Library item?") },
        text = { Text("This removes its progress, rating and notes from this device. Other Library items and all Money and Schedule records are kept.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { model.delete(item!!.id, context) { deleting = false } }) { Text("Delete") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleting = false }) { Text("Keep item") } })
}

@Composable
internal fun LibraryRow(item: LibraryItemEntity, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LibraryCover(item)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text("${mediaLabel(item.kind)} · ${statusLabel(item.kind, item.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(progressLabel(item), style = MaterialTheme.typography.bodyMedium)
            if (item.rating != null) Text("${item.rating} / 5", style = MaterialTheme.typography.bodySmall)
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp))
    }
}

@Composable
private fun LibraryEditor(base: LibraryItemEntity, create: Boolean, progressOnly: Boolean, busy: Boolean, error: String?, cleanupCovers: (List<String>) -> Unit, close: () -> Unit, save: (LibraryItemEntity, () -> Unit) -> Unit) {
    var imported by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val draft = rememberSaveable(saver = mapSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>>(save = { it.toMap() }, restore = { it.map { e -> e.key to e.value as String }.toMutableStateMap() })) {
        mapOf("title" to base.title, "subtitle" to base.subtitle, "description" to base.description, "year" to (base.releaseYear?.toString() ?: ""), "status" to base.status.name,
            "rating" to (base.rating?.toString() ?: ""), "notes" to base.notes, "start" to (base.startedEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: ""),
            "end" to (base.completedEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: ""), "favorite" to base.favorite.toString(), "cover" to (base.coverPath ?: ""),
            "progress" to base.progress.toString(), "total" to (base.total?.toString() ?: ""), "season" to (base.season?.toString() ?: ""), "episode" to (base.episode?.toString() ?: ""),
            "seasons" to (base.totalSeasons?.toString() ?: ""), "platform" to base.platform, "hours" to (base.hoursPlayedMinutes?.let { BigDecimal(it).divide(BigDecimal(60), 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() } ?: ""),
            "percent" to (base.completionPercent?.toString() ?: ""), "author" to base.author, "isbn" to base.isbn).toList().toMutableStateMap()
    }
    fun number(key: String) = draft.getValue(key).trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: throw IllegalArgumentException("Use a whole number for $key") }
    fun date(key: String) = draft.getValue(key).trim().takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).toEpochDay() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var coverBusy by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf<String?>(null) }
    EditorFrame(if (progressOnly) "Update progress" else if (create) "Add ${mediaLabel(base.kind).lowercase()}" else "Edit Library item", busy || coverBusy, coverError ?: error, { cleanupCovers(imported); close() }, {
        val next = LibraryStatus.valueOf(draft.getValue("status"))
        val hoursText = draft.getValue("hours").trim()
        val minutes = if (hoursText.isBlank()) null else {
            require(hoursText.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) { "Use hours with up to two decimal places" }
            BigDecimal(hoursText).multiply(BigDecimal(60)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        }
        var value = base.copy(title = draft.getValue("title").trim(), subtitle = draft.getValue("subtitle").trim(), description = draft.getValue("description").trim(), releaseYear = number("year"),
            status = next, rating = number("rating"), notes = draft.getValue("notes"), startedEpochDay = date("start"), completedEpochDay = date("end"), favorite = draft["favorite"] == "true", coverPath = draft["cover"]?.ifBlank { null },
            progress = number("progress") ?: 0, total = number("total"), season = number("season"), episode = number("episode"), totalSeasons = number("seasons"), platform = draft.getValue("platform"),
            hoursPlayedMinutes = if (hoursText == (base.hoursPlayedMinutes?.let { BigDecimal(it).divide(BigDecimal(60), 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() } ?: "")) base.hoursPlayedMinutes else minutes,
            completionPercent = number("percent"), author = draft.getValue("author").trim(), isbn = draft.getValue("isbn").trim())
        if (next != base.status) value = value.withStatus(next)
        validateLibrary(value)
        save(value) { cleanupCovers(imported + listOfNotNull(base.coverPath)) }
    }) {
        val markDirty = LocalMarkDraftDirty.current
        @Composable fun input(key: String, label: String, numeric: Boolean = false, multi: Boolean = false) {
            Field(label, draft.getValue(key), !busy && !coverBusy, if (numeric) KeyboardType.Decimal else KeyboardType.Text, single = !multi) { draft[key] = it }
        }
        if (!progressOnly) {
            Text(mediaLabel(base.kind), style = MaterialTheme.typography.titleMedium)
            input("title", "Title"); input("subtitle", "Subtitle (optional)")
            input("description", "Synopsis (optional)", multi = true); input("year", "Release / publication year (optional)", true)
        }
        Choice("Status", draft.getValue("status"), LibraryStatus.entries.map { it.name to statusLabel(base.kind, it) }, !busy) { draft["status"] = it }
        when (base.kind) {
            LibraryKind.BOOK -> { input("author", "Author (optional)"); input("isbn", "ISBN (optional)"); input("progress", "Current page", true); input("total", "Total pages (optional)", true) }
            LibraryKind.SERIES -> { input("season", "Current season (optional)", true); input("episode", "Current episode (optional)", true); input("seasons", "Total seasons (optional)", true); input("progress", "Episodes watched", true); input("total", "Total episodes (optional)", true); Text("Episode totals are personal tracking aids. Reaching a released episode never marks a show completed.", style = MaterialTheme.typography.bodySmall) }
            LibraryKind.GAME -> { Choice("Platform", draft.getValue("platform"), listOf("" to "Not specified") + gamePlatforms.map { it to it }, !busy) { draft["platform"] = it }; input("hours", "Hours played (optional)", true); input("percent", "Completion percentage (optional)", true) }
            LibraryKind.MOVIE -> { }
        }
        if (!progressOnly) {
            input("rating", "Personal rating (1–5, optional)", true); input("notes", "Notes / review (optional)", multi = true)
            input("start", "Started date (YYYY-MM-DD, optional)"); input("end", "Completed / watched date (YYYY-MM-DD, optional)")
            Text("Status changes keep your progress, reviews and dates. Clear a date here only when you intend to remove it.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Favorite", Modifier.weight(1f)); Switch(draft["favorite"] == "true", { markDirty(); draft["favorite"] = it.toString() }, enabled = !busy) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
                coverBusy = true; coverError = null
                try { val name = importLibraryCover(context, uri); imported = imported + name; draft["cover"] = name; markDirty() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { coverError = "Could not import this cover. Choose a JPEG, PNG or WebP up to 5 MiB." }
                finally { coverBusy = false }
            } }
            LibraryCover(base.copy(coverPath = draft["cover"]?.ifBlank { null }))
            TextButton(enabled = !busy && !coverBusy, onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }) { Text("Choose local cover") }
            if (!draft["cover"].isNullOrBlank()) TextButton(enabled = !busy && !coverBusy, onClick = { markDirty(); draft["cover"] = "" }) { Text("Remove cover") }
            Text("Covers stay on this device and are not included in backup files. Titles, progress, dates and reviews are backed up.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
