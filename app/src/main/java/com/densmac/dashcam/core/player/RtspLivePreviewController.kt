package com.densmac.dashcam.core.player

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.domain.model.DashcamCamera
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class RtspLivePreviewController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatchersProvider
) : LivePreviewEngine {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LivePreviewState>(LivePreviewState.Idle)
    override val state: StateFlow<LivePreviewState> = _state
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var startup: CompletableDeferred<AppResult<Unit>>? = null

    override fun attach(surfaceHost: Any) {
        val host = surfaceHost as? ViewGroup ?: return
        val view = PlayerView(host.context).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        host.removeAllViews()
        host.addView(view)
        playerView = view
        view.player = player
    }

    override suspend fun start(url: String): AppResult<Unit> = mutex.withLock {
        withContext(dispatchers.main) {
            stopInternal()
            _state.value = LivePreviewState.Preparing
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
            val exoPlayer = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build()
            player = exoPlayer
            playerView?.player = exoPlayer
            val deferred = CompletableDeferred<AppResult<Unit>>()
            startup = deferred
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _state.value = LivePreviewState.Buffering
                        Player.STATE_READY -> {
                            _state.value = LivePreviewState.Playing
                            deferred.complete(AppResult.Success(Unit))
                        }
                        Player.STATE_ENDED -> _state.value = LivePreviewState.Idle
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val appError = AppError.RtspUnavailable
                    _state.value = LivePreviewState.Error(appError)
                    deferred.complete(AppResult.Failure(appError))
                }
            })
            _state.value = LivePreviewState.Connecting
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(url))
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            val started = withTimeoutOrNull(DashcamConstants.STREAM_START_TIMEOUT_MS) { deferred.await() }
            started ?: AppResult.Failure(AppError.RtspUnavailable).also {
                stopInternal()
                _state.value = LivePreviewState.Error(AppError.RtspUnavailable)
            }
        }
    }

    override suspend fun switchCamera(camera: DashcamCamera): AppResult<Unit> = start(DashcamConstants.RTSP_TRACK2_URL)

    override suspend fun stop() {
        mutex.withLock {
            withContext(dispatchers.main) { stopInternal() }
        }
    }

    override fun release() {
        val localPlayer = player
        playerView?.player = null
        playerView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        playerView = null
        localPlayer?.release()
        player = null
        startup?.cancel()
        startup = null
        _state.value = LivePreviewState.Released
    }

    private fun stopInternal() {
        startup?.cancel()
        startup = null
        playerView?.player = null
        player?.release()
        player = null
        _state.value = LivePreviewState.Idle
    }
}
