package com.habitbell.app.breath

/**
 * # BreathInputEvent
 *
 * Primitive event emitted by underlying sensor or touch data sources to [BreathCountManager].
 *
 * ## Architectural Role & Relationships
 * - Dispatched by [AcousticBreathSensorProvider], [ManualTapBreathProvider], or [SimulatedBreathProvider].
 * - Ingested by [BreathCountManager] to evaluate stroke progression, cadence, and round state.
 *
 * ## Lifecycle & Concurrency
 * Immutable value entity. Thread-safe across all dispatchers.
 *
 * @property strokeDelta Number of strokes to increment (typically 1 on stroke detection, or 0 for continuous amplitude telemetry).
 * @property instantaneousCadenceBpm Measured or estimated cadence in strokes per minute (BPM).
 * @property audioAmplitudeRms Real-time normalized acoustic energy (`0.0f` to `1.0f`) for visual audio ripples.
 * @property isHummingActive Whether sustained vocal resonance (Bhramari) is actively detected.
 * @property activeHumDurationSeconds Unbroken duration of active humming in seconds.
 * @property thresholdRms Normalized acoustic trigger threshold (`0.0f` to `1.0f`) evaluated by DSP.
 * @property timestampMillis Epoch timestamp in milliseconds.
 */
data class BreathInputEvent(
    val strokeDelta: Int = 0,
    val instantaneousCadenceBpm: Int = 0,
    val audioAmplitudeRms: Float = 0f,
    val isHummingActive: Boolean = false,
    val activeHumDurationSeconds: Float = 0f,
    val thresholdRms: Float = 0.04f,
    val timestampMillis: Long = System.currentTimeMillis()
)
