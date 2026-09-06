package com.habitbell.app.health

import kotlinx.coroutines.flow.StateFlow

/**
 * # StepDataSource
 *
 * Contract abstraction for pedometer and step counting data providers.
 *
 * ## Architectural Role & Relationships
 * - Establishes unified lifecycle operations ([start], [pause], [resume], [stop], [reset])
 *   implemented by [HardwarePedometerProvider], [HealthConnectManager], and test simulators.
 * - Decouples [com.habitbell.app.engine.TimerEngine] from concrete sensor or cloud health APIs.
 *
 * ## Concurrency & Thread Safety
 * Implementations must dispatch updates safely to [stepFlow] and ensure thread-safe state emissions.
 */
interface StepDataSource {

    /** The health/hardware provider typology represented by this source. */
    val providerType: HealthProviderType

    /** Whether this source is available and supported on the current host hardware and OS. */
    val isAvailable: Boolean

    /** Continuous reactive stream emitting the latest step metrics for the active session. */
    val stepFlow: StateFlow<StepUpdate>

    /**
     * Commences active step monitoring.
     *
     * @param initialSessionSteps Optional baseline offset to resume an existing session.
     */
    fun start(initialSessionSteps: Int = 0)

    /**
     * Temporarily halts active step accumulation while maintaining baseline offsets.
     */
    fun pause()

    /**
     * Resumes step accumulation from paused state.
     */
    fun resume()

    /**
     * Terminates step tracking and detaches system hardware listeners.
     */
    fun stop()

    /**
     * Resets internal counters and baseline offsets back to zero.
     */
    fun reset()
}
