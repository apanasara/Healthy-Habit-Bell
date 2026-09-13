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

    /**
     * Derives an updated [BreathCounterConfig] for the targeted [newTechnique],
     * applying canonical default parameters while preserving user hardware preferences
     * (input mode, sensitivity, audio/haptic toggles).
     *
     * @param newTechnique Desired breathwork modality.
     * @return New immutable [BreathCounterConfig] initialized for [newTechnique].
     */
    fun withTechnique(newTechnique: BreathTechnique): BreathCounterConfig {
        val canonical = forTechnique(newTechnique)
        return canonical.copy(
            defaultInputMode = this.defaultInputMode,
            isSoundFeedbackEnabled = if (newTechnique == BreathTechnique.BHRAMARI) false else this.isSoundFeedbackEnabled,
            isHapticFeedbackEnabled = this.isHapticFeedbackEnabled,
            isVoiceGuidanceEnabled = this.isVoiceGuidanceEnabled,
            micSensitivity = this.micSensitivity
        )
    }

    companion object {
        /**
         * Resolves canonical default configuration for a given [technique].
         *
         * @param technique The breathwork modality to initialize.
         * @return Canonical [BreathCounterConfig] pre-calibrated for that technique.
         */
        fun forTechnique(technique: BreathTechnique): BreathCounterConfig {
            return when (technique) {
                BreathTechnique.KAPALABHATI -> DEFAULT_KAPALABHATI
                BreathTechnique.BHASTRIKA -> DEFAULT_BHASTRIKA
                BreathTechnique.BHRAMARI -> DEFAULT_BHRAMARI
                BreathTechnique.FREE_COUNT -> DEFAULT_FREE_COUNT
            }
        }

        /** Default Kapalabhati configuration: 3 rounds (30, 60, 90 strokes), 20s Kumbhaka, 15s rest. */
        val DEFAULT_KAPALABHATI = BreathCounterConfig(
            technique = BreathTechnique.KAPALABHATI,
            targetRounds = 3,
            strokesPerRound = listOf(30, 60, 90),
            retentionSeconds = 20,
            restSeconds = 15,
            defaultInputMode = BreathInputSourceType.ACOUSTIC_MIC,
            isSoundFeedbackEnabled = true,
            isHapticFeedbackEnabled = true,
            isVoiceGuidanceEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Bhastrika configuration: 3 rounds of 21 bellows cycles, 25s Kumbhaka, 20s rest. */
        val DEFAULT_BHASTRIKA = BreathCounterConfig(
            technique = BreathTechnique.BHASTRIKA,
            targetRounds = 3,
            strokesPerRound = listOf(21, 21, 21),
            retentionSeconds = 25,
            restSeconds = 20,
            defaultInputMode = BreathInputSourceType.ACOUSTIC_MIC,
            isSoundFeedbackEnabled = true,
            isHapticFeedbackEnabled = true,
            isVoiceGuidanceEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Bhramari configuration: 7 rounds of sustained humming resonance, 0s retention, 5s rest. */
        val DEFAULT_BHRAMARI = BreathCounterConfig(
            technique = BreathTechnique.BHRAMARI,
            targetRounds = 7,
            strokesPerRound = listOf(1, 1, 1, 1, 1, 1, 1),
            retentionSeconds = 0,
            restSeconds = 5,
            defaultInputMode = BreathInputSourceType.ACOUSTIC_MIC,
            isSoundFeedbackEnabled = false,
            isHapticFeedbackEnabled = true,
            isVoiceGuidanceEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Free Count configuration: 1 round of 108 open-ended strokes. */
        val DEFAULT_FREE_COUNT = BreathCounterConfig(
            technique = BreathTechnique.FREE_COUNT,
            targetRounds = 1,
            strokesPerRound = listOf(108),
            retentionSeconds = 0,
            restSeconds = 0,
            defaultInputMode = BreathInputSourceType.MANUAL_TAP,
            isSoundFeedbackEnabled = true,
            isHapticFeedbackEnabled = true,
            isVoiceGuidanceEnabled = false,
            micSensitivity = 1.0f
        )
    }
}
