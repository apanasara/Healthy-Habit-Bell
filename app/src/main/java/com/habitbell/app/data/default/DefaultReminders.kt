package com.habitbell.app.data.default

import com.habitbell.app.data.model.RoutineReminder

object DefaultReminders {
    val ALL_REMINDERS = listOf(
        RoutineReminder("rem-1", "08:00", "Water & Hydration", DefaultProfiles.HYDRATION.id),
        RoutineReminder("rem-2", "12:30", "Mindful Lunch", DefaultProfiles.EATING.id),
        RoutineReminder("rem-3", "17:00", "Evening Pranayama", DefaultProfiles.PRANAYAMA_BOX.id),
        RoutineReminder("rem-4", "20:00", "Night Reiki", DefaultProfiles.REIKI.id)
    )
}
