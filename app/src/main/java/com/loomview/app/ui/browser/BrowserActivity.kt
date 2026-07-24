package com.loomview.app.ui.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.loomview.app.data.datastore.AppLanguage
import com.loomview.app.data.datastore.AppTheme
import com.loomview.app.data.datastore.SettingsDataStore
import com.loomview.app.ui.settings.SettingsActivity
import com.loomview.app.ui.theme.LoomviewTheme
import com.loomview.app.ui.viewer.ViewerActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * App 啟動頁 / 檔案瀏覽器。
 */
@AndroidEntryPoint
class BrowserActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { openInViewer(it, grantedPersistable = true) }
        }

        setContent {
            val appTheme by settingsDataStore.appTheme.collectAsState(initial = AppTheme.SYSTEM)
            val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = AppLanguage.SYSTEM)
            LoomviewTheme(appTheme = appTheme, appLanguage = appLanguage) {
                BrowserScreen(
                    viewModel = viewModel,
                    onImportClick = { openDocumentLauncher.launch(arrayOf("text/html", "*/*")) },
                    onFileClick = { uriString ->
                        openInViewer(android.net.Uri.parse(uriString), grantedPersistable = true)
                    },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }

    private fun openInViewer(uri: android.net.Uri, grantedPersistable: Boolean) {
        val intent = Intent(this, ViewerActivity::class.java).apply {
            data = uri
            if (grantedPersistable) {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }
}
