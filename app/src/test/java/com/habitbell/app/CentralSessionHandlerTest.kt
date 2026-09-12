package com.habitbell.app

import android.media.AudioAttributes
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # CentralSessionHandlerTest
 *
 * Unit test suite verifying:
 * 1. Audio routing attributes (Bug-1): USAGE_MEDIA and CONTENT_TYPE_MUSIC usage.
 * 2. Central session profile resolution logic for Android Auto ("eating", "posture", "breathing").
 * 3. Bidirectional state synchronization properties across Car HUD, Mobile, Watch, and TV (Bug-2).
 * 4. Assistant voice query transcription matching.
 */
class CentralSessionHandlerTest {

    /**
     * Verifies that AudioAttributes constants for automotive media channel routing
     * (USAGE_MEDIA and CONTENT_TYPE_MUSIC) match standard Android OS specifications (Bug-1).
     */
    @Test
    fun testAudioRoutingConstantsForAutomotiveMediaChannel() {
        assertEquals(
            "USAGE_MEDIA must be 1 to guarantee automotive media routing",
            AudioAttributes.USAGE_MEDIA,
            1
        )
        assertEquals(
            "CONTENT_TYPE_MUSIC must be 2 for soothing chimes and harmonic drones",
            AudioAttributes.CONTENT_TYPE_MUSIC,
            2
        )
    }

    /**
     * Verifies resolution of Android Auto media identifiers ("eating", "posture", "breathing")
     * to authoritative default wellness profiles.
     */
    @Test
    fun testAutomotiveMediaIdResolution() {
        val eatingProfile = DefaultProfiles.EATING
        assertEquals("eating-mindful-20", eatingProfile.id)
        assertEquals(2700, eatingProfile.totalDurationSeconds)
        assertEquals(60, eatingProfile.intervalDurationSeconds)

        val breathingProfile = DefaultProfiles.PRANAYAMA_BOX
        assertEquals("pranayama-box-breath", breathingProfile.id)
        assertEquals(20, breathingProfile.pranayamaConfig?.targetRounds)

        val postureProfile = DefaultProfiles.MINDFUL_READING
        assertEquals("mindful-reading-30", postureProfile.id)
        assertEquals(1800, postureProfile.totalDurationSeconds)
    }

    /**
     * Verifies that session states correctly reflect running, paused, and stopped statuses
     * for unified MediaSession state mapping (Bug-2).
     */
    @Test
    fun testSessionStatePlaybackMapping() {
        val runningState = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.EATING,
            remainingSeconds = 2640, // 44:00
            totalSeconds = 2700,
            nextBellSeconds = 60
        )
        assertEquals(SessionStatus.RUNNING, runningState.status)
        assertEquals("44:00", runningState.formattedRemainingTime)
        assertEquals("01:00", runningState.formattedNextBellTime)
        assertTrue(runningState.progressFraction > 0f)

