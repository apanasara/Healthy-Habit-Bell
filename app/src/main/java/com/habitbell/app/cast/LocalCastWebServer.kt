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

/**
 * Embedded lightweight HTTP server and Network Service Discovery (NSD) broadcaster.
 *
 * Enables zero-cloud, privacy-first screen mirroring to any Smart TV or computer web browser
 * on the same local area network (LAN):
 * 1. Hosts an embedded daemon listening on port 8888 (e.g. `http://192.168.1.X:8888`).
 * 2. Advertises an mDNS / Bonjour service (`_http._tcp.`) named `HabitBellTV` for zero-configuration discovery.
 * 3. Serves the pre-compiled, responsive TV web dashboard from `assets/tv/index.html`.
 *
 * @param context Android context for asset resolution and Wi-Fi system service acquisition.
 * @param port TCP port to bind the HTTP server socket (defaults to `8888`).
 */
class LocalCastWebServer(private val context: Context, private val port: Int = 8888) {

    private val TAG = "LocalCastWebServer"

    /** Bound server socket listening for incoming HTTP connections. */
    private var serverSocket: ServerSocket? = null

    /** Background daemon thread executing the socket accept loop. */
    private var serverThread: Thread? = null

    /** Multicast lock allowing mDNS discovery packets to traverse Wi-Fi interfaces. */
    private var multicastLock: WifiManager.MulticastLock? = null

    /** System Network Service Discovery manager for local mDNS service registration. */
    private var nsdManager: NsdManager? = null

    /** Listener callback for NSD service registration events. */
    private var registrationListener: NsdManager.RegistrationListener? = null

    /** Volatile execution flag indicating whether the HTTP server is currently listening. */
    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Starts the local HTTP server daemon and advertises the NSD Bonjour service.
     * Binds to `0.0.0.0` (all IPv4 network interfaces).
     */
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

    /**
     * Handles an incoming HTTP request on a dedicated background thread,
     * returning the bundled `tv/index.html` dashboard asset with CORS headers.
     *
     * @param socket Client socket connection.
     */
    private fun handleClientAsync(socket: Socket) {
        thread(isDaemon = true, name = "HabitBell-TVClient") {
            try {
                socket.soTimeout = 8000 // 8s read timeout to prevent stale client hangs
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: return@thread
                val parts = requestLine.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"

                Log.i(TAG, "TV Request from ${socket.remoteSocketAddress}: $path")

                // Read bundled TV dashboard HTML asset
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

    /**
     * Acquires a Wi-Fi MulticastLock allowing mDNS discovery packets to pass through the radio chip.
     */
    private fun acquireLocks() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("HabitBellTVMulticast")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
    }

    /**
     * Registers the mDNS service (`_http._tcp.`) via [NsdManager] so nearby Smart TVs
     * can automatically locate the web broadcast without manual IP typing.
     */
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

    /**
     * Halts the HTTP server socket, releases multicast locks, and unregisters NSD services.
     */
    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        try { registrationListener?.let { nsdManager?.unregisterService(it) } } catch (_: Exception) {}
        try { if (multicastLock?.isHeld == true) multicastLock?.release() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        multicastLock = null
    }

    /**
     * Discovers the device's non-loopback IPv4 address on Wi-Fi (`wlan`) or Ethernet (`eth`) interfaces.
     *
     * @return IPv4 address string (e.g., "192.168.1.15"), or null if offline.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            // Priority 1: Match active wireless or ethernet adapters
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
            // Priority 2: Fallback to any valid non-loopback IPv4 interface
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

    /**
     * Resolves the full local URL for browser casting.
     *
     * @return HTTP URL string (e.g., `http://192.168.1.45:8888`).
     */
    fun getTvUrl(): String {
        val ip = getLocalIpAddress() ?: "192.168.1.2"
        return "http://$ip:$port"
    }
}
