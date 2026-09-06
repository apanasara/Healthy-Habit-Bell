/**
 * Samsung Tizen & LG webOS DIAL / SSDP Discovery Subsystem
 *
 * Architectural Role:
 * Subsystem within the Cast layer providing zero-install, zero-cloud TV discovery and
 * remote launch capabilities for non-Android smart TVs (principally Samsung Tizen OS and LG webOS).
 * Utilizes SSDP (Simple Service Discovery Protocol) over UDP multicast (239.255.255.250:1900)
 * to locate TVs advertising DIAL (Discovery and Launch) services (urn:dial-multiscreen-org:service:dial:1)
 * or UPnP MediaRenderer profiles.
 *
 * Component Relationships:
 * - Managed by: [com.habitbell.app.engine.CentralSessionHandler]
 * - Interacts with: [LocalCastWebServer] to provide the local TV dashboard URL
 * - Emits state to: UI layer presentation components via reactive [StateFlow]
 *
 * Concurrency & Lifecycle:
 * Background multicast UDP queries and HTTP XML description fetches operate asynchronously
 * on [kotlinx.coroutines.Dispatchers.IO] threads. Thread-safe state is exposed via immutable StateFlow.
 */
package com.habitbell.app.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

/**
 * Supported Smart TV operating systems and ecosystems.
 */
enum class SmartTvPlatform {
    SAMSUNG_TIZEN,
    LG_WEBOS,
    GENERIC_DIAL,
    UNKNOWN
}

/**
 * Immutable domain model representing a discovered Smart TV.
 *
 * @property id Unique device UDN or hardware identifier.
 * @property friendlyName User-facing TV name (e.g. "Samsung QLED 65", "LG OLED C3").
 * @property platform Operating system platform classification.
 * @property locationUrl SSDP device description XML endpoint URL.
 * @property dialBaseUrl DIAL application launch REST endpoint URL, if supported.
 * @property ipAddress Resolved IPv4 address of the TV.
 */
data class SmartTvDevice(
    val id: String,
    val friendlyName: String,
    val platform: SmartTvPlatform,
    val locationUrl: String,
    val dialBaseUrl: String? = null,
    val ipAddress: String
)

/**
 * DIAL & SSDP Network Discoverer orchestrating Samsung Tizen and LG webOS detection and remote launching.
 *
 * @param context Android application context for Wi-Fi multicast lock acquisition.
 */
class DialTvDiscoverer(private val context: Context) {

    private val tag = "DialTvDiscoverer"

    /** Dedicated IO coroutine scope for UDP socket operations and HTTP descriptor fetches. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Active scanning job handle. */
    private var scanJob: Job? = null

    /** Multicast lock allowing UDP discovery packets to pass through the Wi-Fi chip. */
    private var multicastLock: WifiManager.MulticastLock? = null

    /** Backing mutable state flow of discovered Smart TVs on the local network. */
    private val _discoveredTvs = MutableStateFlow<List<SmartTvDevice>>(emptyList())

    /** Public immutable stream of discovered Samsung, LG, and DIAL Smart TVs. */
    val discoveredTvs: StateFlow<List<SmartTvDevice>> = _discoveredTvs.asStateFlow()

    /** Execution flag indicating whether an active discovery scan is underway. */
    @Volatile
    var isScanning: Boolean = false
        private set

