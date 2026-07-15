package com.densmac.dashcam.ui.screens.downloads

import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.domain.model.DownloadItem

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val pendingDeleteId: String? = null,
    val message: String? = null,
    // In-app playback of a completed local download (null = player closed).
    val playing: DownloadItem? = null,
    val playbackState: MediaPlaybackState = MediaPlaybackState.Idle
) {
    val activeDownloads: List<DownloadItem>
        get() = downloads.filter { it.status.isActive }
    val finishedDownloads: List<DownloadItem>
        get() = downloads.filter { !it.status.isActive }
}

private val com.densmac.dashcam.domain.model.DownloadStatus.isActive: Boolean
    get() = this == com.densmac.dashcam.domain.model.DownloadStatus.QUEUED ||
        this == com.densmac.dashcam.domain.model.DownloadStatus.RUNNING ||
        this == com.densmac.dashcam.domain.model.DownloadStatus.PAUSED
