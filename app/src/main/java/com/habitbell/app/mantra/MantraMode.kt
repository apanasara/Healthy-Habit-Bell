package com.habitbell.app.mantra

/**
 * # MantraMode
 *
 * Domain enumeration categorizing the distinct acoustic signal processing and temporal
 * integration modes supported by the [AcousticMantraSensorProvider] and [MantraCountManager].
 *
 * ## Architectural Role & Component Relationships
 * - Used by [com.habitbell.app.data.model.MantraCounterConfig] to determine detection DSP pipelines.
 * - Ingested by [AcousticMantraSensorProvider] to select between cumulative verse integration,
 *   rapid pulse hysteresis, or autocorrelation harmonic pitch tracking.
 * - Rendered by [com.habitbell.app.ui.components.MantraCounterContent] for modality badges and UI cues.
 *
 * ## Lifecycle & Concurrency
 * Immutable compile-time enum. Thread-safe across all coroutine dispatchers and background services.
 *
 * @property displayName User-facing title for UI cards, chips, and headers.
 * @property description Concise explanation of the acoustic and temporal mechanics.
 */
enum class MantraMode(
    val displayName: String,
    val description: String
) {
    /**
     * Extended Verse / Sloka Mode:
     * Designed for multi-line sacred recitations lasting 6 to 30 seconds per repetition
     * (e.g. Gayatri Mantra, Maha Mrityunjaya Mantra, Quranic Surahs, Bible prayers).
     *
     * Utilizes Intra-Verse Pause Bridging: natural breathing pauses (< 1.2s) between lines
     * are bridged without resetting. Exactly 1 count is registered when cumulative vocal duration
     * meets the minimum threshold followed by a genuine concluding breath pause (>= 1.5s).
     */
    EXTENDED_VERSE(
        displayName = "Extended Verse",
        description = "Multi-line slokas & verses; bridges internal line pauses to count 1 per complete verse."
    ),

    /**
     * Short Rhythmic Japa Mode:
     * Designed for rapid to medium-paced single-phrase or word repetitions lasting 0.3 to 2.5 seconds
     * (e.g. "Ram... Ram...", "Om Namah Shivaya", Tasbih phrases, Jesus Prayer).
     *
     * Utilizes a fast 3-stage hysteresis state machine with refractory lockout to prevent double-triggering.
     */
    SHORT_JAPA(
        displayName = "Short Japa",
        description = "Rhythmic single-phrase repetitions; counts on each distinct vocal burst."
    ),

    /**
     * Sustained Aumkar / Vocal Drone Mode:
     * Designed for prolonged vocal resonance and humming lasting 2.0 to 12.0 seconds
     * (e.g. Aumkar chanting, sacred humming drone).
     *
     * Utilizes normalized autocorrelation pitch tracking over the fundamental human vocal swara band (80 Hz - 250 Hz),
     * registering 1 count upon exhalation release.
     */
    AUMKAR_DRONE(
        displayName = "Aumkar Drone",
        description = "Deep sustained vocal resonance; tracks continuous pitch duration and counts upon release."
    )
}
