package com.habitbell.app

import com.habitbell.app.data.model.PranayamaConfig
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.PranayamaStep
import com.habitbell.app.data.model.TimerProfile
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # PranayamaZeroStepAndCompletionTest
 *
 * Architectural Unit Test Suite validating Pranayama zero-second step skipping
 * and clean final-phase completion laws.
 *
 * ## Architectural Role & Relationships
 * - Validates [com.habitbell.app.data.model.PranayamaConfig.activeSteps] zero-duration phase filtering.
 * - Validates [com.habitbell.app.engine.TimerEngine] multi-interval tick progression, round boundaries,
 *   and terminal state preservation.
 *
 * ## Concurrency & Thread-Safety Model
 * Pure JUnit 4 tests executing synchronously on JVM test runners.
 */
class PranayamaZeroStepAndCompletionTest {

    /**
     * Verifies that [PranayamaConfig.activeSteps] strictly excludes any phase where duration is 0,
     * while preserving canonical 4-phase entries in [PranayamaConfig.steps].
     */
    @Test
    fun testActiveStepsFiltersZeroDurationPhases() {
        // Case 1: All 4 phases active (4:16:8:16)
        val configAll = PranayamaConfig(
            steps = listOf(
                PranayamaStep(PranayamaPhase.INHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_IN, 16),
                PranayamaStep(PranayamaPhase.EXHALE, 8),
                PranayamaStep(PranayamaPhase.HOLD_OUT, 16)
            ),
            targetRounds = 12
        )
        assertEquals("Canonical steps list must retain all 4 phases", 4, configAll.steps.size)
        assertEquals("Active steps must include all 4 phases when all > 0s", 4, configAll.activeSteps.size)

        // Case 2: Bahya Kumbhaka is 0s (4:16:8:0)
        val configNoBahya = configAll.withStepDurations(bahya = 0)
        assertEquals("Canonical steps list must retain all 4 phases", 4, configNoBahya.steps.size)
        assertEquals("Active steps must filter out 0s Bahya Kumbhaka", 3, configNoBahya.activeSteps.size)
        assertFalse("Active steps must not contain HOLD_OUT", configNoBahya.activeSteps.any { it.phase == PranayamaPhase.HOLD_OUT })
        assertEquals(PranayamaPhase.INHALE, configNoBahya.activeSteps[0].phase)
        assertEquals(PranayamaPhase.HOLD_IN, configNoBahya.activeSteps[1].phase)
        assertEquals(PranayamaPhase.EXHALE, configNoBahya.activeSteps[2].phase)

        // Case 3: Both Antar and Bahya Kumbhaka are 0s (4:0:8:0)
        val configPureBreath = configAll.withStepDurations(antar = 0, bahya = 0)
        assertEquals(4, configPureBreath.steps.size)
        assertEquals("Active steps must filter out both Kumbhakas", 2, configPureBreath.activeSteps.size)
        assertEquals(PranayamaPhase.INHALE, configPureBreath.activeSteps[0].phase)
        assertEquals(PranayamaPhase.EXHALE, configPureBreath.activeSteps[1].phase)

        // Case 4: Antar Kumbhaka is 0s, Bahya is active (4:0:8:16)
        val configNoAntar = configAll.withStepDurations(antar = 0, bahya = 16)
        assertEquals(4, configNoAntar.steps.size)
        assertEquals("Active steps must filter out 0s Antar Kumbhaka", 3, configNoAntar.activeSteps.size)
        assertFalse("Active steps must not contain HOLD_IN", configNoAntar.activeSteps.any { it.phase == PranayamaPhase.HOLD_IN })
        assertEquals(PranayamaPhase.INHALE, configNoAntar.activeSteps[0].phase)
        assertEquals(PranayamaPhase.EXHALE, configNoAntar.activeSteps[1].phase)
        assertEquals(PranayamaPhase.HOLD_OUT, configNoAntar.activeSteps[2].phase)
    }

    /**
     * Verifies that when Bahya Kumbhaka has duration > 0s (e.g. 4:4:4:4), the multi-round session
     * concludes strictly and cleanly on Bahya Kumbhaka without spilling into Puraka.
     */
    @Test
    fun testPranayamaCycleWithBahyaKumbhakaEndsAtBahyaWithoutPurakSpill() {
        val config = PranayamaConfig(
            steps = listOf(
                PranayamaStep(PranayamaPhase.INHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_IN, 4),
                PranayamaStep(PranayamaPhase.EXHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_OUT, 4)
            ),
            targetRounds = 2,
            isVoiceGuidanceEnabled = false
        )
        val profile = TimerProfile(
            id = "test-pranayama-4x4",
            name = "Test Sama Vritti",
            type = TimerType.MULTI_INTERVAL,
            category = "Breathwork",
            iconName = "self_improvement",
            pranayamaConfig = config
        )

        val engine = TimerEngine()
        engine.loadProfile(profile)

        // Initial state verification
        assertEquals(SessionStatus.IDLE, engine.state.value.status)
        assertEquals(32, engine.state.value.totalSeconds)
        assertEquals(32, engine.state.value.remainingSeconds)
        assertEquals(1, engine.state.value.currentRound)
        assertEquals(2, engine.state.value.totalRounds)
        assertEquals(PranayamaPhase.INHALE, engine.state.value.currentPranayamaPhase)
        assertEquals(4, engine.state.value.phaseRemainingSeconds)

        // Track executed phases and durations
        val phaseHistory = mutableListOf<Pair<PranayamaPhase, Int>>()
        var lastPhase = engine.state.value.currentPranayamaPhase

        // Simulate tick-by-tick execution (32 ticks total)
        for (second in 1..32) {
            engine.tickOneSecond()
            val state = engine.state.value
            if (state.currentPranayamaPhase != lastPhase && state.currentPranayamaPhase != null) {
                phaseHistory.add(state.currentPranayamaPhase to state.currentRound)
                lastPhase = state.currentPranayamaPhase
            }
            if (state.status == SessionStatus.COMPLETED) {
                break
            }
        }

        val finalState = engine.state.value
        assertEquals("Session must complete at tick 32", SessionStatus.COMPLETED, finalState.status)
        assertEquals("Remaining seconds must be 0", 0, finalState.remainingSeconds)
        assertEquals("Phase remaining seconds must be 0", 0, finalState.phaseRemainingSeconds)
        assertEquals("Session must conclude cleanly on Bahya Kumbhaka (HOLD_OUT)", PranayamaPhase.HOLD_OUT, finalState.currentPranayamaPhase)
        assertEquals("Current round must be pinned at targetRounds (2), never incrementing to 3", 2, finalState.currentRound)
    }

