package com.habitbell.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated circular countdown progress ring enclosing timer content.
 *
 * Renders a subtle background track and an animated primary color progress arc
 * that sweeps clockwise starting from the top (-90 degrees).
 *
 * @param progress Normalized completion fraction from `0.0f` (0%) to `1.0f` (100%).
 * @param modifier Composable layout modifier.
 * @param size Outer dimension bounding box of the circular ring (defaults to `280.dp`).
 * @param strokeWidth Stroke thickness for the progress and track arcs (defaults to `6.dp`).
 * @param content Composable slot centered inside the circular ring (typically time texts).
 */
@Composable
fun CircularProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    strokeWidth: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    // Smooth progress interpolation to avoid jerky step jumps between 1-second ticks
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "RingProgress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val radius = (this.size.minDimension - stroke) / 2f

            // 1. Static background circular track
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = stroke)
            )

            // 2. Dynamic progress arc with rounded caps sweeping clockwise from 12 o'clock
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        // Center content slot (e.g. countdown timer text and round badges)
        content()
    }
}
