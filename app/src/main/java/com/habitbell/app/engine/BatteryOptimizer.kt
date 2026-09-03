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
 * BatteryOptimizer manages hardware-level battery efficiency for mindful sessions:
 * 1. Proximity Sensor detection for Hardware Pocket Mode (turning off display rendering).
 * 2. Auto-dimming screen brightness during restful inactivity.
 * 3. Controlled CPU WakeLock without excessive battery drain.
 */
class BatteryOptimizer(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var isSensorRegistered = false

    private val _isPocketCovered = MutableStateFlow(false)
    val isPocketCovered: StateFlow<Boolean> = _isPocketCovered.asStateFlow()

    private val _isScreenDimmed = MutableStateFlow(false)
    val isScreenDimmed: StateFlow<Boolean> = _isScreenDimmed.asStateFlow()

    fun startProximityMonitoring() {
        if (!isSensorRegistered && proximitySensor != null) {
            sensorManager?.registerListener(
                this,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL // Low battery consumption rate
            )
            isSensorRegistered = true
        }
    }

    fun stopProximityMonitoring() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
            _isPocketCovered.value = false
        }
    }

    fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HabitBell::SessionActiveWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(3 * 3600 * 1000L) // Max 3 hours failsafe
        acquireWifiLock()
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        releaseWifiLock()
    }

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

    fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun applyScreenAwake(activity: Activity, keepAwake: Boolean) {
        if (keepAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun setScreenBrightness(activity: Activity, dim: Boolean) {
        val layout = activity.window.attributes
        layout.screenBrightness = if (dim) 0.05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = layout
        _isScreenDimmed.value = dim
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            // Distance < maxRange indicates proximity (in pocket or face down)
            _isPocketCovered.value = distance < maxRange
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
