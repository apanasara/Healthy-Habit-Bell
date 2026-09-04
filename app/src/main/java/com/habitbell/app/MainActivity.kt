package com.habitbell.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.ui.components.PocketOverlay
import com.habitbell.app.ui.screens.*
import com.habitbell.app.ui.theme.HabitBellTheme
import com.habitbell.app.ui.viewmodel.AppScreen
import com.habitbell.app.ui.viewmodel.HabitBellViewModel

/**
 * Main host activity for Habit Bell's Jetpack Compose presentation layer.
 *
 * Responsibilities:
 * 1. **Window Insets**: Enables edge-to-edge immersive rendering.
 * 2. **Compose Root**: Hosts screen navigation transitions between Home, Session, TV Dashboard, and Create Timer.
 * 3. **Hardware Display Coordination**: Dynamically binds `FLAG_KEEP_SCREEN_ON` via [HabitBellViewModel.batteryOptimizer].
 * 4. **Hardware Pocket Blanking**: Renders the pure black [PocketOverlay] when proximity sensors detect pocketing.
 * 5. **Voice & Assistant Intents**: Decodes Google Assistant voice commands (`ACTION_SET_TIMER`, deep links).
 * 6. **SAF Audio Picking**: Launches system file picker for custom ambient audio tracks and requests persistent URI permissions.
 */
class MainActivity : ComponentActivity() {

    /** Shared ViewModel instance scoped to this Activity. */
    private val viewModel: HabitBellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            // Collect reactive state streams with lifecycle awareness to prevent unnecessary background recomposition
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
            val profiles by viewModel.profiles.collectAsStateWithLifecycle()
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            val recentProfiles by viewModel.recentProfiles.collectAsStateWithLifecycle()
            val reminders by viewModel.reminders.collectAsStateWithLifecycle()
            val isPocketBlanking by viewModel.isPocketBlankingActive.collectAsStateWithLifecycle()
            val isDisplayDimmed by viewModel.isDisplayDimmed.collectAsStateWithLifecycle()

