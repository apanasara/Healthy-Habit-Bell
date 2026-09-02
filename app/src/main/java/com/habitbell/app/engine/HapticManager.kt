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
     * 1 Vibration - Interval Bell Cue (matching PRD: "1 Vibration Interval")
     */
    fun triggerIntervalHaptic() {
        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    /**
     * 3 Vibrations - Session Completed (matching PRD: "3 Vibrations Session Completed")
     */
    fun triggerCompletionHaptic() {
        if (vibrator?.hasVibrator() != true) return
        val timings = longArrayOf(0, 250, 150, 250, 150, 500)
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
     * Subtle breath cue pulse for Pranayama inhale/hold/exhale phase transitions
     */
    fun triggerBreathPhaseHaptic() {
        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(45, (VibrationEffect.DEFAULT_AMPLITUDE * 0.7).toInt().coerceIn(1, 255))
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(45)
        }
    }
}
