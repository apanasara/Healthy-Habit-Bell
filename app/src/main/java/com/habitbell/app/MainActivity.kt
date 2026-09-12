package com.habitbell.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.ui.components.DisplayAutomationOverlay
import com.habitbell.app.ui.screens.*
import com.habitbell.app.ui.theme.HabitBellTheme
import com.habitbell.app.ui.viewmodel.AppScreen
import com.habitbell.app.ui.viewmodel.HabitBellViewModel

/**
 * Main host activity for Habit Bell's Jetpack Compose presentation layer.
 *
 * Responsibilities:
 * 1. **Window Insets**: Enables edge-to-edge immersive rendering.
 * 2. **Compose Root**: Hosts screen navigation transitions between Home, Session, and Create Timer.
 * 3. **Google Cast Framework Integration**: Extends [FragmentActivity] to supply [androidx.fragment.app.FragmentManager]
 *    required by [androidx.mediarouter.app.MediaRouteButton] for native Cast device discovery dialogs.
 * 4. **Hardware Display Coordination**: Dynamically binds `FLAG_KEEP_SCREEN_ON` via [HabitBellViewModel.batteryOptimizer].
 * 5. **Hardware Pocket Blanking**: Renders the pure black [PocketOverlay] when proximity sensors detect pocketing.
 * 6. **Voice & Assistant Intents**: Decodes Google Assistant voice commands (`ACTION_SET_TIMER`, deep links).
 * 7. **SAF Audio Picking**: Launches system file picker for custom ambient audio tracks and requests persistent URI permissions.
 */
class MainActivity : FragmentActivity() {

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
            val isSessionOngoing = sessionState.status == SessionStatus.RUNNING || sessionState.status == SessionStatus.PAUSED

            // Direct launch check: bypass animated splash transition for voice commands, deep links, notification navigation, or ongoing sessions
            val isDirectIntent = (intent?.action != null && intent?.action != android.content.Intent.ACTION_MAIN) ||
                (intent?.getBooleanExtra("EXTRA_NAVIGATE_TO_SESSION", false) == true) ||
                isSessionOngoing
            var showSplashOverlay by remember { mutableStateOf(!isDirectIntent) }
            val profiles by viewModel.profiles.collectAsStateWithLifecycle()
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            val recentProfiles by viewModel.recentProfiles.collectAsStateWithLifecycle()
            val reminders by viewModel.reminders.collectAsStateWithLifecycle()
            val isPocketBlanking by viewModel.isPocketBlankingActive.collectAsStateWithLifecycle()
            val curtainState by viewModel.displayCurtainState.collectAsStateWithLifecycle()
            val isDisplayDimmed by viewModel.isDisplayDimmed.collectAsStateWithLifecycle()
            val isCasting by viewModel.castManager.isCasting.collectAsStateWithLifecycle()
            val castDeviceName by viewModel.castManager.castDeviceName.collectAsStateWithLifecycle()
            val selectedHealthProvider by viewModel.selectedHealthProvider.collectAsStateWithLifecycle()
            var hasActivityPermission by remember { mutableStateOf(viewModel.healthStepManager.hasActivityRecognitionPermission()) }

