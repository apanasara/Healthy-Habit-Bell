package com.habitbell.app.ui.screens

/**
 * SplashScreen.kt
 * ───────────────────────────────────────────────────────────────────────────
 * Architectural Role: In-App Branded Entry Screen — Sacred Geometry Edition.
 *
 * Animation direction chosen: "Option 3 — Sacred Geometry"
 *   A Flower of Life mandala (7 seed circles + 6 outer ring circles = 13 total)
 *   is progressively stroke-drawn arc by arc. Once complete, the mandala slowly
 *   rotates forever (rotation rate: ~90 s per full revolution). A soft radial
 *   background glow expands very gently (~10 s cycle). The OM symbol (ॐ), logo,
 *   app name, and tagline fade in after the geometry finishes drawing.
 *
 * Golden Ratio (φ = 1.618034) Governance:
 *   - Flower radius        : screenWidth / (φ² + φ) ≈ screenWidth / 4.236
 *   - Logo size            : 160 dp  (existing brand standard)
 *   - OM → logo spacer     : (8 × φ) dp ≈ 13 dp
 *   - Logo → title spacer  : (8 × φ²) dp ≈ 21 dp
 *   - Title → tagline spcr : (8 / φ) dp ≈ 5 dp
 *   - Title font size      : 26 sp
 *   - Tagline font size    : 26 / φ² ≈ 9.9 sp → 10 sp
 *   - Tagline letter-spc   : 2.5 sp
 *   - OM symbol font size  : 13 sp (between tagline and title, golden midpoint)
 *
 * Component Relationships:
 *   - Hosted by `MainActivity` as the initial Compose overlay.
 *   - Complements the native Android 12+ OS window splash (`splash_background.xml`).
 *   - Transitions to `AppScreen.HOME` via [onTimeout] callback.
 *
 * Lifecycle & Concurrency:
 *   - Total active duration: ~5 700 ms (geometry draw 3 s + element fade 1.2 s + pause 1.5 s).
 *   - Runs on the main UI dispatcher via `LaunchedEffect`.
 *   - User tap skips animation and immediately calls [onTimeout].
 *   - Thread-safety: all state is confined to the composition thread.
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

// ─── Brand Constants ────────────────────────────────────────────────────────

/**
 * AMOLED background colour matching `splash_background.xml` and `Color.kt`.
 * Value: #060709
 */
private val SplashBackground = Color(0xFF060709)

/**
 * Primary gold accent. Matches `BellGold` in `Color.kt`.
 * Value: #D4AF37
 */
private val BellGold = Color(0xFFD4AF37)

/**
 * Warm gold for the logo glow. Matches the SVG logo fill in `HabitBell.svg`.
 * Value: #dc9b39
 */
private val LogoGold = Color(0xFFdc9b39)

/**
 * Tagline / subtitle colour. Matches `SplashScreen.kt` original line 118.
 * Value: #E5A93C
 */
private val SubtitleGold = Color(0xFFE5A93C)

// ─── Golden Ratio ────────────────────────────────────────────────────────────

/**
 * Golden ratio constant φ = (1 + √5) / 2 ≈ 1.618034.
 * Used to derive all sizing, spacing, and font scale proportions in this screen.
 */
private const val PHI = 1.618034f

// ─── Composable ─────────────────────────────────────────────────────────────

/**
 * Displays the Sacred Geometry branded splash screen.
 *
 * Animation sequence:
 * 1. Flower of Life mandala draws progressively over 3 000 ms.
 * 2. Mandala transitions into slow perpetual rotation (~90 s / revolution).
 * 3. OM symbol (ॐ) fades in at 800 ms delay after draw starts.
 * 4. Logo and text group fade in once drawing finishes.
 * 5. 1 500 ms mindful pause, then [onTimeout] is dispatched.
 *
 * Background glow cycles very gently (10 s period, 20 % radius expansion).
 *
 * @param onTimeout Callback dispatched when the animation sequence finishes
 *                  or the user taps to skip. Always called on the main thread.
 * @param modifier  Composable layout modifier forwarded from the host.
 */
