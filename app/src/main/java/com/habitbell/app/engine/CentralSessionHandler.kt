package com.habitbell.app.engine

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.habitbell.app.auto.HabitBellMediaService
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.TimerProfile
import com.habitbell.app.data.repository.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * # CentralSessionHandler
 *
 * Process-level singleton orchestrating unified wellness timer sessions and media controls.
 *
 * ## Architectural Role & Relationships
 * Serves as the authoritative single source of truth across all Habit Bell display and input surfaces:
 * - **Car HUD (Android Auto)**: Transmits live playback states and metadata to vehicle head units via [MediaSessionCompat].
 * - **Mobile Display (`MainActivity` / Compose `SessionScreen`)**: Renders reactive countdown animations and controls.
 * - **Car App Library (`HabitBellCarScreen`)**: Delivers driver-safe automotive list templates for in-car sessions.
 * - **Wear OS / Smart Watch**: Exposes standard media transport controls via mirrored Android media sessions.
 * - **TV Dashboard & Local WebCast (`LocalCastWebServer`)**: Broadcasts state snapshots to local smart screens.
 *
 * ## Lifecycle & Thread Safety
 * - Bound to the process lifecycle via [Application] context.
 * - Thread-safe state emissions using Kotlin [StateFlow].
 * - Background state updates executed on [Dispatchers.Main.immediate] and [Dispatchers.Default].
 *
 * @param application Process-level application context for hardware and system service resolution.
 */
class CentralSessionHandler(private val application: Application) {

