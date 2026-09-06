/**
 * Display Automation Subsystem Unit Test Suite
 *
 * Architectural Role:
 * Validates sensor fusion threshold algorithms, mathematical vector calculations
 * (Earth gravity Z-axis alignment, tilt angles, motion jerk deltas), optical pocket-mode
 * heuristics, and state machine transitions for the Unified Display Automation subsystem.
 *
 * Component Relationships:
 * - Tests: [com.habitbell.app.engine.DisplayAutomationManager]
 * - Validates: [com.habitbell.app.engine.DisplayCurtainState] and [com.habitbell.app.engine.DisplayCurtainMode]
 *
 * Concurrency & Lifecycle:
 * Pure JUnit 4 unit tests executing synchronously in memory on JVM test runners.
 */
package com.habitbell.app

import com.habitbell.app.engine.DisplayAutomationManager
import com.habitbell.app.engine.DisplayCurtainMode
import com.habitbell.app.engine.DisplayCurtainState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test specification for multi-sensor display automation logic and state management.
 */
class DisplayAutomationTest {

    /**
     * Verifies that flat surface gravity vector evaluation correctly detects horizontal resting state.
     * When resting flat screen-up, Earth gravity (+9.81 m/s²) aligns primarily with the Z-axis.
     */
    @Test
    fun testEvaluateFlatness_detectsRestingFlatDevice() {
        // Ideal flat table: z = 9.81 m/s², x = 0 m/s², y = 0 m/s²
        assertTrue(
            "Device with pure Z gravity must be classified as resting flat",
            DisplayAutomationManager.evaluateFlatness(x = 0.0f, y = 0.0f, z = 9.81f)
        )

        // Slight tabletop angle: z = 9.2 m/s², slight horizontal drift
        assertTrue(
            "Device within tolerance thresholds must be classified as resting flat",
            DisplayAutomationManager.evaluateFlatness(x = 1.5f, y = -1.2f, z = 9.2f)
        )

        // Handheld tilted angle: z = 6.0 m/s², vertical tilt y = 7.0 m/s²
        assertFalse(
            "Device tilted towards user must NOT be classified as resting flat",
            DisplayAutomationManager.evaluateFlatness(x = 0.5f, y = 7.0f, z = 6.0f)
        )

        // Sideways / Landscape handheld: x = 9.0 m/s²
        assertFalse(
            "Device held in landscape must NOT be classified as resting flat",
            DisplayAutomationManager.evaluateFlatness(x = 9.0f, y = 1.0f, z = 3.0f)
        )

        // Face down on table: z = -9.81 m/s²
        assertFalse(
            "Device resting face-down must NOT be classified as resting flat face-up",
            DisplayAutomationManager.evaluateFlatness(x = 0.0f, y = 0.0f, z = -9.81f)
        )
    }

    /**
     * Verifies that tilt towards user or sudden acceleration jerk triggers physical lift detection.
     */
    @Test
    fun testEvaluateLift_detectsPickupAndTiltGestures() {
        // Picked up from table and held upright: z drops to 5.5 m/s², y increases to 7.2 m/s²
        assertTrue(
            "Device tilted upright towards user face must trigger lift detection",
            DisplayAutomationManager.evaluateLift(x = 0.2f, y = 7.2f, z = 5.5f, jerkDelta = 0.2f)
        )

        // Sudden rapid upward jerk from surface (jerkDelta > 1.2 m/s²)
        assertTrue(
            "Sudden acceleration spike must trigger lift detection even before tilt angle settles",
            DisplayAutomationManager.evaluateLift(x = 0.1f, y = 1.0f, z = 9.0f, jerkDelta = 1.8f)
        )

        // Static on table with zero jerk
        assertFalse(
            "Device resting flat with zero jerk must NOT trigger lift detection",
            DisplayAutomationManager.evaluateLift(x = 0.0f, y = 0.1f, z = 9.81f, jerkDelta = 0.05f)
        )
    }