            // System file picker contract for selecting local audio files for ambient soundscapes
            val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    val fileName = try {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                        }
                    } catch (_: Exception) { null } ?: uri.lastPathSegment ?: "Custom Audio"

                    // Cache locally to app internal storage so it permanently becomes default Aum track
                    try {
                        val internalFile = java.io.File(filesDir, "custom_aum.mp3")
                        contentResolver.openInputStream(uri)?.use { input ->
                            internalFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Exception) {}

                    viewModel.setBgMusicCustomUri(uri.toString(), fileName)
                }
            }

            // Keep screen awake dynamically while session is running and Display Mode is enabled
            LaunchedEffect(sessionState.status, uiState.isDisplayMode) {
                val shouldKeepAwake = uiState.isDisplayMode && sessionState.status == SessionStatus.RUNNING
                viewModel.batteryOptimizer.applyScreenAwake(this@MainActivity, shouldKeepAwake)
            }

            // Dynamically modulate hardware screen brightness based on the Display Mode dimming lifecycle
            LaunchedEffect(isDisplayDimmed) {
                viewModel.batteryOptimizer.setScreenBrightness(this@MainActivity, isDisplayDimmed)
            }

            // Distraction-free full-screen immersion: dynamically hide system status bar
            // (time clock, battery percentage, app notification icons, and network indicators)
            // whenever the user is on an active timer session screen (SESSION or TV_DASHBOARD)
            val isTimerScreen = uiState.currentScreen == AppScreen.SESSION || uiState.currentScreen == AppScreen.TV_DASHBOARD
            LaunchedEffect(isTimerScreen) {
                setStatusBarHidden(isTimerScreen)
            }

            HabitBellTheme(themeMode = uiState.selectedTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background YouTube player attached to Window hierarchy with full viewport dimensions for uninterrupted audio
                    AndroidView(
                        factory = { ctx ->
                            viewModel.bgMusicManager.getOrCreateWebView(ctx).apply {
                                isFocusable = false
                                isClickable = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Navigation routing based on active AppScreen
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
                                },
                                onUserInteraction = {
                                    viewModel.userInteractionWake()
                                }
                            )
                        }

                        AppScreen.TV_DASHBOARD -> {
                            TVDashboardScreen(
                                sessionState = sessionState,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onExitTVMode = { viewModel.exitSessionToHome() }
                            )
                        }

                        AppScreen.CREATE_TIMER -> {
                            CreateTimerScreen(
                                onCancel = { viewModel.navigateTo(AppScreen.HOME) },
                                onSave = { customProfile ->
                                    viewModel.createCustomProfile(customProfile)
                                }
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
                            bellStyle = uiState.bellStyle,
                            onBellStyleSelected = { viewModel.setBellStyle(it) },
                            onTestOptionC = { viewModel.playOptionCPreview() },
                            onTestGong = { viewModel.playGongPreview() },
                            onStartQuickDemo = { viewModel.startQuickDemoSession() },
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
                            },
                            tvCastUrl = viewModel.getTvCastUrl(),
                            isBgMusicEnabled = uiState.isBgMusicEnabled,
                            bgMusicType = uiState.bgMusicType,
                            bgMusicCustomName = uiState.bgMusicCustomName,
                            bgMusicYouTubeUrl = uiState.bgMusicYouTubeUrl,
                            bgMusicVolume = uiState.bgMusicVolume,
                            onBgMusicToggle = { viewModel.setBgMusicEnabled(it) },
                            onBgMusicTypeSelected = { viewModel.setBgMusicType(it) },
                            onPickCustomAudio = { audioPickerLauncher.launch("audio/*") },
                            onBgMusicYouTubeUrlChange = { viewModel.setBgMusicYouTubeUrl(it) },
                            onBgMusicVolumeChange = { viewModel.setBgMusicVolume(it) },
                            onPreviewBgMusic = { viewModel.previewBgMusic(it) }
                        )
                    }

                    // Hardware & Manual Pocket Mode Blanking (Pure OLED Black #000000)
                    if (isPocketBlanking) {
                        PocketOverlay(
                            onDismiss = {
                                viewModel.setPocketMode(false)
                            }
                        )
                    }

                    // System Back button interceptor
                    androidx.activity.compose.BackHandler(enabled = uiState.currentScreen != AppScreen.HOME || uiState.isSettingsDrawerOpen) {
                        if (uiState.isSettingsDrawerOpen) {
                            viewModel.openSettingsDrawer(false)
                        } else {
                            viewModel.exitSessionToHome()
                        }
                    }
                }
            }
        }

        // Handle Google Assistant & Voice Action on initial activity launch
        handleVoiceIntent(intent)
    }

    /**
     * Catches re-launched intents when the activity is already active in singleTop mode.
     *
     * @param intent Newly delivered intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    /**
     * Decodes Assistant actions, deep links, and voice intent parameters to start or control timers.
     *
     * @param intent Incoming intent.
     */
    private fun handleVoiceIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: ""
        val dataUri = intent.data

        when {
            // Google Assistant / System Voice: "OK Google, set timer on Habit Bell"
            action == android.provider.AlarmClock.ACTION_SET_TIMER -> {
                val lengthSec = intent.getIntExtra(android.provider.AlarmClock.EXTRA_LENGTH, 0)
                val message = intent.getStringExtra(android.provider.AlarmClock.EXTRA_MESSAGE) ?: ""
                viewModel.startVoiceTimer(lengthSec, message)
            }

            // Google Assistant / System Voice: "OK Google, stop timer on Habit Bell"
            action == android.provider.AlarmClock.ACTION_DISMISS_TIMER ||
            (dataUri != null && dataUri.scheme == "habitbell" && dataUri.host == "action" && dataUri.path == "/stop") -> {
                viewModel.stopTimer()
            }

            // App Action / Voice: Pause
            (dataUri != null && dataUri.scheme == "habitbell" && dataUri.host == "action" && dataUri.path == "/pause") -> {
                viewModel.pauseTimer()
            }

            // App Action / Voice: Resume
            (dataUri != null && dataUri.scheme == "habitbell" && dataUri.host == "action" && dataUri.path == "/resume") -> {
                viewModel.resumeTimer()
            }

            // Deep link: habitbell://start?profile=...
            (dataUri != null && dataUri.scheme == "habitbell" && dataUri.host == "start") -> {
                val profileKey = dataUri.getQueryParameter("profile") ?: ""
                val bg = dataUri.getQueryParameter("bg")
                if (bg == "youtube" || bg == "yt") {
                    viewModel.setBgMusicType(com.habitbell.app.engine.BackgroundSoundType.YOUTUBE_LINK)
                }
                viewModel.startVoiceTimer(0, profileKey)
            }

            // App Actions / Voice search with timerName parameter
            intent.hasExtra("timerName") -> {
                val timerName = intent.getStringExtra("timerName") ?: ""
                val duration = intent.getStringExtra("timerDuration")?.toIntOrNull() ?: 0
                viewModel.startVoiceTimer(duration, timerName)
            }
        }
    }

    /**
     * Modulates the visibility of the Android system status bar.
     *
     * When entering an active mindful timer screen ([AppScreen.SESSION] or [AppScreen.TV_DASHBOARD]),
     * the system status bar (displaying time clock, notification icons, battery gauge, and cellular/Wi-Fi
     * signals) is hidden to eliminate visual clutter, reduce cognitive distraction, and promote sustained presence.
     *
     * Transient reveals are supported via [WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE]:
     * swiping down from the screen edge allows a brief glance at battery or notifications without permanently
     * unhiding the status bar.
     *
     * @param hide `true` to hide the status bar for immersive focus; `false` to restore standard visibility.
     */
    private fun setStatusBarHidden(hide: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    /**
     * Synchronizes status bar visibility whenever the activity returns to the foreground.
     */
    override fun onResume() {
        super.onResume()
        val isTimerScreen = viewModel.uiState.value.currentScreen == AppScreen.SESSION ||
                viewModel.uiState.value.currentScreen == AppScreen.TV_DASHBOARD
        setStatusBarHidden(isTimerScreen)
    }

    /**
     * Restores system screen brightness and unhides system status bars when the activity is backgrounded.
     */
    override fun onStop() {
        super.onStop()
        viewModel.batteryOptimizer.setScreenBrightness(this, false)
        setStatusBarHidden(false)
    }

    /**
     * Cancels any pending hardware haptic pulses and ensures status bars are restored on termination.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.batteryOptimizer.setScreenBrightness(this, false)
        setStatusBarHidden(false)
        if (isFinishing) {
            viewModel.cancelHaptics()
        }
    }
}
