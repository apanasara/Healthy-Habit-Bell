package com.habitbell.app.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.habitbell.app.MainActivity

/**
 * # CastOptionsProvider
 *
 * Configures the Google Cast SDK framework options and default receiver for Habit Bell.
 *
 * ## Architectural Role & Component Relationships
 * This class is referenced by the Google Cast SDK via AndroidManifest `<meta-data>`:
 * - Directs the Cast Framework to connect to the Google Cast **Default Media Receiver** (`CC1AD845`).
 *   This is Google's built-in universal web receiver that runs without registration fees across
 *   all Chromecast, Google TV, Android TV, and Nest smart displays worldwide.
 * - Sets up notifications, remote media client integration, and activity intent targets for lockscreen/notification controls.
 * - Optionally allows overriding the receiver application ID if a custom Web Receiver is deployed.
 *
 * ## Concurrency & Lifecycle
 * - Instantiated reflectively by [com.google.android.gms.cast.framework.CastContext] on the main thread during app initialization.
 * - Thread-safe and stateless.
 */
class CastOptionsProvider : OptionsProvider {

    companion object {
        /**
         * The default Google Cast receiver application ID.
         *
         * Uses [CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID] ("CC1AD845"),
         * which plays media (ambient audio, bell chimes, streams) with title, subtitle,
         * album artwork, and progress controls on any TV hardware without screen mirroring.
         */
        const val DEFAULT_RECEIVER_APP_ID = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID

        /**
         * Custom Web Receiver Application ID registered on Google Cast Developer Console.
         * Directs Cast sessions to load https://apanasara.github.io/Healthy-Habit-Bell/.
         */
        @Volatile
        var customReceiverAppId: String? = "4662865D"
    }

    /**
     * Builds and supplies [CastOptions] to the Google Cast framework.
     *
     * @param context Application context used for resolving notification resources and activity classes.
     * @return Fully configured [CastOptions] instance.
     */
    override fun getCastOptions(context: Context): CastOptions {
        val appId = customReceiverAppId ?: DEFAULT_RECEIVER_APP_ID

        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(appId)
            .setCastMediaOptions(mediaOptions)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setResumeSavedSession(false)
            .build()
    }

    /**
     * Supplies optional additional session providers.
     *
     * @param context Application context.
     * @return List of custom [SessionProvider] instances, or null for default behavior.
     */
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
