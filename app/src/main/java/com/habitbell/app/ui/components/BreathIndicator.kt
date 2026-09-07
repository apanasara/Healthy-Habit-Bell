package com.habitbell.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.PranayamaPhase
import kotlin.math.PI
import kotlin.math.sin

/**
 * # BreathIndicator
 *
 * Classical sacred side-view blooming lotus visualizer, calm waterline ripples, and dynamic prana
 * radial aura for Pranayama breathwork.
 *
 * ## Architectural Role & Presentation Model
 * - **Presentation Layer Hero Visualizer**: Primary visual centerpiece within [com.habitbell.app.ui.screens.SessionScreen]
 *   and TV leanback dashboards during multi-interval pranayama breathwork.
 * - **Side-View Lotus Architecture**: Renders 13 organic curved petals structured across 7 distinct
 *   depth tiers (outermost horizontal wings -> lateral wings -> chalice petals -> central erect spine).
 *   - **Pūraka (Inhale)**: Unfurls organically outward into full bloom with smooth cubic lift from the waterline.
 *   - **Antar Kumbhaka (Hold In)**: Open flower hovers soothingly with living C2-continuous aquatic floating.
 *   - **Recaka (Exhale)**: Petals fold gently inward into a serene closed bud with cubic descent to the waterline.
 *   - **Bāhya Kumbhaka (Hold Out / Void)**: Slender closed bud rests tranquilly in the Shunya stillness.
 * - **Theme-Harmonized Calyx ("Patte") & Stem**: Dynamically colors the 3 calyx leaves and vertical stem
 *   according to the active app theme (luminous chartreuse/emerald on dark AMOLED; rich natural sage/olive on parchment).
 * - **Zero Text/Numeral Overlap**: Elevates Sanskrit nomenclature and large countdown numerals into an upper HUD,
 *   preserving generous clear space above the apex of the flower.
 *
 * ## Concurrency & Thread Safety
 * - Executed strictly on Compose animation frame clock on the UI thread.
 *
 * @param phase Active breathwork phase ([PranayamaPhase.INHALE], [PranayamaPhase.HOLD_IN], etc.).
 * @param remainingSeconds Seconds remaining in the current active breath phase.
 * @param phaseDuration Total configured duration of the active breath phase in seconds.
 * @param modifier Composable layout modifier.
 * @param size Outer dimension bounding box of the visualizer (defaults to `340.dp`).
 * @param showHud Whether to render the integrated upper Sanskrit HUD (defaults to `true`).
 * @param showGuidanceCapsule Whether to render the lower guidance pill inside the canvas (defaults to `false`).
 */
