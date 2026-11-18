package com.jevon.personalos.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
        LazyColumn(Modifier.fillMaxSize().testTag("LibraryDetail"), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = { model.select(null) }) { Text("All Library") } }
            val current = item
            if (current == null) item { Text("Item unavailable. It may have been removed. Return to your Library.") }
            else {
                item { LibraryDetailHeader(current) }
                item {
                    PorosCard(contentPadding = 16.dp, verticalSpacing = 12.dp) {
                        Text("Progress", style = MaterialTheme.typography.titleMedium)
                        LibraryTrackingProgress(current)
                        Choice("Status", current.status.name,
                            LibraryStatus.entries.map { it.name to statusLabel(current.kind, it) },
                            enabled = !busy, compact = true,
                        ) { model.save(current.withStatus(LibraryStatus.valueOf(it)), false) {} }
                        if (current.kind != LibraryKind.MOVIE) Button(
                            enabled = !busy,
                            onClick = { model.clearError(); editor = "progress" },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Update progress") }
                    }
                }
                item {
                    PorosCard(contentPadding = 16.dp, verticalSpacing = 12.dp) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Your notes", style = MaterialTheme.typography.titleMedium)
                            Text(current.rating?.let { "Rating · $it / 5" } ?: "Not rated",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Text(current.notes.ifBlank { "No notes yet" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    MoreOptions("Synopsis, dates & metadata") {
                        LibraryMetadata(current)
                    }
                }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            val completedColumns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
            LazyColumn(Modifier.fillMaxSize().testTag("LibraryList"), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionHeading("Your Library", "Add to Library", { chooseType = true }) }
                item { SearchField("Search titles", filter.search) { model.setFilter(filter.copy(search = it, page = 0)) } }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("" to "All media") + LibraryKind.entries.map { it.name to mediaLabel(it) }).forEach { (kind, label) ->
                            FilterChip(selected = filter.kind == kind,
                                onClick = { model.setFilter(filter.copy(kind = kind, page = 0)) },
                                label = { Text(label) }, shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    val compact = LocalDensity.current.fontScale <= 1.15f
                    FlowRow(
                        if (compact) Modifier.horizontalScroll(rememberScrollState())
                            else Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = if (compact) 2 else 1,
                    ) {
                        Choice("Status", filter.status, listOf("" to "All statuses", "PLANNED" to "Planned / Backlog", "ACTIVE" to "In progress", "COMPLETED" to "Completed", "ON_HOLD" to "On Hold", "DROPPED" to "Dropped"), compact = compact) { model.setFilter(filter.copy(status = it, page = 0)) }
                        Choice("Sort", filter.sort, listOf("UPDATED" to "Recently updated", "ADDED" to "Recently added", "TITLE" to "Title"), compact = compact) { model.setFilter(filter.copy(sort = it, page = 0)) }
                    }
                }
                if (filter.search.isBlank() && filter.status.isBlank() && filter.page == 0) {
                    if (data.active.isNotEmpty()) {
                        item { SectionHeading("Currently enjoying", "View all", { model.setFilter(filter.copy(status = "ACTIVE", page = 0)) }) }
                        items(data.active, key = { "active/${it.id}" }) { active ->
                            LibraryRow(active, featured = true, update = if (active.kind == LibraryKind.MOVIE) null else { { model.select(active.id); model.clearError(); editor = "progress" } }) { model.select(active.id) }
                        }
                    }
                    if (data.planned.isNotEmpty()) {
                        item { SectionHeading("Backlog & queue", "View backlog", { model.setFilter(filter.copy(status = "PLANNED", page = 0)) }) }
                        items(data.planned, key = { "planned/${it.id}" }) { LibraryRow(it) { model.select(it.id) } }
                    }
                    if (data.completed.isNotEmpty()) {
                        item { SectionHeading("Recently completed", "View completed", { model.setFilter(filter.copy(status = "COMPLETED", page = 0)) }) }
                        items(data.completed.chunked(completedColumns), key = { row -> "completed/${row.joinToString { it.id }}" }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { completed ->
                                    LibraryCompletedCard(completed, Modifier.weight(1f)) { model.select(completed.id) }
                                }
                                if (row.size < completedColumns) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
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
internal fun LibraryRow(
    item: LibraryItemEntity,
    update: (() -> Unit)? = null,
    featured: Boolean = false,
    open: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Surface(onClick = open, color = LocalAtmosphere.current.panel, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(if (featured) 16.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LibraryCover(item,
                    Modifier.size(if (featured) 64.dp else 48.dp, if (featured) 88.dp else 68.dp),
                    detailedFallback = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mediaLabel(item.kind), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(item.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = if (largeText) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis)
                    if (featured) {
                        val context = when (item.kind) {
                            LibraryKind.BOOK -> item.author
                            LibraryKind.GAME -> item.platform
                            else -> item.subtitle
                        }
                        if (context.isNotBlank()) Text(context,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (largeText) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis)
                        LibraryTrackingProgress(item, compact = true)
                    } else {
                        Text(statusLabel(item.kind, item.status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.status == LibraryStatus.ACTIVE || item.status == LibraryStatus.ON_HOLD) {
                            LibraryProgress(item, compact = true)
                        }
                        item.rating?.let {
                            Text("$it / 5", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (!featured) Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (update != null) FilledTonalButton(
                onClick = update,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text("Update progress", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun LibraryTrackingProgress(item: LibraryItemEntity, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LibraryProgress(item, compact)
        if (item.kind == LibraryKind.SERIES && (item.season != null || item.episode != null)) {
            Text("Episodes watched · ${item.progress}${item.total?.let { " of $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryCompletedCard(item: LibraryItemEntity, modifier: Modifier, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier, color = LocalAtmosphere.current.panel,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                LibraryCover(item, Modifier.fillMaxWidth().aspectRatio(1.25f), detailedFallback = true)
                item.rating?.let { rating ->
                    Surface(Modifier.align(Alignment.TopEnd).padding(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text("$rating / 5", Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall,
                maxLines = if (LocalDensity.current.fontScale > 1.3f) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis)
            Text("${mediaLabel(item.kind)} · ${statusLabel(item.kind, item.status)}" +
                item.completedEpochDay?.let { " · ${LocalDate.ofEpochDay(it)}" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryDetailHeader(item: LibraryItemEntity) {
    PorosCard(contentPadding = 16.dp, verticalSpacing = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LibraryCover(item, Modifier.size(112.dp, 160.dp), detailedFallback = true)
                    LibraryDetailTitle(item)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LibraryCover(item, Modifier.size(112.dp, 160.dp), detailedFallback = true)
                Column(Modifier.weight(1f)) { LibraryDetailTitle(item) }
            }
        }
    }
}

@Composable
private fun LibraryDetailTitle(item: LibraryItemEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(statusLabel(item.kind, item.status), style = MaterialTheme.typography.labelMedium,
            color = if (item.status == LibraryStatus.ACTIVE || item.status == LibraryStatus.COMPLETED)
                LocalAtmosphere.current.positive else MaterialTheme.colorScheme.primary)
        Text(item.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium)
        Text("${mediaLabel(item.kind)}${item.releaseYear?.let { " · $it" } ?: ""}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.author.isNotBlank()) Text(item.author, style = MaterialTheme.typography.bodyMedium)
        if (item.platform.isNotBlank()) Text(item.platform, style = MaterialTheme.typography.bodyMedium)
        if (item.favorite) Text("Favorite", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LibraryMetadata(item: LibraryItemEntity) {
    PorosCard(contentPadding = 16.dp, verticalSpacing = 4.dp) {
        if (item.description.isNotBlank()) DetailValue("Synopsis", item.description)
        item.totalSeasons?.let { DetailValue("Total seasons", it.toString()) }
        if (item.isbn.isNotBlank()) DetailValue("ISBN", item.isbn)
        DetailValue("Added", if (item.addedAtMillis == 0L) "Not recorded (legacy item)"
            else Instant.ofEpochMilli(item.addedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString())
        DetailValue("Started", item.startedEpochDay?.let(LocalDate::ofEpochDay)?.toString() ?: "Not recorded")
        DetailValue("Completed", item.completedEpochDay?.let(LocalDate::ofEpochDay)?.toString() ?: "Not recorded")
        DetailValue("Updated", Instant.ofEpochMilli(item.updatedAtMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString())
        if (item.provider != null) DetailValue("Metadata source", "${item.provider} · ${item.providerId}")
    }
}

@Composable
internal fun LibraryProgress(item: LibraryItemEntity, compact: Boolean = false) {
    val fraction = when {
        item.kind == LibraryKind.GAME -> item.completionPercent?.div(100f)
        item.kind in listOf(LibraryKind.BOOK, LibraryKind.SERIES) && item.total != null && item.total > 0 -> item.progress.toFloat() / item.total
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        Text(
            progressLabel(item),
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fraction != null) LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp),
            color = if (item.kind == LibraryKind.BOOK) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
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
    }, compactLayout = progressOnly, saveLabel = if (progressOnly) "Save progress" else null) {
        val markDirty = LocalMarkDraftDirty.current
        @Composable fun input(key: String, label: String, numeric: Boolean = false, multi: Boolean = false) {
            Field(label, draft.getValue(key), !busy && !coverBusy, if (numeric) KeyboardType.Decimal else KeyboardType.Text, single = !multi) { draft[key] = it }
        }
        if (!progressOnly) {
            Text(mediaLabel(base.kind), style = MaterialTheme.typography.titleMedium)
            input("title", "Title"); input("subtitle", "Subtitle (optional)")
            input("description", "Synopsis (optional)", multi = true); input("year", "Release / publication year (optional)", true)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LibraryCover(base, Modifier.size(48.dp, 68.dp), detailedFallback = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(base.title, style = MaterialTheme.typography.titleLarge)
                    Text(mediaLabel(base.kind), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (progressOnly) FinancialChoice("Status", draft.getValue("status"),
            LibraryStatus.entries.map { it.name to statusLabel(base.kind, it) }, !busy,
        ) { draft["status"] = it }
        else Choice("Status", draft.getValue("status"), LibraryStatus.entries.map { it.name to statusLabel(base.kind, it) }, !busy) { draft["status"] = it }
        when (base.kind) {
            LibraryKind.BOOK -> {
                if (progressOnly) LibraryInputPair(
                    first = { input("progress", "Current page", true) },
                    second = { input("total", "Total pages (optional)", true) },
                ) else {
                    input("author", "Author (optional)")
                    input("isbn", "ISBN (optional)")
                    input("progress", "Current page", true)
                    input("total", "Total pages (optional)", true)
                }
            }
            LibraryKind.SERIES -> {
                if (progressOnly) {
                    LibraryInputPair(
                        first = { input("season", "Current season (optional)", true) },
                        second = { input("episode", "Current episode (optional)", true) },
                    )
                    LibraryInputPair(
                        first = { input("progress", "Episodes watched", true) },
                        second = { input("total", "Total episodes (optional)", true) },
                    )
                } else {
                    input("season", "Current season (optional)", true)
                    input("episode", "Current episode (optional)", true)
                    input("seasons", "Total seasons (optional)", true)
                    input("progress", "Episodes watched", true)
                    input("total", "Total episodes (optional)", true)
                }
                Text("Episode totals are personal tracking aids. Reaching a released episode never marks a show completed.",
                    style = MaterialTheme.typography.bodySmall)
            }
            LibraryKind.GAME -> {
                if (!progressOnly) Choice("Platform", draft.getValue("platform"), listOf("" to "Not specified") + gamePlatforms.map { it to it }, !busy) { draft["platform"] = it }
                input("hours", "Hours played (optional)", true)
                input("percent", "Completion percentage (optional)", true)
            }
            LibraryKind.MOVIE -> { }
        }
        if (progressOnly && base.kind != LibraryKind.MOVIE) MoreOptions("Additional ${mediaLabel(base.kind).lowercase()} details", enabled = !busy) {
            when (base.kind) {
                LibraryKind.BOOK -> {
                    input("author", "Author (optional)")
                    input("isbn", "ISBN (optional)")
                }
                LibraryKind.SERIES -> input("seasons", "Total seasons (optional)", true)
                LibraryKind.GAME -> FinancialChoice("Platform", draft.getValue("platform"),
                    listOf("" to "Not specified") + gamePlatforms.map { it to it }, !busy,
                ) { draft["platform"] = it }
                LibraryKind.MOVIE -> { }
            }
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

@Composable
private fun LibraryInputPair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.15f) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first()
            second()
        }
    }
}
