package com.habitbell.app.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class HabitBellCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return HabitBellCarScreen(carContext)
    }
}
