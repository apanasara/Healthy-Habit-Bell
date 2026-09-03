package com.habitbell.app.data.model

/**
 * Individual posture or procedural step within a sequential compound routine.
 *
 * @property index 1-based ordering index of the pose within the sequence.
 * @property name Primary colloquial or translated name of the posture (e.g., "Downward-Facing Dog").
 * @property sanskritName Traditional Sanskrit or technical designation (e.g., "Adho Mukha Svanasana").
 * @property durationSeconds Duration held in this posture in seconds before the transition chime.
 * @property breathCue Accompanying breathing guidance synchronized with the transition (e.g. "Inhale, arch gently").
 */
data class CompoundPose(
    val index: Int,
    val name: String,
    val sanskritName: String,
    val durationSeconds: Int,
    val breathCue: String
)

/**
 * Configuration aggregate for compound sequential routines (e.g., Yoga flows, Reiki hand positions).
 *
 * @property poses Ordered list of postures/steps to execute in sequence.
 * @property targetRounds Number of full sequence repetitions to perform.
 */
data class CompoundConfig(
    val poses: List<CompoundPose>,
    val targetRounds: Int
)
