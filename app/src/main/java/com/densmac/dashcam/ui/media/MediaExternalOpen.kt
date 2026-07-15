package com.densmac.dashcam.ui.media

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Open a downloaded dashcam file in an external app (VLC, MX Player, gallery, ...) via a
 * FileProvider content URI. `.ts` is advertised as video/mp2t and `.jpg` as image/jpeg so
 * external players pick the right handler; the raw file is never renamed away from `.ts`.
 */
fun openDashcamFileExternally(context: Context, localPath: String?) {
    val path = localPath ?: return
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "File is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    val mime = when (file.extension.lowercase()) {
        "ts" -> "video/mp2t"
        "jpg", "jpeg" -> "image/jpeg"
        "mp4" -> "video/mp4"
        else -> "*/*"
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }.onFailure {
        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
    }
}
