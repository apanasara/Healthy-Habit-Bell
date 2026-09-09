package com.habitbell.app.ui.model

/**
 * UI representation of a Surya Namaskar step.
 *
 * @property id Unique identifier of the step (primary key).
 * @property name Human‑readable name of the pose (e.g., "Pranamasana").
 * @property durationSeconds Duration for this step in seconds. Must be non‑negative.
 * @property isEnabled Whether the step is active in the routine.
 * @property voiceCueMode Voice cue playback mode for the step.
 * @property mantraEnabled Flag indicating if the mantra audio should be played for this step.
 * @property repetition Number of repetitions for the step; default is 1.
 * @property puraka Duration of inhalation (seconds) for breath‑control; optional, 0 if unused.
 * @property kumbhaka Duration of breath‑hold (seconds); optional, 0 if unused.
 * @property rekha Duration of exhalation (seconds); optional, 0 if unused.
 */
 data class StepUiModel(
    val id: Long,
    val name: String,
    val durationSeconds: Int = 0,
    val isEnabled: Boolean = true,
    val voiceCueMode: com.habitbell.app.audio.VoiceCueMode = com.habitbell.app.audio.VoiceCueMode.NONE,
    val mantraEnabled: Boolean = false,
    val repetition: Int = 1,
    val puraka: Int = 0,
    val kumbhaka: Int = 0,
    val rekha: Int = 0
 )