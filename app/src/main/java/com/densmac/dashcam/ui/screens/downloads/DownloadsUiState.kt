package com.densmac.dashcam.ui.screens.downloads

import com.densmac.dashcam.domain.model.DownloadItem

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val pendingDeleteId: String? = null,
    val message: String? = null
)
