/**
 * # AnimatedPoseView
 *
 * Composable component rendering animated posture illustrations (AnimatedVectorDrawable / VectorDrawable)
 * for Surya Namaskar and compound sequential routines.
 *
 * ## Architectural Role & Component Relationships
 * Presentation layer UI component located under `com.habitbell.app.ui`.
 * - Embedded in [com.habitbell.app.ui.SuryaTimerScreen] to display pose visual feedback.
 * - Bridges Android's native [android.graphics.drawable.AnimatedVectorDrawable] and [android.graphics.drawable.Animatable]
 *   into Jetpack Compose via [androidx.compose.ui.viewinterop.AndroidView].
 *
 * ## Concurrency & Thread Safety
 * Executes on the Main (UI) thread within Jetpack Compose recomposition cycles.
 * Animation triggers are lifecycle-aware and managed on the main thread via [Animatable.start].
 */
package com.habitbell.app.ui

import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.habitbell.app.R

/**
 * Renders an animated vector drawable posture illustration with automatic entrance playback.
 *
 * When an [Animatable] drawable (e.g. `AnimatedVectorDrawable`) is provided via [drawableResId],
 * the animation automatically starts upon rendering and stops when exiting composition.
 *
 * @param drawableResId Android drawable resource ID representing the pose vector or animated vector.
 * @param modifier Composable layout modifier for size, padding, and constraints.
 * @param size Dimension specifying the width and height of the illustration in density-independent pixels [Dp].
 * @param contentDescription Accessibility description for screen readers.
 */
@Composable
fun AnimatedPoseView(
    @DrawableRes drawableResId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentDescription: String? = null
) {
    val context = LocalContext.current

    // Resolve drawable resource from application context
    val drawable: android.graphics.drawable.Drawable? = remember(drawableResId) {
        ContextCompat.getDrawable(context, drawableResId)
    }

    // Automatically trigger Animatable playback when drawable attaches to composition
    DisposableEffect(drawable) {
        val animatable = drawable as? Animatable
        animatable?.start()

        onDispose {
            animatable?.stop()
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable)
                    this.contentDescription = contentDescription
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(drawable)
                imageView.contentDescription = contentDescription
                (drawable as? Animatable)?.start()
            },
            modifier = Modifier.size(size)
        )
    }
}
