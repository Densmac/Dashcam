package com.densmac.dashcam.ui.screens.detail

import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle

data class ClipDetailUiState(
    val bundle: DashcamFileBundle? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val pendingDelete: List<DashcamFile> = emptyList()
)
