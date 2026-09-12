package com.habitbell.app.cast

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * # ScreenMirroringManager
 *
 * Central subsystem responsible for detecting external TV displays, managing screen mirroring state,
 * and coordinating manual and automatic orientation switching between horizontal landscape and
 * vertical portrait formats.
 *
 * ## Architectural Role & Relationships
 * - **Hardware Sensing**: Observes system [DisplayManager] callbacks to detect Miracast, HDMI,
 *   Wi-Fi Display (WiDi), and USB-C DisplayPort connections.
 * - **Manual Override Mode**: Enables users mirroring their screen via system Quick Settings
 *   (e.g., Google Cast Screen Mirroring, Samsung Smart View) to explicitly activate TV controls.
 * - **Orientation Dispatcher**: Coordinates with [com.habitbell.app.MainActivity] to dynamically
 *   reorient the Android Activity window according to user preference.
 * - **UI Surfaces**: Supplies reactive state streams to [com.habitbell.app.ui.screens.SessionScreen],
 *   [com.habitbell.app.ui.screens.ModernHomeScreenSample], and [com.habitbell.app.ui.screens.SettingsDrawer].
 *
 * ## Lifecycle & Concurrency Model
 * - Lifecycle is tied to [com.habitbell.app.engine.CentralSessionHandler] and spans the application process.
 * - Thread-safe state publishing utilizing Kotlin [StateFlow] collectors running on the Main dispatcher.
 *
 * @param application Process-level application context for resolving system [DisplayManager].
 */
class ScreenMirroringManager(private val application: Application) {

    companion object {
        @Volatile
        private var INSTANCE: ScreenMirroringManager? = null

        /**
         * Returns the process-level singleton instance of [ScreenMirroringManager].
         *
         * @param context Application or component context.
         * @return The active [ScreenMirroringManager] singleton.
         */
        fun getInstance(context: Context): ScreenMirroringManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenMirroringManager(context.applicationContext as Application).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Pure calculation function determining the next orientation when toggling.
         *
         * @param currentTarget Currently selected [ScreenOrientation].
         * @param isCurrentlyLandscape Whether the active window layout is currently rendered in landscape.
         * @return The resulting [ScreenOrientation] to apply.
         */
        fun calculateToggledOrientation(
            currentTarget: ScreenOrientation,
            isCurrentlyLandscape: Boolean
        ): ScreenOrientation {
            return when (currentTarget) {
                ScreenOrientation.LANDSCAPE -> ScreenOrientation.PORTRAIT
                ScreenOrientation.PORTRAIT -> ScreenOrientation.LANDSCAPE
                ScreenOrientation.AUTO -> {
                    if (isCurrentlyLandscape) ScreenOrientation.PORTRAIT else ScreenOrientation.LANDSCAPE
                }
            }
        }

        /**
         * Evaluates whether a given display ID corresponds to an external/secondary display
         * rather than the default mobile device panel.
         *
         * @param displayId System display integer identifier.
         * @return True if secondary/external display, false if primary default display.
         */
        fun isExternalDisplay(displayId: Int): Boolean {
            return displayId != Display.DEFAULT_DISPLAY
        }
    }

    /** Coroutine scope for state combination flows, isolated by a SupervisorJob. */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** System display service for monitoring physical and virtual external screens. */
    private val displayManager = application.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    /** Handler bound to the main application looper for display callback dispatching. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Mutable backing state indicating whether a physical external display is connected. */
    private val _isHardwareDisplayConnected = MutableStateFlow(false)

    /** Public immutable stream indicating whether an external hardware display is detected. */
    val isHardwareDisplayConnected: StateFlow<Boolean> = _isHardwareDisplayConnected.asStateFlow()

    /** Mutable backing state for the friendly name of the connected external display. */
    private val _externalDisplayName = MutableStateFlow<String?>(null)

    /** Public immutable stream emitting the friendly name of the connected external display, if any. */
    val externalDisplayName: StateFlow<String?> = _externalDisplayName.asStateFlow()

    /** Mutable backing state indicating whether manual Screen Mirroring mode is toggled on. */
    private val _isScreenMirroringManual = MutableStateFlow(false)

    /** Public immutable stream indicating whether manual Screen Mirroring mode is active. */
    val isScreenMirroringManual: StateFlow<Boolean> = _isScreenMirroringManual.asStateFlow()

