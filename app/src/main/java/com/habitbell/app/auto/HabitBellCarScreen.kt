package com.habitbell.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

class HabitBellCarScreen(carContext: CarContext) : Screen(carContext) {
    private var isPostureActive = false
    private var isDrivingCalmActive = false

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Posture & Spinal Alignment")
                .addText(if (isPostureActive) "Active • 15m gentle posture bell" else "Tap to start subtle driving posture check")
                .setOnClickListener {
                    isPostureActive = !isPostureActive
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Mindful Breath & Stress-Free Drive")
                .addText(if (isDrivingCalmActive) "Active • 20m calming breath chime" else "Tap to start driving mindfulness")
                .setOnClickListener {
                    isDrivingCalmActive = !isDrivingCalmActive
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Hydration & Rest Break")
                .addText("45m hydration reminders for long journeys")
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Habit Bell")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
