package com.remote.onboard

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.remote.onboard.video.MjpegHttpServer
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Onboard camera screen and first-stage Wi-Fi LAN MJPEG broadcaster.
 * The PC viewer is served directly by this phone on port 8080.
 */
class TransmissionActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraState: TextView
    private lateinit var serverState: TextView
    private lateinit var viewerAddress: TextView
    private lateinit var clientState: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var server: MjpegHttpServer? = null
    private var selectedLens = CameraSelector.LENS_FACING_BACK
    private var cameraPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildInterface()
        if (hasCameraPermission()) startCameraAndServer() else requestCameraPermission()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0A0F16.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val previewFrame = FrameLayout(this).apply { setBackgroundColor(0xFF03070C.toInt()) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(0, -1, 3.1f))

        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(4), dp(4))
        }
        panelContent.addView(TextView(this).apply {
            text = "REMOTE · CÁMARA"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
        })
        cameraState = infoText("Cámara: preparando…")
        panelContent.addView(cameraState)
        panelContent.addView(labelText("CÁMARA"))
        panelContent.addView(Button(this).apply {
            text = "Usar cámara trasera"
            setOnClickListener { switchCamera(CameraSelector.LENS_FACING_BACK) }
        })
        panelContent.addView(Button(this).apply {
            text = "Usar cámara frontal"
            setOnClickListener { switchCamera(CameraSelector.LENS_FACING_FRONT) }
        })
        panelContent.addView(labelText("VISOR EN LA PC · MISMA WI-FI"))
        viewerAddress = infoText("Buscando dirección de red…").apply {
            setTextIsSelectable(true)
        }
        panelContent.addView(viewerAddress)
        serverState = infoText("Servidor: iniciando…")
        panelContent.addView(serverState)
        clientState = infoText("PC conectadas: 0")
        panelContent.addView(clientState)
        panelContent.addView(TextView(this).apply {
            text = "Abre la dirección indicada en el navegador de la PC. Ambos equipos deben estar conectados a la misma red Wi-Fi."
            textSize = 11f
            setTextColor(0xFFB7C5D3.toInt())
            setPadding(0, dp(8), 0, dp(8))
        })
        panelContent.addView(Button(this).apply {
            text = "Volver al diagnóstico"
            setOnClickListener { finish() }
        })
        val panelScroll = ScrollView(this).apply { addView(panelContent) }
        root.addView(panelScroll, LinearLayout.LayoutParams(0, -1, 1.25f))
        setContentView(root)
    }

    private fun startCameraAndServer() {
        if (server == null) {
            val httpServer = MjpegHttpServer(
                port = SERVER_PORT,
                onClientCountChanged = { count ->
                    runOnUiThread { clientState.text = "PC conectadas: $count" }
                },
                onServerStarted = {
                    runOnUiThread { serverState.text = "Servidor: activo en puerto $SERVER_PORT" }
                },
                onServerError = { message ->
                    runOnUiThread { serverState.text = "Error del servidor: $message" }
                }
            )
            server = httpServer
            httpServer.start()
            val ip = findWifiIpv4()
            viewerAddress.text = if (ip != null) "http://$ip:$SERVER_PORT" else
                "No se detectó IP Wi-Fi. Conecta el teléfono a la misma Wi-Fi que la PC."
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
            } catch (e: Exception) {
                cameraState.text = "No se pudo iniciar la cámara: ${e.message ?: "error"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder()
            .setTargetRotation(displayRotation())
            .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setTargetRotation(displayRotation())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(cameraExecutor) { image ->
            try {
                val jpeg = imageToJpeg(image)
                if (jpeg != null) server?.publishJpeg(jpeg)
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }

        val selector = CameraSelector.Builder().requireLensFacing(selectedLens).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
            cameraState.text = if (selectedLens == CameraSelector.LENS_FACING_BACK)
                "Cámara trasera: activa · 640×480" else "Cámara frontal: activa · 640×480"
        } catch (e: Exception) {
            cameraState.text = "No se pudo activar esta cámara: ${e.message ?: "no disponible"}"
        }
    }

    private fun switchCamera(lens: Int) {
        if (selectedLens == lens) return
        selectedLens = lens
        if (cameraProvider != null) bindCamera()
    }

    private fun imageToJpeg(image: ImageProxy): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + width * height / 2)
        copyPlane(image.planes[0].buffer, image.planes[0].rowStride,
            image.planes[0].pixelStride, width, height, nv21, 0, 1)
        val u = image.planes[1]
        val v = image.planes[2]
        var offset = ySize
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val uBuffer = u.buffer
        val vBuffer = v.buffer
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val ui = uBase + row * u.rowStride + col * u.pixelStride
                val vi = vBase + row * v.rowStride + col * v.pixelStride
                nv21[offset++] = vBuffer.get(vi)
                nv21[offset++] = uBuffer.get(ui)
            }
        }
        val output = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 62, output)
        val raw = output.toByteArray()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return raw

        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val rotatedOutput = ByteArrayOutputStream()
        rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 62, rotatedOutput)
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
        return rotatedOutput.toByteArray()
    }

    private fun copyPlane(
        buffer: ByteBuffer, rowStride: Int, pixelStride: Int,
        width: Int, height: Int, output: ByteArray, outputOffset: Int, outputPixelStride: Int
    ) {
        var outIndex = outputOffset
        val base = buffer.position()
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                output[outIndex] = buffer.get(base + rowStart + col * pixelStride)
                outIndex += outputPixelStride
            }
        }
    }

    private fun findWifiIpv4(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .sortedBy {
                    when {
                        it.name.startsWith("wlan", true) -> 0
                        it.name.startsWith("ap", true) -> 1
                        else -> 2
                    }
                }
            interfaces.asSequence()
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = windowManager.defaultDisplay.rotation

    private fun requestCameraPermission() {
        if (cameraPermissionRequested) return
        cameraPermissionRequested = true
        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (hasCameraPermission()) startCameraAndServer()
            else cameraState.text = "Permiso de cámara denegado. Autorízalo en Ajustes para transmitir."
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun infoText(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(0xFFE0E8F0.toInt())
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun labelText(value: String) = TextView(this).apply {
        text = value
        textSize = 10f
        setTextColor(0xFF6EBED2.toInt())
        setPadding(0, dp(12), 0, dp(3))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SERVER_PORT = 8080
        private const val CAMERA_PERMISSION_REQUEST = 3001
    }
}
