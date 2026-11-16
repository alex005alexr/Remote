package com.remote.onboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.remote.onboard.core.RemoteService

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            text = "Remote listo. Servicio detenido."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "Iniciar sistema"
            setOnClickListener {
                requestNotificationPermissionIfNeeded()
                val intent = Intent(this@MainActivity, RemoteService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, intent)
                status.text = "Solicitud de inicio enviada."
            }
        }

        val stopButton = Button(this).apply {
            text = "Detener sistema"
            setOnClickListener {
                stopService(Intent(this@MainActivity, RemoteService::class.java))
                status.text = "Servicio detenido."
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(status)
            addView(startButton)
            addView(stopButton)
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
