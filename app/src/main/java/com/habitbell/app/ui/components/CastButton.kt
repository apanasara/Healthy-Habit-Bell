package com.habitbell.app.ui.components

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory
import com.google.android.gms.cast.framework.CastButtonFactory
import com.habitbell.app.R

/**
 * Custom [MediaRouteDialogFactory] ensuring that [MediaRouteChooserDialog] and
 * [MediaRouteControllerDialog] are always instantiated with an explicit non-translucent
 * Theme context, preventing [IllegalArgumentException: background can not be translucent: #0]
 * crashes during Cast discovery dialog inflation.
 *
 * @property isDark True when current theme background luminance is dark (< 0.5f).
 */
class HabitBellMediaRouteDialogFactory(private val isDark: Boolean) : MediaRouteDialogFactory() {
    override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment {
        return object : MediaRouteChooserDialogFragment() {
            override fun onCreateChooserDialog(context: Context, savedInstanceState: Bundle?): MediaRouteChooserDialog {
                val themeResId = if (isDark) R.style.HabitBellMediaRouteTheme_Dark else R.style.HabitBellMediaRouteTheme_Light
                return MediaRouteChooserDialog(ContextThemeWrapper(context, themeResId))
            }
        }
    }

    override fun onCreateControllerDialogFragment(): MediaRouteControllerDialogFragment {
        return object : MediaRouteControllerDialogFragment() {
            override fun onCreateControllerDialog(context: Context, savedInstanceState: Bundle?): MediaRouteControllerDialog {
                val themeResId = if (isDark) R.style.HabitBellMediaRouteTheme_Dark else R.style.HabitBellMediaRouteTheme_Light
                return MediaRouteControllerDialog(ContextThemeWrapper(context, themeResId))
            }
        }
    }
}

/**
 * # CastButton
 *
 * Material 3 Jetpack Compose wrapper for the official Google Cast [MediaRouteButton].
 *
 * ## Architectural Role & Component Relationships
 * Connects the mobile Compose UI directly to the Google Cast SDK ecosystem:
 * - Invokes the native Android Cast device picker dialog (Living Room TV, Chromecast, Google TV).
 * - Animates connection states (unconnected, connecting, active streaming).
 * - Directs sessions to the TV hardware without screen mirroring and without opening phone browsers (Bug-1).
 * - Automatically resolves theme contrast: applies [R.style.HabitBellMediaRouteTheme_Light] with dark charcoal
 *   tint on light parchment surfaces, and [R.style.HabitBellMediaRouteTheme_Dark] with warm white tint on dark surfaces.
 * - Protects against AndroidX MediaRouter theme crashes by attaching [HabitBellMediaRouteDialogFactory].
 *
 * ## Concurrency & Lifecycle
 * - Interacts with Google Play Services Cast framework on the main UI thread.
 * - Safely handles environments where Google Play Services may be absent or outdated.
 *
 * @param modifier Compose layout modifier applied to the button element.
 * @param tint Optional color tint override for the Cast icon.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    // Detect whether current background is dark or light to select contrasting MediaRoute theme
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    key(isDark) {
        AndroidView(
            factory = { context ->
                val themeResId = if (isDark) {
                    R.style.HabitBellMediaRouteTheme_Dark
                } else {
                    R.style.HabitBellMediaRouteTheme_Light
                }
                val themedContext = ContextThemeWrapper(context, themeResId)
                MediaRouteButton(themedContext).apply {
                    setAlwaysVisible(true)
                    setDialogFactory(HabitBellMediaRouteDialogFactory(isDark))
                    try {
                        CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
                    } catch (_: Exception) {
                        // Gracefully handles devices without Google Play Services
                    }
                }
            },
            modifier = modifier
        )
    }
}
