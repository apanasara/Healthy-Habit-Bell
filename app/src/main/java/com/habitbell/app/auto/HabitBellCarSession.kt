package com.habitbell.app.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.habitbell.app.engine.CentralSessionHandler

/**
 * Manages the active Android Auto vehicle session lifecycle.
 *
 * Dispatches car screen creation when the vehicle head unit establishes communication,
 * and coordinates vehicle display automation state with [CentralSessionHandler].
 */
class HabitBellCarSession : Session() {

    /**
     * Constructs the primary driver screen template and registers automotive connection status.
     *
     * @param intent Launch intent from the automotive host.
     * @return Initial [HabitBellCarScreen] instance.
     */
    override fun onCreateScreen(intent: Intent): Screen {
        val sessionHandler = CentralSessionHandler.getInstance(carContext)
        sessionHandler.displayAutomationManager.setCarConnected(true, "Android Auto")

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                sessionHandler.displayAutomationManager.setCarConnected(false)
                sessionHandler.bluetoothDisconnectionManager.onCarDisconnected()
            }
        })

        return HabitBellCarScreen(carContext)
    }
}
