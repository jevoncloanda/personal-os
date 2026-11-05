package com.jevon.personalos.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jevon.personalos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibraryFilter(val search: String = "", val kind: String = "", val status: String = "", val sort: String = "UPDATED", val page: Int = 0)
data class LibraryContent(val items: List<LibraryItemEntity>, val count: Int, val active: List<LibraryItemEntity>, val planned: List<LibraryItemEntity>, val completed: List<LibraryItemEntity>)
sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val content: LibraryContent) : LibraryState
    data object Error : LibraryState
}
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(private val db: PersonalDatabase, private val saved: SavedStateHandle) : ViewModel() {
    val filter = MutableStateFlow(LibraryFilter(saved["library-search"] ?: "", saved["library-kind"] ?: "", saved["library-status"] ?: "", saved["library-sort"] ?: "UPDATED", saved["library-page"] ?: 0))
    private val revision = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val state = combine(filter.debounce(200), revision) { f, _ -> f }.flatMapLatest { f ->
        db.invalidationTracker.createFlow("library_items").map {
            db.withTransaction {
                val dao = db.personalDao()
                LibraryState.Ready(LibraryContent(dao.libraryPage(f.search.trim(), f.kind, f.status, f.sort, 61, f.page * 60),
                    dao.libraryCount(f.search.trim(), f.kind, f.status), dao.libraryHighlights("ACTIVE", f.kind), dao.libraryHighlights("PLANNED", f.kind), dao.libraryHighlights("COMPLETED", f.kind))) as LibraryState
            }
        }.catch { emit(LibraryState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState.Loading)
    val selectedId = saved.getStateFlow<String?>("library-selected", null)
    val selected = selectedId.flatMapLatest { id -> db.invalidationTracker.createFlow("library_items").map { id?.let { db.personalDao().libraryItem(it) } } }
        .catch { error.value = "Could not open this item. Your records are unchanged."; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun select(id: String?) { saved["library-selected"] = id; error.value = null }
    fun setFilter(f: LibraryFilter) {
        filter.value = f
        saved["library-search"] = f.search; saved["library-kind"] = f.kind; saved["library-status"] = f.status; saved["library-sort"] = f.sort; saved["library-page"] = f.page
    }
    fun retry() { revision.value++ }
    fun clearError() { error.value = null }
    fun write(done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { block(); done() } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "Check the entered values" }
            catch (_: Exception) { error.value = "Could not save this change. Your previous records are preserved. Try again." }
            finally { busy.value = false }
        }
    }
    fun save(item: LibraryItemEntity, create: Boolean, done: () -> Unit) = write(done) {
        db.personalDao().saveLibrary(item.copy(updatedAtMillis = System.currentTimeMillis()), create)
    }
    fun cleanupCovers(context: android.content.Context, names: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            names.distinct().forEach { name ->
                try { db.withTransaction { if (db.personalDao().libraryCoverUses(name) == 0) deleteUnusedLibraryCover(context, name) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Artwork cleanup must not invalidate a successful record save. */ }
            }
        }
    }
    fun delete(id: String, context: android.content.Context, done: () -> Unit) = write(done) {
        val cover = db.personalDao().libraryItem(id)?.coverPath
        db.personalDao().deleteLibrary(id); select(null)
        cover?.let { cleanupCovers(context, listOf(it)) }
    }
}
