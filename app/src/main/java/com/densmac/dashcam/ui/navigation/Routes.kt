package com.densmac.dashcam.ui.navigation

import android.net.Uri

sealed class Route(val path: String) {
    data object MainTabs : Route("main_tabs")
    data object Live : Route("live")
    data object Library : Route("library")
    data object Downloads : Route("downloads")
    data object Settings : Route("settings")
    data object ClipDetail : Route("clip_detail/{bundleId}") {
        fun create(bundleId: String): String = "clip_detail/${Uri.encode(bundleId)}"
    }
}
