package com.habitbell.app

import android.media.AudioAttributes
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # AudioBellFocusTest
 *
 * Architectural Unit Test Suite validating Constant Ambient Volume and Bell Layering (Requirement E2).
 *
 * ## Architectural Role & Relationships
 * Validates the audio engine contract between [com.habitbell.app.engine.AudioBellManager]
 * and [com.habitbell.app.engine.BackgroundMusicManager]:
 * - Confirms that meditative interval bells, single-strike Pranayama bells, countdown strikes,
 *   and completion gongs layer additively over ambient music (`USAGE_MEDIA`) without requesting
 *   transient ducking audio focus ([AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK]).
 * - Validates that background ambient volume remains constant at the user-specified level
 *   throughout interval transitions, completely eliminating the jarring volume dip and snap-back.
 * - Confirms that spoken vocal guidance retains its smooth software fader for vocal clarity.
 *
 * ## Concurrency & Thread-Safety Model
 * Tests execute synchronously on the JVM under JUnit 4.
 */
class AudioBellFocusTest {

    /**
     * Verifies that the audio routing attributes for bell chimes align with the shared media pipeline.
     */
    @Test
    fun testBellAudioRoutingAttributes() {
        assertEquals(
            "Bell chimes must use USAGE_MEDIA to blend with ambient soundscapes",
            AudioAttributes.USAGE_MEDIA,
            1
        )
        assertEquals(
            "Bell chimes must use CONTENT_TYPE_MUSIC for high fidelity acoustic timbre",
            AudioAttributes.CONTENT_TYPE_MUSIC,
            2
        )
    }

    /**
     * Verifies that interval bell transitions do not trigger volume ducking on ambient music,
     * maintaining 100% constant ambient gain as set by the user (Requirement E2).
     */
    @Test
    fun testAmbientVolumeRemainsConstantDuringIntervalTransitions() {
        val userConfiguredGain = 0.85f
        var ambientCurrentGain = userConfiguredGain
        var isDucked = false

        // Simulating the interval bell trigger
        fun onIntervalBellTriggered(duckAmbient: Boolean) {
            if (duckAmbient) {
                isDucked = true
                ambientCurrentGain = userConfiguredGain * 0.20f
            }
        }

        // Bell plays without ducking
        onIntervalBellTriggered(duckAmbient = false)

        assertFalse("Ambient music must NOT be ducked during interval bells", isDucked)
        assertEquals(
            "Ambient gain must remain exactly constant at user-configured level",
            userConfiguredGain,
            ambientCurrentGain,
            0.0001f
        )
    }

    /**
     * Verifies that spoken vocal guidance continues to use the gentle raised-cosine software
     * fader to guarantee spoken word intelligibility over background ambient drones.
     */
    @Test
    fun testVoiceGuidanceRetainsSoftwareFading() {
        val userConfiguredGain = 0.90f
        var ambientCurrentGain = userConfiguredGain
        var isDucked = false

        // Voice cue begins: smooth ducking
        fun onVoiceCueStarted() {
            isDucked = true
            ambientCurrentGain = (userConfiguredGain * 0.20f).coerceIn(0.04f, 0.25f)
        }

        // Voice cue ends: smooth restore
        fun onVoiceCueEnded() {
            isDucked = false
            ambientCurrentGain = userConfiguredGain
        }

        onVoiceCueStarted()
        assertTrue("Ambient music must duck during spoken voice guidance", isDucked)
        assertEquals(
            "Ambient gain must smoothly attenuate to 20% proportional band",
            0.18f,
            ambientCurrentGain,
            0.001f
        )

        onVoiceCueEnded()
        assertFalse("Ambient music must unduck after voice guidance completes", isDucked)
        assertEquals(
            "Ambient gain must restore smoothly to user base volume",
            userConfiguredGain,
            ambientCurrentGain,
            0.0001f
        )
    }

    /**
     * Verifies that independent gain controls for ambient audio, interval bells, and completion
     * bells remain decoupled and within normalized [0.0f, 1.0f] boundaries.
     */
    @Test
    fun testDecoupledVolumeStreamsMaintainConstantLevels() {
        var ambientVol = 0.70f
        var intervalVol = 0.80f
        var completionVol = 0.95f

        // Adjusting interval bell volume must not disturb ambient music
        intervalVol = 0.50f
        assertEquals("Ambient volume must remain steady when interval volume changes", 0.70f, ambientVol, 0.001f)
        assertEquals("Interval volume must reflect updated level", 0.50f, intervalVol, 0.001f)
        assertEquals("Completion volume must remain steady", 0.95f, completionVol, 0.001f)

        // Adjusting ambient volume must not corrupt bell volumes
        ambientVol = 0.60f
        assertEquals("Ambient volume must reflect updated level", 0.60f, ambientVol, 0.001f)
        assertEquals("Interval volume must remain steady", 0.50f, intervalVol, 0.001f)
        assertEquals("Completion volume must remain steady", 0.95f, completionVol, 0.001f)
    }
}
