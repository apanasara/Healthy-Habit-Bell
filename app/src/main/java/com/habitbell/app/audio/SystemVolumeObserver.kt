package com.habitbell.app.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * # SystemVolumeObserver
 *
 * Lifecycle-aware coordinator managing observation and direct modification of the Android
 * system media volume ([AudioManager.STREAM_MUSIC]).
 *
 * ## Architectural Role & Component Relationships
 * Resolves requirement **E7** (Direct Ambient Volume Control):
 * - Binds the application's ambient volume slider directly to Android's physical media stream.
 * - Bridges hardware volume button presses, Bluetooth headset volume keys, and system slider
 *   modifications into a unified reactive Kotlin [StateFlow] stream consumed by [com.habitbell.app.ui.viewmodel.HabitBellViewModel].
 * - Eliminates redundant dual-volume confusion where system volume and app volume were separate.
 *
 * ## Thread-Safety & Concurrency
 * - Dispatches volume observation callbacks across the Android Main [Looper] thread.
 * - Thread-safe state emission backed by [MutableStateFlow].
 * - Prevents recursive feedback loops during active UI slider scrubbing via internal guard flags.
 *
 * @param context Process-level or application context.
 */
class SystemVolumeObserver(private val context: Context) {

    companion object {
        private const val TAG = "SystemVolumeObserver"

        /** Flag specifying zero UI popups during programmatically dispatched slider updates. */
        private const val FLAG_NO_SYSTEM_UI = 0
    }

    /** System audio manager instance accessing hardware audio streams. */
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Main thread handler for content observer dispatch. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Backing mutable flow holding the normalized system media volume (0.0f..1.0f). */
    private val _volume = MutableStateFlow(readCurrentNormalizedVolume())

    /** Public immutable stream emitting normalized system media volume (0.0f..1.0f). */
    val volume: StateFlow<Float> = _volume.asStateFlow()

    /** Guard flag preventing locally dispatched slider adjustments from triggering self-echo loops. */
    @Volatile
    private var isDispatchingLocally = false

    /** Content observer listening to system volume modifications across the entire OS. */
    private val contentObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (isDispatchingLocally) {
                return
            }
            val currentVol = readCurrentNormalizedVolume()
            if (kotlin.math.abs(_volume.value - currentVol) > 0.001f) {
                Log.d(TAG, "Hardware/System media volume updated to: $currentVol")
                _volume.value = currentVol
            }
        }
    }

    /** Tracks whether the content observer is currently active and registered with ContentResolver. */
    private var isRegistered = false

    /**
     * Indicates whether the [ContentObserver] is currently actively registered with [Settings.System.CONTENT_URI].
     *
     * @return True if actively observing system volume changes; false if dormant/unregistered.
     */
    val isObserving: Boolean get() = isRegistered

    /**
     * Registers the [ContentObserver] with Android's [Settings.System.CONTENT_URI].
     *
     * Lifecycle: Safe to call repeatedly; idempotent. Invoked lazily when the user opens
     * volume configuration screens to conserve battery and eliminate background OS dispatch overhead.
     */
    fun register() {
        if (!isRegistered) {
            try {
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    contentObserver
                )
                isRegistered = true
                _volume.value = readCurrentNormalizedVolume()
                Log.i(TAG, "Registered SystemVolumeObserver on Settings.System.CONTENT_URI (initial=${_volume.value})")
            } catch (e: Exception) {
                Log.w(TAG, "Could not register SystemVolumeObserver: ${e.message}")
            }
        }
    }

    /**
     * Unregisters the [ContentObserver] to avoid memory leaks upon component teardown.
     *
     * Lifecycle: Called during process termination or ViewModel clearing.
     */
    fun unregister() {
        if (isRegistered) {
            try {
                context.contentResolver.unregisterContentObserver(contentObserver)
                isRegistered = false
                Log.i(TAG, "Unregistered SystemVolumeObserver")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering SystemVolumeObserver: ${e.message}")
            }
        }
    }

    /**
     * Reads current hardware media stream volume and computes a normalized floating-point ratio.
     *
     * @return Normalized volume gain in the closed interval `[0.0f, 1.0f]`.
     */
    fun readCurrentNormalizedVolume(): Float {
        val am = audioManager ?: return 0.5f
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol <= 0) return 0f

        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }

        val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val range = (maxVol - minVol).coerceAtLeast(1)
        val normalized = ((currentVol - minVol).toFloat() / range).coerceIn(0f, 1f)
        return normalized
    }

    /**
     * Sets the physical system media volume ([AudioManager.STREAM_MUSIC]) directly from
     * a normalized floating-point gain level.
     *
     * @param normalized Target volume gain in the closed interval `[0.0f, 1.0f]`.
     */
    @android.annotation.SuppressLint("WrongConstant")
    fun setNormalizedVolume(normalized: Float) {
        val am = audioManager ?: return
        val clamped = normalized.coerceIn(0f, 1f)

        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol <= 0) return

        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }

        val range = (maxVol - minVol).coerceAtLeast(1)
        val targetIndex = (minVol + (clamped * range)).roundToInt().coerceIn(minVol, maxVol)

        isDispatchingLocally = true
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, FLAG_NO_SYSTEM_UI)
            _volume.value = clamped
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting system media volume to index $targetIndex", e)
        } finally {
            mainHandler.postDelayed({
                isDispatchingLocally = false
            }, 100L)
        }
    }
}
