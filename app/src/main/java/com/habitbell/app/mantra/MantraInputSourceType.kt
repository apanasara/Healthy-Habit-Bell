package com.habitbell.app.mantra

/**
 * # MantraInputSourceType
 *
 * Domain enumeration defining the input modality for registering mantra recitation events.
 *
 * ## Architectural Role & Component Relationships
 * - Used by [com.habitbell.app.data.model.MantraCounterConfig] to determine primary tracking source.
 * - Ingested by [MantraCountManager] to dynamically route telemetry from [AcousticMantraSensorProvider],
 *   [ManualTapMantraProvider], or [SimulatedMantraProvider].
 * - Rendered by [com.habitbell.app.ui.components.MantraCounterContent] for input switching chips.
 *
 * ## Lifecycle & Concurrency
 * Immutable compile-time enum. Thread-safe across all coroutine dispatchers and background services.
 *
 * @property displayName User-facing title for UI cards and selection chips.
 * @property iconSymbol Material icon or emoji identifier representing the modality.
 */
enum class MantraInputSourceType(
    val displayName: String,
    val iconSymbol: String
) {
    /**
     * Hands-free acoustic microphone DSP sensing.
     * Uses real-time vocal formant filtering, intra-verse pause bridging, and pitch tracking.
     */
    ACOUSTIC_MIC(
        displayName = "Acoustic Mic",
        iconSymbol = "🎙️"
    ),

    /**
     * Manual screen tap or physical bead advance mode.
     * Engineered for silent meditation halls, libraries, mosques, churches, or quiet whisper recitation.
     */
    MANUAL_BEAD_TAP(
        displayName = "Tap Bead",
        iconSymbol = "📿"
    ),

    /**
     * Deterministic synthetic recitation provider used for unit testing, CI pipelines, and Compose previews.
     */
    SIMULATED(
        displayName = "Simulated",
        iconSymbol = "⚡"
    )
}
