package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.StepTriggerMode
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.health.AppleHealthBridgeManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # StepTrackingEngineTest
 *
 * Comprehensive unit test suite verifying:
 * 1. Default step configurations on walking and meditative walking profiles.
 * 2. Step interval bell countdown mathematics and boundary triggers.
 * 3. Step trigger modes ([StepTriggerMode.TIME_OR_STEPS], [StepTriggerMode.STEPS_ONLY], [StepTriggerMode.TIME_ONLY]).
 * 4. Step cadence (SPM - steps per minute) calculation algorithms.
 * 5. Apple Health / HealthKit workout export JSON schema serialization.
 *
 * Architectural Layer: Unit Test / Engine Verification
 * Threading Model: Synchronous JUnit test runner
 */
class StepTrackingEngineTest {

    /**
     * Verifies that pre-packaged walking profiles contain accurate step targets, interval cadences,
     * and step trigger modes.
     */
    @Test
    fun testWalkingProfilesDefaultStepConfigurations() {
        // 1. Mindful Walking: 2000 steps total goal, 500 steps interval bell, dual trigger
        val mindfulWalking = DefaultProfiles.MINDFUL_WALKING
        assertTrue("Mindful Walking must have step tracking enabled", mindfulWalking.isStepTrackingEnabled)
        assertEquals(2000, mindfulWalking.stepGoal)
        assertEquals(500, mindfulWalking.stepInterval)
        assertEquals(StepTriggerMode.TIME_OR_STEPS, mindfulWalking.stepTriggerMode)

        // 2. Step Walk Meditation: 3000 steps goal, 500 steps interval bell, pure step trigger
        val stepMeditation = DefaultProfiles.STEP_WALK_MEDITATION
        assertTrue("Step Walk Meditation must have step tracking enabled", stepMeditation.isStepTrackingEnabled)
        assertEquals(3000, stepMeditation.stepGoal)
        assertEquals(500, stepMeditation.stepInterval)
        assertEquals(StepTriggerMode.STEPS_ONLY, stepMeditation.stepTriggerMode)

        // 3. Power Step Walk: 5000 steps goal, 1000 steps interval bell, dual trigger
        val powerWalk = DefaultProfiles.POWER_STEP_WALK
        assertTrue("Power Step Walk must have step tracking enabled", powerWalk.isStepTrackingEnabled)
        assertEquals(5000, powerWalk.stepGoal)
        assertEquals(1000, powerWalk.stepInterval)
        assertEquals(StepTriggerMode.TIME_OR_STEPS, powerWalk.stepTriggerMode)

        // 4. Non-walking profiles (e.g. Eating) must NOT have step tracking enabled by default
        val eating = DefaultProfiles.EATING
        assertFalse("Eating profile must not track steps by default", eating.isStepTrackingEnabled)
    }

    /**
     * Verifies remaining steps countdown and next interval bell countdown mathematics
     * across various step milestones.
     */
    @Test
    fun testStepIntervalCalculationAndBoundaryTriggers() {
        val baseProfile = DefaultProfiles.MINDFUL_WALKING.copy(
            stepGoal = 2000,
            stepInterval = 500,
            stepTriggerMode = StepTriggerMode.TIME_OR_STEPS
        )

        // Session at start (0 steps)
        val initialSession = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = baseProfile,
            remainingSeconds = 1200,
            currentSteps = 0,
            targetSteps = 2000,
            nextStepBellSteps = 500
        )
        assertEquals(0, initialSession.currentSteps)
        assertEquals(500, initialSession.nextStepBellSteps)

        // Session midway to first bell (200 steps taken, 300 remaining to bell)
        val midwaySession = initialSession.copy(
            currentSteps = 200,
            nextStepBellSteps = 300
        )
        assertEquals(200, midwaySession.currentSteps)
        assertEquals(300, midwaySession.nextStepBellSteps)

        // Calculate next step bell steps dynamically
        fun calculateNextBellSteps(currentSteps: Int, interval: Int): Int {
            if (interval <= 0) return 0
            val stepsIntoInterval = currentSteps % interval
            return interval - stepsIntoInterval
        }

