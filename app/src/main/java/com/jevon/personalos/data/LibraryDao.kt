package com.jevon.personalos.data

import androidx.room.*
import com.jevon.personalos.library.validateLibrary

@Dao
abstract class LibraryDao : ScheduleDao() {
    @Query("SELECT * FROM library_items WHERE id=:id") abstract suspend fun libraryItem(id: String): LibraryItemEntity?
    @Query("SELECT * FROM library_items WHERE (:kind='' OR kind=:kind) AND (:status='' OR status=:status) AND instr(lower(title),lower(:search))>0 ORDER BY CASE WHEN :sort='TITLE' THEN lower(title) END ASC, CASE WHEN :sort='ADDED' THEN addedAtMillis ELSE updatedAtMillis END DESC,id LIMIT :limit OFFSET :offset")
    abstract suspend fun libraryPage(search: String, kind: String, status: String, sort: String, limit: Int = 61, offset: Int = 0): List<LibraryItemEntity>
    @Query("SELECT COUNT(*) FROM library_items WHERE (:kind='' OR kind=:kind) AND (:status='' OR status=:status) AND instr(lower(title),lower(:search))>0")
    abstract suspend fun libraryCount(search: String, kind: String, status: String): Int
    @Query("SELECT * FROM library_items WHERE status=:status AND (:kind='' OR kind=:kind) ORDER BY updatedAtMillis DESC,id LIMIT 4")
    abstract suspend fun libraryHighlights(status: String, kind: String): List<LibraryItemEntity>
    @Query("SELECT COUNT(*) FROM library_items WHERE id!=:id AND kind=:kind AND provider=:provider AND providerId=:providerId")
    protected abstract suspend fun providerDuplicates(id: String, kind: LibraryKind, provider: String?, providerId: String?): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertLibrary(item: LibraryItemEntity)
    @Update protected abstract suspend fun updateLibrary(item: LibraryItemEntity): Int
    @Query("DELETE FROM library_items WHERE id=:id") abstract suspend fun deleteLibrary(id: String)
    @Query("SELECT COUNT(*) FROM library_items WHERE coverPath=:name") abstract suspend fun libraryCoverUses(name: String): Int
    @Transaction open suspend fun saveLibrary(item: LibraryItemEntity, create: Boolean) {
        validateLibrary(item)
        require(providerDuplicates(item.id, item.kind, item.provider, item.providerId) == 0) { "This provider item is already in your Library" }
        if (create) insertLibrary(item) else {
            val old = requireNotNull(libraryItem(item.id)) { "Library item no longer exists" }
            require(old.kind == item.kind) { "Media type cannot change; create a separate item" }
            check(updateLibrary(item) == 1)
        }
    }
}
