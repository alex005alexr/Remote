package com.remote.onboard.telemetry

data class TelemetrySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val accelerationX: Float? = null,
    val accelerationY: Float? = null,
    val accelerationZ: Float? = null,
    val gyroX: Float? = null,
    val gyroY: Float? = null,
    val gyroZ: Float? = null,
    val orientationAzimuth: Float? = null,
    val orientationPitch: Float? = null,
    val orientationRoll: Float? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null
)
