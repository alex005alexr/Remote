package com.remote.onboard.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.remote.onboard.telemetry.TelemetryCollector
import com.remote.onboard.telemetry.TelemetrySnapshot

class RemoteService : Service() {
    companion object {
        private const val CHANNEL_ID = "remote_status"
        private const val NOTIFICATION_ID = 1
        const val ACTION_TELEMETRY = "com.remote.onboard.TELEMETRY_UPDATE"
    }

    private lateinit var telemetryCollector: TelemetryCollector
    private var lastNotificationUpdate = 0L
    private var lastUiUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando telemetría…"))
        telemetryCollector = TelemetryCollector(this) { snapshot ->
            publishTelemetry(snapshot)
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

    private fun publishTelemetry(snapshot: TelemetrySnapshot) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUiUpdate < 250L) return
        lastUiUpdate = now
        val update = Intent(ACTION_TELEMETRY).setPackage(packageName).apply {
            snapshot.latitude?.let { putExtra("latitude", it) }
            snapshot.longitude?.let { putExtra("longitude", it) }
            snapshot.altitudeMeters?.let { putExtra("altitude", it) }
            snapshot.speedMetersPerSecond?.let { putExtra("speed", it) }
            snapshot.accuracyMeters?.let { putExtra("accuracy", it) }
            snapshot.orientationAzimuth?.let { putExtra("azimuth", it) }
            snapshot.orientationPitch?.let { putExtra("pitch", it) }
            snapshot.orientationRoll?.let { putExtra("roll", it) }
            snapshot.accelerationX?.let { putExtra("ax", it) }
            snapshot.accelerationY?.let { putExtra("ay", it) }
            snapshot.accelerationZ?.let { putExtra("az", it) }
            snapshot.gyroX?.let { putExtra("gx", it) }
            snapshot.gyroY?.let { putExtra("gy", it) }
            snapshot.gyroZ?.let { putExtra("gz", it) }
            snapshot.batteryPercent?.let { putExtra("battery", it) }
            snapshot.charging?.let { putExtra("charging", it) }
            putExtra("timestamp", snapshot.timestampMs)
        }
        sendBroadcast(update)
    }

    private fun updateTelemetryNotification(snapshot: TelemetrySnapshot) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdate < 3_000L) return
        lastNotificationUpdate = now
        val gps = if (snapshot.latitude != null && snapshot.longitude != null) "GPS activo"
            else "GPS esperando señal/permiso"
        val battery = snapshot.batteryPercent?.let { " · Batería $it%" } ?: ""
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("Sensores activos · $gps$battery"))
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
        val channel = NotificationChannel(CHANNEL_ID, "Estado de Remote", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
