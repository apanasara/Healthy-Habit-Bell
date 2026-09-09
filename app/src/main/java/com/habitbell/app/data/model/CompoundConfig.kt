/**
 * # CompoundConfig
 *
 * Domain data models defining sequential compound routines such as Surya Namaskar and Reiki hand positions.
 *
 * ## Architectural Role & Component Relationships
 * Data layer component in `com.habitbell.app.data.model`:
 * - Embedded directly within [com.habitbell.app.data.model.TimerProfile] when `type == TimerType.COMPOUND`.
 * - Consumed by [com.habitbell.app.engine.TimerEngine] to drive 1Hz step transitions and round tracking.
 * - Rendered by [com.habitbell.app.ui.components.CompoundPoseCard] during active sessions.
 *
 * ## Concurrency & Thread Safety
 * Immutable data classes, completely thread-safe across UI, IO, and default dispatcher contexts.
 */
package com.habitbell.app.data.model

import com.habitbell.app.audio.VoiceCueMode

/**
 * Individual posture or procedural step within a sequential compound routine.
 *
 * @property index 1-based ordering index of the pose within the sequence (1..12).
 * @property name Primary colloquial or translated name of the posture (e.g., "Pranamasana").
 * @property sanskritName Traditional Sanskrit or technical designation (e.g., "Prayer Pose").
 * @property durationSeconds Duration held in this posture in seconds before the transition chime.
 * @property breathCue Accompanying breathing guidance synchronized with the transition (e.g. "Inhale & Exhale gently").
 * @property mantra Classical Solar Mantra associated with this posture (e.g., "ॐ मित्राय नमः").
 * @property voiceCueMode Active voice guidance delivery mode for this step.
 */
data class CompoundPose(
    val index: Int,
    val name: String,
    val sanskritName: String,
    val durationSeconds: Int,
    val breathCue: String,
    val mantra: String = "",
    val voiceCueMode: VoiceCueMode = VoiceCueMode.STEP_NAME
)

/**
 * Configuration aggregate for compound sequential routines (e.g., Yoga flows, Reiki hand positions).
 *
 * @property poses Ordered list of postures/steps to execute in sequence.
 * @property targetRounds Number of full sequence repetitions to perform.
 * @property speedPreset Active speed preset key ("slow", "moderate", "fast", "custom").
 * @property voiceCueMode Global voice cue mode applied across all steps in the routine.
 */
data class CompoundConfig(
    val poses: List<CompoundPose>,
    val targetRounds: Int,
    val speedPreset: String = "moderate",
    val voiceCueMode: VoiceCueMode = VoiceCueMode.STEP_NAME
)