    companion object {
        /** Process-level singleton handle. */
        @Volatile
        private var INSTANCE: CentralSessionHandler? = null

        /**
         * Obtains or initializes the process-level [CentralSessionHandler] singleton instance.
         *
         * @param context Component or application context.
         * @return Authoritative [CentralSessionHandler] instance.
         */
        fun getInstance(context: Context): CentralSessionHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CentralSessionHandler(context.applicationContext as Application).also {
                    INSTANCE = it
                }
            }
        }
    }

    /** Coroutine scope bound to process lifecycle with a supervisor job to isolate child failures. */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** Persistent data repository for timer profiles, favorites, and routine reminders. */
    val repository: TimerRepository = TimerRepository(application)

    /** Audio engine for Tibetan singing bowl samples and procedural additive synthesis. */
    val audioManager: AudioBellManager = AudioBellManager(application)

    /** Haptic sensory manager restricted strictly to Pocket Mode. */
    val hapticManager: HapticManager = HapticManager(application)

    /** Hardware battery optimization, partial wake-lock, and proximity sensor monitor. */
    val batteryOptimizer: BatteryOptimizer = BatteryOptimizer(application)

    /** Ambient background soundscape manager (bundled Aum drone, custom SAF files, YouTube audio). */
    val bgMusicManager: BackgroundMusicManager = BackgroundMusicManager(application)

    /** Native Google Cast manager enabling pure app casting to TV hardware without mirroring. */
    val castManager: com.habitbell.app.cast.HabitBellCastManager = com.habitbell.app.cast.HabitBellCastManager.getInstance(application)

    /** Core 1Hz heartbeat finite state machine governing timer countdowns. */
    val engine: TimerEngine = TimerEngine(audioManager, hapticManager)

    /** Authoritative Android media session shared by Car HUD, notifications, and Wear OS. */
    val mediaSession: MediaSessionCompat = MediaSessionCompat(application, "HabitBellMediaSession").apply {
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
    }

    /** Convenient accessor for the media session token. */
    val sessionToken: MediaSessionCompat.Token
        get() = mediaSession.sessionToken

    /** Live read-only stream of the active session state from the underlying engine. */
    val sessionState: StateFlow<TimerSessionState> = engine.state

    /** Backing mutable state flow for the active media ID being played or selected. */
    private val _currentMediaId = MutableStateFlow("eating")

    /** Public immutable stream indicating the currently loaded media profile ID. */
    val currentMediaId: StateFlow<String> = _currentMediaId.asStateFlow()

    init {
        setupMediaSessionCallback()
        observeEngineState()
        setupCastListener()

        // Initialize engine with default profile
        val initialProfile = repository.getProfileById("eating-mindful-20")
            ?: repository.profiles.value.firstOrNull()
            ?: DefaultProfiles.EATING
        engine.loadProfile(initialProfile)
        updateMetadata(initialProfile)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, 0L)
    }

    /**
     * Connects remote media action callbacks from Google Cast TV remotes to central session control.
     */
    private fun setupCastListener() {
        castManager.onRemotePlaybackAction = { isPlay ->
            if (isPlay) resume() else pause()
        }
    }

    /**
     * Registers transport control callbacks on the authoritative [MediaSessionCompat].
     * Directs automotive steering wheel buttons, Car HUD taps, and lockscreen actions
     * to the central handler logic.
     */
    private fun setupMediaSessionCallback() {
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                resume()
            }

            override fun onPause() {
                pause()
            }

            override fun onStop() {
                stop()
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                startProfileById(mediaId ?: "eating")
            }

            override fun onSkipToNext() {
                val nextProfile = when (_currentMediaId.value) {
                    "eating" -> "posture"
                    "posture" -> "breathing"
                    else -> "eating"
                }
                startProfileById(nextProfile)
            }

            override fun onSkipToPrevious() {
                val prevProfile = when (_currentMediaId.value) {
                    "eating" -> "breathing"
                    "breathing" -> "posture"
                    else -> "eating"
                }
                startProfileById(prevProfile)
            }

            override fun onSeekTo(pos: Long) {
                // Seek events from media controllers
            }
        })
        mediaSession.isActive = true
    }

    /**
     * Observes [TimerEngine.state] transitions and synchronizes:
     * 1. [MediaSessionCompat] playback state (PLAYING, PAUSED, STOPPED).
     * 2. Background music playback lifecycle.
     * 3. Foreground service indicators and wake locks.
     */
    private fun observeEngineState() {
        scope.launch {
            var lastStatus: SessionStatus? = null
            engine.state.collect { state ->
                val statusChanged = state.status != lastStatus
                val elapsedMs = ((state.totalSeconds - state.remainingSeconds) * 1000L).coerceAtLeast(0L)

                if (statusChanged) {
                    lastStatus = state.status
                    when (state.status) {
                        SessionStatus.RUNNING -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, elapsedMs)
                            updateMetadata(state.profile)
                            batteryOptimizer.acquireWakeLock()
                            bgMusicManager.start()
                            startMediaService()

                            if (castManager.isCasting.value) {
                                castManager.loadSession(
                                    profileName = state.profile.name,
                                    subtitle = "${state.profile.totalDurationSeconds / 60}m Mindful Session",
                                    durationSeconds = state.totalSeconds
                                )
                            }
                        }
                        SessionStatus.PAUSED -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, elapsedMs)
                            bgMusicManager.pause()
                            startMediaService()

                            if (castManager.isCasting.value) {
                                castManager.pause()
                            }
                        }
                        SessionStatus.COMPLETED -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
                            batteryOptimizer.releaseWakeLock()
                            bgMusicManager.stop()
                            repository.recordSessionCompleted(state.profile.id)

                            if (castManager.isCasting.value) {
                                castManager.stop()
                            }
                        }
                        SessionStatus.IDLE -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
                            batteryOptimizer.releaseWakeLock()
                            bgMusicManager.stop()

                            if (castManager.isCasting.value) {
                                castManager.stop()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts or resumes the active timer session across all control surfaces.
     * Synchronously commands the [TimerEngine], resumes [BackgroundMusicManager],
     * and updates [MediaSessionCompat] so the Car HUD immediately transitions to Playing.
     */
    fun resume() {
        engine.startOrResume()
    }

    /**
     * Pauses the active timer session across all control surfaces.
     * Synchronously halts the [TimerEngine], pauses [BackgroundMusicManager],
     * and updates [MediaSessionCompat] so the Car HUD immediately transitions to Paused.
     */
    fun pause() {
        engine.pause()
    }

    /**
     * Toggles between running and paused states.
     */
    fun togglePlayPause() {
        if (engine.state.value.status == SessionStatus.RUNNING) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * Completely halts the active session and resets engine state to [SessionStatus.IDLE].
     */
    fun stop() {
        engine.stop()
    }

    /**
     * Resets the active session countdown back to initial configuration values.
     */
    fun reset() {
        engine.reset()
    }

    /**
     * Loads a target [TimerProfile] and immediately commences countdown and background audio.
     *
     * @param profile Profile configuration governing duration, intervals, and bell timbre.
     */
    fun startProfile(profile: TimerProfile) {
        _currentMediaId.value = profile.id
        engine.loadProfile(profile)
        updateMetadata(profile)
        engine.startOrResume()
    }

    /**
     * Resolves a timer profile by identifier and launches it.
     * Matches known automotive keys ("eating", "posture", "breathing") or repository profile IDs.
     *
     * @param mediaId Unique profile identifier string.
     */
    fun startProfileById(mediaId: String) {
        val targetProfile = when (mediaId) {
            "eating", "eating-mindful-20" -> repository.getProfileById("eating-mindful-20") ?: DefaultProfiles.EATING
            "posture" -> repository.getProfileById("posture") ?: repository.getProfileById("mindful-reading-30") ?: DefaultProfiles.MINDFUL_READING
            "breathing" -> repository.getProfileById("pranayama-box-breath") ?: DefaultProfiles.PRANAYAMA_BOX
            else -> repository.getProfileById(mediaId) ?: repository.profiles.value.firstOrNull() ?: DefaultProfiles.EATING
        }
        startProfile(targetProfile)
    }

    /**
     * Launches a session matched from a Google Assistant voice action or deep link.
     *
     * @param durationSec Requested session length in seconds (or 0 for profile default).
     * @param message Natural language transcription query.
     */
    fun startVoiceTimer(durationSec: Int, message: String) {
        val allProfiles = repository.profiles.value
        val lowerMessage = message.lowercase().trim()
        val matched = allProfiles.find {
            lowerMessage.isNotEmpty() && (
                lowerMessage.contains(it.name.lowercase()) ||
                it.name.lowercase().contains(lowerMessage) ||
                (lowerMessage.contains("eat") && it.id.contains("eating")) ||
                (lowerMessage.contains("posture") && it.id == "posture") ||
                (lowerMessage.contains("read") && it.id == "reading") ||
                (lowerMessage.contains("walk") && it.id == "walking")
            )
        } ?: allProfiles.firstOrNull() ?: DefaultProfiles.EATING

        val finalProfile = if (durationSec > 0) {
            matched.copy(
                totalDurationSeconds = durationSec,
                intervalDurationSeconds = (durationSec / 5).coerceIn(30, 300)
            )
        } else matched
        startProfile(finalProfile)
    }

    /**
     * Broadcasts updated transport control state and playback position to automotive HUD
     * and system media controllers.
     *
     * @param state Playback state constant from [PlaybackStateCompat].
     * @param positionMs Current elapsed session time in milliseconds.
     */
    fun updatePlaybackState(state: Int, positionMs: Long = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) {
        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, positionMs, playbackSpeed)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    /**
     * Synchronizes metadata (track title, artist, album/interval subtitle, duration)
     * on [MediaSessionCompat] for display on Car HUD, Bluetooth dashboards, and Wear OS.
     *
     * @param profile Active profile configuration.
     */
    fun updateMetadata(profile: TimerProfile) {
        val durationMs = profile.totalDurationSeconds * 1000L
        val subtitle = when {
            profile.intervalDurationSeconds > 0 -> "${profile.intervalDurationSeconds / 60}m interval chime"
            else -> "Continuous mindfulness"
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, profile.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, profile.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Habit Bell • Soothing Chimes")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subtitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()

        mediaSession.setMetadata(metadata)
    }

    /**
     * Ensures the automotive foreground media service is active while audio is playing.
     */
    private fun startMediaService() {
        try {
            val intent = Intent(application, HabitBellMediaService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        } catch (_: Exception) {}
    }

    /**
     * Cleanly releases hardware handles and tears down background observers.
     */
    fun destroy() {
        engine.destroy()
        hapticManager.cancel()
        bgMusicManager.release()
        audioManager.release()
        batteryOptimizer.releaseWakeLock()
        batteryOptimizer.stopProximityMonitoring()
        mediaSession.release()
        castManager.destroy()
    }
}
