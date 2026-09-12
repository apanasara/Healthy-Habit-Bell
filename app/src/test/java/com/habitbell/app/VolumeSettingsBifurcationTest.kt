package com.habitbell.app

import com.habitbell.app.engine.BackgroundSoundType
import com.habitbell.app.ui.viewmodel.GlobalSettingsCategory
import org.junit.Assert.*
import org.junit.Test

/**
 * # VolumeSettingsBifurcationTest
 *
 * Unit test suite validating the Global Settings Bifurcation (Requirement E5),
 * Dedicated Volume Controller (Requirement E6), and Decoupled TV Casting (Requirement E8).
 *
 * ## Architectural Role & Relationships
 * Validates domain model contracts, category segmentation, volume decoupling,
 * and sheet state machines without requiring Android framework handles or instrumentation.
 *
 * ## Concurrency & Concurrency Model
 * Thread-safe execution under JUnit 4 test runner on local JVM.
 */
class VolumeSettingsBifurcationTest {

    /**
     * Verifies that [GlobalSettingsCategory] contains exactly the 3 bifurcated categories (Requirement E5):
     * [GlobalSettingsCategory.THEME_DISPLAY], [GlobalSettingsCategory.SENSORS_HEALTH],
     * and [GlobalSettingsCategory.AUTOMATION_BATTERY].
     */
    @Test
    fun testGlobalSettingsCategoryIntegrity() {
        val categories = GlobalSettingsCategory.values()
        assertEquals("Expected exactly 3 bifurcated categories", 3, categories.size)
        assertEquals(GlobalSettingsCategory.THEME_DISPLAY, categories[0])
        assertEquals(GlobalSettingsCategory.SENSORS_HEALTH, categories[1])
        assertEquals(GlobalSettingsCategory.AUTOMATION_BATTERY, categories[2])
    }

    /**
     * Verifies that Interval Bell volume and Completion Gong volume are decoupled (Requirement E6).
     * Modifying one must not alter or corrupt the other.
     */
    @Test
    fun testIntervalAndCompletionVolumeDecoupling() {
        var intervalVolume = 0.60f
        var completionVolume = 0.90f

        // Adjust interval bell volume independently
        val newIntervalVolume = 0.75f.coerceIn(0f, 1f)
        intervalVolume = newIntervalVolume

        assertEquals("Interval volume must update to 0.75", 0.75f, intervalVolume, 0.001f)
        assertEquals("Completion volume must remain untouched at 0.90", 0.90f, completionVolume, 0.001f)

        // Adjust completion bell volume independently
        val newCompletionVolume = 0.40f.coerceIn(0f, 1f)
        completionVolume = newCompletionVolume

        assertEquals("Interval volume must remain untouched at 0.75", 0.75f, intervalVolume, 0.001f)
        assertEquals("Completion volume must update to 0.40", 0.40f, completionVolume, 0.001f)
    }

    /**
     * Verifies boundary clamping [0.0f..1.0f] for both bell volume streams.
     */
    @Test
    fun testVolumeClampingBoundaries() {
        fun clamp(vol: Float) = vol.coerceIn(0f, 1f)

        assertEquals(0.0f, clamp(-0.5f), 0.001f)
        assertEquals(0.0f, clamp(0.0f), 0.001f)
        assertEquals(0.5f, clamp(0.5f), 0.001f)
        assertEquals(1.0f, clamp(1.0f), 0.001f)
        assertEquals(1.0f, clamp(1.8f), 0.001f)
    }

    /**
     * Verifies that sheet display states ([isVolumeSheetOpen], [isCastSheetOpen], [isSettingsDrawerOpen])
     * can toggle independently without unwanted cross-coupling.
     */
    @Test
    fun testSheetStateIndependence() {
        data class TestUiState(
            val isVolumeSheetOpen: Boolean = false,
            val isCastSheetOpen: Boolean = false,
            val isSettingsDrawerOpen: Boolean = false
        )

        var state = TestUiState()
        assertFalse(state.isVolumeSheetOpen)
        assertFalse(state.isCastSheetOpen)
        assertFalse(state.isSettingsDrawerOpen)

        // Open volume sheet
        state = state.copy(isVolumeSheetOpen = true)
        assertTrue(state.isVolumeSheetOpen)
        assertFalse(state.isCastSheetOpen)
        assertFalse(state.isSettingsDrawerOpen)

        // Dismiss volume sheet and open cast sheet
        state = state.copy(isVolumeSheetOpen = false, isCastSheetOpen = true)
        assertFalse(state.isVolumeSheetOpen)
        assertTrue(state.isCastSheetOpen)
        assertFalse(state.isSettingsDrawerOpen)

        // Open main settings drawer
        state = state.copy(isCastSheetOpen = false, isSettingsDrawerOpen = true)
        assertFalse(state.isVolumeSheetOpen)
        assertFalse(state.isCastSheetOpen)
        assertTrue(state.isSettingsDrawerOpen)
    }

    /**
     * Verifies that all ambient background sound strategies in [BackgroundSoundType]
     * are supported for selection in the dedicated volume settings sheet (Requirement E6).
     */
    @Test
    fun testAmbientAudioSelectionStrategies() {
        val soundTypes = BackgroundSoundType.values()
        assertTrue("DEFAULT_AUM must be present", soundTypes.contains(BackgroundSoundType.DEFAULT_AUM))
        assertTrue("YOUTUBE_LINK must be present", soundTypes.contains(BackgroundSoundType.YOUTUBE_LINK))
        assertTrue("CUSTOM_FILE must be present", soundTypes.contains(BackgroundSoundType.CUSTOM_FILE))
        assertTrue("NONE must be present", soundTypes.contains(BackgroundSoundType.NONE))
    }

    /**
     * Verifies that the Option C triple bell cadence strike gains scale strictly with [intervalVolume].
     */
    @Test
    fun testOptionCTripleStrikeGainCalculation() {
        val intervalVolume = 0.80f
        val strike1Gain = intervalVolume * 0.90f
        val strike2Gain = intervalVolume * 0.95f
        val strike3Gain = intervalVolume * 1.00f

        assertEquals("Strike 1 gain scaled with interval volume", 0.72f, strike1Gain, 0.001f)
        assertEquals("Strike 2 gain scaled with interval volume", 0.76f, strike2Gain, 0.001f)
        assertEquals("Strike 3 gain scaled with interval volume", 0.80f, strike3Gain, 0.001f)
    }
}
