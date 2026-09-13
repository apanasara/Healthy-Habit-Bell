package com.habitbell.app.mantra

/**
 * # MantraInputEvent
 *
 * Primitive telemetry packet emitted by [MantraDataSource] implementations ([AcousticMantraSensorProvider],
 * [ManualTapMantraProvider], [SimulatedMantraProvider]) into [MantraCountManager].
 *
 * ## Architectural Role & Component Relationships
 * - Dispatched from background audio DSP loops or touch input handlers.
 * - Ingested by [MantraCountManager] to increment beads, track cadence, evaluate ambient calibration, and advance Mala rounds.
 * - Forwards live vocal RMS audio amplitude and threshold metrics for real-time UI biofeedback.
 *
 * ## Lifecycle & Concurrency
 * Immutable value carrier data class. Thread-safe across all coroutine dispatchers.
 *
 * @property beadDelta Number of completed recitations in this sample window (typically 0 or 1).
 * @property instantaneousCadenceCpm Rolling cadence estimation in Chants Per Minute (CPM).
 * @property audioAmplitudeRms Normalized full-spectrum acoustic energy (0.0f to 1.0f) for visual ripples.
 * @property thresholdRms Normalized detection threshold level (0.0f to 1.0f) for the live needle gauge.
 * @property isSpeechActive True when human vocal energy is actively detected in the current window.
 * @property activeVerseDurationSeconds Cumulative vocal duration of the ongoing verse or Aumkar drone in seconds.
 * @property isCalibrating Whether the acoustic sensor is actively measuring stationary ambient room noise (AC, fan, wind).
 * @property calibrationProgress Fractional progress of room ambient calibration normalized from `0.0f` to `1.0f`.
 * @property timestampMillis Monotonic system timestamp in milliseconds when this event was recorded.
 */
data class MantraInputEvent(
    val beadDelta: Int = 0,
    val instantaneousCadenceCpm: Int = 0,
    val audioAmplitudeRms: Float = 0f,
    val thresholdRms: Float = 0.05f,
    val isSpeechActive: Boolean = false,
    val activeVerseDurationSeconds: Float = 0f,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis()
)
