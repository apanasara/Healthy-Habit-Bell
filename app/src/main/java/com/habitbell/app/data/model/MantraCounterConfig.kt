package com.habitbell.app.data.model

import com.habitbell.app.mantra.MantraInputSourceType
import com.habitbell.app.mantra.MantraTechnique

/**
 * # MantraCounterConfig
 *
 * Configuration aggregate governing sacred recitation parameters, target beads,
 * Mala rounds, feedback styles, and input providers for [TimerProfile] instances.
 *
 * ## Architectural Role & Component Relationships
 * - Embedded within [TimerProfile] when mantra/japa counting is enabled (`mantraConfig != null`).
 * - Ingested by [com.habitbell.app.mantra.MantraCountManager] upon session initialization.
 * - Parameterizes [com.habitbell.app.mantra.AcousticMantraSensorProvider] DSP filter bands and timers.
 *
 * ## Lifecycle & Concurrency
 * Immutable data configuration. Thread-safe across all coroutine dispatchers and background services.
 *
 * @property technique The sacred chanting modality ([MantraTechnique.GAYATRI_MANTRA], [MantraTechnique.AUMKAR], etc.).
 * @property targetBeads Total beads per Mala round (default 108; 100 for Tasbih, 33 for Jesus Prayer).
 * @property targetMalas Total Mala rounds to complete the session (default 1).
 * @property minVerseDurationSec Minimum cumulative vocal duration required in seconds before a verse can complete.
 * @property interVersePauseThresholdSec Concluding silence required to confirm completion in seconds.
 * @property defaultInputMode Primary input provider ([MantraInputSourceType.ACOUSTIC_MIC] or [MantraInputSourceType.MANUAL_BEAD_TAP]).
 * @property isBeadHapticEnabled Whether a tactile micro-pulse vibrates on every registered bead.
 * @property isMilestoneChimeEnabled Whether harmonic bells sound at half-Mala (54) and completion (108).
 * @property micSensitivity Multiplier adjusting microphone detection threshold (0.5f to 2.5f, default 1.0f).
 */
data class MantraCounterConfig(
    val technique: MantraTechnique = MantraTechnique.GAYATRI_MANTRA,
    val targetBeads: Int = 108,
    val targetMalas: Int = 1,
    val minVerseDurationSec: Float = 6.5f,
    val interVersePauseThresholdSec: Float = 1.6f,
    val defaultInputMode: MantraInputSourceType = MantraInputSourceType.ACOUSTIC_MIC,
    val isBeadHapticEnabled: Boolean = true,
    val isMilestoneChimeEnabled: Boolean = true,
    val micSensitivity: Float = 1.0f
) {
    /**
     * Total target chants across all configured Mala rounds.
     */
    val totalTargetChants: Int
        get() = targetBeads * targetMalas

    /**
     * Derives an updated [MantraCounterConfig] for the targeted [newTechnique],
     * applying canonical default parameters while preserving user hardware preferences
     * (input mode, sensitivity, audio/haptic toggles).
     *
     * @param newTechnique Desired sacred recitation modality.
     * @return New immutable [MantraCounterConfig] initialized for [newTechnique].
     */
    fun withTechnique(newTechnique: MantraTechnique): MantraCounterConfig {
        val canonical = forTechnique(newTechnique)
        return canonical.copy(
            defaultInputMode = this.defaultInputMode,
            isBeadHapticEnabled = this.isBeadHapticEnabled,
            isMilestoneChimeEnabled = this.isMilestoneChimeEnabled,
            micSensitivity = this.micSensitivity
        )
    }

    companion object {
        /**
         * Resolves canonical default configuration for a given [technique].
         *
         * @param technique The sacred recitation modality to initialize.
         * @return Canonical [MantraCounterConfig] pre-calibrated for that technique.
         */
        fun forTechnique(technique: MantraTechnique): MantraCounterConfig {
            return when (technique) {
                MantraTechnique.GAYATRI_MANTRA -> DEFAULT_GAYATRI
                MantraTechnique.MAHA_MRITYUNJAYA -> DEFAULT_MAHA_MRITYUNJAYA
                MantraTechnique.AUMKAR -> DEFAULT_AUMKAR
                MantraTechnique.RAM_JAPA -> DEFAULT_RAM_JAPA
                MantraTechnique.TASBIH_DHIKR -> DEFAULT_TASBIH
                MantraTechnique.JESUS_PRAYER -> DEFAULT_JESUS_PRAYER
                MantraTechnique.UNIVERSAL_VERSE -> DEFAULT_UNIVERSAL
            }
        }

        /** Default Gayatri Mantra configuration: 108 beads, 4.0s minimum verse duration, 1.2s pause. */
        val DEFAULT_GAYATRI = MantraCounterConfig(
            technique = MantraTechnique.GAYATRI_MANTRA,
            targetBeads = 108,
            targetMalas = 1,
            minVerseDurationSec = 4.0f,
            interVersePauseThresholdSec = 1.2f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Maha Mrityunjaya configuration: 108 beads, 4.0s minimum verse duration, 1.2s pause. */
        val DEFAULT_MAHA_MRITYUNJAYA = MantraCounterConfig(
            technique = MantraTechnique.MAHA_MRITYUNJAYA,
            targetBeads = 108,
            targetMalas = 1,
            minVerseDurationSec = 4.0f,
            interVersePauseThresholdSec = 1.2f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Aumkar configuration: 21 counts, 2.2s minimum drone duration, 1.2s pause. */
        val DEFAULT_AUMKAR = MantraCounterConfig(
            technique = MantraTechnique.AUMKAR,
            targetBeads = 21,
            targetMalas = 1,
            minVerseDurationSec = 2.2f,
            interVersePauseThresholdSec = 1.2f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Ram Naam Japa configuration: 108 beads, 0.35s minimum duration, 0.45s pause. */
        val DEFAULT_RAM_JAPA = MantraCounterConfig(
            technique = MantraTechnique.RAM_JAPA,
            targetBeads = 108,
            targetMalas = 1,
            minVerseDurationSec = 0.35f,
            interVersePauseThresholdSec = 0.45f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Islamic Tasbih configuration: 100 beads (33/33/34), 0.40s duration, 0.50s pause. */
        val DEFAULT_TASBIH = MantraCounterConfig(
            technique = MantraTechnique.TASBIH_DHIKR,
            targetBeads = 100,
            targetMalas = 1,
            minVerseDurationSec = 0.40f,
            interVersePauseThresholdSec = 0.50f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Jesus Prayer configuration: 33 knots, 0.80s duration, 0.60s pause. */
        val DEFAULT_JESUS_PRAYER = MantraCounterConfig(
            technique = MantraTechnique.JESUS_PRAYER,
            targetBeads = 33,
            targetMalas = 1,
            minVerseDurationSec = 0.80f,
            interVersePauseThresholdSec = 0.60f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )

        /** Default Universal Scripture configuration: 108 beads, 3.5s duration, 1.2s pause. */
        val DEFAULT_UNIVERSAL = MantraCounterConfig(
            technique = MantraTechnique.UNIVERSAL_VERSE,
            targetBeads = 108,
            targetMalas = 1,
            minVerseDurationSec = 3.5f,
            interVersePauseThresholdSec = 1.2f,
            defaultInputMode = MantraInputSourceType.ACOUSTIC_MIC,
            isBeadHapticEnabled = true,
            isMilestoneChimeEnabled = true,
            micSensitivity = 1.0f
        )
    }
}
