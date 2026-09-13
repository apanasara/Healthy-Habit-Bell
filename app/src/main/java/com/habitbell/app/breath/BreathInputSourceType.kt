package com.habitbell.app.breath

/**
 * # BreathInputSourceType
 *
 * Classification of input mechanisms delivering breath stroke triggers to [BreathCountManager].
 *
 * ## Architectural Role & Relationships
 * - Analogous to `HealthProviderType` in the health pedometer subsystem.
 * - Allows seamless runtime switching between acoustic hands-free microphone listening,
 *   touch-screen manual tapping, and deterministic simulated playback.
 * - Persisted in user preferences and exposed to UI controls.
 *
 * ## Lifecycle & Concurrency
 * Immutable enum. Thread-safe across all coroutine dispatchers.
 *
 * @property displayName User-facing title for UI selector chips.
 * @property iconSymbol Material icon symbol identifier.
 * @property description Brief summary of operational mode.
 */
enum class BreathInputSourceType(
    val displayName: String,
    val iconSymbol: String,
    val description: String
) {
    /**
     * Real-time acoustic Digital Signal Processing (DSP) listening via device microphone.
     */
    ACOUSTIC_MIC(
        displayName = "Microphone Sensor",
        iconSymbol = "mic",
        description = "Hands-free acoustic detection of nasal exhales, bellows, or humming."
    ),

    /**
     * Interactive screen tap counter (Japa Mala / manual counting mode).
     */
    MANUAL_TAP(
        displayName = "Manual Tap",
        iconSymbol = "touch_app",
        description = "Tap anywhere on the pulse ring to count strokes in noisy or silent halls."
    ),

    /**
     * Synthetic deterministic breath generator used for previews, CI, and test suites.
     */
    SIMULATED(
        displayName = "Simulated",
        iconSymbol = "smart_toy",
        description = "Automated pacing generator for testing and UI demonstration."
    )
}
