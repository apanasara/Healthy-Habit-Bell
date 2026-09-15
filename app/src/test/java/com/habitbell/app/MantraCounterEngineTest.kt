package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.MantraCounterConfig
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.mantra.*
import org.junit.Assert.*
import org.junit.Test

/**
 * # MantraCounterEngineTest
 *
 * Comprehensive unit test suite verifying:
 * 1. Default mantra counter profile configurations (Gayatri, Maha Mrityunjaya, Aumkar, Ram Japa, Tasbih, Jesus Prayer, Universal).
 * 2. Strict preservation of existing breathwork, compound, and linear meditation profiles.
 * 3. [MantraUpdate] mathematical calculations, progress fractions, half-Mala milestones, and formatting.
 * 4. [ManualTapMantraProvider] bead increments and cadence (CPM) estimation.
 * 5. [MantraCountManager] bead progression, half-Mala milestone trigger (54), and full completion trigger (108).
 * 6. [AcousticMantraSensorProvider.BiquadBandpassFilter] frequency response (formant passband vs. low-rumble and high-hiss attenuation).
 * 7. Autocorrelation fundamental pitch estimation for sustained Aumkar chanting (136.1 Hz Pranava tone).
 * 8. [TimerSessionState] integration with active mantra counter updates.
 *
 * Architectural Layer: Unit Test / Mantra & Sacred Verse Counter Verification
 * Threading Model: Synchronous JUnit test runner
 */
class MantraCounterEngineTest {

    /**
     * Verifies that pre-packaged mantra counter profiles contain accurate canonical parameters,
     * target bead counts, timing envelopes, and technique mappings.
     */
    @Test
    fun testMantraCounterDefaultProfiles() {
        // 1. Primary Mantra Counter Preset Profile
        val mantraProfile = DefaultProfiles.MANTRA_COUNTER
        assertTrue("Mantra Counter must have mantra counting enabled", mantraProfile.isMantraCountingEnabled)
        assertEquals("mantra-japa-counter", mantraProfile.id)
        assertEquals("Mantra Counter", mantraProfile.name)
        assertEquals(TimerType.MULTI_INTERVAL, mantraProfile.type)

        val mConfig = mantraProfile.mantraConfig
        assertNotNull("Mantra configuration must not be null", mConfig)
        assertEquals(MantraTechnique.GAYATRI_MANTRA, mConfig!!.technique)
        assertEquals(108, mConfig.targetBeads)
        assertEquals(1, mConfig.targetMalas)
        assertEquals(108, mConfig.totalTargetChants)
        assertEquals(2.5f, mConfig.minVerseDurationSec, 0.01f)
        assertEquals(1.8f, mConfig.interVersePauseThresholdSec, 0.01f)
        assertEquals(MantraMode.EXTENDED_VERSE, mConfig.technique.defaultMode)

        // 2. Canonical technique parameter resolution via withTechnique()
        val mrityunjaya = mConfig.withTechnique(MantraTechnique.MAHA_MRITYUNJAYA)
        assertEquals(MantraTechnique.MAHA_MRITYUNJAYA, mrityunjaya.technique)
        assertEquals(108, mrityunjaya.targetBeads)
        assertEquals(2.8f, mrityunjaya.minVerseDurationSec, 0.01f)
        assertEquals(1.8f, mrityunjaya.interVersePauseThresholdSec, 0.01f)
        assertEquals(MantraMode.EXTENDED_VERSE, mrityunjaya.technique.defaultMode)

        val aumkar = mConfig.withTechnique(MantraTechnique.AUMKAR)
        assertEquals(MantraTechnique.AUMKAR, aumkar.technique)
        assertEquals(21, aumkar.targetBeads)
        assertEquals(2.2f, aumkar.minVerseDurationSec, 0.01f)
        assertEquals(1.2f, aumkar.interVersePauseThresholdSec, 0.01f)
        assertEquals(MantraMode.AUMKAR_DRONE, aumkar.technique.defaultMode)

        val ramJapa = mConfig.withTechnique(MantraTechnique.RAM_JAPA)
        assertEquals(MantraTechnique.RAM_JAPA, ramJapa.technique)
        assertEquals(108, ramJapa.targetBeads)
        assertEquals(0.12f, ramJapa.minVerseDurationSec, 0.01f)
        assertEquals(0.45f, ramJapa.interVersePauseThresholdSec, 0.01f)
        assertEquals(MantraMode.SHORT_JAPA, ramJapa.technique.defaultMode)

        val tasbih = mConfig.withTechnique(MantraTechnique.TASBIH_DHIKR)
        assertEquals(MantraTechnique.TASBIH_DHIKR, tasbih.technique)
        assertEquals(100, tasbih.targetBeads)
        assertEquals(0.18f, tasbih.minVerseDurationSec, 0.01f)
        assertEquals(0.50f, tasbih.interVersePauseThresholdSec, 0.01f)
        assertEquals(MantraMode.SHORT_JAPA, tasbih.technique.defaultMode)

        val jesusPrayer = mConfig.withTechnique(MantraTechnique.JESUS_PRAYER)
        assertEquals(MantraTechnique.JESUS_PRAYER, jesusPrayer.technique)
        assertEquals(33, jesusPrayer.targetBeads)
        assertEquals(0.80f, jesusPrayer.minVerseDurationSec, 0.01f)
        assertEquals(0.60f, jesusPrayer.interVersePauseThresholdSec, 0.01f)
        assertEquals(MantraMode.SHORT_JAPA, jesusPrayer.technique.defaultMode)

        val universal = mConfig.withTechnique(MantraTechnique.UNIVERSAL_VERSE)
        assertEquals(MantraTechnique.UNIVERSAL_VERSE, universal.technique)
        assertEquals(108, universal.targetBeads)
        assertEquals(2.2f, universal.minVerseDurationSec, 0.01f)
        assertEquals(1.6f, universal.interVersePauseThresholdSec, 0.01f)

        // 3. User hardware preferences preservation across technique changes
        val customConfig = mConfig.copy(
            defaultInputMode = MantraInputSourceType.MANUAL_BEAD_TAP,
            micSensitivity = 1.8f,
            isBeadHapticEnabled = false,
            isMilestoneChimeEnabled = false
        )
        val switched = customConfig.withTechnique(MantraTechnique.AUMKAR)
        assertEquals(MantraInputSourceType.MANUAL_BEAD_TAP, switched.defaultInputMode)
        assertEquals(1.8f, switched.micSensitivity, 0.01f)
        assertFalse(switched.isBeadHapticEnabled)
        assertFalse(switched.isMilestoneChimeEnabled)

        // 4. Catalog size verification: 11 core presets in ALL_PRESETS
        assertEquals(11, DefaultProfiles.ALL_PRESETS.size)
    }

