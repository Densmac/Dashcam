package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.domain.model.DashcamCamera
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ParsedDashcamFilename(
    val localDateTime: LocalDateTime,
    val streamId: String,
    val camera: DashcamCamera,
    val extension: String
)

class DashcamFilenameParser @Inject constructor() {
    private val regex = Regex("""(?<date>\d{8})_(?<time>\d{6})_(?<streamId>\d+)_(?<camera>[fb])\.(?<ext>ts|jpg|jpeg|TXT|txt)""")
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)

    fun parse(filename: String): ParsedDashcamFilename? {
        val match = regex.matchEntire(filename.substringAfterLast('/')) ?: return null
        val camera = DashcamCamera.fromSuffix(match.groups["camera"]?.value) ?: return null
        val date = match.groups["date"]?.value ?: return null
        val time = match.groups["time"]?.value ?: return null
        val streamId = match.groups["streamId"]?.value ?: return null
        val ext = match.groups["ext"]?.value ?: return null
        return runCatching {
            ParsedDashcamFilename(
                localDateTime = LocalDateTime.parse(date + time, formatter),
                streamId = streamId,
                camera = camera,
                extension = ext.lowercase(Locale.US)
            )
        }.getOrNull()
    }
}