@Composable
fun BreathIndicator(
    phase: PranayamaPhase,
    remainingSeconds: Int,
    phaseDuration: Int,
    modifier: Modifier = Modifier,
    size: Dp = 340.dp,
    showHud: Boolean = true,
    showGuidanceCapsule: Boolean = false
) {
    // 1. Biological scale and bloom target endpoints
    val targetBloom = when (phase) {
        PranayamaPhase.INHALE -> 1.0f     // Full blooming petal expansion
        PranayamaPhase.HOLD_IN -> 1.0f    // Sustained full bloom
        PranayamaPhase.EXHALE -> 0.0f     // Progressive closure to resting bud
        PranayamaPhase.HOLD_OUT -> 0.0f   // Quiet closed bud in Shunya void
    }

    val animDurationMs = (phaseDuration * 1000).coerceAtLeast(1000)

    // Smooth C2-continuous bloom transition (FastOutSlowInEasing matching cubic bezier curve)
    val bloom by animateFloatAsState(
        targetValue = targetBloom,
        animationSpec = tween(
            durationMillis = animDurationMs,
            easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1.0f)
        ),
        label = "LotusBloomKinematics"
    )

    // 2. Living aquatic micro-pulsation and floating wave
    val infiniteTransition = rememberInfiniteTransition(label = "LivingBreathTransition")
    val floatWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LotusFloatWave"
    )

    // Water ripple time tracking
    val rippleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaterRippleTimer"
    )

    // Calculate kinematic vertical float offset and lateral sway
    val waveOffset = sin(floatWave * 2f * PI.toFloat())
    val floatOffsetDp = when (phase) {
        PranayamaPhase.INHALE -> -bloom * 3.5f
        PranayamaPhase.HOLD_IN -> -3.5f - (waveOffset * 4.5f)
        PranayamaPhase.EXHALE -> -bloom * 3.5f
        PranayamaPhase.HOLD_OUT -> -waveOffset * 3.5f
    }
    val swayAngleDeg = when (phase) {
        PranayamaPhase.INHALE -> sin(bloom * PI.toFloat()) * 0.7f
        PranayamaPhase.HOLD_IN -> waveOffset * 1.15f
        PranayamaPhase.EXHALE -> -sin((1f - bloom) * PI.toFloat()) * 0.7f
        PranayamaPhase.HOLD_OUT -> waveOffset * 0.9f
    }

    // 3. Theme & Color Psychology
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val primaryPranaColor by animateColorAsState(
        targetValue = if (isDark) {
            when (phase) {
                PranayamaPhase.INHALE -> Color(0xFFF43F5E)    // Luminous Ruby Rose (oxygenation)
                PranayamaPhase.HOLD_IN -> Color(0xFFFBBF24)   // Radiant Solar Amber (retention)
                PranayamaPhase.EXHALE -> Color(0xFFA78BFA)    // Meditative Twilight Violet (release)
                PranayamaPhase.HOLD_OUT -> Color(0xFF38BDF8)  // Deep Starlight Cyan (Shunya void)
            }
        } else {
            when (phase) {
                PranayamaPhase.INHALE -> Color(0xFFE11D48)    // Deep Rose
                PranayamaPhase.HOLD_IN -> Color(0xFFD97706)   // Warm Amber
                PranayamaPhase.EXHALE -> Color(0xFF7C3AED)    // Mystic Violet
                PranayamaPhase.HOLD_OUT -> Color(0xFF0284C7)  // Cerulean Blue
            }
        },
        animationSpec = tween(durationMillis = 600),
        label = "PranaColor"
    )

    // Creative Theme-Harmonized Calyx ("Patte") & Stem Palette
    val leafFillColor = if (isDark) Color(0xFFA3E635).copy(alpha = 0.55f) else Color(0xFF84CC16).copy(alpha = 0.42f)
    val leafStrokeColor = if (isDark) Color(0xFFD9F99D).copy(alpha = 0.80f) else Color(0xFF65A30D).copy(alpha = 0.85f)
    val stemColor = if (isDark) Color(0xFF84CC16).copy(alpha = 0.90f) else Color(0xFF65A30D).copy(alpha = 0.90f)
    val receptacleColor = if (isDark) Color(0xFF84CC16) else Color(0xFF4D7C0F)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Canvas rendering Side-View Lotus, Prana Radial Aura, Water Ripples, and Calyx Leaves
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val cx = canvasW / 2f
            val baseWaterY = canvasH * 0.77f
            val floatOffsetPx = floatOffsetDp.dp.toPx()
            val cy = baseWaterY + floatOffsetPx
            val maxPetalLen = canvasH * 0.39f
            val stemLen = canvasH * 0.11f

            // A. Luminous Breathing Prana Radial Aura behind flower
            val auraRadius = (maxPetalLen * 0.75f) + (bloom * maxPetalLen * 0.55f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryPranaColor.copy(alpha = if (isDark) 0.22f else 0.14f),
                        primaryPranaColor.copy(alpha = if (isDark) 0.08f else 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(cx, baseWaterY - maxPetalLen * 0.35f),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(cx, baseWaterY - maxPetalLen * 0.35f)
            )

            // B. Calm Resting Waterline Ripples (at fixed resting waterline baseWaterY)
            drawCalmWaterRipples(
                cx = cx,
                waterY = baseWaterY,
                rippleColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                rippleAnimTime = rippleTime
            )

            // C. Side-View Lotus Petals & Calyx Leaves
            rotate(degrees = swayAngleDeg, pivot = Offset(cx, cy)) {
                // Draw 13 Curved Lotus Petals sorted by layer
                PETAL_SPECS.sortedBy { it.layer }.forEach { spec ->
                    val curAngle = lerp(spec.angleBud, spec.angleBloom, bloom)
                    val curLenRatio = lerp(spec.lengthRatioBud, spec.lengthRatioBloom, bloom)
                    val curLength = maxPetalLen * curLenRatio

                    rotate(degrees = curAngle, pivot = Offset(cx, cy)) {
                        drawCurvedLotusPetal(
                            center = Offset(cx, cy),
                            length = curLength,
                            widthRatio = spec.widthRatio,
                            alpha = spec.alpha,
                            fillColor = primaryPranaColor,
                            strokeColor = if (isDark) {
                                Color.White.copy(alpha = (spec.alpha * 1.1f).coerceAtMost(0.7f))
                            } else {
                                primaryPranaColor.copy(alpha = (spec.alpha * 1.2f).coerceAtMost(0.85f))
                            }
                        )
                    }
                }

                // Draw Calyx (3 curved leaves "patte" + vertical stem + receptacle dot)
                drawGeometricCalyx(
                    anchor = Offset(cx, cy),
                    bloom = bloom,
                    stemLength = stemLen,
                    stemColor = stemColor,
                    leafFillColor = leafFillColor,
                    leafStrokeColor = leafStrokeColor,
                    receptacleColor = receptacleColor
                )
            }
        }

        // Integrated HUD (Rendered cleanly when showHud = true, immune to petal overlap)
        if (showHud) {
            // Upper HUD: Sanskrit Nomenclature and Countdown Numeral positioned high above flower apex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
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

                // Large Minimalist Seconds Countdown Numeral
                Text(
                    text = "$remainingSeconds",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Lower Anchor: Optional English Biofeedback Guidance Capsule
            if (showGuidanceCapsule) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.75f),
                    border = BorderStroke(1.dp, primaryPranaColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Text(
                        text = phase.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Immutable configuration defining the geometric properties and animation trajectory of an individual lotus petal.
 *
 * @property id Semantic identifier of the petal.
 * @property angleBud Deflection angle in degrees when closed as a resting bud.
 * @property angleBloom Deflection angle in degrees when unfurled in full blooming expansion.
 * @property lengthRatioBud Petal length scaling factor (0.0f..1.0f) during closed bud phase.
 * @property lengthRatioBloom Petal length scaling factor (0.0f..1.0f) during full bloom phase.
 * @property widthRatio Petal lateral width to length ratio (0.0f..1.0f).
 * @property alpha Base opacity alpha value (0.0f..1.0f).
 * @property layer Rendering z-order layer index (0 being outermost background wings, 6 being central spine).
 */
private data class LotusPetalSpec(
    val id: String,
    val angleBud: Float,
    val angleBloom: Float,
    val lengthRatioBud: Float,
    val lengthRatioBloom: Float,
    val widthRatio: Float,
    val alpha: Float,
    val layer: Int
)

private val PETAL_SPECS = listOf(
    // Tier 0: Outermost horizontal wings
    LotusPetalSpec("wing_L", -6f, -84f, 0.89f, 0.555f, 0.44f, 0.22f, 0),
    LotusPetalSpec("wing_R", 6f, 84f, 0.89f, 0.555f, 0.44f, 0.22f, 0),
    // Tier 1: Outer lateral wings
    LotusPetalSpec("out_L2", -5f, -70f, 0.916f, 0.666f, 0.43f, 0.26f, 1),
    LotusPetalSpec("out_R2", 5f, 70f, 0.916f, 0.666f, 0.43f, 0.26f, 1),
    // Tier 2: Mid lateral petals
    LotusPetalSpec("out_L1", -4f, -56f, 0.944f, 0.777f, 0.42f, 0.30f, 2),
    LotusPetalSpec("out_R1", 4f, 56f, 0.944f, 0.777f, 0.42f, 0.30f, 2),
    // Tier 3: Core fanning petals
    LotusPetalSpec("mid_L", -3f, -42f, 0.958f, 0.861f, 0.41f, 0.34f, 3),
    LotusPetalSpec("mid_R", 3f, 42f, 0.958f, 0.861f, 0.41f, 0.34f, 3),
    // Tier 4: Inner-mid chalice
    LotusPetalSpec("in_L", -2f, -28f, 0.972f, 0.93f, 0.40f, 0.38f, 4),
    LotusPetalSpec("in_R", 2f, 28f, 0.972f, 0.93f, 0.40f, 0.38f, 4),
    // Tier 5: Inner core
    LotusPetalSpec("core_L", -1f, -14f, 0.986f, 0.972f, 0.39f, 0.42f, 5),
    LotusPetalSpec("core_R", 1f, 14f, 0.986f, 0.972f, 0.39f, 0.42f, 5),
    // Tier 6: Central erect spine
    LotusPetalSpec("center", 0f, 0f, 1.0f, 1.0f, 0.38f, 0.46f, 6)
)

/**
 * Linear interpolation helper.
 *
 * @param a Start value.
 * @param b End value.
 * @param t Progress fraction (0.0f..1.0f).
 * @return Interpolated float value.
 */
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Draws an individual organic curved lotus petal formed by two smooth semicircular lobes.
 *
 * @param center Base pivot origin of the petal in pixels.
 * @param length Tip distance from petal base in pixels.
 * @param widthRatio Petal lateral width to length ratio (0.0f..1.0f).
 * @param alpha Petal fill opacity.
 * @param fillColor Semi-transparent fill tint.
 * @param strokeColor Contour outline tint.
 */
private fun DrawScope.drawCurvedLotusPetal(
    center: Offset,
    length: Float,
    widthRatio: Float,
    alpha: Float,
    fillColor: Color,
    strokeColor: Color
) {
    val halfW = length * widthRatio * 0.55f
    val tipY = center.y - length

    val path = Path().apply {
        moveTo(center.x, center.y)
        // Left smooth semicircular curved arc
        cubicTo(
            center.x - (halfW * 1.10f), center.y - (length * 0.38f),
            center.x - (halfW * 0.65f), center.y - (length * 0.86f),
            center.x, tipY
        )
        // Right smooth semicircular curved arc back to base
        cubicTo(
            center.x + (halfW * 0.65f), center.y - (length * 0.86f),
            center.x + (halfW * 1.10f), center.y - (length * 0.38f),
            center.x, center.y
        )
        close()
    }

    drawPath(path = path, color = fillColor.copy(alpha = alpha))
    drawPath(path = path, color = strokeColor, style = Stroke(width = 1.2.dp.toPx()))
}

/**
 * Draws the calyx (3 curved leaves "patte"), vertical stem, and central receptacle dot.
 *
 * @param anchor Base pivot of the lotus flower.
 * @param bloom Bloom expansion progress (0.0f..1.0f).
 * @param stemLength Length of the vertical stem in pixels.
 * @param stemColor Color of the stem line.
 * @param leafFillColor Fill color of the 3 calyx leaves.
 * @param leafStrokeColor Stroke contour color of the 3 calyx leaves.
 * @param receptacleColor Color of the central receptacle seed.
 */
private fun DrawScope.drawGeometricCalyx(
    anchor: Offset,
    bloom: Float,
    stemLength: Float,
    stemColor: Color,
    leafFillColor: Color,
    leafStrokeColor: Color,
    receptacleColor: Color
) {
    // 1. Stem
    drawLine(
        color = stemColor,
        start = anchor,
        end = Offset(anchor.x, anchor.y + stemLength),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // 2. 3 Calyx Leaves ("Patte")
    val calyxSpread = lerp(22f, 58f, bloom)
    val calyxLen = lerp(stemLength * 0.48f, stemLength * 0.68f, bloom)
    val calyxW = calyxLen * 0.42f

    fun drawLeaf(angleDeg: Float) {
        rotate(degrees = angleDeg, pivot = anchor) {
            val leafPath = Path().apply {
                moveTo(anchor.x, anchor.y)
                cubicTo(
                    anchor.x - (calyxW * 1.15f), anchor.y + (calyxLen * 0.4f),
                    anchor.x - (calyxW * 0.65f), anchor.y + (calyxLen * 0.85f),
                    anchor.x, anchor.y + calyxLen
                )
                cubicTo(
                    anchor.x + (calyxW * 0.65f), anchor.y + (calyxLen * 0.85f),
                    anchor.x + (calyxW * 1.15f), anchor.y + (calyxLen * 0.4f),
                    anchor.x, anchor.y
                )
                close()
            }
            drawPath(path = leafPath, color = leafFillColor)
            drawPath(path = leafPath, color = leafStrokeColor, style = Stroke(width = 1.dp.toPx()))
        }
    }

    drawLeaf(180f - calyxSpread)
    drawLeaf(180f + calyxSpread)
    drawLeaf(180f)

    // Central receptacle dot
    drawCircle(
        color = receptacleColor,
        radius = 3.5.dp.toPx(),
        center = anchor
    )
}

/**
 * Draws subtle calm water ripple rings at the resting waterline.
 *
 * @param cx Center horizontal coordinate in pixels.
 * @param waterY Vertical waterline coordinate in pixels.
 * @param rippleColor Base color of the ripple rings.
 * @param rippleAnimTime Seconds counter for continuous ripple propagation.
 */
private fun DrawScope.drawCalmWaterRipples(
    cx: Float,
    waterY: Float,
    rippleColor: Color,
    rippleAnimTime: Float
) {
    val r1 = (rippleAnimTime * 0.45f) % 2.5f
    val r2 = (rippleAnimTime * 0.45f + 1.25f) % 2.5f

    listOf(r1, r2).forEach { r ->
        val rw = 24.dp.toPx() + (r * 54.dp.toPx())
        val rh = 4.5.dp.toPx() + (r * 4.0.dp.toPx())
        val alpha = ((1f - (r / 2.5f)).coerceIn(0f, 1f) * 0.35f)

        drawOval(
            color = rippleColor.copy(alpha = alpha),
            topLeft = Offset(cx - rw, waterY - rh),
            size = Size(rw * 2f, rh * 2f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}


