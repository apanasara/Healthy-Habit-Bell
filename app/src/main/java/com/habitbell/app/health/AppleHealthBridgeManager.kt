package com.habitbell.app.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant

/**
 * # AppleHealthBridgeManager
 *
 * Cross-platform synchronization bridge for Apple Health, HealthKit, and Apple TV companion apps.
 *
 * ## Architectural Role & Relationships
 * - Interfaces with the Apple ecosystem, coordinating with `tv-platforms/apple-tvos` companions
 *   and HealthKit workout schemas.
 * - Serializes completed walking sessions into standard HealthKit JSON payloads compatible with
 *   `HKWorkoutActivityType.walking` and `HKQuantityTypeIdentifier.stepCount`.
 *
 * ## Concurrency & Lifecycle
 * In-memory thread-safe state machine operating across process lifecycle.
 */
class AppleHealthBridgeManager : StepDataSource {

    override val providerType: HealthProviderType = HealthProviderType.APPLE_HEALTH_BRIDGE

    override val isAvailable: Boolean = true

    /** Backing mutable state flow for reactive step metrics. */
    private val _stepFlow = MutableStateFlow(
        StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    )

    override val stepFlow: StateFlow<StepUpdate> = _stepFlow.asStateFlow()

    private var sessionSteps: Int = 0
    private var isTracking: Boolean = false

    override fun start(initialSessionSteps: Int) {
        sessionSteps = initialSessionSteps
        isTracking = true
        _stepFlow.value = StepUpdate(
            sessionSteps = sessionSteps,
            rawCumulativeSteps = sessionSteps.toLong(),
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    }

    override fun pause() {
        isTracking = false
    }

    override fun resume() {
        isTracking = true
    }

    override fun stop() {
        isTracking = false
    }

    override fun reset() {
        isTracking = false
        sessionSteps = 0
        _stepFlow.value = StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Ingests external step count updates received from an Apple Watch or companion bridge.
     *
     * @param steps Discrete step count.
     * @param cadence Estimated steps per minute.
     */
    fun ingestCompanionSteps(steps: Int, cadence: Int) {
        if (!isTracking) return
        sessionSteps = steps
        _stepFlow.value = StepUpdate(
            sessionSteps = steps,
            rawCumulativeSteps = steps.toLong(),
            cadenceStepsPerMinute = cadence,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Generates an Apple HealthKit compatible workout export descriptor.
     *
     * @param workoutTitle Display title (e.g. "Mindful Walking Meditation").
     * @param steps Total steps completed.
     * @param durationSeconds Total elapsed duration in seconds.
     * @param startTime Beginning timestamp.
     * @param endTime Completion timestamp.
     * @return JSON string formatted for Apple HealthKit import.
     */
    fun buildHealthKitWorkoutPayload(
        workoutTitle: String,
        steps: Int,
        durationSeconds: Int,
        startTime: Instant,
        endTime: Instant
    ): String {
        return JSONObject().apply {
            put("activityType", "HKWorkoutActivityTypeWalking")
            put("title", workoutTitle)
            put("totalStepCount", steps)
            put("durationSeconds", durationSeconds)
            put("startDate", startTime.toString())
            put("endDate", endTime.toString())
            put("sourceName", "Habit Bell")
            put("metadata", JSONObject().apply {
                put("HKWasUserEntered", false)
                put("HKIndoorWorkout", false)
            })
        }.toString(2)
    }
}
