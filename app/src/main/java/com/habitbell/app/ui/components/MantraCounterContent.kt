package com.habitbell.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.mantra.MantraInputSourceType
import com.habitbell.app.mantra.MantraMode
import com.habitbell.app.mantra.MantraTechnique
import com.habitbell.app.mantra.MantraUpdate
import kotlin.math.cos
import kotlin.math.sin

/**
 * # MantraCounterContent
 *
 * Dedicated Jetpack Compose visualizer for real-time sacred mantra and scripture recitation sessions
 * (Gayatri Mantra, Maha Mrityunjaya, Aumkar, Ram Japa, Islamic Tasbih, Jesus Prayer).
 *
 * ## Architectural Role & Component Relationships
 * - Hosted by [com.habitbell.app.ui.screens.SessionScreen] when `sessionState.isMantraCountingActive` is true.
 * - Renders a sacred circular 108-bead prayer Mala (or 33/100-bead ring based on tradition) with dynamic illumination.
 * - Displays real-time acoustic biofeedback ripples driven by [MantraUpdate.audioAmplitudeRms].
 * - Exposes interactive full-surface tap counting for silent halls, libraries, and manual overrides.
 *
 * ## Concurrency & Recomposition
 * - Reacts reactively to [MantraUpdate] state emissions.
 * - Uses smooth Compose hardware-accelerated animators for 60 FPS acoustic wave pulses.
 *
 * @param sessionState Current authoritative timer session state.
 * @param mantraUpdate Real-time recitation metrics and Mala round progression.
 * @param selectedInputSource Active input provider ([MantraInputSourceType]).
 * @param onSelectInputSource Callback to switch between microphone and touch screen mode.
 * @param onSelectSensitivity Callback to adjust microphone detection sensitivity.
 * @param onSelectTechnique Callback to fast-switch sacred recitation modalities.
 * @param onManualBeadTap Callback invoked when user taps the screen to advance beads.
 * @param modifier Composable layout modifier.
 */
