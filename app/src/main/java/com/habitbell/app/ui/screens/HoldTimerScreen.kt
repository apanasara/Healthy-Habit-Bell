package com.habitbell.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.R
import com.habitbell.app.holdtimer.HoldTimerManager
import com.habitbell.app.holdtimer.HoldTimerPhase
import com.habitbell.app.holdtimer.HoldTimerSessionState
import com.habitbell.app.ui.components.CircularProgressRing
import java.util.Locale

/**
 * # HoldTimerScreen
 *
 * Dedicated Jetpack Compose presentation surface for hands-free Voice-Driven
 * Yoga & Physiotherapy Hold Timer sessions.
 *
 * ## Architectural Role & Component Relationships
 * - Observes reactive [HoldTimerSessionState] from [HoldTimerManager].
 * - Completely styled and aligned with [SessionScreen] and the Pranayama design system:
 *   - Punch-hole safe top action bar with Phosphor back, sun/moon theme, volume, TV casting, and settings buttons.
 *   - Pre-session interactive setup chips (Hold duration, Rest duration, Repeat rounds, Voice cadence)
 *     when in [HoldTimerPhase.PREPARATION] mode before countdown commences.
 *   - Centered [CircularProgressRing] featuring dynamic phase colors, ambient glow, and high-legibility numerals.
 *   - Two-way voice command binding ("Hey Yoga, hold 45 seconds", "Too fast", "Pause").
 *
 * ## Lifecycle & Concurrency
 * Pure Compose UI function running on Android Main thread.
 *
 * @param manager Domain coordinator governing active hold timer execution.
 * @param onNavigateBack Navigation callback to return to the previous screen or home dashboard.
 * @param onToggleTheme Callback allowing toggling visual light/dark/AMOLED themes from the top bar.
 * @param onOpenSettings Callback opening the unified settings drawer for this profile.
 * @param onOpenVolumeSettings Callback opening the master volume adjustment sheet.
 * @param onOpenCastSettings Callback opening the TV screen mirroring & casting sheet.
 */
