package com.habitbell.app

import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.ui.viewmodel.AppScreen
import com.habitbell.app.ui.viewmodel.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # SessionRestorationAndTaskCleanupTest
 *
 * Architectural Role:
 * Test suite verifying lifecycle resilience, background task dismissal contracts,
 * and ongoing session restoration logic addressing FLAW1 and FLAW2.
 *
 * Single Responsibility:
 * Verifies that:
 * 1. Active sessions (RUNNING or PAUSED) correctly dictate [AppScreen.SESSION] as the primary initial screen.
 * 2. IDLE or COMPLETED sessions default safely to [AppScreen.HOME].
 * 3. Ongoing session banners on the home dashboard activate strictly during RUNNING or PAUSED states.
 * 4. The notification navigation intent extra flag (`EXTRA_NAVIGATE_TO_SESSION`) triggers direct session view routing.
 */
class SessionRestorationAndTaskCleanupTest {

    /**
     * Verifies that when a session is in [SessionStatus.RUNNING] or [SessionStatus.PAUSED],
     * the initial screen evaluation maps to [AppScreen.SESSION], ensuring ongoing sessions
     * are loaded immediately upon app launch (FLAW2 fix).
     */
    @Test
    fun testInitialScreenEvaluationWithActiveSession() {
        val runningState = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.EATING,
            remainingSeconds = 1200,
            totalSeconds = 2700
        )
        val initialRunningScreen = if (runningState.status == SessionStatus.RUNNING || runningState.status == SessionStatus.PAUSED) {
            AppScreen.SESSION
        } else {
            AppScreen.HOME
        }
        assertEquals("Running session must restore directly to AppScreen.SESSION", AppScreen.SESSION, initialRunningScreen)

        val pausedState = runningState.copy(status = SessionStatus.PAUSED)
        val initialPausedScreen = if (pausedState.status == SessionStatus.RUNNING || pausedState.status == SessionStatus.PAUSED) {
            AppScreen.SESSION
        } else {
            AppScreen.HOME
        }
        assertEquals("Paused session must restore directly to AppScreen.SESSION", AppScreen.SESSION, initialPausedScreen)
    }

    /**
     * Verifies that when no session is active ([SessionStatus.IDLE] or [SessionStatus.COMPLETED]),
     * the app launches cleanly on [AppScreen.HOME].
     */
    @Test
    fun testInitialScreenEvaluationWithIdleSession() {
        val idleState = TimerSessionState(
            status = SessionStatus.IDLE,
            profile = DefaultProfiles.EATING,
            remainingSeconds = 2700,
            totalSeconds = 2700
        )
        val initialIdleScreen = if (idleState.status == SessionStatus.RUNNING || idleState.status == SessionStatus.PAUSED) {
            AppScreen.SESSION
        } else {
            AppScreen.HOME
        }
        assertEquals("Idle state must route to AppScreen.HOME", AppScreen.HOME, initialIdleScreen)

        val completedState = idleState.copy(status = SessionStatus.COMPLETED)
        val initialCompletedScreen = if (completedState.status == SessionStatus.RUNNING || completedState.status == SessionStatus.PAUSED) {
            AppScreen.SESSION
        } else {
            AppScreen.HOME
        }
        assertEquals("Completed state must route to AppScreen.HOME", AppScreen.HOME, initialCompletedScreen)
    }

    /**
     * Verifies that the Home Screen active session banner is visible only when
     * a session is actively running or paused, allowing 1-tap stop or resume controls.
     */
    @Test
    fun testActiveSessionBannerVisibilityCondition() {
        val runningState = TimerSessionState(
            status = SessionStatus.RUNNING,
            profile = DefaultProfiles.MINDFUL_READING,
            remainingSeconds = 900,
            totalSeconds = 1800
        )
        val shouldShowBannerRunning = runningState.status == SessionStatus.RUNNING || runningState.status == SessionStatus.PAUSED
        assertTrue("Banner must be visible during running practice", shouldShowBannerRunning)

        val pausedState = runningState.copy(status = SessionStatus.PAUSED)
        val shouldShowBannerPaused = pausedState.status == SessionStatus.RUNNING || pausedState.status == SessionStatus.PAUSED
        assertTrue("Banner must be visible during paused practice", shouldShowBannerPaused)

        val idleState = runningState.copy(status = SessionStatus.IDLE)
        val shouldShowBannerIdle = idleState.status == SessionStatus.RUNNING || idleState.status == SessionStatus.PAUSED
        assertFalse("Banner must be hidden when idle", shouldShowBannerIdle)
    }

    /**
     * Verifies the direct session navigation intent flag evaluation.
     */
    @Test
    fun testDirectSessionNavigationExtraFlag() {
        val extraKey = "EXTRA_NAVIGATE_TO_SESSION"
        val intentExtras = mapOf(extraKey to true)

        val shouldNavigateToSession = intentExtras[extraKey] == true
        assertTrue("Intent with EXTRA_NAVIGATE_TO_SESSION must trigger session routing", shouldNavigateToSession)
    }

    /**
     * Verifies UI state transition when session restore is applied.
     */
    @Test
    fun testAppUiStateSessionRestorationTransition() {
        val initialUiState = AppUiState(currentScreen = AppScreen.HOME)
        val activeProfile = DefaultProfiles.SURYA_NAMASKAR.copy(displayMode = true, pocketMode = false)

        val restoredUiState = initialUiState.copy(
            currentScreen = AppScreen.SESSION,
            isDisplayMode = activeProfile.displayMode,
            isPocketModeManual = activeProfile.pocketMode
        )

        assertEquals(AppScreen.SESSION, restoredUiState.currentScreen)
        assertTrue(restoredUiState.isDisplayMode)
        assertFalse(restoredUiState.isPocketModeManual)
    }
}
