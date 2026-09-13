package com.habitbell.app.breath

/**
 * # BreathTechnique
 *
 * Domain enumeration categorizing distinct breathwork modalities supported by the
 * [BreathCountManager] and acoustic sensor subsystem.
 *
 * ## Architectural Role & Relationships
 * - Used by [com.habitbell.app.data.model.BreathCounterConfig] to determine detection DSP algorithms.
 * - Ingested by [AcousticBreathSensorProvider] to parameterize digital bandpass filters and peak logic.
 * - Rendered by [com.habitbell.app.ui.components.BreathCounterContent] for technique badges and visual themes.
 *
 * ## Lifecycle & Concurrency
 * Immutable compile-time enum. Thread-safe across all coroutine dispatchers.
 *
 * @property displayName User-facing title for UI cards and headers.
 * @property sanskritName Classical Sanskrit title in Romanized script.
 * @property sanskritScript Classical Devanagari script representation.
 * @property description Concise pedagogical description of the breath mechanics.
 */
enum class BreathTechnique(
    val displayName: String,
    val sanskritName: String,
    val sanskritScript: String,
    val description: String
) {
    /**
     * Kapalabhati (कपालभाति - Skull Shining Kriya):
     * Characterized by active, forceful abdominal exhalations and passive inhalations.
     * Generates a sharp high-frequency acoustic nasal burst (~1.5 kHz - 4.5 kHz).
     */
    KAPALABHATI(
        displayName = "Kapalabhati",
        sanskritName = "Kapālabhāti",
        sanskritScript = "कपालभाति",
        description = "Active sharp exhalation with passive inhalation; cleanses frontal sinuses."
    ),

    /**
     * Bhastrika (भस्त्रिका - Bellows Breath):
     * Characterized by active forceful inhalations AND active forceful exhalations.
     * Generates alternating two-phase turbulent air rushing sounds (~800 Hz - 3.5 kHz).
     */
    BHASTRIKA(
        displayName = "Bhastrika",
        sanskritName = "Bhastrikā",
        sanskritScript = "भस्त्रिका",
        description = "Rapid active inhale and active exhale like a bellows; generates internal heat."
    ),

    /**
     * Bhramari (भ्रामरी - Humming Bee Breath):
     * Characterized by deep inhalation followed by a smooth, sustained nasal humming resonance.
     * Generates a periodic acoustic drone with distinct fundamental pitch (F0: ~80 Hz - 250 Hz).
     */
    BHRAMARI(
        displayName = "Bhramari",
        sanskritName = "Bhrāmarī",
        sanskritScript = "भ्रामरी",
        description = "Smooth nasal humming drone on exhalation; calms the nervous system."
    ),

    /**
     * Free Count / Universal Breath Counter:
     * Unbiased breath counting without rigid frequency filtering, suitable for
     * manual tap pacing or open-ended mindful respiration tracking.
     */
    FREE_COUNT(
        displayName = "Free Breath Counter",
        sanskritName = "Japa Prāṇāyāma",
        sanskritScript = "जप प्राणायाम",
        description = "Open-ended stroke counter with custom targets and milestone interval bells."
    )
}