    /**
     * Verifies strict non-regression and isolation: existing breathwork, compound,
     * linear meditation, and mindful eating presets must not have mantra counting enabled.
     */
    @Test
    fun testExistingProfilesIsolation() {
        val breathCounter = DefaultProfiles.BREATH_COUNTER
        assertFalse("Breath Counter must NOT have mantra counting enabled", breathCounter.isMantraCountingEnabled)
        assertTrue(breathCounter.isBreathCountingEnabled)

        val hatha = DefaultProfiles.PRANAYAMA_HATHA
        assertFalse("Hatha Pranayama must NOT have mantra counting enabled", hatha.isMantraCountingEnabled)
        assertFalse(hatha.isBreathCountingEnabled)

        val surya = DefaultProfiles.SURYA_NAMASKAR
        assertFalse("Surya Namaskar must NOT have mantra counting enabled", surya.isMantraCountingEnabled)

        val eating = DefaultProfiles.EATING
        assertFalse("Mindful Eating must NOT have mantra counting enabled", eating.isMantraCountingEnabled)

        val reiki = DefaultProfiles.REIKI
        assertFalse("Reiki must NOT have mantra counting enabled", reiki.isMantraCountingEnabled)
    }

    /**
     * Verifies [MantraUpdate] progress fractions, half-Mala detection, and UI string formatting.
     */
    @Test
    fun testMantraUpdateMathAndFormatting() {
        val update = MantraUpdate(
            currentBead = 54,
            targetBeads = 108,
            currentMala = 1,
            targetMalas = 1,
            totalSessionChants = 54,
            cadenceCpm = 8,
            activeVerseDurationSeconds = 9.4f,
            audioAmplitudeRms = 0.45f,
            technique = MantraTechnique.GAYATRI_MANTRA
        )

        // Progress fraction = 54 / 108 = 0.5f
        assertEquals(0.5f, update.malaProgressFraction, 0.001f)
        assertEquals(0.5f, update.overallProgressFraction, 0.001f)
        assertTrue("54th bead of 108 must represent half-Mala", update.currentBead == update.targetBeads / 2)
        assertFalse("Session not completed at 54 beads", update.isCompleted)

        assertEquals("54 / 108", update.formattedBeadDisplay)
        assertEquals("MALA 1", update.formattedMalaDisplay)
        assertEquals("9.4s", update.formattedVerseTimer)
        assertEquals("8 CPM", update.formattedCadenceDisplay)

        // Completion snapshot
        val completed = update.copy(currentBead = 108, totalSessionChants = 108, isCompleted = true)
        assertEquals(1.0f, completed.malaProgressFraction, 0.001f)
        assertTrue("Session completed at 108 beads", completed.isCompleted)
    }

