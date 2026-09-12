/**
 * # SuryaPoseAssets
 *
 * Central visual and auditory asset registry for the 12 classical Surya Namaskar postures.
 *
 * ## Architectural Role & Component Relationships
 * Presentation & Subsystem Asset Catalog in `com.habitbell.app.ui`:
 * - Bridges posture indexes (1..12) from [com.habitbell.app.data.model.CompoundPose] to their
 *   dedicated monochrome vector silhouettes in `res/drawable`.
 * - Resolves high-definition studio-mastered audio assets in `res/raw` matching the project's
 *   authoritative Lata-style SwaraNeural profile.
 * - Used by [com.habitbell.app.ui.SuryaTimerScreen], [com.habitbell.app.ui.components.CompoundPoseCard],
 *   and [com.habitbell.app.audio.SuryaVoicePlayer].
 *
 * ## Concurrency & Thread Safety
 * Immutable, stateless singleton object safe for concurrent reads across Main, IO, and Audio dispatcher threads.
 */
package com.habitbell.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import com.habitbell.app.R
import com.habitbell.app.audio.VoiceCueMode

object SuryaPoseAssets {

    /**
     * Resolves the dedicated monochrome vector silhouette drawable for a given 1-based posture step index.
     *
     * The 12 classical Surya Namaskar steps follow an authentic cyclical progression:
     * 1 & 12: Pranamasana (Prayer Pose)
     * 2 & 11: Hastauttanasana (Raised Arms Pose / Backbend)
     * 3 & 10: Padahastasana (Standing Forward Bend)
     * 4 & 9:  Ashwa Sanchalanasana (Equestrian Pose / Low Lunge)
     * 5:      Dandasana (Plank Pose)
     * 6:      Ashtanga Namaskara (Salute with 8 Limbs)
     * 7:      Bhujangasana (Cobra Pose)
     * 8:      Parvatasana (Mountain / Downward-Facing Dog)
     *
     * @param stepIndex 1-based index (1 to 12) of the Surya Namaskar posture.
     * @return Drawable resource ID representing the posture's vector silhouette.
     */
    @DrawableRes
    fun getDrawableForStep(stepIndex: Int): Int {
        return when (stepIndex) {
            1, 12 -> R.drawable.yoga_pranamasana
            2, 11 -> R.drawable.yoga_hastauttanasana
            3, 10 -> R.drawable.yoga_padahastasana
            4, 9 -> R.drawable.yoga_ashwa_sanchalanasana
            5 -> R.drawable.yoga_dandasana
            6 -> R.drawable.yoga_ashtanga_namaskara
            7 -> R.drawable.yoga_bhujangasana
            8 -> R.drawable.yoga_parvatasana
            else -> R.drawable.yoga_pranamasana
        }
    }

    /**
     * Resolves the studio-mastered audio clip for the given step index, voice mode, and step duration.
     *
     * In accordance with the project's Step Timing vs. Voice Timing Law:
     * If the step duration is very short (< 4s, e.g. Fast preset), bilingual cues automatically
     * fall back to the concise Sanskrit solar mantra so the spoken audio is never cut off mid-word.
     *
     * @param stepIndex 1-based index (1 to 12) of the active Surya Namaskar posture.
     * @param mode Selected [VoiceCueMode] governing guidance style.
     * @param stepDurationSeconds Allocated duration for this posture in seconds.
     * @return Raw resource ID from [R.raw], or null if silent / unsupported.
     */
    @RawRes
    fun getAudioResource(
        stepIndex: Int,
        mode: VoiceCueMode,
        stepDurationSeconds: Int? = null
    ): Int? {
        if (mode == VoiceCueMode.NONE) return null

        val effectiveMode = if (mode != VoiceCueMode.SLOKA && stepDurationSeconds != null && stepDurationSeconds < 4) {
            VoiceCueMode.SLOKA
        } else {
            mode
        }

        return when (effectiveMode) {
            VoiceCueMode.SLOKA -> when (stepIndex) {
                1 -> R.raw.surya_01_pranamasana_sanskrit
                2 -> R.raw.surya_02_hastauttanasana_sanskrit
                3 -> R.raw.surya_03_padahastasana_sanskrit
                4 -> R.raw.surya_04_ashwasanchalanasana_sanskrit
                5 -> R.raw.surya_05_dandasana_sanskrit
                6 -> R.raw.surya_06_ashtanganamaskara_sanskrit
                7 -> R.raw.surya_07_bhujangasana_sanskrit
                8 -> R.raw.surya_08_parvatasana_sanskrit
                9 -> R.raw.surya_09_ashwasanchalanasana_sanskrit
                10 -> R.raw.surya_10_padahastasana_sanskrit
                11 -> R.raw.surya_11_hastauttanasana_sanskrit
                12 -> R.raw.surya_12_pranamasana_sanskrit
                else -> null
            }
            VoiceCueMode.STEP_NAME, VoiceCueMode.PRANIC -> when (stepIndex) {
                1 -> R.raw.surya_01_pranamasana_bilingual
                2 -> R.raw.surya_02_hastauttanasana_bilingual
                3 -> R.raw.surya_03_padahastasana_bilingual
                4 -> R.raw.surya_04_ashwasanchalanasana_bilingual
                5 -> R.raw.surya_05_dandasana_bilingual
                6 -> R.raw.surya_06_ashtanganamaskara_bilingual
                7 -> R.raw.surya_07_bhujangasana_bilingual
                8 -> R.raw.surya_08_parvatasana_bilingual
                9 -> R.raw.surya_09_ashwasanchalanasana_bilingual
                10 -> R.raw.surya_10_padahastasana_bilingual
                11 -> R.raw.surya_11_hastauttanasana_bilingual
                12 -> R.raw.surya_12_pranamasana_bilingual
                else -> null
            }
            VoiceCueMode.NONE -> null
        }
    }
}
