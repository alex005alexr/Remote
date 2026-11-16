package com.remote.onboard

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * First visual shell for the onboard camera/transmission screen.
 * Camera capture and network streaming are intentionally not wired yet.
 */
class TransmissionActivity : Activity() {
    private lateinit var cameraState: TextView
    private lateinit var streamState: TextView
    private lateinit var previewLabel: TextView
    private lateinit var frontButton: Button
    private lateinit var rearButton: Button
    private var selectedCamera = "Trasera"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(10, 15, 22))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val preview = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 7, 12))
        }
        previewLabel = TextView(this).apply {
            text = "VISTA PREVIA DE CÁMARA\n\nLa imagen aparecerá aquí\ncuando integremos la cámara."
            setTextColor(Color.rgb(205, 215, 225))
            textSize = 18f
            gravity = Gravity.CENTER
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        preview.addView(previewLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val liveBadge = TextView(this).apply {
            text = "●  SIN TRANSMISIÓN"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(150, 55, 45))
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        preview.addView(liveBadge, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply { setMargins(dp(12), dp(12), 0, 0) })

        val overlay = TextView(this).apply {
            text = "GPS: esperando  ·  ENLACE: sin conectar"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }
        preview.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        ).apply { setMargins(dp(12), 0, 0, dp(12)) })

        root.addView(preview, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3.2f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        panel.addView(TextView(this).apply {
            text = "REMOTE"
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        panel.addView(TextView(this).apply {
            text = "PANEL DE TRANSMISIÓN"
            textSize = 11f
            setTextColor(Color.rgb(145, 165, 185))
        })
        cameraState = panelText("Cámara seleccionada: Trasera")
        panel.addView(cameraState)
        streamState = panelText("Video: módulo pendiente")
        panel.addView(streamState)
        panel.addView(panelLabel("SELECCIONAR CÁMARA"))
        frontButton = Button(this).apply {
            text = "Cámara frontal"
            setOnClickListener { selectCamera("Frontal") }
        }
        rearButton = Button(this).apply {
            text = "Cámara trasera"
            setOnClickListener { selectCamera("Trasera") }
        }
        panel.addView(frontButton)
        panel.addView(rearButton)
        panel.addView(panelLabel("ESTADO DEL SISTEMA"))
        panel.addView(panelText("USB / ESP32: independiente"))
        panel.addView(panelText("Red de video: aún no configurada"))
        panel.addView(Button(this).apply {
            text = "Volver al diagnóstico"
            setOnClickListener { finish() }
        })
        root.addView(panel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f))
        setContentView(root)
        selectCamera(selectedCamera)
    }

    private fun selectCamera(camera: String) {
        selectedCamera = camera
        cameraState.text = "Cámara seleccionada: $camera"
        previewLabel.text = "VISTA PREVIA DE CÁMARA\n\nCámara $camera seleccionada\nLa imagen aparecerá aquí al integrar CameraX."
        frontButton.isEnabled = camera != "Frontal"
        rearButton.isEnabled = camera != "Trasera"
        streamState.text = "Video: vista de interfaz (sin captura)"
    }

    private fun panelText(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(Color.rgb(215, 225, 235))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun panelLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 10f
        setTextColor(Color.rgb(110, 190, 210))
        setPadding(0, dp(14), 0, dp(3))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
