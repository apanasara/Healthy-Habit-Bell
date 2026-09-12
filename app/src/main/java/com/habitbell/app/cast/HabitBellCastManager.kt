package com.habitbell.app.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import org.json.JSONObject
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * # HabitBellCastManager
 *
 * Process-level singleton managing Google Cast SDK connectivity and TV streaming.
 *
 * ## Architectural Role & Component Relationships
 * Resolves Bug-1, Bug-2, FLAW-1, and FLAW-2 by establishing native Google Cast sender architecture:
 * - Eliminates phone screen mirroring and avoids opening URLs inside the phone's browser.
 * - Streams mindfulness sessions directly to Google Cast / Chromecast / Google TV hardware.
 * - Communicates with [com.habitbell.app.engine.CentralSessionHandler] to synchronize playback
 *   states bi-directionally between the mobile phone, TV display, and automotive head units.
 * - Decouples buffering from user-initiated pauses to prevent 15-second connect/disconnect churn.
 * - Integrates with [RemoteMediaClient] to deliver track metadata, artwork, and duration to the TV.
 *
 * ## Concurrency & Lifecycle
 * - Instantiated lazily via [getInstance] with the application context.
 * - Cast callbacks are dispatched on the main thread via Android SDK guarantees.
 * - Thread-safe state emissions using Kotlin [StateFlow].
 *
 * @param context Process-level application context.
 */
class HabitBellCastManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HabitBellCastManager"

        /** Default Zen artwork image URL displayed on the TV receiver during mindfulness sessions. */
        const val DEFAULT_ARTWORK_URL = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=1200&auto=format&fit=crop&q=80"

        /** High-res sacred lotus artwork for Pranayama sessions. */
        const val PRANAYAMA_ARTWORK_URL = "https://images.unsplash.com/photo-1545205597-3d9d02c29597?w=1200&auto=format&fit=crop&q=80"

        /** Custom Cast Message Bus namespace for Habit Bell live telemetry and control. */
        const val CUSTOM_NAMESPACE = "urn:x-cast:com.habitbell.cast"

        /** Fallback online ambient stream if local server is unreachable. */
        const val DEFAULT_FALLBACK_STREAM_URL = "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=meditation-bowl-ambient-60s.mp3"

        @Volatile
        private var INSTANCE: HabitBellCastManager? = null

        /**
         * Returns or initializes the process-level [HabitBellCastManager] singleton instance.
         *
         * @param context Application or component context.
         * @return Authoritative [HabitBellCastManager] instance.
         */
        fun getInstance(context: Context): HabitBellCastManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HabitBellCastManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** Handle to the Google Cast framework context. Nullable if Play Services is absent. */
    private var castContext: CastContext? = null

    /** Cast session manager responsible for active device connections. */
    private var sessionManager: SessionManager? = null

    /** Active Google Cast session handle with the connected TV. */
    var currentCastSession: CastSession? = null
        private set

    /** Backing flow indicating whether the app is actively casting to a TV device. */
    private val _isCasting = MutableStateFlow(false)

    /** Public read-only stream indicating active TV casting status. */
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    /** Backing flow holding the human-readable name of the connected Cast device (e.g. "Living Room TV"). */
    private val _castDeviceName = MutableStateFlow<String?>(null)

    /** Public read-only stream emitting the connected device name. */
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    /** Backing flow holding the active Google Cast connection state enum value. */
    private val _castState = MutableStateFlow(CastState.NO_DEVICES_AVAILABLE)

    /** Public read-only stream emitting the active [CastState]. */
    val castState: StateFlow<Int> = _castState.asStateFlow()

    /** Backing flow holding current connected Cast TV hardware volume level (0.0f..1.0f). */
    private val _castVolume = MutableStateFlow(1.0f)

    /** Public read-only stream emitting the connected TV hardware volume level (0.0f..1.0f). */
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    /** Cast listener observing hardware volume modifications dispatched from physical TV remotes. */
    private val castListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            val session = currentCastSession ?: return
            if (isDispatchingLocally) return
            try {
                val vol = session.volume.toFloat()
                Log.d(TAG, "Remote TV hardware volume changed: $vol (isMute=${session.isMute})")
                _castVolume.value = vol
            } catch (e: Exception) {
                Log.w(TAG, "Error querying Cast session volume: ${e.message}")
            }
        }
    }

    /** Callback interface notifying central session orchestration of TV remote interactions. */
    var onRemotePlaybackAction: ((isPlay: Boolean) -> Unit)? = null

    /** Callback interface notifying central session orchestration of TV remote reset interactions. */
    var onRemoteResetAction: (() -> Unit)? = null

    /** Callback interface notifying central session orchestration that the TV web receiver is ready for immediate telemetry sync. */
    var onReceiverReady: (() -> Unit)? = null

    /** Guard flag preventing local mobile play/pause/load dispatches from echoing back as TV remote commands. */
    @Volatile
    private var isDispatchingLocally = false

    /** Tracks the last confirmed player state to eliminate redundant status transitions. */
    private var lastObservedPlayerState: Int = MediaStatus.PLAYER_STATE_UNKNOWN

    /** Remote media client listener observing playback transitions on the TV. */
    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = currentCastSession?.remoteMediaClient ?: return
            val playerState = client.playerState

            // 1. Ignore updates provoked by mobile's own local commands
            if (isDispatchingLocally) {
                Log.d(TAG, "Ignoring RemoteMediaClient status update during local mobile dispatch (playerState=$playerState)")
                return
            }

            // 2. Explicitly ignore transient buffering, loading, or idle states to prevent 15s reconnect churn
            if (playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
                playerState == MediaStatus.PLAYER_STATE_LOADING ||
                playerState == MediaStatus.PLAYER_STATE_IDLE ||
                playerState == MediaStatus.PLAYER_STATE_UNKNOWN
            ) {
                Log.d(TAG, "Ignoring transient Cast player state: $playerState (buffering/idle/loading)")
                return
            }

            // 3. Only dispatch when player genuinely transitions between PLAYING and PAUSED
            if (playerState != lastObservedPlayerState) {
                lastObservedPlayerState = playerState
                when (playerState) {
                    MediaStatus.PLAYER_STATE_PLAYING -> {
                        Log.i(TAG, "Remote TV resumed playback via physical TV remote")
                        onRemotePlaybackAction?.invoke(true)
                    }
                    MediaStatus.PLAYER_STATE_PAUSED -> {
                        Log.i(TAG, "Remote TV paused playback via physical TV remote")
                        onRemotePlaybackAction?.invoke(false)
                    }
                }
            }
        }
    }

    /** Listener monitoring Google Cast connection and disconnection lifecycles. */
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            Log.i(TAG, "Cast session starting...")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.i(TAG, "Cast session successfully started with ID: $sessionId")
            bindSession(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Cast session start failed with code: $error")
            unbindSession()
        }

        override fun onSessionEnding(session: CastSession) {
            Log.i(TAG, "Cast session ending...")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.i(TAG, "Cast session ended (code $error)")
            unbindSession()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            Log.i(TAG, "Cast session resuming...")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.i(TAG, "Cast session resumed (wasSuspended=$wasSuspended)")
            bindSession(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Cast session resume failed: $error")
            unbindSession()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            Log.w(TAG, "Cast session suspended: $reason")
        }
    }

    /** Listener monitoring framework device availability changes. */
    private val castStateListener = CastStateListener { newState ->
        _castState.value = newState
        Log.d(TAG, "Cast state changed to: $newState")
    }

    init {
        initializeCastFramework()
    }

    /**
     * Safely initializes the Google Cast Framework context and attaches session listeners.
     */
    private fun initializeCastFramework() {
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
            castContext?.addCastStateListener(castStateListener)

            // Inspect current active session if already established
            val current = sessionManager?.currentCastSession
            if (current != null && current.isConnected) {
                bindSession(current)
            }
            _castState.value = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE
            Log.i(TAG, "Google Cast Framework initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Google Cast Framework not available on this device: ${e.message}")
        }
    }

    /**
     * Binds an active Cast session, registers media client observers, and attaches custom message bus listener.
     *
     * @param session Active [CastSession] connected to the TV.
     */
    private fun bindSession(session: CastSession) {
        currentCastSession = session
        val deviceName = session.castDevice?.friendlyName ?: "Google Cast TV"
        _castDeviceName.value = deviceName
        _isCasting.value = true

        session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)

        // Attach custom message channel for TV remote signals and live telemetry
        try {
            session.setMessageReceivedCallbacks(CUSTOM_NAMESPACE) { _, namespace, message ->
                Log.d(TAG, "Custom message received on $namespace: $message")
                handleIncomingCustomMessage(message)
            }
            Log.i(TAG, "Registered custom message channel for $CUSTOM_NAMESPACE")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register custom message channel on Cast session: ${e.message}")
        }

        // Attach CastListener to observe physical TV remote volume adjustments
        try {
            session.addCastListener(castListener)
            val currentVol = session.volume.toFloat()
            _castVolume.value = currentVol
            Log.i(TAG, "Attached castListener to CastSession (initial TV volume: $currentVol)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach castListener or query initial volume: ${e.message}")
        }

        Log.i(TAG, "Bound Cast session to device: $deviceName")
        onReceiverReady?.invoke()
    }

    /**
     * Unbinds the active Cast session, detaches custom message bus, and clears device references.
     */
    private fun unbindSession() {
        try {
            currentCastSession?.removeCastListener(castListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing castListener: ${e.message}")
        }
        try {
            currentCastSession?.removeMessageReceivedCallbacks(CUSTOM_NAMESPACE)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing custom message handler: ${e.message}")
        }
        currentCastSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        currentCastSession = null
        _castDeviceName.value = null
        _isCasting.value = false
        Log.i(TAG, "Unbound Cast session")
    }

    /**
     * Loads a wellness session onto the TV's Google Cast receiver.
     *
     * Configures title, subtitle, duration, and high-resolution mindful artwork
     * for display on the TV receiver.
     *
     * @param profileName Name of the active mindfulness routine (e.g., "Pranayama (Hatha Yoga)").
     * @param subtitle Explanatory subtitle (e.g., "Round 1/12 • 4:16:8:16").
     * @param durationSeconds Total duration of the routine in seconds.
     * @param streamUrl Optional streaming audio URL to play over the TV hardware.
     * @param artworkUrl Optional high-resolution mindful artwork image URL.
     */
    fun loadSession(
        profileName: String,
        subtitle: String,
        durationSeconds: Int,
        streamUrl: String? = null,
        artworkUrl: String? = null
    ) {
        val client = currentCastSession?.remoteMediaClient ?: return

        isDispatchingLocally = true
        try {
            val finalArtwork = artworkUrl ?: DEFAULT_ARTWORK_URL
            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, profileName)
                putString(MediaMetadata.KEY_SUBTITLE, subtitle)
                putString(MediaMetadata.KEY_ARTIST, "Habit Bell • Meditative Chimes")
                putString(MediaMetadata.KEY_ALBUM_TITLE, "Habit Bell Living Room TV")
                addImage(WebImage(Uri.parse(finalArtwork)))
            }

            // Custom Web Receiver is hosted via HTTPS (GitHub Pages). Insecure HTTP media streams
            // will be blocked by Chromium's mixed-content security policy.
            val isCustomReceiver = CastOptionsProvider.customReceiverAppId != null
            val localServer = LocalCastWebServer(context)
            val localIp = localServer.getLocalIpAddress()
            val mediaUrl = streamUrl ?: if (!isCustomReceiver && localIp != null) {
                "http://$localIp:8888/media/aum.mp3"
            } else {
                DEFAULT_FALLBACK_STREAM_URL
            }

            val mediaInfo = MediaInfo.Builder(mediaUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("audio/mp3")
                .setMetadata(movieMetadata)
                .setStreamDuration(durationSeconds * 1000L)
                .build()

            val requestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(0L)
                .build()

            client.load(requestData)
            Log.i(TAG, "Loaded session '$profileName' onto Google Cast TV (mediaUrl=$mediaUrl)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session onto Google Cast TV", e)
        } finally {
            scope.launch {
                delay(1200)
                isDispatchingLocally = false
            }
        }
    }

    /**
     * Directs the connected TV receiver to resume playback.
     */
    fun play() {
        isDispatchingLocally = true
        currentCastSession?.remoteMediaClient?.play()
        scope.launch {
            delay(600)
            isDispatchingLocally = false
        }
    }

    /**
     * Directs the connected TV receiver to pause playback.
     */
    fun pause() {
        isDispatchingLocally = true
        currentCastSession?.remoteMediaClient?.pause()
        scope.launch {
            delay(600)
            isDispatchingLocally = false
        }
    }

    /**
     * Toggles play/pause state on the connected TV receiver.
     */
    fun togglePlayPause() {
        val client = currentCastSession?.remoteMediaClient ?: return
        if (client.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    /**
     * Halts media playback on the TV receiver.
     */
    fun stop() {
        isDispatchingLocally = true
        currentCastSession?.remoteMediaClient?.stop()
        lastObservedPlayerState = MediaStatus.PLAYER_STATE_UNKNOWN
        scope.launch {
            delay(600)
            isDispatchingLocally = false
        }
    }

    /**
     * Dispatches a JSON string payload across the Google Cast Custom Message Bus to the Web Receiver.
     *
     * Enables continuous real-time transmission of countdown clock digits, interval boundaries,
     * Sanskrit pranayama cues, Surya Namaskar asanas, and volume adjustments directly to TV screens.
     *
     * @param json Serialized JSON telemetry payload adhering to the Habit Bell Cast Protocol.
     */
    fun sendCustomMessage(json: String) {
        val session = currentCastSession ?: return
        if (!session.isConnected) return
        try {
            session.sendMessage(CUSTOM_NAMESPACE, json)?.setResultCallback { result ->
                if (!result.status.isSuccess) {
                    Log.w(TAG, "Failed to transmit Cast message: ${result.status.statusMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception transmitting Cast message to receiver", e)
        }
    }

    /**
     * Handles incoming custom JSON messages received from the Google Cast Custom Web Receiver.
     *
     * Interprets TV remote control commands (play/pause/toggle) to maintain two-way synchronization
     * between the living room TV and mobile phone state machine.
     *
     * @param message Raw JSON message string received across [CUSTOM_NAMESPACE].
     */
    private fun handleIncomingCustomMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "play" -> {
                    Log.i(TAG, "TV Custom Receiver requested PLAY")
                    onRemotePlaybackAction?.invoke(true)
                }
                "pause" -> {
                    Log.i(TAG, "TV Custom Receiver requested PAUSE")
                    onRemotePlaybackAction?.invoke(false)
                }
                "toggle" -> {
                    Log.i(TAG, "TV Custom Receiver requested TOGGLE")
                    togglePlayPause()
                }
                "reset" -> {
                    Log.i(TAG, "TV Custom Receiver requested RESET")
                    onRemoteResetAction?.invoke()
                }
                "ready", "ping" -> {
                    Log.i(TAG, "TV Custom Receiver signaled ready/ping; synchronizing session state")
                    onReceiverReady?.invoke()
                }
                "volume" -> {
                    val vol = json.optDouble("volume", -1.0)
                    if (vol in 0.0..1.0) {
                        _castVolume.value = vol.toFloat()
                        Log.i(TAG, "TV Custom Receiver signaled volume update: $vol")
                    }
                }
                else -> {
                    Log.d(TAG, "Unhandled TV receiver message type: ${json.optString("type")}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse incoming Cast message: $message", e)
        }
    }

    /**
     * Sets the physical Google Cast / TV hardware master volume directly.
     *
     * Dispatches volume update across the Google Cast SDK [CastSession], immediately alters the
     * connected TV's master output volume, and broadcasts a custom JSON message to the Web Receiver.
     *
     * @param volume Target volume gain in the closed interval `[0.0f, 1.0f]`.
     */
    fun setDeviceVolume(volume: Float) {
        val session = currentCastSession ?: return
        val safeGain = volume.coerceIn(0f, 1f)
        isDispatchingLocally = true
        try {
            session.volume = safeGain.toDouble()
            _castVolume.value = safeGain
            Log.d(TAG, "Dispatched volume $safeGain directly to Cast device")
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting volume on CastSession", e)
        } finally {
            scope.launch {
                delay(300)
                isDispatchingLocally = false
            }
        }

        // Also transmit volume event across custom namespace for Web Receiver display
        try {
            val payload = JSONObject().apply {
                put("type", "volume")
                put("volume", safeGain.toDouble())
            }
            sendCustomMessage(payload.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending volume telemetry to Cast receiver: ${e.message}")
        }
    }

    /**
     * Cleanly terminates the active Google Cast connection without leaving orphaned routes.
     */
    fun disconnect() {
        sessionManager?.endCurrentSession(true)
        unbindSession()
    }

    /**
     * Releases observers and unbinds framework listeners.
     */
    fun destroy() {
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        castContext?.removeCastStateListener(castStateListener)
        unbindSession()
    }
}
