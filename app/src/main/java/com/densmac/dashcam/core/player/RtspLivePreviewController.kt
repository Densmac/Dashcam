package com.densmac.dashcam.core.player

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.core.common.Logger
import com.densmac.dashcam.domain.model.DashcamCamera
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtspLivePreviewController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatchersProvider
) : LivePreviewEngine {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LivePreviewState>(LivePreviewState.Idle)
    override val state: StateFlow<LivePreviewState> = _state

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var surfaceHost: ViewGroup? = null
    private var surfaceHostReady = CompletableDeferred<ViewGroup>()
    private var startup: CompletableDeferred<AppResult<Unit>>? = null

    override fun attach(surfaceHost: Any) {
        val host = surfaceHost as? ViewGroup ?: return
        this.surfaceHost = host
        if (!surfaceHostReady.isCompleted) surfaceHostReady.complete(host)
        attachVideoLayout(host)
    }

    override suspend fun start(url: String): AppResult<Unit> = mutex.withLock {
        withContext(dispatchers.main) {
            stopInternal(clearLayout = false, releaseLibVlc = true)
            _state.value = LivePreviewState.Preparing
            startVlc(url)
        }
    }

    override suspend fun switchCamera(camera: DashcamCamera): AppResult<Unit> =
        start(DashcamConstants.RTSP_ROOT_URL)

    override suspend fun stop() {
        mutex.withLock {
            withContext(dispatchers.main) { stopInternal(clearLayout = false, releaseLibVlc = false) }
        }
    }

    override fun release() {
        stopInternal(clearLayout = true, releaseLibVlc = true)
        surfaceHost = null
        surfaceHostReady = CompletableDeferred()
        _state.value = LivePreviewState.Released
    }

    private suspend fun startVlc(url: String): AppResult<Unit> {
        val host = surfaceHost ?: withTimeoutOrNull(DashcamConstants.STREAM_START_TIMEOUT_MS) {
            surfaceHostReady.await()
        }
        val layout = host?.let(::attachVideoLayout)
            ?: return AppResult.Failure(AppError.RtspUnavailable).also {
                Logger.d("LibVLC live preview did not receive a video host in time")
                _state.value = LivePreviewState.Error(AppError.RtspUnavailable)
            }

        val vlc = libVlc ?: LibVLC(
            context,
            arrayListOf(
                "--no-audio",
                "--rtsp-tcp",
                "--network-caching=150",
                "--live-caching=150",
                "--clock-jitter=0",
                "--clock-synchro=0"
            )
        ).also { libVlc = it }

        val vlcPlayer = MediaPlayer(vlc)
        player = vlcPlayer
        val deferred = CompletableDeferred<AppResult<Unit>>()
        startup = deferred
        vlcPlayer.attachViews(layout, null, false, false)
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> _state.value = LivePreviewState.Connecting
                MediaPlayer.Event.Buffering -> {
                    if (_state.value != LivePreviewState.Playing) _state.value = LivePreviewState.Buffering
                }
                MediaPlayer.Event.Playing -> {
                    Logger.d("LibVLC live preview is playing")
                    _state.value = LivePreviewState.Playing
                    deferred.complete(AppResult.Success(Unit))
                }
                MediaPlayer.Event.EncounteredError -> {
                    Logger.d("LibVLC live preview failed")
                    _state.value = LivePreviewState.Error(AppError.RtspUnavailable)
                    deferred.complete(AppResult.Failure(AppError.RtspUnavailable))
                }
                MediaPlayer.Event.EndReached,
                MediaPlayer.Event.Stopped -> {
                    if (_state.value == LivePreviewState.Playing) _state.value = LivePreviewState.Idle
                }
            }
        }

        return runCatching {
            val media = Media(vlc, Uri.parse(url)).apply {
                addOption(":rtsp-tcp")
                addOption(":network-caching=150")
                addOption(":live-caching=150")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":no-audio")
            }
            Logger.d("Starting LibVLC RTSP preview: $url")
            vlcPlayer.media = media
            media.release()
            _state.value = LivePreviewState.Connecting
            vlcPlayer.play()

            withTimeoutOrNull(DashcamConstants.STREAM_START_TIMEOUT_MS) { deferred.await() }
                ?: AppResult.Failure(AppError.RtspUnavailable).also {
                    Logger.d("LibVLC live preview timed out before playback")
                    stopInternal(clearLayout = false, releaseLibVlc = true)
                    _state.value = LivePreviewState.Error(AppError.RtspUnavailable)
                }
        }.getOrElse { throwable ->
            Logger.e("LibVLC live preview setup failed", throwable)
            stopInternal(clearLayout = false, releaseLibVlc = true)
            _state.value = LivePreviewState.Error(AppError.RtspUnavailable)
            AppResult.Failure(AppError.RtspUnavailable)
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

    private fun stopInternal(clearLayout: Boolean, releaseLibVlc: Boolean) {
        startup?.cancel()
        startup = null
        player?.runCatchingRelease()
        player = null
        if (releaseLibVlc) {
            runCatching { libVlc?.release() }
            libVlc = null
        }
        if (clearLayout) clearViews()
        _state.value = LivePreviewState.Idle
    }

    private fun clearViews() {
        videoLayout?.let { layout ->
            runCatching { (layout.parent as? ViewGroup)?.removeView(layout) }
        }
        videoLayout = null
    }

    private fun MediaPlayer.runCatchingRelease() {
        runCatching { stop() }
        runCatching { detachViews() }
        runCatching { release() }
    }
}
