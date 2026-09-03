package com.habitbell.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.PranayamaPhase

/**
 * Animated breathing circle visualizer for multi-phase Pranayama breathwork.
 *
 * Smoothly scales an inner core ring and luminous breathing aura to guide inhalation,
 * breath retention, and exhalation cycles in harmony with configured phase durations.
 *
 * @param phase Active breathwork phase ([PranayamaPhase.INHALE], [PranayamaPhase.HOLD_IN], [PranayamaPhase.EXHALE], [PranayamaPhase.HOLD_OUT]).
 * @param remainingSeconds Seconds remaining in the current active breath phase.
 * @param phaseDuration Total configured duration of the active breath phase in seconds.
 * @param modifier Composable layout modifier.
 * @param size Outer dimension bounding box of the visualizer (defaults to `260.dp`).
 */
@Composable
fun BreathIndicator(
    phase: PranayamaPhase,
    remainingSeconds: Int,
    phaseDuration: Int,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    // Determine target expansion scale factor based on biological breath mechanics
    val targetScale = when (phase) {
        PranayamaPhase.INHALE -> 1.0f    // Full lung expansion
        PranayamaPhase.HOLD_IN -> 0.96f  // Steady full retention with subtle pulse
        PranayamaPhase.EXHALE -> 0.45f   // Deflation to resting residual capacity
        PranayamaPhase.HOLD_OUT -> 0.45f // Sustained calm resting empty state
    }

    // Dynamic animation duration synchronized to the exact phase length
    val animDurationMs = (phaseDuration * 1000).coerceAtLeast(1000)
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = animDurationMs, easing = FastOutSlowInEasing),
        label = "BreathScale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = this.size.minDimension / 2f
            val currentRadius = maxRadius * animatedScale

            // 1. Static outer boundary guideline
            drawCircle(
                color = trackColor,
                radius = maxRadius - 10f,
                style = Stroke(width = 2.dp.toPx())
            )

            // 2. Dynamic breathing aura fill
            drawCircle(
                color = primaryColor.copy(alpha = 0.22f),
                radius = currentRadius
            )

            // 3. Crisp inner core contour ring
            drawCircle(
                color = primaryColor,
                radius = currentRadius,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Centered textual cue and countdown
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phase.displayName.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$remainingSeconds",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraLight,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
