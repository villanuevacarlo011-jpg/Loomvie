package com.loomview.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loomview.app.data.datastore.AppTheme
import com.loomview.app.data.datastore.AppLanguage
import com.loomview.app.data.datastore.RenderQuality
import com.loomview.app.data.datastore.SettingsDataStore
import com.loomview.app.data.repository.HtmlFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val repository: HtmlFileRepository,
) : ViewModel() {

    val renderQuality: StateFlow<RenderQuality?> = dataStore.renderQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fpsCap: StateFlow<Int?> = dataStore.fpsCap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pauseWhenScreenOff: StateFlow<Boolean?> = dataStore.pauseWhenScreenOff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val downgradeOnLowBattery: StateFlow<Boolean?> = dataStore.downgradeOnLowBattery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appTheme: StateFlow<AppTheme?> = dataStore.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { dataStore.setAppTheme(theme) }
    }

    val appLanguage: StateFlow<AppLanguage?> = dataStore.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { dataStore.setAppLanguage(language) }
    }

    fun setRenderQuality(quality: RenderQuality) {
        viewModelScope.launch { dataStore.setRenderQuality(quality) }
    }

    fun setFpsCap(fps: Int) {
        viewModelScope.launch { dataStore.setFpsCap(fps) }
    }

    fun setPauseWhenScreenOff(enabled: Boolean) {
        viewModelScope.launch { dataStore.setPauseWhenScreenOff(enabled) }
    }

    fun setDowngradeOnLowBattery(enabled: Boolean) {
        viewModelScope.launch { dataStore.setDowngradeOnLowBattery(enabled) }
    }

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        _cacheSizeBytes.value = repository.thumbnailCacheSizeBytes()
    }

    fun clearThumbnailCache() {
        repository.clearThumbnailCache()
        refreshCacheSize()
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            repository.clearThumbnailCache()
            refreshCacheSize()
        }
    }
}
