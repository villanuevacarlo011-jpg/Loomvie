package com.loomview.app.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loomview.app.data.repository.HtmlFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val uri: Uri? = null,
    val displayName: String = "",
    val isFavorite: Boolean = false,
    val sizeBytes: Long = 0L,
    val lastModifiedMillis: Long = 0L,
    val mimeType: String? = null,
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: HtmlFileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    /**
     * 載入一個檔案並記錄開啟紀錄。
     * 不做任何內容驗證 —— 就算這個 Uri 指向的東西根本不是有效 HTML，
     * 也讓 WebView 自己去面對載入失敗，App 邏輯層不介入判斷。
     */
    fun loadFile(uri: Uri, grantedPersistable: Boolean) {
        _state.value = _state.value.copy(uri = uri, displayName = uri.lastPathSegment ?: "")
        viewModelScope.launch {
            repository.recordOpened(uri, grantedPersistable)
            val metadata = repository.queryFileMetadata(uri)
            _state.value = _state.value.copy(
                displayName = metadata.displayName,
                sizeBytes = metadata.sizeBytes,
                lastModifiedMillis = metadata.lastModifiedMillis,
                mimeType = metadata.mimeType,
            )
        }
    }

    fun toggleFavorite() {
        val uri = _state.value.uri ?: return
        val newValue = !_state.value.isFavorite
        _state.value = _state.value.copy(isFavorite = newValue)
        viewModelScope.launch {
            repository.setFavorite(uri, newValue)
        }
    }

    fun updateThumbnailPath(path: String) {
        val uri = _state.value.uri ?: return
        viewModelScope.launch {
            repository.setThumbnailPath(uri, path)
        }
    }
}
