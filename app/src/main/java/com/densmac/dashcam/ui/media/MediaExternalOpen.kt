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
 *
 * If [preferredPackage] is set (the user's chosen default player) and still installed, the file
 * opens straight in it; otherwise the system "Open with" chooser is shown.
 */
fun openDashcamFileExternally(context: Context, localPath: String?, preferredPackage: String? = null) {
    val path = localPath ?: return
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "File is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeFor(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (preferredPackage != null && isPackageInstalled(context, preferredPackage)) {
        val direct = Intent(intent).setPackage(preferredPackage)
        if (context.startActivitySafely(direct)) return
    }
    if (!context.startActivitySafely(Intent.createChooser(intent, "Open with"))) {
        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
    }
}

/** Apps that can handle dashcam video, for the "default player" picker. */
data class ExternalApp(val packageName: String, val label: String)

fun videoPlayerApps(context: Context): List<ExternalApp> {
    val probe = Intent(Intent.ACTION_VIEW).setDataAndType(
        android.net.Uri.parse("content://probe/x.ts"),
        "video/mp2t"
    )
    val pm = context.packageManager
    return pm.queryIntentActivities(probe, 0)
        .mapNotNull { it.activityInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { ExternalApp(it.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.label.lowercase() }
}

fun appLabel(context: Context, packageName: String?): String? {
    packageName ?: return null
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}

private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
    "ts" -> "video/mp2t"
    "jpg", "jpeg" -> "image/jpeg"
    "mp4" -> "video/mp4"
    else -> "*/*"
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
}.getOrDefault(false)

private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching {
    startActivity(intent)
    true
}.getOrDefault(false)

/**
 * Share a downloaded dashcam file with other apps (WhatsApp, Gmail, Drive, …) via ACTION_SEND
 * and a FileProvider content URI, so the receiving app can read it.
 */
fun shareDashcamFile(context: Context, localPath: String?) {
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
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }.onFailure {
        Toast.makeText(context, "No app can share this file.", Toast.LENGTH_SHORT).show()
    }
}
