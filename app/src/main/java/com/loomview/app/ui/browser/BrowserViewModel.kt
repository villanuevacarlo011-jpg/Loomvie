package com.loomview.app.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loomview.app.data.local.HtmlFileEntity
import com.loomview.app.data.repository.HtmlFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BrowserTab { ALL, RECENT, FAVORITES }
enum class ViewMode { GRID, LIST }

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: HtmlFileRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(BrowserTab.ALL)
    val selectedTab: StateFlow<BrowserTab> = _selectedTab.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    val files: StateFlow<List<HtmlFileEntity>> = _selectedTab
        .flatMapLatest { tab ->
            when (tab) {
                BrowserTab.ALL -> repository.observeAll()
                BrowserTab.RECENT -> repository.observeRecent()
                BrowserTab.FAVORITES -> repository.observeFavorites()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: BrowserTab) {
        _selectedTab.value = tab
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    }

    fun toggleFavorite(entity: HtmlFileEntity) {
        viewModelScope.launch {
            repository.setFavorite(android.net.Uri.parse(entity.uriString), !entity.isFavorite)
        }
    }

    fun removeFile(entity: HtmlFileEntity) {
        viewModelScope.launch {
            repository.remove(entity)
        }
    }
}

