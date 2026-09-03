package com.habitbell.app.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.habitbell.app.MainActivity
import com.habitbell.app.R

/**
 * Android Foreground Service maintaining active timer execution in the background.
 *
 * Responsibilities:
 * - Prevents the Android operating system from killing the app process during prolonged meditations.
 * - Displays an unobtrusive, low-priority ongoing notification showing remaining session time.
 * - Ensures silent operation without disruptive head-up alerts or vibration popups.
 */
class TimerService : Service() {

    companion object {
        /** Unique notification channel identifier for Habit Bell timer sessions. */
        const val CHANNEL_ID = "habit_bell_timer_channel"

        /** Fixed notification ID for the ongoing foreground timer notification. */
        const val NOTIFICATION_ID = 1001

        /** Intent action indicating that a timer session has started. */
        const val ACTION_START = "ACTION_START"

        /** Intent action indicating that a timer session has paused. */
        const val ACTION_PAUSE = "ACTION_PAUSE"

        /** Intent action indicating that a timer session has resumed. */
        const val ACTION_RESUME = "ACTION_RESUME"

        /** Intent action instructing the foreground service to terminate. */
        const val ACTION_STOP = "ACTION_STOP"

        /** Intent extra key passing the active session profile title. */
        const val EXTRA_TITLE = "EXTRA_TITLE"

        /** Intent extra key passing formatted remaining time string (e.g., "14:20"). */
        const val EXTRA_TIME = "EXTRA_TIME"

        /**
         * Helper method to start or update the foreground service.
         *
         * @param context Application or component context.
         * @param title Title of the active wellness profile (e.g. "Mindful Eating").
         * @param timeText Formatted remaining countdown string (e.g. "08:45").
         */
        fun startService(context: Context, title: String, timeText: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TIME, timeText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Helper method to halt and tear down the foreground service.
         *
         * @param context Application or component context.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Handles service intent actions and updates the ongoing notification.
     *
     * @param intent Incoming intent containing action and countdown extras.
     * @param flags Additional start flags.
     * @param startId Unique integer token representing this specific start request.
     * @return [START_NOT_STICKY] to avoid automatic restart if killed under severe OS memory pressure.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Active Session"
        val timeText = intent?.getStringExtra(EXTRA_TIME) ?: "00:00"

        when (action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val notification = buildNotification(title, timeText)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Builds an ongoing notification with low priority to eliminate alert sounds or vibrations.
     *
     * @param title Session profile title displayed in the notification content header.
     * @param timeText Formatted remaining countdown string.
     * @return Fully built [Notification] instance.
     */
    private fun buildNotification(title: String, timeText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Habit Bell • $title")
            .setContentText("Remaining: $timeText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Battery friendly, zero vibration/sound popups
            .build()
    }

    /**
     * Configures the system notification channel with [NotificationManager.IMPORTANCE_LOW]
     * to guarantee zero intrusive chimes or vibrations from the notification itself.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Habit Bell Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Distraction-free background session indicators"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * This service communicates via StartCommand intents rather than binding.
     *
     * @return Always `null`.
     */
    override fun onBind(intent: Intent?): IBinder? = null
}
