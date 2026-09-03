package com.habitbell.app.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactile feedback manager providing sensory vibration cues.
 *
 * Designed specifically for Pocket Mode and eyes-free usage during meditation,
 * mindful eating, and breathwork:
 * - Dual-Pulse vibration for intermediate interval chimes.
 * - Triple-Pulse crescendo vibration for session completion.
 * - Subtle single pulse for Pranayama breath phase transitions.
 *
 * Note: Under the project's soundscape guidelines, haptic vibration is strictly activated
 * when the device is inside a pocket or placed face down to maintain undisturbed tranquility.
 *
 * @param context Android context for acquiring the system vibrator service.
 */
class HapticManager(private val context: Context) {

    /**
     * System Vibrator reference, resolved via [VibratorManager] on Android 12+ (API 31)
     * or legacy [Context.VIBRATOR_SERVICE] on earlier Android versions.
     */
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Triggers a firm dual-pulse vibration pattern to signal an interval boundary.
     *
     * Timing Pattern:
     * - Delay: 0ms
     * - Pulse 1: 250ms at maximum amplitude (255)
     * - Rest: 150ms
     * - Pulse 2: 300ms at maximum amplitude (255)
     *
     * Easily perceptible through trouser fabric or exercise attire.
     */
    fun triggerIntervalHaptic() {
        if (vibrator?.hasVibrator() != true) return
        val timings = longArrayOf(0, 250, 150, 300)
        val amplitudes = intArrayOf(0, 255, 0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    /**
     * Triggers an unmistakable triple-pulse vibration pattern signaling session completion.
     *
     * Timing Pattern:
     * - Delay: 0ms
     * - Pulse 1: 350ms (full amplitude)
     * - Rest: 150ms
     * - Pulse 2: 350ms (full amplitude)
     * - Rest: 150ms
     * - Pulse 3: 600ms (sustained finale)
     */
    fun triggerCompletionHaptic() {
        if (vibrator?.hasVibrator() != true) return
        val timings = longArrayOf(0, 350, 150, 350, 150, 600)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    /**
     * Triggers a short, gentle tactile cue (120ms) indicating a transition between
     * Pranayama breathwork phases (Inhale, Hold, Exhale, Hold) or Yoga sequence poses.
     */
    fun triggerBreathPhaseHaptic() {
        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(120, 220)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    /**
     * Immediately halts any active or queued hardware vibration sequences.
     */
    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
