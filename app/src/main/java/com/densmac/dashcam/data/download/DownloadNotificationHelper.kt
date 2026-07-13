package com.densmac.dashcam.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.densmac.dashcam.MainActivity
import com.densmac.dashcam.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val channelId = "dashcam_downloads"

    fun foregroundInfo(title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Keep your phone connected to DASHCAM Wi-Fi")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()
        return ForegroundInfo(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            channelId,
            "Dashcam downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