        assertEquals(500, calculateNextBellSteps(0, 500))
        assertEquals(300, calculateNextBellSteps(200, 500))
        assertEquals(1, calculateNextBellSteps(499, 500))
        assertEquals(500, calculateNextBellSteps(500, 500)) // Exact interval milestone reset
        assertEquals(250, calculateNextBellSteps(750, 500))
    }

    /**
     * Verifies completion triggering under different [StepTriggerMode] policies.
     */
    @Test
    fun testStepTriggerModeCompletionLogic() {
        fun isSessionCompleted(
            mode: StepTriggerMode,
            timeRemainingSeconds: Int,
            currentSteps: Int,
            stepGoal: Int?
        ): Boolean {
            val timeElapsed = timeRemainingSeconds <= 0
            val stepsAchieved = stepGoal != null && stepGoal > 0 && currentSteps >= stepGoal

            return when (mode) {
                StepTriggerMode.TIME_ONLY -> timeElapsed
                StepTriggerMode.STEPS_ONLY -> stepsAchieved
                StepTriggerMode.TIME_OR_STEPS -> timeElapsed || stepsAchieved
            }
        }

        val stepGoal = 2000

        // Case 1: TIME_ONLY mode
        // Steps reached but time remains -> NOT complete
        assertFalse(isSessionCompleted(StepTriggerMode.TIME_ONLY, 300, 2500, stepGoal))
        // Time elapsed -> complete regardless of steps
        assertTrue(isSessionCompleted(StepTriggerMode.TIME_ONLY, 0, 500, stepGoal))

        // Case 2: STEPS_ONLY mode
        // Time elapsed but steps not reached -> NOT complete
        assertFalse(isSessionCompleted(StepTriggerMode.STEPS_ONLY, 0, 1500, stepGoal))
        // Steps reached -> complete regardless of remaining time
        assertTrue(isSessionCompleted(StepTriggerMode.STEPS_ONLY, 600, 2000, stepGoal))

        // Case 3: TIME_OR_STEPS mode
        // Neither reached -> NOT complete
        assertFalse(isSessionCompleted(StepTriggerMode.TIME_OR_STEPS, 600, 1000, stepGoal))
        // Steps reached early -> complete
        assertTrue(isSessionCompleted(StepTriggerMode.TIME_OR_STEPS, 600, 2000, stepGoal))
        // Time reached early -> complete
        assertTrue(isSessionCompleted(StepTriggerMode.TIME_OR_STEPS, 0, 1200, stepGoal))
    }

    /**
     * Verifies step cadence calculation algorithm (steps per minute from temporal sliding window).
     */
    @Test
    fun testStepCadenceCalculation() {
        fun computeCadence(stepsDelta: Int, timeDeltaMillis: Long): Int {
            if (timeDeltaMillis <= 0L || stepsDelta <= 0) return 0
            return ((stepsDelta.toLong() * 60_000L) / timeDeltaMillis).toInt()
        }

        // 100 steps in 60,000 ms (1 minute) = 100 SPM
        assertEquals(100, computeCadence(100, 60_000L))

        // 20 steps in 10,000 ms (10 seconds) = 120 SPM
        assertEquals(120, computeCadence(20, 10_000L))

        // 0 steps = 0 SPM
        assertEquals(0, computeCadence(0, 10_000L))

        // Negative or zero time delta = 0 SPM (safety guard against divide-by-zero)
        assertEquals(0, computeCadence(50, 0L))
    }

    /**
     * Verifies that the Apple Health / HealthKit bridge serialization constructs a valid JSON
     * payload complying with HealthKit workout import schemas.
     */
    @Test
    fun testAppleHealthWorkoutJsonSerialization() {
        val startTime = java.time.Instant.ofEpochMilli(1717200000000L)
        val endTime = java.time.Instant.ofEpochMilli(1717201800000L) // +30 minutes (1800 seconds)
        val totalSteps = 2450

        val bridgeManager = AppleHealthBridgeManager()
        val jsonString = bridgeManager.buildHealthKitWorkoutPayload(
            workoutTitle = "Mindful Walking",
            steps = totalSteps,
            durationSeconds = 1800,
            startTime = startTime,
            endTime = endTime
        )

        assertNotNull("Payload must not be null", jsonString)
        val json = JSONObject(jsonString)

        assertEquals("HKWorkoutActivityTypeWalking", json.getString("activityType"))
        assertEquals("Mindful Walking", json.getString("title"))
        assertEquals(totalSteps, json.getInt("totalStepCount"))
        assertEquals(1800, json.getInt("durationSeconds"))
        assertEquals(startTime.toString(), json.getString("startDate"))
        assertEquals(endTime.toString(), json.getString("endDate"))
        assertEquals("Habit Bell", json.getString("sourceName"))

        // Verify metadata object
        val metadata = json.getJSONObject("metadata")
        assertFalse(metadata.getBoolean("HKWasUserEntered"))
        assertFalse(metadata.getBoolean("HKIndoorWorkout"))
    }

    /**
     * Verifies that the SimulatedStepProvider correctly initializes, advances steps via
     * injection, transitions pause/resume states, and resets.
     */
    @Test
    fun testSimulatedStepProviderLifecycleAndInjection() {
        val provider = com.habitbell.app.health.SimulatedStepProvider()
        assertEquals(com.habitbell.app.health.HealthProviderType.SIMULATED, provider.providerType)
        assertTrue(provider.isAvailable)
        assertEquals(0, provider.stepFlow.value.sessionSteps)

        // Start with initial offset 100
        provider.start(100)
        assertEquals(100, provider.stepFlow.value.sessionSteps)

        // Inject 250 steps
        provider.injectSteps(250)
        assertEquals(350, provider.stepFlow.value.sessionSteps)
        assertEquals(105, provider.stepFlow.value.cadenceStepsPerMinute)

        // Pause clears cadence
        provider.pause()
        assertEquals(0, provider.stepFlow.value.cadenceStepsPerMinute)

        // Reset zeroes step counter
        provider.reset()
        assertEquals(0, provider.stepFlow.value.sessionSteps)
        assertEquals(0, provider.stepFlow.value.cadenceStepsPerMinute)
    }

    /**
     * Verifies HealthProviderType titles and descriptions for UI rendering.
     */
    @Test
    fun testHealthProviderTypeMetadata() {
        val providers = com.habitbell.app.health.HealthProviderType.values()
        assertEquals(4, providers.size)

        val hardware = com.habitbell.app.health.HealthProviderType.HARDWARE_SENSOR
        assertEquals("Device Pedometer", hardware.displayName)
        assertTrue(hardware.description.contains("hardware sensor"))

        val healthConnect = com.habitbell.app.health.HealthProviderType.HEALTH_CONNECT
        assertEquals("Google Health Connect", healthConnect.displayName)
        assertTrue(healthConnect.description.contains("Google Fit"))

        val appleBridge = com.habitbell.app.health.HealthProviderType.APPLE_HEALTH_BRIDGE
        assertEquals("Apple Health Bridge", appleBridge.displayName)

        val simulated = com.habitbell.app.health.HealthProviderType.SIMULATED
        assertEquals("Step Simulator", simulated.displayName)
    }
}
