package com.habitbell.app.breath

/**
 * # BreathCounterPhase
 *
 * Operational lifecycle phases within a multi-round breath counting session.
 *
 * ## Architectural Role & Relationships
 * - Governs state machine transitions in [BreathCountManager].
 * - Ingested by [com.habitbell.app.ui.components.BreathCounterContent] to switch between
 *   the rapid acoustic stroke pulsar, the blooming lotus Kumbhaka retention hold, and the resting aura.
 * - Synchronized with [com.habitbell.app.engine.TimerEngine] for milestone chimes and audio guidance.
 *
 * ## Lifecycle & Concurrency
 * Immutable compile-time enum. Thread-safe across all coroutine dispatchers.
 *
 * @property displayName User-facing title for UI headers and status badges.
 * @property guidanceCue Actionable mindfulness cue displayed on screen.
 * @property sanskritScript Classical Devanagari script representation.
 */
enum class BreathCounterPhase(
    val displayName: String,
    val guidanceCue: String,
    val sanskritScript: String
) {
    /**
     * Settle into meditation posture and calibrate ambient room acoustics (AC, wind, fan).
     */
    PREPARATION(
        displayName = "Calibrate & Settle",
        guidanceCue = "Remain silent. Calibrating room ambience...",
        sanskritScript = "प्रारम्भिक स्थिति / समंजन"
    ),

    /**
     * Active rapid stroke counting stage (pumping exhalations or bellows breathing).
     */
    STROKES(
        displayName = "Pumping Strokes",
        guidanceCue = "Maintain steady rhythmic breath strokes.",
        sanskritScript = "कपालभाति / भस्त्रिका"
    ),

    /**
     * Deep inhalation and internal breath retention (*Antar Kumbhaka*) with Bandhas.
     */
    RETENTION_HOLD(
        displayName = "Breath Retention",
        guidanceCue = "Deep inhale and hold with Jalandhara & Mula Bandha.",
        sanskritScript = "अभ्यन्तर कुम्भक"
    ),

    /**
     * Gentle exhalation and passive resting stillness before the next round begins.
     */
    REST(
        displayName = "Stillness & Rest",
        guidanceCue = "Release breath and observe the stillness within.",
        sanskritScript = "शान्त स्थिति"
    ),

    /**
     * All configured rounds successfully completed.
     */
    COMPLETED(
        displayName = "Complete",
        guidanceCue = "Sadhana complete. Rest in meditative awareness.",
        sanskritScript = "पूर्णम्"
    )
}
