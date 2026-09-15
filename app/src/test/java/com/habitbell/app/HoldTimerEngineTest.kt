package com.habitbell.app

import com.habitbell.app.data.model.HoldTimerConfig
import com.habitbell.app.holdtimer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * # HoldTimerEngineTest
 *
 * Unit test suite verifying the Dual-Cue scheduler, in-session dynamic parameter alterations,
 * adaptive speed control, and clinician safety alerts for [HoldTimerEngine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldTimerEngineTest {

    private class MockDualCueSpeaker : DualCueSpeaker {
        val spokenPhrases = mutableListOf<String>()
        var lastSpeedMultiplier: Float = 1.0f

        override fun speak(text: String, speedMultiplier: Float) {
            spokenPhrases.add(text)
            lastSpeedMultiplier = speedMultiplier
        }

        override fun stop() {}
        override fun release() {}
    }

    private lateinit var mockSpeaker: MockDualCueSpeaker

    @Before
    fun setUp() {
        mockSpeaker = MockDualCueSpeaker()
    }

    @Test
    fun testConfigValidationAndJsonRoundtrip() {
        val config = HoldTimerConfig(
            holdDurationSec = 45,
            restDurationSec = 20,
            repeatCount = 5,
            maxHoldSec = 60,
            ttsSpeed = 1.15f
        )
        assertTrue(config.isHoldDurationSafe())
        assertEquals("First", config.getRoundName(1))
        assertEquals("Fifth", config.getRoundName(5))
        assertEquals("Round 13", config.getRoundName(13))

        val json = config.toJson()
        val parsed = HoldTimerConfig.fromJson(json)
        assertEquals(config.holdDurationSec, parsed.holdDurationSec)
        assertEquals(config.restDurationSec, parsed.restDurationSec)
        assertEquals(config.repeatCount, parsed.repeatCount)
        assertEquals(config.maxHoldSec, parsed.maxHoldSec)
        assertEquals(config.ttsSpeed, parsed.ttsSpeed, 0.01f)
    }

    @Test
    fun testSafetyWarningWhenHoldExceedsClinicianLimit() = runTest {
        val engine = HoldTimerEngine(speaker = mockSpeaker, coroutineScope = this)
        val unsafeConfig = HoldTimerConfig(
            holdDurationSec = 75,
            restDurationSec = 10,
            repeatCount = 3,
            maxHoldSec = 60
        )
        assertFalse(unsafeConfig.isHoldDurationSafe())

        engine.loadConfig(unsafeConfig)
        val state = engine.sessionState.value
        assertNotNull(state.safetyWarning)
        assertTrue(state.safetyWarning!!.contains("exceeds clinician limit"))
    }

    @Test
    fun testCountdownAndDualCueAnnouncements() = runTest {
        val engine = HoldTimerEngine(speaker = mockSpeaker, coroutineScope = this)
        val shortConfig = HoldTimerConfig(
            holdDurationSec = 3,
            restDurationSec = 2,
            repeatCount = 2,
            maxHoldSec = 60,
            ttsSpeed = 1.0f
        )
        engine.loadConfig(shortConfig)
        engine.startSession()

        // Allow preparation & first round announcement
        testScheduler.advanceTimeBy(1300)
        assertTrue(mockSpeaker.spokenPhrases.contains("First"))

        // Advance through 3-second hold count: "one", "two", "three"
        testScheduler.advanceTimeBy(3500)
        assertTrue(mockSpeaker.spokenPhrases.contains("one"))
        assertTrue(mockSpeaker.spokenPhrases.contains("two"))
        assertTrue(mockSpeaker.spokenPhrases.contains("three"))

        // Rest phase announced
        testScheduler.advanceTimeBy(1200)
        assertTrue(mockSpeaker.spokenPhrases.contains("Rest"))

        // Rest counts and advance into second round
        testScheduler.advanceTimeBy(4000)
        assertTrue(mockSpeaker.spokenPhrases.contains("Second"))
    }

    @Test
    fun testInSessionHoldDurationAdjustment() = runTest {
        val engine = HoldTimerEngine(speaker = mockSpeaker, coroutineScope = this)
        val config = HoldTimerConfig(holdDurationSec = 30, maxHoldSec = 60)
        engine.loadConfig(config)

        // Adjust hold duration on the fly to 45 seconds (safe)
        engine.updateHoldDuration(45)
        assertEquals(45, engine.sessionState.value.totalSecondsInPhase)
        assertNull(engine.sessionState.value.safetyWarning)
        assertTrue(mockSpeaker.spokenPhrases.any { it.contains("Hold set to 45 seconds") })

        // Adjust hold duration on the fly exceeding clinician max limit (75 seconds)
        engine.updateHoldDuration(75)
        assertNotNull(engine.sessionState.value.safetyWarning)
        assertTrue(mockSpeaker.spokenPhrases.any { it.contains("Warning: hold time exceeds the recommended limit") })
    }

    @Test
    fun testInSessionRepeatCountAdjustment() = runTest {
        val engine = HoldTimerEngine(speaker = mockSpeaker, coroutineScope = this)
        val config = HoldTimerConfig(repeatCount = 3)
        engine.loadConfig(config)

        engine.updateRepeatCount(5)
        assertEquals(5, engine.sessionState.value.totalRounds)
        assertTrue(mockSpeaker.spokenPhrases.any { it.contains("Repeats set to 5") })
    }

    @Test
    fun testAdaptiveSpeedControlTooFastAndTooSlow() = runTest {
        val engine = HoldTimerEngine(speaker = mockSpeaker, coroutineScope = this)
        val config = HoldTimerConfig(ttsSpeed = 1.0f)
        engine.loadConfig(config)

        // User says "Too fast" -> delta is -0.15f -> speed becomes 0.85f
        engine.adjustPace(-0.15f)
        assertEquals(0.85f, engine.sessionState.value.ttsSpeed, 0.01f)
        assertTrue(mockSpeaker.spokenPhrases.contains("Slower"))

        // User says "Too slow" -> delta is +0.15f -> speed becomes 1.0f
        engine.adjustPace(+0.15f)
        assertEquals(1.0f, engine.sessionState.value.ttsSpeed, 0.01f)
        assertTrue(mockSpeaker.spokenPhrases.contains("Faster"))
    }

    @Test
    fun testPauseResumeAndSkipRound() = runTest {
        val engine = HoldTimerEngine(speaker = mockSpeaker, coroutineScope = this)
        val config = HoldTimerConfig(holdDurationSec = 10, repeatCount = 3)
        engine.loadConfig(config)
        engine.startSession()

        testScheduler.advanceTimeBy(1500)
        engine.pause()
        assertTrue(engine.sessionState.value.isPaused)

        engine.resume()
        assertFalse(engine.sessionState.value.isPaused)

        engine.skipToNextRound()
        testScheduler.advanceTimeBy(1500)
        // Advances to round 2
        assertEquals(2, engine.sessionState.value.currentRound)
    }
}
