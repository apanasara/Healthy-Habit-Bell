package com.habitbell.app.data.model

data class TimerProfile(
    val id: String,
    val name: String,
    val type: TimerType,
    val category: String,
    val iconName: String,
    val totalDurationSeconds: Int = 0,
    val intervalDurationSeconds: Int = 0,
    val bellPattern: BellPattern = BellPattern.THREE_BELL,
    val theme: ThemeMode = ThemeMode.AMOLED,
    val displayMode: Boolean = true,
    val pocketMode: Boolean = false,
    val isFavorite: Boolean = false,
    val pranayamaConfig: PranayamaConfig? = null,
    val compoundConfig: CompoundConfig? = null
)
