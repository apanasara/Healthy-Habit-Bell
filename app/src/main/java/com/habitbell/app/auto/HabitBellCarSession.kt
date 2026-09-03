package com.habitbell.app.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Manages the active Android Auto vehicle session lifecycle.
 *
 * Dispatches car screen creation when the vehicle head unit establishes communication.
 */
class HabitBellCarSession : Session() {

    /**
     * Constructs the primary driver screen template.
     *
     * @param intent Launch intent from the automotive host.
     * @return Initial [HabitBellCarScreen] instance.
     */
    override fun onCreateScreen(intent: Intent): Screen {
        return HabitBellCarScreen(carContext)
    }
}
