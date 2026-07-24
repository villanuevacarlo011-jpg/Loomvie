package com.loomview.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HtmlFileDao {

    @Query("SELECT * FROM html_files ORDER BY lastOpenedAtMillis DESC")
    fun observeAll(): Flow<List<HtmlFileEntity>>

    @Query("SELECT * FROM html_files ORDER BY lastOpenedAtMillis DESC LIMIT 20")
    fun observeRecent(): Flow<List<HtmlFileEntity>>

    @Query("SELECT * FROM html_files WHERE isFavorite = 1 ORDER BY lastOpenedAtMillis DESC")
    fun observeFavorites(): Flow<List<HtmlFileEntity>>

    @Query("SELECT * FROM html_files WHERE uriString = :uriString LIMIT 1")
    suspend fun findByUri(uriString: String): HtmlFileEntity?

    @Upsert
    suspend fun upsert(entity: HtmlFileEntity)

    @Query("UPDATE html_files SET isFavorite = :isFavorite WHERE uriString = :uriString")
    suspend fun setFavorite(uriString: String, isFavorite: Boolean)

    @Query("UPDATE html_files SET thumbnailPath = :thumbnailPath WHERE uriString = :uriString")
    suspend fun setThumbnailPath(uriString: String, thumbnailPath: String?)

    @Delete
    suspend fun delete(entity: HtmlFileEntity)

    @Query("DELETE FROM html_files")
    suspend fun clearAll()
}
