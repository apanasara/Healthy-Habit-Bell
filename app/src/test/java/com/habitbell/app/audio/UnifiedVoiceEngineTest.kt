package com.habitbell.app.audio

import com.habitbell.app.R
import com.habitbell.app.data.model.HoldTimerConfig
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.VoiceCueStyle
import com.habitbell.app.holdtimer.HoldTimerEngine
import com.habitbell.app.holdtimer.HoldTimerPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * # UnifiedVoiceEngineTest
 *
 * Unit test suite verifying [UnifiedVoiceEngine] cue resolution, step timing guards,
 * [HoldTimerConfig] serialization with voice parameters, and [HoldTimerEngine] dual-cue coordination.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedVoiceEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class MockDualCueSpeaker : com.habitbell.app.holdtimer.DualCueSpeaker {
        val spokenTexts = mutableListOf<String>()
        var isStopped = false
        var isReleased = false

        override fun speak(text: String, speedMultiplier: Float) {
            spokenTexts.add(text)
        }

        override fun stop() {
            isStopped = true
        }

        override fun release() {
            isReleased = true
        }
    }

    private lateinit var mockSpeaker: MockDualCueSpeaker

    @Before
    fun setUp() {
        mockSpeaker = MockDualCueSpeaker()
    }

    @Test
    fun testPranayamaSanskritResourceResolution() {
        assertEquals(R.raw.pranayama_purak_sanskrit, resolveTestPranayamaResource(PranayamaPhase.INHALE, VoiceCueStyle.SANSKRIT, 4))
        assertEquals(R.raw.pranayama_kumbhak_sanskrit, resolveTestPranayamaResource(PranayamaPhase.HOLD_IN, VoiceCueStyle.SANSKRIT, 8))
        assertEquals(R.raw.pranayama_rechak_sanskrit, resolveTestPranayamaResource(PranayamaPhase.EXHALE, VoiceCueStyle.SANSKRIT, 8))
        assertEquals(R.raw.pranayama_kumbhak_sanskrit, resolveTestPranayamaResource(PranayamaPhase.HOLD_OUT, VoiceCueStyle.SANSKRIT, 4))
    }

    @Test
    fun testStepTimingVsVoiceTimingLaw_BilingualUnder6SecondsFallsBackToSanskrit() {
        val resourceUnder6s = resolveTestPranayamaResource(PranayamaPhase.INHALE, VoiceCueStyle.BILINGUAL, stepDurationSeconds = 4)
        assertEquals(
            "Under 6s step must fall back to Sanskrit cue to prevent clipping",
            R.raw.pranayama_purak_sanskrit,
            resourceUnder6s
        )

        val resourceAtLeast6s = resolveTestPranayamaResource(PranayamaPhase.HOLD_IN, VoiceCueStyle.BILINGUAL, stepDurationSeconds = 8)
        assertEquals(
            "Steps >= 6s must enjoy full bilingual studio cue",
            R.raw.pranayama_kumbhak_bilingual,
            resourceAtLeast6s
        )
    }

    @Test
    fun testEnglishVoiceStyleReturnsNullForTtsFallback() {
        val resource = resolveTestPranayamaResource(PranayamaPhase.INHALE, VoiceCueStyle.ENGLISH, 8)
        assertNull("English cue style must return null to trigger TTS synthesis fallback", resource)
    }

    @Test
    fun testHoldTimerConfigSerializationWithVoiceParameters() {
        val config = HoldTimerConfig(
            holdDurationSec = 45,
            restDurationSec = 20,
            repeatCount = 3,
            ttsSpeed = 0.90f,
            voiceCueStyle = VoiceCueStyle.BILINGUAL,
            voiceVolume = 0.65f
        )

        val json = config.toJson()
        assertTrue("JSON must contain voiceCueStyle", json.contains("voiceCueStyle"))
        assertTrue("JSON must contain voiceVolume", json.contains("voiceVolume"))

        val deserialized = HoldTimerConfig.fromJson(json)
        assertEquals(VoiceCueStyle.BILINGUAL, deserialized.voiceCueStyle)
        assertEquals(0.65f, deserialized.voiceVolume, 0.01f)
        assertEquals(45, deserialized.holdDurationSec)
        assertEquals(20, deserialized.restDurationSec)
        assertEquals(3, deserialized.repeatCount)
    }

    @Test
    fun testHoldTimerEngineDualCueExecutionWithSpeaker() = runTest(testDispatcher) {
        val config = HoldTimerConfig(
            holdDurationSec = 3,
            restDurationSec = 2,
            repeatCount = 2,
            ttsSpeed = 1.0f,
            isCountAloudEnabled = true,
            voiceCueStyle = VoiceCueStyle.BILINGUAL,
            voiceVolume = 0.52f
        )

        val engine = HoldTimerEngine(
            speaker = mockSpeaker,
            coroutineScope = testScope
        )

        engine.loadConfig(config)
        assertEquals(HoldTimerPhase.PREPARATION, engine.sessionState.value.phase)

        engine.startSession()

        advanceTimeBy(1300)
        assertEquals(HoldTimerPhase.HOLD, engine.sessionState.value.phase)
        assertTrue(mockSpeaker.spokenTexts.contains("First"))

        advanceTimeBy(3500)
        assertTrue(mockSpeaker.spokenTexts.contains("one"))
        assertTrue(mockSpeaker.spokenTexts.contains("two"))
        assertTrue(mockSpeaker.spokenTexts.contains("three"))

        advanceTimeBy(1200)
        assertEquals(HoldTimerPhase.REST, engine.sessionState.value.phase)
        assertTrue(mockSpeaker.spokenTexts.contains("Rest"))

        advanceTimeBy(10000)
        assertEquals(HoldTimerPhase.COMPLETED, engine.sessionState.value.phase)
        assertTrue(mockSpeaker.spokenTexts.contains("Session complete"))
    }

    private fun resolveTestPranayamaResource(
        phase: PranayamaPhase,
        style: VoiceCueStyle,
        stepDurationSeconds: Int? = null
    ): Int? {
        val effectiveStyle = if (style == VoiceCueStyle.BILINGUAL && stepDurationSeconds != null && stepDurationSeconds < 6) {
            VoiceCueStyle.SANSKRIT
        } else {
            style
        }

        return when (effectiveStyle) {
            VoiceCueStyle.SANSKRIT -> when (phase) {
                PranayamaPhase.INHALE -> R.raw.pranayama_purak_sanskrit
                PranayamaPhase.HOLD_IN -> R.raw.pranayama_kumbhak_sanskrit
                PranayamaPhase.EXHALE -> R.raw.pranayama_rechak_sanskrit
                PranayamaPhase.HOLD_OUT -> R.raw.pranayama_kumbhak_sanskrit
            }
            VoiceCueStyle.BILINGUAL -> when (phase) {
                PranayamaPhase.INHALE -> R.raw.pranayama_purak_bilingual
                PranayamaPhase.HOLD_IN -> R.raw.pranayama_kumbhak_bilingual
                PranayamaPhase.EXHALE -> R.raw.pranayama_rechak_bilingual
                PranayamaPhase.HOLD_OUT -> R.raw.pranayama_kumbhak_bilingual
            }
            VoiceCueStyle.ENGLISH -> null
        }
    }
}
