package com.habitbell.app.engine

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import com.habitbell.app.cast.AirPlayCastManager
import com.habitbell.app.cast.HabitBellCastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Contextual classification of the active display automation state.
 */
enum class DisplayCurtainMode {
    NONE,
    POCKET,
    CAR_HUD,
    TV_CAST,
    WATCH
}

/**
 * Immutable state snapshot governing the display curtain rendering.
 *
 * @property isActive Whether the pure #000000 AMOLED curtain is currently covering the screen.
 * @property mode Active trigger context ([DisplayCurtainMode]).
 * @property title Primary badge text to render on the blackout curtain.
 * @property subtitle Explanatory secondary text for wake interaction.
 * @property deviceName External connected target device name.
 */
data class DisplayCurtainState(
    val isActive: Boolean = false,
    val mode: DisplayCurtainMode = DisplayCurtainMode.NONE,
    val title: String = "",
    val subtitle: String = "Tap anywhere or lift phone to view controls",
    val deviceName: String? = null
)

/**
 * # DisplayAutomationManager
 *
 * Centralized automation orchestrator managing the mobile screen state across hardware
 * sensors and external display surfaces (Pocket Mode, Car HUD, Smart TV, and Smart Watch).
 *
 * ## Architectural Role & Relationships
 * - **Sensors**: Coordinates hardware Proximity (`TYPE_PROXIMITY`), Ambient Light (`TYPE_LIGHT`),
 *   Gravity/Accelerometer (`TYPE_GRAVITY` / `TYPE_ACCELEROMETER`), and Significant Motion
 *   (`TYPE_SIGNIFICANT_MOTION`) to detect device obstruction, flat resting, and pick-up gestures.
 * - **Peripherals**: Observes Android Auto head units, Google Cast / AirPlay TV receivers, and
 *   Wear OS smartwatches.
 * - **Output**: Emits authoritative [DisplayCurtainState] to drive the pure `#000000` AMOLED blackout
 *   curtain in `MainActivity`, conserving battery while delivering zero-latency Lift-to-Wake.
 *
 * ## Concurrency & Thread-Safety Model
 * - Bound to application process lifecycle with dedicated [CoroutineScope] on [Dispatchers.Default].
 * - Sensor events processed on UI/Sensor threads and synchronized via atomic Kotlin [StateFlow].
 */
