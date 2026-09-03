package com.habitbell.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.ui.components.PocketOverlay
import com.habitbell.app.ui.screens.*
import com.habitbell.app.ui.theme.HabitBellTheme
import com.habitbell.app.ui.viewmodel.AppScreen
import com.habitbell.app.ui.viewmodel.HabitBellViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HabitBellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
            val profiles by viewModel.profiles.collectAsStateWithLifecycle()
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            val recentProfiles by viewModel.recentProfiles.collectAsStateWithLifecycle()
            val reminders by viewModel.reminders.collectAsStateWithLifecycle()
            val isPocketBlanking by viewModel.isPocketBlankingActive.collectAsStateWithLifecycle()

            // Hardware Display Mode: keep screen awake if session is active and display mode is on
            LaunchedEffect(sessionState.status, uiState.isDisplayMode) {
                val shouldKeepAwake = uiState.isDisplayMode && sessionState.status == SessionStatus.RUNNING
                viewModel.batteryOptimizer.applyScreenAwake(this@MainActivity, shouldKeepAwake)
            }

            HabitBellTheme(themeMode = uiState.selectedTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                profiles = profiles,
                                favorites = favorites,
                                recentProfiles = recentProfiles,
                                reminders = reminders,
                                currentTheme = uiState.selectedTheme,
                                isZenMode = uiState.isZenMode,
                                onSelectProfile = { profile ->
                                    viewModel.startProfileSession(profile)
                                },
                                onConfigureProfile = { profile ->
                                    viewModel.startProfileSession(profile)
                                    viewModel.openSettingsDrawer(true)
                                },
                                onToggleFavorite = { id ->
                                    viewModel.toggleFavorite(id)
                                },
                                onToggleZenMode = {
                                    viewModel.setZenMode(!uiState.isZenMode)
                                },
                                onCycleTheme = {
                                    val nextTheme = when (uiState.selectedTheme) {
                                        ThemeMode.AMOLED -> ThemeMode.EYE_COMFORT
                                        ThemeMode.EYE_COMFORT -> ThemeMode.DARK
                                        ThemeMode.DARK -> ThemeMode.LIGHT
                                        ThemeMode.LIGHT -> ThemeMode.AMOLED
                                    }
                                    viewModel.setTheme(nextTheme)
                                },
                                onCreateNewClick = {
                                    viewModel.navigateTo(AppScreen.CREATE_TIMER)
                                },
                                onOpenTVMode = { profile ->
                                    viewModel.startProfileSession(profile, openTVMode = true)
                                }
                            )
                        }

                        AppScreen.SESSION -> {
                            SessionScreen(
                                sessionState = sessionState,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onReset = { viewModel.resetSession() },
                                onOpenSettings = { viewModel.openSettingsDrawer(true) },
                                onExit = { viewModel.exitSessionToHome() },
                                onTriggerPocketMode = {
                                    viewModel.setPocketMode(!uiState.isPocketModeManual)
                                },
                                onOpenTVMode = {
                                    viewModel.navigateTo(AppScreen.TV_DASHBOARD)
                                }
                            )
                        }

                        AppScreen.TV_DASHBOARD -> {
                            TVDashboardScreen(
                                sessionState = sessionState,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onExitTVMode = { viewModel.navigateTo(AppScreen.SESSION) }
                            )
                        }

                        AppScreen.CREATE_TIMER -> {
                            CreateTimerScreen(
                                onSave = { profile ->
                                    viewModel.createCustomProfile(profile)
                                },
                                onCancel = { viewModel.navigateTo(AppScreen.HOME) }
                            )
                        }
                    }

                    // Settings Bottom Sheet Drawer
                    if (uiState.isSettingsDrawerOpen) {
                        SettingsDrawer(
                            profile = sessionState.profile,
                            currentTheme = uiState.selectedTheme,
                            isZenMode = uiState.isZenMode,
                            isPocketMode = uiState.isPocketModeManual,
                            isDisplayMode = uiState.isDisplayMode,
                            isAutoDim = uiState.isAutoDim,
                            bellVolume = uiState.bellVolume,
                            onDismiss = { viewModel.openSettingsDrawer(false) },
                            onThemeSelected = { viewModel.setTheme(it) },
                            onZenModeToggle = { viewModel.setZenMode(it) },
                            onPocketModeToggle = { viewModel.setPocketMode(it) },
                            onDisplayModeToggle = { viewModel.setDisplayMode(it) },
                            onAutoDimToggle = { viewModel.setAutoDim(it) },
                            onVolumeChange = { viewModel.setBellVolume(it) },
                            onTestBell = { viewModel.playTestBell() },
                            onUpdateTime = { total, interval ->
                                viewModel.updateActiveProfileTimes(total, interval)
                            },
                            onOpenTVMode = {
                                viewModel.openSettingsDrawer(false)
                                viewModel.navigateTo(AppScreen.TV_DASHBOARD)
                            }
                        )
                    }

                    // Hardware & Manual Pocket Mode Blanking (Pure OLED Black)
                    if (isPocketBlanking) {
                        PocketOverlay(
                            onDismiss = {
                                viewModel.setPocketMode(false)
                            }
                        )
                    }
                }
            }
        }
    }
}