@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // ── Animatables ──────────────────────────────────────────────────────────

    /**
     * Global Flower of Life draw progress.
     * Range: 0.0 (nothing drawn) → 1.0 (all 13 circles fully stroked).
     * Duration: 3 000 ms, FastOutSlowIn easing for a mindful, deliberate feel.
     */
    val drawProgress = remember { Animatable(0f) }

    /**
     * Alpha for the logo + text content group. Fades in after geometry finishes.
     * Range: 0.0 (invisible) → 1.0 (fully visible). Duration: 900 ms.
     */
    val contentAlpha = remember { Animatable(0f) }

    /**
     * Alpha specifically for the OM (ॐ) symbol above the logo.
     * Starts fading in at 800 ms into the geometry draw so it appears
     * slightly before the geometry finishes. Duration: 700 ms.
     */
    val omAlpha = remember { Animatable(0f) }

    // ── Infinite Transitions ─────────────────────────────────────────────────

    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    /**
     * Mandala rotation angle in degrees. One full 360° revolution every 90 000 ms.
     * Linear easing ensures smooth, constant angular velocity.
     */
    val infiniteRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mandala_rotation"
    )

    /**
     * Background glow alpha multiplier. Pulses very gently between 0.06 and 0.22
     * over a 10 000 ms (10 s) cycle. Produces an atmospheric, breathing quality.
     * Range: 0.06 → 0.22 (dimensionless alpha fraction).
     */
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    /**
     * Background glow radius multiplier. Expands from 1.0× to 1.20× very gently
     * over 10 000 ms, creating the impression of the glow breathing outward.
     * Range: 1.0 → 1.20 (dimensionless multiplier applied to base glow radius).
     */
    val glowRadiusMult by infiniteTransition.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius"
    )

    // ── State flags ──────────────────────────────────────────────────────────

    /**
     * Becomes true when [drawProgress] reaches 1.0. Used as a gate to enable
     * mandala rotation — the geometry draws without rotation, then rotates forever.
     */
    var isGeometryDrawn by remember { mutableStateOf(false) }

    // ── Animation Sequence ───────────────────────────────────────────────────

    LaunchedEffect(Unit) {
        // Start OM symbol fade slightly ahead of geometry completion
        launch {
            delay(800)
            omAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        }

        // Phase 1 — Draw the Flower of Life mandala (3 000 ms)
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3_000, easing = FastOutSlowInEasing)
        )

        // Transition to rotation phase
        isGeometryDrawn = true

        // Phase 2 — Fade in logo + text (900 ms)
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )

        // Phase 3 — Mindful pause (1 500 ms) then navigate
        delay(1_500)
        onTimeout()
    }

    // ── Effective rotation: 0 while drawing, smooth perpetual after ──────────
    val effectiveRotation = if (isGeometryDrawn) infiniteRotation else 0f

    // ─── UI ─────────────────────────────────────────────────────────────────

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTimeout() },          // tap anywhere to skip
        contentAlignment = Alignment.Center
    ) {

        // ── Layer 1: Sacred Geometry Canvas (fills full screen) ──────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // ── Flower of Life geometry parameters derived from golden ratio ──
            //
            // Base radius R = screenWidth / (φ² + φ)
            //   φ² + φ = φ(φ+1) = φ·φ² = φ³ ≈ 4.236
            // This produces a mandala that fills roughly half the screen width,
            // keeping the circles tight and jewel-like, not overwhelming.
            val R = size.width / (PHI * PHI + PHI)   // ≈ width / 4.236

            // Circumference of one Flower of Life circle (px).
            // Used for arc sweep calculation: sweepAngle = progress * 360°.
            val circumference = 2f * PI.toFloat() * R

            // ── Background glow — very gentle, expanding radial gradient ─────
            //
            // Base glow radius = R × φ² ≈ R × 2.618, scaled by animated multiplier.
            // Alpha is kept very low (glowAlpha ∈ 0.06..0.22) for atmospheric subtlety.
            val glowBaseRadius = R * PHI * PHI
            val glowRadius = glowBaseRadius * glowRadiusMult
            val glowCenter = Offset(cx, cy)

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to BellGold.copy(alpha = glowAlpha * 0.9f),
                        0.5f to BellGold.copy(alpha = glowAlpha * 0.3f),
                        1.0f to BellGold.copy(alpha = 0f)
                    ),
                    center = glowCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = glowCenter
            )

            // ── Flower of Life: compute 13 circle centres ─────────────────────
            //
            // Topology:
            //   [0]        — centre seed at (cx, cy)
            //   [1..6]     — 6 inner ring, radius R, angles 0°,60°,120°,180°,240°,300°
            //   [7..12]    — 6 outer ring, radius R√3, angles 30°,90°,150°,210°,270°,330°
            //
            // The 7 seed circles (centre + 6 inner) form the classic Seed of Life.
            // Adding the 6 outer ring produces the full Flower of Life.
            val centres = buildList {
                add(Offset(cx, cy))                          // centre
                for (i in 0..5) {                            // 6 inner ring
                    val a = i * PI.toFloat() / 3f
                    add(Offset(cx + R * cos(a), cy + R * sin(a)))
                }
                for (i in 0..5) {                            // 6 outer ring
                    val a = i * PI.toFloat() / 3f + PI.toFloat() / 6f
                    add(Offset(cx + R * sqrt(3f) * cos(a), cy + R * sqrt(3f) * sin(a)))
                }
            }

            // ── Draw circles with progressive stroke + mandala rotation ───────
            //
            // `rotate(effectiveRotation, Offset(cx,cy))` applies to all 13 arcs.
            // While drawing (effectiveRotation == 0), the mandala is stationary.
            // After drawing, `infiniteRotation` drives a gentle perpetual spin.
            rotate(degrees = effectiveRotation, pivot = Offset(cx, cy)) {
                centres.forEachIndexed { index, centre ->

                    // Staggered delay: circle i begins drawing at progress = i/totalCircles.
                    // This cascades the arcs, creating a mesmerising sequential reveal.
                    val delay = index.toFloat() / centres.size.toFloat()

                    // Local draw progress for this specific circle (0.0 → 1.0).
                    val localProgress = ((drawProgress.value - delay) / (1f - delay + 0.001f))
                        .coerceIn(0f, 1f)

                    // Skip fully unstarted circles to avoid rendering zero-length arcs
                    if (localProgress <= 0f) return@forEachIndexed

                    // Stroke alpha: outer ring (index 7-12) slightly more transparent
                    // for visual depth — inner seed brighter, outer ring dimmer.
                    val circleAlpha = if (index < 7) 0.45f else 0.28f

                    drawArc(
                        color = BellGold.copy(alpha = circleAlpha),
                        startAngle = -90f,                  // start from 12 o'clock
                        sweepAngle = localProgress * 360f,  // arc grows to full circle
                        useCenter = false,
                        topLeft = Offset(centre.x - R, centre.y - R),
                        size = Size(R * 2f, R * 2f),
                        style = Stroke(
                            width = 0.9f,
                            // Subtle dash after drawing completes — short gap every φ-units
                            pathEffect = if (isGeometryDrawn)
                                PathEffect.dashPathEffect(floatArrayOf(PHI * 4f, PHI * 2f), 0f)
                            else null
                        )
                    )
                }
            }
        }

        // ── Layer 2: Content — OM, Logo, App Name, Tagline ──────────────────
        //
        // All spacing and font sizes governed by the golden ratio φ = 1.618034.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // OM symbol (ॐ) — sacred sound, complements the Flower of Life geometry.
            // Fades in independently while geometry is still drawing (omAlpha state).
            // Font size: 13 sp — sits between tagline (10 sp) and title (26 sp) at φ midpoint.
            Text(
                text = "ॐ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                color = BellGold.copy(alpha = omAlpha.value * 0.75f),
                modifier = Modifier.alpha(omAlpha.value)
            )

            // OM → Logo spacer: 8 × φ dp ≈ 12.9 dp (golden ratio base unit × φ)
            Spacer(modifier = Modifier.height((8f * PHI).dp))

            // Brand Logo — uses the same resource as the original SplashScreen.
            // Size: 160 dp (existing brand standard, unchanged).
            // Animated: fades in with contentAlpha after geometry drawing finishes.
            Image(
                painter = painterResource(id = R.drawable.ic_habit_bell_logo),
                contentDescription = "Habit Bell Official Logo",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(160.dp)
                    .alpha(contentAlpha.value)
            )

            // Logo → Title spacer: 8 × φ² dp ≈ 20.9 dp
            Spacer(modifier = Modifier.height((8f * PHI * PHI).dp))

            // App Name — unchanged brand typography.
            // Font: 26 sp, FontWeight.Light, letterSpacing 4 sp.
            Text(
                text = "Habit Bell",
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                color = Color.White.copy(alpha = contentAlpha.value)
            )

            // Title → Tagline spacer: 8 / φ dp ≈ 4.9 dp (compressed ratio for tight pairing)
            Spacer(modifier = Modifier.height((8f / PHI).dp))

            // Tagline — smaller than before (reduced from 12 sp to 10 sp via φ² scaling).
            // Size: 26 / φ² ≈ 9.93 sp → 10 sp.
            // Letter spacing: 2.5 sp (slightly tighter than title for subordinate visual weight).
            Text(
                text = "Mindful Wellness & Living Room Timer",
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.5.sp,
                color = SubtitleGold.copy(alpha = contentAlpha.value * 0.85f)
            )
        }
    }
}
