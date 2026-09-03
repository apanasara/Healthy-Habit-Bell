package com.habitbell.app.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Firm Dual-Pulse Vibration for Interval Cue (Tactile, punchy, easily felt through pocket fabric)
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
     * 3 Vibrations - Session Completed (Strong, noticeable triple pulse)
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
     * Breath cue pulse for Pranayama inhale/hold/exhale phase transitions
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
     * Immediately cancels all ongoing and queued vibrations
     */
    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
