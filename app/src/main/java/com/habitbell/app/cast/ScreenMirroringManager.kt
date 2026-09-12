/**
 * # ScreenMirroringManager
 *
 * Centralized subsystem managing external display detection, TV screen mirroring lifecycle,
 * and dynamic screen rotation orientation alignment between mobile devices and TV screens.
 *
 * ## Architectural Role & Component Relationships
 * Core subsystem in `com.habitbell.app.cast`:
 * - Coordinates with Android OS [android.hardware.display.DisplayManager] via [DisplayManager.DisplayListener]
 *   to automatically detect active external displays (Miracast, Wi-Fi Display, Chromecast screen mirror, HDMI).
 * - Exposes reactive Kotlin [StateFlow] streams consumed by [com.habitbell.app.ui.viewmodel.HabitBellViewModel]
 *   and [com.habitbell.app.MainActivity] to dynamically request activity orientation updates (`requestedOrientation`).
 * - Drives user orientation rotation toggles on [com.habitbell.app.ui.screens.SessionScreen],
 *   [com.habitbell.app.ui.screens.ModernHomeScreenSample], and [com.habitbell.app.ui.screens.SettingsDrawer].
 *
 * ## Concurrency & Thread Safety
 * Process-level singleton instance. State mutation is thread-safe via atomic [MutableStateFlow] updates.
 * Display listener registrations execute safely on the main thread via [Looper.getMainLooper].
 *
 * ## Lifecycle
 * Instantiated within [com.habitbell.app.engine.CentralSessionHandler] and bound to the application lifecycle.
 */
package com.habitbell.app.cast

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Manager orchestrating external display monitoring and interactive screen rotation.
 *
 * @param context Application context for system service resolution.
 */
class ScreenMirroringManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScreenMirroringManager"

        @Volatile
        private var INSTANCE: ScreenMirroringManager? = null

        /**
         * Returns or initializes the process-level [ScreenMirroringManager] singleton instance.
         *
         * @param context Application context.
         * @return Authoritative [ScreenMirroringManager] instance.
         */
        fun getInstance(context: Context): ScreenMirroringManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenMirroringManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /** Process-level coroutine scope isolated with a SupervisorJob. */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** Android system display manager service. */
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    /** Main thread handler for receiving display lifecycle callbacks. */
    private val mainHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // Mutable Backing State Flows
    // -------------------------------------------------------------------------

    /** Mutable backing state indicating whether a physical/wireless external display is connected. */
    private val _isExternalDisplayConnected = MutableStateFlow(false)

    /** Mutable backing state tracking the human-readable display label of the connected TV. */
    private val _externalDisplayName = MutableStateFlow<String?>(null)

    /** Mutable backing state tracking manual screen mirroring mode toggle from the user interface. */
    private val _isScreenMirroringManual = MutableStateFlow(false)

    /** Mutable backing state tracking the current target requested orientation. */
    private val _targetOrientation = MutableStateFlow(ScreenOrientation.AUTO)

    /** Mutable backing state tracking whether the current layout is rendered in landscape orientation. */
    private val _isLandscape = MutableStateFlow(false)

    // -------------------------------------------------------------------------
    // Public Immutable Reactive Streams
    // -------------------------------------------------------------------------

    /** Read-only state flow emitting whether an external display is physically connected. */
    val isExternalDisplayConnected: StateFlow<Boolean> = _isExternalDisplayConnected.asStateFlow()

    /** Read-only state flow emitting the friendly label of the connected TV or external display. */
    val externalDisplayName: StateFlow<String?> = _externalDisplayName.asStateFlow()

    /** Read-only state flow emitting whether manual screen mirroring mode is enabled by user. */
    val isScreenMirroringManual: StateFlow<Boolean> = _isScreenMirroringManual.asStateFlow()

    /** Read-only state flow emitting the active target orientation setting. */
    val targetOrientation: StateFlow<ScreenOrientation> = _targetOrientation.asStateFlow()

    /** Read-only state flow emitting whether the display is currently in horizontal landscape format. */
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    /**
     * Authoritative reactive stream indicating whether Screen Mirroring is actively in effect.
     * Evaluates to `true` if an external display is hardware-detected OR if the user manually enabled
     * Screen Mirroring mode in Settings.
     */
    val isScreenMirroringActive: StateFlow<Boolean> = combine(
        _isExternalDisplayConnected,
        _isScreenMirroringManual
    ) { externalConnected, manualEnabled ->
        externalConnected || manualEnabled
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    // -------------------------------------------------------------------------
    // Display Listener Implementation
    // -------------------------------------------------------------------------

    /** Listener monitoring hardware display connection, disconnection, and property alterations. */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display added: id=$displayId")
            evaluateExternalDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: id=$displayId")
            evaluateExternalDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "Display changed: id=$displayId")
            evaluateExternalDisplays()
        }
    }

    init {
        // Register display listener on main thread
        try {
            displayManager?.registerDisplayListener(displayListener, mainHandler)
            evaluateExternalDisplays()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register DisplayListener", e)
        }
    }

    /**
     * Scans all system displays to identify external presentation / Miracast / wireless display surfaces.
     * Any non-default display (`displayId != Display.DEFAULT_DISPLAY`) is classified as external.
     */
    fun evaluateExternalDisplays() {
        try {
            val displays = displayManager?.displays ?: emptyArray()
            val externalDisplays = displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }

            val hasExternal = externalDisplays.isNotEmpty()
            val displayName = externalDisplays.firstOrNull()?.name

            _isExternalDisplayConnected.value = hasExternal
            _externalDisplayName.value = displayName

            Log.i(TAG, "External displays evaluated: count=${externalDisplays.size}, name=$displayName, connected=$hasExternal")
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating external displays", e)
        }
    }

    // -------------------------------------------------------------------------
    // Orientation Management & Rotation Control
    // -------------------------------------------------------------------------

    /**
     * Toggles screen orientation between horizontal [ScreenOrientation.LANDSCAPE] and
     * vertical [ScreenOrientation.PORTRAIT].
     *
     * If the current active state is landscape, flips to portrait; otherwise flips to landscape.
     *
     * @return The newly assigned [ScreenOrientation].
     */
    fun toggleOrientation(): ScreenOrientation {
        val nextOrientation = if (_isLandscape.value || _targetOrientation.value == ScreenOrientation.LANDSCAPE) {
            ScreenOrientation.PORTRAIT
        } else {
            ScreenOrientation.LANDSCAPE
        }
        setOrientation(nextOrientation)
        return nextOrientation
    }

    /**
     * Assigns the desired screen orientation target for display alignment.
     *
     * @param orientation Desired [ScreenOrientation] mode.
     */
    fun setOrientation(orientation: ScreenOrientation) {
        _targetOrientation.value = orientation
        when (orientation) {
            ScreenOrientation.LANDSCAPE -> _isLandscape.value = true
            ScreenOrientation.PORTRAIT -> _isLandscape.value = false
            ScreenOrientation.AUTO -> {
                // Keep _isLandscape in sync with actual configuration
            }
        }
        Log.i(TAG, "Target orientation set to: $orientation")
    }

    /**
     * Convenience method to rotate screen between horizontal and vertical orientations.
     *
     * @return The newly assigned [ScreenOrientation].
     */
    fun rotateScreen(): ScreenOrientation {
        return toggleOrientation()
    }

    /**
     * Enables or disables manual Screen Mirroring mode override.
     *
     * @param enabled True to manually engage screen mirroring controls; false to rely solely on hardware detection.
     */
    fun setScreenMirroringManual(enabled: Boolean) {
        _isScreenMirroringManual.value = enabled
        Log.i(TAG, "Manual screen mirroring mode set to: $enabled")
    }

    /**
     * Synchronizes internal orientation tracking with the host Android Activity's configuration changes.
     *
     * @param newOrientation Android system orientation value ([Configuration.ORIENTATION_LANDSCAPE] or [Configuration.ORIENTATION_PORTRAIT]).
     */
    fun notifyConfigurationChanged(newOrientation: Int) {
        val isLand = newOrientation == Configuration.ORIENTATION_LANDSCAPE
        _isLandscape.value = isLand
        if (_targetOrientation.value == ScreenOrientation.AUTO) {
            Log.d(TAG, "Configuration changed to isLandscape=$isLand (AUTO mode)")
        }
    }

    /**
     * Resets screen orientation back to system default automatic sensor tracking ([ScreenOrientation.AUTO]).
     */
    fun resetOrientation() {
        setOrientation(ScreenOrientation.AUTO)
    }

    /**
     * Unregisters system display listeners and terminates background monitoring resources.
     */
    fun destroy() {
        try {
            displayManager?.unregisterDisplayListener(displayListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering DisplayListener", e)
        }
    }
}
