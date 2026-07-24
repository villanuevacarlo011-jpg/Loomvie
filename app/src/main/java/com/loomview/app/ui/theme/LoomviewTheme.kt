package com.loomview.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import com.loomview.app.data.datastore.AppLanguage
import com.loomview.app.data.datastore.AppTheme
import java.util.Locale

/**
 * App 層級的 Compose 主題包裝。依使用者在設定頁選擇的 AppTheme
 * （LIGHT / DARK / SYSTEM）決定實際套用的配色，Android 12+ 裝置
 * 優先使用動態色彩（跟隨桌布），較舊裝置退回固定的 Material3 預設配色。
 * 同時支援根據 AppLanguage 設定動態切換語系 (Locale)。
 */
@Composable
fun LoomviewTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appLanguage: AppLanguage = AppLanguage.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    
    // 處理語言切換
    val currentConfig = LocalConfiguration.current
    val locale = when (appLanguage) {
        AppLanguage.EN -> Locale("en")
        AppLanguage.ZH_TW -> Locale("zh", "TW")
        AppLanguage.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.content.res.Resources.getSystem().configuration.locales.get(0)
            } else {
                @Suppress("DEPRECATION")
                android.content.res.Resources.getSystem().configuration.locale
            }
        }
    }
    
    val config = Configuration(currentConfig).apply {
        setLocale(locale)
    }
    val localizedContext = context.createConfigurationContext(config)

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDarkTheme) dynamicDarkColorScheme(localizedContext) else dynamicLightColorScheme(localizedContext)
        useDarkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides config
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