@Composable
fun HoldTimerScreen(
    manager: HoldTimerManager,
    onNavigateBack: () -> Unit,
    onToggleTheme: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenVolumeSettings: () -> Unit = {},
    onOpenCastSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by manager.sessionState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Theme-aware button pills matching SessionScreen standard
    val buttonPillBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.75f)
    val buttonBorder = BorderStroke(
        1.dp,
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    )

    // Harmonious circadian phase colors matching Pranayama aesthetics
    val phaseColor by animateColorAsState(
        targetValue = when (state.phase) {
            HoldTimerPhase.HOLD -> MaterialTheme.colorScheme.primary       // Energy / Focus
            HoldTimerPhase.REST -> Color(0xFFFFB300)                      // Amber / Soothing Rest
            HoldTimerPhase.PREPARATION -> Color(0xFF81C784)               // Soft Green / Gentle Readiness
            HoldTimerPhase.PAUSED -> Color(0xFF9E9E9E)                    // Neutral Slate
            HoldTimerPhase.COMPLETED -> Color(0xFFB388FF)                 // Sacred Purple Bloom
        },
        animationSpec = tween(durationMillis = 400),
        label = "phaseColorAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // -------------------------------------------------------------
            // Top Section: Clean Action Bar + Lowered Activity Title
            // (100% immune to camera punch-hole, strictly matching SessionScreen)
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(42.dp)
                            .background(buttonPillBg, CircleShape)
                            .border(buttonBorder, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ph_back),
                            contentDescription = "Exit Session",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Top Action Icons: TV Cast, Volume, Sun/Moon Theme, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onOpenCastSettings,
                            modifier = Modifier
                                .size(42.dp)
                                .background(buttonPillBg, CircleShape)
                                .border(buttonBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tv,
                                contentDescription = "Cast & Mirror",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenVolumeSettings,
                            modifier = Modifier
                                .size(42.dp)
                                .background(buttonPillBg, CircleShape)
                                .border(buttonBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = "Volume Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(42.dp)
                                .background(buttonPillBg, CircleShape)
                                .border(buttonBorder, CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = if (isDark) R.drawable.ic_ph_sun else R.drawable.ic_ph_moon),
                                contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(42.dp)
                                .background(buttonPillBg, CircleShape)
                                .border(buttonBorder, CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ph_tune),
                                contentDescription = "Hold Timer Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title and Hands-free Voice Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hold Timer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Yoga & Physiotherapy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Hands-Free Microphone Indicator Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Active",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Hey Yoga",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            // Maintain reactive activeConfig state for interactive pre-session chips
            var activeConfig by remember { mutableStateOf(manager.getActiveConfig()) }

            // 1. Safety Alert Banner (if hold exceeds clinician threshold)
            val warning = state.safetyWarning
            if (warning != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 2. Voice Command Feedback Pill
            AnimatedVisibility(
                visible = state.feedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .border(buttonBorder, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = phaseColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.feedbackMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Central Circular Countdown Display (Matching SessionScreen / Pranayama aesthetics)
            val progressFraction = if (state.totalSecondsInPhase > 0) {
                state.currentSecond.toFloat() / state.totalSecondsInPhase.toFloat()
            } else 0f

            CircularProgressRing(
                progress = progressFraction,
                size = 230.dp,
                strokeWidth = 5.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onOpenSettings() }
                ) {
                    // Phase Tag Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = phaseColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = when (state.phase) {
                                HoldTimerPhase.PREPARATION -> "PREPARE"
                                HoldTimerPhase.HOLD -> "HOLD"
                                HoldTimerPhase.REST -> "REST"
                                HoldTimerPhase.PAUSED -> "PAUSED"
                                HoldTimerPhase.COMPLETED -> "COMPLETE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = phaseColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Minimalist ExtraLight Countdown Numerals (Matching SessionScreen typography)
                    Text(
                        text = "${state.currentSecond}",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = (-1).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "of ${state.totalSecondsInPhase}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Round Ordinal Indicator
                    Text(
                        text = "Round ${state.currentRound} of ${state.totalRounds}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4. Configuration & Pacing Controls
            if (state.phase == HoldTimerPhase.PREPARATION) {
                // Pre-Session Configuration Panel: Quick Chips for Hold, Rest, Rounds, Voice Cadence
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Hold Duration Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hold",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(15, 30, 45, 60).forEach { sec ->
                                val isSelected = activeConfig.holdDurationSec == sec
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        manager.updateHoldDuration(sec)
                                        activeConfig = manager.getActiveConfig()
                                    },
                                    label = { Text("${sec}s", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }

                    // Row 2: Rest Duration Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rest",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(0 to "0s", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (sec, label) ->
                                val isSelected = activeConfig.restDurationSec == sec
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        manager.updateRestDuration(sec)
                                        activeConfig = manager.getActiveConfig()
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }

                    // Row 3: Repeat Rounds Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rounds",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(2, 3, 4, 5, 8).forEach { rounds ->
                                val isSelected = activeConfig.repeatCount == rounds
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        manager.updateRepeatCount(rounds)
                                        activeConfig = manager.getActiveConfig()
                                    },
                                    label = { Text("${rounds}x", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }

                    // Row 4: Voice Cadence / Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cadence",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                0.75f to "0.75x",
                                0.85f to "0.85x",
                                1.0f to "1.0x",
                                1.15f to "1.15x"
                            ).forEach { (speed, label) ->
                                val isSelected = kotlin.math.abs(activeConfig.ttsSpeed - speed) < 0.05f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        manager.updateVoiceSpeed(speed)
                                        activeConfig = manager.getActiveConfig()
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // In-Session Adaptive Speed Chips ("Too Fast" / "Too Slow")
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            manager.engine.adjustPace(-0.15f)
                            activeConfig = manager.getActiveConfig()
                        },
                        label = { Text("Too Fast (Slower)", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.height(34.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilterChip(
                        selected = false,
                        onClick = {
                            manager.engine.adjustPace(+0.15f)
                            activeConfig = manager.getActiveConfig()
                        },
                        label = { Text("Too Slow (Faster)", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.height(34.dp)
                    )
                }
            }

            // 5. Playback Transport & Export Bar (Matching SessionScreen aesthetics)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                IconButton(
                    onClick = { manager.reset() },
                    modifier = Modifier
                        .size(46.dp)
                        .background(buttonPillBg, CircleShape)
                        .border(buttonBorder, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_reset),
                        contentDescription = "Reset Session",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause Primary Floating Action Button
                val isRunning = state.phase != HoldTimerPhase.PREPARATION && !state.isPaused && state.phase != HoldTimerPhase.COMPLETED
                FilledIconButton(
                    onClick = {
                        if (state.phase == HoldTimerPhase.PREPARATION) {
                            manager.startSession()
                        } else if (state.isPaused) {
                            manager.resume()
                        } else if (state.phase == HoldTimerPhase.COMPLETED) {
                            manager.reset()
                            manager.startSession()
                        } else {
                            manager.pause()
                        }
                    },
                    modifier = Modifier.size(62.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isRunning) R.drawable.ic_ph_pause else R.drawable.ic_ph_play
                        ),
                        contentDescription = if (isRunning) "Pause" else "Play",
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Skip Round Button
                IconButton(
                    onClick = { manager.skipToNextRound() },
                    modifier = Modifier
                        .size(46.dp)
                        .background(buttonPillBg, CircleShape)
                        .border(buttonBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Round",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Export CSV/JSON Button
                IconButton(
                    onClick = {
                        try {
                            val file = manager.logger.exportToFile(context, "csv")
                            val shareIntent = manager.logger.createShareIntent(context, file)
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Session Telemetry"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(buttonPillBg, CircleShape)
                        .border(buttonBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export CSV",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
