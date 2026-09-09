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
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.data.repository.TimerRepository
import org.json.JSONObject
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

    /** Tracks active cast session routine ID to avoid duplicate loadSession() calls on pause/resume. */
    private var lastCastProfileId: String? = null

    /** AirPlay 2 discovery manager enabling direct casting to Apple TV hardware over Wi-Fi. */
    val airPlayManager: com.habitbell.app.cast.AirPlayCastManager = com.habitbell.app.cast.AirPlayCastManager.getInstance(application)

    /** DIAL & SSDP discoverer enabling zero-click launch on Samsung Tizen & LG webOS smart TVs. */
    val dialTvDiscoverer: com.habitbell.app.cast.DialTvDiscoverer = com.habitbell.app.cast.DialTvDiscoverer.getInstance(application)

    /** Health and step tracking orchestrator connecting Google Fit, Health Connect, Apple bridge & sensors. */
    val healthStepManager: com.habitbell.app.health.HealthStepManager = com.habitbell.app.health.HealthStepManager(application)

    /** Gentle lady voice guidance coordinator for Pranayama breathwork. */
    val voiceGuide: PranayamaVoiceGuide = PranayamaVoiceGuide(application, bgMusicManager)

    /** Voice guidance player for Surya Namaskar Asana cues and Solar Mantras. */
    val suryaVoicePlayer: com.habitbell.app.audio.SuryaVoicePlayer = com.habitbell.app.audio.SuryaVoicePlayer(application, bgMusicManager)

    /** Core 1Hz heartbeat finite state machine governing timer countdowns. */
    val engine: TimerEngine = TimerEngine(audioManager, hapticManager).apply {
        voiceGuide = this@CentralSessionHandler.voiceGuide
        suryaVoicePlayer = this@CentralSessionHandler.suryaVoicePlayer
    }

    /** Unified screen display automation orchestrator (Pocket, Car, TV, and Watch modes). */
    val displayAutomationManager: DisplayAutomationManager = DisplayAutomationManager(
        application = application,
        sessionStateProvider = { engine.state.value },
        castManager = castManager,
        airPlayManager = airPlayManager
    )

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
        setupHealthStepListener()

        // Initialize engine with default profile
        val initialProfile = repository.getProfileById("eating-mindful-20")
            ?: repository.profiles.value.firstOrNull()
            ?: DefaultProfiles.EATING
        engine.loadProfile(initialProfile)
        updateMetadata(initialProfile)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, 0L)
    }

    /**
     * Ingests live step updates and pipes them to the active [TimerEngine] session.
     */
    private fun setupHealthStepListener() {
        scope.launch {
            healthStepManager.activeStepUpdate.collect { stepUpdate ->
                if (engine.state.value.status == SessionStatus.RUNNING) {
                    engine.onStepCountUpdated(stepUpdate.sessionSteps, stepUpdate.cadenceStepsPerMinute)
                }
            }
        }
    }

    /**
     * Connects remote media action callbacks from Google Cast TV remotes to central session control,
     * and synchronizes running session state to newly connected Cast displays.
     */
    private fun setupCastListener() {
        castManager.onRemotePlaybackAction = { isPlay ->
            if (isPlay) resume() else pause()
        }

        // Auto-load running session onto TV if Cast connection is established mid-session
        scope.launch {
            castManager.isCasting.collect { isCasting ->
                if (isCasting && engine.state.value.status == SessionStatus.RUNNING) {
                    val state = engine.state.value
                    lastCastProfileId = state.profile.id
                    val subtitle = buildSessionSubtitle(state)
                    val artwork = resolveArtworkForProfile(state.profile)
                    castManager.loadSession(
                        profileName = state.profile.name,
                        subtitle = subtitle,
                        durationSeconds = state.totalSeconds,
                        artworkUrl = artwork
                    )
                    // Push initial telemetry snapshot immediately to TV receiver
                    val initialTelemetry = buildCastTelemetryJson(state)
                    castManager.sendCustomMessage(initialTelemetry)
                }
            }
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
                // Seek events not applicable to guided intervals
            }
        })
        mediaSession.isActive = true
    }

    /**
     * Observes continuous timer state mutations from [TimerEngine] and orchestrates:
     * 1. Automotive MediaSession playback state updates.
     * 2. Background music playback lifecycle.
     * 3. Foreground service indicators and wake locks.
     * 4. Google Cast media receiver synchronization without buffering churn.
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
                            batteryOptimizer.acquireWifiLock()
                            displayAutomationManager.startMonitoring()
                            bgMusicManager.start()
                            startMediaService()

                            if (state.profile.isStepTrackingEnabled) {
                                healthStepManager.startSession()
                            }

                            if (castManager.isCasting.value) {
                                if (lastCastProfileId != state.profile.id) {
                                    lastCastProfileId = state.profile.id
                                    val subtitle = buildSessionSubtitle(state)
                                    val artwork = resolveArtworkForProfile(state.profile)
                                    castManager.loadSession(
                                        profileName = state.profile.name,
                                        subtitle = subtitle,
                                        durationSeconds = state.totalSeconds,
                                        artworkUrl = artwork
                                    )
                                } else {
                                    castManager.play()
                                }
                            }
                        }
                        SessionStatus.PAUSED -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, elapsedMs)
                            bgMusicManager.pause()
                            startMediaService()

                            if (state.profile.isStepTrackingEnabled) {
                                healthStepManager.pauseSession()
                            }

                            if (castManager.isCasting.value) {
                                castManager.pause()
                            }
                        }
                        SessionStatus.COMPLETED -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
                            batteryOptimizer.releaseWakeLock()
                            displayAutomationManager.stopMonitoring()
                            bgMusicManager.stop()
                            repository.recordSessionCompleted(state.profile.id)
                            lastCastProfileId = null

                            if (state.profile.isStepTrackingEnabled) {
                                val finalSteps = state.currentSteps
                                scope.launch {
                                    healthStepManager.recordCompletedWalkingSession(state.profile.name, finalSteps)
                                }
                                healthStepManager.stopSession()
                            }

                            if (castManager.isCasting.value) {
                                castManager.stop()
                            }
                        }
                        SessionStatus.IDLE -> {
                            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
                            batteryOptimizer.releaseWakeLock()
                            displayAutomationManager.stopMonitoring()
                            bgMusicManager.stop()
                            healthStepManager.resetSession()
                            lastCastProfileId = null

                            if (castManager.isCasting.value) {
                                castManager.stop()
                            }
                        }
                    }
                }

                // 5. Continuously synchronize live session countdown & telemetry to Google Cast receiver
                if (castManager.isCasting.value) {
                    val telemetryPayload = buildCastTelemetryJson(state)
                    castManager.sendCustomMessage(telemetryPayload)
                }
            }
        }
    }

    /**
     * Resolves high-resolution mindful artwork URL tailored to the active routine category.
     *
     * @param profile Active [TimerProfile].
     * @return Curated high-res HTTPS artwork image URL.
     */
    private fun resolveArtworkForProfile(profile: TimerProfile): String {
        return when {
            profile.type == TimerType.MULTI_INTERVAL || profile.pranayamaConfig != null ->
                "https://images.unsplash.com/photo-1545205597-3d9d02c29597?w=1200&auto=format&fit=crop&q=80" // Sacred Lotus Meditation
            profile.type == TimerType.COMPOUND || profile.name.contains("Surya", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=1200&auto=format&fit=crop&q=80" // Golden Dawn Sun Salutation
            profile.name.contains("Eat", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=1200&auto=format&fit=crop&q=80" // Mindful Dining
            profile.name.contains("Walk", ignoreCase = true) || profile.stepGoal != null ->
                "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=1200&auto=format&fit=crop&q=80" // Mindful Forest Path
            profile.name.contains("Reiki", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1515377905703-c4788e51af15?w=1200&auto=format&fit=crop&q=80" // Energy Healing & Zen Stones
            else ->
                "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=1200&auto=format&fit=crop&q=80"
        }
    }

    /**
     * Constructs descriptive contextual subtitle for the Cast receiver based on session mode.
     *
     * @param state Active [TimerSessionState].
     * @return User-friendly subtitle string (e.g. "Breathwork • Round 1/12 • Adhama Classical").
     */
    private fun buildSessionSubtitle(state: TimerSessionState): String {
        return when {
            state.profile.pranayamaConfig != null ->
                "Breathwork • Round ${state.currentRound}/${state.totalRounds} • Adhama Classical"
            state.profile.compoundConfig != null ->
                "Surya Namaskar • Round ${state.currentRound}/${state.totalRounds} • 12 Postures"
            state.profile.stepGoal != null ->
                "Mindful Walk • Goal: ${state.profile.stepGoal} steps"
            else ->
                "${state.profile.totalDurationSeconds / 60}m Mindful Session • Habit Bell"
        }
    }

    /**
     * Serializes live [TimerSessionState] into a compact JSON telemetry payload for the Google Cast Custom Web Receiver.
     *
     * Transmits countdown timing strings, interval offsets, Sanskrit breath cues, Surya Namaskar poses,
     * and completion status across the native Google Cast message bus.
     *
     * @param state Active [TimerSessionState] snapshot.
     * @param triggerBell Optional flag instructing the TV receiver to synthesize a Tibetan bell chime locally.
     * @param bellFrequency Audio synthesis frequency in Hertz (default 432 Hz).
     * @return Formatted JSON string adhering to the Habit Bell Cast protocol.
     */
    fun buildCastTelemetryJson(
        state: TimerSessionState,
        triggerBell: Boolean = false,
        bellFrequency: Int = 432
    ): String {
        val json = JSONObject()
        json.put("type", "state")
        json.put("formattedTime", state.formattedRemainingTime)
        json.put("formattedNextBell", state.formattedNextBellTime)
        json.put("profileName", state.profile.name)
        json.put("status", state.status.name)
        json.put("currentRound", state.currentRound)
        json.put("totalRounds", state.totalRounds)
        json.put("progressFraction", state.progressFraction.toDouble())

        // Multi-interval / Pranayama tracking
        val pranayamaPhase = state.currentPranayamaPhase
        if (pranayamaPhase != null) {
            json.put("pranayamaPhase", pranayamaPhase.name)
            json.put("pranayamaSanskrit", pranayamaPhase.sanskritName)
            json.put("pranayamaDisplay", pranayamaPhase.displayName)
        } else {
            json.put("pranayamaPhase", "")
        }

        // Compound Sequencer / Surya Namaskar tracking
        val currentPose = state.currentPose
        if (currentPose != null) {
            json.put("poseName", currentPose.name)
            json.put("poseSanskrit", currentPose.sanskritName)
            json.put("poseBreath", currentPose.breathCue)
        } else {
            json.put("poseName", "")
        }

        json.put("triggerBell", triggerBell)
        json.put("bellFrequency", bellFrequency)
        return json.toString()
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
            profile.stepGoal != null && profile.stepInterval != null ->
                "Goal: %,d steps • Bell every %,d steps".format(profile.stepGoal, profile.stepInterval)
            profile.stepGoal != null ->
                "Goal: %,d steps".format(profile.stepGoal)
            profile.stepInterval != null ->
                "Bell every %,d steps".format(profile.stepInterval)
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
        healthStepManager.destroy()
        hapticManager.cancel()
        bgMusicManager.release()
        audioManager.release()
        batteryOptimizer.releaseWakeLock()
        batteryOptimizer.stopProximityMonitoring()
        mediaSession.release()
        castManager.destroy()
    }
}
