package com.habitbell.app.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto CarAppService entry point.
 *
 * Integrates with Android Automotive OS and Android Auto head units to provide driver-safe,
 * glanceable wellness routines (posture alignments, hydration checks, mindful breathing).
 */
class HabitBellCarAppService : CarAppService() {

    /**
     * Creates a host validator defining permitted car head-unit hosts.
     * Allows all certified hosts for compatibility with Android Auto emulators and OEM head units.
     *
     * @return [HostValidator] instance.
     */
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    /**
     * Instantiates a new car session for the connected vehicle dashboard.
     *
     * @return [HabitBellCarSession] managing the car screen lifecycle.
     */
    override fun onCreateSession(): Session {
        return HabitBellCarSession()
    }
}
