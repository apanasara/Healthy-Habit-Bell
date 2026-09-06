package com.habitbell.app.health

/**
 * # StepUpdate
 *
 * Immutable data transfer object capturing a discrete step measurement event.
 *
 * ## Architectural Role & Relationships
 * - Emitted by [StepDataSource] implementations.
 * - Consumed by [HealthStepManager] and forwarded to [com.habitbell.app.engine.TimerEngine].
 *
 * ## Concurrency & Thread Safety
 * Immutable data class; safe for concurrent read across all coroutine scopes and threads.
 *
 * @property sessionSteps Total steps accumulated strictly during the active walking timer session.
 * @property rawCumulativeSteps Cumulative hardware sensor step count since device boot (or total historical steps).
 * @property cadenceStepsPerMinute Instantaneous cadence (steps per minute) computed over a sliding temporal window.
 * @property timestampMillis Epoch timestamp in milliseconds when this step update was recorded.
 */
data class StepUpdate(
    val sessionSteps: Int,
    val rawCumulativeSteps: Long,
    val cadenceStepsPerMinute: Int,
    val timestampMillis: Long
)
