package com.doctorsonwheels.wheelmd

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class DownloadService : Service() {
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
        var instance: DownloadService? = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("DownloadService", "onCreate called")
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DownloadService", "onStartCommand called")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Model download starting...")
            .setContentText("Preparing to download model.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateProgress(percent: Int, text: String) {
        Log.d("DownloadService", "updateProgress: $percent% - $text")

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading model...")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun finishDownload() {
        Log.d("DownloadService", "finishDownload called")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download complete")
            .setContentText("Your model was downloaded successfully.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        stopForeground(false)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Model Downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d("DownloadService", "Notification channel created")
        }
    }
}