    /**
     * Combined reactive stream emitting true if either hardware display detection
     * or manual screen mirroring mode is active.
     */
    val isScreenMirroringActive: StateFlow<Boolean> = combine(
        _isHardwareDisplayConnected,
        _isScreenMirroringManual
    ) { hwConnected, manualMode ->
        hwConnected || manualMode
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    /** Mutable backing state for the target orientation requested by the user. */
    private val _targetOrientation = MutableStateFlow(ScreenOrientation.AUTO)

    /** Public immutable stream of the currently requested [ScreenOrientation]. */
    val targetOrientation: StateFlow<ScreenOrientation> = _targetOrientation.asStateFlow()

    /** Mutable backing state tracking whether current effective display rendering is landscape. */
    private val _isLandscape = MutableStateFlow(false)

    /** Public immutable stream indicating whether the active display window is in horizontal landscape. */
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    /** Hardware display listener monitoring display attach/detach/change lifecycle events. */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            evaluateConnectedDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            evaluateConnectedDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            evaluateConnectedDisplays()
        }
    }

    init {
        // Register display listener on main looper
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        evaluateConnectedDisplays()

        // Initialize current device window orientation
        val initialOrientation = application.resources.configuration.orientation
        _isLandscape.value = (initialOrientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    /**
     * Scans currently connected system displays to detect secondary external screens
     * (e.g. HDMI monitors, Miracast receivers, wireless display adapters).
     */
    private fun evaluateConnectedDisplays() {
        val manager = displayManager ?: return
        val displays = manager.displays
        var foundExternal = false
        var displayName: String? = null

        for (display in displays) {
            // Display ID 0 is the primary built-in device screen (Display.DEFAULT_DISPLAY)
            if (isExternalDisplay(display.displayId)) {
                foundExternal = true
                displayName = display.name
                break
            }
        }

        _isHardwareDisplayConnected.value = foundExternal
        _externalDisplayName.value = displayName
    }

    /**
     * Toggles screen orientation between Horizontal ([ScreenOrientation.LANDSCAPE])
     * and Vertical ([ScreenOrientation.PORTRAIT]).
     *
     * If the current orientation is [ScreenOrientation.AUTO], toggles to the opposite
     * of the currently rendered window orientation.
     *
     * @return The newly assigned [ScreenOrientation].
     */
    fun toggleOrientation(): ScreenOrientation {
        val newTarget = calculateToggledOrientation(_targetOrientation.value, _isLandscape.value)
        _targetOrientation.value = newTarget
        _isLandscape.value = (newTarget == ScreenOrientation.LANDSCAPE)
        return newTarget
    }

    /**
     * Sets an explicit screen orientation target for display alignment.
     *
     * @param orientation Target [ScreenOrientation] (LANDSCAPE, PORTRAIT, or AUTO).
     */
    fun setOrientation(orientation: ScreenOrientation) {
        _targetOrientation.value = orientation
        if (orientation != ScreenOrientation.AUTO) {
            _isLandscape.value = (orientation == ScreenOrientation.LANDSCAPE)
        }
    }

    /**
     * Enables or disables manual Screen Mirroring mode.
     *
     * When enabled, mirroring UI controls (such as the orientation rotation button)
     * are surfaced even if the system [DisplayManager] has not registered a secondary
     * hardware display entity (e.g., when mirroring via Google Cast Screen Mirroring or Smart View).
     *
     * @param enabled True to engage manual screen mirroring mode; false to revert to automatic hardware sensing.
     */
    fun setScreenMirroringManual(enabled: Boolean) {
        _isScreenMirroringManual.value = enabled
        if (!enabled && !_isHardwareDisplayConnected.value) {
            // Reset orientation to auto if screen mirroring is completely disengaged
            _targetOrientation.value = ScreenOrientation.AUTO
        }
    }

    /**
     * Propagates system window configuration changes from Activity to sync effective landscape state.
     *
     * @param orientation New [Configuration.ORIENTATION_LANDSCAPE] or [Configuration.ORIENTATION_PORTRAIT] code.
     */
    fun notifyConfigurationChanged(orientation: Int) {
        _isLandscape.value = (orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    /**
     * Releases system display listeners and cancels active coroutine scopes.
     */
    fun destroy() {
        displayManager?.unregisterDisplayListener(displayListener)
        scope.cancel()
    }
}
