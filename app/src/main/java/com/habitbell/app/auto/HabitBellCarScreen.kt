package com.habitbell.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

/**
 * Driver-optimized car screen adhering to Android for Cars design guidelines.
 *
 * Employs a glanceable [ListTemplate] presenting driving-safe wellness routines:
 * - Posture & Spinal Alignment checks (15m subtle interval bell).
 * - Mindful Breath & Stress-Free Drive (20m calming breath chime).
 * - Hydration & Rest Break guidance for road trips (45m reminder).
 *
 * @param carContext The vehicle automotive context.
 */
class HabitBellCarScreen(carContext: CarContext) : Screen(carContext) {

    /** State flag indicating whether the posture check routine is actively running. */
    private var isPostureActive = false

    /** State flag indicating whether the calm driving breath routine is actively running. */
    private var isDrivingCalmActive = false

    /**
     * Builds and returns the automotive UI template for the vehicle display.
     *
     * @return [Template] instance satisfying driver-distraction safety constraints.
     */
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // 1. Posture & Spinal Alignment item
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

        // 2. Mindful Breath & Calming Drive item
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

        // 3. Hydration & Rest Break item
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
