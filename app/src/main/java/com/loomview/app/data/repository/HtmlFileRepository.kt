package com.loomview.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.loomview.app.data.local.HtmlFileDao
import com.loomview.app.data.local.HtmlFileEntity
import com.loomview.app.util.ThumbnailGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 所有跟「取得/記錄 HTML 檔案」有關的邏輯集中在這裡。
 *
 * 這一層只處理檔案系統層級的操作（Uri、權限、檔名、大小），
 * 絕不讀取、解析或快取 HTML 檔案本身的內容 —— WebView 會直接用 Uri 載入，
 * Repository 不介入內容，也不對內容格式做任何假設。
 */
@Singleton
class HtmlFileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HtmlFileDao,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun observeAll(): Flow<List<HtmlFileEntity>> = dao.observeAll()
    fun observeRecent(): Flow<List<HtmlFileEntity>> = dao.observeRecent()
    fun observeFavorites(): Flow<List<HtmlFileEntity>> = dao.observeFavorites()

    /**
     * 記錄一次「開啟」事件。嘗試取得長期權限；若來源 Intent 沒有給
     * FLAG_GRANT_PERSISTABLE_URI_PERMISSION（例如某些第三方 App 分享進來的檔案），
     * 則退化為僅本次工作階段可用，並如實記錄 hasPersistedPermission = false，
     * 交由 UI 層決定是否提示使用者重新選取以取得長期授權。
     */
    suspend fun recordOpened(uri: Uri, grantedPersistable: Boolean) {
        var persisted = false
        if (grantedPersistable) {
            persisted = tryTakePersistablePermission(uri)
        }

        val (displayName, size) = queryDisplayNameAndSize(uri)
        val existing = dao.findByUri(uri.toString())

        dao.upsert(
            HtmlFileEntity(
                uriString = uri.toString(),
                displayName = displayName,
                sizeBytes = size,
                lastOpenedAtMillis = System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false,
                hasPersistedPermission = persisted || (existing?.hasPersistedPermission ?: false),
                thumbnailPath = existing?.thumbnailPath,
            )
        )
    }

    suspend fun setThumbnailPath(uri: Uri, path: String) {
        dao.setThumbnailPath(uri.toString(), path)
    }

    suspend fun setFavorite(uri: Uri, isFavorite: Boolean) {
        dao.setFavorite(uri.toString(), isFavorite)
    }

    suspend fun remove(entity: HtmlFileEntity) {
        dao.delete(entity)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    /** 設定頁「清除快取」按鈕呼叫：刪除所有縮圖檔案，並清空資料庫裡的路徑欄位。 */
    fun clearThumbnailCache() {
        thumbnailGenerator.clearCache()
    }

    fun thumbnailCacheSizeBytes(): Long = thumbnailGenerator.cacheSizeBytes()

    /**
     * 供「檔案資訊」面板使用的公開查詢。純檔案系統層級 metadata
     * （名稱、大小、修改時間、MIME 類型），不讀取、不解析檔案內容。
     */
    fun queryFileMetadata(uri: Uri): FileMetadata {
        DocumentFile.fromSingleUri(context, uri)?.let { doc ->
            if (doc.exists()) {
                return FileMetadata(
                    displayName = doc.name ?: uri.lastPathSegment ?: "unknown.html",
                    sizeBytes = doc.length(),
                    lastModifiedMillis = doc.lastModified(),
                    mimeType = doc.type,
                )
            }
        }
        val (name, size) = queryDisplayNameAndSize(uri)
        return FileMetadata(displayName = name, sizeBytes = size, lastModifiedMillis = 0L, mimeType = null)
    }

    /**
     * 使用者透過 App 內建瀏覽器（SAF ACTION_OPEN_DOCUMENT）主動選取的檔案，
     * 一定會拿到 FLAG_GRANT_PERSISTABLE_URI_PERMISSION，所以這裡固定嘗試持久化。
     */
    fun buildOpenDocumentIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

    private fun tryTakePersistablePermission(uri: Uri): Boolean =
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        } catch (_: SecurityException) {
            false
        }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        // 優先走 DocumentFile，對 content:// 的相容性最好；失敗則退化為 Uri 最後一段字串。
        DocumentFile.fromSingleUri(context, uri)?.let { doc ->
            if (doc.exists()) {
                return (doc.name ?: uri.lastPathSegment ?: "unknown.html") to doc.length()
            }
        }

        var name = uri.lastPathSegment ?: "unknown.html"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }
}

/** 檔案系統層級的通用 metadata，供 UI 顯示用，不涉及檔案內容解析。 */
data class FileMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String?,
)
