package com.loomview.app.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.loomview.app.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SetWallpaperViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    suspend fun applyAsWallpaper(uri: Uri) {
        settingsDataStore.setWallpaperUri(uri.toString())
    }
}
