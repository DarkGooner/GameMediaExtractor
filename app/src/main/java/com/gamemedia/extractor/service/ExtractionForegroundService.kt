package com.gamemedia.extractor.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Lightweight foreground service wrapper. In this architecture the actual extraction runs
 * inside [com.gamemedia.extractor.work.ExtractionWorker] promoted to the foreground via
 * WorkManager's setForeground/setExpedited APIs (see WorkManager foreground service
 * integration docs). This service class is kept for cases where the app wants an explicit,
 * independently-controllable foreground presence (e.g. to survive process death scenarios
 * WorkManager alone doesn't cover on some OEM skins) and can be started as a no-op shell
 * that simply keeps the process alive while a worker is active.
 */
class ExtractionForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildProgressNotification(
            this, progress = 0, contentText = "Extraction service running"
        )
        startForeground(4201, notification)
        return START_STICKY
    }
}
