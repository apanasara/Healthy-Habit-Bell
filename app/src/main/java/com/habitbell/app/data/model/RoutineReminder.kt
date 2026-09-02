package com.habitbell.app.data.model

data class RoutineReminder(
    val id: String,
    val timeString: String,      // "08:00", "12:30", "17:00", "20:00"
    val title: String,           // "Water", "Lunch", "Pranayama", "Reiki"
    val profileId: String,
    val enabled: Boolean = true
)
