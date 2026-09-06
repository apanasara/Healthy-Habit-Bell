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
 * # HabitBellChooserDialogFragment
 *
 * Public top-level [MediaRouteChooserDialogFragment] providing a dedicated, non-translucent
 * theme context wrapper for Google Cast device discovery sheets.
 *
 * ## Architectural Role & Component Relationships
 * Instantiated by [HabitBellMediaRouteDialogFactory] and managed by Android's [androidx.fragment.app.FragmentManager].
 * Must remain a public, top-level class with an empty public constructor to guarantee safe
 * state recreation across configuration changes without triggering
 * `IllegalStateException: Fragment ... must be a public static class`.
 *
 * ## Concurrency & Lifecycle
 * Runs on the Android Main UI Thread bound to the host Activity's Fragment lifecycle.
 */
class HabitBellChooserDialogFragment : MediaRouteChooserDialogFragment() {

    override fun onCreateChooserDialog(context: Context, savedInstanceState: Bundle?): MediaRouteChooserDialog {
        val themeResId = arguments?.getInt(ARG_THEME_RES_ID, R.style.HabitBellMediaRouteTheme_Dark)
            ?: R.style.HabitBellMediaRouteTheme_Dark
        return MediaRouteChooserDialog(ContextThemeWrapper(context, themeResId))
    }

    companion object {
        private const val ARG_THEME_RES_ID = "arg_theme_res_id"

        /**
         * Creates a new instance of [HabitBellChooserDialogFragment] configured with the specified theme.
         *
         * @param themeResId Android style resource ID for the dialog window background.
         * @return New configured [HabitBellChooserDialogFragment] instance.
         */
        fun newInstance(themeResId: Int): HabitBellChooserDialogFragment {
            return HabitBellChooserDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_THEME_RES_ID, themeResId)
                }
            }
        }
    }
}

/**
 * # HabitBellControllerDialogFragment
 *
 * Public top-level [MediaRouteControllerDialogFragment] providing a dedicated, non-translucent
 * theme context wrapper for active Google Cast session playback controller sheets.
 *
 * ## Architectural Role & Component Relationships
 * Instantiated by [HabitBellMediaRouteDialogFactory] and managed by Android's [androidx.fragment.app.FragmentManager].
 * Must remain a public, top-level class with an empty public constructor to guarantee safe
 * state recreation across configuration changes without triggering `IllegalStateException`.
 *
 * ## Concurrency & Lifecycle
 * Runs on the Android Main UI Thread bound to the host Activity's Fragment lifecycle.
 */
class HabitBellControllerDialogFragment : MediaRouteControllerDialogFragment() {

    override fun onCreateControllerDialog(context: Context, savedInstanceState: Bundle?): MediaRouteControllerDialog {
        val themeResId = arguments?.getInt(ARG_THEME_RES_ID, R.style.HabitBellMediaRouteTheme_Dark)
            ?: R.style.HabitBellMediaRouteTheme_Dark
        return MediaRouteControllerDialog(ContextThemeWrapper(context, themeResId))
    }

    companion object {
        private const val ARG_THEME_RES_ID = "arg_theme_res_id"

        /**
         * Creates a new instance of [HabitBellControllerDialogFragment] configured with the specified theme.
         *
         * @param themeResId Android style resource ID for the dialog window background.
         * @return New configured [HabitBellControllerDialogFragment] instance.
         */
        fun newInstance(themeResId: Int): HabitBellControllerDialogFragment {
            return HabitBellControllerDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_THEME_RES_ID, themeResId)
                }
            }
        }
    }
}

/**
 * Custom [MediaRouteDialogFactory] ensuring that [MediaRouteChooserDialog] and
 * [MediaRouteControllerDialog] are instantiated using public, static [HabitBellChooserDialogFragment]
 * and [HabitBellControllerDialogFragment] with explicit non-translucent theme contexts.
 *
 * @property isDark True when current theme background luminance is dark (< 0.5f).
 */
class HabitBellMediaRouteDialogFactory(private val isDark: Boolean = true) : MediaRouteDialogFactory() {
    override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment {
        val themeResId = if (isDark) R.style.HabitBellMediaRouteTheme_Dark else R.style.HabitBellMediaRouteTheme_Light
        return HabitBellChooserDialogFragment.newInstance(themeResId)
    }

    override fun onCreateControllerDialogFragment(): MediaRouteControllerDialogFragment {
        val themeResId = if (isDark) R.style.HabitBellMediaRouteTheme_Dark else R.style.HabitBellMediaRouteTheme_Light
        return HabitBellControllerDialogFragment.newInstance(themeResId)
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
