package com.habitbell.app.auto

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

class HabitBellMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "HabitBellMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }

                override fun onPause() {
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onStop() {
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }
            })
            setSessionToken(sessionToken)
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("habit_bell_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val mediaItems = mutableListOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("eating")
                    .setTitle("Mindful Eating (15m)")
                    .setSubtitle("3m interval Tibetan bell chime")
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
        mediaSession.release()
    }
}
