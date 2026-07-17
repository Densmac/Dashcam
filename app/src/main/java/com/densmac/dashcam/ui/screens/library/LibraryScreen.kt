package com.densmac.dashcam.ui.screens.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.common.DateTimeFormatters
import com.densmac.dashcam.core.common.kbToDisplayMb
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.haptics.HapticEvent
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.model.DashcamMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    onDownloadEnqueued: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FolderTabs(selected = state.folder, onSelected = viewModel::load)

        if (state.selectionMode) {
            SelectionDock(
                selectedCount = state.selectedIds.size,
                onDelete = viewModel::requestDeleteSelected,
                onClear = viewModel::clearSelection
            )
        }

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.load() },
            modifier = Modifier.weight(1f)
        ) {
            if (!state.loading && state.bundles.isEmpty()) {
                // Scrollable so pull-to-refresh gesture works even on the empty state.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    LibraryStateIcon(
                        connectionIssue = state.message != null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(154.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.bundles, key = { it.id }) { bundle ->
                        LibraryCard(
                            bundle = bundle,
                            selected = bundle.id in state.selectedIds,
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelection(bundle) else onOpenDetail(bundle.id)
                            },
                            onLongClick = { viewModel.toggleSelection(bundle) },
                            onDownload = {
                                viewModel.downloadBundle(bundle)
                                onDownloadEnqueued()
                            },
                            onDelete = { viewModel.requestDelete(listOfNotNull(bundle.front, bundle.rear)) },
                            loadThumbnail = viewModel::loadThumbnail
                        )
                    }
                }
            }
        }
    }

    if (state.pendingDelete.isNotEmpty()) {
        ConfirmDangerDialog(
            title = "Delete recording?",
            message = "This removes the selected file from the dashcam SD card. This cannot be undone.",
            confirmText = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun LibraryStateIcon(
    connectionIssue: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (connectionIssue) Icons.Outlined.WifiOff else Icons.Outlined.VideocamOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun SelectionDock(
    selectedCount: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(29.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$selectedCount", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBubble(icon = Icons.Outlined.Delete, contentDescription = "Delete", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
            IconBubble(icon = Icons.Outlined.Close, contentDescription = "Clear", onClick = onClear)
        }
    }
}

@Composable
private fun FolderTabs(selected: DashcamFolder, onSelected: (DashcamFolder) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), RoundedCornerShape(27.dp))
            .horizontalScroll(rememberScrollState())
            .padding(5.dp)
    ) {
        DashcamFolder.entries.forEach { folder ->
            val active = selected == folder
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .hapticClickable { onSelected(folder) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    folder.shortLabel(),
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    bundle: DashcamFileBundle,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    loadThumbnail: suspend (DashcamFile) -> ByteArray?
) {
    val hapticClick = rememberHapticClick(onClick = onClick)
    val hapticLongClick = rememberHapticClick(HapticEvent.Warning, onLongClick)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.76f)
            .clip(RoundedCornerShape(24.dp))
            .background(tileBrush(bundle.folder))
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(24.dp)
            )
            .combinedClickable(onClick = hapticClick, onLongClick = hapticLongClick)
    ) {
        RecordingThumbnail(
            bundle = bundle,
            loadThumbnail = loadThumbnail,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.76f)
                        )
                    )
                )
        )

        MediaBadge(
            bundle = bundle,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateTimeFormatters.displayCameraTime(bundle.startTime),
                    color = Color(0xFFF7E7C7),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bundle.totalSizeKb.kbToDisplayMb(),
                    color = Color(0xFFBFAE8F),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                TileAction(icon = Icons.Outlined.Download, contentDescription = "Download", onClick = onDownload)
                TileAction(icon = Icons.Outlined.Delete, contentDescription = "Delete", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RecordingThumbnail(
    bundle: DashcamFileBundle,
    loadThumbnail: suspend (DashcamFile) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    val frontThumbnail by produceState<ImageBitmap?>(initialValue = null, bundle.front?.path) {
        value = null
        val bytes = bundle.front?.let { loadThumbnail(it) } ?: return@produceState
        value = decodeThumbnail(bytes)
    }
    val rearThumbnail by produceState<ImageBitmap?>(initialValue = null, bundle.rear?.path) {
        value = null
        val bytes = bundle.rear?.let { loadThumbnail(it) } ?: return@produceState
        value = decodeThumbnail(bytes)
    }

    if (bundle.front != null && bundle.rear != null) {
        Row(modifier = modifier) {
            ThumbnailPane(
                thumbnail = frontThumbnail,
                mediaType = bundle.mediaType,
                label = "F",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            ThumbnailPane(
                thumbnail = rearThumbnail,
                mediaType = bundle.mediaType,
                label = "R",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
        }
    } else {
        ThumbnailPane(
            thumbnail = frontThumbnail ?: rearThumbnail,
            mediaType = bundle.mediaType,
            label = bundle.primary?.camera?.displayName?.take(1),
            modifier = modifier
        )
    }
}

@Composable
private fun ThumbnailPane(
    thumbnail: ImageBitmap?,
    mediaType: DashcamMediaType,
    label: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = if (mediaType == DashcamMediaType.PICTURE) Icons.Outlined.PhotoCamera else Icons.Outlined.Videocam,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(42.dp)
            )
        }
        if (!label.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xB20A0B08)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color(0xFFF7E7C7),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private suspend fun decodeThumbnail(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}

@Composable
private fun MediaBadge(bundle: DashcamFileBundle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD90A0B08))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (bundle.mediaType == DashcamMediaType.PICTURE) Icons.Outlined.PhotoCamera else Icons.Outlined.Videocam,
            contentDescription = null,
            tint = Color(0xFFF7E7C7),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = if (bundle.isCompletePair) "F/R" else bundle.primary?.camera?.displayName?.take(1) ?: "?",
            color = Color(0xFFF7E7C7),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IconBubble(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val hapticClick = rememberHapticClick(onClick = onClick)
    IconButton(
        onClick = hapticClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color(0xFFF7E7C7)
) {
    val hapticClick = rememberHapticClick(onClick = onClick)
    IconButton(
        onClick = hapticClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xD90A0B08))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

private fun tileBrush(folder: DashcamFolder): Brush {
    val top = when (folder) {
        DashcamFolder.LOOP -> Color(0xFF3E3020)
        DashcamFolder.EVENT -> Color(0xFF623026)
        DashcamFolder.PARK -> Color(0xFF313024)
        DashcamFolder.EMR -> Color(0xFF4C2525)
        DashcamFolder.RACE -> Color(0xFF44311B)
    }
    return Brush.linearGradient(listOf(top, Color(0xFF11120D)))
}

private fun DashcamFolder.shortLabel(): String = when (this) {
    DashcamFolder.LOOP -> "Loop"
    DashcamFolder.EVENT -> "Event"
    DashcamFolder.PARK -> "Park"
    DashcamFolder.EMR -> "SOS"
    DashcamFolder.RACE -> "Race"
}
