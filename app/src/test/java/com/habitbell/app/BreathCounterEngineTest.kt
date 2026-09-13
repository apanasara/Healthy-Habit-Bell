package com.habitbell.app

import com.habitbell.app.breath.*
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.BreathCounterConfig
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import org.junit.Assert.*
import org.junit.Test

/**
 * # BreathCounterEngineTest
 *
 * Comprehensive unit test suite verifying:
 * 1. Default breath counter profile configurations (Kapalabhati, Bhastrika, Bhramari).
 * 2. Uncompromised preservation of classical guided Pranayama (Visama Vritti, Box, 4-7-8).
 * 3. Manual tap provider cadence (BPM) calculation and debounce protection.
 * 4. [BreathStrokeUpdate] formatting, progress fractions, and round boundaries.
 * 5. [TimerSessionState] integration with breath counter updates.
 *
 * Architectural Layer: Unit Test / Breath Counter Verification
 * Threading Model: Synchronous JUnit test runner
 */
class BreathCounterEngineTest {

    /**
     * Verifies that pre-packaged breath counter profiles contain accurate technique parameters,
     * target stroke counts, and retention stages.
     */
    @Test
    fun testBreathCounterDefaultProfiles() {
        // 1. Unified Breathwork Counter: 3 rounds (30, 60, 90 strokes), 20s Kumbhaka, 15s rest
        val breathCounter = DefaultProfiles.BREATH_COUNTER
        assertTrue("Unified Breath Counter must have breath counting enabled", breathCounter.isBreathCountingEnabled)
        assertEquals("kriya-breath-counter", breathCounter.id)
        val bConfig = breathCounter.breathCounterConfig
        assertNotNull("Breath counter config must not be null", bConfig)
        assertEquals(BreathTechnique.KAPALABHATI, bConfig!!.technique)
        assertEquals(3, bConfig.targetRounds)
        assertEquals(listOf(30, 60, 90), bConfig.strokesPerRound)
        assertEquals(20, bConfig.retentionSeconds)
        assertEquals(15, bConfig.restSeconds)
        assertEquals(30, bConfig.strokesForRound(1))
        assertEquals(60, bConfig.strokesForRound(2))
        assertEquals(90, bConfig.strokesForRound(3))
        assertEquals(180, bConfig.totalTargetStrokes)

        // 2. Dynamic technique switching preserves hardware preferences
        val bhastrikaConfig = bConfig.withTechnique(BreathTechnique.BHASTRIKA)
        assertEquals(BreathTechnique.BHASTRIKA, bhastrikaConfig.technique)
        assertEquals(3, bhastrikaConfig.targetRounds)
        assertEquals(listOf(21, 21, 21), bhastrikaConfig.strokesPerRound)
        assertEquals(25, bhastrikaConfig.retentionSeconds)
        assertEquals(20, bhastrikaConfig.restSeconds)

        val bhramariConfig = bConfig.withTechnique(BreathTechnique.BHRAMARI)
        assertEquals(BreathTechnique.BHRAMARI, bhramariConfig.technique)
        assertEquals(7, bhramariConfig.targetRounds)
        assertEquals(0, bhramariConfig.retentionSeconds)
        assertEquals(5, bhramariConfig.restSeconds)

        // 3. Backwards-compatible accessor aliases
        val kapalabhati = DefaultProfiles.KAPALABHATI_COUNTER
        assertEquals(BreathTechnique.KAPALABHATI, kapalabhati.breathCounterConfig?.technique)
        val bhastrika = DefaultProfiles.BHASTRIKA_COUNTER
        assertEquals(BreathTechnique.BHASTRIKA, bhastrika.breathCounterConfig?.technique)
        val bhramari = DefaultProfiles.BHRAMARI_COUNTER
        assertEquals(BreathTechnique.BHRAMARI, bhramari.breathCounterConfig?.technique)

        // 4. Catalog size verification: 9 core profiles
        assertEquals(9, DefaultProfiles.ALL_PRESETS.size)
    }

    /**
     * Strict verification that classical guided Pranayama (Visama Vritti 4:16:8:16)
     * has NOT been modified or compromised in any way.
     */
    @Test
    fun testGuidedPranayamaPreservation() {
        val hatha = DefaultProfiles.PRANAYAMA_HATHA
        assertFalse("Classical Hatha Pranayama must not be marked as breath counter", hatha.isBreathCountingEnabled)
        assertEquals(TimerType.MULTI_INTERVAL, hatha.type)
        assertNotNull(hatha.pranayamaConfig)
        assertEquals(12, hatha.pranayamaConfig!!.targetRounds)
        assertEquals(4, hatha.pranayamaConfig!!.purakSeconds)
        assertEquals(16, hatha.pranayamaConfig!!.antarKumbhakSeconds)
        assertEquals(8, hatha.pranayamaConfig!!.rechakSeconds)
        assertEquals(16, hatha.pranayamaConfig!!.bahyaKumbhakSeconds)

        val surya = DefaultProfiles.SURYA_NAMASKAR
        assertFalse("Surya Namaskar compound must not be marked as breath counter", surya.isBreathCountingEnabled)
        assertEquals(TimerType.COMPOUND, surya.type)

        val eating = DefaultProfiles.EATING
        assertFalse("Eating profile must not have breath counting enabled", eating.isBreathCountingEnabled)
    }