class DisplayAutomationManager(
    private val application: Application,
    private val sessionStateProvider: () -> TimerSessionState,
    private val castManager: HabitBellCastManager,
    private val airPlayManager: AirPlayCastManager
) : SensorEventListener {

    /** Process-level coroutine scope isolated with a SupervisorJob. */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Hardware sensor subsystem manager handle. */
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Optical proximity sensor situated adjacent to the front-facing camera. */
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    /** Ambient light sensor measuring illuminance in lux units. */
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** Hardware gravity sensor isolating Earth's gravitational vector (9.81 m/s²). */
    private val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    /** Fallback linear/3-axis accelerometer if hardware gravity sensor is absent. */
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Low-power hardware trigger sensor for instantaneous pick-up wakeup. */
    private val significantMotionSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    // -------------------------------------------------------------------------
    // Mutable Backing States & Hardware Flags
    // -------------------------------------------------------------------------

    /** Flag tracking whether continuous sensor listeners are currently registered. */
    private var isRegistered = false

    /** Mutable backing flow indicating physical proximity obstruction (< 5 cm). */
    private val _isProximityCovered = MutableStateFlow(false)

    /** Mutable backing flow tracking ambient light level in lux (lx). */
    private val _ambientLux = MutableStateFlow(100.0f)

    /** Mutable backing flow indicating whether device is resting flat facing upwards on a table. */
    private val _isRestingFlat = MutableStateFlow(false)

    /** Mutable backing flow indicating whether user has physically lifted the device upright. */
    private val _isPickedUp = MutableStateFlow(false)

    /** Mutable backing flow tracking manual pocket mode switch override. */
    private val _isManualPocket = MutableStateFlow(false)

    /** Mutable backing flow tracking active Android Auto / CarPlay HUD connection. */
    private val _isCarConnected = MutableStateFlow(false)

    /** Mutable backing flow tracking connected vehicle brand or head unit name. */
    private val _carDeviceName = MutableStateFlow<String?>(null)

    /** Mutable backing flow tracking active Smart Watch (Wear OS) companion pairing. */
    private val _isWatchConnected = MutableStateFlow(false)

    /** Mutable backing flow tracking connected smartwatch model label. */
    private val _watchDeviceName = MutableStateFlow<String?>(null)

    /** Mutable backing flow tracking temporary manual wake dismissal by the user. */
    private val _isTemporarilyAwake = MutableStateFlow(false)

    /** Mutable backing flow tracking countdown seconds remaining before re-engaging curtain (0..10s). */
    private val _inactivityCountdown = MutableStateFlow(10)

    /** Coroutine job managing the 10-second flat-inactivity countdown timer. */
    private var countdownJob: Job? = null

    /** Previous accelerometer acceleration magnitude for jerk/motion delta calculation. */
    private var lastAccelMagnitude: Float = 9.81f

    /** Significant motion trigger callback instance for automatic re-arming. */
    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            // Significant motion hardware trigger signals immediate physical pick-up
            onDeviceMovedOrLifted()
            armSignificantMotionTrigger()
        }
    }

    // -------------------------------------------------------------------------
    // Public Immutable Reactive Exposures
    // -------------------------------------------------------------------------

    /** Read-only stream indicating whether device is resting flat on a surface facing upwards. */
    val isRestingFlat: StateFlow<Boolean> = _isRestingFlat.asStateFlow()

    /** Read-only stream indicating whether device has been lifted or tilted upright. */
    val isPickedUp: StateFlow<Boolean> = _isPickedUp.asStateFlow()

    /** Read-only stream of inactivity countdown seconds (10s down to 0). */
    val inactivityCountdown: StateFlow<Int> = _inactivityCountdown.asStateFlow()

    /** Read-only stream indicating if proximity sensor is obstructed. */
    val isProximityCovered: StateFlow<Boolean> = _isProximityCovered.asStateFlow()

    /** Authoritative combined StateFlow driving the full-screen AMOLED power curtain. */
    val curtainState: StateFlow<DisplayCurtainState> = combine(
        _isProximityCovered,
        _ambientLux,
        _isManualPocket,
        _isCarConnected,
        _isWatchConnected,
        castManager.isCasting,
        airPlayManager.isAirPlayActive,
        _isRestingFlat,
        _isTemporarilyAwake
    ) { params ->
        val proximity = params[0] as Boolean
        val lux = params[1] as Float
        val manualPocket = params[2] as Boolean
        val car = params[3] as Boolean
        val watch = params[4] as Boolean
        val casting = params[5] as Boolean
        val airPlay = params[6] as Boolean
        val flat = params[7] as Boolean
        val temporarilyAwake = params[8] as Boolean

        val session = sessionStateProvider()
        val isSessionActive = session.status == SessionStatus.RUNNING || session.status == SessionStatus.PAUSED

        // Evaluate priority contexts
        when {
            // 1. Pocket Mode (Highest Priority): Manual override switch or optical sensor obstruction (proximity + dark ambient)
            evaluatePocketMode(proximity, lux, manualPocket, temporarilyAwake) && isSessionActive -> {
                DisplayCurtainState(
                    isActive = true,
                    mode = DisplayCurtainMode.POCKET,
                    title = "Pocket Mode Active",
                    subtitle = "Silent tactile chimes • Tap anywhere to wake"
                )
            }

            // 2. Car Mode: Connected to Android Auto / Car HUD
            car && isSessionActive -> {
                val shouldShowCurtain = !temporarilyAwake
                DisplayCurtainState(
                    isActive = shouldShowCurtain,
                    mode = DisplayCurtainMode.CAR_HUD,
                    title = "🚗 Car HUD Active",
                    subtitle = "Driving Mode • Tap screen or lift phone for mobile controls",
                    deviceName = _carDeviceName.value ?: "Android Auto"
                )
            }

            // 3. Smart TV Cast Mode: Connected to Chromecast, Apple TV, or Smart TV WebCast
            (casting || airPlay) && isSessionActive -> {
                val shouldShowCurtain = !temporarilyAwake
                val tvName = castManager.castDeviceName.value ?: if (airPlay) "Apple TV" else "Smart TV"
                DisplayCurtainState(
                    isActive = shouldShowCurtain,
                    mode = DisplayCurtainMode.TV_CAST,
                    title = "📺 Casting to $tvName",
                    subtitle = "Big Screen Mode • Lift phone or tap to view controls",
                    deviceName = tvName
                )
            }

            // 4. Smart Watch Mode: Paired Wear OS companion active
            watch && isSessionActive -> {
                val shouldShowCurtain = !temporarilyAwake
                val watchName = _watchDeviceName.value ?: "Smartwatch"
                DisplayCurtainState(
                    isActive = shouldShowCurtain,
                    mode = DisplayCurtainMode.WATCH,
                    title = "⌚ Connected to $watchName",
                    subtitle = "Wrist Controls Active • Lift phone or tap to view controls",
                    deviceName = watchName
                )
            }

            // 5. Normal Active State: No curtain applied
            else -> DisplayCurtainState(isActive = false, mode = DisplayCurtainMode.NONE)
        }
    }.combine(_isPickedUp) { state, pickedUp ->
        // If the phone is physically picked up while in external display modes (TV, Car, Watch)
        // or in Pocket Mode, automatically lift the curtain for instant zero-friction interaction.
        if (pickedUp && (state.mode == DisplayCurtainMode.TV_CAST || state.mode == DisplayCurtainMode.WATCH || state.mode == DisplayCurtainMode.CAR_HUD || state.mode == DisplayCurtainMode.POCKET)) {
            state.copy(isActive = false)
        } else {
            state
        }
    }.stateIn(
        scope = scope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = DisplayCurtainState(isActive = false, mode = DisplayCurtainMode.NONE)
    )

    init {
        // Observe resting flat state to initiate 10-second countdown in external modes
        scope.launch {
            _isRestingFlat.collect { flat ->
                if (flat) {
                    _isPickedUp.value = false
                    startFlatInactivityCountdown()
                } else {
                    cancelFlatCountdown()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle & Sensor Registration
    // -------------------------------------------------------------------------

    /**
     * Starts continuous low-frequency hardware sensor monitoring.
     */
    fun startMonitoring() {
        if (!isRegistered && sensorManager != null) {
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            lightSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            val tiltSensor = gravitySensor ?: accelerometer
            tiltSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            armSignificantMotionTrigger()
            isRegistered = true
        }
    }

    /**
     * Stops hardware sensor monitoring and releases trigger listeners.
     */
    fun stopMonitoring() {
        if (isRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this)
            disarmSignificantMotionTrigger()
            isRegistered = false
            cancelFlatCountdown()
            _isProximityCovered.value = false
            _isRestingFlat.value = false
            _isPickedUp.value = false
            _isTemporarilyAwake.value = false
        }
    }

    /**
     * Arms the low-power hardware significant motion trigger.
     */
    private fun armSignificantMotionTrigger() {
        if (significantMotionSensor != null && sensorManager != null) {
            sensorManager.requestTriggerSensor(triggerListener, significantMotionSensor)
        }
    }

    /**
     * Disarms the hardware significant motion trigger.
     */
    private fun disarmSignificantMotionTrigger() {
        if (significantMotionSensor != null && sensorManager != null) {
            sensorManager.cancelTriggerSensor(triggerListener, significantMotionSensor)
        }
    }

    // -------------------------------------------------------------------------
    // User Interaction & Inactivity Handlers
    // -------------------------------------------------------------------------

    /**
     * Invoked whenever the user taps anywhere on the screen or interacts with controls.
     * Resets the 10-second flat inactivity countdown timer.
     */
    fun notifyUserTouched() {
        _isTemporarilyAwake.value = true
        if (_isRestingFlat.value) {
            startFlatInactivityCountdown()
        }
    }

    /**
     * Temporarily dismisses the AMOLED curtain on user tap to reveal the full UI.
     */
    fun dismissCurtainTemporarily() {
        _isTemporarilyAwake.value = true
        if (_isRestingFlat.value) {
            startFlatInactivityCountdown()
        }
    }

    /**
     * Initiates the 10-second countdown before re-engaging the AMOLED blackout curtain
     * when the device is resting flat on a surface.
     */
    private fun startFlatInactivityCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in 10 downTo 1) {
                _inactivityCountdown.value = sec
                delay(1000L)
            }
            _inactivityCountdown.value = 0
            // Grace period expired while resting flat: re-engage blackout curtain
            _isTemporarilyAwake.value = false
        }
    }

    /**
     * Cancels any ongoing flat inactivity countdown when the device is picked up or moved.
     */
    private fun cancelFlatCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _inactivityCountdown.value = 10
    }

    /**
     * Internal handler invoked when significant physical movement or upright tilt is detected.
     * Instantly sets picked-up state, awards a temporary wake grace period, cancels flat inactivity
     * countdowns, and clears manual pocket override to restore full mobile visibility.
     */
    private fun onDeviceMovedOrLifted() {
        _isPickedUp.value = true
        _isTemporarilyAwake.value = true
        _isManualPocket.value = false
        cancelFlatCountdown()
    }

    // -------------------------------------------------------------------------
    // External Peripheral Mutators
    // -------------------------------------------------------------------------

    /**
     * Updates the Android Auto / CarPlay HUD connection status.
     *
     * @param connected Whether vehicle head unit is connected.
     * @param deviceName Optional automotive brand or system name.
     */
    fun setCarConnected(connected: Boolean, deviceName: String? = null) {
        _isCarConnected.value = connected
        _carDeviceName.value = deviceName
    }

    /**
     * Updates the Smart Watch (Wear OS) connection status.
     *
     * @param connected Whether a companion smartwatch is active.
     * @param deviceName Optional smartwatch model name.
     */
    fun setWatchConnected(connected: Boolean, deviceName: String? = null) {
        _isWatchConnected.value = connected
        _watchDeviceName.value = deviceName
    }

    /**
     * Manually overrides the Pocket Mode blackout state.
     *
     * @param enabled True to engage manual pocket blackout; false to release.
     */
    fun setPocketModeManual(enabled: Boolean) {
        _isManualPocket.value = enabled
    }

    // -------------------------------------------------------------------------
    // SensorEventListener Implementation
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5.0f
                _isProximityCovered.value = distance < maxRange
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                _ambientLux.value = lux
            }

            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Calculate total acceleration vector magnitude: sqrt(x^2 + y^2 + z^2)
                val currentMagnitude = sqrt(x * x + y * y + z * z)
                val jerkDelta = kotlin.math.abs(currentMagnitude - lastAccelMagnitude)
                lastAccelMagnitude = currentMagnitude

                // Flatness evaluation:
                // Earth gravity (9.81 m/s²) aligned strictly with +Z axis indicates device is resting flat screen-up.
                val isFlat = evaluateFlatness(x = x, y = y, z = z)

                // Lifted/Upright evaluation:
                // Device tilted towards user: z drops below threshold and y increases, or jerkDelta exceeds threshold
                val isLifted = evaluateLift(x = x, y = y, z = z, jerkDelta = jerkDelta)

                if (isFlat != _isRestingFlat.value) {
                    _isRestingFlat.value = isFlat
                }

                if (isLifted && !_isPickedUp.value) {
                    onDeviceMovedOrLifted()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy variations do not affect threshold comparisons
    }

    companion object {
        /** Minimum Z-axis gravity acceleration (m/s²) to classify device as resting flat face-up. */
        const val GRAVITY_FLAT_Z_THRESHOLD = 8.8f

        /** Maximum absolute X or Y axis acceleration (m/s²) allowed for flat orientation. */
        const val GRAVITY_FLAT_XY_MAX = 3.0f

        /** Maximum Z-axis gravity acceleration (m/s²) below which device is considered tilted/lifted. */
        const val GRAVITY_LIFT_Z_THRESHOLD = 7.5f

        /** Minimum absolute Y-axis gravity acceleration (m/s²) indicating vertical/handheld tilt. */
        const val GRAVITY_LIFT_Y_MIN = 3.5f

        /** Acceleration jerk delta threshold (m/s²) triggering physical pickup detection. */
        const val MOTION_JERK_DELTA_THRESHOLD = 1.2f

        /** Maximum ambient illuminance (lux) to classify optical sensor obstruction as in-pocket. */
        const val POCKET_LUX_THRESHOLD = 10.0f

        /** Flat inactivity timeout duration in seconds before re-engaging AMOLED blackout curtain. */
        const val FLAT_INACTIVITY_TIMEOUT_SECONDS = 10

        /**
         * Evaluates whether 3-axis accelerometer/gravity vectors correspond to a flat face-up orientation.
         *
         * @param x Lateral acceleration along the phone's horizontal width in m/s².
         * @param y Longitudinal acceleration along the phone's vertical length in m/s².
         * @param z Normal acceleration perpendicular to the phone's screen in m/s².
         * @return True if the device is resting flat on a horizontal surface facing upward; false otherwise.
         */
        fun evaluateFlatness(x: Float, y: Float, z: Float): Boolean {
            return z >= GRAVITY_FLAT_Z_THRESHOLD &&
                    kotlin.math.abs(x) < GRAVITY_FLAT_XY_MAX &&
                    kotlin.math.abs(y) < GRAVITY_FLAT_XY_MAX
        }

        /**
         * Evaluates whether 3-axis acceleration or sudden jerk delta indicates the phone was lifted or tilted.
         *
         * @param x Lateral acceleration in m/s².
         * @param y Longitudinal acceleration in m/s².
         * @param z Normal acceleration in m/s².
         * @param jerkDelta Rate of change in total acceleration vector magnitude in m/s².
         * @return True if the device has been tilted toward the user or picked up; false otherwise.
         */
        fun evaluateLift(x: Float, y: Float, z: Float, jerkDelta: Float): Boolean {
            return (z < GRAVITY_LIFT_Z_THRESHOLD && kotlin.math.abs(y) > GRAVITY_LIFT_Y_MIN) ||
                    jerkDelta > MOTION_JERK_DELTA_THRESHOLD
        }

        /**
         * Evaluates whether optical proximity and ambient light readings satisfy Pocket Mode criteria.
         *
         * @param proximityCovered True if physical obstruction is detected within < 5 cm of the receiver.
         * @param ambientLux Ambient illuminance measured in lux (lx).
         * @param manualOverride User manual toggle switch setting.
         * @param isTemporarilyAwake True if device is in a transient awake grace period after user tap or lift.
         * @return True if Pocket Mode blackout should be engaged; false otherwise.
         */
        fun evaluatePocketMode(
            proximityCovered: Boolean,
            ambientLux: Float,
            manualOverride: Boolean,
            isTemporarilyAwake: Boolean = false
        ): Boolean {
            if (isTemporarilyAwake) return false
            return manualOverride || (proximityCovered && ambientLux < POCKET_LUX_THRESHOLD)
        }
    }
}
