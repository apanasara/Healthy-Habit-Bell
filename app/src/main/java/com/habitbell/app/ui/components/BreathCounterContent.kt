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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.breath.BreathCounterPhase
import com.habitbell.app.breath.BreathInputSourceType
import com.habitbell.app.breath.BreathStrokeUpdate
import com.habitbell.app.breath.BreathTechnique
import com.habitbell.app.engine.TimerSessionState

/**
 * # BreathCounterContent
 *
 * Dedicated Compose visualizer for real-time breath stroke counting sessions
 * (Kapalabhati, Bhastrika, and Bhramari).
 *
 * ## Architectural Role & Relationships
 * - Hosted by [com.habitbell.app.ui.screens.SessionScreen] when `profile.isBreathCountingEnabled` is true.
 * - Renders real-time acoustic ripples driven by [BreathStrokeUpdate.audioAmplitudeRms].
 * - Exposes interactive full-surface tap counting for silent halls and manual overrides.
 * - Displays yogic state progression: rapid stroke pumping -> golden retention hold -> resting aura.
 *
 * ## Concurrency & Recomposition
 * - Reacts reactively to [BreathStrokeUpdate] state emissions.
 * - Uses smooth Compose hardware-accelerated animators for 60 FPS acoustic wave pulses.
 *
 * @param sessionState Current authoritative timer session state.
 * @param breathUpdate Real-time breath metrics and round progression.
 * @param selectedInputSource Active input provider ([BreathInputSourceType]).
 * @param onSelectInputSource Callback to switch between microphone and touch screen mode.
 * @param onSelectSensitivity Callback to adjust microphone detection sensitivity.
 * @param onSelectTechnique Callback to switch between classical breathwork modalities.
 * @param onManualStrokeTap Callback invoked when user taps the active counting surface.
 * @param modifier Composable layout modifier.
 */
