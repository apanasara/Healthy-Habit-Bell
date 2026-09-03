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
data class AppUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val selectedTheme: ThemeMode = ThemeMode.AMOLED,
    val isZenMode: Boolean = false,
    val isPocketModeManual: Boolean = false,
    val isDisplayMode: Boolean = true,
    val isAutoDim: Boolean = true,
    val bellVolume: Float = 0.9f,
    val isSettingsDrawerOpen: Boolean = false,
    val isBgMusicEnabled: Boolean = true,
    val bgMusicType: BackgroundSoundType = BackgroundSoundType.DEFAULT_AUM,
    val bgMusicCustomUri: String? = null,
    val bgMusicCustomName: String? = null,
    val bgMusicYouTubeUrl: String = "https://www.youtube.com/watch?v=x6UITRjhijI",
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

    /** Repository managing persistent profiles, favorites, and routine reminders. */
    private val repository = TimerRepository(application)

    /** Audio engine for Tibetan bell chimes and procedural synthesis. */
    private val audioManager = AudioBellManager(application)

    /** Haptic manager for sensory vibration pulses in Pocket Mode. */
    private val hapticManager = HapticManager(application)

    /** Power management and hardware proximity sensor coordinator. */
    val batteryOptimizer = BatteryOptimizer(application)

    /** Ambient audio engine for continuous meditation drones and YouTube audio. */
    val bgMusicManager = BackgroundMusicManager(application)

    /** Core 1Hz finite state machine governing timer countdowns and phase cycles. */
    private val engine = TimerEngine(audioManager, hapticManager)

    /** Embedded local HTTP daemon and NSD service for broadcasting to Smart TVs. */
    val castServer = com.habitbell.app.cast.LocalCastWebServer(application)

    /** Mutable state flow holding the reactive application UI state. */
    private val _uiState = MutableStateFlow(AppUiState())

    /** Public read-only stream emitting application UI state changes. */
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /** Live stream of the active timer engine countdown and phase progress. */
    val sessionState: StateFlow<TimerSessionState> = engine.state

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

    /**
     * Evaluates whether the AMOLED pure-black `#000000` curtain should be active.
     * Engages when the session is RUNNING and either manual pocket mode is switched on
     * or the hardware proximity sensor detects the device is face down or in a pocket.
     */
    val isPocketBlankingActive: StateFlow<Boolean> = combine(
        _uiState.map { it.isPocketModeManual },
        batteryOptimizer.isPocketCovered,
        sessionState.map { it.status == SessionStatus.RUNNING }
    ) { manual, sensorCovered, isRunning ->
        isRunning && (manual || sensorCovered)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Enforce project rule: Haptic vibration ONLY triggers when device is in pocket mode
        engine.isPocketModeActive = { _uiState.value.isPocketModeManual || isPocketBlankingActive.value }

        // Start local TV web receiver server for decoupled TV casting
        castServer.start()

        bgMusicManager.isEnabled = _uiState.value.isBgMusicEnabled
        bgMusicManager.soundType = _uiState.value.bgMusicType
        bgMusicManager.volume = _uiState.value.bgMusicVolume

        // Observe session status transitions to orchestrate foreground services and battery optimization
        viewModelScope.launch {
            var lastStatus: SessionStatus? = null
            sessionState.collect { state ->
                val statusChanged = state.status != lastStatus
                if (statusChanged) {
                    lastStatus = state.status
                    when (state.status) {
                        SessionStatus.RUNNING -> {
                            TimerService.startService(
                                getApplication(),
                                state.profile.name,
                                state.formattedRemainingTime
                            )
                            batteryOptimizer.acquireWakeLock()
                            if (_uiState.value.isDisplayMode) {
                                batteryOptimizer.startProximityMonitoring()
                            }
                            bgMusicManager.start()
                        }
                        SessionStatus.PAUSED -> {
                            TimerService.startService(
                                getApplication(),
                                state.profile.name,
                                "${state.formattedRemainingTime} (Paused)"
                            )
                            bgMusicManager.pause()
                        }
                        SessionStatus.COMPLETED -> {
                            TimerService.stopService(getApplication())
                            batteryOptimizer.releaseWakeLock()
                            batteryOptimizer.stopProximityMonitoring()
                            bgMusicManager.stop()
                            repository.recordSessionCompleted(state.profile.id)
                        }
                        SessionStatus.IDLE -> {
                            TimerService.stopService(getApplication())
                            batteryOptimizer.releaseWakeLock()
                            batteryOptimizer.stopProximityMonitoring()
                            bgMusicManager.stop()
                        }
                    }
                } else if (state.status == SessionStatus.RUNNING) {
                    // Ongoing 1-second ticks: only update notification, DO NOT restart music
                    TimerService.startService(
                        getApplication(),
                        state.profile.name,
                        state.formattedRemainingTime
                    )
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
        engine.loadProfile(profile)
        _uiState.update {
            it.copy(
                currentScreen = if (openTVMode) AppScreen.TV_DASHBOARD else AppScreen.SESSION,
                selectedTheme = profile.theme,
                isDisplayMode = profile.displayMode,
                isPocketModeManual = profile.pocketMode
            )
        }
        engine.startOrResume()
    }

    /**
     * Toggles between running and paused states for the active session.
     */
    fun togglePlayPause() {
        if (sessionState.value.status == SessionStatus.RUNNING) {
            engine.pause()
        } else {
            engine.startOrResume()
        }
    }

    /**
     * Pauses the active timer countdown.
     */
    fun pauseTimer() {
        engine.pause()
    }

    /**
     * Resumes the paused timer countdown.
     */
    fun resumeTimer() {
        engine.startOrResume()
    }

    /**
     * Stops the timer and navigates back to the Home screen.
     */
    fun stopTimer() {
        engine.reset()
        exitSessionToHome()
    }

    /**
     * Matches a natural language query or voice command to a preset profile and starts it.
     *
     * @param durationSec Requested duration in seconds (or 0 for profile default).
     * @param message Voice transcription text (e.g., "start mindful eating timer").
     */
    fun startVoiceTimer(durationSec: Int, message: String) {
        val allProfiles = profiles.value
        val lowerMessage = message.lowercase().trim()
        val matched = allProfiles.find {
            lowerMessage.isNotEmpty() && (
                lowerMessage.contains(it.name.lowercase()) ||
                it.name.lowercase().contains(lowerMessage) ||
                (lowerMessage.contains("eat") && it.id == "eating") ||
                (lowerMessage.contains("posture") && it.id == "posture") ||
                (lowerMessage.contains("read") && it.id == "reading") ||
                (lowerMessage.contains("walk") && it.id == "walking")
            )
        } ?: allProfiles.firstOrNull()

        if (matched != null) {
            val finalProfile = if (durationSec > 0) {
                matched.copy(
                    totalDurationSeconds = durationSec,
                    intervalDurationSeconds = (durationSec / 5).coerceIn(30, 300)
                )
            } else matched
            startProfileSession(finalProfile)
        }
    }

    /**
     * Resets the active session back to initial values without leaving the session screen.
     */
    fun resetSession() {
        engine.reset()
    }

    /**
     * Halts the active session and returns to the home screen.
     */
    fun exitSessionToHome() {
        engine.pause()
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
     * @param enabled True to dim screen to 5% brightness during rest.
     */
    fun setAutoDim(enabled: Boolean) {
        _uiState.update { it.copy(isAutoDim = enabled) }
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
     * Immediately terminates all ongoing tactile vibration sequences.
     */
    fun cancelHaptics() {
        hapticManager.cancel()
    }

    /**
     * Retrieves the local LAN HTTP URL for Smart TV browser casting (e.g., `http://192.168.1.5:8888`).
     *
     * @return Formatted network URL string.
     */
    fun getTvCastUrl(): String {
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
     * Opens or closes the settings configuration side drawer.
     *
     * @param open True to display drawer, false to dismiss.
     */
    fun openSettingsDrawer(open: Boolean) {
        _uiState.update { it.copy(isSettingsDrawerOpen = open) }
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
    }

    /**
     * Adjusts the ambient background music volume gain.
     *
     * @param volume Normalized floating-point volume (0.0f..1.0f).
     */
    fun setBgMusicVolume(volume: Float) {
        _uiState.update { it.copy(bgMusicVolume = volume) }
        bgMusicManager.volume = volume
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
    }

    /**
     * Cleanly tears down all background services, web servers, audio buffers, and hardware locks
     * when the ViewModel lifecycle terminates.
     */
    override fun onCleared() {
        super.onCleared()
        engine.destroy()
        hapticManager.cancel()
        bgMusicManager.release()
        castServer.stop()
        TimerService.stopService(getApplication())
        audioManager.release()
        batteryOptimizer.releaseWakeLock()
        batteryOptimizer.stopProximityMonitoring()
    }
}
