package com.loomview.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.Window
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy

/**
 * 為任意 HTML 檔案產生一張靜態縮圖，供瀏覽器 Grid 模式使用。
 *
 * 通用容器原則同樣適用在這裡：產生縮圖的方式是「把頁面渲染出來、截一張圖」，
 * 不解析 HTML 結構、不判斷內容類型、不對任何特定格式做特殊處理 ——
 * 對純靜態頁面、CSS 動畫、WebGL 內容，這裡做的事完全一樣：
 * 載入、等待一小段時間讓首幀畫面穩定、截圖、存檔。
 *
 * 縮圖快取獨立存放在 App 私有目錄（cacheDir），不依賴來源檔案的 SAF 權限存續，
 * 即使使用者之後撤銷了某個資料夾的授權，縮圖仍能顯示（直到使用者手動清除快取）。
 */
@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "thumbnails").apply { mkdirs() }
    }

    /** 縮圖若已存在快取則直接回傳路徑，否則回傳 null（呼叫端決定要不要觸發產生） */
    fun getCachedThumbnailPath(uri: Uri): String? {
        val file = cacheFileFor(uri)
        return if (file.exists()) file.absolutePath else null
    }

    /** 供 ViewerActivity 在前景時呼叫，使用 PixelCopy 能擷取到最完美的 WebGL 與動態畫面 */
    fun captureFromActiveWebView(uri: Uri, webView: WebView, window: Window, onComplete: (String?) -> Unit = { _ -> }) {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) {
            onComplete(null)
            return
        }

        try {
            val scale = 0.5f
            val thumbWidth = (width * scale).toInt()
            val thumbHeight = (height * scale).toInt()
            val bitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val location = IntArray(2)
                webView.getLocationInWindow(location)
                
                // 計算 WebView 在 Window 內的真實邊界
                val windowRect = android.graphics.Rect()
                window.decorView.getWindowVisibleDisplayFrame(windowRect)
                
                val rect = android.graphics.Rect(
                    Math.max(location[0], windowRect.left),
                    Math.max(location[1], windowRect.top),
                    Math.min(location[0] + width, windowRect.right),
                    Math.min(location[1] + height, windowRect.bottom)
                )
                
                if (rect.width() <= 0 || rect.height() <= 0) {
                    fallbackSoftwareCapture(uri, webView, bitmap, onComplete)
                    return
                }

                // PixelCopy 的 dest Bitmap 必須與 srcRect 大小完全一致，否則在許多 Android 裝置上會直接失敗 (ERROR_DESTINATION_INVALID)
                val fullBitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)

                PixelCopy.request(
                    window,
                    rect,
                    fullBitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            // 擷取成功後，手動縮小成我們需要的縮圖大小
                            val scaled = Bitmap.createScaledBitmap(fullBitmap, thumbWidth, thumbHeight, true)
                            fullBitmap.recycle() // 釋放大圖
                            bitmap.recycle() // 釋放未使用的 fallback bitmap
                            saveBitmapAsync(uri, scaled, onComplete)
                        } else {
                            fullBitmap.recycle()
                            fallbackSoftwareCapture(uri, webView, bitmap, onComplete)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } else {
                fallbackSoftwareCapture(uri, webView, bitmap, onComplete)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }

    private fun fallbackSoftwareCapture(uri: Uri, webView: WebView, bitmap: Bitmap, onComplete: (String?) -> Unit) {
        try {
            val canvas = Canvas(bitmap)
            val scale = bitmap.width.toFloat() / webView.width.toFloat()
            canvas.scale(scale, scale)
            webView.draw(canvas)
            saveBitmapAsync(uri, bitmap, onComplete)
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap.recycle()
            onComplete(null)
        }
    }

    private fun saveBitmapAsync(uri: Uri, bitmap: Bitmap, onComplete: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = cacheFileFor(uri)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(file.absolutePath) }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(null) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun cacheSizeBytes(): Long =
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun cacheFileFor(uri: Uri): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.png")
    }

    companion object {
        // No longer used
    }
}
