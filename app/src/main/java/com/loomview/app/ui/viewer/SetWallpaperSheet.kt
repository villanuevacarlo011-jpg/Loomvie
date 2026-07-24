package com.loomview.app.ui.viewer

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loomview.app.R
import com.loomview.app.wallpaper.LoomviewLiveWallpaper
import kotlinx.coroutines.launch

/**
 * 「設為桌布」流程的 Bottom Sheet。
 *
 * 不管使用者選了哪個檔案、內容是什麼，流程完全一致：
 * 1. 把目前檔案 Uri 寫進 SettingsDataStore
 * 2. 呼叫系統 ACTION_CHANGE_LIVE_WALLPAPER，導向系統原生的桌布預覽/套用頁面
 *
 * App 不在這裡做任何「這份 HTML 適不適合當桌布」的判斷——一律放行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetWallpaperSheet(
    uri: Uri,
    onDismiss: () -> Unit,
    onApply: suspend (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val strTitle = stringResource(R.string.sheet_set_wallpaper_title)
    val strDesc = stringResource(R.string.sheet_set_wallpaper_desc)
    val strCancel = stringResource(R.string.btn_cancel)
    val strApply = stringResource(R.string.btn_confirm_apply)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = strTitle,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strDesc,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strCancel)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            onApply(uri)
                            launchSystemWallpaperPicker(context)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strApply)
                }
            }
        }
    }
}

/**
 * 導向系統原生的 Live Wallpaper 預覽/套用頁面，讓使用者在系統 UI 中看到實際效果並手動確認。
 * 這是標準的 Android Live Wallpaper 套用流程，不是自行呼叫 WallpaperManager.setBitmap
 * （那個 API 是給靜態圖片用的，會丟失動態效果）。
 */
private fun launchSystemWallpaperPicker(context: Context) {
    val component = ComponentName(context, LoomviewLiveWallpaper::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // 少數 OEM 客製系統可能沒有實作這個系統頁面；退化為開啟一般桌布選擇頁。
        val fallback = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}