    companion object {
        /** Standard SSDP multicast address. */
        const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"

        /** Standard SSDP port. */
        const val SSDP_PORT = 1900

        /** DIAL protocol service search target. */
        const val SEARCH_TARGET_DIAL = "urn:dial-multiscreen-org:service:dial:1"

        /** UPnP MediaRenderer search target supported by Samsung and LG TVs. */
        const val SEARCH_TARGET_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        @Volatile
        private var instance: DialTvDiscoverer? = null

        /**
         * Returns the process-wide singleton instance of [DialTvDiscoverer].
         *
         * @param context Android context.
         * @return Thread-safe singleton instance.
         */
        fun getInstance(context: Context): DialTvDiscoverer {
            return instance ?: synchronized(this) {
                instance ?: DialTvDiscoverer(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Starts background SSDP discovery by broadcasting M-SEARCH probes for DIAL and MediaRenderer devices.
     */
    fun startDiscovery() {
        if (isScanning) return
        isScanning = true
        acquireMulticastLock()

        scanJob = scope.launch {
            try {
                sendMsearchProbes()
            } catch (e: Exception) {
                Log.e(tag, "Error during Smart TV SSDP discovery", e)
            } finally {
                isScanning = false
                releaseMulticastLock()
            }
        }
    }

    /**
     * Sends SSDP M-SEARCH UDP multicast datagrams and listens for unicast responses from TVs.
     */
    private suspend fun sendMsearchProbes() = withContext(Dispatchers.IO) {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            soTimeout = 4000 // 4s timeout for response collection
            bind(InetSocketAddress(0))
        }

        try {
            val multicastGroup = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)

            // Probe 1: DIAL protocol (Samsung Tizen, modern smart TVs)
            val dialProbe = buildMsearchPacket(SEARCH_TARGET_DIAL)
            val dialPacket = DatagramPacket(dialProbe, dialProbe.size, multicastGroup, SSDP_PORT)
            socket.send(dialPacket)

            // Probe 2: UPnP MediaRenderer (Samsung, LG webOS)
            val upnpProbe = buildMsearchPacket(SEARCH_TARGET_MEDIA_RENDERER)
            val upnpPacket = DatagramPacket(upnpProbe, upnpProbe.size, multicastGroup, SSDP_PORT)
            socket.send(upnpPacket)

            Log.i(tag, "SSDP M-SEARCH broadcast dispatched to $SSDP_MULTICAST_ADDRESS:$SSDP_PORT")

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val startTime = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startTime < 4500)) {
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                    val senderIp = receivePacket.address.hostAddress ?: ""
                    parseSsdpResponse(response, senderIp)
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    if (isActive) Log.w(tag, "Socket read error: ${e.message}")
                }
            }
        } finally {
            socket.close()
        }
    }

    /**
     * Builds an RFC-compliant SSDP M-SEARCH byte array.
     *
     * @param searchTarget The SSDP ST header value.
     * @return Formatted UTF-8 byte array.
     */
    private fun buildMsearchPacket(searchTarget: String): ByteArray {
        val mSearch = StringBuilder()
            .append("M-SEARCH * HTTP/1.1\r\n")
            .append("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n")
            .append("MAN: \"ssdp:discover\"\r\n")
            .append("MX: 3\r\n")
            .append("ST: $searchTarget\r\n")
            .append("\r\n")
            .toString()
        return mSearch.toByteArray(Charsets.UTF_8)
    }

    /**
     * Parses an incoming SSDP response header to extract device description endpoint and platform.
     *
     * @param response Raw HTTP/1.1 response string from TV.
     * @param senderIp IPv4 address of the responding TV.
     */
    private fun parseSsdpResponse(response: String, senderIp: String) {
        val lines = response.lines()
        var location: String? = null
        var usn: String? = null
        var dialUrl: String? = null

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.startsWith("location:") -> location = line.substringAfter(":").trim()
                lower.startsWith("usn:") -> usn = line.substringAfter(":").trim()
                lower.startsWith("application-url:") -> dialUrl = line.substringAfter(":").trim()
            }
        }

        if (location != null) {
            scope.launch {
                fetchDeviceDescription(location, usn ?: location, dialUrl, senderIp)
            }
        }
    }

    /**
     * Fetches and parses the UPnP / DIAL device description XML to determine friendly name and platform.
     *
     * @param locationUrl Device XML description URL.
     * @param usn Unique Service Name.
     * @param dialUrl DIAL Application URL header value, if present.
     * @param senderIp Device IP address.
     */
    private suspend fun fetchDeviceDescription(
        locationUrl: String,
        usn: String,
        dialUrl: String?,
        senderIp: String
    ) = withContext(Dispatchers.IO) {
        try {
            val url = URL(locationUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }

            if (connection.responseCode == 200) {
                val xml = connection.inputStream.bufferedReader().use { it.readText() }
                val friendlyName = extractXmlTag(xml, "friendlyName") ?: "Smart TV ($senderIp)"
                val manufacturer = extractXmlTag(xml, "manufacturer")?.lowercase() ?: ""
                val modelName = extractXmlTag(xml, "modelName")?.lowercase() ?: ""

                val platform = when {
                    manufacturer.contains("samsung") || modelName.contains("tizen") -> SmartTvPlatform.SAMSUNG_TIZEN
                    manufacturer.contains("lg") || modelName.contains("webos") -> SmartTvPlatform.LG_WEBOS
                    dialUrl != null -> SmartTvPlatform.GENERIC_DIAL
                    else -> SmartTvPlatform.UNKNOWN
                }

                val device = SmartTvDevice(
                    id = usn.substringBefore("::"),
                    friendlyName = friendlyName,
                    platform = platform,
                    locationUrl = locationUrl,
                    dialBaseUrl = dialUrl,
                    ipAddress = senderIp
                )

                addDevice(device)
                Log.i(tag, "Discovered Smart TV: ${device.friendlyName} [${device.platform}] at $senderIp")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch device description from $locationUrl: ${e.message}")
        }
    }

    /**
     * Thread-safely updates the discovered TVs state flow.
     *
     * @param device Discovered smart TV.
     */
    private fun addDevice(device: SmartTvDevice) {
        val current = _discoveredTvs.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id || it.ipAddress == device.ipAddress }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredTvs.value = current
    }

    /**
     * Simple fast XML tag content extractor without heavy parser overhead.
     *
     * @param xml Raw XML string.
     * @param tag Target tag name.
     * @return Extracted inner text or null.
     */
    fun extractXmlTag(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val startIdx = xml.indexOf(startTag)
        val endIdx = xml.indexOf(endTag)
        return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            xml.substring(startIdx + startTag.length, endIdx).trim()
        } else null
    }

    /**
     * Halts active SSDP scanning.
     */
    fun stopDiscovery() {
        isScanning = false
        scanJob?.cancel()
        scanJob = null
        releaseMulticastLock()
    }

    /**
     * Acquires Android Wi-Fi MulticastLock.
     */
    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("HabitBellDialMulticast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    /**
     * Releases Android Wi-Fi MulticastLock.
     */
    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
