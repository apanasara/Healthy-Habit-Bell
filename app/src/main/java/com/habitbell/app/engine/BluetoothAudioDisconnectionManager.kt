package com.habitbell.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * # BluetoothAudioDisconnectionManager
 *
 * Engine subsystem responsible for detecting peripheral audio disconnections (Bluetooth A2DP,
 * Bluetooth SCO, BLE Audio, hearing aids, wired headphones, and automotive head units) and
 * automatically requesting session pause to replicate standard media player behavior.
 *
 * ## Architectural Role & Relationships
 * - **Engine Layer**: Subsystem managed directly by [CentralSessionHandler].
 * - **Audio Framework**: Integrates with [AudioManager] via [AudioDeviceCallback] and system
 *   broadcasts for [AudioManager.ACTION_AUDIO_BECOMING_NOISY].
 * - **Automotive Integration**: Coordinates with Android Auto vehicle disconnects via
 *   [onCarDisconnected].
 * - **Consumer Surfaces**: Triggers [onPauseRequested] callback, causing [CentralSessionHandler]
 *   to immediately pause [TimerEngine], mute/pause [BackgroundMusicManager], and update
 *   [android.support.v4.media.session.MediaSessionCompat].
 *
 * ## Lifecycle & Thread-Safety Model
 * - Lifecycle-bound: Active monitoring listeners are registered strictly during [SessionStatus.RUNNING]
 *   and unregistered when transitioning to [SessionStatus.PAUSED], [SessionStatus.COMPLETED], or [SessionStatus.IDLE].
 * - Thread Safety: Internal state mutations and debounce evaluations are guarded on the main Looper
 *   and protected by monotonic clock timestamps ([SystemClock.elapsedRealtime]).
 *
 * @param context Process-level or application context for system service and broadcast resolution.
 * @param sessionStateProvider Lambda providing real-time snapshots of active [TimerSessionState].
 * @param onPauseRequested Invoked on the main thread when a qualifying peripheral disconnect occurs.
 */
