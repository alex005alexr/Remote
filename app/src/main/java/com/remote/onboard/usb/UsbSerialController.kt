package com.remote.onboard.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * USB serial connection used for initial ESP32/adapter bring-up.
 * It deliberately does not send control commands; protocol design comes later.
 */
class UsbSerialController(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onReceived: (String) -> Unit
) {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.remote.onboard.USB_PERMISSION"
        private const val BAUD_RATE = 115200
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private var selectedDriver: UsbSerialDriver? = null
    private var serialPort: UsbSerialPort? = null
    private var serialIoManager: SerialInputOutputManager? = null
    private var executor: ExecutorService? = null
    private var connection: android.hardware.usb.UsbDeviceConnection? = null
    private var receivedBytes = 0L

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                val driver = selectedDriver
                if (driver?.device?.deviceId == device.deviceId) openDriver(driver)
                else onStatus("El dispositivo autorizado ya no está disponible. Vuelve a conectar.")
            } else {
                onStatus("Permiso USB denegado. No se abrió el puerto serial.")
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun connectFirstAvailable() {
        closePort()
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            selectedDriver = null
            onStatus("No se detectó un adaptador serial compatible. Conecta el ESP32 por USB OTG.")
            return
        }

        val driver = drivers.first()
        selectedDriver = driver
        val device = driver.device
        val description = "VID 0x${device.vendorId.toString(16)} · PID 0x${device.productId.toString(16)}"
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                appContext,
                device.deviceId,
                Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            onStatus("Solicitando permiso USB para ${driver.javaClass.simpleName} ($description)…")
            usbManager.requestPermission(device, permissionIntent)
            return
        }
        openDriver(driver)
    }

    private fun openDriver(driver: UsbSerialDriver) {
        closePort()
        try {
            val usbConnection = usbManager.openDevice(driver.device)
                ?: run {
                    onStatus("Android no pudo abrir el dispositivo USB.")
                    return
                }
            val port = driver.ports.firstOrNull()
                ?: run {
                    usbConnection.close()
                    onStatus("El dispositivo no expone puertos serial.")
                    return
                }

            connection = usbConnection
            serialPort = port
            port.open(usbConnection)
            port.setParameters(
                BAUD_RATE,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            receivedBytes = 0L

            val ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    receivedBytes += data.size
                    val hex = data.take(24).joinToString(" ") { "%02X".format(it) }
                    onReceived("RX: ${data.size} bytes · total ${receivedBytes} · $hex")
                }

                override fun onRunError(e: Exception) {
                    onStatus("Error de lectura serial: ${e.message ?: e.javaClass.simpleName}")
                }
            })
            serialIoManager = ioManager
            executor = Executors.newSingleThreadExecutor().also { it.submit(ioManager) }
            onStatus("Puerto serial conectado · ${driver.javaClass.simpleName} · ${BAUD_RATE} baudios · 8N1. Esperando datos.")
        } catch (e: Exception) {
            closePort()
            onStatus("No se pudo abrir el puerto serial: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun disconnect() {
        closePort()
        selectedDriver = null
        onStatus("Puerto serial desconectado.")
    }

    private fun closePort() {
        serialIoManager?.stop()
        serialIoManager = null
        executor?.shutdownNow()
        executor = null
        try {
            serialPort?.close()
        } catch (_: Exception) {
        }
        serialPort = null
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
    }

    fun release() {
        disconnect()
        try {
            appContext.unregisterReceiver(permissionReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
