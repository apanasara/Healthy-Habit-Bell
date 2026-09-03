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

enum class AppScreen {
    HOME,
    SESSION,
    TV_DASHBOARD,
    CREATE_TIMER
}

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

class HabitBellViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TimerRepository(application)
    private val audioManager = AudioBellManager(application)
    private val hapticManager = HapticManager(application)
    val batteryOptimizer = BatteryOptimizer(application)
    val bgMusicManager = BackgroundMusicManager(application)
    private val engine = TimerEngine(audioManager, hapticManager)
    val castServer = com.habitbell.app.cast.LocalCastWebServer(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<TimerSessionState> = engine.state
    val profiles: StateFlow<List<TimerProfile>> = repository.profiles
    val favorites: StateFlow<List<TimerProfile>> = repository.favorites
    val reminders: StateFlow<List<RoutineReminder>> = repository.reminders

    val recentProfiles: StateFlow<List<TimerProfile>> = combine(
        repository.profiles,
        repository.recentProfileIds
    ) { allProfiles, recentIds ->
        recentIds.mapNotNull { id -> allProfiles.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isPocketBlankingActive: StateFlow<Boolean> = combine(
        _uiState.map { it.isPocketModeManual },
        batteryOptimizer.isPocketCovered,
        sessionState.map { it.status == SessionStatus.RUNNING }
    ) { manual, sensorCovered, isRunning ->
        isRunning && (manual || sensorCovered)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Link pocket mode state to TimerEngine so vibration ONLY triggers in pocket mode
        engine.isPocketModeActive = { _uiState.value.isPocketModeManual || isPocketBlankingActive.value }
        // Start local TV web receiver server for decoupled TV casting
        castServer.start()

        bgMusicManager.isEnabled = _uiState.value.isBgMusicEnabled
        bgMusicManager.soundType = _uiState.value.bgMusicType
        bgMusicManager.volume = _uiState.value.bgMusicVolume

        // Monitor session status to manage foreground notification and battery optimizer
        viewModelScope.launch {
            sessionState.collect { state ->
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
            }
        }
    }

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

    fun togglePlayPause() {
        if (sessionState.value.status == SessionStatus.RUNNING) {
            engine.pause()
        } else {
            engine.startOrResume()
        }
    }

    fun resetSession() {
        engine.reset()
    }

    fun exitSessionToHome() {
        engine.pause()
        _uiState.update { it.copy(currentScreen = AppScreen.HOME, isSettingsDrawerOpen = false) }
    }

    fun toggleFavorite(profileId: String) {
        repository.toggleFavorite(profileId)
    }

    fun setTheme(theme: ThemeMode) {
        _uiState.update { it.copy(selectedTheme = theme) }
    }

    fun setZenMode(enabled: Boolean) {
        _uiState.update { it.copy(isZenMode = enabled) }
    }

    fun setPocketMode(enabled: Boolean) {
        _uiState.update { it.copy(isPocketModeManual = enabled) }
    }

    fun setDisplayMode(enabled: Boolean) {
        _uiState.update { it.copy(isDisplayMode = enabled) }
    }

    fun setAutoDim(enabled: Boolean) {
        _uiState.update { it.copy(isAutoDim = enabled) }
    }

    fun setBellVolume(volume: Float) {
        _uiState.update { it.copy(bellVolume = volume) }
        audioManager.setVolume(volume)
    }

    fun playTestBell() {
        audioManager.playIntervalBell()
        hapticManager.triggerIntervalHaptic()
    }

    fun cancelHaptics() {
        hapticManager.cancel()
    }

    fun getTvCastUrl(): String {
        return castServer.getTvUrl()
    }

    fun openSettingsDrawer(open: Boolean) {
        _uiState.update { it.copy(isSettingsDrawerOpen = open) }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

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

    fun createCustomProfile(profile: TimerProfile) {
        repository.saveCustomProfile(profile)
        startProfileSession(profile)
    }

    fun setBgMusicEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isBgMusicEnabled = enabled) }
        bgMusicManager.isEnabled = enabled
        if (!enabled) {
            bgMusicManager.stop()
        } else if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
    }

    fun setBgMusicType(type: BackgroundSoundType) {
        _uiState.update { it.copy(bgMusicType = type) }
        bgMusicManager.soundType = type
        if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
    }

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

    fun setBgMusicVolume(volume: Float) {
        _uiState.update { it.copy(bgMusicVolume = volume) }
        bgMusicManager.volume = volume
    }

    fun setBgMusicYouTubeUrl(url: String) {
        _uiState.update { it.copy(bgMusicYouTubeUrl = url, bgMusicType = BackgroundSoundType.YOUTUBE_LINK) }
        bgMusicManager.youtubeUrl = url
        bgMusicManager.soundType = BackgroundSoundType.YOUTUBE_LINK
        if (sessionState.value.status == SessionStatus.RUNNING) {
            bgMusicManager.start()
        }
    }

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
