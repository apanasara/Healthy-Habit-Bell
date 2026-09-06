package com.habitbell.app.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
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
 * Resolves Bug-1 and Bug-2 by establishing native Google Cast sender architecture:
 * - Eliminates phone screen mirroring and avoids opening URLs inside the phone's browser.
 * - Streams mindfulness sessions directly to Google Cast / Chromecast / Google TV hardware.
 * - Communicates with [com.habitbell.app.engine.CentralSessionHandler] to synchronize playback
 *   states bi-directionally between the mobile phone, TV display, and automotive head units.
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
        private const val DEFAULT_ARTWORK_URL = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=1200&auto=format&fit=crop&q=80"

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

    /** Callback interface notifying central session orchestration of TV remote interactions. */
    var onRemotePlaybackAction: ((isPlay: Boolean) -> Unit)? = null

    /** Remote media client listener observing playback transitions on the TV. */
    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = currentCastSession?.remoteMediaClient ?: return
            val isPlaying = client.isPlaying
            Log.d(TAG, "RemoteMediaClient status updated: isPlaying=$isPlaying")
            onRemotePlaybackAction?.invoke(isPlaying)
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
     * Binds an active Cast session and registers media client observers.
     *
     * @param session Active [CastSession] connected to the TV.
     */
    private fun bindSession(session: CastSession) {
        currentCastSession = session
        val deviceName = session.castDevice?.friendlyName ?: "Google Cast TV"
        _castDeviceName.value = deviceName
        _isCasting.value = true

        session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        Log.i(TAG, "Bound Cast session to device: $deviceName")
    }

    /**
     * Unbinds the active Cast session and clears device references.
     */
    private fun unbindSession() {
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
     * @param profileName Name of the active mindfulness routine (e.g., "Mindful Eating").
     * @param subtitle Explanatory subtitle (e.g., "Habit Bell • 20m Session").
     * @param durationSeconds Total duration of the routine in seconds.
     * @param streamUrl Optional streaming audio URL to play over the TV hardware.
     */
    fun loadSession(
        profileName: String,
        subtitle: String,
        durationSeconds: Int,
        streamUrl: String? = null
    ) {
        val client = currentCastSession?.remoteMediaClient ?: return

        try {
            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, profileName)
                putString(MediaMetadata.KEY_SUBTITLE, subtitle)
                putString(MediaMetadata.KEY_ARTIST, "Habit Bell • Meditative Chimes")
                putString(MediaMetadata.KEY_ALBUM_TITLE, "Habit Bell Living Room TV")
                addImage(WebImage(Uri.parse(DEFAULT_ARTWORK_URL)))
            }

            // High-quality ambient audio stream or bundled media stream
            val mediaUrl = streamUrl ?: "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=meditation-bowl-ambient-60s.mp3"

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
            Log.i(TAG, "Loaded session '$profileName' onto Google Cast TV")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session onto Google Cast TV", e)
        }
    }

    /**
     * Directs the connected TV receiver to resume playback.
     */
    fun play() {
        currentCastSession?.remoteMediaClient?.play()
    }

    /**
     * Directs the connected TV receiver to pause playback.
     */
    fun pause() {
        currentCastSession?.remoteMediaClient?.pause()
    }

    /**
     * Toggles play/pause state on the connected TV receiver.
     */
    fun togglePlayPause() {
        val client = currentCastSession?.remoteMediaClient ?: return
        if (client.isPlaying) {
            client.pause()
        } else {
            client.play()
        }
    }

    /**
     * Halts media playback on the TV receiver.
     */
    fun stop() {
        currentCastSession?.remoteMediaClient?.stop()
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
