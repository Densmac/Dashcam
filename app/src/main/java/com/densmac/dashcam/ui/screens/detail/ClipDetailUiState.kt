package com.densmac.dashcam.ui.screens.detail

import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle

data class ClipDetailUiState(
    val bundle: DashcamFileBundle? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val pendingDelete: List<DashcamFile> = emptyList(),
    // Which side the viewer is focused on (drives the media area and the action bar).
    val selectedSide: DashcamCamera = DashcamCamera.FRONT,
    // In-app streaming: the file currently opened in the media viewer (null = nothing playing),
    // and the underlying player state for buffering/error/retry UI.
    val streamingFile: DashcamFile? = null,
    val playbackState: MediaPlaybackState = MediaPlaybackState.Idle,
    // Completed downloads keyed by remote path -> local file path, so the viewer can play the
    // local copy instead of re-streaming and can offer "Open externally".
    val downloadedPaths: Map<String, String> = emptyMap(),
    // The user's chosen default external player package (null = show the chooser).
    val externalPlayerPackage: String? = null,
    // Bumped each time a download is enqueued, to flash a centered confirmation icon.
    val downloadQueuedTick: Int = 0
) {
    fun localPathFor(file: DashcamFile?): String? = file?.let { downloadedPaths[it.path] }

    /** The file for the currently selected side, falling back to whichever side exists. */
    val selectedFile: DashcamFile?
        get() = when (selectedSide) {
            DashcamCamera.FRONT -> bundle?.front ?: bundle?.rear
            DashcamCamera.REAR -> bundle?.rear ?: bundle?.front
        }

    val isSelectedStreaming: Boolean
        get() = streamingFile != null && streamingFile.path == selectedFile?.path
}
