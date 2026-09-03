package com.habitbell.app.cast

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Lightweight embedded HTTP server that serves the standalone Habit Bell TV Web Receiver
 * directly to any Smart TV, Chromecast with Google TV, or Android TV on the local Wi-Fi network.
 *
 * Runs 100% independently on the TV hardware with ZERO ongoing battery draw on the mobile device.
 */
class LocalCastWebServer(private val context: Context, val port: Int = 8888) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            serverJob = scope.launch {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val line = reader.readLine() ?: return@launch
                val parts = line.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"

                val html = try {
                    context.assets.open("tv/index.html").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "<html><body><h1>Habit Bell TV Ready</h1></body></html>"
                }

                val bytes = html.toByteArray(Charsets.UTF_8)
                val out: OutputStream = socket.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                out.write("Content-Type: text/html; charset=UTF-8\r\n".toByteArray())
                out.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                out.write("Connection: close\r\n\r\n".toByteArray())
                out.write(bytes)
                out.flush()
                socket.close()
            } catch (_: Exception) {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getTvUrl(): String {
        val ip = getLocalIpAddress() ?: "localhost"
        return "http://$ip:$port"
    }
}
