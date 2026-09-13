package com.habitbell.app.data.model

import com.habitbell.app.breath.BreathInputSourceType
import com.habitbell.app.breath.BreathTechnique

/**
 * # BreathCounterConfig
 *
 * Configuration aggregate governing stroke counting parameters, retention stages,
 * feedback styles, and input providers for [TimerProfile] instances utilizing the
 * breath counter subsystem.
 *
 * ## Architectural Role & Relationships
 * - Embedded within [TimerProfile] when breath stroke counting is enabled.
 * - Ingested by [com.habitbell.app.breath.BreathCountManager] upon session initialization.
 * - Parameterizes [com.habitbell.app.breath.AcousticBreathSensorProvider] frequency filters.
 *
 * ## Lifecycle & Concurrency
 * Immutable data configuration. Thread-safe across all coroutine dispatchers.
 *
 * @property technique The physiological breathwork modality ([BreathTechnique.KAPALABHATI], [BreathTechnique.BHASTRIKA], etc.).
 * @property targetRounds Total number of rounds to complete the session (default 3).
 * @property strokesPerRound List of target stroke counts for each sequential round (e.g. `[30, 60, 90]`).
 * @property retentionSeconds Internal breath retention (*Antar Kumbhaka*) hold duration in seconds between rounds.
 * @property restSeconds Recovery resting duration in seconds after breath release before the next round begins.
 * @property defaultInputMode Primary input provider ([BreathInputSourceType.ACOUSTIC_MIC] or [BreathInputSourceType.MANUAL_TAP]).
 * @property isSoundFeedbackEnabled Whether an acoustic click/tick plays through SoundPool on every registered stroke.
 * @property isHapticFeedbackEnabled Whether a subtle micro-haptic pulse vibrates on every registered stroke.
 * @property isVoiceGuidanceEnabled Whether vocal transition prompts are articulated at round boundaries.
 * @property micSensitivity Multiplier adjusting microphone detection threshold sensitivity (0.5f to 2.0f, default 1.0f).
 */
data class BreathCounterConfig(
    val technique: BreathTechnique = BreathTechnique.KAPALABHATI,
    val targetRounds: Int = 3,
    val strokesPerRound: List<Int> = listOf(30, 60, 90),
    val retentionSeconds: Int = 20,
    val restSeconds: Int = 15,
    val defaultInputMode: BreathInputSourceType = BreathInputSourceType.ACOUSTIC_MIC,
    val isSoundFeedbackEnabled: Boolean = true,
    val isHapticFeedbackEnabled: Boolean = true,
    val isVoiceGuidanceEnabled: Boolean = true,
    val micSensitivity: Float = 1.0f
) {
    /**
     * Resolves the target stroke count for a specific 1-based [roundIndex].
     *
     * @param roundIndex 1-based round index (e.g. 1, 2, 3).
     * @return Target stroke count for that round, falling back to the last element if exceeded.
     */
    fun strokesForRound(roundIndex: Int): Int {
        if (strokesPerRound.isEmpty()) return 30
        val zeroIndex = (roundIndex - 1).coerceAtLeast(0)
        return if (zeroIndex < strokesPerRound.size) {
            strokesPerRound[zeroIndex]
        } else {
            strokesPerRound.last()
        }
    }

    /**
     * Total sum of all target strokes across all configured rounds.
     */
    val totalTargetStrokes: Int
        get() {
            var sum = 0
            for (r in 1..targetRounds) {
                sum += strokesForRound(r)
            }
            return sum
        }
}
