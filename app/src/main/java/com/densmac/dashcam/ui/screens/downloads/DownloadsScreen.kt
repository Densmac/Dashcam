package com.densmac.dashcam.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.GlassCard
import com.densmac.dashcam.domain.model.DownloadItem
import com.densmac.dashcam.domain.model.DownloadStatus

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Transfers", style = MaterialTheme.typography.displaySmall)
            }
        }
        state.message?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.downloads.isEmpty()) {
            item { DownloadsStateIcon() }
        } else {
            items(state.downloads, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onRetry = { viewModel.retry(item.id) },
                    onCancel = { viewModel.cancel(item.id) },
                    onDelete = { viewModel.requestDelete(item.id) }
                )
            }
        }
    }
    if (state.pendingDeleteId != null) {
        ConfirmDangerDialog(
            title = "Delete local file?",
            message = "This removes the saved copy from this phone. It does not delete the dashcam SD-card recording.",
            confirmText = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun DownloadsStateIcon() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
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
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.remotePath.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium)
                Text("${item.folder.displayName} - ${item.camera?.displayName ?: "Unknown"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusDot(item.status)
        }
        Spacer(Modifier.height(16.dp))
        if (item.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)) {
            LinearProgressIndicator(progress = item.progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (item.status == DownloadStatus.FAILED) {
                DownloadAction(Icons.Outlined.Refresh, "Retry", onRetry)
            }
            if (item.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)) {
                DownloadAction(Icons.Outlined.Cancel, "Cancel", onCancel)
            }
            DownloadAction(Icons.Outlined.Delete, "Delete", onDelete, tint = MaterialTheme.colorScheme.error)
        }
        item.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusDot(status: DownloadStatus) {
    val color = when (status) {
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
            .border(1.dp, color.copy(alpha = 0.34f), CircleShape)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(status.name.take(1), color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DownloadAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}
