package com.loomview.app.wallpaper

import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.webkit.WebView
import com.loomview.app.data.datastore.RenderQuality
import com.loomview.app.data.datastore.SettingsDataStore
import com.loomview.app.util.applyLoomviewBaseSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通用 HTML Live Wallpaper 引擎。
 *
 * 核心原則（呼應規劃書 6.1 節）：這裡完全不知道、也不關心目前渲染的 HTML 裡面是什麼。
 * Engine 只做兩件容器層級的事：
 *   1. 不把輸入事件轉發給 WebView（桌布環境本來就拿不到大部分手勢）
 *   2. 螢幕不可見時暫停渲染，可見時恢復
 *
 * 任何跟畫面內容有關的行為（動不動、怎麼動、要不要有 idle 動畫）都是該 HTML 自己的責任。
 */
@AndroidEntryPoint
class LoomviewLiveWallpaper : WallpaperService() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreateEngine(): Engine = LoomviewEngine()

    inner class LoomviewEngine : Engine() {

        private var webView: WebView? = null
        private val engineScope = CoroutineScope(Dispatchers.Main + Job())
        private var currentUriString: String? = null
        private var isVisibleState = false
        private var renderQuality = RenderQuality.STANDARD
        private var pauseWhenScreenOff = true
        private var downgradeOnLowBattery = true
        private var isScreenOn = true
        private var isBatteryLowAndUnplugged = false
        private var currentScale = 1.0f
        private var currentFpsCap = 30
        private var lastRenderWidth = 0
        private var lastRenderHeight = 0

        private var virtualDisplay: VirtualDisplay? = null
        private var presentation: Presentation? = null
        private var displayManager: DisplayManager? = null

        private val screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        updateRenderingState()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        updateRenderingState()
                    }
                }
            }
        }

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (scale > 0) level * 100 / scale.toFloat() else 100f
                    
                    val wasLow = isBatteryLowAndUnplugged
                    isBatteryLowAndUnplugged = !isCharging && batteryPct <= 15f
                    if (wasLow != isBatteryLowAndUnplugged) {
                        updateWebViewScale()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)

            displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            this@LoomviewLiveWallpaper.registerReceiver(screenStateReceiver, screenFilter)

            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            this@LoomviewLiveWallpaper.registerReceiver(batteryReceiver, batteryFilter)

            webView = WebView(this@LoomviewLiveWallpaper).apply {
                // isWallpaperMode = true：容器不轉發輸入事件，僅此而已 —— 不做任何內容假設。
                applyLoomviewBaseSettings(isWallpaperMode = true)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        applyFpsCapToWebView()
                    }
                }
            }

            // 監聽目前選定的桌布 Uri；使用者之後換了另一份 HTML 檔案時，
            // 桌布會即時跟著更新，不需要重新套用桌布。
            engineScope.launch {
                settingsDataStore.wallpaperUri.collectLatest { uriString ->
                    if (uriString != null && uriString != currentUriString) {
                        currentUriString = uriString
                        webView?.loadUrl(uriString)
                    }
                }
            }

            engineScope.launch {
                settingsDataStore.renderQuality.collectLatest { 
                    renderQuality = it
                    updateWebViewScale()
                }
            }
            engineScope.launch {
                settingsDataStore.pauseWhenScreenOff.collectLatest { 
                    pauseWhenScreenOff = it
                    updateRenderingState()
                }
            }
            engineScope.launch {
                settingsDataStore.downgradeOnLowBattery.collectLatest { 
                    downgradeOnLowBattery = it
                    updateWebViewScale()
                }
            }
            engineScope.launch {
                settingsDataStore.fpsCap.collectLatest { 
                    currentFpsCap = it
                    applyFpsCapToWebView()
                    applySurfaceFrameRate()
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            updateRenderingState()
        }

        private fun updateRenderingState() {
            val shouldRender = isVisibleState && (isScreenOn || !pauseWhenScreenOff)
            if (shouldRender) {
                webView?.onResume()
            } else {
                webView?.onPause()
            }
        }

        private fun updateWebViewScale() {
            val effectiveQuality = if (downgradeOnLowBattery && isBatteryLowAndUnplugged) {
                RenderQuality.BATTERY_SAVER
            } else {
                renderQuality
            }
            
            val scale = when (effectiveQuality) {
                RenderQuality.NATIVE -> 1.0f
                RenderQuality.STANDARD -> 0.75f
                RenderQuality.BATTERY_SAVER -> 0.5f
            }
            currentScale = scale
            
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            
            // 使用真實螢幕解析度作為基準，不管目前 Surface 被縮放成多少
            val baseWidth = metrics.widthPixels
            val baseHeight = metrics.heightPixels

            val renderWidth = (baseWidth * scale).toInt()
            val renderHeight = (baseHeight * scale).toInt()
            
            // VirtualDisplay 始終維持跟螢幕實體大小一致，只有當旋轉螢幕時才需要重建 VirtualDisplay
            if (baseWidth != lastRenderWidth || baseHeight != lastRenderHeight) {
                lastRenderWidth = baseWidth
                lastRenderHeight = baseHeight
                if (baseWidth > 0 && baseHeight > 0 && surfaceHolder.surface.isValid) {
                    recreateVirtualDisplay(surfaceHolder, baseWidth, baseHeight)
                }
            }
            
            // 利用 View 的 scaleX/Y 搭配較小的 LayoutParams，
            // 讓 WebView 以低解析度渲染，但被 View 系統自動硬體延展到滿版
            webView?.let { wv ->
                val lp = wv.layoutParams ?: android.widget.FrameLayout.LayoutParams(renderWidth, renderHeight)
                lp.width = renderWidth
                lp.height = renderHeight
                wv.layoutParams = lp
                
                wv.pivotX = 0f
                wv.pivotY = 0f
                wv.scaleX = 1.0f / scale
                wv.scaleY = 1.0f / scale
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            updateWebViewScale()
            applySurfaceFrameRate()
        }

        private fun applySurfaceFrameRate() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    if (surfaceHolder.surface.isValid) {
                        surfaceHolder.surface.setFrameRate(
                            currentFpsCap.toFloat(),
                            android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun applyFpsCapToWebView() {
            val js = """
                (function() {
                    if (!window.__loomview_original_raf) {
                        window.__loomview_original_raf = window.requestAnimationFrame;
                    }
                    window.__loomview_fps = $currentFpsCap;
                    if (!window.__loomview_raf_hooked) {
                        window.__loomview_raf_hooked = true;
                        var lastTime = 0;
                        window.requestAnimationFrame = function(callback) {
                            return window.__loomview_original_raf.call(window, function(time) {
                                var interval = 1000 / window.__loomview_fps;
                                if (time - lastTime >= interval) {
                                    lastTime = time;
                                    callback(time);
                                } else {
                                    window.requestAnimationFrame(callback);
                                }
                            });
                        };
                    }
                })();
            """.trimIndent()
            webView?.evaluateJavascript(js, null)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event == null || webView == null) return
            
            val scaledEvent = MotionEvent.obtain(event)
            if (currentScale != 1.0f) {
                scaledEvent.setLocation(event.x * currentScale, event.y * currentScale)
            }
            // 轉發觸控事件給 WebView，讓 HTML 能接收互動
            webView?.dispatchTouchEvent(scaledEvent)
            scaledEvent.recycle()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            updateRenderingState()
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                this@LoomviewLiveWallpaper.unregisterReceiver(screenStateReceiver)
                this@LoomviewLiveWallpaper.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            engineScope.cancel()
            releaseVirtualDisplay()
            webView?.apply {
                stopLoading()
                onPause()
                destroy()
            }
            webView = null
        }

        private fun recreateVirtualDisplay(holder: SurfaceHolder, width: Int, height: Int) {
            releaseVirtualDisplay()
            if (width <= 0 || height <= 0 || !holder.surface.isValid) return

            virtualDisplay = displayManager?.createVirtualDisplay(
                "LoomviewWallpaper",
                width,
                height,
                resources.displayMetrics.densityDpi,
                holder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            )

            virtualDisplay?.display?.let { display ->
                // 使用 Presentation 將 WebView 附著在一個真實的、硬體加速的 Window 上，
                // 這樣 WebGL 才能成功建立 Pipeline。這是 Live Wallpaper 中嵌入 WebView 的最佳實踐。
                presentation = object : Presentation(this@LoomviewLiveWallpaper, display) {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                        
                        webView?.let {
                            (it.parent as? ViewGroup)?.removeView(it)
                            val container = android.widget.FrameLayout(context)
                            val lp = it.layoutParams ?: android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            container.addView(it, lp)
                            setContentView(container)
                        }
                    }
                }
                
                try {
                    presentation?.show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun releaseVirtualDisplay() {
            try {
                presentation?.dismiss()
            } catch (e: Exception) {
                // Ignore
            }
            presentation = null
            
            virtualDisplay?.release()
            virtualDisplay = null
        }
    }
}
