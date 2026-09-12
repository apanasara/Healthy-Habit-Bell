package com.habitbell.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.habitbell.app.engine.CentralSessionHandler
import com.habitbell.app.engine.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * # HabitBellCarScreen
 *
 * Driver-optimized automotive screen adhering strictly to Android for Cars design guidelines.
 *
 * ## Architectural Role & Session Synchronization
 * Directly observes and commands the process-level [CentralSessionHandler].
 * Any action triggered from this screen (starting posture alignment, pausing breathwork)
 * immediately synchronizes across:
 * - The vehicle instrument cluster / HUD ([android.support.v4.media.session.MediaSessionCompat])
 * - The connected mobile handset display
 * - Paired Wear OS smartwatches
 * - Local Smart TV dashboards
 *
 * @param carContext The vehicle automotive context.
 */
class HabitBellCarScreen(carContext: CarContext) : Screen(carContext) {

    /** Authoritative process-level session handler. */
    private val sessionHandler = CentralSessionHandler.getInstance(carContext)

    /** Background coroutine job observing session countdowns and invalidating template. */
    private var stateObserverJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                stateObserverJob?.cancel()
                stateObserverJob = lifecycleScope.launch {
                    sessionHandler.sessionState.collect {
                        invalidate()
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                stateObserverJob?.cancel()
                stateObserverJob = null
            }
        })
    }

    /**
     * Builds and returns the automotive UI template for the vehicle display.
     *
     * @return [Template] instance satisfying driver-distraction safety constraints.
     */
    override fun onGetTemplate(): Template {
        val sessionState = sessionHandler.sessionState.value
        val isRunning = sessionState.status == SessionStatus.RUNNING || sessionState.status == SessionStatus.PREPARING
        val isPaused = sessionState.status == SessionStatus.PAUSED
        val activeProfileId = sessionState.profile.id

        val listBuilder = ItemList.Builder()

        // 1. Posture & Spinal Alignment item
        val isPostureActive = (isRunning || isPaused) && activeProfileId == "posture"
        val postureText = when {
            isPostureActive && sessionState.status == SessionStatus.PREPARING -> "Get Ready • ${sessionState.preparationSecondsRemaining}s • Tap to pause"
            isPostureActive && isRunning -> "Active • ${sessionState.formattedRemainingTime} remaining • Tap to pause"
            isPostureActive && isPaused -> "Paused • ${sessionState.formattedRemainingTime} remaining • Tap to resume"
            else -> "5m subtle interval bell • Tap to start"
        }
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Posture & Spinal Alignment")
                .addText(postureText)
                .setOnClickListener {
                    if (isPostureActive) {
                        sessionHandler.togglePlayPause()
                    } else {
                        sessionHandler.startProfileById("posture")
                    }
                }
                .build()
        )

        // 2. Mindful Breath & Calming Drive item
        val isBreathActive = (isRunning || isPaused) && (activeProfileId == "breathing" || activeProfileId == "box-breathing")
        val breathText = when {
            isBreathActive && sessionState.status == SessionStatus.PREPARING -> "Get Ready • ${sessionState.preparationSecondsRemaining}s • Tap to pause"
            isBreathActive && isRunning -> "Active • ${sessionState.formattedRemainingTime} remaining • Tap to pause"
            isBreathActive && isPaused -> "Paused • ${sessionState.formattedRemainingTime} remaining • Tap to resume"
            else -> "4m calm breath chime • Tap to start"
        }
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Mindful Breath & Stress-Free Drive")
                .addText(breathText)
                .setOnClickListener {
                    if (isBreathActive) {
                        sessionHandler.togglePlayPause()
                    } else {
                        sessionHandler.startProfileById("breathing")
                    }
                }
                .build()
        )

        // 3. Mindful Eating / Break item
        val isEatingActive = (isRunning || isPaused) && activeProfileId.contains("eating")
        val eatingText = when {
            isEatingActive && sessionState.status == SessionStatus.PREPARING -> "Get Ready • ${sessionState.preparationSecondsRemaining}s • Tap to pause"
            isEatingActive && isRunning -> "Active • ${sessionState.formattedRemainingTime} remaining • Tap to pause"
            isEatingActive && isPaused -> "Paused • ${sessionState.formattedRemainingTime} remaining • Tap to resume"
            else -> "1m gentle interval chime • Tap to start"
        }
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Mindful Eating & Break")
                .addText(eatingText)
                .setOnClickListener {
                    if (isEatingActive) {
                        sessionHandler.togglePlayPause()
                    } else {
                        sessionHandler.startProfileById("eating")
                    }
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle(if (isRunning) "Habit Bell (${sessionState.formattedRemainingTime})" else "Habit Bell")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
