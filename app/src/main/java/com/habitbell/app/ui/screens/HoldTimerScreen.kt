package com.habitbell.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.holdtimer.HoldTimerManager
import com.habitbell.app.holdtimer.HoldTimerPhase
import com.habitbell.app.holdtimer.HoldTimerSessionState

/**
 * # HoldTimerScreen
 *
 * Dedicated Jetpack Compose presentation surface for hands-free Voice-Driven
 * Yoga & Physiotherapy Hold Timer sessions.
 *
 * ## Architectural Role & Component Relationships
 * - Observes reactive [HoldTimerSessionState] from [HoldTimerManager].
 * - Renders color-coded hold and rest countdown rings, round indicators, and voice speech feedback.
 * - Bridges hands-free voice commands with tactile on-screen controls for pacing, pause, skip, and CSV/JSON export.
 *
 * ## Lifecycle & Concurrency
 * Pure Compose UI function running on Android Main thread.
 *
 * @param manager Domain coordinator governing active hold timer execution.
 * @param onNavigateBack Navigation callback to return to the previous screen or home dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldTimerScreen(
    manager: HoldTimerManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by manager.sessionState.collectAsState()

    // Color theme transitions based on active phase
    val phaseColor by animateColorAsState(
        targetValue = when (state.phase) {
            HoldTimerPhase.HOLD -> Color(0xFF00E5FF)       // Cyan / Energy
            HoldTimerPhase.REST -> Color(0xFFFFB300)       // Amber / Rest
            HoldTimerPhase.PREPARATION -> Color(0xFF81C784) // Soft Green
            HoldTimerPhase.PAUSED -> Color(0xFF9E9E9E)     // Neutral Gray
            HoldTimerPhase.COMPLETED -> Color(0xFFB388FF)  // Purple Bloom
        },
        label = "phaseColorAnim"
    )

    val progressAnim by animateFloatAsState(
        targetValue = state.progressFraction,
        label = "progressAnim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Voice Hold Timer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Yoga & Physiotherapy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Hands-free Voice Status Badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Active",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Hey Yoga",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Safety Alert Banner (if hold exceeds clinician threshold)
            val warning = state.safetyWarning
            if (warning != null) {
                Surface(
                    color = Color(0xFF3E2723),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, Color(0xFFFF5722), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFCCBC)
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
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(20.dp))
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
                            color = Color.White
                        )
                    }
                }
            }

            // 3. Central Circular Countdown Display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .padding(16.dp)
            ) {
                // Background Track
                CircularProgressIndicator(
                    progress = 1.0f,
                    strokeWidth = 10.dp,
                    color = Color(0xFF222222),
                    modifier = Modifier.fillMaxSize()
                )

                // Animated Progress Ring
                CircularProgressIndicator(
                    progress = progressAnim,
                    strokeWidth = 10.dp,
                    color = phaseColor,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Phase Tag
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = phaseColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = state.phase.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = phaseColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Huge Numeric Second Counter
                    Text(
                        text = "${state.currentSecond}",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Text(
                        text = "of ${state.totalSecondsInPhase}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Round Ordinal Indicator
                    Text(
                        text = "Round ${state.currentRound} of ${state.totalRounds}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4. Adaptive Speed Chips ("Too Fast" / "Too Slow")
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = false,
                    onClick = { manager.engine.adjustPace(-0.15f) },
                    label = { Text("Too Fast (Slower)") },
                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilterChip(
                    selected = false,
                    onClick = { manager.engine.adjustPace(+0.15f) },
                    label = { Text("Too Slow (Faster)") },
                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            // 5. Playback Transport & Export Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Export CSV",
                        tint = Color.White
                    )
                }

                // Play / Pause Floating Action Button
                FilledIconButton(
                    onClick = {
                        if (state.isPaused || state.phase == HoldTimerPhase.PREPARATION) {
                            if (state.phase == HoldTimerPhase.PREPARATION) {
                                manager.startSession()
                            } else {
                                manager.resume()
                            }
                        } else {
                            manager.pause()
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = phaseColor)
                ) {
                    Icon(
                        imageVector = if (state.isPaused || state.phase == HoldTimerPhase.PREPARATION) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isPaused) "Resume" else "Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Skip Round Button
                IconButton(
                    onClick = { manager.skipToNextRound() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next Round",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
