package com.densmac.dashcam.ui.screens.library

import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder

data class LibraryUiState(
    val folder: DashcamFolder = DashcamFolder.LOOP,
    val bundles: List<DashcamFileBundle> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val message: String? = null,
    val pendingDelete: List<DashcamFile> = emptyList()
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}
