package com.densmac.dashcam.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeFormatters {
    val dashcamTimestamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
    private val todayTime = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)

    fun displayCameraTime(value: LocalDateTime?): String {
        if (value == null) return "Unknown time"
        val date = value.toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "Today, ${value.format(todayTime)}"
            today.minusDays(1) -> "Yesterday, ${value.format(todayTime)}"
            else -> value.format(longDate)
        }
    }
}

fun Long.kbToDisplayMb(): String = String.format(Locale.US, "%.1f MB", this / 1024.0)

fun Long.mbToDisplayGb(): String = String.format(Locale.US, "%.1f GB", this / 1024.0)