class BluetoothAudioDisconnectionManager(
    private val context: Context,
    private val sessionStateProvider: () -> TimerSessionState,
    private val onPauseRequested: () -> Unit
) {

    companion object {
        /** Diagnostic log tag for logcat filtering. */
        private const val TAG = "BluetoothAudioDisconnect"

        /** Monotonic debouncing barrier in milliseconds (1000ms = 1.0s) to prevent duplicate pause events. */
        const val DEBOUNCE_THRESHOLD_MS = 1000L

        /**
         * Evaluates whether the specified [AudioDeviceInfo] type matches a Bluetooth audio peripheral,
         * automotive sound system, hearing aid, or wired/USB headset.
         *
         * @param type Integer constant corresponding to an [AudioDeviceInfo] device type.
         * @return `true` if the device is a Bluetooth or external headset output peripheral, `false` otherwise.
         */
        fun isBluetoothOrHeadsetAudioType(type: Int): Boolean {
            // Standard audio devices supported across Android versions
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                type == AudioDeviceInfo.TYPE_USB_DEVICE
            ) {
                return true
            }

            // Hearing aids (API 28+)
            if (type == AudioDeviceInfo.TYPE_HEARING_AID) {
                return true
            }

            // Bluetooth Low Energy (BLE) Audio endpoints (API 31+ / 33+)
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            ) {
                return true
            }

            return false
        }
    }

    /** System audio manager for registering device callbacks. */
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Main thread handler for scheduling listener events. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Master user toggle controlling whether disconnects should pause the active session. Defaults to true. */
    @Volatile
    var isEnabled: Boolean = true

    /** Tracks whether hardware monitoring callbacks and broadcast receivers are actively registered. */
    private var isMonitoring: Boolean = false

    /** Monotonic timestamp in milliseconds of the most recent disconnection-triggered pause. */
    private var lastDisconnectionTimestampMs: Long = 0L

    /**
     * Dedicated [BroadcastReceiver] listening for the canonical [AudioManager.ACTION_AUDIO_BECOMING_NOISY] intent.
     * Fired by Android framework when audio output reroutes from external peripheral to internal speakers.
     */
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Received ACTION_AUDIO_BECOMING_NOISY broadcast from Android OS")
                handleDisconnectionEvent("ACTION_AUDIO_BECOMING_NOISY")
            }
        }
    }

    /**
     * Hardware audio routing callback detecting additions and removals of audio endpoint devices.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices.isNullOrEmpty()) return

            var peripheralRemoved = false
            var peripheralLabel = "Audio Device"

            for (device in removedDevices) {
                if (isBluetoothOrHeadsetAudioType(device.type)) {
                    peripheralRemoved = true
                    peripheralLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        "${device.productName} (Type ${device.type})"
                    } else {
                        "Peripheral (Type ${device.type})"
                    }
                    break
                }
            }

            if (peripheralRemoved) {
                Log.d(TAG, "AudioDeviceCallback detected removal of: $peripheralLabel")
                handleDisconnectionEvent("AudioDeviceCallback: $peripheralLabel")
            }
        }
    }

    /**
     * Activates audio routing and broadcast monitoring if not already active.
     * Should be called when an active timer transitions to [SessionStatus.RUNNING].
     */
    @Synchronized
    fun startMonitoring() {
        if (isMonitoring) return

        try {
            // 1. Register modern AudioDeviceCallback for unconditional device removal notifications
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

            // 2. Register system receiver for ACTION_AUDIO_BECOMING_NOISY
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            ContextCompat.registerReceiver(
                context,
                noisyAudioReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            isMonitoring = true
            Log.d(TAG, "Started monitoring Bluetooth and audio peripheral disconnections")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register audio disconnection listeners: ${e.message}", e)
        }
    }

    /**
     * Deactivates audio routing and broadcast monitoring to conserve power and avoid memory leaks.
     * Should be called when a timer transitions to [SessionStatus.PAUSED], [SessionStatus.COMPLETED],
     * or [SessionStatus.IDLE].
     */
    @Synchronized
    fun stopMonitoring() {
        if (!isMonitoring) return

        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering AudioDeviceCallback: ${e.message}")
        }

        try {
            context.unregisterReceiver(noisyAudioReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering noisyAudioReceiver: ${e.message}")
        }

        isMonitoring = false
        Log.d(TAG, "Stopped monitoring Bluetooth and audio peripheral disconnections")
    }

    /**
     * Processes an identified peripheral disconnection event, evaluates user preference and debounce guard,
     * and triggers session pause if the timer is actively running.
     *
     * @param source Descriptive source string identifying the trigger mechanism (e.g. "AudioDeviceCallback").
     */
    fun handleDisconnectionEvent(source: String) {
        if (!isEnabled) {
            Log.d(TAG, "Ignoring disconnection from $source: feature is disabled by user.")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val elapsedSinceLastMs = now - lastDisconnectionTimestampMs
        if (elapsedSinceLastMs in 0 until DEBOUNCE_THRESHOLD_MS) {
            Log.d(TAG, "Throttling duplicate disconnection event from $source (${elapsedSinceLastMs}ms < ${DEBOUNCE_THRESHOLD_MS}ms)")
            return
        }
        lastDisconnectionTimestampMs = now

        val currentState = sessionStateProvider()
        if (currentState.status == SessionStatus.RUNNING) {
            Log.i(TAG, "Bluetooth/audio peripheral disconnected via $source. Pausing running timer session.")
            mainHandler.post {
                // Re-verify on main looper before dispatching pause
                if (sessionStateProvider().status == SessionStatus.RUNNING) {
                    onPauseRequested()
                }
            }
        } else {
            Log.d(TAG, "Disconnection from $source ignored: session is not running (${currentState.status})")
        }
    }

    /**
     * Explicit notification that an Android Auto vehicle connection has terminated.
     * If the session is actively running, pauses the session to avoid runaway background timers.
     */
    fun onCarDisconnected() {
        Log.d(TAG, "Explicit vehicle disconnect notified from Android Auto host")
        handleDisconnectionEvent("Android Auto Host Disconnect")
    }

    /**
     * Tears down all active hardware and broadcast handles.
     */
    fun destroy() {
        stopMonitoring()
    }
}
