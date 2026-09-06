package com.habitbell.app.data.model

/**
 * # StepTriggerMode
 *
 * Operational trigger policy determining how step metrics and elapsed time interact
 * to evaluate session completion and boundary transitions.
 *
 * ## Architectural Role & Relationships
 * - Used by [TimerProfile] to specify session completion rules.
 * - Evaluated by [com.habitbell.app.engine.TimerEngine] during countdown and step arrival events.
 *
 * ## Lifecycle & Concurrency
 * Immutable enum instantiated at compile time; thread-safe across all dispatchers.
 */
enum class StepTriggerMode {
    /**
     * Dual-trigger mode: Session reaches completion when either the countdown time
     * expires OR the target step count is reached, whichever occurs first.
     *
     * Provides bounded session duration for scheduled walking meditations while
     * rewarding energetic pace when step goals are completed early.
     */
    TIME_OR_STEPS,

    /**
     * Step-exclusive mode: Session duration is open-ended and only completes when the
     * user achieves the configured target step count. Elapsed time is continuously
     * recorded for cadence and workout metrics.
     */
    STEPS_ONLY,

    /**
     * Time-exclusive mode: Countdown time strictly dictates session completion.
     * Step tracking is engaged for real-time cadence and logging, but does not
     * prematurely trigger the session completion gong.
     */
    TIME_ONLY
}