@Composable
fun BreathCounterContent(
    sessionState: TimerSessionState,
    breathUpdate: BreathStrokeUpdate,
    selectedInputSource: BreathInputSourceType,
    onSelectInputSource: (BreathInputSourceType) -> Unit,
    onSelectSensitivity: (Float) -> Unit = {},
    onSelectTechnique: (BreathTechnique) -> Unit = {},
    onManualStrokeTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = sessionState.profile
    val config = profile.breathCounterConfig
    val technique = config?.technique ?: BreathTechnique.KAPALABHATI

    // Live acoustic ripple scale factor (0.0f..1.0f)
    val animatedRms by animateFloatAsState(
        targetValue = breathUpdate.audioAmplitudeRms.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "AcousticRms"
    )

    // Breathing pulse for ambient resting circles
    val infiniteTransition = rememberInfiniteTransition(label = "AcousticAmbientPulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientPulse"
    )

    // Phase-specific aura color
    val phaseAuraColor by animateColorAsState(
        targetValue = when (breathUpdate.currentPhase) {
            BreathCounterPhase.STROKES -> Color(0xFFFF9800) // Warm energetic amber
            BreathCounterPhase.RETENTION_HOLD -> Color(0xFFFFD700) // Sacred golden Kumbhaka
            BreathCounterPhase.REST -> Color(0xFF00E5FF) // Cool calm cyan
            BreathCounterPhase.COMPLETED -> Color(0xFF4CAF50) // Vibrant emerald
            BreathCounterPhase.PREPARATION -> Color(0xFF00E5FF) // Cool calm cyan ambient calibration
        },
        animationSpec = tween(durationMillis = 600),
        label = "PhaseAura"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Header: Technique Sanskrit & Title + Input Switcher
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = technique.sanskritScript,
                style = MaterialTheme.typography.titleMedium,
                color = phaseAuraColor.copy(alpha = 0.85f),
                letterSpacing = 2.sp
            )
            Text(
                text = "${technique.displayName} Counter",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = breathUpdate.currentPhase.guidanceCue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Technique Switcher Chips (Available in Preparation or Idle)
            if (sessionState.status == com.habitbell.app.engine.SessionStatus.IDLE || breathUpdate.currentPhase == BreathCounterPhase.PREPARATION) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    val techniques = listOf(
                        BreathTechnique.KAPALABHATI,
                        BreathTechnique.BHASTRIKA,
                        BreathTechnique.BHRAMARI
                    )
                    techniques.forEach { t ->
                        val isSelected = technique == t
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectTechnique(t) },
                            label = { Text("${t.sanskritScript} ${t.displayName}", fontSize = 11.sp) },
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    }
                }
            }

            // Input Mode Switcher Chips (Mic vs Tap)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedInputSource == BreathInputSourceType.ACOUSTIC_MIC,
                    onClick = { onSelectInputSource(BreathInputSourceType.ACOUSTIC_MIC) },
                    label = { Text("🎙️ Mic Sensor", fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = selectedInputSource == BreathInputSourceType.MANUAL_TAP,
                    onClick = { onSelectInputSource(BreathInputSourceType.MANUAL_TAP) },
                    label = { Text("👆 Tap Counter", fontSize = 12.sp) }
                )
            }

            // Live Mic Sensitivity & Audio Level Meter (Acoustic Mode)
            if (selectedInputSource == BreathInputSourceType.ACOUSTIC_MIC && 
                (breathUpdate.currentPhase == BreathCounterPhase.STROKES || breathUpdate.currentPhase == BreathCounterPhase.PREPARATION)) {
                Spacer(modifier = Modifier.height(8.dp))

                // Sensitivity selector chips: [Low] [Med] [High]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "Sensitivity: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    val currentSens = breathUpdate.micSensitivity
                    listOf(
                        Pair("Low", 0.7f),
                        Pair("Med", 1.0f),
                        Pair("High", 1.5f)
                    ).forEach { (label, value) ->
                        val isSelected = kotlin.math.abs(currentSens - value) < 0.2f
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) phaseAuraColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.dp, phaseAuraColor) else null,
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clickable { onSelectSensitivity(value) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) phaseAuraColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Live Audio Level & Threshold Gauge Bar
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    val totalWidth = maxWidth
                    val fillWidth = totalWidth * animatedRms.coerceIn(0f, 1f)
                    val thresholdOffset = totalWidth * breathUpdate.thresholdRms.coerceIn(0f, 1f)
                    val isOverThreshold = animatedRms >= breathUpdate.thresholdRms

                    // Active energy level
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(fillWidth)
                            .background(
                                if (isOverThreshold) phaseAuraColor else Color(0xFF4CAF50).copy(alpha = 0.85f)
                            )
                    )

                    // Vertical Threshold Needle
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

        // 2. Hero Centerpiece: Pulsing Acoustic Counter / Retention Ring
        Box(
            modifier = Modifier
                .size(310.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onManualStrokeTap()
                },
            contentAlignment = Alignment.Center
        ) {
            // Background interactive Canvas with acoustic ripples
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (size.minDimension / 2f) - 16.dp.toPx()
                val rippleScale = 1.0f + (animatedRms * 0.15f)

                // Outer acoustic energy halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            phaseAuraColor.copy(alpha = 0.25f * animatedRms.coerceAtLeast(0.2f)),
                            phaseAuraColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.25f * rippleScale
                    ),
                    center = center,
                    radius = baseRadius * 1.25f * rippleScale
                )

                // Secondary subtle harmonic ripple
                drawCircle(
                    color = phaseAuraColor.copy(alpha = 0.20f),
                    center = center,
                    radius = baseRadius * ambientPulse,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Primary inner stroke boundary circle
                drawCircle(
                    color = phaseAuraColor.copy(alpha = 0.65f),
                    center = center,
                    radius = baseRadius,
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // Central Progress Ring enclosing the hero numbers
            CircularProgressRing(
                progress = breathUpdate.roundProgressFraction,
                size = 300.dp,
                strokeWidth = 6.dp
            ) {
                // Central Information Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    when (breathUpdate.currentPhase) {
                        BreathCounterPhase.STROKES -> {
                            if (technique == BreathTechnique.BHRAMARI) {
                                Text(
                                    text = "HUMMING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = phaseAuraColor,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = breathUpdate.formattedHumDuration,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = breathUpdate.formattedRoundDisplay,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "STROKES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = phaseAuraColor,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = "${breathUpdate.currentRoundStrokes}",
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Target: ${breathUpdate.targetRoundStrokes}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Cadence Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = phaseAuraColor.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "⚡ ${breathUpdate.formattedCadenceDisplay}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = phaseAuraColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        BreathCounterPhase.RETENTION_HOLD -> {
                            Text(
                                text = "KUMBHAKA • HOLD IN",
                                style = MaterialTheme.typography.labelSmall,
                                color = phaseAuraColor,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "${breathUpdate.phaseRemainingSeconds}s",
                                fontSize = 58.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = phaseAuraColor
                            )
                            Text(
                                text = "Tri-Bandha Engaged",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        BreathCounterPhase.REST -> {
                            Text(
                                text = "STILLNESS • REST",
                                style = MaterialTheme.typography.labelSmall,
                                color = phaseAuraColor,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "${breathUpdate.phaseRemainingSeconds}s",
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = phaseAuraColor
                            )
                            Text(
                                text = "Observe natural breath",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        BreathCounterPhase.COMPLETED -> {
                            Text(
                                text = "COMPLETE",
                                style = MaterialTheme.typography.labelSmall,
                                color = phaseAuraColor,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "ॐ",
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                color = phaseAuraColor
                            )
                            Text(
                                text = "Sadhana Finished",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        BreathCounterPhase.PREPARATION -> {
                            Text(
                                text = "CALIBRATING ROOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = phaseAuraColor,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = if (breathUpdate.phaseRemainingSeconds > 0) "${breathUpdate.phaseRemainingSeconds}s" else "Calibrating",
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = phaseAuraColor
                            )
                            Text(
                                text = "Measuring ambient sound",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom Controls & Milestone Round Indicators
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Round Dots indicator
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                for (r in 1..breathUpdate.targetRounds) {
                    val isCurrent = r == breathUpdate.currentRound
                    val isPast = r < breathUpdate.currentRound
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> phaseAuraColor
                                    isPast -> phaseAuraColor.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            Text(
                text = breathUpdate.formattedRoundDisplay,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (selectedInputSource == BreathInputSourceType.MANUAL_TAP) {
                    "👆 Tap circle on every exhale stroke"
                } else {
                    "🎙️ Listening to breath • Tap circle to add stroke manually"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}
