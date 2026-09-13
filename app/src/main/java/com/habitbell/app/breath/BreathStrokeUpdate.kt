package com.habitbell.app.breath

/**
 * # BreathStrokeUpdate
 *
 * Immutable snapshot representing real-time breath metrics, stroke counts, cadence,
 * and phase progression emitted by the active [BreathDataSource].
 *
 * ## Architectural Role & Relationships
 * - Directly analogous to `StepUpdate` in the pedometer subsystem.
 * - Emitted via reactive Kotlin [kotlinx.coroutines.flow.StateFlow] by [BreathCountManager].
 * - Ingested by [com.habitbell.app.engine.TimerEngine] to evaluate milestone interval bells and round progression.
 * - Rendered by [com.habitbell.app.ui.components.BreathCounterContent] for live numerical and visual feedback.
 *
 * ## Lifecycle & Concurrency
 * Immutable value entity. Safe to share across all threads and coroutine scopes.
 *
 * @property currentRoundStrokes Number of breath strokes completed in the active round.
 * @property targetRoundStrokes Configured target strokes required to complete the active round (e.g. 30, 60, 90).
 * @property totalSessionStrokes Cumulative breath strokes recorded across all rounds since session start.
 * @property currentRound Active 1-based round index (e.g. Round 1 of 3).
 * @property targetRounds Total number of rounds configured for this session (e.g. 3).
 * @property cadenceBpm Instantaneous estimated cadence in strokes per minute (BPM).
 * @property currentPhase Active operational phase ([BreathCounterPhase.STROKES], [BreathCounterPhase.RETENTION_HOLD], etc.).
 * @property phaseRemainingSeconds Remaining countdown seconds if in a timed phase (retention hold or rest).
 * @property phaseDurationSeconds Total allocated seconds for the active timed phase.
 * @property humDurationSeconds Length of active unbroken humming vibration in seconds (used for [BreathTechnique.BHRAMARI]).
 * @property audioAmplitudeRms Normalized acoustic energy envelope level (`0.0f` to `1.0f`) used for visual waveform ripples.
 * @property thresholdRms Dynamic acoustic trigger threshold level (`0.0f` to `1.0f`) used for visual gauge display.
 * @property isCalibrating Whether the acoustic provider is actively profiling environmental background noise (AC, wind, fan).
 * @property micSensitivity Active microphone sensitivity multiplier (0.5f to 2.5f).
 * @property timestampMillis Epoch timestamp in milliseconds when this metric update was generated.
 */
data class BreathStrokeUpdate(
    val currentRoundStrokes: Int = 0,
    val targetRoundStrokes: Int = 30,
    val totalSessionStrokes: Int = 0,
    val currentRound: Int = 1,
    val targetRounds: Int = 3,
    val cadenceBpm: Int = 0,
    val currentPhase: BreathCounterPhase = BreathCounterPhase.STROKES,
    val phaseRemainingSeconds: Int = 0,
    val phaseDurationSeconds: Int = 0,
    val humDurationSeconds: Float = 0f,
    val audioAmplitudeRms: Float = 0f,
    val thresholdRms: Float = 0.04f,
    val micSensitivity: Float = 1.0f,
    val isCalibrating: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    /**
     * Normalized completion progress for the active round ranging from `0.0f` to `1.0f`.
     */
    val roundProgressFraction: Float
        get() {
            return when (currentPhase) {
                BreathCounterPhase.STROKES -> {
                    if (targetRoundStrokes > 0) {
                        (currentRoundStrokes.toFloat() / targetRoundStrokes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                }
                BreathCounterPhase.RETENTION_HOLD, BreathCounterPhase.REST -> {
                    if (phaseDurationSeconds > 0) {
                        ((phaseDurationSeconds - phaseRemainingSeconds).toFloat() / phaseDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                }
                BreathCounterPhase.COMPLETED -> 1f
                BreathCounterPhase.PREPARATION -> {
                    if (phaseDurationSeconds > 0) {
                        ((phaseDurationSeconds - phaseRemainingSeconds).toFloat() / phaseDurationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                }
            }
        }

    /**
     * Whether the active round's stroke goal has been achieved.
     */
    val isRoundGoalReached: Boolean
        get() = currentRoundStrokes >= targetRoundStrokes

    /**
     * Human-readable stroke count display (e.g. "24 / 60 strokes" or "24 strokes").
     */
    val formattedStrokeDisplay: String
        get() = if (targetRoundStrokes > 0) {
            "$currentRoundStrokes / $targetRoundStrokes"
        } else {
            "$currentRoundStrokes"
        }

    /**
     * Human-readable cadence display (e.g. "72 BPM" or "0 BPM").
     */
    val formattedCadenceDisplay: String
        get() = if (cadenceBpm > 0) "$cadenceBpm BPM" else "— BPM"

    /**
     * Human-readable round indicator (e.g. "Round 2 of 3").
     */
    val formattedRoundDisplay: String
        get() = "Round $currentRound of $targetRounds"

    /**
     * Human-readable Bhramari humming duration (e.g. "12.4s").
     */
    val formattedHumDuration: String
        get() = "%.1fs".format(humDurationSeconds)
}
