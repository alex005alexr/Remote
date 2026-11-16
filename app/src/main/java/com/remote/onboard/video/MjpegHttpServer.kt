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
    private val onClientCountChanged: (Int) -> Unit = {},
    private val onServerStarted: () -> Unit = {},
    private val onServerError: (String) -> Unit = {}
) {
    @Volatile private var running = false
    @Volatile private var latestFrame: ByteArray? = null
    @Volatile private var latestTelemetry: String = """{\"available\":false,\"message\":\"Esperando telemetría del teléfono\"}"""
    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()

    fun start() {
        if (running) return
        running = true
        thread(name = "remote-http-accept") {
            try {
                serverSocket = ServerSocket(port, 8)
                onServerStarted()
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    clients.add(socket)
                    onClientCountChanged(clients.size)
                    thread(name = "remote-http-client") { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (running) onServerError(e.message ?: e.javaClass.simpleName)
            } finally {
                running = false
            }
        }
    }

    fun publishJpeg(jpeg: ByteArray) {
        latestFrame = jpeg
    }

    fun publishTelemetryJson(json: String) { latestTelemetry = json }

    fun isRunning(): Boolean = running

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        clients.toList().forEach { try { it.close() } catch (_: Exception) { } }
        clients.clear()
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
            when (path.substringBefore("?")) {
                "/stream.mjpg" -> streamFrames(socket)
                "/telemetry.json" -> sendTelemetry(socket)
                else -> sendViewerPage(socket)
            }
        } catch (_: Exception) {
        } finally {
            clients.remove(socket)
            try { socket.close() } catch (_: Exception) { }
            onClientCountChanged(clients.size)
        }
    }


    private fun sendTelemetry(socket: Socket) {
        val bytes = latestTelemetry.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: " + bytes.size + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun sendViewerPage(socket: Socket) {
        val html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Remote · Telemetría del carro</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#0a111a;color:#edf4fb;font:15px Arial}
header{padding:12px 18px;background:#142231;display:flex;justify-content:space-between;gap:10px}
h1{font-size:18px;margin:0}.status{color:#9dd6ad;font-size:12px}
main{padding:12px;max-width:1500px;margin:auto;display:grid;grid-template-columns:minmax(0,2fr) minmax(280px,1fr);gap:12px}
.video,.panel{background:#111d2a;border:1px solid #26394c;border-radius:10px;padding:10px;min-width:0}
img{display:block;width:100%;height:auto;max-height:calc(100vh - 90px);object-fit:contain;background:#05090d}
h2{font-size:13px;color:#91cfe0;letter-spacing:.08em;margin:4px 0 10px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.metric{background:#1a2a3a;border-radius:8px;padding:10px;min-height:65px}
.metric span{display:block;color:#9eb1c2;font-size:11px;margin-bottom:7px}.metric strong{font-size:20px}
.unit{font-size:11px;color:#aab9c7;margin-left:3px}.wide{grid-column:1/-1}
.good{color:#8fdda8}.warn{color:#ffd17d}.muted{color:#aab9c7}
.compass{width:112px;height:112px;border:2px solid #577087;border-radius:50%;margin:6px auto;position:relative;background:repeating-conic-gradient(#263b4d 0deg 1deg,transparent 1deg 30deg)}
.compass:before{content:'N';position:absolute;top:5px;left:50%;transform:translateX(-50%);color:#ff8f82;font-weight:bold}
.needle{position:absolute;left:50%;top:15px;height:42px;width:3px;background:#ff8f82;transform-origin:50% 43px;transform:translateX(-50%)}
.horizon{height:105px;overflow:hidden;border:2px solid #6e8498;border-radius:8px;position:relative;background:#172b40;margin:8px 0}
.skyground{position:absolute;inset:-60%;background:linear-gradient(#3576a5 0 50%,#9b875e 50% 100%);transform-origin:center}
.cross{position:absolute;left:15%;right:15%;top:50%;height:2px;background:white}
.note{font-size:11px;color:#9eb1c2;line-height:1.4;margin-top:10px}
@media(max-width:800px){main{grid-template-columns:1fr}}
</style></head><body>
<header><h1>REMOTE · TELEMETRÍA DEL CARRO</h1><span class="status" id="connection">● CONECTANDO</span></header>
<main><section class="video"><h2>VIDEO EN DIRECTO</h2><img src="/stream.mjpg" alt="Esperando video"></section>
<aside class="panel"><h2>ESTADO DEL VEHÍCULO</h2><div class="grid">
<div class="metric"><span>MOVIMIENTO</span><strong id="motion" class="warn">Esperando</strong></div>
<div class="metric"><span>VELOCIDAD GPS</span><strong id="speed">—</strong><small class="unit">km/h</small></div>
<div class="metric"><span>INCLINACIÓN LATERAL</span><strong id="roll">—</strong><small class="unit">°</small></div>
<div class="metric"><span>INCLINACIÓN ADELANTE/ATRÁS</span><strong id="pitch">—</strong><small class="unit">°</small></div>
<div class="metric"><span>RUMBO</span><strong id="heading">—</strong><small class="unit">°</small></div>
<div class="metric"><span>ALTITUD</span><strong id="altitude">—</strong><small class="unit">m</small></div>
<div class="metric"><span>BATERÍA DEL TELÉFONO</span><strong id="battery">—</strong></div>
<div class="metric"><span>PRECISIÓN GPS</span><strong id="accuracy">—</strong><small class="unit">m</small></div>
<div class="metric wide"><span>HORIZONTE ARTIFICIAL</span><div class="horizon"><div class="skyground" id="horizon"></div><div class="cross"></div></div></div>
<div class="metric wide"><span>BRÚJULA · HACIA DÓNDE APUNTA</span><div class="compass"><div class="needle" id="needle"></div></div><strong id="direction" style="display:block;text-align:center">—</strong></div>
</div><div class="note" id="sensorNote">Esperando datos. Fija firmemente el teléfono al carro.</div>
<div class="note">La velocidad y el movimiento se estiman con GPS. La orientación depende de cómo esté montado el teléfono. El motor puede afectar la brújula.</div>
</aside></main>
<script>
const el=id=>document.getElementById(id),fmt=(v,d=1)=>Number.isFinite(v)?v.toFixed(d):'—';
const dirs=['Norte','Noreste','Este','Sureste','Sur','Suroeste','Oeste','Noroeste'];
function update(t){if(!t||!t.available){el('sensorNote').textContent=t?.message||'Esperando datos del teléfono';return}
el('connection').textContent='● TELEMETRÍA ACTIVA';el('speed').textContent=fmt(Number.isFinite(t.speed)?t.speed*3.6:null,1);
el('motion').textContent=t.motion||'Sin determinar';el('motion').className=t.motion==='En movimiento'?'good':(t.motion==='Detenido'?'muted':'warn');
el('roll').textContent=fmt(t.roll);el('pitch').textContent=fmt(t.pitch);el('heading').textContent=fmt(t.heading,0);el('altitude').textContent=fmt(t.altitude,0);
el('battery').textContent=Number.isFinite(t.battery)?t.battery+'%':'—';el('accuracy').textContent=fmt(t.accuracy,0);
if(Number.isFinite(t.heading)){const h=((t.heading%360)+360)%360;el('needle').style.transform='translateX(-50%) rotate('+h+'deg)';el('direction').textContent=dirs[Math.round(h/45)%8]+' · '+Math.round(h)+'°'}else el('direction').textContent='Sin rumbo';
if(Number.isFinite(t.roll)&&Number.isFinite(t.pitch)){const y=Math.max(-35,Math.min(35,t.pitch))*1.2;el('horizon').style.transform='rotate('+(-t.roll)+'deg) translateY('+y+'px)'}
el('sensorNote').textContent=Date.now()-(t.timestamp||Date.now())>5000?'Datos antiguos: revisa la conexión.':'Sensores del teléfono actualizados en tiempo real.'}
async function poll(){try{const r=await fetch('/telemetry.json?ts='+Date.now(),{cache:'no-store'});update(await r.json())}catch(e){el('connection').textContent='● SIN TELEMETRÍA'}}
setInterval(poll,500);poll();
</script></body></html>"""

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
            val frame = latestFrame
            if (frame == null || frame === lastFrame) {
                try { Thread.sleep(40) } catch (_: InterruptedException) { break }
                continue
            }
            out.write("--remote-frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
            out.write(frame)
            out.write("\r\n".toByteArray())
            out.flush()
            lastFrame = frame
        }
    }
}
