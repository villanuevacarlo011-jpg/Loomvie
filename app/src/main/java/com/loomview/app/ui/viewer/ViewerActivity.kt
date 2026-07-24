package com.loomview.app.ui.viewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import com.loomview.app.data.datastore.AppLanguage
import com.loomview.app.data.datastore.AppTheme
import com.loomview.app.data.datastore.SettingsDataStore
import com.loomview.app.ui.theme.LoomviewTheme
import com.loomview.app.util.applyLoomviewBaseSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 通用 HTML 檢視器。
 *
 * 【重要架構決策】WebView 不透過 Compose 的 AndroidView 建立，而是用傳統
 * View 系統（FrameLayout.addView）直接承載。
 *
 * 背景：實測證實 Compose 的 AndroidView 包裝 WebView 時，在特定裝置
 * （Qualcomm Adreno GPU 驅動）上會導致 WebView 內部的合成管線初始化失敗
 * （症狀：AdrenoVK-0 "Shader compilation failed" / "Pipeline create failed"，
 * 畫面呈現全黑，即使該 HTML 在系統瀏覽器如 Chrome/Brave 上能正常顯示）。
 * 純 `setContentView(WebView)`（不經過 Compose）與純 Compose UI（不含 WebView）
 * 兩者個別測試都正常，唯獨兩者疊加（AndroidView 包 WebView）才會觸發問題 ——
 * 這是 Compose 的 AndroidViewHolder 橋接層與 WebView 原生合成器之間的初始化
 * 時序衝突，屬於框架層級的相容性問題，不是我們的 WebView 設定或 HTML 內容的問題。
 *
 * 因此這裡採用「WebView 用傳統 View 系統承載，其餘 UI（工具列/選單/BottomSheet）
 * 用 ComposeView 疊加在上層」的混合架構：ComposeView 本身不包 WebView，
 * 不會觸發同樣的合成管線衝突，同時保留原本規劃的完整 Compose UI。
 *
 * 【觸控事件轉發】ComposeView 疊在 WebView 上方，Android 預設會讓上層 View
 * 攔截所有觸控事件，導致工具列淡出後使用者點擊畫面完全無法穿透給下方的
 * WebView（也就摸不到 HTML 內部的互動邏輯）。這裡自訂一個 FrameLayout 子類別
 * 覆寫 dispatchTouchEvent：工具列隱藏時，直接把觸控事件轉發給 WebView 處理
 * （同時喚回工具列），工具列顯示時維持標準分派流程，讓使用者能點擊按鈕。
 */
@AndroidEntryPoint
class ViewerActivity : ComponentActivity() {

    private val viewModel: ViewerViewModel by viewModels()
    private val setWallpaperViewModel: SetWallpaperViewModel by viewModels()
    private lateinit var webView: WebView
    private var loadedUriString: String? = null

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var thumbnailGenerator: com.loomview.app.util.ThumbnailGenerator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var toolbarVisibleState by mutableStateOf(true)

        webView = WebView(this).apply {
            applyLoomviewBaseSettings(isWallpaperMode = false)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val rootLayout = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (!toolbarVisibleState) {
                    // 工具列已隱藏：把事件轉發給 WebView 自己處理，
                    // 對任何 HTML 的觸控互動能力完整保留，不經過 Compose 這層。
                    // 手指按下的瞬間同時喚回工具列。
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                        toolbarVisibleState = true
                    }
                    return webView.dispatchTouchEvent(ev)
                }
                // 工具列顯示中：交還標準分派流程，讓使用者能點擊工具列按鈕，
                // 或點擊工具列以外的區域讓事件穿透到 WebView（標準 ViewGroup 行為）。
                return super.dispatchTouchEvent(ev)
            }
        }

        rootLayout.addView(webView)

        // 工具列、選單、BottomSheet 等純 UI（不含 WebView）疊加在上層，
        // 用一般的 ComposeView 承載，不會觸發 AndroidView 包 WebView 的問題，
        // 因為這個 ComposeView 內部完全沒有嵌入任何傳統 View 系統元件。
        val overlayComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.TOP,
            )
            setContent {
                val appTheme by settingsDataStore.appTheme.collectAsState(initial = AppTheme.SYSTEM)
                val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = AppLanguage.SYSTEM)
                val state by viewModel.state.collectAsStateWithLifecycle()
                LoomviewTheme(appTheme = appTheme, appLanguage = appLanguage) {
                    ViewerOverlay(
                        state = state,
                        toolbarVisible = toolbarVisibleState,
                        onToolbarTimeout = { toolbarVisibleState = false },
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onApplyWallpaper = setWallpaperViewModel::applyAsWallpaper,
                    )
                }
            }
        }
        rootLayout.addView(overlayComposeView)

        setContentView(rootLayout)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentUri = viewModel.state.value.uri
                if (currentUri != null) {
                    // 隱藏 Compose UI（三個點等），確保截圖時只拍到純淨的 HTML/WebGL 畫面
                    overlayComposeView.visibility = android.view.View.INVISIBLE
                    // 給 UI 一點時間套用隱藏狀態
                    overlayComposeView.post {
                        thumbnailGenerator.captureFromActiveWebView(currentUri, webView, window) { path ->
                            if (path != null) {
                                viewModel.updateThumbnailPath(path)
                            }
                            runOnUiThread { finish() }
                        }
                    }
                } else {
                    finish()
                }
            }
        })

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onRestart() {
        super.onRestart()
        // 當使用者從設定桌布畫面或其他 App 返回時，強制重新載入。
        // 這能確保背景被系統回收的 WebGL Context 或資源能被正常恢復，避免畫面卡在載入狀態。
        if (::webView.isInitialized) webView.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webView.isInitialized) webView.destroy()
    }

    /**
     * 從三種可能的來源情境取出檔案 Uri，載入一次後記錄，避免重複 loadUrl
     * 同一個 content:// Uri（會導致底層 provider 讀取 session 被重複消耗）。
     */
    private fun handleIncomingIntent(intent: Intent) {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.parcelableExtraCompat(Intent.EXTRA_STREAM)
            else -> intent.data ?: intent.parcelableExtraCompat(EXTRA_HTML_URI)
        }

        val grantedPersistable =
            (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0

        uri?.let { resolvedUri ->
            viewModel.loadFile(resolvedUri, grantedPersistable)
            if (resolvedUri.toString() != loadedUriString) {
                webView.loadUrl(resolvedUri.toString())
                loadedUriString = resolvedUri.toString()
            }
        }
    }

    companion object {
        const val EXTRA_HTML_URI = "com.loomview.app.EXTRA_HTML_URI"
    }
}

/**
 * Intent.getParcelableExtra(String) 在 API 33 起被標記為 deprecated，
 * 型別安全版本 getParcelableExtra(String, Class<T>) 要求 API 33+。
 * App 的 minSdk 是 26，仍需支援舊版本，因此依系統版本分流：
 * Android 13+ 用新的型別安全 API，較舊版本使用舊版 API（明確 suppress，
 * 因為在 minSdk 範圍內沒有更好的替代方案）。
 */
private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtraCompat(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
