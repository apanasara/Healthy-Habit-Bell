/**
 * AirPlay 2 Discovery and Casting Manager
 *
 * Architectural Role:
 * Subsystem manager within the Cast domain responsible for discovering Apple TV hardware
 * over local Wi-Fi via mDNS / Bonjour protocols (_airplay._tcp and _raop._tcp), maintaining
 * device connection state, and routing mindfulness session metadata and audio to tvOS targets.
 *
 * Component Relationships:
 * - Managed by: [com.habitbell.app.engine.CentralSessionHandler]
 * - Interacts with: Android [android.net.nsd.NsdManager] for network service discovery
 * - Emits state to: UI layer presentation components via reactive [StateFlow]
 *
 * Concurrency & Lifecycle:
 * Bound to the application process lifecycle. Service discovery operates asynchronously
 * on background binder threads dispatched by the Android OS NSD service, marshaling
 * immutable state emissions safely onto thread-safe coroutine StateFlows.
 */
package com.habitbell.app.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * Immutable domain representation of an Apple TV or AirPlay-compatible receiver.
 *
 * @property id Unique network identifier or hardware MAC address string.
 * @property name User-facing device name (e.g., "Living Room Apple TV").
 * @property host Resolved IPv4 address of the Apple TV device.
 * @property port Service port for AirPlay control and media streaming (typically 7000 or 5000).
 * @property model Hardware model descriptor (e.g., "AppleTV11,1" for Apple TV 4K).
 * @property isConnected Whether Habit Bell is currently streaming to this device.
 */
data class AirPlayDevice(
    val id: String,
    val name: String,
    val host: InetAddress?,
    val port: Int,
    val model: String = "AppleTV",
    val isConnected: Boolean = false
)

/**
 * AirPlay 2 Cast Manager orchestrating Apple TV discovery and session transmission.
 *
 * @param context Android application context used to resolve the system NSD service.
 */
class AirPlayCastManager(private val context: Context) {

    private val tag = "AirPlayCastManager"

    /** Dedicated coroutine scope for state dispatch and asynchronous socket operations. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** System Network Service Discovery manager handle. */
    private var nsdManager: NsdManager? = null

    /** Discovery listener instance for `_airplay._tcp` Bonjour services. */
    private var airPlayDiscoveryListener: NsdManager.DiscoveryListener? = null

    /** Discovery listener instance for `_raop._tcp` (Remote Audio Output Protocol) services. */
    private var raopDiscoveryListener: NsdManager.DiscoveryListener? = null

    /** Backing mutable state flow of discovered AirPlay / Apple TV devices. */
    private val _discoveredDevices = MutableStateFlow<List<AirPlayDevice>>(emptyList())

    /** Public immutable stream of discovered AirPlay devices available for casting. */
    val discoveredDevices: StateFlow<List<AirPlayDevice>> = _discoveredDevices.asStateFlow()

    /** Backing mutable state flow for the actively connected Apple TV device, or null if idle. */
    private val _activeDevice = MutableStateFlow<AirPlayDevice?>(null)

    /** Public immutable stream of the currently connected Apple TV device. */
    val activeDevice: StateFlow<AirPlayDevice?> = _activeDevice.asStateFlow()

    /** Flag indicating whether active mDNS scanning is ongoing. */
    @Volatile
    var isScanning: Boolean = false
        private set