    /**
     * Verifies that when Bahya Kumbhaka is 0s (e.g. 4:4:4:0), the multi-round session:
     * 1. Completely skips Bahya Kumbhaka (never visits or displays it).
     * 2. Concludes strictly and cleanly on Rechaka (EXHALE) at the end of the final round.
     * 3. Never spills into Puraka or Bahya Kumbhaka upon session completion.
     */
    @Test
    fun testPranayamaZeroBahyaKumbhakaEndsAtRechakaWithoutPurakSpill() {
        val config = PranayamaConfig(
            steps = listOf(
                PranayamaStep(PranayamaPhase.INHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_IN, 4),
                PranayamaStep(PranayamaPhase.EXHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_OUT, 0)
            ),
            targetRounds = 2,
            isVoiceGuidanceEnabled = false
        )
        val profile = TimerProfile(
            id = "test-pranayama-no-bahya",
            name = "Test 3-Phase Breathwork",
            type = TimerType.MULTI_INTERVAL,
            category = "Breathwork",
            iconName = "self_improvement",
            pranayamaConfig = config
        )

        val engine = TimerEngine()
        engine.loadProfile(profile)

        // Initial state verification: total duration must be (4 + 4 + 4 + 0) * 2 = 24 seconds
        assertEquals(24, engine.state.value.totalSeconds)
        assertEquals(24, engine.state.value.remainingSeconds)
        assertEquals(PranayamaPhase.INHALE, engine.state.value.currentPranayamaPhase)

        val observedPhases = mutableListOf<PranayamaPhase>()

        // Simulate tick-by-tick execution (24 ticks total)
        for (second in 1..24) {
            engine.tickOneSecond()
            val state = engine.state.value
            state.currentPranayamaPhase?.let { observedPhases.add(it) }
            if (state.status == SessionStatus.COMPLETED) {
                break
            }
        }

        // Verify HOLD_OUT was NEVER observed at any second
        assertFalse("Bahya Kumbhaka (HOLD_OUT) must never execute when configured to 0s", observedPhases.contains(PranayamaPhase.HOLD_OUT))

        val finalState = engine.state.value
        assertEquals("Session must reach COMPLETED status", SessionStatus.COMPLETED, finalState.status)
        assertEquals(0, finalState.remainingSeconds)
        assertEquals(0, finalState.phaseRemainingSeconds)
        assertEquals("Session must conclude cleanly on Rechaka (EXHALE)", PranayamaPhase.EXHALE, finalState.currentPranayamaPhase)
        assertEquals("Current round must remain pinned at targetRounds (2)", 2, finalState.currentRound)
    }

    /**
     * Verifies that when both Antar and Bahya Kumbhaka are 0s (e.g. 4:0:4:0):
     * 1. Only Puraka and Rechaka alternate.
     * 2. Neither Kumbhaka phase is ever executed.
     * 3. Total session duration matches exactly (4 + 4) * 3 = 24 seconds across 3 rounds.
     * 4. Concludes cleanly on Rechaka.
     */
    @Test
    fun testPranayamaBothKumbhakasZeroSkipsBothAndAlternatesCleanly() {
        val config = PranayamaConfig(
            steps = listOf(
                PranayamaStep(PranayamaPhase.INHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_IN, 0),
                PranayamaStep(PranayamaPhase.EXHALE, 4),
                PranayamaStep(PranayamaPhase.HOLD_OUT, 0)
            ),
            targetRounds = 3,
            isVoiceGuidanceEnabled = false
        )
        val profile = TimerProfile(
            id = "test-pranayama-2-phase",
            name = "Test Pure Inhale Exhale",
            type = TimerType.MULTI_INTERVAL,
            category = "Breathwork",
            iconName = "self_improvement",
            pranayamaConfig = config
        )

        val engine = TimerEngine()
        engine.loadProfile(profile)

        assertEquals(24, engine.state.value.totalSeconds)

        val observedPhases = mutableListOf<PranayamaPhase>()
        var ticksExecuted = 0

        for (second in 1..24) {
            engine.tickOneSecond()
            ticksExecuted++
            val state = engine.state.value
            state.currentPranayamaPhase?.let { observedPhases.add(it) }
            if (state.status == SessionStatus.COMPLETED) {
                break
            }
        }

        assertEquals("Must execute exactly 24 ticks for 3 rounds of 8s", 24, ticksExecuted)
        assertFalse("Antar Kumbhaka must never execute", observedPhases.contains(PranayamaPhase.HOLD_IN))
        assertFalse("Bahya Kumbhaka must never execute", observedPhases.contains(PranayamaPhase.HOLD_OUT))

        val finalState = engine.state.value
        assertEquals(SessionStatus.COMPLETED, finalState.status)
        assertEquals(PranayamaPhase.EXHALE, finalState.currentPranayamaPhase)
        assertEquals(3, finalState.currentRound)
    }
}