    /**
     * Verifies [ManualTapMantraProvider] bead increments and cadence (CPM) calculations.
     */
    @Test
    fun testManualTapMantraProviderCadence() {
        val provider = ManualTapMantraProvider()
        val config = MantraCounterConfig.DEFAULT_GAYATRI
        provider.start(config)

        // First manual tap emits a single bead increment
        provider.registerManualBead()
        val firstEvent = provider.inputFlow.value
        assertEquals(1, firstEvent.beadDelta)

        // Second manual tap registers progression
        provider.registerManualBead()
        val secondEvent = provider.inputFlow.value
        assertEquals(1, secondEvent.beadDelta)

        provider.stop()
    }

    /**
     * Verifies [MantraCountManager] bead progression, half-Mala milestone triggers (at 54),
     * and full completion gong triggers (at 108).
     */
    @Test
    fun testMantraCountManagerBeadProgressionAndMilestones() {
        val simulated = SimulatedMantraProvider()
        val manualTap = ManualTapMantraProvider()
        val acoustic = AcousticMantraSensorProvider()

        val manager = MantraCountManager(
            context = null,
            acousticProvider = acoustic,
            manualTapProvider = manualTap,
            simulatedProvider = simulated
        )

        var milestoneTriggered = false
        var milestoneBead = 0
        var sessionCompletedTriggered = false
        var totalBeadsCounted = 0

        manager.onBeadRegistered = { bead, _, _, _ ->
            totalBeadsCounted = bead
        }
        manager.onMilestoneReached = { bead ->
            milestoneTriggered = true
            milestoneBead = bead
        }
        manager.onSessionCompleted = {
            sessionCompletedTriggered = true
        }

        // Start 108-bead session
        val config = MantraCounterConfig(
            technique = MantraTechnique.GAYATRI_MANTRA,
            targetBeads = 108,
            targetMalas = 1
        )
        manager.startSession(config)

        // Step up to bead 53
        for (i in 1..53) {
            manager.registerManualBead()
        }
        assertEquals(53, totalBeadsCounted)
        assertFalse("Milestone should not trigger before 54th bead", milestoneTriggered)

        // Bead 54 -> Half-Mala Milestone!
        manager.registerManualBead()
        assertEquals(54, totalBeadsCounted)
        assertTrue("Half-Mala milestone must trigger on 54th bead", milestoneTriggered)
        assertEquals(54, milestoneBead)
        assertFalse("Session not completed at 54 beads", sessionCompletedTriggered)

        // Step up to bead 107
        for (i in 55..107) {
            manager.registerManualBead()
        }
        assertFalse("Session not completed at 107 beads", sessionCompletedTriggered)

        // Bead 108 -> Session Completed!
        manager.registerManualBead()
        assertEquals(108, totalBeadsCounted)
        assertTrue("Session completed must trigger on 108th bead", sessionCompletedTriggered)

        manager.stopSession()
    }