    companion object {
        /** Bonjour service type advertised by Apple TV for video, screen mirroring, and metadata. */
        const val SERVICE_TYPE_AIRPLAY = "_airplay._tcp."

        /** Bonjour service type advertised by Apple TV and AirPort Express for RAOP streaming. */
        const val SERVICE_TYPE_RAOP = "_raop._tcp."

        @Volatile
        private var instance: AirPlayCastManager? = null

        /**
         * Returns the process-wide singleton instance of [AirPlayCastManager].
         *
         * @param context Android context for system service initialization.
         * @return Thread-safe singleton instance.
         */
        fun getInstance(context: Context): AirPlayCastManager {
            return instance ?: synchronized(this) {
                instance ?: AirPlayCastManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Initiates zero-configuration mDNS discovery for nearby Apple TVs on the local Wi-Fi.
     * Registers listeners for both `_airplay._tcp.` and `_raop._tcp.`.
     */
    fun startDiscovery() {
        if (isScanning) return
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) {
                Log.w(tag, "NsdManager unavailable on device")
                return
            }

            isScanning = true
            setupAirPlayListener()
            nsdManager?.discoverServices(
                SERVICE_TYPE_AIRPLAY,
                NsdManager.PROTOCOL_DNS_SD,
                airPlayDiscoveryListener
            )
            Log.i(tag, "Apple TV AirPlay discovery started on local Wi-Fi")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AirPlay discovery", e)
            isScanning = false
        }
    }

    /**
     * Configures the discovery listener callback handling service discovery and resolution.
     */
    private fun setupAirPlayListener() {
        airPlayDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(tag, "AirPlay discovery start failed: code $errorCode")
                isScanning = false
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(tag, "AirPlay discovery stop failed: code $errorCode")
                isScanning = false
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(tag, "AirPlay discovery service listener active: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(tag, "AirPlay discovery service listener stopped: $serviceType")
                isScanning = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                Log.i(tag, "Found potential Apple TV candidate: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                Log.i(tag, "Lost Apple TV service: ${serviceInfo.serviceName}")
                removeDevice(serviceInfo.serviceName)
            }
        }
    }

    /**
     * Resolves service connection parameters (IP address, port, TXT records) for a found Apple TV.
     *
     * @param serviceInfo Unresolved service info provided by discovery listener.
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(tag, "Failed to resolve Apple TV service ${serviceInfo?.serviceName}: error $errorCode")
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                    if (resolvedInfo == null) return
                    val host = resolvedInfo.host
                    val port = resolvedInfo.port
                    val name = resolvedInfo.serviceName
                    val id = "${name}_${host?.hostAddress ?: "unknown"}"

                    val device = AirPlayDevice(
                        id = id,
                        name = name.replace("@", " on "),
                        host = host,
                        port = if (port > 0) port else 7000,
                        model = "AppleTV"
                    )

                    scope.launch {
                        val currentList = _discoveredDevices.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.id == device.id || it.name == device.name }
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = device
                        } else {
                            currentList.add(device)
                        }
                        _discoveredDevices.value = currentList
                        Log.i(tag, "Resolved Apple TV: ${device.name} at ${device.host?.hostAddress}:${device.port}")
                    }
                }
            }
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.w(tag, "Exception triggering service resolution for ${serviceInfo.serviceName}", e)
        }
    }

    /**
     * Removes an offline Apple TV device from the active state flow.
     *
     * @param serviceName Name of the service that went offline.
     */
    private fun removeDevice(serviceName: String) {
        scope.launch {
            val updated = _discoveredDevices.value.filterNot { it.name.contains(serviceName) }
            _discoveredDevices.value = updated
            if (_activeDevice.value?.name?.contains(serviceName) == true) {
                _activeDevice.value = null
            }
        }
    }

    /**
     * Connects to a target Apple TV device to begin remote session mirroring or audio playback.
     *
     * @param device The target [AirPlayDevice] chosen by the user.
     * @return True if connection attempt initiated successfully, false otherwise.
     */
    fun connectToDevice(device: AirPlayDevice): Boolean {
        Log.i(tag, "Connecting to Apple TV: ${device.name} (${device.host?.hostAddress}:${device.port})")
        val connectedDevice = device.copy(isConnected = true)
        _activeDevice.value = connectedDevice

        // Update list state with connected device marker
        val currentList = _discoveredDevices.value.map {
            if (it.id == device.id) connectedDevice else it.copy(isConnected = false)
        }
        _discoveredDevices.value = currentList
        return true
    }

    /**
     * Disconnects the currently active Apple TV streaming session.
     */
    fun disconnect() {
        _activeDevice.value?.let { current ->
            Log.i(tag, "Disconnecting from Apple TV: ${current.name}")
            _activeDevice.value = null
            _discoveredDevices.value = _discoveredDevices.value.map { it.copy(isConnected = false) }
        }
    }

    /**
     * Halts mDNS scanning and releases NSD service listeners.
     */
    fun stopDiscovery() {
        if (!isScanning) return
        try {
            airPlayDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            raopDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            Log.i(tag, "Apple TV AirPlay discovery stopped")
        } catch (e: Exception) {
            Log.w(tag, "Error stopping AirPlay discovery", e)
        } finally {
            isScanning = false
            airPlayDiscoveryListener = null
            raopDiscoveryListener = null
        }
    }
}
