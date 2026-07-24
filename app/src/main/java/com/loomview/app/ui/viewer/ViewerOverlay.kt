package com.loomview.app.ui.viewer

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.loomview.app.R
import kotlinx.coroutines.delay

/**
 * Viewer 畫面的純 UI 覆蓋層：工具列、選單、設為桌布的 Bottom Sheet。
 *
 * 刻意不含任何 WebView/AndroidView —— 這一層只透過 ComposeView 疊加在
 * ViewerActivity 用傳統 View 系統建立的 WebView 上方，兩者完全獨立，
 * 不會觸發 Compose AndroidView 包裝 WebView 時的合成管線衝突。
 *
 * 這個 Composable 本身背景必須維持透明（不能用 Box.background 蓋滿全螢幕），
 * 否則會直接擋住底下的 WebView 畫面。
 *
 * 「點擊喚回工具列」的手勢不在這裡處理 —— Compose 內部不消耗 pointer 事件
 * 只能確保事件在 Compose 節點之間繼續傳遞，無法讓事件穿透到 ComposeView
 * 之外、完全獨立的另一個 Android View（WebView）。這個轉發邏輯改在
 * ViewerActivity 用 dispatchTouchEvent 處理，見該檔案的說明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerOverlay(
    state: ViewerUiState,
    toolbarVisible: Boolean,
    onToolbarTimeout: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onApplyWallpaper: suspend (Uri) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var showFileInfo by remember { mutableStateOf(false) }

    // 沉浸模式：無操作 3 秒後自動淡出工具列。喚回邏輯（點擊畫面）由
    // ViewerActivity 透過 toolbarVisible 參數控制，這裡只負責淡出的計時。
    LaunchedEffect(toolbarVisible) {
        if (toolbarVisible) {
            delay(3000)
            onToolbarTimeout()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = toolbarVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = state.displayName,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.content_desc_menu))
                    }
                    ViewerOverflowMenu(
                        expanded = menuExpanded,
                        isFavorite = state.isFavorite,
                        onDismiss = { menuExpanded = false },
                        onSetWallpaper = {
                            menuExpanded = false
                            showWallpaperSheet = true
                        },
                        onToggleFavorite = onToggleFavorite,
                        onShowFileInfo = {
                            menuExpanded = false
                            showFileInfo = true
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }
    }

    if (showWallpaperSheet && state.uri != null) {
        SetWallpaperSheet(
            uri = state.uri,
            onDismiss = { showWallpaperSheet = false },
            onApply = onApplyWallpaper,
        )
    }

    if (showFileInfo) {
        FileInfoDialog(
            state = state,
            onDismiss = { showFileInfo = false },
        )
    }
}
