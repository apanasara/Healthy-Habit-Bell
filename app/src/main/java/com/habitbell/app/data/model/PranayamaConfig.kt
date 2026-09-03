package com.habitbell.app.data.model

/**
 * The four classical phases of yogic breath control (Pranayama).
 *
 * @property displayName Short human-readable title for UI chips and indicators.
 * @property cue Instruction prompt displayed or spoken during phase transitions.
 */
enum class PranayamaPhase(val displayName: String, val cue: String) {
    /** Puraka: Controlled inhalation expanding lungs from abdomen to chest. */
    INHALE("Inhale", "Inhale deeply"),

    /** Antar Kumbhaka: Internal breath retention with relaxed diaphragm. */
    HOLD_IN("Hold", "Hold breath"),

    /** Rechaka: Slow, continuous exhalation emptying the lungs. */
    EXHALE("Exhale", "Exhale slowly"),

    /** Bahya Kumbhaka: External breath retention resting in emptiness. */
    HOLD_OUT("Rest", "Rest empty")
}

/**
 * Represents a single timed phase within a Pranayama breathwork cycle.
 *
 * @property phase The breathwork phase ([PranayamaPhase.INHALE], [PranayamaPhase.HOLD_IN], etc.).
 * @property durationSeconds Duration allocated for this breath phase in seconds.
 */
data class PranayamaStep(
    val phase: PranayamaPhase,
    val durationSeconds: Int
)

/**
 * Configuration aggregate for multi-interval breathwork routines.
 *
 * @property steps Ordered list of breath phases comprising one full breathing cycle.
 * @property targetRounds Number of cycles/repetitions to complete the full session.
 */
data class PranayamaConfig(
    val steps: List<PranayamaStep>,
    val targetRounds: Int
)
