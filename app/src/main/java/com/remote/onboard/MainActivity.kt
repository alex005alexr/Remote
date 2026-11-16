package com.remote.onboard

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.remote.onboard.core.RemoteService
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var telemetryView: TextView
    private lateinit var usbView: TextView
    private lateinit var statusView: TextView
    private var receiverRegistered = false

    private val telemetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RemoteService.ACTION_TELEMETRY) telemetryView.text = formatTelemetry(intent)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshUsbStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        telemetryView = sectionText("Todavía no hay datos. Inicia Remote.")
        usbView = sectionText("")
        statusView = sectionText("Remote detenido.")

        val startButton = Button(this).apply {
            text = "Iniciar telemetría"
            setOnClickListener {
                requestNotificationPermissionIfNeeded()
                if (hasLocationPermission()) startRemoteService()
                else requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST
                )
            }
        }
        val stopButton = Button(this).apply {
            text = "Detener sistema"
            setOnClickListener {
                stopService(Intent(this@MainActivity, RemoteService::class.java))
                statusView.text = "Remote detenido."
            }
        }
        val refreshUsbButton = Button(this).apply {
            text = "Actualizar dispositivos USB"
            setOnClickListener { refreshUsbStatus() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(TextView(this@MainActivity).apply {
                text = "REMOTE · DIAGNÓSTICO"
                textSize = 22f
                gravity = Gravity.CENTER
            })
            addView(statusView)
            addView(startButton)
            addView(stopButton)
            addView(TextView(this@MainActivity).apply {
                text = "TELEMETRÍA DEL TELÉFONO"
                textSize = 18f
                setPadding(0, 24, 0, 8)
            })
            addView(telemetryView)
            addView(TextView(this@MainActivity).apply {
                text = "USB OTG"
                textSize = 18f
                setPadding(0, 24, 0, 8)
            })
            addView(usbView)
            addView(refreshUsbButton)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        refreshUsbStatus()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, telemetryReceiver,
                IntentFilter(RemoteService.ACTION_TELEMETRY), ContextCompat.RECEIVER_NOT_EXPORTED)
            val usbFilter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(this, usbReceiver, usbFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        refreshUsbStatus()
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(telemetryReceiver)
            unregisterReceiver(usbReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun sectionText(initial: String) = TextView(this).apply {
        text = initial
        textSize = 15f
        setPadding(0, 4, 0, 4)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startRemoteService() {
        ContextCompat.startForegroundService(this, Intent(this, RemoteService::class.java))
        statusView.text = "Remote activo. Recopilando datos localmente."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            startRemoteService()
            if (!hasLocationPermission()) statusView.text = "Remote activo. GPS sin permiso; sensores disponibles."
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun formatTelemetry(intent: Intent): String {
        fun floatValue(key: String, digits: Int = 2): String {
            if (!intent.hasExtra(key)) return "—"
            return String.format(Locale.US, "%.${digits}f", intent.getFloatExtra(key, Float.NaN))
        }
        fun doubleValue(key: String, digits: Int = 2): String {
            if (!intent.hasExtra(key)) return "—"
            return String.format(Locale.US, "%.${digits}f", intent.getDoubleExtra(key, Double.NaN))
        }
        val gps = if (intent.hasExtra("latitude")) {
            "Latitud: ${doubleValue("latitude", 6)}\nLongitud: ${doubleValue("longitude", 6)}\n" +
                "Altitud: ${doubleValue("altitude")} m\nVelocidad: ${floatValue("speed")} m/s\n" +
                "Precisión: ${floatValue("accuracy")} m"
        } else "GPS: esperando señal o permiso"
        val battery = if (intent.hasExtra("battery")) "${intent.getIntExtra("battery", -1)}%" else "—"
        val charging = if (intent.getBooleanExtra("charging", false)) "Cargando" else "No cargando"
        return "$gps\n\nORIENTACIÓN\nAzimut: ${floatValue("azimuth")}°\n" +
            "Inclinación: ${floatValue("pitch")}°\nAlabeo: ${floatValue("roll")}°\n\n" +
            "ACELERÓMETRO (m/s²)\nX: ${floatValue("ax")}  Y: ${floatValue("ay")}  Z: ${floatValue("az")}\n\n" +
            "GIROSCOPIO (rad/s)\nX: ${floatValue("gx")}  Y: ${floatValue("gy")}  Z: ${floatValue("gz")}\n\n" +
            "BATERÍA: $battery · $charging"
    }

    private fun refreshUsbStatus() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values
        if (devices.isEmpty()) {
            usbView.text = "No hay dispositivos USB conectados.\nConecta un pendrive o, más adelante, el ESP32 mediante OTG."
            return
        }
        usbView.text = devices.joinToString("\n\n") { device ->
            val interfaces = (0 until device.interfaceCount).joinToString(", ") { index ->
                val usbInterface = device.getInterface(index)
                "clase ${usbInterface.interfaceClass}/subclase ${usbInterface.interfaceSubclass}"
            }
            "Dispositivo: ${device.deviceName}\nFabricante: ${device.manufacturerName ?: "No informado"}\n" +
                "Producto: ${device.productName ?: "No informado"}\nVID: 0x${device.vendorId.toString(16)} " +
                "PID: 0x${device.productId.toString(16)}\nInterfaces: $interfaces"
        }
    }

    companion object { private const val LOCATION_PERMISSION_REQUEST = 1002 }
}