@Composable
fun MantraCounterContent(
    sessionState: TimerSessionState,
    mantraUpdate: MantraUpdate,
    selectedInputSource: MantraInputSourceType,
    onSelectInputSource: (MantraInputSourceType) -> Unit,
    onSelectSensitivity: (Float) -> Unit = {},
    onSelectTechnique: (MantraTechnique) -> Unit = {},
    onManualBeadTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = sessionState.profile
    val config = profile.mantraConfig
    val technique = config?.technique ?: mantraUpdate.technique

    // Live acoustic ripple scale factor (0.0f..1.0f)
    val animatedRms by animateFloatAsState(
        targetValue = mantraUpdate.audioAmplitudeRms.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "MantraAcousticRms"
    )

    // Breathing pulse for ambient resting circles
    val infiniteTransition = rememberInfiniteTransition(label = "MantraAmbientPulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MantraAmbientPulse"
    )

    // Sacred Aura Color: Golden Amber for Vedic/Japa, Jade for Tasbih, Celestial Blue for Christian Prayer
    val sacredColor by animateColorAsState(
        targetValue = when (technique) {
            MantraTechnique.TASBIH_DHIKR -> Color(0xFF00E676) // Radiant Islamic Jade
            MantraTechnique.JESUS_PRAYER -> Color(0xFF40C4FF) // Celestial Hesychastic Blue
            MantraTechnique.AUMKAR -> Color(0xFFFFAB00) // Deep Omkar Amber Gold
            else -> Color(0xFFFFB300) // Sacred Vedic Saffron Gold
        },
        animationSpec = tween(durationMillis = 600),
        label = "SacredAuraColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Header: Script Text, Title & Modality Switchers
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = technique.scriptText,
                style = MaterialTheme.typography.titleMedium,
                color = sacredColor.copy(alpha = 0.90f),
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = technique.displayName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (mantraUpdate.isCalibrating) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Remain silent. Calibrating room acoustics (AC, fan, wind)...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Technique Fast-Switcher Chips (Preparation or Idle)
            if (sessionState.isPreparing || sessionState.status == com.habitbell.app.engine.SessionStatus.IDLE) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    listOf(
                        MantraTechnique.GAYATRI_MANTRA,
                        MantraTechnique.MAHA_MRITYUNJAYA,
                        MantraTechnique.AUMKAR,
                        MantraTechnique.RAM_JAPA,
                        MantraTechnique.TASBIH_DHIKR
                    ).forEach { tech ->
                        val isSelected = technique == tech
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) sacredColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.dp, sacredColor) else null,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .clickable { onSelectTechnique(tech) }
                        ) {
                            Text(
                                text = when (tech) {
                                    MantraTechnique.GAYATRI_MANTRA -> "Gayatri"
                                    MantraTechnique.MAHA_MRITYUNJAYA -> "Mrityunjaya"
                                    MantraTechnique.AUMKAR -> "Aumkar"
                                    MantraTechnique.RAM_JAPA -> "Ram"
                                    MantraTechnique.TASBIH_DHIKR -> "Tasbih"
                                    else -> tech.displayName
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) sacredColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Input Mode Switcher Chips (Mic vs Tap)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedInputSource == MantraInputSourceType.ACOUSTIC_MIC,
                    onClick = { onSelectInputSource(MantraInputSourceType.ACOUSTIC_MIC) },
                    label = { Text("🎙️ Mic Sensor", fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = selectedInputSource == MantraInputSourceType.MANUAL_BEAD_TAP,
                    onClick = { onSelectInputSource(MantraInputSourceType.MANUAL_BEAD_TAP) },
                    label = { Text("📿 Tap Bead", fontSize = 12.sp) }
                )
            }

            // Live Mic Sensitivity & Audio Level Meter (Acoustic Mode)
            if (selectedInputSource == MantraInputSourceType.ACOUSTIC_MIC) {
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Sensitivity: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    val currentSens = mantraUpdate.micSensitivity
                    listOf(
                        Pair("Low", 0.7f),
                        Pair("Med", 1.0f),
                        Pair("High", 1.5f),
                        Pair("Whisper", 2.2f)
                    ).forEach { (label, value) ->
                        val isSelected = kotlin.math.abs(currentSens - value) < 0.2f
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) sacredColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.dp, sacredColor) else null,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .clickable { onSelectSensitivity(value) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) sacredColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Live Audio Level & Threshold Needle
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    val totalWidth = maxWidth
                    val fillWidth = totalWidth * animatedRms.coerceIn(0f, 1f)
                    val thresholdOffset = totalWidth * mantraUpdate.thresholdRms.coerceIn(0f, 1f)
                    val isOverThreshold = animatedRms >= mantraUpdate.thresholdRms

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(fillWidth)
                            .background(
                                if (isOverThreshold) sacredColor else Color(0xFF4CAF50).copy(alpha = 0.85f)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .offset(x = thresholdOffset)
                            .background(Color.White)
                    )
                }
            }
        }

        // 2. Hero Centerpiece: 108 Sacred Beads Circular Canvas & Acoustic Core
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onManualBeadTap()
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Canvas: Circular 108-bead Mala + Acoustic Ripples
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val malaRadius = (size.minDimension / 2f) - 16.dp.toPx()
                val rippleScale = 1.0f + (animatedRms * 0.18f)

                // Central Acoustic Biofeedback Ripple
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            sacredColor.copy(alpha = 0.28f * animatedRms.coerceAtLeast(0.15f)),
                            sacredColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = malaRadius * 0.85f * rippleScale
                    ),
                    center = center,
                    radius = malaRadius * 0.85f * rippleScale
                )

                // Subtle ambient harmonic circle
                drawCircle(
                    color = sacredColor.copy(alpha = 0.15f),
                    center = center,
                    radius = malaRadius * 0.80f * ambientPulse,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 108 Sacred Mala Beads Ring (or target count based on config)
                val totalBeads = mantraUpdate.targetBeads.coerceAtLeast(1)
                val currentBead = mantraUpdate.currentBead.coerceIn(0, totalBeads)

                // Draw connecting sacred thread
                drawCircle(
                    color = sacredColor.copy(alpha = 0.25f),
                    center = center,
                    radius = malaRadius,
                    style = Stroke(width = 1.dp.toPx())
                )

                // For large bead counts (e.g. 108), step by 1; adjust bead size accordingly
                val beadRadiusPx = when {
                    totalBeads <= 33 -> 5.5.dp.toPx()
                    totalBeads <= 54 -> 4.5.dp.toPx()
                    else -> 3.2.dp.toPx() // 108 beads
                }

                for (i in 0 until totalBeads) {
                    // Start at top (-PI/2) and rotate clockwise
                    val angleRad = (2.0 * Math.PI * i / totalBeads) - (Math.PI / 2.0)
                    val bx = center.x + (malaRadius * cos(angleRad)).toFloat()
                    val by = center.y + (malaRadius * sin(angleRad)).toFloat()

                    val isMeruBead = (i == 0) // Meru / Guru bead anchor at 12 o'clock
                    val isCompleted = (i < currentBead)
                    val isCurrent = (i == currentBead)

                    val bRadius = if (isMeruBead) beadRadiusPx * 1.8f else if (isCurrent) beadRadiusPx * 1.4f else beadRadiusPx
                    val bColor = when {
                        isMeruBead && currentBead >= totalBeads -> sacredColor
                        isMeruBead -> sacredColor.copy(alpha = 0.90f)
                        isCompleted -> sacredColor
                        isCurrent -> sacredColor.copy(alpha = 0.70f + (animatedRms * 0.30f))
                        else -> sacredColor.copy(alpha = 0.20f)
                    }

                    drawCircle(
                        color = bColor,
                        center = Offset(bx, by),
                        radius = bRadius,
                        style = Fill
                    )

                    if (isCompleted || isCurrent) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.45f),
                            center = Offset(bx - bRadius * 0.25f, by - bRadius * 0.25f),
                            radius = bRadius * 0.35f,
                            style = Fill
                        )
                    }
                }
            }

            // Center Hero Metrics Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                if (mantraUpdate.isCompleted) {
                    Text(
                        text = "MALA COMPLETED",
                        style = MaterialTheme.typography.labelSmall,
                        color = sacredColor,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "ॐ",
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold,
                        color = sacredColor
                    )
                    Text(
                        text = "Purna Sadhana Finished",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (mantraUpdate.isCalibrating) {
                    Text(
                        text = "CALIBRATING ROOM",
                        style = MaterialTheme.typography.labelSmall,
                        color = sacredColor,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (mantraUpdate.calibrationSecondsRemaining > 0) "${mantraUpdate.calibrationSecondsRemaining}s" else "Calibrating",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = sacredColor
                    )
                    Text(
                        text = "Measuring ambient sound",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "BEADS",
                        style = MaterialTheme.typography.labelSmall,
                        color = sacredColor,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${mantraUpdate.currentBead}",
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "of ${mantraUpdate.targetBeads}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Cadence Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = sacredColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "⚡ ${mantraUpdate.formattedCadenceDisplay}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = sacredColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Live active verse recitation progress for Extended Verse
                    if (technique.defaultMode == MantraMode.EXTENDED_VERSE && mantraUpdate.activeVerseDurationSeconds > 0.5f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🎙️ Reciting: ${mantraUpdate.formattedVerseTimer}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sacredColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    } else if (technique.defaultMode == MantraMode.AUMKAR_DRONE && mantraUpdate.activeVerseDurationSeconds > 0.5f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ॐ Drone: ${mantraUpdate.formattedVerseTimer}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sacredColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Bottom Controls & Mala Progress Summary
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mala Rounds Indicator
            if (mantraUpdate.targetMalas > 1) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    for (m in 1..mantraUpdate.targetMalas) {
                        val isCurrent = m == mantraUpdate.currentMala
                        val isPast = m < mantraUpdate.currentMala
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isCurrent) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCurrent -> sacredColor
                                        isPast -> sacredColor.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
            }

            Text(
                text = mantraUpdate.formattedMalaDisplay,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (selectedInputSource == MantraInputSourceType.MANUAL_BEAD_TAP) {
                    "👆 Tap anywhere to count prayer bead"
                } else {
                    "🎙️ Listening to sacred recitation • Tap to advance bead manually"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}
