package com.remote.onboard.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RemoteService : Service() {
    companion object {
        private const val CHANNEL_ID = "remote_status"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Sistema activo; módulos en preparación."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // El servicio será el coordinador. Cámara, sensores y USB se implementarán
        // como módulos independientes para que un fallo no detenga los demás.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Estado de Remote",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
