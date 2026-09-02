package com.habitbell.app.data.model

data class CompoundPose(
    val index: Int,
    val name: String,
    val sanskritName: String,
    val durationSeconds: Int,
    val breathCue: String
)

data class CompoundConfig(
    val poses: List<CompoundPose>,
    val targetRounds: Int
)
