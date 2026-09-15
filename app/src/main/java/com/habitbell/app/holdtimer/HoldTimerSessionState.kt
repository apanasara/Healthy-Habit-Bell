package com.habitbell.app.holdtimer

/**
 * # HoldTimerPhase
 *
 * Operational phase states of the Voice-Driven Yoga / Physiotherapy Hold Timer FSM.
 */
enum class HoldTimerPhase {
    /** Initial preparation buffer allowing practitioner to assume posture before count commences. */
    PREPARATION,

    /** Active isometric hold phase where seconds are counted upwards. */
    HOLD,

    /** Inter-round recovery rest period where countdown is announced. */
    REST,

    /** Timer paused either via voice command or interactive touch gesture. */
    PAUSED,

    /** Session successfully finished across all requested rounds. */
    COMPLETED
}

/**
 * # HoldTimerSessionState
 *
 * Immutable reactive telemetry state emitted by [HoldTimerEngine] and consumed by presentation UI.
 *
 * ## Architectural Role & Component Relationships
 * - Emitted via Kotlin [kotlinx.coroutines.flow.StateFlow] from `HoldTimerEngine`.
 * - Rendered by `com.habitbell.app.ui.screens.HoldTimerScreen` in Jetpack Compose.
 * - Bridges live hands-free speech recognition status, clinician safety alerts, and countdown timing.
 *
 * ## Lifecycle & Concurrency
 * Immutable value object. Thread-safe across all coroutine dispatchers and background services.
 *
 * @property phase Current operational phase ([HoldTimerPhase.PREPARATION], [HoldTimerPhase.HOLD], etc.).
 * @property currentRound 1-based index of the active round (1..[totalRounds]).
 * @property totalRounds Total target rounds scheduled for this session.
 * @property currentSecond Elapsed seconds within the current phase (0..[totalSecondsInPhase]).
 * @property totalSecondsInPhase Total duration allocated for the active phase in seconds.
 * @property ttsSpeed Active speech rate multiplier applied to audio announcements (e.g. 1.0f = normal).
 * @property isPaused Whether the countdown loop is currently suspended.
 * @property isListening Whether the offline voice command listener is actively capturing microphone input.
 * @property lastRecognizedCommand The most recent raw utterance or parsed command recognized by speech recognizer.
 * @property safetyWarning Clinician safety alert text if hold duration exceeds maximum limit, null otherwise.
 * @property feedbackMessage System response or status message displayed to the user (e.g. "Pace slowed to 0.85x").
 */
data class HoldTimerSessionState(
    val phase: HoldTimerPhase = HoldTimerPhase.PREPARATION,
    val currentRound: Int = 1,
    val totalRounds: Int = 4,
    val currentSecond: Int = 0,
    val totalSecondsInPhase: Int = 30,
    val ttsSpeed: Float = 1.0f,
    val isPaused: Boolean = false,
    val isListening: Boolean = false,
    val lastRecognizedCommand: String? = null,
    val safetyWarning: String? = null,
    val feedbackMessage: String? = null
) {
    /**
     * Normalized progress within the active phase (0.0f..1.0f).
     */
    val progressFraction: Float
        get() = if (totalSecondsInPhase > 0) {
            (currentSecond.toFloat() / totalSecondsInPhase.toFloat()).coerceIn(0.0f, 1.0f)
        } else {
            0.0f
        }

    /**
     * Seconds remaining until the active phase transitions to the next phase or round.
     */
    val remainingSecondsInPhase: Int
        get() = (totalSecondsInPhase - currentSecond).coerceAtLeast(0)

    /**
     * Overall round completion progress fraction across the entire session (0.0f..1.0f).
     */
    val sessionProgressFraction: Float
        get() = if (totalRounds > 0) {
            ((currentRound - 1).toFloat() + progressFraction) / totalRounds.toFloat()
        } else {
            0.0f
        }
}
