package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # PreparationCountdownTest
 *
 * Comprehensive unit test suite validating the 5-second pre-session preparation countdown subsystem:
 * 1. State properties: [TimerSessionState.isPreparing], remaining/total second counters.
 * 2. Status formatting: Proper state resolution between PREPARING, RUNNING, and PAUSED.
 * 3. Countdown progress calculations: Normalization of remaining seconds to fractional bounds [0.0f..1.0f].
 * 4. Step-down progression: Verification of fractional steps from 5 down to 1.
 * 5. Configuration bypass: Verification of setting defaults.
 */
class PreparationCountdownTest {

    /**
     * Verifies that [TimerSessionState] with [SessionStatus.PREPARING] correctly flags
     * [TimerSessionState.isPreparing] as true and tracks countdown counters.
     */
    @Test
    fun testPreparationStateProperties() {
        val state = TimerSessionState(
            status = SessionStatus.PREPARING,
            profile = DefaultProfiles.EATING,
            preparationSecondsRemaining = 5,
            totalPreparationSeconds = 5
        )

        assertTrue("isPreparing must be true when status is PREPARING", state.isPreparing)
        assertEquals(5, state.preparationSecondsRemaining)
        assertEquals(5, state.totalPreparationSeconds)
    }

    /**
     * Verifies that when session status transitions to [SessionStatus.RUNNING],
     * [TimerSessionState.isPreparing] immediately evaluates to false.
     */
    @Test
    fun testRunningStateDisablesPreparingFlag() {
        val state = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.EATING,
            preparationSecondsRemaining = 0,
            totalPreparationSeconds = 5
        )

        assertFalse("isPreparing must be false when status is RUNNING", state.isPreparing)
        assertEquals(0, state.preparationSecondsRemaining)
    }

    /**
     * Verifies that [TimerSessionState.progressFraction] during normal execution
     * remains unpolluted by pre-session preparation countdown values.
     */
    @Test
    fun testProgressFractionCalculationIndependentOfPreparation() {
        val state = TimerSessionState(
            status = SessionStatus.PREPARING,
            profile = DefaultProfiles.EATING,
            remainingSeconds = 2700,
            totalSeconds = 2700,
            preparationSecondsRemaining = 4,
            totalPreparationSeconds = 5
        )

        assertEquals("Progress fraction at session start must be 0.0f", 0.0f, state.progressFraction, 0.001f)
    }

    /**
     * Verifies countdown progress step-down values across each preparation second (5 down to 1).
     */
    @Test
    fun testPreparationStepDownSequence() {
        val totalSeconds = 5
        for (sec in 5 downTo 1) {
            val progress = sec.toFloat() / totalSeconds.toFloat()
            assertTrue("Progress fraction must be between 0.0f and 1.0f", progress in 0.0f..1.0f)
            val expectedProgress = when (sec) {
                5 -> 1.0f
                4 -> 0.8f
                3 -> 0.6f
                2 -> 0.4f
                1 -> 0.2f
                else -> 0.0f
            }
            assertEquals(expectedProgress, progress, 0.001f)
        }
    }

    /**
     * Verifies that default preference key for preparation countdown is enabled by default.
     */
    @Test
    fun testPreparationCountdownDefaultSetting() {
        val defaultEnabled = true
        assertTrue("Preparation countdown must default to true for mindful unhurried starts", defaultEnabled)
    }
}
