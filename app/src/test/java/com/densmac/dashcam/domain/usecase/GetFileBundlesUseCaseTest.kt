package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.model.DashcamMediaType
import com.densmac.dashcam.domain.repository.FileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GetFileBundlesUseCaseTest {
    private val useCase = GetFileBundlesUseCase(FakeFileRepository())

    @Test
    fun pairsFrontRearWithinThreeSeconds() {
        val bundles = useCase.buildBundles(
            listOf(
                file("front.ts", DashcamCamera.FRONT, second = 10),
                file("rear.ts", DashcamCamera.REAR, second = 12)
            )
        )
        assertEquals(1, bundles.size)
        assertTrue(bundles.single().isCompletePair)
    }

    @Test
    fun doesNotPairFilesBeyondThreeSeconds() {
        val bundles = useCase.buildBundles(
            listOf(
                file("front.ts", DashcamCamera.FRONT, second = 10),
                file("rear.ts", DashcamCamera.REAR, second = 14)
            )
        )
        assertEquals(2, bundles.size)
        assertFalse(bundles.any(DashcamFileBundle::isCompletePair))
    }

    @Test
    fun keepsUnmatchedFrontFile() {
        val bundles = useCase.buildBundles(listOf(file("front.ts", DashcamCamera.FRONT, second = 10)))
        assertEquals(1, bundles.size)
        assertEquals(DashcamCamera.FRONT, bundles.single().front?.camera)
        assertEquals(null, bundles.single().rear)
    }

    @Test
    fun keepsUnmatchedRearFile() {
        val bundles = useCase.buildBundles(listOf(file("rear.ts", DashcamCamera.REAR, second = 10)))
        assertEquals(1, bundles.size)
        assertEquals(DashcamCamera.REAR, bundles.single().rear?.camera)
        assertEquals(null, bundles.single().front)
    }

    @Test
    fun invokesRepositoryForFolder() = runTest {
        val repository = FakeFileRepository(listOf(file("front.ts", DashcamCamera.FRONT, second = 10)))
        val result = GetFileBundlesUseCase(repository)(DashcamFolder.LOOP)
        assertTrue(result is AppResult.Success)
    }

    private fun file(filename: String, camera: DashcamCamera, second: Int): DashcamFile =
        DashcamFile(
            path = "/mnt/sdcard/$filename",
            filename = filename,
            folder = DashcamFolder.LOOP,
            camera = camera,
            mediaType = DashcamMediaType.VIDEO,
            sizeKb = 1024,
            cameraLocalDateTime = LocalDateTime.of(2026, 7, 13, 19, 21, second),
            createTimeRaw = null,
            createTimeString = null,
            durationSeconds = null,
            typeRaw = 2,
            thumbnailUrl = null
        )
}

private class FakeFileRepository(
    private val files: List<DashcamFile> = emptyList()
) : FileRepository {
    override suspend fun getFiles(folder: DashcamFolder, start: Int, end: Int): AppResult<List<DashcamFile>> =
        AppResult.Success(files)

    override suspend fun getBundles(folder: DashcamFolder): AppResult<List<DashcamFileBundle>> =
        AppResult.Success(emptyList())

    override suspend fun takeSnapshot(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun deleteFile(path: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun thumbnailUrl(path: String): String = ""
}