    /**
     * Verifies Pocket Mode optical heuristics combining proximity sensor and ambient lux.
     */
    @Test
    fun testEvaluatePocketMode_sensorFusionAndManualOverride() {
        // Real pocket condition: proximity sensor blocked (< 5 cm) AND dark ambient (< 10 lux)
        assertTrue(
            "Covered proximity sensor in dark enclosure must activate pocket mode",
            DisplayAutomationManager.evaluatePocketMode(
                proximityCovered = true,
                ambientLux = 1.5f,
                manualOverride = false
            )
        )

        // False positive prevention: Proximity covered (e.g. thumb near top bezel) but bright room (150 lux)
        assertFalse(
            "Covered proximity sensor under bright ambient illumination must NOT trigger pocket mode",
            DisplayAutomationManager.evaluatePocketMode(
                proximityCovered = true,
                ambientLux = 150.0f,
                manualOverride = false
            )
        )

        // In hand in a dark room: low lux (2 lux) but proximity unblocked
        assertFalse(
            "Dark room without proximity obstruction must NOT trigger pocket mode",
            DisplayAutomationManager.evaluatePocketMode(
                proximityCovered = false,
                ambientLux = 2.0f,
                manualOverride = false
            )
        )

        // Manual user override: always activates regardless of sensor hardware
        assertTrue(
            "Manual pocket override must engage blackout curtain regardless of sensor readings",
            DisplayAutomationManager.evaluatePocketMode(
                proximityCovered = false,
                ambientLux = 500.0f,
                manualOverride = true
            )
        )

        // User tapped screen or lifted device (temporarily awake): sensor obstruction does not re-blank during grace period
        assertFalse(
            "Temporarily awake device must NOT engage pocket blackout despite proximity obstruction",
            DisplayAutomationManager.evaluatePocketMode(
                proximityCovered = true,
                ambientLux = 1.5f,
                manualOverride = false,
                isTemporarilyAwake = true
            )
        )

        // User tapped screen or lifted device (temporarily awake): even manual override is released during grace period
        assertFalse(
            "Temporarily awake device must NOT engage pocket blackout even if manual override was set",
            DisplayAutomationManager.evaluatePocketMode(
                proximityCovered = false,
                ambientLux = 1.5f,
                manualOverride = true,
                isTemporarilyAwake = true
            )
        )
    }

    /**
     * Verifies DisplayCurtainState immutable data model and default values.
     */
    @Test
    fun testDisplayCurtainState_modelDefaultsAndTransformations() {
        val defaultState = DisplayCurtainState()
        assertFalse("Default curtain state must be inactive", defaultState.isActive)
        assertEquals("Default curtain mode must be NONE", DisplayCurtainMode.NONE, defaultState.mode)
        assertEquals("Default title must be empty", "", defaultState.title)
        assertNull("Default device name must be null", defaultState.deviceName)

        val carState = defaultState.copy(
            isActive = true,
            mode = DisplayCurtainMode.CAR_HUD,
            title = "🚗 Car HUD Active",
            deviceName = "Android Auto"
        )
        assertTrue(carState.isActive)
        assertEquals(DisplayCurtainMode.CAR_HUD, carState.mode)
        assertEquals("🚗 Car HUD Active", carState.title)
        assertEquals("Android Auto", carState.deviceName)

        val tvState = defaultState.copy(
            isActive = true,
            mode = DisplayCurtainMode.TV_CAST,
            title = "📺 Casting to Apple TV",
            deviceName = "Apple TV 4K"
        )
        assertTrue(tvState.isActive)
        assertEquals(DisplayCurtainMode.TV_CAST, tvState.mode)
        assertEquals("Apple TV 4K", tvState.deviceName)
    }

    /**
     * Verifies system constants for flat inactivity timeout and sensor thresholds.
     */
    @Test
    fun testSystemConstants() {
        assertEquals(
            "Flat surface inactivity grace period must be exactly 10 seconds",
            10,
            DisplayAutomationManager.FLAT_INACTIVITY_TIMEOUT_SECONDS
        )
        assertEquals(
            "Gravity flat Z threshold must be 8.8 m/s²",
            8.8f,
            DisplayAutomationManager.GRAVITY_FLAT_Z_THRESHOLD
        )
        assertEquals(
            "Gravity lift Z threshold must be 7.5 m/s²",
            7.5f,
            DisplayAutomationManager.GRAVITY_LIFT_Z_THRESHOLD
        )
        assertEquals(
            "Pocket lux ceiling must be 10.0 lux",
            10.0f,
            DisplayAutomationManager.POCKET_LUX_THRESHOLD
        )
    }
}
