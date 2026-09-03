package com.habitbell.app.engine

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages device power conservation, hardware sensor listening, and CPU wake locks.
 *
 * Key Responsibilities:
 * 1. **Proximity Monitoring**: Tracks whether the device is in a pocket or placed face-down
 *    to trigger AMOLED black overlay and pocket haptics.
 * 2. **Partial CPU WakeLock**: Prevents Android Doze mode from killing the 1Hz timer coroutine
 *    during prolonged meditative sessions.
 * 3. **Low-Latency Wi-Fi Lock**: Keeps Wi-Fi transceiver active during active Local TV WebCast streaming.
 * 4. **Display Brightness & Awake Control**: Modulates window flags to conserve OLED battery power.
 *
 * @param context Application or Activity context.
 */
class BatteryOptimizer(private val context: Context) : SensorEventListener {

    /** System sensor manager used for registering hardware proximity listener. */
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Hardware proximity sensor instance, nullable if the device lacks the physical sensor. */
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    /** System PowerManager for acquiring and releasing CPU wake locks. */
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /** System WifiManager for maintaining local network casting connections. */
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager

    /** High-performance Wi-Fi lock preventing network sleep during TV casting. */
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    /** Partial wake lock ensuring the CPU stays alive while screen is off or app is backgrounded. */
    private var wakeLock: PowerManager.WakeLock? = null

    /** Guard flag tracking active sensor registration to prevent duplicate listener overhead. */
    private var isSensorRegistered = false

    /** Mutable backing flow indicating if the device is covered (in pocket or face down). */
    private val _isPocketCovered = MutableStateFlow(false)

    /** Read-only state flow emitting whether the device is covered/in pocket. */
    val isPocketCovered: StateFlow<Boolean> = _isPocketCovered.asStateFlow()

    /** Mutable backing flow indicating if screen brightness has been dimmed. */
    private val _isScreenDimmed = MutableStateFlow(false)

    /** Read-only state flow emitting active screen dimming state. */
    val isScreenDimmed: StateFlow<Boolean> = _isScreenDimmed.asStateFlow()

    /**
     * Registers the hardware proximity sensor listener at [SensorManager.SENSOR_DELAY_NORMAL]
     * to ensure minimal battery draw.
     */
    fun startProximityMonitoring() {
        if (!isSensorRegistered && proximitySensor != null) {
            sensorManager?.registerListener(
                this,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            isSensorRegistered = true
        }
    }

    /**
     * Unregisters the proximity sensor listener and resets pocket state to `false`.
     */
    fun stopProximityMonitoring() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
            _isPocketCovered.value = false
        }
    }

    /**
     * Acquires a [PowerManager.PARTIAL_WAKE_LOCK] for up to 3 hours to guarantee unbroken
     * timer countdowns even if the system enters Doze mode.
     */
    fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HabitBell::SessionActiveWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(3 * 3600 * 1000L) // 3-hour automatic timeout failsafe
        acquireWifiLock()
    }

    /**
     * Releases the active partial wake lock and associated Wi-Fi lock.
     */
    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        releaseWifiLock()
    }

    /**
     * Acquires a Wi-Fi lock to ensure unbroken HTTP SSE streaming to local TV dashboards.
     */
    fun acquireWifiLock() {
        if (wifiLock == null) {
            wifiLock = wifiManager?.createWifiLock(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
                },
                "HabitBell::CastWifiLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            wifiLock?.acquire()
        } catch (_: Exception) {}
    }

    /**
     * Releases the Wi-Fi multicast/streaming lock.
     */
    fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
    }

    /**
     * Configures the window flag to keep the screen awake during active visual practice.
     *
     * @param activity Hosting Activity.
     * @param keepAwake If true, adds [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]; otherwise clears it.
     */
    fun applyScreenAwake(activity: Activity, keepAwake: Boolean) {
        if (keepAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Adjusts the window screen brightness level.
     *
     * @param activity Hosting Activity.
     * @param dim If true, reduces brightness to minimal 5% (0.05f); if false, restores system default.
     */
    fun setScreenBrightness(activity: Activity, dim: Boolean) {
        val layout = activity.window.attributes
        layout.screenBrightness = if (dim) 0.05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = layout
        _isScreenDimmed.value = dim
    }

    /**
     * Hardware sensor callback invoked when proximity distance changes.
     *
     * @param event The [SensorEvent] containing updated sensor reading array.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            // Distance strictly less than maximum range indicates device obstruction/pocket insertion
            _isPocketCovered.value = distance < maxRange
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sensor accuracy transitions do not impact proximity threshold calculation
    }
}
