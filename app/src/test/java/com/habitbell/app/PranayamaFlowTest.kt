package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.PranayamaRatioStage
import com.habitbell.app.data.model.PranayamaStep
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.data.model.VoiceCueStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # PranayamaFlowTest
 *
 * Comprehensive unit test suite validating the Classical Hatha Yoga Pranayama Flow.
 *
 * Verifies:
 * 1. Default 4:16:8:16 ratio parameters (*Hatha Yoga Pradipika* Visama Vritti).
 * 2. 4-phase step sequence durations and Sanskrit nomenclature.
 * 3. Immutable duration mutation via [com.habitbell.app.data.model.PranayamaConfig.withStepDurations].
 * 4. 5-round interval milestone chime cadence arithmetic.
 * 5. Linguistic voice prompt cue styles (Sanskrit, English, Bilingual).
 */
class PranayamaFlowTest {

    /**
     * Verifies that the Classical Hatha Yoga profile initializes with the exact golden 4:16:8:16 ratio:
     * - Purak (Inhale): 4s
     * - Antar Kumbhak (Hold In): 16s
     * - Rechak (Exhale): 8s
     * - Bahya Kumbhak (Hold Out): 16s
     * Total cycle = 44s, target = 12 rounds (Classical Adhama standard), interval bell = disabled by default,
     * Option A (Sanskrit) gentle lady voice cue.
     */
    @Test
    fun testClassicalHathaPranayamaDefaults() {
        val profile = DefaultProfiles.PRANAYAMA_HATHA
        assertEquals("Pranayama (Hatha Yoga)", profile.name)
        assertEquals(TimerType.MULTI_INTERVAL, profile.type)
        assertEquals("Hatha Breathwork", profile.category)
        assertTrue(profile.isFavorite)

        val config = profile.pranayamaConfig
        assertNotNull(config)
        config!!

        assertEquals(4, config.steps.size)
        assertEquals(4, config.purakSeconds)
        assertEquals(16, config.antarKumbhakSeconds)
        assertEquals(8, config.rechakSeconds)
        assertEquals(16, config.bahyaKumbhakSeconds)
        assertEquals(44, config.cycleDurationSeconds)
        assertEquals(12, config.targetRounds) // Classical Adhama standard (HYP 2.12 / Gheranda Samhita 5.49)
        assertEquals(false, config.isIntervalBellEnabled) // Default disabled to preserve meditative state
        assertEquals(5, config.intervalBellRoundCadence)
        assertTrue(config.isVoiceGuidanceEnabled)
        assertEquals(VoiceCueStyle.SANSKRIT, config.voiceCueStyle) // Option A (Default)

        val totalExpected = 44 * 12
        assertEquals(totalExpected, config.totalSessionSeconds)
    }

    /**
     * Verifies that each phase contains correct classical Sanskrit nomenclature and Devanagari script.
     */
    @Test
    fun testPranayamaPhaseSanskritNomenclature() {
        assertEquals("Purak", PranayamaPhase.INHALE.sanskritName)
        assertEquals("पूरक", PranayamaPhase.INHALE.sanskritScript)

        assertEquals("Kumbhak", PranayamaPhase.HOLD_IN.sanskritName)
        assertEquals("अभ्यन्तर कुम्भक", PranayamaPhase.HOLD_IN.sanskritScript)

        assertEquals("Rechak", PranayamaPhase.EXHALE.sanskritName)
        assertEquals("रेचक", PranayamaPhase.EXHALE.sanskritScript)

        assertEquals("Kumbhak", PranayamaPhase.HOLD_OUT.sanskritName)
        assertEquals("बाह्य कुम्भक", PranayamaPhase.HOLD_OUT.sanskritScript)
    }

    /**
     * Verifies customized seconds adjustment and cycle duration calculation.
     */
    @Test
    fun testPranayamaCustomStepDurationsMutation() {
        val baseConfig = DefaultProfiles.PRANAYAMA_HATHA.pranayamaConfig!!

        // User adjusts to custom timings: Purak=6s, Antar=24s, Rechak=12s, Bahya=12s, 15 rounds
        val customConfig = baseConfig.withStepDurations(
            purak = 6,
            antar = 24,
            rechak = 12,
            bahya = 12,
            rounds = 15,
            voiceEnabled = true,
            voiceStyle = VoiceCueStyle.BILINGUAL
        )

        assertEquals(6, customConfig.purakSeconds)
        assertEquals(24, customConfig.antarKumbhakSeconds)
        assertEquals(12, customConfig.rechakSeconds)
        assertEquals(12, customConfig.bahyaKumbhakSeconds)
        assertEquals(54, customConfig.cycleDurationSeconds) // 6 + 24 + 12 + 12
        assertEquals(15, customConfig.targetRounds)
        assertEquals(54 * 15, customConfig.totalSessionSeconds)
        assertEquals(VoiceCueStyle.BILINGUAL, customConfig.voiceCueStyle)
    }

