package com.habitbell.app.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.habitbell.app.MainActivity
import com.habitbell.app.R
import com.habitbell.app.engine.CentralSessionHandler
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * # HabitBellMediaService
 *
 * Primary [MediaBrowserServiceCompat] implementation exposing Habit Bell routines to Android Auto,
 * Automotive OS, Bluetooth car head units, Wear OS, and system lockscreen media controls.
 *
 * ## Architectural Role & Single Session Handler Integration
 * Binds directly to the authoritative [CentralSessionHandler] process singleton.
 * Does NOT instantiate private MediaSessions or disconnected audio engines:
 * - Emits [CentralSessionHandler.sessionToken] so the Car HUD, mobile app, and watch share a single source of truth.
 * - Reflects real-time playback state transitions (PLAYING, PAUSED, STOPPED) from [CentralSessionHandler].
 * - Forwards media button intents and browsable catalog choices to [CentralSessionHandler].
 *
 * ## Concurrency & Lifecycle
 * - Service lifecycle managed by Android Media Framework and foreground playback demands.
 * - Subscribes to [CentralSessionHandler.sessionState] on [Dispatchers.Main] via a scoped supervisor job.
 */
class HabitBellMediaService : MediaBrowserServiceCompat() {

    companion object {
        /** Dedicated notification channel identifier for Android Auto media sessions. */
        const val CHANNEL_ID = "habit_bell_auto_channel"

        /** Fixed ongoing notification ID for automotive media foreground execution. */
        const val NOTIFICATION_ID = 2002
    }

    /** Authoritative process-level session handler. */
    private lateinit var sessionHandler: CentralSessionHandler

    /** Service-bound coroutine scope for observing state emissions. */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Observer job tracking [CentralSessionHandler.sessionState] emissions. */
    private var stateObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Acquire process-level single session handler
        sessionHandler = CentralSessionHandler.getInstance(this)

        // Set session token ONCE on the service pointing to the single authoritative session
        sessionToken = sessionHandler.sessionToken

        createNotificationChannel()
        observeSessionState()
    }

    /** Tracks whether the service is currently promoted to foreground execution. */
    private var isForeground = false

    /** Last recorded session status to detect actual lifecycle transitions. */
    private var lastRecordedStatus: SessionStatus? = null

    /**
     * Subscribes to [CentralSessionHandler.sessionState] to synchronize the ongoing
     * automotive media notification with current playback status without IPC thrashing.
     */
    private fun observeSessionState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            sessionHandler.sessionState.collect { state ->
                val statusChanged = state.status != lastRecordedStatus
                lastRecordedStatus = state.status

                when (state.status) {
                    SessionStatus.RUNNING -> {
                        if (!isForeground) {
                            // Promote to foreground once on active transition
                            val notification = buildNotification(state)
                            startForeground(NOTIFICATION_ID, notification)
                            isForeground = true
                        } else if (statusChanged || state.remainingSeconds % 5 == 0) {
                            // Non-blocking content update throttled to every 5s to eliminate 1Hz IPC spam
                            val notification = buildNotification(state)
                            notificationManager.notify(NOTIFICATION_ID, notification)
                        }
                    }
                    SessionStatus.PAUSED -> {
                        if (isForeground) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                            isForeground = false
                        }
                        if (statusChanged) {
                            val notification = buildNotification(state)
                            notificationManager.notify(NOTIFICATION_ID, notification)
                        }
                    }
                    SessionStatus.COMPLETED, SessionStatus.IDLE -> {
                        if (isForeground) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            isForeground = false
                        } else {
                            notificationManager.cancel(NOTIFICATION_ID)
                        }
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(sessionHandler.mediaSession, intent)
        return START_NOT_STICKY
    }

    /**
     * Responds to the removal of the app task from the Android Recents screen ("Clear All" / swipe away).
     *
     * Halts the active timer session across all subsystems, silences ambient soundscapes,
     * releases wake locks, dismisses the ongoing foreground media notification, and terminates
     * this service cleanly to prevent runaway background timers.
     *
     * @param rootIntent Intent that started the task that is now removed, if available.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Cleanly halt active session across audio, haptics, wake locks, and timers
        sessionHandler.stop()

        // Dismiss and remove ongoing foreground notification
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }

        // Terminate background service completely
        stopSelf()
    }

    /**
     * Constructs a driver-optimized [NotificationCompat.MediaStyle] ongoing notification
     * linked directly to the authoritative [CentralSessionHandler.sessionToken].
     *
     * @param state Active [TimerSessionState] snapshot.
     * @return Formatted ongoing [Notification] instance.
     */
    private fun buildNotification(state: TimerSessionState): Notification {
        val isPlaying = state.status == SessionStatus.RUNNING
        val title = state.profile.name
        val subtitle = "${state.formattedRemainingTime} • ${if (isPlaying) "Active" else "Paused"}"

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_NAVIGATE_TO_SESSION", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PAUSE
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY
                )
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_STOP
            )
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Habit Bell • $title")
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }

    /**
     * Configures the system notification channel with [NotificationManager.IMPORTANCE_LOW]
     * to eliminate intrusive alert sounds or head-up popups while driving.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Habit Bell Car Media",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Android Auto media controls and status"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Returns the root node for media tree browsing by external car head units or media clients.
     *
     * @param clientPackageName Calling package identifier.
     * @param clientUid Calling user identifier.
     * @param rootHints Optional bundle parameters passed by the client.
     * @return [BrowserRoot] handle for browsing.
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("habit_bell_root", null)
    }

    /**
     * Loads the list of playable mindful audio routines for the vehicle media browser.
     *
     * @param parentId Browsable hierarchy parent identifier.
     * @param result Result callback receiver.
     */
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val mediaItems = mutableListOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("eating")
                    .setTitle("Mindful Eating (45m)")
                    .setSubtitle("1m interval Tibetan bell chime")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            ),
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("posture")
                    .setTitle("Posture Alignment (30m)")
                    .setSubtitle("5m gentle spine check bell")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            ),
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("breathing")
                    .setTitle("Driving Calm Breath (20m)")
                    .setSubtitle("4m mindful breathing bell")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )
        )
        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        super.onDestroy()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        // Do NOT release sessionHandler.mediaSession here since it is owned by the process singleton
    }
}
