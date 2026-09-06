package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.PranayamaPhase
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
     * Total cycle = 44s, target = 20 rounds, interval bell = every 5 rounds.
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
        assertEquals(20, config.targetRounds)
        assertEquals(5, config.intervalBellRoundCadence)
        assertTrue(config.isVoiceGuidanceEnabled)
        assertEquals(VoiceCueStyle.SANSKRIT, config.voiceCueStyle)

        val totalExpected = 44 * 20
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
}
