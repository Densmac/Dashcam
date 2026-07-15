package com.densmac.dashcam.core.player

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.core.common.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import javax.inject.Inject

/**
 * Plays on-demand media (remote dashcam HTTP .ts files or downloaded local files) with LibVLC.
 *
 * Kept separate from [RtspLivePreviewController] so file playback and the live RTSP stream never
 * share a LibVLC instance or surface. Unscoped (one instance per injecting ViewModel) so the clip
 * viewer and the Transfers viewer never fight over a single shared player. Lifecycle-safe: the
 * caller must [attach] a surface host, and MUST call [release] when the viewer leaves composition.
 * All player mutations are serialized and hop to the main thread; nothing blocks the caller's thread.
 */
class MediaFilePlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatchersProvider
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<MediaPlaybackState>(MediaPlaybackState.Idle)
    val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var surfaceHost: ViewGroup? = null
    private var surfaceHostReady = CompletableDeferred<ViewGroup>()
    private var currentUrl: String? = null

    fun attach(host: Any) {
        val group = host as? ViewGroup ?: return
        surfaceHost = group
        if (!surfaceHostReady.isCompleted) surfaceHostReady.complete(group)
        attachVideoLayout(group)
    }

    suspend fun play(url: String) = mutex.withLock {
        withContext(dispatchers.main) {
            currentUrl = url
            stopInternal(releaseLibVlc = true)
            _state.value = MediaPlaybackState.Opening
            startPlayback(url)
        }
    }

    suspend fun retry() {
        currentUrl?.let { play(it) }
    }

    suspend fun pause() = mutex.withLock {
        withContext(dispatchers.main) {
            val p = player ?: return@withContext
            if (p.isPlaying) {
                p.pause()
                _state.value = MediaPlaybackState.Paused
            }
        }
    }

    suspend fun resume() = mutex.withLock {
        withContext(dispatchers.main) {
            val p = player ?: run {
                currentUrl?.let { startPlayback(it) }
                return@withContext
            }
            if (!p.isPlaying) {
                p.play()
                _state.value = MediaPlaybackState.Playing
            }
        }
    }

    fun release() {
        stopInternal(releaseLibVlc = true)
        videoLayout?.let { layout -> (layout.parent as? ViewGroup)?.removeView(layout) }
        videoLayout = null
        surfaceHost = null
        surfaceHostReady = CompletableDeferred()
        currentUrl = null
        _state.value = MediaPlaybackState.Idle
    }

    private suspend fun startPlayback(url: String) {
        // The surface host is attached by the AndroidView on a later frame. For local playback
        // there is no network preamble to absorb that delay, so wait for the surface instead of
        // silently bailing (which would strand the viewer on the buffering spinner).
        val host = surfaceHost ?: withTimeoutOrNull(SURFACE_WAIT_MS) { surfaceHostReady.await() }
        if (host == null) {
            Logger.d("MediaFilePlayer did not receive a video surface in time")
            _state.value = MediaPlaybackState.Error(com.densmac.dashcam.core.common.AppError.Unknown(IllegalStateException("No video surface")))
            return
        }
        val layout = attachVideoLayout(host)

        // LibVLC's constructor loads native libraries (disk I/O), so build it off the main thread.
        val vlc = libVlc ?: withContext(dispatchers.io) {
            LibVLC(
                context,
                arrayListOf(
                    "--no-sub-autodetect-file",
                    "--network-caching=1500",
                    "--file-caching=1500"
                )
            )
        }.also { libVlc = it }

        val vlcPlayer = MediaPlayer(vlc)
        player = vlcPlayer
        vlcPlayer.attachViews(layout, null, false, false)
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> _state.value = MediaPlaybackState.Opening
                MediaPlayer.Event.Buffering ->
                    if (_state.value != MediaPlaybackState.Playing) {
                        _state.value = MediaPlaybackState.Buffering
                    }
                MediaPlayer.Event.Playing -> _state.value = MediaPlaybackState.Playing
                MediaPlayer.Event.Paused -> _state.value = MediaPlaybackState.Paused
                MediaPlayer.Event.EndReached -> _state.value = MediaPlaybackState.Ended
                MediaPlayer.Event.EncounteredError -> {
                    Logger.d("MediaFilePlayer encountered error for $url")
                    _state.value = MediaPlaybackState.Error(com.densmac.dashcam.core.common.AppError.RtspUnavailable)
                }
            }
        }

        runCatching {
            // Building the Media parses the source (disk/network I/O), so do it off the main thread.
            val media = withContext(dispatchers.io) {
                Media(vlc, Uri.parse(url)).apply {
                    setHWDecoderEnabled(true, false)
                    addOption(":network-caching=1500")
                    addOption(":file-caching=1500")
                }
            }
            Logger.d("MediaFilePlayer opening: $url")
            vlcPlayer.media = media
            media.release()
            vlcPlayer.play()
        }.onFailure { throwable ->
            Logger.e("MediaFilePlayer failed to open $url", throwable)
            _state.value = MediaPlaybackState.Error(com.densmac.dashcam.core.common.AppError.Unknown(throwable))
        }
    }

    private fun attachVideoLayout(host: ViewGroup): VLCVideoLayout {
        val existing = videoLayout
        if (existing?.parent === host) return existing
        val layout = VLCVideoLayout(host.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        host.removeAllViews()
        host.addView(layout)
        videoLayout = layout
        return layout
    }

    private companion object {
        const val SURFACE_WAIT_MS = 3_000L
    }

    private fun stopInternal(releaseLibVlc: Boolean) {
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.detachViews() }
            runCatching { p.release() }
        }
        player = null
        if (releaseLibVlc) {
            runCatching { libVlc?.release() }
            libVlc = null
        }
    }
}
