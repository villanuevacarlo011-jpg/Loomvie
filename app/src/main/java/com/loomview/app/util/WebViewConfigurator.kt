package com.loomview.app.util

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Viewer 與 Wallpaper 共用的 WebView 基礎設定。
 *
 * 這裡刻意保持「通用容器」的設定 —— 開啟 JS、允許各種常見的網頁能力，
 * 不針對任何特定 HTML 的格式或依賴做開關判斷。任何 .html 檔案，
 * 不管是純靜態頁面還是使用 WebGL/Canvas 的動態內容，套用的都是同一套設定。
 *
 * allowFileAccess / DOM storage 開啟的理由：
 * 這是為了讓「HTML 引用同資料夾下的其他資源檔（圖片、JS、CSS）」這種常見情境也能正常運作 ——
 * 不是所有使用者的 HTML 都會是像某些匯出工具那樣的 all-in-one 自包含檔案。
 * App 不應該假設輸入內容的結構，因此保留完整能力，交給 HTML 自己決定要不要用。
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applyLoomviewBaseSettings(isWallpaperMode: Boolean) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        loadWithOverviewMode = true
        useWideViewPort = true
        mediaPlaybackRequiresUserGesture = false
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }

    // 【已解決】先前懷疑的 GPU 合成崩潰（AdrenoVK-0 "Shader compilation failed"）
    // 實際根因是 Compose 的 AndroidView 包裝 WebView 導致的合成管線初始化衝突，
    // 不是 GPU 合成本身的問題 —— 詳見 ViewerActivity 的架構說明。
    // WebView 現在改由傳統 View 系統直接承載（FrameLayout.addView），
    // 不再需要強制退回軟體渲染，讓 WebView 使用其預設的硬體加速合成即可。

    if (isWallpaperMode) {
        // 透明背景只在桌布模式需要 —— 目的是跟系統桌布合成時避免不自然的邊框閃爍。
        // 在 Viewer 模式套用透明背景會讓沒有明確設定 alpha:false 的 WebGL context
        // 疊加在畫面下方的黑色容器背景上，呈現「看似黑屏、實則沒有正確合成」的假象。
        // Viewer 模式維持不透明背景，跟一般瀏覽器行為一致。
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // 桌布模式：容器層級不轉發輸入事件 —— 見 ViewerActivity / LoomviewWallpaperService 說明。
        // 這裡不做任何跟內容有關的判斷，純粹是輸入事件轉發與否的開關。
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    } else {
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    webViewClient = object : WebViewClient() {
        // 除錯輔助：把 WebView 的載入生命週期印到 Logcat，
        // 用來確認 loadUrl 是否真的觸發、content:// URI 是否真的能被讀取，
        // 這是容器層級的通用診斷，不解析、不干涉內容本身。
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            android.util.Log.d("LoomviewWebView", "onPageStarted: $url")
        }

        override fun onPageFinished(view: WebView, url: String) {
            android.util.Log.d("LoomviewWebView", "onPageFinished: $url")
        }

        override fun onReceivedError(
            view: WebView,
            request: android.webkit.WebResourceRequest,
            error: android.webkit.WebResourceError,
        ) {
            android.util.Log.e(
                "LoomviewWebView",
                "onReceivedError: url=${request.url} code=${error.errorCode} desc=${error.description}",
            )
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: android.webkit.WebResourceRequest,
        ): Boolean {
            android.util.Log.d("LoomviewWebView", "shouldOverrideUrlLoading: ${request.url}")
            return false
        }
    }
    webChromeClient = object : WebChromeClient() {
        // 把 HTML 內部的 console.log/warn/error 轉發到 Logcat（tag: LoomviewWebConsole）。
        // 這純粹是除錯輔助 —— 對任何 HTML 內容都適用，不解析、不干涉訊息內容本身，
        // 方便在「畫面沒有正確渲染」時透過 adb logcat 觀察該 HTML 是否丟出了例外
        // （例如 WebGL context 取得失敗、shader 編譯錯誤等）。
        override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
            android.util.Log.d(
                "LoomviewWebConsole",
                "${message.message()} [${message.sourceId()}:${message.lineNumber()}]",
            )
            return true
        }

        override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
            // Android WebView 預設會為沒有 poster 屬性的 <video> 標籤顯示一個巨大的播放按鈕（白色三角形）。
            // 為了避免在桌布預覽或載入期間出現這個巨大三角形，我們回傳一個 1x1 的透明 Bitmap 來隱藏它。
            return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }
}
