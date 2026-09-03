package com.habitbell.app.data.model

/**
 * Execution topology classifying how a timer counts down and coordinates interval cues.
 */
enum class TimerType {
    /**
     * Continuous linear countdown of a single total duration with periodic interval chimes.
     * Examples: Mindful eating (30s bite bells), meditation, water intake intervals.
     */
    LINEAR,

    /**
     * Cyclic multi-interval countdown with distinct phases (e.g. Inhale, Hold In, Exhale, Hold Out).
     * Examples: Box breathing, 4-7-8 relaxation pranayama, Wim Hof cycles.
     */
    MULTI_INTERVAL,

    /**
     * Sequential step-by-step posture or action sequencer with custom names, durations, and cues.
     * Examples: 12-step Surya Namaskar yoga flows, 12 Reiki hand placement transitions.
     */
    COMPOUND
}