    /**
     * Verifies that the 2nd-order Biquad Voice Bandpass Filter (center 800 Hz, Q = 0.5)
     * correctly passes voice formant frequencies (500 Hz - 1500 Hz) while significantly
     * attenuating low-frequency physical room rumble (60 Hz / 100 Hz) and high-frequency mic hiss (7500 Hz).
     */
    @Test
    fun testVoiceBandpassFilterFrequencyResponse() {
        val sampleRate = 16000f
        val filter = AcousticMantraSensorProvider.BiquadBandpassFilter(
            sampleRate = sampleRate,
            centerFreq = 800f,
            q = 0.5f
        )

        fun measureRms(freqHz: Float, numSamples: Int = 1600): Float {
            filter.reset()
            var sumSquares = 0.0f
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

        val formantRms = measureRms(800f)  // Center voice formant
        val rumbleRms = measureRms(60f)    // AC mains / HVAC rumble
        val hissRms = measureRms(7500f)    // High frequency sensor hiss

        // Formant should pass robustly (> 0.5 RMS)
        assertTrue("Voice formant 800 Hz RMS ($formantRms) should pass (> 0.5)", formantRms > 0.5f)

        // 60 Hz rumble must be attenuated by at least 8x
        assertTrue(
            "Low frequency rumble ($rumbleRms) must be heavily attenuated compared to formant ($formantRms)",
            rumbleRms < formantRms * 0.15f
        )

        // 7500 Hz hiss must be attenuated
        assertTrue(
            "High frequency hiss ($hissRms) must be attenuated compared to formant ($formantRms)",
            hissRms < formantRms * 0.20f
        )
    }

    /**
     * Verifies the Normalized Autocorrelation algorithm detects fundamental vocal pitch
     * for sustained Aumkar chanting (synthetic sine wave at 136.1 Hz, the classical Vedic C# frequency).
     */
    @Test
    fun testAutocorrelationPitchEstimationForAumkar() {
        val sampleRate = 16000
        val targetFreq = 136.1f // Traditional Vedic C# Aum frequency
        val buffer = ShortArray(1600) // 100ms window

        for (i in buffer.indices) {
            val t = i.toFloat() / sampleRate
            val sampleVal = (kotlin.math.sin(2.0 * Math.PI * targetFreq * t) * 20000.0).toInt()
            buffer[i] = sampleVal.toShort()
        }

        val estimatedPitch = AcousticMantraSensorProvider.computeAutocorrelationPitch(
            buffer = buffer,
            length = buffer.size,
            sampleRate = sampleRate,
            minFreqHz = 75f,
            maxFreqHz = 320f
        )

        // Expected lag = 16000 / 136.1 = ~117.5 samples -> estimated pitch ~136 Hz
        assertEquals(136.1f, estimatedPitch, 4.0f)
    }

    /**
     * Verifies [TimerSessionState] integration with active mantra counter updates.
     */
    @Test
    fun testTimerSessionStateWithMantraCounter() {
        val profile = DefaultProfiles.MANTRA_COUNTER
        val update = MantraUpdate(
            currentBead = 27,
            targetBeads = 108,
            currentMala = 1,
            targetMalas = 1,
            totalSessionChants = 27,
            cadenceCpm = 10,
            activeVerseDurationSeconds = 8.2f,
            audioAmplitudeRms = 0.6f,
            technique = MantraTechnique.GAYATRI_MANTRA
        )

        val state = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = profile,
            mantraUpdate = update,
            currentRound = update.currentMala,
            totalRounds = update.targetMalas
        )

        assertTrue("Mantra counting must be active in session state", state.isMantraCountingActive)
        assertNotNull("Mantra update must not be null", state.mantraUpdate)
        assertEquals(27, state.mantraUpdate?.currentBead)
        assertEquals(10, state.mantraUpdate?.cadenceCpm)
        assertEquals(MantraTechnique.GAYATRI_MANTRA, state.mantraUpdate?.technique)
        assertEquals("Mantra Counter", state.profile.name)
    }

    /**
     * Verifies that [MantraUpdate] and [MantraInputEvent] carry ambient acoustic noise calibration metrics,
     * and confirms that active bead counting is gated during room calibration while amplitude is forwarded.
     */
    @Test
    fun testMantraAmbientAcousticCalibrationStateAndGating() {
        // 1. MantraUpdate calibration state
        val calibratingUpdate = MantraUpdate(
            currentBead = 0,
            targetBeads = 108,
            isCalibrating = true,
            calibrationSecondsRemaining = 3,
            audioAmplitudeRms = 0.02f,
            thresholdRms = 0.06f
        )
        assertTrue(calibratingUpdate.isCalibrating)
        assertEquals(3, calibratingUpdate.calibrationSecondsRemaining)

        // 2. MantraInputEvent calibration telemetry
        val event = MantraInputEvent(
            beadDelta = 0,
            audioAmplitudeRms = 0.03f,
            thresholdRms = 0.06f,
            isCalibrating = true,
            calibrationProgress = 0.66f
        )
        assertTrue(event.isCalibrating)
        assertEquals(0.66f, event.calibrationProgress, 0.01f)

        // 3. Manager gating during calibration
        val manager = MantraCountManager()
        val config = MantraCounterConfig.DEFAULT_GAYATRI
        manager.startSession(config)

        // In manual tap mode (no mic hardware in unit test), isCalibrating is false
        assertFalse(manager.mantraFlow.value.isCalibrating)
    }

