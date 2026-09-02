package com.habitbell.app.data.model

enum class PranayamaPhase(val displayName: String, val cue: String) {
    INHALE("Inhale", "Inhale deeply"),
    HOLD_IN("Hold", "Hold breath"),
    EXHALE("Exhale", "Exhale slowly"),
    HOLD_OUT("Rest", "Rest empty")
}

data class PranayamaStep(
    val phase: PranayamaPhase,
    val durationSeconds: Int
)

data class PranayamaConfig(
    val steps: List<PranayamaStep>,
    val targetRounds: Int
)
