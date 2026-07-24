package com.loomview.app.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loomview.app.R
import java.text.DateFormat
import java.util.Date

/**
 * 檔案資訊面板。顯示純檔案系統層級的通用 metadata：檔案大小、修改日期、
 * 來源路徑、MIME 類型。這些都不需要解析 HTML 內容即可取得，
 * 適用於任何 .html 檔案，跟規劃書 5.3 節的定義一致。
 */
@Composable
fun FileInfoDialog(
    state: ViewerUiState,
    onDismiss: () -> Unit,
) {
    val strTitle = stringResource(R.string.dialog_file_info_title)
    val strFilename = stringResource(R.string.info_filename)
    val strSize = stringResource(R.string.info_size)
    val strModified = stringResource(R.string.info_last_modified)
    val strType = stringResource(R.string.info_type)
    val strPath = stringResource(R.string.info_path)
    val strClose = stringResource(R.string.btn_close)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strTitle) },
        text = {
            Column {
                InfoRow(strFilename, state.displayName)
                InfoRow(strSize, formatFileSize(state.sizeBytes))
                if (state.lastModifiedMillis > 0) {
                    InfoRow(strModified, formatDate(state.lastModifiedMillis))
                }
                InfoRow(strType, state.mimeType ?: "text/html")
                state.uri?.let { InfoRow(strPath, it.toString()) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strClose) }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
