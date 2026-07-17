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
import kotlinx.coroutines.flow.update
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

    // Playback position/length in ms and whether the current media supports seeking. Driven by
    // LibVLC events so the scrubber reflects real player state.
    private val _progress = MutableStateFlow(MediaProgress())
    val progress: StateFlow<MediaProgress> = _progress.asStateFlow()

    // True while the user is dragging the scrubber, so incoming TimeChanged events don't fight the
    // drag thumb.
    @Volatile private var scrubbing = false
    // A seek requested before the media is seekable / has a known length, applied once it plays.
    @Volatile private var pendingSeekFraction: Float? = null

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var surfaceHost: ViewGroup? = null
    private var surfaceHostReady = CompletableDeferred<ViewGroup>()
    private var currentUrl: String? = null
    // Known clip length (from the file list) used to seed the scrubber when LibVLC can't measure a
    // remote .ts stream itself, so seeking is available immediately while streaming.
    private var currentKnownDurationMs: Long = 0L

    fun attach(host: Any) {
        val group = host as? ViewGroup ?: return
        val hostChanged = surfaceHost !== group
        surfaceHost = group
        if (!surfaceHostReady.isCompleted) surfaceHostReady.complete(group)
        val layout = attachVideoLayout(group)
        // If playback is already running and we've moved to a different container (e.g. entering
        // or leaving fullscreen), re-bind the player's views to the new surface so the video
        // follows the container instead of going black.
        val activePlayer = player
        if (activePlayer != null && hostChanged) {
            runCatching { activePlayer.detachViews() }
            runCatching { activePlayer.attachViews(layout, null, false, true) }
        }
    }

    suspend fun play(url: String, knownDurationMs: Long = 0L) = mutex.withLock {
        withContext(dispatchers.main) {
            currentUrl = url
            currentKnownDurationMs = knownDurationMs
            stopInternal(releaseLibVlc = true)
            _state.value = MediaPlaybackState.Opening
            startPlayback(url)
        }
    }

    suspend fun retry() {
        currentUrl?.let { play(it, currentKnownDurationMs) }
    }

    /**
     * Stop and release the current player (closing the camera's single-session playback socket) but
     * keep the attached surface so the next [play] can reuse it. Used when swiping between clips: the
     * old stream must be torn down before the next GET, without destroying the video surface.
     */
    suspend fun stop() = mutex.withLock {
        withContext(dispatchers.main) {
            stopInternal(releaseLibVlc = true)
            _progress.value = MediaProgress()
            scrubbing = false
            pendingSeekFraction = null
            currentUrl = null
            _state.value = MediaPlaybackState.Idle
        }
    }

    /**
     * Re-bind the running player to the current surface. Call when the app returns to the
     * foreground: the Android Surface backing the video is destroyed while backgrounded, so audio
     * keeps playing but the picture is lost until the views are re-attached.
     */
    fun reattach() {
        val host = surfaceHost ?: return
        val activePlayer = player ?: return
        val layout = attachVideoLayout(host)
        runCatching { activePlayer.detachViews() }
        runCatching { activePlayer.attachViews(layout, null, false, true) }
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

    /** Called when the user starts dragging the scrubber; freezes position updates from the player. */
    fun beginScrub() {
        scrubbing = true
    }

    /** Live preview of the thumb position (0f..1f) during a drag, without seeking yet. */
    fun previewScrub(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        _progress.update { p ->
            p.copy(position = f, positionMs = (f * p.durationMs).toLong())
        }
    }

    /**
     * Commit a seek to [fraction] (0f..1f) and keep playing from there. Handles the awkward cases
     * robustly: media that isn't marked seekable yet (defer until it plays), a clip that already
     * Ended (replay from the seek point), and a paused player (resume after the jump).
     */
    suspend fun seekTo(fraction: Float) = mutex.withLock {
        withContext(dispatchers.main) {
            val p = player
            scrubbing = false
            if (p == null) return@withContext
            val target = fraction.coerceIn(0f, 1f)
            if (_state.value == MediaPlaybackState.Ended) {
                // Restart the media and jump to the target once it's playing again.
                pendingSeekFraction = target
                runCatching { p.stop() }
                runCatching { p.play() }
                return@withContext
            }
            applySeek(p, target)
            if (!p.isPlaying) {
                runCatching { p.play() }
                _state.value = MediaPlaybackState.Playing
            }
        }
    }

    private fun applySeek(p: MediaPlayer, fraction: Float) {
        // Position (0f..1f) maps to a byte offset even when the container duration is unknown,
        // which is how .ts-over-HTTP seeking works (LibVLC re-syncs to the next TS packet). It
        // also works for local files, so prefer it uniformly.
        runCatching { p.position = fraction }
        _progress.update {
            it.copy(position = fraction, positionMs = (fraction * it.durationMs).toLong())
        }
    }

    fun release() {
        _progress.value = MediaProgress()
        scrubbing = false
        pendingSeekFraction = null
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

        // Seed the scrubber as seekable so it's usable immediately: both local files and the
        // camera's raw .ts over HTTP support Range seeking. SeekableChanged/LengthChanged refine it.
        _progress.value = MediaProgress(durationMs = currentKnownDurationMs, seekable = true)
        scrubbing = false
        val vlcPlayer = MediaPlayer(vlc)
        player = vlcPlayer
        // Render on a TextureView (last arg = true), not a SurfaceView: a SurfaceView punches its own
        // window and shows only audio when hosted inside the fullscreen Compose Dialog, whereas a
        // TextureView composites normally in any window.
        vlcPlayer.attachViews(layout, null, false, true)
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> _state.value = MediaPlaybackState.Opening
                MediaPlayer.Event.Buffering ->
                    if (_state.value != MediaPlaybackState.Playing) {
                        _state.value = MediaPlaybackState.Buffering
                    }
                MediaPlayer.Event.Playing -> {
                    _state.value = MediaPlaybackState.Playing
                    // Apply a seek that was requested before the media was seekable / measured.
                    pendingSeekFraction?.let { fraction ->
                        pendingSeekFraction = null
                        applySeek(vlcPlayer, fraction)
                    }
                }
                MediaPlayer.Event.Paused -> _state.value = MediaPlaybackState.Paused
                MediaPlayer.Event.EndReached -> {
                    _state.value = MediaPlaybackState.Ended
                    // Reset the scrubber to the start; playback stays stopped until the user replays.
                    _progress.update { it.copy(position = 0f, positionMs = 0L) }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Logger.d("MediaFilePlayer encountered error for $url")
                    _state.value = MediaPlaybackState.Error(com.densmac.dashcam.core.common.AppError.RtspUnavailable)
                }
                MediaPlayer.Event.TimeChanged ->
                    // Only trust LibVLC's clock when we have no known duration; otherwise elapsed is
                    // derived from position × duration (PositionChanged), which is accurate for the
                    // proxied .ts even though LibVLC's own time counter isn't.
                    if (!scrubbing) _progress.update { if (it.durationMs > 0) it else it.copy(positionMs = event.timeChanged) }
                MediaPlayer.Event.PositionChanged ->
                    if (!scrubbing) _progress.update {
                        val pos = event.positionChanged.coerceIn(0f, 1f)
                        it.copy(position = pos, positionMs = if (it.durationMs > 0) (pos * it.durationMs).toLong() else it.positionMs)
                    }
                MediaPlayer.Event.LengthChanged ->
                    // LibVLC reports length 0 for the raw .ts; don't let that clobber the duration we
                    // probed from the TS timestamps (which drives the total-time label and elapsed).
                    if (event.lengthChanged > 0) _progress.update { it.copy(durationMs = event.lengthChanged) }
                MediaPlayer.Event.SeekableChanged ->
                    _progress.update { it.copy(seekable = event.seekable) }
            }
        }

        val remote = url.startsWith("http", ignoreCase = true)
        runCatching {
            // Building the Media parses the source (disk/network I/O), so do it off the main thread.
            val media = withContext(dispatchers.io) {
                Media(vlc, Uri.parse(url)).apply {
                    setHWDecoderEnabled(true, false)
                    if (remote) {
                        // The camera serves the raw .ts over HTTP with Range support (this is how
                        // Viidure plays back: Range GETs to stream and to read the tail for
                        // duration). Do NOT force http-continuous — that turns it into a live
                        // stream, disabling Range/seek and breaking A/V sync.
                        //
                        // Making the stream seekable (via the loopback proxy) makes LibVLC buffer
                        // less than it did for a "live" stream, so the single-session camera's bursty
                        // 4K delivery underruns and playback stutters. A deep cache here plus the
                        // proxy's 48 MB read-ahead ride through the camera's SD-contention gaps for
                        // smooth continuous play. (Seeks still re-buffer a few seconds — that's
                        // camera-bound: seeking cold-starts a new transfer at a new byte offset on
                        // the single-session server — and isn't reduced by lowering this.)
                        addOption(":network-caching=5000")
                        addOption(":clock-jitter=0")
                    } else {
                        addOption(":file-caching=1500")
                    }
                }
            }
            Logger.d("MediaFilePlayer opening: $url (remote=$remote)")
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
