package com.loomview.app.ui.viewer

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.loomview.app.R

@Composable
fun ViewerOverflowMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowFileInfo: () -> Unit,
) {
    val strSetWallpaper = stringResource(R.string.menu_set_wallpaper)
    val strFavorite = stringResource(if (isFavorite) R.string.menu_remove_favorite else R.string.menu_add_favorite)
    val strFileInfo = stringResource(R.string.menu_file_info)

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(strSetWallpaper) },
            onClick = onSetWallpaper,
        )
        DropdownMenuItem(
            text = { Text(strFavorite) },
            onClick = onToggleFavorite,
        )
        DropdownMenuItem(
            text = { Text(strFileInfo) },
            onClick = onShowFileInfo,
        )
    }
}
