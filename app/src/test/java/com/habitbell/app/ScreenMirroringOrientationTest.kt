/**
 * # Screen Mirroring & TV Orientation Unit Test Suite
 *
 * ## Architectural Role & Component Relationships
 * Validates the core orientation switching and external display detection logic of the
 * Screen Mirroring subsystem:
 * - [com.habitbell.app.cast.ScreenOrientation]: Enum states ([ScreenOrientation.PORTRAIT],
 *   [ScreenOrientation.LANDSCAPE], [ScreenOrientation.AUTO]) and toggle transitions.
 * - [com.habitbell.app.cast.ScreenMirroringManager]: Pure orientation calculation algorithms
 *   and external display identifier validation.
 *
 * ## Concurrency & Lifecycle
 * Pure JUnit 4 unit tests executing synchronously in-memory on the JVM test runner.
 */
package com.habitbell.app

import com.habitbell.app.cast.ScreenMirroringManager
import com.habitbell.app.cast.ScreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying orientation toggle transitions, hardware display ID classification,
 * and TV mirroring state resolution.
 */
class ScreenMirroringOrientationTest {

    /**
     * Verifies that toggling [ScreenOrientation.PORTRAIT] transitions directly to [ScreenOrientation.LANDSCAPE].
     */
    @Test
    fun testScreenOrientationToggle_fromPortrait_returnsLandscape() {
        val next = ScreenOrientation.PORTRAIT.toggle()
        assertEquals(
            "Toggling from Portrait must produce Landscape for horizontal TV viewing",
            ScreenOrientation.LANDSCAPE,
            next
        )
    }

    /**
     * Verifies that toggling [ScreenOrientation.LANDSCAPE] transitions directly to [ScreenOrientation.PORTRAIT].
     */
    @Test
    fun testScreenOrientationToggle_fromLandscape_returnsPortrait() {
        val next = ScreenOrientation.LANDSCAPE.toggle()
        assertEquals(
            "Toggling from Landscape must produce Portrait for vertical TV/monitor viewing",
            ScreenOrientation.PORTRAIT,
            next
        )
    }

    /**
     * Verifies that toggling from [ScreenOrientation.AUTO] defaults into [ScreenOrientation.LANDSCAPE].
     */
    @Test
    fun testScreenOrientationToggle_fromAuto_returnsLandscape() {
        val next = ScreenOrientation.AUTO.toggle()
        assertEquals(
            "Toggling from Auto must default to Landscape for TV presentation",
            ScreenOrientation.LANDSCAPE,
            next
        )
    }

    /**
     * Verifies the pure orientation calculation algorithm when current target is [ScreenOrientation.PORTRAIT].
     */
    @Test
    fun testCalculateToggledOrientation_targetPortrait() {
        val next = ScreenMirroringManager.calculateToggledOrientation(
            currentTarget = ScreenOrientation.PORTRAIT,
            isCurrentlyLandscape = false
        )
        assertEquals(ScreenOrientation.LANDSCAPE, next)
    }

    /**
     * Verifies the pure orientation calculation algorithm when current target is [ScreenOrientation.LANDSCAPE].
     */
    @Test
    fun testCalculateToggledOrientation_targetLandscape() {
        val next = ScreenMirroringManager.calculateToggledOrientation(
            currentTarget = ScreenOrientation.LANDSCAPE,
            isCurrentlyLandscape = true
        )
        assertEquals(ScreenOrientation.PORTRAIT, next)
    }

    /**
     * Verifies the pure orientation calculation algorithm when target is [ScreenOrientation.AUTO]
     * and current physical layout is rendered in Portrait (isCurrentlyLandscape = false).
     */
    @Test
    fun testCalculateToggledOrientation_targetAuto_currentlyPortrait() {
        val next = ScreenMirroringManager.calculateToggledOrientation(
            currentTarget = ScreenOrientation.AUTO,
            isCurrentlyLandscape = false
        )
        assertEquals(
            "When in Auto and currently Portrait, toggle must produce Landscape",
            ScreenOrientation.LANDSCAPE,
            next
        )
    }

    /**
     * Verifies the pure orientation calculation algorithm when target is [ScreenOrientation.AUTO]
     * and current physical layout is rendered in Landscape (isCurrentlyLandscape = true).
     */
    @Test
    fun testCalculateToggledOrientation_targetAuto_currentlyLandscape() {
        val next = ScreenMirroringManager.calculateToggledOrientation(
            currentTarget = ScreenOrientation.AUTO,
            isCurrentlyLandscape = true
        )
        assertEquals(
            "When in Auto and currently Landscape, toggle must produce Portrait",
            ScreenOrientation.PORTRAIT,
            next
        )
    }

    /**
     * Verifies that the primary built-in device screen (Display.DEFAULT_DISPLAY = 0)
     * is correctly classified as NOT an external display.
     */
    @Test
    fun testIsExternalDisplay_defaultDisplayIsFalse() {
        val isExternal = ScreenMirroringManager.isExternalDisplay(0)
        assertFalse(
            "Display ID 0 (DEFAULT_DISPLAY) must not be classified as an external TV display",
            isExternal
        )
    }

    /**
     * Verifies that secondary display IDs (> 0) (e.g., HDMI, Miracast, Wi-Fi Display)
     * are correctly classified as external displays.
     */
    @Test
    fun testIsExternalDisplay_secondaryDisplayIsTrue() {
        assertTrue("Display ID 1 must be classified as external", ScreenMirroringManager.isExternalDisplay(1))
        assertTrue("Display ID 2 must be classified as external", ScreenMirroringManager.isExternalDisplay(2))
        assertTrue("Display ID 99 must be classified as external", ScreenMirroringManager.isExternalDisplay(99))
    }

    /**
     * Verifies exhaustive enum representation and values integrity.
     */
    @Test
    fun testScreenOrientationEnumValues() {
        val values = ScreenOrientation.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(ScreenOrientation.PORTRAIT))
        assertTrue(values.contains(ScreenOrientation.LANDSCAPE))
        assertTrue(values.contains(ScreenOrientation.AUTO))
    }
}
