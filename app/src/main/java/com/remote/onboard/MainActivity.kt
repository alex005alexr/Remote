package com.remote.onboard

import android.Manifest
import android.content.Intent
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.remote.onboard.core.RemoteService

class MainActivity : Activity() {
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
                if (hasLocationPermission()) {
                    startRemoteService()
                } else {
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        LOCATION_PERMISSION_REQUEST
                    )
                    status.text = "Esperando permiso de ubicación…"
                }
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun startRemoteService() {
        val intent = Intent(this, RemoteService::class.java)
        ContextCompat.startForegroundService(this, intent)
        status.text = "Remote activo. Recopilando telemetría."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Los sensores y la batería funcionan aunque el usuario no conceda GPS.
            startRemoteService()
            if (!hasLocationPermission()) {
                status.text = "Remote activo. GPS sin permiso; sensores disponibles."
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1002
    }
}
