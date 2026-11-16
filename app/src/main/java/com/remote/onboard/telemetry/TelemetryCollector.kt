package com.remote.onboard.telemetry

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Collects phone telemetry independently from networking and USB.
 * Missing hardware or permissions leave the corresponding values null.
 */
class TelemetryCollector(
    context: Context,
    private val onUpdate: (TelemetrySnapshot) -> Unit
) : SensorEventListener, LocationListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var snapshot = TelemetrySnapshot()
    private var running = false
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    fun start() {
        if (running) return
        running = true

        registerSensor(Sensor.TYPE_ACCELEROMETER)
        registerSensor(Sensor.TYPE_GYROSCOPE)
        registerSensor(Sensor.TYPE_ROTATION_VECTOR)
        refreshBattery()
        startLocation()
        publish()
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
            // Permission may have been revoked while the service was running.
        }
    }

    private fun registerSensor(type: Int) {
        sensorManager.getDefaultSensor(type)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun startLocation() {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            val provider = when {
                hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            } ?: return

            locationManager.getLastKnownLocation(provider)?.let(::updateLocation)
            locationManager.requestLocationUpdates(
                provider, 1_000L, 0f, this, Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            // Location remains unavailable until permission is granted again.
        } catch (_: IllegalArgumentException) {
            // Provider is not available on this device.
        }
    }

    private fun updateLocation(location: Location) {
        snapshot = snapshot.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            timestampMs = System.currentTimeMillis()
        )
        publish()
    }

    private fun refreshBattery() {
        val battery = appContext.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        snapshot = snapshot.copy(
            batteryPercent = percent,
            charging = charging,
            timestampMs = System.currentTimeMillis()
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val v = event.values
        snapshot = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> snapshot.copy(
                accelerationX = v[0], accelerationY = v[1], accelerationZ = v[2]
            )
            Sensor.TYPE_GYROSCOPE -> snapshot.copy(
                gyroX = v[0], gyroY = v[1], gyroZ = v[2]
            )
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, v)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                snapshot.copy(
                    orientationAzimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat(),
                    orientationPitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat(),
                    orientationRoll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
                )
            }
            else -> snapshot
        }.copy(timestampMs = System.currentTimeMillis())
        publish()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onLocationChanged(location: Location) = updateLocation(location)

    @Deprecated("Deprecated by Android; retained for compatibility with older API levels")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun publish() = onUpdate(snapshot)
}