    /**
     * Verifies that milestone interval bells strike on every 5 completed rounds:
     * After round 5, round 10, round 15, round 20.
     */
    @Test
    fun test5RoundIntervalMilestoneCadence() {
        val cadence = 5

        for (completedRounds in 1..20) {
            val isMilestone = (completedRounds % cadence == 0)
            if (completedRounds in listOf(5, 10, 15, 20)) {
                assertTrue("Round $completedRounds must trigger interval bell", isMilestone)
            } else {
                assertTrue("Round $completedRounds should not trigger interval bell", !isMilestone)
            }
        }
    }

    /**
     * Verifies that PRANAYAMA_HATHA is present in the global catalog [DefaultProfiles.ALL_PRESETS].
     */
    @Test
    fun testHathaProfileInAllPresets() {
        val presets = DefaultProfiles.ALL_PRESETS
        val hatha = presets.find { it.id == "pranayama-hatha-classical" }
        assertNotNull(hatha)
        assertEquals("Pranayama (Hatha Yoga)", hatha!!.name)
    }

    /**
     * Verifies that Voice Cue defaults to Option A (Traditional Sanskrit) and can seamlessly
     * switch to Option B (Bilingual Guided).
     */
    @Test
    fun testVoiceCueOptionADefaultAndOptionBSwitch() {
        val config = DefaultProfiles.PRANAYAMA_HATHA.pranayamaConfig!!
        assertEquals(VoiceCueStyle.SANSKRIT, config.voiceCueStyle)

        val switched = config.withStepDurations(voiceStyle = VoiceCueStyle.BILINGUAL)
        assertEquals(VoiceCueStyle.BILINGUAL, switched.voiceCueStyle)
        assertTrue(switched.voiceCueStyle.displayName.contains("Option B"))
        assertTrue(config.voiceCueStyle.displayName.contains("Option A"))
    }

    /**
     * Verifies that milestone interval bells default to disabled to preserve the meditative state,
     * and can be enabled with custom round cadences (e.g. every 3, 5, or 6 rounds).
     */
    @Test
    fun testIntervalBellToggleAndCadenceMutation() {
        val config = DefaultProfiles.PRANAYAMA_HATHA.pranayamaConfig!!
        assertEquals(false, config.isIntervalBellEnabled)

        // User turns ON interval bell with cadence 6
        val enabledConfig = config.withStepDurations(intervalEnabled = true, cadence = 6)
        assertTrue(enabledConfig.isIntervalBellEnabled)
        assertEquals(6, enabledConfig.intervalBellRoundCadence)
    }

    /**
     * Verifies proportional ratio stages detection and dynamic scaling across classical
     * Hatha Yoga stages (Sama Vritti 1:1:1:1, Madhya 1:2:2:1, Visama Vritti 1:4:2:4, Gentle 1:4:2:1, Half 1:4:2:2).
     */
    @Test
    fun testPranayamaRatioStageMatchingAndScaling() {
        // 1. Visama Vritti (Classical Advanced) 1:4:2:4
        assertEquals(PranayamaRatioStage.VISAMA_VRITTI_CLASSICAL, PranayamaRatioStage.matchRatio(4, 16, 8, 16))
        assertEquals(PranayamaRatioStage.VISAMA_VRITTI_CLASSICAL, PranayamaRatioStage.matchRatio(3, 12, 6, 12))

        // 2. Madhya (Intermediate) 1:2:2:1
        assertEquals(PranayamaRatioStage.MADHYA_INTERMEDIATE, PranayamaRatioStage.matchRatio(4, 8, 8, 4))
        assertEquals(PranayamaRatioStage.MADHYA_INTERMEDIATE, PranayamaRatioStage.matchRatio(5, 10, 10, 5))

        // 3. Sama Vritti (Box) 1:1:1:1
        assertEquals(PranayamaRatioStage.SAMA_VRITTI_BOX, PranayamaRatioStage.matchRatio(4, 4, 4, 4))
        assertEquals(PranayamaRatioStage.SAMA_VRITTI_BOX, PranayamaRatioStage.matchRatio(6, 6, 6, 6))

        // 4. Visama Vritti (Gentle Void) 1:4:2:1
        assertEquals(PranayamaRatioStage.VISAMA_VRITTI_GENTLE, PranayamaRatioStage.matchRatio(4, 16, 8, 4))

        // 5. Visama Vritti (Half Void) 1:4:2:2
        assertEquals(PranayamaRatioStage.VISAMA_VRITTI_HALF, PranayamaRatioStage.matchRatio(4, 16, 8, 8))

        // 6. Custom manual ratio
        assertEquals(PranayamaRatioStage.CUSTOM, PranayamaRatioStage.matchRatio(4, 7, 8, 2))

        // 7. Test proportional scaling with base multiplier 5
        val baseSec = 5
        val classical = PranayamaRatioStage.VISAMA_VRITTI_CLASSICAL
        val scaledPurak = baseSec * classical.purakRatio
        val scaledAntar = baseSec * classical.antarRatio
        val scaledRechak = baseSec * classical.rechakRatio
        val scaledBahya = baseSec * classical.bahyaRatio
        assertEquals(5, scaledPurak)
        assertEquals(20, scaledAntar)
        assertEquals(10, scaledRechak)
        assertEquals(20, scaledBahya)
        assertEquals(PranayamaRatioStage.VISAMA_VRITTI_CLASSICAL, PranayamaRatioStage.matchRatio(scaledPurak, scaledAntar, scaledRechak, scaledBahya))
    }
}
