package com.habitbell.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitbell.app.data.model.*
import com.habitbell.app.data.repository.TimerRepository
import com.habitbell.app.engine.AudioBellManager
import com.habitbell.app.engine.BackgroundMusicManager
import com.habitbell.app.engine.BackgroundSoundType
import com.habitbell.app.engine.BatteryOptimizer
import com.habitbell.app.engine.CentralSessionHandler
import com.habitbell.app.engine.HapticManager
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerEngine
import com.habitbell.app.engine.TimerService
import com.habitbell.app.engine.TimerSessionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Top-level navigation destinations within the mobile Jetpack Compose application.
 */
enum class AppScreen {
    /** Primary landing dashboard with profile cards, favorites, and routine reminders. */
    HOME,

    /** Active fullscreen timer session display with circular countdown and controls. */
    SESSION,

    /** Big-screen leanback mode for TV or landscape mirroring. */
    TV_DASHBOARD,

    /** Custom profile creation and configuration editor screen. */
    CREATE_TIMER
}

/**
 * Immutable snapshot of application-wide UI and user preference states.
 *
 * @property currentScreen Active navigation destination ([AppScreen.HOME], [AppScreen.SESSION], etc.).
 * @property selectedTheme Visual color palette applied across screens ([ThemeMode]).
 * @property isZenMode If true, strips secondary UI elements for minimalist countdown focus.
 * @property isPocketModeManual Manual override switch for Pocket Mode AMOLED black screen.
 * @property isDisplayMode If true, keeps the display awake during active practice.
 * @property isAutoDim If true, dims display brightness to 5% during resting intervals.
 * @property bellVolume Master gain level for Tibetan bell audio cues (0.0f..1.0f).
 * @property isSettingsDrawerOpen Visibility flag for the slide-out configuration drawer.
 * @property isBgMusicEnabled Master toggle for ambient soundscape playback.
 * @property bgMusicType Active ambient sound source ([BackgroundSoundType]).
 * @property bgMusicCustomUri Storage Access Framework URI string for user-chosen audio files.
 * @property bgMusicCustomName Display filename of user-selected custom audio track.
 * @property bgMusicYouTubeUrl Web link to YouTube meditation track for streaming playback.
 * @property bgMusicVolume Master gain level for ambient background audio (0.0f..1.0f).
 */
/**
 * # SettingsDrawerTab
 *
 * Tab classification within the dual-domain Settings Drawer.
 *
 * ## Architectural Role & Relationships
 * Disaggregates configuration into dynamic profile-specific parameters ([TIMER])
 * vs persistent system-wide environment & hardware parameters ([GLOBAL]).
 */
enum class SettingsDrawerTab {
    /** Dynamic, profile-specific timing, target goals, and ambient sound controls. */
    TIMER,

    /** Persistent system-wide environment, theme, volume, and connectivity settings. */
    GLOBAL
}

data class AppUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val selectedTheme: ThemeMode = ThemeMode.AMOLED,
    val isZenMode: Boolean = false,
    val isPocketModeManual: Boolean = false,
    val isDisplayMode: Boolean = true,
    val isAutoDim: Boolean = true,
    val bellVolume: Float = 0.9f,
    val bellStyle: com.habitbell.app.engine.BellSoundStyle = com.habitbell.app.engine.BellSoundStyle.ZEN_TINGSHA,
    val isSettingsDrawerOpen: Boolean = false,
    val settingsDrawerTab: SettingsDrawerTab = SettingsDrawerTab.TIMER,
    val isBgMusicEnabled: Boolean = true,
    val bgMusicType: BackgroundSoundType = BackgroundSoundType.DEFAULT_AUM,
    val bgMusicCustomUri: String? = null,
    val bgMusicCustomName: String? = null,
    val bgMusicYouTubeUrl: String = "https://youtu.be/x6UITRjhijI",
    val bgMusicVolume: Float = 0.35f
)

/**
 * Primary presentation coordinator and state-holder for Habit Bell.
 *
 * Implements Unidirectional Data Flow (UDF) by exposing immutable [StateFlow] streams to
 * Compose components while delegating business logic to [TimerEngine], persistence to
 * [TimerRepository], and peripheral hardware controls to [BatteryOptimizer], [AudioBellManager],
 * [HapticManager], [BackgroundMusicManager], and [com.habitbell.app.cast.LocalCastWebServer].
 *
 * @param application Android Application instance for resource and service access.
 */