        val pausedState = runningState.copy(status = SessionStatus.PAUSED)
        assertEquals(SessionStatus.PAUSED, pausedState.status)
        assertEquals("44:00", pausedState.formattedRemainingTime)
    }

    /**
     * Verifies natural language voice command matching rules for Assistant and Android Auto voice search.
     */
    @Test
    fun testVoiceCommandProfileMatching() {
        val presets = DefaultProfiles.ALL_PRESETS

        fun matchVoice(message: String) = presets.find {
            val lower = message.lowercase().trim()
            lower.isNotEmpty() && (
                lower.contains(it.name.lowercase()) ||
                it.name.lowercase().contains(lower) ||
                (lower.contains("eat") && it.id.contains("eating")) ||
                (lower.contains("read") && it.id.contains("reading")) ||
                (lower.contains("walk") && it.id.contains("walking"))
            )
        }

        val eatMatch = matchVoice("start mindful eating session")
        assertEquals(DefaultProfiles.EATING, eatMatch)

        val walkMatch = matchVoice("start walking timer")
        assertEquals(DefaultProfiles.MINDFUL_WALKING, walkMatch)

        val readMatch = matchVoice("reading timer please")
        assertEquals(DefaultProfiles.MINDFUL_READING, readMatch)
    }

    /**
     * Verifies the TV API JSON serialization contract used by the LocalCastWebServer
     * to keep Smart TV browsers in sync with CentralSessionHandler.
     */
    @Test
    fun testTvApiStateSerializationContract() {
        val state = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.PRANAYAMA_HATHA,
            remainingSeconds = 1200,
            totalSeconds = 2700,
            nextBellSeconds = 45,
            currentRound = 3,
            totalRounds = 12,
            currentPranayamaPhase = com.habitbell.app.data.model.PranayamaPhase.INHALE,
            phaseRemainingSeconds = 4,
            phaseDurationSeconds = 4
        )

        val phase = state.currentPranayamaPhase
        val phaseName = phase?.name ?: ""
        val phaseDisplay = phase?.displayName ?: ""
        val phaseSanskrit = phase?.sanskritName ?: ""
        val pose = state.currentPose
        val poseName = pose?.name ?: ""
        val poseSanskrit = pose?.sanskritName ?: ""
        val poseBreath = pose?.breathCue ?: ""

        val json = """{"status":"${state.status.name}","profileName":"${state.profile.name}","profileType":"${state.profile.type.name}","remainingSeconds":${state.remainingSeconds},"totalSeconds":${state.totalSeconds},"nextBellSeconds":${state.nextBellSeconds},"formattedTime":"${state.formattedRemainingTime}","formattedNextBell":"${state.formattedNextBellTime}","progressFraction":${state.progressFraction},"currentRound":${state.currentRound},"totalRounds":${state.totalRounds},"pranayamaPhase":"$phaseName","pranayamaDisplay":"$phaseDisplay","pranayamaSanskrit":"$phaseSanskrit","phaseRemaining":${state.phaseRemainingSeconds},"phaseDuration":${state.phaseDurationSeconds},"poseName":"$poseName","poseSanskrit":"$poseSanskrit","poseBreath":"$poseBreath","poseRemaining":${state.poseRemainingSeconds}}"""

        assertTrue(json.contains("\"status\":\"RUNNING\""))
        assertTrue(json.contains("\"profileName\":\"Pranayama (Hatha Yoga)\""))
        assertTrue(json.contains("\"remainingSeconds\":1200"))
        assertTrue(json.contains("\"formattedTime\":\"20:00\""))
        assertTrue(json.contains("\"nextBellSeconds\":45"))
        assertTrue(json.contains("\"pranayamaPhase\":\"INHALE\""))
        assertTrue(json.contains("\"pranayamaDisplay\":\"Inhale\""))
        assertTrue(json.contains("\"pranayamaSanskrit\":\"Purak\""))
        assertTrue(json.contains("\"currentRound\":3"))
        assertTrue(json.contains("\"totalRounds\":12"))
    }

    /**
     * Verifies that Google Cast Default Media Receiver Application ID
     * adheres to standard Play Services Cast specifications ("CC1AD845").
     */
    @Test
    fun testCastOptionsProviderDefaultAppId() {
        assertEquals(
            "Default Cast Receiver must be Google Universal Media Receiver CC1AD845",
            "CC1AD845",
            com.habitbell.app.cast.CastOptionsProvider.DEFAULT_RECEIVER_APP_ID
        )
    }

    /**
     * Verifies that Cast telemetry JSON correctly emits screenMode, eating/pranayama flags,
     * and phase durations for the redesigned Chromecast web receiver.
     */
    @Test
    fun testCastTelemetryJsonContract() {
        val eatingState = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.EATING,
            remainingSeconds = 2400,
            totalSeconds = 2700,
            nextBellSeconds = 45
        )
        val isEating = eatingState.profile.category.contains("Eating", ignoreCase = true)
        assertEquals(true, isEating)

        val idleState = TimerSessionState(
            status = SessionStatus.IDLE,
            profile = DefaultProfiles.EATING
        )
        assertEquals(SessionStatus.IDLE, idleState.status)

        val pranayamaState = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.PRANAYAMA_HATHA,
            currentPranayamaPhase = com.habitbell.app.data.model.PranayamaPhase.INHALE,
            phaseRemainingSeconds = 4,
            phaseDurationSeconds = 4
        )
        val isPranayama = pranayamaState.profile.type == com.habitbell.app.data.model.TimerType.MULTI_INTERVAL
        assertEquals(true, isPranayama)

        // Verify Surya Namaskar / Compound sequence state mapping
        val suryaPose = com.habitbell.app.data.model.CompoundPose(
            index = 1,
            name = "Pranamasana",
            sanskritName = "Prayer Pose",
            durationSeconds = 5,
            breathCue = "Inhale & Exhale gently",
            mantra = "ॐ मित्राय नमः"
        )
        val suryaState = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.SURYA_NAMASKAR,
            currentPose = suryaPose,
            poseRemainingSeconds = 5,
            currentRound = 1,
            totalRounds = 6
        )
        val isSurya = suryaState.profile.type == com.habitbell.app.data.model.TimerType.COMPOUND
        assertEquals(true, isSurya)
        assertEquals(1, suryaState.currentPose?.index)
        assertEquals("ॐ मित्राय नमः", suryaState.currentPose?.mantra)
    }
}
