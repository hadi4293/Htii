package com.example.htii

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class StreamingService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.stream_notification_title),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LocalMediaServerRegistry.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, StreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.stream_notification_title))
            .setContentText(getString(R.string.stream_notification_text))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .addAction(0, getString(R.string.stop_stream), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.htii.action.START_STREAM"
        const val ACTION_STOP = "com.example.htii.action.STOP_STREAM"
        private const val CHANNEL_ID = "local_stream"
        private const val NOTIFICATION_ID = 2401
    }
}

object LocalMediaServerRegistry {
    @Volatile
    private var server: LocalMediaServer? = null

    fun register(mediaServer: LocalMediaServer) {
        server = mediaServer
    }

    fun stop() {
        server?.stop()
    }
}
