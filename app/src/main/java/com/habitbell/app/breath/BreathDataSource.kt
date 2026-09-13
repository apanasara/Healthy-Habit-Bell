package com.habitbell.app.breath

import com.habitbell.app.data.model.BreathCounterConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * # BreathDataSource
 *
 * Contract abstraction for breath stroke counting and acoustic sensor data providers.
 *
 * ## Architectural Role & Relationships
 * - Establishes unified lifecycle operations ([start], [pause], [resume], [stop], [reset])
 *   implemented by [AcousticBreathSensorProvider], [ManualTapBreathProvider], and [SimulatedBreathProvider].
 * - Decouples [BreathCountManager] from concrete audio hardware handles.
 * - Streams primitive [BreathInputEvent] items into [BreathCountManager] for authoritative aggregation.
 *
 * ## Concurrency & Thread Safety
 * Implementations must dispatch updates safely to [inputFlow] across coroutine dispatchers.
 */
interface BreathDataSource {

    /** The input provider typology represented by this source. */
    val inputSourceType: BreathInputSourceType

    /** Whether this source is available and supported on current host hardware and permissions. */
    val isAvailable: Boolean

    /** Continuous reactive stream emitting the latest raw breath inputs for the active session. */
    val inputFlow: StateFlow<BreathInputEvent>

    /**
     * Commences active monitoring with the specified [config].
     *
     * @param config Target breath counter configuration governing technique and thresholds.
     */
    fun start(config: BreathCounterConfig)

    /**
     * Temporarily halts active monitoring and releases or pauses hardware listeners.
     */
    fun pause()

    /**
     * Resumes monitoring from paused state.
     */
    fun resume()

    /**
     * Terminates breath monitoring and completely releases system audio resources.
     */
    fun stop()

    /**
     * Resets internal metrics and baseline offsets back to zero.
     */
    fun reset()

    /**
     * Registers a single manual stroke trigger (used for touch tap mode or testing).
     */
    fun registerManualStroke()
}
