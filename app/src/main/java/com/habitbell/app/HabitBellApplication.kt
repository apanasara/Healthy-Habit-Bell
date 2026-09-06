package com.habitbell.app

import android.app.Application
import com.habitbell.app.engine.CentralSessionHandler

/**
 * Global application entry point for the Habit Bell wellness operating system.
 *
 * Serves as the process-level context container for dependency initialization,
 * background services, notification channel setup, and lifecycle monitoring.
 */
class HabitBellApplication : Application() {

    /** Process-level single session handler and media session orchestrator. */
    lateinit var sessionHandler: CentralSessionHandler
        private set

    companion object {
        /** Global application instance handle. */
        lateinit var instance: HabitBellApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionHandler = CentralSessionHandler.getInstance(this)
    }
}
