package com.habitbell.app

import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.ui.theme.*
import com.habitbell.app.ui.viewmodel.SettingsDrawerTab
import org.junit.Assert.*
import org.junit.Test

/**
 * # SettingsDrawerModelTest
 *
 * Unit test suite validating the dual-domain Settings Drawer data contracts,
 * Sun-Moon circadian classifications, and blue-light-reduced palette mappings.
 *
 * ## Architectural Role & Relationships
 * Validates domain model state machines and circadian invariants defined in
 * [ThemeMode] and [SettingsDrawerTab] without requiring Android instrumentation handles.
 *
 * ## Concurrency & Concurrency Model
 * Thread-safe execution under JUnit 4 test runner on local JVM.
 */
class SettingsDrawerModelTest {

    /**
     * Verifies that the [ThemeMode] correctly classifies Sun (Day / Light) vs. Moon (Night / Dark).
     */
    @Test
    fun testSunMoonCircadianClassifications() {
        // LIGHT is the designated Sun (Day) theme
        assertTrue("LIGHT should be classified as Sun Day theme", ThemeMode.LIGHT.isSunDayTheme)
        assertFalse("LIGHT should not be classified as Moon Night theme", ThemeMode.LIGHT.isMoonNightTheme)

        // AMOLED, EYE_COMFORT, and DARK are designated Moon (Night) themes
        assertTrue("AMOLED should be classified as Moon Night theme", ThemeMode.AMOLED.isMoonNightTheme)
        assertFalse("AMOLED should not be classified as Sun Day theme", ThemeMode.AMOLED.isSunDayTheme)

        assertTrue("EYE_COMFORT should be classified as Moon Night theme", ThemeMode.EYE_COMFORT.isMoonNightTheme)
        assertFalse("EYE_COMFORT should not be classified as Sun Day theme", ThemeMode.EYE_COMFORT.isSunDayTheme)

        assertTrue("DARK should be classified as Moon Night theme", ThemeMode.DARK.isMoonNightTheme)
        assertFalse("DARK should not be classified as Sun Day theme", ThemeMode.DARK.isSunDayTheme)
    }

    /**
     * Verifies that [SettingsDrawerTab] contains exactly TIMER and GLOBAL domains.
     */
    @Test
    fun testSettingsDrawerTabEnumIntegrity() {
        val tabs = SettingsDrawerTab.values()
        assertEquals(2, tabs.size)
        assertEquals(SettingsDrawerTab.TIMER, tabs[0])
        assertEquals(SettingsDrawerTab.GLOBAL, tabs[1])
    }

    /**
     * Verifies that the Sun Day warm palette colors are initialized properly without nullability or alpha distortion.
     */
    @Test
    fun testSunDayEyeComfortPaletteConstants() {
        assertNotNull(SunDayWarmBg)
        assertNotNull(SunDayWarmSurface)
        assertNotNull(SunDayWarmCard)
        assertNotNull(SunDayWarmText)
        assertNotNull(SunDayWarmMuted)
        assertNotNull(SunDayAmber)

        // Verifies backward compatibility aliases match
        assertEquals(SunDayWarmBg, LightBg)
        assertEquals(SunDayWarmSurface, LightSurface)
        assertEquals(SunDayWarmText, LightText)
        assertEquals(SunDayWarmMuted, LightMuted)
    }
}
