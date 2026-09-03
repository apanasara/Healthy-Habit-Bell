package com.habitbell.app.data.model

/**
 * Scheduled daily habit reminder prompting mindful routines at designated times.
 *
 * @property id Unique identifier for the reminder (e.g. "rem-1").
 * @property timeString 24-hour military format time string (e.g. "08:00", "12:30", "17:00").
 * @property title Human-readable habit descriptor (e.g., "Water & Hydration", "Mindful Lunch").
 * @property profileId Identifier of the target [TimerProfile] to launch upon reminder activation.
 * @property enabled Boolean switch indicating if this reminder is currently active.
 */
data class RoutineReminder(
    val id: String,
    val timeString: String,
    val title: String,
    val profileId: String,
    val enabled: Boolean = true
)
