package com.habitbell.app.data.default

import com.habitbell.app.data.model.RoutineReminder

/**
 * # DefaultReminders
 *
 * Pre-configured daily habit reminders spaced throughout morning, afternoon, evening, and night.
 *
 * ## Architectural Role & Component Relationships
 * Supplies foundational reminder schedule linked to default [DefaultProfiles] for [com.habitbell.app.data.repository.TimerRepository].
 *
 * ## Lifecycle & Thread Safety
 * Immutable singleton object safe for concurrent reads across background alarms and UI threads.
 */
object DefaultReminders {
    /**
     * Default list of routine reminders linking designated times to preset wellness profiles.
     */
    val ALL_REMINDERS = listOf(
        RoutineReminder("rem-1", "08:00", "Water & Hydration", DefaultProfiles.HYDRATION.id),
        RoutineReminder("rem-2", "12:30", "Mindful Lunch", DefaultProfiles.EATING.id),
        RoutineReminder("rem-3", "17:00", "Evening Pranayama", DefaultProfiles.PRANAYAMA_HATHA.id),
        RoutineReminder("rem-4", "20:00", "Night Reiki", DefaultProfiles.REIKI.id)
    )
}
