package com.densmac.dashcam.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.common.DateTimeFormatters
import com.densmac.dashcam.core.common.kbToDisplayMb
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.DashcamButton
import com.densmac.dashcam.core.design.components.DashcamButtonStyle
import com.densmac.dashcam.core.design.components.EmptyState
import com.densmac.dashcam.core.design.components.GlassCard
import com.densmac.dashcam.core.design.components.SectionHeader
import com.densmac.dashcam.domain.model.DashcamFile

@Composable
fun ClipDetailScreen(
    onBack: () -> Unit,
    viewModel: ClipDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bundle = state.bundle
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Clip",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.displaySmall
            )
        }
        if (bundle == null) {
            EmptyState(if (state.loading) "Loading clip..." else "Clip not found.")
        } else {
            GlassCard(hero = true, contentPadding = PaddingValues(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF4A3320),
                                    Color(0xFF11120D)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, tint = Color(0xFFF7E7C7))
                }
            }
            GlassCard {
                Text(DateTimeFormatters.displayCameraTime(bundle.startTime), style = MaterialTheme.typography.headlineMedium)
                Text("${bundle.folder.displayName} - ${bundle.totalSizeKb.kbToDisplayMb()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FileCard("Front file", bundle.front, onDownload = {
                bundle.front?.let { viewModel.download(listOf(it)) }
            }, onDelete = {
                bundle.front?.let { viewModel.requestDelete(listOf(it)) }
            })
            FileCard("Rear file", bundle.rear, onDownload = {
                bundle.rear?.let { viewModel.download(listOf(it)) }
            }, onDelete = {
                bundle.rear?.let { viewModel.requestDelete(listOf(it)) }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DashcamButton("Download both", { viewModel.download(listOfNotNull(bundle.front, bundle.rear)) }, icon = Icons.Outlined.Download, modifier = Modifier.weight(1f))
                DashcamButton("Delete both", { viewModel.requestDelete(listOfNotNull(bundle.front, bundle.rear)) }, icon = Icons.Outlined.Delete, style = DashcamButtonStyle.Outline, modifier = Modifier.weight(1f))
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun FileCard(
    title: String,
    file: DashcamFile?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        if (file == null) {
            Text("Not available in this bundle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(file.filename, style = MaterialTheme.typography.titleMedium)
            Text("${file.camera?.displayName ?: "Unknown"} - ${file.sizeKb.kbToDisplayMb()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashcamButton("Download", onDownload, icon = Icons.Outlined.Download, style = DashcamButtonStyle.Tonal)
                DashcamButton("Delete", onDelete, icon = Icons.Outlined.Delete, style = DashcamButtonStyle.Outline)
            }
        }
    }
}
