package com.habitbell.app.ui.components

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.habitbell.app.R
import com.habitbell.app.cast.HabitBellCastManager

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
    AndroidView(
        factory = { context ->
            // Use Theme.AppCompat context wrapper to satisfy MediaRouteButton styling requirements
            val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
            MediaRouteButton(themedContext).apply {
                try {
                    CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
                } catch (_: Exception) {
                    // Gracefully handles devices without Google Play Services
                }
            }
        },
        modifier = modifier.size(36.dp)
    )
}
