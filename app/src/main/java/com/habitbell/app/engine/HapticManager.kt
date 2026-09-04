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
     * Triggers 3 heavy tactile vibration pulses to signal an interval boundary in Pocket Mode.
     *
     * Engineered specifically for mindful eating and public meditation so the user is clearly
     * cued to take their next bite through trousers or pockets without emitting any audible sound.
     *
     * Timing Pattern:
     * - Delay: 0ms
     * - Pulse 1: 350ms at maximum amplitude (255)
     * - Rest: 150ms
     * - Pulse 2: 350ms at maximum amplitude (255)
     * - Rest: 150ms
     * - Pulse 3: 350ms at maximum amplitude (255)
     */
    fun triggerIntervalHaptic() {
        if (vibrator?.hasVibrator() != true) return
        val timings = longArrayOf(0, 350, 150, 350, 150, 350)
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
     * Triggers an unmistakable sustained vibration pattern signaling session completion in Pocket Mode.
     *
     * Timing Pattern:
     * - Delay: 0ms
     * - Pulse 1: 400ms (full amplitude 255)
     * - Rest: 150ms
     * - Pulse 2: 400ms (full amplitude 255)
     * - Rest: 150ms
     * - Pulse 3: 800ms (sustained finale)
     */
    fun triggerCompletionHaptic() {
        if (vibrator?.hasVibrator() != true) return
        val timings = longArrayOf(0, 400, 150, 400, 150, 800)
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