class HabitBellViewModel(application: Application) : AndroidViewModel(application) {

    /** SharedPreferences handle for persisting background music and bell preferences. */
    private val prefs = application.getSharedPreferences("habit_bell_settings", android.content.Context.MODE_PRIVATE)

    /** Authoritative process-level session handler orchestrating media controls and timer state. */
    val sessionHandler: CentralSessionHandler = CentralSessionHandler.getInstance(application)

    /** Repository managing persistent profiles, favorites, and routine reminders. */
    val repository: TimerRepository = sessionHandler.repository

    /** Audio engine for Tibetan bell chimes and procedural synthesis. */
    val audioManager: AudioBellManager = sessionHandler.audioManager

    /** Haptic manager for sensory vibration pulses in Pocket Mode. */
    val hapticManager: HapticManager = sessionHandler.hapticManager

    /** Power management and hardware proximity sensor coordinator. */
    val batteryOptimizer: BatteryOptimizer = sessionHandler.batteryOptimizer

    /** Ambient audio engine for continuous meditation drones and YouTube audio. */
    val bgMusicManager: BackgroundMusicManager = sessionHandler.bgMusicManager

    /** Core 1Hz finite state machine governing timer countdowns and phase cycles. */
    val engine: TimerEngine = sessionHandler.engine

    /** Health and step tracking manager coordinating pedometers and health platforms. */
    val healthStepManager: com.habitbell.app.health.HealthStepManager = sessionHandler.healthStepManager

    /** Currently selected health data provider stream. */
    val selectedHealthProvider: StateFlow<com.habitbell.app.health.HealthProviderType> = healthStepManager.selectedProviderType

    /** Google Cast manager coordinating pure app streaming to TV hardware. */
    val castManager: com.habitbell.app.cast.HabitBellCastManager = sessionHandler.castManager

    /** Embedded local HTTP daemon and NSD service for auxiliary Smart TV web browsers (Samsung/LG). */
    val castServer = com.habitbell.app.cast.LocalCastWebServer(application)

    /** Mutable state flow holding the reactive application UI state. */
    private val _uiState = MutableStateFlow(AppUiState())

    /** Public read-only stream emitting application UI state changes. */
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /** Live stream of the active timer engine countdown and phase progress. */
    val sessionState: StateFlow<TimerSessionState> = sessionHandler.sessionState

    /** Catalog of all available preset and user-created timer profiles. */
    val profiles: StateFlow<List<TimerProfile>> = repository.profiles

    /** Filtered list of profiles marked as favorites. */
    val favorites: StateFlow<List<TimerProfile>> = repository.favorites

    /** Scheduled daily routine habit reminders. */
    val reminders: StateFlow<List<RoutineReminder>> = repository.reminders

