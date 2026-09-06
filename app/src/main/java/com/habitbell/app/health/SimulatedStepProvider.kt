package com.habitbell.app.health

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * # SimulatedStepProvider
 *
 * Deterministic synthetic step provider engineered for unit tests, emulators, and UI previews.
 *
 * ## Architectural Role & Relationships
 * - Implements [StepDataSource] to provide controllable step updates when physical sensors are absent.
 * - Simulates a natural walking pace (~100-110 steps per minute).
 *
 * ## Concurrency & Lifecycle
 * Managed via coroutine scope on [Dispatchers.Default]; cancelled upon [stop] or [reset].
 */
class SimulatedStepProvider : StepDataSource {

    override val providerType: HealthProviderType = HealthProviderType.SIMULATED

    override val isAvailable: Boolean = true

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var simulationJob: Job? = null

    private val _stepFlow = MutableStateFlow(
        StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    )

    override val stepFlow: StateFlow<StepUpdate> = _stepFlow.asStateFlow()

    private var currentSteps: Int = 0
    private var isPaused: Boolean = false

    override fun start(initialSessionSteps: Int) {
        currentSteps = initialSessionSteps
        isPaused = false
        _stepFlow.value = StepUpdate(
            sessionSteps = currentSteps,
            rawCumulativeSteps = currentSteps.toLong(),
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
        simulationJob?.cancel()
        simulationJob = scope.launch {
            // Emits ~1.8 steps per second (~108 steps per minute natural walking cadence)
            while (isActive) {
                delay(555L)
                if (!isPaused) {
                    currentSteps++
                    _stepFlow.value = StepUpdate(
                        sessionSteps = currentSteps,
                        rawCumulativeSteps = currentSteps.toLong(),
                        cadenceStepsPerMinute = 108,
                        timestampMillis = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    override fun pause() {
        isPaused = true
        _stepFlow.update { it.copy(cadenceStepsPerMinute = 0) }
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        simulationJob?.cancel()
        simulationJob = null
        isPaused = false
        _stepFlow.update { it.copy(cadenceStepsPerMinute = 0) }
    }

    override fun reset() {
        stop()
        currentSteps = 0
        _stepFlow.value = StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Manually advances step count by a discrete delta for testing boundary triggers.
     *
     * @param count Number of steps to increment synchronously.
     */
    fun injectSteps(count: Int) {
        currentSteps += count
        _stepFlow.value = StepUpdate(
            sessionSteps = currentSteps,
            rawCumulativeSteps = currentSteps.toLong(),
            cadenceStepsPerMinute = 105,
            timestampMillis = System.currentTimeMillis()
        )
    }
}