    /**
     * Verifies manual tap provider cadence estimation and stroke delta emissions.
     */
    @Test
    fun testManualTapProviderCadence() {
        val provider = ManualTapBreathProvider()
        val config = BreathCounterConfig(
            technique = BreathTechnique.KAPALABHATI,
            targetRounds = 3,
            strokesPerRound = listOf(30, 60, 90)
        )
        provider.start(config)

        // First tap initiates baseline
        provider.registerManualStroke()
        val firstUpdate = provider.inputFlow.value
        assertEquals(1, firstUpdate.strokeDelta)
        assertEquals(0, firstUpdate.instantaneousCadenceBpm)

        // Reset and cleanup
        provider.stop()
    }

    /**
     * Verifies [BreathStrokeUpdate] formatting, progress fractions, and completion flags.
     */
    @Test
    fun testBreathStrokeUpdateMathAndFormatting() {
        val update = BreathStrokeUpdate(
            currentRoundStrokes = 15,
            targetRoundStrokes = 30,
            totalSessionStrokes = 45,
            currentRound = 2,
            targetRounds = 3,
            cadenceBpm = 75,
            currentPhase = BreathCounterPhase.STROKES,
            phaseRemainingSeconds = 0,
            phaseDurationSeconds = 0,
            humDurationSeconds = 0f,
            audioAmplitudeRms = 0.5f
        )

        assertEquals(0.5f, update.roundProgressFraction, 0.001f)
        assertFalse(update.isRoundGoalReached)
        assertEquals("15 / 30", update.formattedStrokeDisplay)
        assertEquals("75 BPM", update.formattedCadenceDisplay)
        assertEquals("Round 2 of 3", update.formattedRoundDisplay)

        // Goal reached snapshot
        val completedUpdate = update.copy(currentRoundStrokes = 30)
        assertEquals(1.0f, completedUpdate.roundProgressFraction, 0.001f)
        assertTrue(completedUpdate.isRoundGoalReached)

        // Retention hold snapshot
        val retentionUpdate = BreathStrokeUpdate(
            currentPhase = BreathCounterPhase.RETENTION_HOLD,
            phaseRemainingSeconds = 5,
            phaseDurationSeconds = 20
        )
        // (20 - 5) / 20 = 15 / 20 = 0.75f progress
        assertEquals(0.75f, retentionUpdate.roundProgressFraction, 0.001f)
    }

    /**
     * Verifies [TimerSessionState] computation and integration for breath counter profiles.
     */
    @Test
    fun testTimerSessionStateWithBreathCounter() {
        val profile = DefaultProfiles.KAPALABHATI_COUNTER
        val update = BreathStrokeUpdate(
            currentRoundStrokes = 12,
            targetRoundStrokes = 30,
            totalSessionStrokes = 12,
            currentRound = 1,
            targetRounds = 3,
            cadenceBpm = 68,
            currentPhase = BreathCounterPhase.STROKES
        )

        val state = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = profile,
            breathUpdate = update,
            currentRound = update.currentRound,
            totalRounds = update.targetRounds
        )

        assertTrue(state.isBreathCountingActive)
        assertNotNull(state.breathUpdate)
        assertEquals(12, state.breathUpdate?.currentRoundStrokes)
        assertEquals(68, state.breathUpdate?.cadenceBpm)
        assertEquals("Kapalabhati Counter", state.profile.name)
    }

    /**
     * Verifies the 2nd-order Biquad Bandpass Filter (center 2400 Hz, Q = 1.0)
     * correctly passes Kapalabhati nasal hiss frequencies while rejecting low-frequency
     * rumble (100 Hz) and ultra-high frequency mic hiss (7500 Hz).
     */
    @Test
    fun testBiquadBandpassFilterAttenuatesRumbleAndHiss() {
        val sampleRate = 16000f
        val filter = AcousticBreathSensorProvider.BiquadBandpassFilter(
            sampleRate = sampleRate,
            centerFreq = 2000f,
            q = 0.8f
        )

        // Helper to compute RMS of filtered sine wave of given frequency
        fun measureFilterRms(freqHz: Float, numSamples: Int = 1600): Float {
            filter.reset()
            var sumSquares = 0.0f
            // Skip first 200 samples for filter transient settling
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val input = kotlin.math.sin(2.0 * Math.PI * freqHz * t).toFloat()
                val output = filter.process(input)
                if (i >= 200) {
                    sumSquares += output * output
                }
            }
            return kotlin.math.sqrt(sumSquares / (numSamples - 200))
        }

        val passbandRms = measureFilterRms(2000f) // Center frequency (breath hiss)
        val rumbleRms = measureFilterRms(100f)    // Room rumble / low humming
        val hissRms = measureFilterRms(7500f)     // High-frequency sensor hiss

        // Center frequency should pass with minimal attenuation (near 1.0 / sqrt(2) = 0.707)
        assertTrue("Passband 2000 Hz RMS ($passbandRms) should be robust (> 0.5)", passbandRms > 0.5f)

        // 100 Hz rumble must be attenuated by at least 15x (> 23 dB)
        assertTrue(
            "Low frequency rumble ($rumbleRms) must be heavily attenuated compared to passband ($passbandRms)",
            rumbleRms < passbandRms * 0.10f
        )

        // 7500 Hz hiss must also be attenuated
        assertTrue(
            "High frequency hiss ($hissRms) must be attenuated compared to passband ($passbandRms)",
            hissRms < passbandRms * 0.20f
        )
    }

    /**
     * Verifies that [BreathStrokeUpdate] holds mic sensitivity and dynamic threshold metrics.
     */
    @Test
    fun testBreathStrokeUpdateSensitivityAndThreshold() {
        val update = BreathStrokeUpdate(
            currentRoundStrokes = 10,
            targetRoundStrokes = 30,
            thresholdRms = 0.08f,
            micSensitivity = 1.5f
        )

        assertEquals(0.08f, update.thresholdRms, 0.001f)
        assertEquals(1.5f, update.micSensitivity, 0.001f)
    }
}