            // Activity recognition permission request launcher for step counting
            val activityRecognitionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasActivityPermission = isGranted
            }

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

            // Keep screen awake dynamically while session is running to prevent Android OS display sleep
            // from terminating the real-time Screen Mirroring capture encoder or dropping Chromecast streams
            LaunchedEffect(sessionState.status) {
                val shouldKeepAwake = sessionState.status == SessionStatus.RUNNING || sessionState.status == SessionStatus.PREPARING
                viewModel.batteryOptimizer.applyScreenAwake(this@MainActivity, shouldKeepAwake)
            }

            // Dynamically modulate hardware screen brightness based on the Display Mode dimming lifecycle
            LaunchedEffect(isDisplayDimmed) {
                viewModel.batteryOptimizer.setScreenBrightness(this@MainActivity, isDisplayDimmed)
            }

            // Distraction-free full-screen immersion: dynamically hide system status bar
            // (time clock, battery percentage, app notification icons, and network indicators)
            // whenever the user is on an active timer session screen (SESSION)
            val isTimerScreen = uiState.currentScreen == AppScreen.SESSION
            LaunchedEffect(isTimerScreen) {
                setStatusBarHidden(isTimerScreen)
            }

            HabitBellTheme(themeMode = uiState.selectedTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    viewModel.onUserTouchDisplay()
                                }
                            }
                        }
                ) {
                    // Background YouTube player attached to Window hierarchy only when YouTube streaming is actively selected
                    if (uiState.isBgMusicEnabled && uiState.bgMusicType == com.habitbell.app.engine.BackgroundSoundType.YOUTUBE_LINK) {
                        AndroidView(
                            factory = { ctx ->
                                viewModel.bgMusicManager.getOrCreateWebView(ctx).apply {
                                    isFocusable = false
                                    isClickable = false
                                }
                            },
                            modifier = Modifier.size(1.dp).alpha(0.01f)
                        )
                    }

                    // Navigation routing based on active AppScreen
                    when (uiState.currentScreen) {
                        AppScreen.HOME -> {
                            ModernHomeScreenSample(
                                profiles = profiles,
                                favorites = favorites,
                                reminders = reminders,
                                isZenMode = uiState.isZenMode,
                                onSelectProfile = { profile ->
                                    viewModel.startProfileSession(profile)
                                },
                                onToggleZenMode = {
                                    viewModel.setZenMode(!uiState.isZenMode)
                                },
                                onCycleTheme = {
                                    val nextTheme = when (uiState.selectedTheme) {
                                        ThemeMode.LIGHT -> ThemeMode.AMOLED
                                        else -> ThemeMode.LIGHT
                                    }
                                    viewModel.setTheme(nextTheme)
                                },
                                onCreateNewClick = {
                                    viewModel.navigateTo(AppScreen.CREATE_TIMER)
                                },
                                onOpenSettings = {
                                    viewModel.openSettingsDrawer(true, com.habitbell.app.ui.viewmodel.SettingsDrawerTab.GLOBAL)
                                },
                                sessionState = sessionState,
                                onResumeSession = {
                                    viewModel.navigateTo(AppScreen.SESSION)
                                },
                                onStopSession = {
                                    viewModel.stopTimer()
                                }
                            )
                        }

                        AppScreen.SESSION -> {
                            SessionScreen(
                                sessionState = sessionState,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onReset = { viewModel.resetSession() },
                                onOpenSettings = {
                                    viewModel.openSettingsDrawer(true, com.habitbell.app.ui.viewmodel.SettingsDrawerTab.TIMER)
                                },
                                onExit = { viewModel.exitSessionToHome() },
                                onToggleTheme = {
                                    val nextTheme = when (uiState.selectedTheme) {
                                        ThemeMode.LIGHT -> ThemeMode.AMOLED
                                        else -> ThemeMode.LIGHT
                                    }
                                    viewModel.setTheme(nextTheme)
                                },
                                onUserInteraction = {
                                    viewModel.userInteractionWake()
                                },
                                onSkipPreparation = {
                                    viewModel.skipPreparation()
                                }
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

                        AppScreen.SURYA_TIMER -> {
                            val suryaViewModel: com.habitbell.app.viewmodel.SuryaTimerViewModel =
                                androidx.lifecycle.viewmodel.compose.viewModel()
                            com.habitbell.app.ui.SuryaTimerScreen(
                                viewModel = suryaViewModel,
                                onBack = { viewModel.navigateTo(AppScreen.HOME) }
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
                            onUpdatePranayama = { purak, antar, rechak, bahya, rounds, intervalBellEnabled, intervalCadence, voiceEnabled, voiceStyle, tribandhaVoiceEnabled, voiceVolume ->
                                viewModel.updateActivePranayamaSettings(purak, antar, rechak, bahya, rounds, intervalBellEnabled, intervalCadence, voiceEnabled, voiceStyle, tribandhaVoiceEnabled, voiceVolume)
                            },
                            onTestVoiceCue = { style, isTriBandha, volume -> viewModel.testPranayamaVoiceCue(style, isTriBandha, volume) },
                            onTestPranayamaIntervalBell = { viewModel.testPranayamaIntervalBell() },
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
                            onPreviewBgMusic = { viewModel.previewBgMusic(it) },
                            isCasting = isCasting,
                            castDeviceName = castDeviceName,
                            onDisconnectCast = { viewModel.castManager.disconnect() },
                            selectedHealthProvider = selectedHealthProvider,
                            onHealthProviderSelected = { viewModel.selectHealthProvider(it) },
                            onTestStep = { viewModel.injectTestSteps(250) },
                            onUpdateSteps = { goal, interval, mode ->
                                viewModel.updateActiveProfileSteps(goal, interval, mode)
                            },
                            hasActivityPermission = hasActivityPermission,
                            onRequestActivityPermission = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    activityRecognitionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            },
                            activeTab = uiState.settingsDrawerTab,
                            onTabSelected = { viewModel.setSettingsDrawerTab(it) },
                            onToggleSunMoonTheme = { viewModel.toggleSunMoonTheme() },
                            isPauseOnBluetoothDisconnect = uiState.isPauseOnBluetoothDisconnect,
                            onPauseOnBluetoothDisconnectToggle = { viewModel.setPauseOnBluetoothDisconnect(it) },
                            isPrepCountdownEnabled = uiState.isPrepCountdownEnabled,
                            onPrepCountdownToggle = { viewModel.setPrepCountdownEnabled(it) },
                            onOpenSuryaEditor = {
                                viewModel.openSettingsDrawer(false)
                                viewModel.navigateTo(AppScreen.SURYA_TIMER)
                            },
                            onUpdateSurya = { poses, targetRounds, speedPreset, customPace, voiceMode ->
                                viewModel.updateActiveSuryaSettings(
                                    steps = poses.mapIndexed { idx, p ->
                                        com.habitbell.app.data.model.StepEntity(
                                            id = (idx + 1).toLong(),
                                            name = p.name,
                                            orderIdx = idx,
                                            durationSeconds = p.durationSeconds,
                                            voiceCueMode = voiceMode
                                        )
                                    },
                                    targetRounds = targetRounds,
                                    speedPreset = speedPreset,
                                    customPaceSeconds = customPace,
                                    voiceCueMode = voiceMode
                                )
                            }
                        )
                    }

                    // Automated Full-Screen AMOLED Blackout Curtain (Pocket, Car, TV, Watch)
                    if (curtainState.isActive) {
                        DisplayAutomationOverlay(
                            state = curtainState,
                            onDismiss = {
                                viewModel.dismissDisplayCurtain()
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

                    // In-App Branded Entry Splash Screen overlay with serene fade-out handoff
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSplashOverlay,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 400)
                        )
                    ) {
                        SplashScreen(
                            onTimeout = { showSplashOverlay = false }
                        )
                    }
                }
            }
        }

        // Check and restore active ongoing session if present
        viewModel.checkAndRestoreOngoingSession()

        // Handle incoming intents (Google Assistant voice commands, share sheet, deep links)
        handleIncomingIntent(intent)
    }

    /**
     * Catches re-launched intents when the activity is already active in singleTop mode.
     *
     * @param intent Newly delivered intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.checkAndRestoreOngoingSession()
        handleIncomingIntent(intent)
    }

    /**
     * Decodes Assistant actions, deep links, system share sheet intents, and voice intent parameters.
     *
     * @param intent Incoming intent.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: ""
        val dataUri = intent.data

        when {
            // Android System Share Sheet: YouTube or URL / text share
            action == Intent.ACTION_SEND -> {
                handleShareIntent(intent)
            }

            // Notification tap / direct session restore action
            intent.getBooleanExtra("EXTRA_NAVIGATE_TO_SESSION", false) -> {
                viewModel.checkAndRestoreOngoingSession()
            }
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

            intent.getStringExtra("screen") == "surya" ||
            (dataUri != null && dataUri.scheme == "habitbell" && dataUri.host == "surya") -> {
                viewModel.navigateTo(AppScreen.SURYA_TIMER)
            }

            intent.getStringExtra("screen") == "surya_settings" ||
            (dataUri != null && dataUri.scheme == "habitbell" && dataUri.host == "surya_settings") -> {
                val surya = com.habitbell.app.data.default.DefaultProfiles.SURYA_NAMASKAR
                viewModel.startProfileSession(surya)
                viewModel.openSettingsDrawer(true, com.habitbell.app.ui.viewmodel.SettingsDrawerTab.TIMER)
            }
        }
    }

    /**
     * Handles shared content from Android's system share sheet (e.g. sharing from the YouTube app or browser).
     *
     * Extracts the YouTube video ID from the shared text, normalizes it into the canonical shortest URL
     * (`https://youtu.be/<videoId>`), copies it into the system clipboard, sets it as Habitbell's ambient music URL,
     * and alerts the user.
     *
     * @param intent Incoming [Intent.ACTION_SEND] intent carrying shared text or web URL.
     */
    private fun handleShareIntent(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            ?: intent.data?.toString()
            ?: return

        val shortestUrl = viewModel.processSharedYouTubeUrl(sharedText)
        if (shortestUrl != null) {
            // Copy the canonical shortest URL to the Android system clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Habitbell Ambient Music", shortestUrl)
            clipboard?.setPrimaryClip(clip)

            // Display clear confirmation toast to the user
            Toast.makeText(
                this,
                "Ambient music URL set: $shortestUrl",
                Toast.LENGTH_LONG
            ).show()

            // Open settings drawer to the Timer / Ambient Sound tab if no timer session is currently active
            if (viewModel.sessionState.value.status != SessionStatus.RUNNING) {
                viewModel.openSettingsDrawer(true, com.habitbell.app.ui.viewmodel.SettingsDrawerTab.TIMER)
            }
        } else {
            Toast.makeText(
                this,
                "No valid YouTube video ID found in shared link",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Modulates the visibility of the Android system status bar.
     *
     * When entering an active mindful timer screen ([AppScreen.SESSION]),
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
     * Synchronizes status bar visibility and active session navigation whenever the activity returns to the foreground.
     */
    override fun onResume() {
        super.onResume()
        viewModel.checkAndRestoreOngoingSession()
        val isTimerScreen = viewModel.uiState.value.currentScreen == AppScreen.SESSION
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
