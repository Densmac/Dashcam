package com.densmac.dashcam.core.player

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject

class LocalFileIntentViewer @Inject constructor() {
    fun openLocalFile(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, if (path.endsWith(".jpg", true)) "image/*" else "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Open dashcam file"))
    }
}
