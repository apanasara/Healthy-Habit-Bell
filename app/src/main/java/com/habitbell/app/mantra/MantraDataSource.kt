package com.habitbell.app.mantra

import com.habitbell.app.data.model.MantraCounterConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * # MantraDataSource
 *
 * Pluggable abstraction contract defining an input provider capable of capturing,
 * analyzing, and emitting mantra recitation events.
 *
 * ## Architectural Role & Component Relationships
 * - Implemented by [AcousticMantraSensorProvider] for microphone DSP analysis.
 * - Implemented by [ManualTapMantraProvider] for tactile screen tap counting.
 * - Implemented by [SimulatedMantraProvider] for deterministic automated testing.
 * - Ingested by [MantraCountManager] as the polymorphic data layer.
 *
 * ## Lifecycle & Thread Safety
 * Implementations must manage hardware handles safely, releasing audio hardware in [stop]
 * and emitting thread-safe immutable events via [inputFlow].
 */
interface MantraDataSource {

    /** The physical or synthetic modality of this provider. */
    val inputSourceType: MantraInputSourceType

    /** Whether required hardware permissions and resources are available. */
    val isAvailable: Boolean

    /** Reactive stream emitting primitive recitation events and audio metrics. */
    val inputFlow: StateFlow<MantraInputEvent>

    /**
     * Initializes and starts data capture with the specified configuration.
     *
     * @param config Configuration governing technique, thresholds, and durations.
     */
    fun start(config: MantraCounterConfig)

    /**
     * Pauses data capture, suspending background sampling loops without resetting state.
     */
    fun pause()

    /**
     * Resumes data capture from a paused state.
     */
    fun resume()

    /**
     * Terminates data capture and releases all held hardware handles ([android.media.AudioRecord]).
     */
    fun stop()

    /**
     * Clears internal sliding buffers, timers, and state machines back to clean baseline.
     */
    fun reset()

    /**
     * Manually registers a single bead advance event (used for touch tap overrides).
     */
    fun registerManualBead()
}
