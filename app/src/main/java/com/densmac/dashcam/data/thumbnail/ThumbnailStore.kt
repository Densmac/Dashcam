package com.densmac.dashcam.data.thumbnail

import android.content.Context
import com.densmac.dashcam.core.common.DispatchersProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent disk cache for clip thumbnails. The camera is single-session, so re-fetching a
 * thumbnail every time the list is opened or scrolled is slow; caching the JPEG bytes on disk makes
 * revisits and scroll-backs instant and cuts the load on the one camera connection. Keyed by the
 * remote file path (which is stable per recording).
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @param:ApplicationContext context: Context,
    private val dispatchers: DispatchersProvider
) {
    private val dir = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    suspend fun get(remotePath: String): ByteArray? = withContext(dispatchers.io) {
        val file = fileFor(remotePath)
        if (file.exists() && file.length() > 0) runCatching { file.readBytes() }.getOrNull() else null
    }

    suspend fun put(remotePath: String, bytes: ByteArray) = withContext(dispatchers.io) {
        runCatching {
            val file = fileFor(remotePath)
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
        }
        Unit
    }

    private fun fileFor(remotePath: String): File = File(dir, key(remotePath))

    private fun key(remotePath: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(remotePath.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".jpg"
    }
}
