package com.loomview.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一筆使用者開啟過的 HTML 檔案紀錄。
 *
 * 刻意只存檔案系統層級的中性 metadata（Uri、顯示名稱、大小、時間）。
 * 不存放任何跟檔案內容有關的欄位（例如解析度、幀數、内嵌資源大小等） ——
 * 這類資訊需要解析 HTML 內容才能取得，違反「App 不對內容做任何假設」的原則。
 * 若未來要顯示更豐富的檔案資訊，應該用檔案系統 API（DocumentFile）即時查詢，
 * 而不是把內容相關欄位固化進資料庫 schema。
 */
@Entity(tableName = "html_files")
data class HtmlFileEntity(
    @PrimaryKey
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastOpenedAtMillis: Long,
    val isFavorite: Boolean = false,
    /** 是否成功取得長期（persistable）Uri 權限；影響 UI 是否提示重新選取檔案 */
    val hasPersistedPermission: Boolean = true,
    /** 縮圖快取檔案的絕對路徑（App 私有 cacheDir），null 代表尚未產生或產生失敗 */
    val thumbnailPath: String? = null,
)
