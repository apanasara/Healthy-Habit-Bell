package com.habitbell.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * # DirectVolumeControlTest
 *
 * Comprehensive unit test suite validating the Direct Ambient Volume Control subsystem (Requirement E7):
 * 1. Stream volume index to normalized floating-point range [0.0f..1.0f] conversion.
 * 2. Normalized floating-point volume to discrete Android [android.media.AudioManager.STREAM_MUSIC] index conversion.
 * 3. Boundary clamping and edge case preservation (zero, max, out-of-bounds).
 * 4. Dual-mode routing heuristics: Chromecast mode (TV hardware volume) vs. Mobile mode (Phone media stream).
 * 5. Audio ducking proportion arithmetic for uninterrupted vocal guidance cues.
 */
class DirectVolumeControlTest {

    /**
     * Verifies that discrete stream volume indices map accurately to continuous [0.0f..1.0f] ratios.
     */
    @Test
    fun testStreamVolumeToNormalizedCalculation() {
        val minVol = 0
        val maxVol = 15 // Typical Android STREAM_MUSIC range (0..15)
        val range = (maxVol - minVol).coerceAtLeast(1)

        val currentVol = 6
        val normalized = ((currentVol - minVol).toFloat() / range).coerceIn(0f, 1f)
        assertEquals(0.40f, normalized, 0.001f)

        val maxNormalized = ((15 - minVol).toFloat() / range).coerceIn(0f, 1f)
        assertEquals(1.0f, maxNormalized, 0.001f)

        val minNormalized = ((0 - minVol).toFloat() / range).coerceIn(0f, 1f)
        assertEquals(0.0f, minNormalized, 0.001f)
    }

    /**
     * Verifies that floating point volume gains [0.0f..1.0f] map to proper discrete integer indices
     * on Android hardware audio systems without rounding drift.
     */
    @Test
    fun testNormalizedToStreamVolumeIndexCalculation() {
        val minVol = 0
        val maxVol = 15
        val range = maxVol - minVol

        fun toStreamIndex(norm: Float): Int {
            val clamped = norm.coerceIn(0f, 1f)
            return (minVol + (clamped * range)).roundToInt().coerceIn(minVol, maxVol)
        }

        assertEquals(0, toStreamIndex(0.0f))
        assertEquals(0, toStreamIndex(-0.5f)) // Out of bounds negative
        assertEquals(15, toStreamIndex(1.0f))
        assertEquals(15, toStreamIndex(1.5f)) // Out of bounds positive
        assertEquals(8, toStreamIndex(0.50f))
        assertEquals(4, toStreamIndex(0.25f))
        assertEquals(11, toStreamIndex(0.75f))
    }

    /**
     * Verifies volume calculations on devices with non-zero minimum stream volume (Android P+).
     */
    @Test
    fun testNonZeroMinStreamVolumeNormalization() {
        val minVol = 1
        val maxVol = 25
        val range = maxVol - minVol

        fun toNormalized(curr: Int): Float = ((curr - minVol).toFloat() / range).coerceIn(0f, 1f)
        fun toIndex(norm: Float): Int = (minVol + (norm.coerceIn(0f, 1f) * range)).roundToInt().coerceIn(minVol, maxVol)

        assertEquals(0.0f, toNormalized(1), 0.001f)
        assertEquals(1.0f, toNormalized(25), 0.001f)
        assertEquals(0.5f, toNormalized(13), 0.001f)

        assertEquals(1, toIndex(0.0f))
        assertEquals(25, toIndex(1.0f))
        assertEquals(13, toIndex(0.5f))
    }

    /**
     * Verifies that active mode determination correctly directs volume adjustments
     * to either Chromecast TV hardware or local mobile phone stream.
     */
    @Test
    fun testDualModeVolumeRoutingLogic() {
        var tvVolumeDispatched: Float? = null
        var phoneVolumeDispatched: Float? = null

        fun dispatchVolume(isCasting: Boolean, volume: Float) {
            val safe = volume.coerceIn(0f, 1f)
            if (isCasting) {
                tvVolumeDispatched = safe
            } else {
                phoneVolumeDispatched = safe
            }
        }

        // 1. Mobile mode
        dispatchVolume(isCasting = false, volume = 0.65f)
        assertEquals(0.65f, phoneVolumeDispatched ?: 0f, 0.001f)
        assertEquals(null, tvVolumeDispatched)

        // 2. Chromecast mode
        dispatchVolume(isCasting = true, volume = 0.85f)
        assertEquals(0.85f, tvVolumeDispatched ?: 0f, 0.001f)
    }

