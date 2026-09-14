package com.gamemedia.extractor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gamemedia.extractor.MainActivity
import com.gamemedia.extractor.R

object NotificationHelper {
    const val CHANNEL_ID = "extraction_progress_channel"
    private const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification(context: Context, progress: Int, contentText: String): Notification {
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Extracting media…")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .build()
    }

    fun updateProgressNotification(context: Context, percent: Int, contentText: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildProgressNotification(context, percent, contentText))
    }

    fun finishNotification(context: Context, status: String) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (status == "COMPLETED") "Extraction complete" else "Extraction $status")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
