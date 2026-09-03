package com.habitbell.app.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalCastWebServer(private val context: Context, private val port: Int = 8888) {

    private val TAG = "LocalCastWebServer"
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        try {
            acquireLocks()

            System.setProperty("java.net.preferIPv4Stack", "true")
            val ipv4Any = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
            serverSocket = ServerSocket(port, 50, ipv4Any).apply {
                reuseAddress = true
            }
            isRunning = true

            registerNsd()

            Log.i(TAG, "TV Webcast Server listening on http://0.0.0.0:$port (LAN: ${getTvUrl()})")

            serverThread = thread(isDaemon = true, name = "HabitBell-TVServer") {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClientAsync(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Server accept error: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TV Webcast Server on port $port", e)
        }
    }

    private fun handleClientAsync(socket: Socket) {
        thread(isDaemon = true, name = "HabitBell-TVClient") {
            try {
                socket.soTimeout = 8000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: return@thread
                val parts = requestLine.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"

                Log.i(TAG, "TV Request from ${socket.remoteSocketAddress}: $path")

                val html = try {
                    context.assets.open("tv/index.html").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open tv/index.html asset", e)
                    "<!DOCTYPE html><html><head><title>Habit Bell TV</title></head><body style='background:#000;color:#fff;text-align:center;padding:50px;font-family:sans-serif;'><h1>Habit Bell TV Dashboard</h1><p>Ready for mindfulness sessions.</p></body></html>"
                }

                val bodyBytes = html.toByteArray(Charsets.UTF_8)
                val out: OutputStream = socket.getOutputStream()
                val response = StringBuilder()
                    .append("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: text/html; charset=UTF-8\r\n")
                    .append("Content-Length: ${bodyBytes.size}\r\n")
                    .append("Access-Control-Allow-Origin: *\r\n")
                    .append("Connection: close\r\n\r\n")
                    .toString()

                out.write(response.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()
                socket.close()
                Log.i(TAG, "Successfully served TV Webcast dashboard (${bodyBytes.size} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Error handling client socket: ${e.message}")
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun acquireLocks() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("HabitBellTVMulticast")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun registerNsd() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "HabitBellTV"
                serviceType = "_http._tcp."
                setPort(port)
            }
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    Log.i(TAG, "NSD registered: ${info?.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, err: Int) {
                    Log.w(TAG, "NSD registration failed: $err")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo?, err: Int) {}
            }
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD setup notice: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        try { registrationListener?.let { nsdManager?.unregisterService(it) } } catch (_: Exception) {}
        try { if (multicastLock?.isHeld == true) multicastLock?.release() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        multicastLock = null
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                if (intf.name.lowercase().contains("wlan") || intf.name.lowercase().contains("eth")) {
                    for (addr in intf.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getTvUrl(): String {
        val ip = getLocalIpAddress() ?: "192.168.1.2"
        return "http://$ip:$port"
    }
}