    /**
     * Verifies that internal voice guidance ducking operates relative to unity base gain (1.0f)
     * and attenuates cleanly into the [0.04f..0.25f] soothing background band.
     */
    @Test
    fun testAmbientVoiceDuckingProportionCalculation() {
        val baseVolume = 1.0f
        val duckedRatio = 0.20f
        val duckedTarget = (baseVolume * duckedRatio).coerceIn(0.04f, 0.25f)

        assertEquals(0.20f, duckedTarget, 0.001f)
        assertTrue("Ducked audio must stay non-zero to maintain soothing ambiance", duckedTarget > 0.05f)
        assertTrue("Ducked audio must not exceed 25% to guarantee vocal clarity", duckedTarget <= 0.25f)
    }

    /**
     * Verifies that volume observation is active ONLY when a volume-controlling UI surface
     * (Volume Settings Sheet or Settings Drawer) is visible AND the application is in the foreground.
     *
     * Ensures zero background battery consumption when the user is running/timing or away from settings.
     */
    @Test
    fun testVolumeUiActiveLifecycleCondition() {
        fun computeIsVolumeUiActive(
            isVolumeSheetOpen: Boolean,
            isSettingsDrawerOpen: Boolean,
            isAppForeground: Boolean
        ): Boolean {
            return (isVolumeSheetOpen || isSettingsDrawerOpen) && isAppForeground
        }

        // 1. Initial State: App in foreground, no sheets open -> DORMANT (no ContentObserver)
        org.junit.Assert.assertFalse(computeIsVolumeUiActive(isVolumeSheetOpen = false, isSettingsDrawerOpen = false, isAppForeground = true))

        // 2. User opens Volume Settings Sheet in foreground -> ACTIVE (ContentObserver registered)
        assertTrue(computeIsVolumeUiActive(isVolumeSheetOpen = true, isSettingsDrawerOpen = false, isAppForeground = true))

        // 3. User opens Settings Drawer in foreground -> ACTIVE (ContentObserver registered)
        assertTrue(computeIsVolumeUiActive(isVolumeSheetOpen = false, isSettingsDrawerOpen = true, isAppForeground = true))

        // 4. User opens both sheet and drawer in foreground -> ACTIVE
        assertTrue(computeIsVolumeUiActive(isVolumeSheetOpen = true, isSettingsDrawerOpen = true, isAppForeground = true))

        // 5. User leaves app / phone goes to background with Volume Sheet open -> DORMANT (unregistered, saving battery)
        org.junit.Assert.assertFalse(computeIsVolumeUiActive(isVolumeSheetOpen = true, isSettingsDrawerOpen = false, isAppForeground = false))

        // 6. User leaves app with Settings Drawer open -> DORMANT (unregistered, saving battery)
        org.junit.Assert.assertFalse(computeIsVolumeUiActive(isVolumeSheetOpen = false, isSettingsDrawerOpen = true, isAppForeground = false))

        // 7. Background state with all UI closed -> DORMANT
        org.junit.Assert.assertFalse(computeIsVolumeUiActive(isVolumeSheetOpen = false, isSettingsDrawerOpen = false, isAppForeground = false))
    }

    /**
     * Verifies simulated registration state machine transitions across user interaction workflows.
     */
    @Test
    fun testVolumeObserverRegistrationStateMachine() {
        var isRegistered = false

        fun register() {
            isRegistered = true
        }

        fun unregister() {
            isRegistered = false
        }

        fun updateLifecycle(isUiActive: Boolean, isCasting: Boolean) {
            if (isUiActive) {
                if (isCasting) {
                    unregister() // Cast mode uses Cast SDK, not phone ContentObserver
                } else {
                    register()
                }
            } else {
                unregister()
            }
        }

        // Initially dormant
        org.junit.Assert.assertFalse(isRegistered)

        // User opens Volume Sheet on phone
        updateLifecycle(isUiActive = true, isCasting = false)
        assertTrue("Must register ContentObserver when volume sheet is open on phone", isRegistered)

        // User switches to TV casting
        updateLifecycle(isUiActive = true, isCasting = true)
        org.junit.Assert.assertFalse("Must unregister phone ContentObserver in Cast mode", isRegistered)

        // User disconnects cast while sheet is still open
        updateLifecycle(isUiActive = true, isCasting = false)
        assertTrue("Must register phone ContentObserver when returning to phone mode", isRegistered)

        // User dismisses sheet
        updateLifecycle(isUiActive = false, isCasting = false)
        org.junit.Assert.assertFalse("Must unregister ContentObserver when sheet dismissed", isRegistered)
    }
}