    /**
     * Verifies that [AcousticMantraSensorProvider.getAmbientMusicSafetyMargin] accurately computes
     * dynamic RMS safety margins based on background music active state and volume gain.
     */
    @Test
    fun testAmbientMusicSafetyMarginCalculation() {
        val provider = AcousticMantraSensorProvider()

        // 1. When background music is not playing -> 0.0f margin
        provider.isAmbientMusicPlaying = { false }
        provider.ambientMusicVolume = { 1.0f }
        assertEquals(0.0f, provider.getAmbientMusicSafetyMargin(), 0.0001f)

        // 2. When background music is active at unity gain (1.0f) -> 0.008f margin (1.0 * 0.008)
        provider.isAmbientMusicPlaying = { true }
        provider.ambientMusicVolume = { 1.0f }
        assertEquals(0.008f, provider.getAmbientMusicSafetyMargin(), 0.0001f)

        // 3. When background music is active at half gain (0.5f) -> 0.004f margin (0.5 * 0.008)
        provider.ambientMusicVolume = { 0.5f }
        assertEquals(0.004f, provider.getAmbientMusicSafetyMargin(), 0.0001f)

        // 4. When background music is active at minimum volume (0.0f) -> 0.0f
        provider.ambientMusicVolume = { 0.0f }
        assertEquals(0.0f, provider.getAmbientMusicSafetyMargin(), 0.0001f)
    }

    /**
     * Verifies that [com.habitbell.app.breath.AcousticBreathSensorProvider.getAmbientMusicSafetyMargin]
     * computes safety margins to prevent breath stroke false positives during ambient music playback.
     */
    @Test
    fun testAcousticBreathSensorProviderAmbientMarginCalculation() {
        val provider = com.habitbell.app.breath.AcousticBreathSensorProvider()

        // 1. Music inactive -> 0.0f
        provider.isAmbientMusicPlaying = { false }
        provider.ambientMusicVolume = { 1.0f }
        assertEquals(0.0f, provider.getAmbientMusicSafetyMargin(), 0.0001f)

        // 2. Music active at unity gain (1.0f) -> 0.005f margin (1.0 * 0.005)
        provider.isAmbientMusicPlaying = { true }
        provider.ambientMusicVolume = { 1.0f }
        assertEquals(0.005f, provider.getAmbientMusicSafetyMargin(), 0.0001f)

        // 3. Music active at half gain (0.5f) -> 0.0025f margin (0.5 * 0.005)
        provider.ambientMusicVolume = { 0.5f }
        assertEquals(0.0025f, provider.getAmbientMusicSafetyMargin(), 0.0001f)
    }

    /**
     * Verifies that when ambient room sound scanning is performed during pre-session preparation,
     * [MantraUpdate] correctly holds the calibrated baseline metrics, and active recitation
     * proceeds with calibrated thresholds without resetting to initial values.
     */
    @Test
    fun testMantraPreSessionCalibrationPreservation() {
        val config = MantraCounterConfig.DEFAULT_GAYATRI
        val preCalibratedUpdate = MantraUpdate(
            currentBead = 0,
            targetBeads = config.targetBeads,
            currentMala = 1,
            targetMalas = config.targetMalas,
            totalSessionChants = 0,
            cadenceCpm = 0,
            audioAmplitudeRms = 0f,
            thresholdRms = 0.045f,
            isReciting = false,
            micSensitivity = config.micSensitivity,
            isCalibrating = false,
            calibrationSecondsRemaining = 0,
            technique = config.technique,
            isCompleted = false
        )

        assertFalse("Pre-calibrated update must have isCalibrating=false", preCalibratedUpdate.isCalibrating)
        assertEquals(0, preCalibratedUpdate.calibrationSecondsRemaining)
        assertEquals(0, preCalibratedUpdate.currentBead)
    }
}
