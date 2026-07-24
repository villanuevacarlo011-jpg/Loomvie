package com.loomview.app.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.loomview.app.R
import com.loomview.app.data.local.HtmlFileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onImportClick: () -> Unit,
    onFileClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }

    fun exitSelection() {
        selectionMode = false
        selectedUris = emptySet()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedUris.size,
                    onClose = ::exitSelection,
                    onDeleteSelected = {
                        files.filter { it.uriString in selectedUris }
                            .forEach(viewModel::removeFile)
                        exitSelection()
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.GRID) {
                                    Icons.Filled.ViewList
                                } else {
                                    Icons.Filled.GridView
                                },
                                contentDescription = stringResource(R.string.content_desc_switch_view),
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.content_desc_settings))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onImportClick) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.content_desc_import))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            BrowserTabRow(selectedTab = selectedTab, onSelect = viewModel::selectTab)

            if (files.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f), tab = selectedTab, onImportClick = onImportClick)
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    when (viewMode) {
                        ViewMode.GRID -> FileGrid(
                            files = files,
                            selectionMode = selectionMode,
                            selectedUris = selectedUris,
                            onClick = { entity ->
                                if (selectionMode) {
                                    selectedUris = toggleSelection(selectedUris, entity.uriString)
                                } else {
                                    onFileClick(entity.uriString)
                                }
                            },
                            onLongClick = { entity ->
                                selectionMode = true
                                selectedUris = toggleSelection(selectedUris, entity.uriString)
                            },
                        )

                        ViewMode.LIST -> FileList(
                            files = files,
                            selectionMode = selectionMode,
                            selectedUris = selectedUris,
                            onClick = { entity ->
                                if (selectionMode) {
                                    selectedUris = toggleSelection(selectedUris, entity.uriString)
                                } else {
                                    onFileClick(entity.uriString)
                                }
                            },
                            onLongClick = { entity ->
                                selectionMode = true
                                selectedUris = toggleSelection(selectedUris, entity.uriString)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun toggleSelection(current: Set<String>, uri: String): Set<String> =
    if (uri in current) current - uri else current + uri

@Composable
private fun BrowserTabRow(selectedTab: BrowserTab, onSelect: (BrowserTab) -> Unit) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        Tab(
            selected = selectedTab == BrowserTab.ALL,
            onClick = { onSelect(BrowserTab.ALL) },
            text = { Text(stringResource(R.string.tab_all)) },
        )
        Tab(
            selected = selectedTab == BrowserTab.RECENT,
            onClick = { onSelect(BrowserTab.RECENT) },
            text = { Text(stringResource(R.string.tab_recent)) },
        )
        Tab(
            selected = selectedTab == BrowserTab.FAVORITES,
            onClick = { onSelect(BrowserTab.FAVORITES) },
            text = { Text(stringResource(R.string.tab_favorites)) },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, tab: BrowserTab, onImportClick: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Web,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when (tab) {
                    BrowserTab.ALL -> stringResource(R.string.empty_hint_all)
                    BrowserTab.RECENT -> stringResource(R.string.empty_hint_recent)
                    BrowserTab.FAVORITES -> stringResource(R.string.empty_hint_favorites)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (tab == BrowserTab.ALL) {
                Button(onClick = onImportClick) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_import))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGrid(
    files: List<HtmlFileEntity>,
    selectionMode: Boolean,
    selectedUris: Set<String>,
    onClick: (HtmlFileEntity) -> Unit,
    onLongClick: (HtmlFileEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(files, key = { it.uriString }) { entity ->
            FileCard(
                entity = entity,
                isSelected = entity.uriString in selectedUris,
                selectionMode = selectionMode,
                onClick = { onClick(entity) },
                onLongClick = { onLongClick(entity) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    entity: HtmlFileEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            if (entity.thumbnailPath != null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val file = java.io.File(entity.thumbnailPath)
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey("${file.absolutePath}_${file.lastModified()}")
                        .diskCacheKey("${file.absolutePath}_${file.lastModified()}")
                        .build(),
                    contentDescription = entity.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 縮圖尚未產生或產生失敗時的通用佔位圖示 —— 不依內容類型顯示不同圖示，
                // 因為 App 不知道、也不該知道內容是什麼。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Web,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entity.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = entity.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = formatSize(entity.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    files: List<HtmlFileEntity>,
    selectionMode: Boolean,
    selectedUris: Set<String>,
    onClick: (HtmlFileEntity) -> Unit,
    onLongClick: (HtmlFileEntity) -> Unit,
) {
    LazyColumn {
        listItems(files, key = { it.uriString }) { entity ->
            ListItem(
                leadingContent = {
                    if (selectionMode) {
                        Checkbox(
                            checked = entity.uriString in selectedUris,
                            onCheckedChange = { onClick(entity) },
                        )
                    } else if (entity.thumbnailPath != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val file = java.io.File(entity.thumbnailPath)
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(file)
                                .memoryCacheKey("${file.absolutePath}_${file.lastModified()}")
                                .diskCacheKey("${file.absolutePath}_${file.lastModified()}")
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(Icons.Filled.Web, contentDescription = null)
                    }
                },
                headlineContent = { Text(entity.displayName, maxLines = 1) },
                supportingContent = { Text(formatSize(entity.sizeBytes)) },
                trailingContent = {
                    if (entity.isFavorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "已收藏",
                            tint = Color(0xFFFFC107),
                        )
                    }
                },
                modifier = Modifier.combinedClickable(
                    onClick = { onClick(entity) },
                    onLongClick = { onLongClick(entity) },
                ),
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDeleteSelected: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.selection_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_cancel_selection))
            }
        },
        actions = {
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_delete_selected))
            }
        },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
