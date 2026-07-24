package com.loomview.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "loomview_settings")

/**
 * 集中管理所有使用者可調設定，以及「目前作為桌布的 HTML 檔案 Uri」這個核心狀態。
 *
 * 注意：這裡只存 Uri 字串本身，不快取、不解析、不驗證檔案內容。
 * WallpaperService 啟動時只是把這個 Uri 丟給 WebView.loadUrl()，
 * 內容能不能正常渲染完全是該 HTML 自己的事。
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        val RENDER_QUALITY = stringPreferencesKey("render_quality") // NATIVE / STANDARD / BATTERY_SAVER
        val FPS_CAP = intPreferencesKey("fps_cap") // 15 / 30 / 60
        val PAUSE_WHEN_SCREEN_OFF = booleanPreferencesKey("pause_when_screen_off")
        val DOWNGRADE_ON_LOW_BATTERY = booleanPreferencesKey("downgrade_on_low_battery")
        val DEFAULT_FOLDER_URI = stringPreferencesKey("default_folder_uri")
        val APP_THEME = stringPreferencesKey("app_theme") // LIGHT / DARK / SYSTEM
        val APP_LANGUAGE = stringPreferencesKey("app_language") // SYSTEM / EN / ZH_TW
    }

    val wallpaperUri: Flow<String?> =
        context.dataStore.data.map { it[Keys.WALLPAPER_URI] }

    suspend fun setWallpaperUri(uri: String) {
        context.dataStore.edit { it[Keys.WALLPAPER_URI] = uri }
    }

    val renderQuality: Flow<RenderQuality> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.RENDER_QUALITY]?.let { RenderQuality.valueOf(it) } ?: RenderQuality.STANDARD
        }

    suspend fun setRenderQuality(quality: RenderQuality) {
        context.dataStore.edit { it[Keys.RENDER_QUALITY] = quality.name }
    }

    val fpsCap: Flow<Int> =
        context.dataStore.data.map { it[Keys.FPS_CAP] ?: 30 }

    suspend fun setFpsCap(fps: Int) {
        context.dataStore.edit { it[Keys.FPS_CAP] = fps }
    }

    val pauseWhenScreenOff: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PAUSE_WHEN_SCREEN_OFF] ?: true }

    suspend fun setPauseWhenScreenOff(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PAUSE_WHEN_SCREEN_OFF] = enabled }
    }

    val downgradeOnLowBattery: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DOWNGRADE_ON_LOW_BATTERY] ?: true }

    suspend fun setDowngradeOnLowBattery(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOWNGRADE_ON_LOW_BATTERY] = enabled }
    }

    val defaultFolderUri: Flow<String?> =
        context.dataStore.data.map { it[Keys.DEFAULT_FOLDER_URI] }

    suspend fun setDefaultFolderUri(uri: String) {
        context.dataStore.edit { it[Keys.DEFAULT_FOLDER_URI] = uri }
    }

    val appTheme: Flow<AppTheme> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.APP_THEME]?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM
        }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    val appLanguage: Flow<AppLanguage> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.APP_LANGUAGE]?.let { AppLanguage.valueOf(it) } ?: AppLanguage.SYSTEM
        }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }
}

enum class RenderQuality { NATIVE, STANDARD, BATTERY_SAVER }
enum class AppTheme { LIGHT, DARK, SYSTEM }
enum class AppLanguage { SYSTEM, EN, ZH_TW }
