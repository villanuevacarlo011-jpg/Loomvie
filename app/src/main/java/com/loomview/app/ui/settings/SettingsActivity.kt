package com.loomview.app.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loomview.app.ui.theme.LoomviewTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 設定畫面：桌布行為、檔案與儲存、外觀、進階。
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appThemeState by viewModel.appTheme.collectAsStateWithLifecycle()
            val appLanguageState by viewModel.appLanguage.collectAsStateWithLifecycle()

            LoomviewTheme(
                appTheme = appThemeState ?: com.loomview.app.data.datastore.AppTheme.SYSTEM,
                appLanguage = appLanguageState ?: com.loomview.app.data.datastore.AppLanguage.SYSTEM
            ) {
                SettingsScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}
