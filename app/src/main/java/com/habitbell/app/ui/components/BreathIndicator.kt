package com.habitbell.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.PranayamaPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * # BreathIndicator
 *
 * Sacred geometry blooming lotus visualizer and dynamic prana aura for Pranayama breathwork.
 *
 * ## Architectural Role & Presentation Model
 * - **Presentation Layer Component**: Primary visual focus within [com.habitbell.app.ui.screens.SessionScreen]
 *   and TV leanback dashboards during multi-interval breathwork.
 * - **Sacred Geometry**: Renders 8 overlapping translucent lotus petals that unfurl and bloom during
 *   Puraka (Inhale), hover with living prana micro-pulsations during Antar Kumbhaka (Hold In), fold gracefully
 *   inward during Rechaka (Exhale), and nest as a dormant seed in the center during Bahya Kumbhaka (Hold Out / Void).
 * - **Bio-Feedback Synchrony**: Coordinates non-linear easing curves matching biological lung expansion
 *   and thoracic relaxation.
 *
 * ## Concurrency & Thread Safety
 * - Executed strictly on Compose animation frame clock on the UI thread.
 *
 * @param phase Active breathwork phase ([PranayamaPhase.INHALE], [PranayamaPhase.HOLD_IN], etc.).
 * @param remainingSeconds Seconds remaining in the current active breath phase.
 * @param phaseDuration Total configured duration of the active breath phase in seconds.
 * @param modifier Composable layout modifier.
 * @param size Outer dimension bounding box of the visualizer (defaults to `280.dp`).
 */
