package com.habitbell.app.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.habitbell.app.MainActivity
import com.habitbell.app.engine.AudioBellManager

/**
 * MediaBrowserServiceCompat providing standard media playback integration for Android Auto,
 * Bluetooth audio metadata, Wear OS, and lock screen media controls.
 */
class HabitBellMediaService : MediaBrowserServiceCompat() {

    private val CHANNEL_ID = "habit_bell_auto_channel"
    private val NOTIFICATION_ID = 2002

    /** Dedicated media session managing playback state, audio buttons, and track metadata. */
    private lateinit var mediaSession: MediaSessionCompat

    /** Audio engine for playing Tibetan chimes directly in the vehicle. */
    private lateinit var audioManager: AudioBellManager

    private var currentMediaId = "eating"
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()

        audioManager = AudioBellManager(this)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "HabitBellMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handlePlayAction(currentMediaId)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    handlePlayAction(mediaId ?: "eating")
                }

                override fun onPause() {
                    handlePauseAction()
                }

                override fun onStop() {
                    handleStopAction()
                }

                override fun onSkipToNext() {
                    val nextId = when (currentMediaId) {
                        "eating" -> "posture"
                        "posture" -> "breathing"
                        else -> "eating"
                    }
                    handlePlayAction(nextId)
                }

                override fun onSkipToPrevious() {
                    val prevId = when (currentMediaId) {
                        "eating" -> "breathing"
                        "breathing" -> "posture"
                        else -> "eating"
                    }
                    handlePlayAction(prevId)
                }
            })
            isActive = true
        }

        // Set session token ONCE on the service - DO NOT call this inside mediaSession.apply
        sessionToken = mediaSession.sessionToken
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateMetadata("eating")
    }

    private fun handlePlayAction(mediaId: String) {
        currentMediaId = mediaId
        isPlaying = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMetadata(mediaId)

        // Play gentle chime through car speakers
        audioManager.playIntervalBell()

        // Launch session on phone app
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("habitbell://start?profile=$mediaId")
            }
            startActivity(launchIntent)
        } catch (_: Exception) {}

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePauseAction() {
        isPlaying = false
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        try {
            val pauseIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("habitbell://action/pause")
            }
            startActivity(pauseIntent)
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun handleStopAction() {
        isPlaying = false
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        try {
            val stopIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("habitbell://action/stop")
            }
            startActivity(stopIntent)
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateMetadata(mediaId: String) {
        val (title, subtitle) = when (mediaId) {
            "posture" -> "Posture Alignment" to "Spine check every 5 min"
            "breathing" -> "Driving Calm Breath" to "Mindful breath every 4 min"
            else -> "Mindful Eating" to "Tibetan chime every 1 min"
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Habit Bell • Soothing Chimes")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subtitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 2700000L)
            .build()

        mediaSession.setMetadata(metadata)
    }

    /**
     * Broadcasts updated transport state (playing, paused, stopped) to automotive and system media receivers.
     */
    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Habit Bell Car Media",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Android Auto media controls and status"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val (title, subtitle) = when (currentMediaId) {
            "posture" -> "Posture Alignment" to "Spine check chime active"
            "breathing" -> "Driving Calm Breath" to "Calm breathing chime active"
            else -> "Mindful Eating" to "Mindful intervals active"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
            )
            .build()
    }

    /**
     * Returns the root node for media tree browsing by external car head units or media clients.
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("habit_bell_root", null)
    }

    /**
     * Loads the list of playable mindful audio routines for the car media browser.
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
        audioManager.release()
        mediaSession.release()
    }
}
