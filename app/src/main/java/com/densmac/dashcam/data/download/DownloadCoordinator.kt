package com.densmac.dashcam.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide coordination for dashcam transfers. The camera exposes a single-session HTTP
 * server, so concurrent raw file GETs (e.g. a front+rear bundle) plus Library thumbnail reads
 * make it stall and time out mid-download. This gates all downloads to one active transfer at a
 * time and lets other camera reads back off while a transfer is running.
 */
@Singleton
class DownloadCoordinator @Inject constructor() {
    private val transferSlot = Semaphore(permits = 1)
    private val activeCount = AtomicInteger(0)
    private val _transferring = MutableStateFlow(false)
    val transferring: StateFlow<Boolean> = _transferring.asStateFlow()

    // True while a live camera stream is playing. Downloads pause their reads so the stream gets
    // the camera's single session to itself, then resume when it clears.
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    /** Set by the media viewer when a remote (camera) stream starts/stops. */
    fun setStreaming(active: Boolean) {
        _streaming.value = active
    }

    /** Suspend while a camera stream is playing (returns immediately when none is). */
    suspend fun awaitStreamingIdle() {
        if (!_streaming.value) return
        streaming.first { !it }
    }

    /** Run [block] as the sole active transfer; other callers queue until the slot is free. */
    suspend fun <T> withTransferSlot(block: suspend () -> T): T = transferSlot.withPermit {
        if (activeCount.incrementAndGet() > 0) _transferring.value = true
        try {
            block()
        } finally {
            if (activeCount.decrementAndGet() == 0) _transferring.value = false
        }
    }

    /** Suspend until no transfer is active, or [timeoutMs] elapses (so callers never block forever). */
    suspend fun awaitTransfersIdle(timeoutMs: Long) {
        if (!_transferring.value) return
        withTimeoutOrNull(timeoutMs) { transferring.first { !it } }
    }
}
