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

@Composable
fun BreathIndicator(
    phase: PranayamaPhase,
    remainingSeconds: Int,
    phaseDuration: Int,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val targetScale = when (phase) {
        PranayamaPhase.INHALE -> 1.0f
        PranayamaPhase.HOLD_IN -> 0.96f
        PranayamaPhase.EXHALE -> 0.45f
        PranayamaPhase.HOLD_OUT -> 0.45f
    }

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

            // Outer guideline
            drawCircle(
                color = trackColor,
                radius = maxRadius - 10f,
                style = Stroke(width = 2.dp.toPx())
            )

            // Animated breathing aura
            drawCircle(
                color = primaryColor.copy(alpha = 0.22f),
                radius = currentRadius
            )

            // Inner core ring
            drawCircle(
                color = primaryColor,
                radius = currentRadius,
                style = Stroke(width = 3.dp.toPx())
            )
        }

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
