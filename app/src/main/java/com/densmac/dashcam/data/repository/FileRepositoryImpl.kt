package com.densmac.dashcam.data.repository

import android.net.Uri
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.data.api.DashcamApi
import com.densmac.dashcam.data.api.DashcamApiMapper
import com.densmac.dashcam.data.api.safeApiCall
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.repository.FileRepository
import com.densmac.dashcam.domain.usecase.GetFileBundlesUseCase
import com.google.gson.JsonParseException
import javax.inject.Inject

class FileRepositoryImpl @Inject constructor(
    private val api: DashcamApi,
    private val mapper: DashcamApiMapper,
    private val networkBinder: DashcamNetworkBinder
) : FileRepository {
    override suspend fun getFiles(folder: DashcamFolder, start: Int, end: Int): AppResult<List<DashcamFile>> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        val playback = safeApiCall({ api.playback("enter") }) { Unit }
        if (playback is AppResult.Failure) return playback
        return safeApiCall({
            api.getFileList(folder.apiValue, start, end)
        }) { response ->
            required(response.info).map { item ->
                mapper.file(item, folder, thumbnailUrl(item.name))
            }
        }
    }

    override suspend fun getBundles(folder: DashcamFolder): AppResult<List<DashcamFileBundle>> {
        val range = folderRange(folder)
        return when (val files = getFiles(folder, range.first, range.last)) {
            is AppResult.Success -> AppResult.Success(GetFileBundlesUseCase(this).buildBundles(files.data))
            is AppResult.Failure -> files
        }
    }

    override suspend fun takeSnapshot(): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.snapshot() }) { Unit }
    }

    override suspend fun deleteFile(path: String): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.deleteFile(path) }) { Unit }
    }

    override fun thumbnailUrl(path: String): String =
        Uri.parse(DashcamConstants.HTTP_BASE_URL)
            .buildUpon()
            .appendEncodedPath(DashcamConstants.ENDPOINT_GET_THUMBNAIL)
            .appendQueryParameter("file", path)
            .build()
            .toString()

    private suspend fun ensureBound(): AppResult<Unit> =
        when (val bind = networkBinder.findAndBindDashcamNetwork()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> bind
        }

    private fun folderRange(folder: DashcamFolder): IntRange = when (folder) {
        DashcamFolder.LOOP -> 0..199
        DashcamFolder.EVENT,
        DashcamFolder.PARK,
        DashcamFolder.EMR,
        DashcamFolder.RACE -> 0..99
    }

    private fun <T> required(value: T?): T = value ?: throw JsonParseException("Missing info")
}
