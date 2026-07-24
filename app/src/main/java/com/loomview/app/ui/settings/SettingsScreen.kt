package com.loomview.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loomview.app.R
import com.loomview.app.data.datastore.AppLanguage
import com.loomview.app.data.datastore.AppTheme
import com.loomview.app.data.datastore.RenderQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val renderQualityState by viewModel.renderQuality.collectAsStateWithLifecycle()
    val fpsCapState by viewModel.fpsCap.collectAsStateWithLifecycle()
    val pauseWhenScreenOffState by viewModel.pauseWhenScreenOff.collectAsStateWithLifecycle()
    val downgradeOnLowBatteryState by viewModel.downgradeOnLowBattery.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val appThemeState by viewModel.appTheme.collectAsStateWithLifecycle()
    val appLanguageState by viewModel.appLanguage.collectAsStateWithLifecycle()
    var showClearAllConfirm by remember { mutableStateOf(false) }

    if (renderQualityState == null || fpsCapState == null || pauseWhenScreenOffState == null || downgradeOnLowBatteryState == null || appThemeState == null || appLanguageState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val renderQuality = renderQualityState!!
    val fpsCap = fpsCapState!!
    val pauseWhenScreenOff = pauseWhenScreenOffState!!
    val downgradeOnLowBattery = downgradeOnLowBatteryState!!
    val appTheme = appThemeState!!
    val appLanguage = appLanguageState!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.section_wallpaper_behavior))

            SettingsRow(title = stringResource(R.string.render_quality)) {
                SingleChoiceSegmented(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(RenderQuality.NATIVE, RenderQuality.STANDARD, RenderQuality.BATTERY_SAVER),
                    selected = renderQuality,
                    onSelect = viewModel::setRenderQuality,
                    label = { 
                        Text(when (it) {
                            RenderQuality.NATIVE -> stringResource(R.string.quality_native)
                            RenderQuality.STANDARD -> stringResource(R.string.quality_standard)
                            RenderQuality.BATTERY_SAVER -> stringResource(R.string.quality_saver)
                        })
                    }
                )
            }

            SettingsRow(title = stringResource(R.string.fps_limit, fpsCap)) {
                SingleChoiceSegmented(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(15, 30, 60),
                    selected = fpsCap,
                    onSelect = viewModel::setFpsCap,
                    label = { Text(it.toString()) }
                )
            }

            SettingsSwitchRow(
                title = stringResource(R.string.pause_screen_off),
                checked = pauseWhenScreenOff,
                onCheckedChange = viewModel::setPauseWhenScreenOff,
            )

            SettingsSwitchRow(
                title = stringResource(R.string.downgrade_low_battery),
                checked = downgradeOnLowBattery,
                onCheckedChange = viewModel::setDowngradeOnLowBattery,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader(stringResource(R.string.section_file_storage))

            SettingsRow(title = stringResource(R.string.thumbnail_cache, formatCacheSize(cacheSizeBytes))) {
                OutlinedButton(onClick = viewModel::clearThumbnailCache) {
                    Text(stringResource(R.string.btn_clear_cache))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader(stringResource(R.string.section_appearance))

            SettingsRow(title = stringResource(R.string.theme)) {
                SingleChoiceSegmented(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM),
                    selected = appTheme,
                    onSelect = viewModel::setAppTheme,
                    label = {
                        Text(when (it) {
                            AppTheme.LIGHT -> stringResource(R.string.theme_light)
                            AppTheme.DARK -> stringResource(R.string.theme_dark)
                            AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                        })
                    }
                )
            }
            
            SettingsRow(title = stringResource(R.string.language)) {
                SingleChoiceSegmented(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(AppLanguage.EN, AppLanguage.ZH_TW, AppLanguage.SYSTEM),
                    selected = appLanguage,
                    onSelect = viewModel::setAppLanguage,
                    label = {
                        Text(when (it) {
                            AppLanguage.EN -> stringResource(R.string.lang_en)
                            AppLanguage.ZH_TW -> stringResource(R.string.lang_zh)
                            AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
                        })
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader(stringResource(R.string.section_advanced))

            SettingsRow(title = stringResource(R.string.clear_all_data)) {
                Text(
                    text = stringResource(R.string.clear_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearAllConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.clear_all_data))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearAllConfirm) {
        val strTitle = stringResource(R.string.dialog_clear_all_title)
        val strDesc = stringResource(R.string.dialog_clear_all_desc)
        val strConfirm = stringResource(R.string.btn_confirm)
        val strCancel = stringResource(R.string.btn_cancel)

        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(strTitle) },
            text = { Text(strDesc) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearAllConfirm = false
                }) { Text(strConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text(strCancel) }
            },
        )
    }
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun SettingsRow(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SingleChoiceSegmented(
    modifier: Modifier = Modifier,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                label(option)
            }
        }
    }
}