@Composable
fun BreathIndicator(
    phase: PranayamaPhase,
    remainingSeconds: Int,
    phaseDuration: Int,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    // 1. Biological scale endpoints
    val targetScale = when (phase) {
        PranayamaPhase.INHALE -> 1.0f     // Full thoracic & diaphragmatic expansion
        PranayamaPhase.HOLD_IN -> 1.0f    // Sustained full expansion
        PranayamaPhase.EXHALE -> 0.42f    // Deflation to residual lung volume
        PranayamaPhase.HOLD_OUT -> 0.38f  // Quiet resting void (Shunya)
    }

    // 2. Smooth phase duration transition
    val animDurationMs = (phaseDuration * 1000).coerceAtLeast(1000)
    val baseScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = animDurationMs,
            easing = FastOutSlowInEasing
        ),
        label = "LotusBaseScale"
    )

    // 3. Living breath micro-pulsation during breath retention (Antar Kumbhaka)
    val infiniteTransition = rememberInfiniteTransition(label = "LivingBreathTransition")
    val retentionPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RetentionPulse"
    )

    // Continuous subtle lotus rotation
    val petalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PetalRotation"
    )

    // Effective scale with living pulse applied during retention
    val effectiveScale = if (phase == PranayamaPhase.HOLD_IN) {
        baseScale * retentionPulse
    } else {
        baseScale
    }

    // 4. Phase-harmonized color psychology
    val primaryPranaColor by animateColorAsState(
        targetValue = when (phase) {
            PranayamaPhase.INHALE -> Color(0xFF2DD4BF)    // Luminous dawn turquoise (oxygenation)
            PranayamaPhase.HOLD_IN -> Color(0xFFFBBF24)   // Radiant solar amber (pranic retention)
            PranayamaPhase.EXHALE -> Color(0xFF818CF8)    // Meditative twilight indigo (calm release)
            PranayamaPhase.HOLD_OUT -> Color(0xFF38BDF8)  // Deep starlight slate (Shunya void)
        },
        animationSpec = tween(durationMillis = 800),
        label = "PranaColor"
    )

    val secondaryPranaColor by animateColorAsState(
        targetValue = when (phase) {
            PranayamaPhase.INHALE -> Color(0xFF10B981)
            PranayamaPhase.HOLD_IN -> Color(0xFFD97706)
            PranayamaPhase.EXHALE -> Color(0xFF6366F1)
            PranayamaPhase.HOLD_OUT -> Color(0xFF64748B)
        },
        animationSpec = tween(durationMillis = 800),
        label = "PranaSecondaryColor"
    )

    // Phase completion fraction for outer circular progress track
    val phaseFraction = if (phaseDuration > 0) {
        (1f - (remainingSeconds.toFloat() / phaseDuration.toFloat())).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Compose Canvas rendering sacred 8-petal blooming lotus and prana aura
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f - 14.dp.toPx()
            val currentRadius = maxRadius * effectiveScale

            // A. Static outer boundary guideline (Vessel / Shunya horizon)
            drawCircle(
                color = primaryPranaColor.copy(alpha = 0.12f),
                radius = maxRadius,
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // B. Active Phase Progress Arc along outer boundary
            val sweepAngle = 360f * phaseFraction
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        primaryPranaColor.copy(alpha = 0.4f),
                        primaryPranaColor,
                        secondaryPranaColor
                    ),
                    center = centerOffset
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerOffset.x - maxRadius, centerOffset.y - maxRadius),
                size = Size(maxRadius * 2f, maxRadius * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // C. Multi-layered luminous prana aura glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryPranaColor.copy(alpha = 0.28f),
                        secondaryPranaColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = currentRadius * 1.25f
                ),
                radius = currentRadius * 1.25f,
                center = centerOffset
            )

            // D. 8 Sacred Blooming Lotus Petals
            val petalCount = 8
            val petalLength = currentRadius
            val petalWidth = currentRadius * 0.52f

            for (i in 0 until petalCount) {
                val angle = (i * (360f / petalCount)) + (petalRotation * 0.05f)
                rotate(degrees = angle, pivot = centerOffset) {
                    drawLotusPetal(
                        center = centerOffset,
                        length = petalLength,
                        width = petalWidth,
                        fillColor = primaryPranaColor.copy(alpha = 0.18f),
                        strokeColor = primaryPranaColor.copy(alpha = 0.55f)
                    )
                }
            }

            // E. Inner core contour ring & glowing seed
            drawCircle(
                color = primaryPranaColor.copy(alpha = 0.85f),
                radius = currentRadius * 0.45f,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryPranaColor.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = currentRadius * 0.45f
                ),
                radius = currentRadius * 0.45f,
                center = centerOffset
            )
        }

        // Centered HUD: Sanskrit Nomenclature, Devanagari Script, English Cue, and Seconds Countdown
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Classical Sanskrit Phase Title
            Text(
                text = phase.sanskritName.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = primaryPranaColor
            )

            // Classical Devanagari Script
            Text(
                text = phase.sanskritScript,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp,
                color = primaryPranaColor.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Large Minimalist Seconds Countdown
            Text(
                text = "$remainingSeconds",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            // English Cue Subtitle
            Text(
                text = phase.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Draws an individual organic lotus petal using cubic bezier curves.
 *
 * @param center Pivot origin of the petal base.
 * @param length Tip distance from petal base in pixels.
 * @param width Maximum lateral swell width of the petal in pixels.
 * @param fillColor Semi-transparent fill tint.
 * @param strokeColor Contour outline tint.
 */
private fun DrawScope.drawLotusPetal(
    center: Offset,
    length: Float,
    width: Float,
    fillColor: Color,
    strokeColor: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y)
        // Left lobe curve
        cubicTo(
            center.x - width, center.y - (length * 0.45f),
            center.x - (width * 0.5f), center.y - (length * 0.9f),
            center.x, center.y - length
        )
        // Right lobe curve
        cubicTo(
            center.x + (width * 0.5f), center.y - (length * 0.9f),
            center.x + width, center.y - (length * 0.45f),
            center.x, center.y
        )
        close()
    }

    drawPath(path = path, color = fillColor)
    drawPath(path = path, color = strokeColor, style = Stroke(width = 1.5.dp.toPx()))
}

