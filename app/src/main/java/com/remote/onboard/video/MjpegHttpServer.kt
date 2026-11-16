package com.remote.onboard.video

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Small HTTP/MJPEG server for the first Wi-Fi LAN test.
 * It binds to all interfaces but is intended for a trusted local network only.
 */
class MjpegHttpServer(
    private val port: Int = 8080,
    private val onClientCountChanged: (Int) -> Unit = {}
) {
    @Volatile private var running = false
    @Volatile private var latestFrame: ByteArray? = null
    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val frameLock = Object()

    fun start() {
        if (running) return
        running = true
        thread(name = "remote-http-accept") {
            try {
                serverSocket = ServerSocket(port, 8)
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    clients.add(socket)
                    onClientCountChanged(clients.size)
                    thread(name = "remote-http-client") { handleClient(socket) }
                }
            } catch (_: Exception) {
                // Closing the server socket is the normal stop path.
            } finally {
                running = false
            }
        }
    }

    fun publishJpeg(jpeg: ByteArray) {
        latestFrame = jpeg
        synchronized(frameLock) { frameLock.notifyAll() }
    }

    fun isRunning(): Boolean = running

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        clients.toList().forEach { try { it.close() } catch (_: Exception) { } }
        clients.clear()
        synchronized(frameLock) { frameLock.notifyAll() }
        onClientCountChanged(0)
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val request = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            socket.soTimeout = 0
            val path = request.split(" ").getOrNull(1) ?: "/"
            if (path == "/stream.mjpg") streamFrames(socket)
            else sendViewerPage(socket)
        } catch (_: Exception) {
        } finally {
            clients.remove(socket)
            try { socket.close() } catch (_: Exception) { }
            onClientCountChanged(clients.size)
        }
    }

    private fun sendViewerPage(socket: Socket) {
        val html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Remote · Cámara en directo</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#101820;color:#eef4fa;font:16px Arial,sans-serif}
header{padding:14px 20px;background:#182633;display:flex;justify-content:space-between;align-items:center}
h1{font-size:18px;margin:0}.status{color:#9dd6ad;font-size:13px}
main{padding:12px;max-width:1200px;margin:auto}img{display:block;width:100%;height:auto;max-height:calc(100vh - 90px);object-fit:contain;background:#05090d;border-radius:8px}
.note{color:#aebdca;font-size:13px;margin-top:10px}
</style></head><body><header><h1>REMOTE · TRANSMISIÓN LOCAL</h1><span class="status">● CONECTANDO</span></header>
<main><img src="/stream.mjpg" alt="Esperando video"><div class="note">Video en red Wi-Fi local · Para detener, cierra la pantalla de transmisión en el teléfono.</div></main>
<script>const s=document.querySelector('.status'),i=document.querySelector('img');i.onload=()=>s.textContent='● RECIBIENDO VIDEO';i.onerror=()=>s.textContent='● ESPERANDO SEÑAL';</script>
</body></html>"""
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun streamFrames(socket: Socket) {
        val out = socket.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\n" +
            "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
            "Connection: close\r\n" +
            "Content-Type: multipart/x-mixed-replace; boundary=remote-frame\r\n\r\n").toByteArray())
        out.flush()
        var lastFrame: ByteArray? = null
        while (running && !socket.isClosed) {
            val frame = synchronized(frameLock) {
                if (latestFrame === lastFrame || latestFrame == null) {
                    try { frameLock.wait(1000) } catch (_: InterruptedException) { }
                }
                latestFrame
            } ?: continue
            if (frame === lastFrame) continue
            out.write("--remote-frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
            out.write(frame)
            out.write("\r\n".toByteArray())
            out.flush()
            lastFrame = frame
        }
    }
}