    /**
     * Chronologically ordered recent profiles (capped at 5 items).
     */
    val recentProfiles: StateFlow<List<TimerProfile>> = combine(
        repository.profiles,
        repository.recentProfileIds
    ) { allProfiles, recentIds ->
        recentIds.mapNotNull { id -> allProfiles.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Unified display automation manager handle from process singleton. */
    val displayAutomationManager: com.habitbell.app.engine.DisplayAutomationManager = sessionHandler.displayAutomationManager

    /** Live reactive stream governing the AMOLED blackout curtain across Pocket, Car, TV, and Watch modes. */
    val displayCurtainState: StateFlow<com.habitbell.app.engine.DisplayCurtainState> = displayAutomationManager.curtainState

    /**
     * Evaluates whether the AMOLED pure-black `#000000` curtain should be active.
     * Delegates directly to [displayCurtainState.isActive].
     */
    val isPocketBlankingActive: StateFlow<Boolean> = displayCurtainState.map { it.isActive }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Determines whether the display should dim to 5% power-saving brightness during resting intervals.
     * Lifted immediately on interval chimes, session completion, or manual screen taps.
     */
    val isDisplayDimmed: StateFlow<Boolean> = combine(
        sessionState,
        _uiState.map { it.isDisplayMode && it.isAutoDim },
        isPocketBlankingActive
    ) { session, autoDimEnabled, inPocket ->
        session.status == SessionStatus.RUNNING && autoDimEnabled && !inPocket && session.isDimmed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Enforce project rule: Haptic vibration ONLY triggers when device is in pocket mode
        engine.isPocketModeActive = { _uiState.value.isPocketModeManual || isPocketBlankingActive.value }

        // Restore persisted user background music and bell chime preferences
        loadSettings()

        // Mute or resume ambient background music when entering or exiting Pocket Mode in public
        viewModelScope.launch {
            isPocketBlankingActive.collect { inPocket ->
                if (sessionState.value.status == SessionStatus.RUNNING) {
                    if (inPocket) {
                        bgMusicManager.pause()
                    } else if (_uiState.value.isBgMusicEnabled) {
                        bgMusicManager.start()
                    }
                }
            }
        }

        // Coordinate proximity monitoring when Display Mode is active
        viewModelScope.launch {
            sessionState.collect { state ->
                if (state.status == SessionStatus.RUNNING && _uiState.value.isDisplayMode) {
                    batteryOptimizer.startProximityMonitoring()
                } else if (state.status != SessionStatus.RUNNING) {
                    batteryOptimizer.stopProximityMonitoring()
                }
            }
        }
    }

    /**
     * Loads a profile into the engine and transitions the UI to the session view.
     *
     * @param profile The target [TimerProfile] to execute.
     * @param openTVMode If true, opens the leanback TV dashboard screen instead of standard mobile screen.
     */
    fun startProfileSession(profile: TimerProfile, openTVMode: Boolean = false) {
        sessionHandler.startProfile(profile)
        _uiState.update {
            it.copy(
                currentScreen = if (openTVMode) AppScreen.TV_DASHBOARD else AppScreen.SESSION,
                isDisplayMode = profile.displayMode,
                isPocketModeManual = profile.pocketMode
            )
        }
    }

    /**
     * Toggles between running and paused states for the active session across all surfaces.
     */
    fun togglePlayPause() {
        sessionHandler.togglePlayPause()
    }

    /**
     * Pauses the active timer countdown across all connected devices and vehicle HUD.
     */
    fun pauseTimer() {
        sessionHandler.pause()
    }

    /**
     * Resumes the paused timer countdown across all connected devices and vehicle HUD.
     */
    fun resumeTimer() {
        sessionHandler.resume()
    }

    /**
     * Stops the timer and navigates back to the Home screen.
     */
    fun stopTimer() {
        sessionHandler.stop()
        exitSessionToHome()
    }

    /**
     * Matches a natural language query or voice command to a preset profile and starts it.
     *
     * @param durationSec Requested duration in seconds (or 0 for profile default).
     * @param message Voice transcription text (e.g., "start mindful eating timer").
     */
    fun startVoiceTimer(durationSec: Int, message: String) {
        sessionHandler.startVoiceTimer(durationSec, message)
        _uiState.update { it.copy(currentScreen = AppScreen.SESSION) }
    }

    /**
     * Resets the active session back to initial values without leaving the session screen.
     */
    fun resetSession() {
        sessionHandler.reset()
    }

    /**
     * Halts the active session and returns to the home screen.
     */
    fun exitSessionToHome() {
        sessionHandler.pause()
        _uiState.update { it.copy(currentScreen = AppScreen.HOME, isSettingsDrawerOpen = false) }
    }

    /**
     * Inverts the favorite flag for the given profile ID.
     *
     * @param profileId Identifier of the profile.
     */
    fun toggleFavorite(profileId: String) {
        repository.toggleFavorite(profileId)
    }

    /**
     * Updates the app theme mode.
     *
     * @param theme Selected [ThemeMode].
     */
    fun setTheme(theme: ThemeMode) {
        _uiState.update { it.copy(selectedTheme = theme) }
    }

    /**
     * Toggles Zen Mode (minimalist countdown UI).
     *
     * @param enabled True to hide non-essential screen widgets.
     */
    fun setZenMode(enabled: Boolean) {
        _uiState.update { it.copy(isZenMode = enabled) }
    }

    /**
     * Manually engages or disengages Pocket Mode AMOLED blanking.
     *
     * @param enabled True to force display blacking.
     */
    fun setPocketMode(enabled: Boolean) {
        _uiState.update { it.copy(isPocketModeManual = enabled) }
        displayAutomationManager.setPocketModeManual(enabled)
    }

    /**
     * Notifies the display automation engine of user touch activity to reset the flat inactivity countdown.
     */
    fun onUserTouchDisplay() {
        displayAutomationManager.notifyUserTouched()
    }

    /**
     * Dismisses the automated AMOLED blackout curtain on explicit tap or wake action.
     */
    fun dismissDisplayCurtain() {
        displayAutomationManager.dismissCurtainTemporarily()
        if (_uiState.value.isPocketModeManual) {
            setPocketMode(false)
        }
    }

    /**
     * Toggles whether the screen should remain awake during practice.
     *
     * @param enabled True to prevent screen timeouts.
     */
    fun setDisplayMode(enabled: Boolean) {
        _uiState.update { it.copy(isDisplayMode = enabled) }
    }

    /**
     * Toggles automatic screen dimming during resting intervals.
     *
     * @param enabled True to dim screen to low brightness during resting countdown.
     */
    fun setAutoDim(enabled: Boolean) {
        _uiState.update { it.copy(isAutoDim = enabled) }
        saveSettings()
    }

    /**
     * Temporarily wakes the display from auto-dimmed state upon user touch or interaction.
     *
     * @param seconds Duration in seconds to keep the display un-dimmed before auto-dimming resumes.
     */
    fun userInteractionWake(seconds: Int = 5) {
        engine.wakeScreenTemporarily(seconds)
    }

    /**
     * Adjusts the Tibetan bell audio gain.
     *
     * @param volume Normalized floating-point volume (0.0f..1.0f).
     */
    fun setBellVolume(volume: Float) {
        _uiState.update { it.copy(bellVolume = volume) }
        audioManager.setVolume(volume)
    }

    /**
     * Auditions an interval bell and pocket vibration pulse for testing sound settings.
     */
    fun playTestBell() {
        audioManager.playIntervalBell()
        hapticManager.triggerIntervalHaptic()
    }

    /**
     * Auditions the approved Option C 3-bell interval sequence:
     * - Strike 1: 2048 Hz (high-vibration crystalline cue, 45% volume)
     * - Strike 2: 1536 Hz (centering chime, 70% volume)
     * - Strike 3: 1024 Hz (deep resonance finale, 100% volume with 7.5s sustain)
     */
    fun playOptionCPreview() {
        audioManager.playOptionCPreview()
        hapticManager.triggerIntervalHaptic()
    }

    /**
     * Auditions the session completion Deep Temple Gong.
     */
    fun playGongPreview() {
        audioManager.playGongPreview()
        hapticManager.triggerCompletionHaptic()
    }

    /**
     * Starts a rapid 10-second demo session to demonstrate both the Option C interval chime (at 5s)
     * and the deep Temple Gong session completion chime (at 0s) live in action.
     */
    fun startQuickDemoSession() {
        val demoProfile = com.habitbell.app.data.model.TimerProfile(
            id = "demo_10s",
            name = "10s Demo (Interval + Gong)",
            type = com.habitbell.app.data.model.TimerType.LINEAR,
            category = "Sound Test",
            iconName = "notifications",
            totalDurationSeconds = 10,
            intervalDurationSeconds = 5
        )
        openSettingsDrawer(false)
        startProfileSession(demoProfile)
    }

    /**
     * Immediately terminates all ongoing tactile vibration sequences.
     */
    fun cancelHaptics() {
        hapticManager.cancel()
    }

    /**
     * Starts the auxiliary Smart TV HTTP server on demand.
     */
    fun startSmartTvServer() {
        if (!castServer.isRunning) {
            castServer.start()
        }
    }

    /**
     * Stops the auxiliary Smart TV HTTP server to conserve battery and radio resources.
     */
    fun stopSmartTvServer() {
        if (castServer.isRunning) {
            castServer.stop()
        }
    }

    /**
     * Retrieves the local LAN HTTP URL for Smart TV browser casting (e.g., `http://192.168.1.5:8888`).
     * Starts the server on demand if not already running.
     *
     * @return Formatted network URL string.
     */
    fun getTvCastUrl(): String {
        startSmartTvServer()
        val base = castServer.getTvUrl()
        val ui = _uiState.value
        return if (ui.isBgMusicEnabled) {
            if (ui.bgMusicType == BackgroundSoundType.YOUTUBE_LINK) {
                val vid = bgMusicManager.extractVideoId(ui.bgMusicYouTubeUrl) ?: "x6UITRjhijI"
                "$base/?yt=$vid"
            } else {
                "$base/?bg=aum"
            }
        } else {
            "$base/?bg=none"
        }
    }

    /**
     * Opens or closes the settings configuration side drawer with optional initial tab targeting.
     *
     * @param open True to display drawer, false to dismiss.
     * @param tab Initial tab to select ([SettingsDrawerTab.TIMER] or [SettingsDrawerTab.GLOBAL]).
     */
    fun openSettingsDrawer(open: Boolean, tab: SettingsDrawerTab = SettingsDrawerTab.TIMER) {
        _uiState.update { it.copy(isSettingsDrawerOpen = open, settingsDrawerTab = tab) }
    }

    /**
     * Switches the active tab inside the open settings drawer.
     *
     * @param tab Target [SettingsDrawerTab] to display.
     */
    fun setSettingsDrawerTab(tab: SettingsDrawerTab) {
        _uiState.update { it.copy(settingsDrawerTab = tab) }
    }

    /**
     * Toggles between Sun (Day / Eye Comfort Light) and Moon (Night / Eye Comfort Dark or AMOLED) themes.
     * Both circadian states feature engineered blue-light reduction for visual comfort.
     */
    fun toggleSunMoonTheme() {
        _uiState.update { current ->
            val nextTheme = if (current.selectedTheme.isSunDayTheme) {
                ThemeMode.EYE_COMFORT
            } else {
                ThemeMode.LIGHT
            }
            current.copy(selectedTheme = nextTheme)
        }
    }

    /**
     * Explicitly switches navigation to a target screen.
     *
     * @param screen Target [AppScreen] destination.
     */
    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    /**
     * Modifies the active profile's total and interval duration parameters.
     *
     * @param totalDuration New total session duration in seconds.
     * @param intervalDuration New interval chime period in seconds.
     */
    fun updateActiveProfileTimes(totalDuration: Int, intervalDuration: Int) {
        val currentProfile = sessionState.value.profile
        repository.updateProfileSettings(
            profileId = currentProfile.id,
            totalDuration = totalDuration,
            intervalDuration = intervalDuration
        )
        engine.loadProfile(
            currentProfile.copy(
                totalDurationSeconds = totalDuration,
                intervalDurationSeconds = intervalDuration
            )
        )
    }

    /**
     * Updates step goal and interval chime cadence for the active profile.
     *
     * @param stepGoal Target step count or null.
     * @param stepInterval Step interval bell frequency or null.
     * @param triggerMode Trigger policy determining completion ([StepTriggerMode.TIME_OR_STEPS], etc.).
     */
    fun updateActiveProfileSteps(
        stepGoal: Int?,
        stepInterval: Int?,
        triggerMode: StepTriggerMode = StepTriggerMode.TIME_OR_STEPS
    ) {
        val currentProfile = sessionState.value.profile
        repository.updateProfileSettings(
            profileId = currentProfile.id,
            stepGoal = stepGoal,
            stepInterval = stepInterval,
            stepTriggerMode = triggerMode
        )
        engine.loadProfile(
            currentProfile.copy(
                stepGoal = stepGoal,
                stepInterval = stepInterval,
                stepTriggerMode = triggerMode
            )
        )
    }

    /** Gentle lady voice guidance coordinator handle from process singleton. */
    val voiceGuide: com.habitbell.app.engine.PranayamaVoiceGuide = sessionHandler.voiceGuide

    /**
     * Updates active Pranayama breath timing parameters, target rounds, and voice guidance settings.
     *
     * @param purakSeconds Duration for Puraka (Inhale) in seconds.
     * @param antarKumbhakSeconds Duration for Antar Kumbhaka (Hold In) in seconds.
     * @param rechakSeconds Duration for Rechaka (Exhale) in seconds.
     * @param bahyaKumbhakSeconds Duration for Bahya Kumbhaka (Hold Out) in seconds.
     * @param targetRounds Total cycles/repetitions configured for the session.
     * @param isVoiceEnabled Whether gentle lady voice prompts are triggered on phase transitions.
     * @param voiceStyle Linguistic cue style ([com.habitbell.app.data.model.VoiceCueStyle]).
     */
    fun updateActivePranayamaSettings(
        purakSeconds: Int,
        antarKumbhakSeconds: Int,
        rechakSeconds: Int,
        bahyaKumbhakSeconds: Int,
        targetRounds: Int,
        isVoiceEnabled: Boolean = true,
        voiceStyle: com.habitbell.app.data.model.VoiceCueStyle = com.habitbell.app.data.model.VoiceCueStyle.SANSKRIT
    ) {
        val currentProfile = sessionState.value.profile
        repository.updatePranayamaSettings(
            profileId = currentProfile.id,
            purakSeconds = purakSeconds,
            antarKumbhakSeconds = antarKumbhakSeconds,
            rechakSeconds = rechakSeconds,
            bahyaKumbhakSeconds = bahyaKumbhakSeconds,
            targetRounds = targetRounds,
            isVoiceEnabled = isVoiceEnabled,
            voiceStyle = voiceStyle
        )
        val updatedConfig = (currentProfile.pranayamaConfig ?: com.habitbell.app.data.model.PranayamaConfig(emptyList(), targetRounds))
            .withStepDurations(
                purak = purakSeconds,
                antar = antarKumbhakSeconds,
                rechak = rechakSeconds,
                bahya = bahyaKumbhakSeconds,
                rounds = targetRounds,
                voiceEnabled = isVoiceEnabled,
                voiceStyle = voiceStyle
            )
        engine.loadProfile(
            currentProfile.copy(
                pranayamaConfig = updatedConfig,
                totalDurationSeconds = updatedConfig.totalSessionSeconds
            )
        )
    }

    /**
     * Auditions a sample gentle lady voice cue ("Purak") for settings preview.
     */
    fun testPranayamaVoiceCue() {
        voiceGuide.auditionCue("Purak")
    }

    /**
     * Switches the active health platform provider (e.g. Device Pedometer, Health Connect, Apple Health).
     *
     * @param provider Selected [com.habitbell.app.health.HealthProviderType].
     */
    fun selectHealthProvider(provider: com.habitbell.app.health.HealthProviderType) {
        healthStepManager.selectProvider(provider)
    }

    /**
     * Injects synthetic footsteps to verify interval bells and completion gong boundaries.
     *
     * @param count Number of steps to inject.
     */
    fun injectTestSteps(count: Int = 250) {
        val current = sessionState.value.currentSteps + count
        engine.onStepCountUpdated(current, 108)
    }

    /**
     * Persists a custom created timer profile and immediately launches its session.
     *
     * @param profile The newly constructed [TimerProfile].
     */
    fun createCustomProfile(profile: TimerProfile) {
        repository.saveCustomProfile(profile)
        startProfileSession(profile)
    }

    /**
     * Toggles ambient background soundscape on or off.
     *
     * @param enabled True to play ambient sound; false to silence.
     */
    fun setBgMusicEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isBgMusicEnabled = enabled) }
        bgMusicManager.isEnabled = enabled
        if (!enabled) {
            bgMusicManager.stop()
        } else if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
        saveSettings()
    }

    /**
     * Updates the active sound source type for background ambient soundscapes.
     *
     * @param type Target [BackgroundSoundType] strategy.
     */
    fun setBgMusicType(type: BackgroundSoundType) {
        _uiState.update { it.copy(bgMusicType = type) }
        bgMusicManager.soundType = type
        if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
        saveSettings()
    }

    /**
     * Configures a custom local audio file for background music.
     *
     * @param uriStr Android Storage Access Framework URI string.
     * @param fileName Human-readable audio file name.
     */
    fun setBgMusicCustomUri(uriStr: String?, fileName: String?) {
        _uiState.update {
            it.copy(
                bgMusicCustomUri = uriStr,
                bgMusicCustomName = fileName,
                bgMusicType = BackgroundSoundType.CUSTOM_FILE
            )
        }
        bgMusicManager.customAudioUri = uriStr
        bgMusicManager.soundType = BackgroundSoundType.CUSTOM_FILE
        if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
        saveSettings()
    }

    /**
     * Adjusts the ambient background music volume gain.
     *
     * @param volume Normalized floating-point volume (0.0f..1.0f).
     */
    fun setBgMusicVolume(volume: Float) {
        _uiState.update { it.copy(bgMusicVolume = volume) }
        bgMusicManager.volume = volume
        saveSettings()
    }

    /**
     * Sets a YouTube meditation video link for ad-free background streaming.
     *
     * @param url Full YouTube video URL or ID.
     */
    fun setBgMusicYouTubeUrl(url: String) {
        _uiState.update { it.copy(bgMusicYouTubeUrl = url, bgMusicType = BackgroundSoundType.YOUTUBE_LINK) }
        bgMusicManager.youtubeUrl = url
        bgMusicManager.soundType = BackgroundSoundType.YOUTUBE_LINK
        if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
        saveSettings()
    }

    /**
     * Toggles live preview auditioning of background ambient audio in settings.
     *
     * @param play True to start audio playback; false to stop.
     */
    fun previewBgMusic(play: Boolean) {
        if (play) {
            bgMusicManager.start()
        } else {
            bgMusicManager.stop()
        }
    }

    /**
     * Sets the active bell chime timbre style (Option C Zen Tingsha, Tibetan Bowl, Temple Gong, Crystal Quartz).
     */
    fun setBellStyle(style: com.habitbell.app.engine.BellSoundStyle) {
        _uiState.update { it.copy(bellStyle = style) }
        audioManager.bellStyle = style
        saveSettings()
    }

    private fun loadSettings() {
        val internalAumFile = java.io.File(getApplication<Application>().filesDir, "custom_aum.mp3")
        val hasInternalAum = internalAumFile.exists() && internalAumFile.length() > 0

        val savedTypeStr = prefs.getString("bg_music_type", null)
        val savedType = when (savedTypeStr) {
            "CUSTOM_FILE" -> BackgroundSoundType.CUSTOM_FILE
            "YOUTUBE_LINK" -> BackgroundSoundType.YOUTUBE_LINK
            "NONE" -> BackgroundSoundType.NONE
            else -> BackgroundSoundType.DEFAULT_AUM
        }

        val savedUri = prefs.getString("bg_music_custom_uri", null)
        val savedName = prefs.getString("bg_music_custom_name", if (hasInternalAum) "aum.mp3" else null)
        val savedEnabled = prefs.getBoolean("bg_music_enabled", true)
        val savedVol = prefs.getFloat("bg_music_volume", 0.35f)
        val savedYt = prefs.getString("bg_music_yt_url", "https://youtu.be/x6UITRjhijI") ?: "https://youtu.be/x6UITRjhijI"

        val savedStyleStr = prefs.getString("bell_style", com.habitbell.app.engine.BellSoundStyle.ZEN_TINGSHA.name)
        val savedStyle = try {
            com.habitbell.app.engine.BellSoundStyle.valueOf(savedStyleStr ?: com.habitbell.app.engine.BellSoundStyle.ZEN_TINGSHA.name)
        } catch (_: Exception) {
            com.habitbell.app.engine.BellSoundStyle.ZEN_TINGSHA
        }

        val savedAutoDim = prefs.getBoolean("is_auto_dim", true)

        _uiState.update {
            it.copy(
                isBgMusicEnabled = savedEnabled,
                bgMusicType = savedType,
                bgMusicCustomUri = savedUri,
                bgMusicCustomName = savedName,
                bgMusicVolume = savedVol,
                bgMusicYouTubeUrl = savedYt,
                bellStyle = savedStyle,
                isAutoDim = savedAutoDim
            )
        }

        audioManager.bellStyle = savedStyle
        bgMusicManager.isEnabled = savedEnabled
        bgMusicManager.soundType = savedType
        bgMusicManager.customAudioUri = savedUri
        bgMusicManager.volume = savedVol
        bgMusicManager.youtubeUrl = savedYt
    }

    private fun saveSettings() {
        val state = _uiState.value
        prefs.edit()
            .putBoolean("bg_music_enabled", state.isBgMusicEnabled)
            .putString("bg_music_type", state.bgMusicType.name)
            .putString("bg_music_custom_uri", state.bgMusicCustomUri)
            .putString("bg_music_custom_name", state.bgMusicCustomName)
            .putFloat("bg_music_volume", state.bgMusicVolume)
            .putString("bg_music_yt_url", state.bgMusicYouTubeUrl)
            .putString("bell_style", state.bellStyle.name)
            .putBoolean("is_auto_dim", state.isAutoDim)
            .apply()
    }

    /**
     * Cleans up local UI resources when the ViewModel lifecycle terminates.
     * Note: [CentralSessionHandler] remains active in the process to guarantee uninterrupted
     * audio playback and synchronization with Android Auto, car HUD, and wearable controllers.
     */
    override fun onCleared() {
        super.onCleared()
        castServer.stop()
    }
}
