package com.remote.onboard.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.remote.onboard.telemetry.TelemetryCollector
import com.remote.onboard.telemetry.TelemetrySnapshot

class RemoteService : Service() {
    companion object {
        private const val CHANNEL_ID = "remote_status"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var telemetryCollector: TelemetryCollector
    private var lastNotificationUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando telemetría…"))

        telemetryCollector = TelemetryCollector(this) { snapshot ->
            updateTelemetryNotification(snapshot)
        }
        telemetryCollector.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (::telemetryCollector.isInitialized) telemetryCollector.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateTelemetryNotification(snapshot: TelemetrySnapshot) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdate < 3_000L) return
        lastNotificationUpdate = now

        val gps = if (snapshot.latitude != null && snapshot.longitude != null) {
            "GPS activo"
        } else {
            "GPS esperando señal/permiso"
        }
        val battery = snapshot.batteryPercent?.let { " · Batería $it%" } ?: ""
        val message = "Sensores activos · $gps$battery"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

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
