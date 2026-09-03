package com.habitbell.app

import android.app.Application

/**
 * Global application entry point for the Habit Bell wellness operating system.
 *
 * Serves as the process-level context container for dependency initialization,
 * background services, notification channel setup, and lifecycle monitoring.
 */
class HabitBellApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Process-level initialization hooks
    }
}
