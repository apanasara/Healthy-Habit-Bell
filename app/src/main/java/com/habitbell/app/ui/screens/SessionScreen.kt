package com.habitbell.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.ui.components.BreathIndicator
import com.habitbell.app.ui.components.CircularProgressRing
import com.habitbell.app.ui.components.CompoundPoseCard

@Composable
fun SessionScreen(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onTriggerPocketMode: () -> Unit,
    onOpenTVMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Exit, Activity Name, TV Mode, Pocket Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Exit Session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = sessionState.profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row {
                    IconButton(onClick = onOpenTVMode) {
                        Icon(
                            Icons.Outlined.Tv,
                            contentDescription = "TV Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onTriggerPocketMode) {
                        Icon(
                            Icons.Outlined.PhoneAndroid,
                            contentDescription = "Pocket Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Center: Ultra Minimal Countdown & Visual Guide
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (sessionState.profile.type) {
                    TimerType.LINEAR -> {
                        CircularProgressRing(
                            progress = sessionState.progressFraction,
                            size = 290.dp,
                            strokeWidth = 6.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = sessionState.formattedRemainingTime,
                                    fontSize = 68.sp,
                                    fontWeight = FontWeight.ExtraLight,
                                    letterSpacing = (-1).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                if (sessionState.profile.intervalDurationSeconds > 0) {
                                    Text(
                                        text = "Next Bell in ${sessionState.formattedNextBellTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    TimerType.MULTI_INTERVAL -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val phase = sessionState.currentPranayamaPhase
                            if (phase != null) {
                                BreathIndicator(
                                    phase = phase,
                                    remainingSeconds = sessionState.phaseRemainingSeconds,
                                    phaseDuration = sessionState.phaseDurationSeconds,
                                    size = 270.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Round ${sessionState.currentRound} of ${sessionState.totalRounds}",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Total Remaining: ${sessionState.formattedRemainingTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TimerType.COMPOUND -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val pose = sessionState.currentPose
                            if (pose != null) {
                                CompoundPoseCard(
                                    pose = pose,
                                    currentRound = sessionState.currentRound,
                                    totalRounds = sessionState.totalRounds,
                                    remainingSeconds = sessionState.poseRemainingSeconds
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = sessionState.formattedRemainingTime,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.ExtraLight,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            // Bottom Controls (PRD: "Play / Pause, Reset, Settings. No clutter.")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Main Play/Pause Button
                    val isRunning = sessionState.status == SessionStatus.RUNNING
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when (sessionState.status) {
                        SessionStatus.RUNNING -> "● Active Mindful Session"
                        SessionStatus.PAUSED -> "Paused"
                        SessionStatus.COMPLETED -> "Session Completed 🙏"
                        SessionStatus.IDLE -> "Ready"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
