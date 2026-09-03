package com.habitbell.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.ui.components.BreathIndicator
import com.habitbell.app.ui.components.CircularProgressRing
import com.habitbell.app.ui.components.CompoundPoseCard

/**
 * Active timer execution screen rendering real-time countdown progress, breathing visualizers,
 * compound posture guidance, and playback controls.
 *
 * Dynamically switches between [LandscapeSessionLayout] and [PortraitSessionLayout] depending
 * on device configuration, ensuring zero cutoffs on horizontal displays and tablets.
 *
 * @param sessionState Reactive snapshot of the active timer engine ([TimerSessionState]).
 * @param onTogglePlayPause Callback to alternate between running and paused timer execution.
 * @param onReset Callback to reset countdown back to initial profile duration.
 * @param onOpenSettings Callback to open the settings configuration drawer.
 * @param onExit Callback to exit session and return to the Home dashboard.
 * @param onTriggerPocketMode Callback to engage manual Pocket Mode AMOLED screen blanking.
 * @param onOpenTVMode Callback to open leanback TV Dashboard mode.
 * @param modifier Composable layout modifier.
 */
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLandscape) {
            LandscapeSessionLayout(
                sessionState = sessionState,
                onTogglePlayPause = onTogglePlayPause,
                onReset = onReset,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                onTriggerPocketMode = onTriggerPocketMode,
                onOpenTVMode = onOpenTVMode
            )
        } else {
            PortraitSessionLayout(
                sessionState = sessionState,
                onTogglePlayPause = onTogglePlayPause,
                onReset = onReset,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                onTriggerPocketMode = onTriggerPocketMode,
                onOpenTVMode = onOpenTVMode
            )
        }
    }
}

/**
 * Responsive 2-column landscape layout ensuring timer rings and action controls
 * remain fully visible simultaneously on horizontal devices without vertical scrolling.
 *
 * @param sessionState Reactive timer state snapshot ([TimerSessionState]).
 * @param onTogglePlayPause Toggle play/pause callback.
 * @param onReset Reset timer callback.
 * @param onOpenSettings Open settings callback.
 * @param onExit Exit to home callback.
 * @param onTriggerPocketMode Manual pocket mode trigger callback.
 * @param onOpenTVMode TV leanback mode trigger callback.
 */
@Composable
private fun LandscapeSessionLayout(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onTriggerPocketMode: () -> Unit,
    onOpenTVMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Timer Ring / Breath Indicator / Pose (Scaled for horizontal screen)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            when (sessionState.profile.type) {
                TimerType.LINEAR -> {
                    CircularProgressRing(
                        progress = sessionState.progressFraction,
                        size = 195.dp,
                        strokeWidth = 5.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onOpenSettings() }
                        ) {
                            Text(
                                text = sessionState.formattedRemainingTime,
                                fontSize = 46.sp,
                                fontWeight = FontWeight.ExtraLight,
                                letterSpacing = (-1).sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (sessionState.profile.intervalDurationSeconds > 0) {
                                Text(
                                    text = "Bell in ${sessionState.formattedNextBellTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                TimerType.MULTI_INTERVAL -> {
                    val phase = sessionState.currentPranayamaPhase
                    if (phase != null) {
                        BreathIndicator(
                            phase = phase,
                            remainingSeconds = sessionState.phaseRemainingSeconds,
                            phaseDuration = sessionState.phaseDurationSeconds,
                            size = 180.dp
                        )
                    }
                }
                TimerType.COMPOUND -> {
                    val pose = sessionState.currentPose
                    if (pose != null) {
                        CompoundPoseCard(
                            pose = pose,
                            currentRound = sessionState.currentRound,
                            totalRounds = sessionState.totalRounds,
                            remainingSeconds = sessionState.poseRemainingSeconds
                        )
                    }
                }
            }
        }

        // Right Column: Title, Quick Actions, Status, and Main Controls
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Exit Session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = sessionState.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenTVMode, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Cast,
                            contentDescription = "Cast to TV",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onTriggerPocketMode, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.PhoneAndroid,
                            contentDescription = "Pocket Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Session Status & Extra Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (sessionState.status) {
                        SessionStatus.RUNNING -> "● Active Mindful Session"
                        SessionStatus.PAUSED -> "Paused"
                        SessionStatus.COMPLETED -> "Session Completed 🙏"
                        SessionStatus.IDLE -> "Ready"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (sessionState.status == SessionStatus.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sessionState.profile.type == TimerType.MULTI_INTERVAL) {
                    Text(
                        text = "Round ${sessionState.currentRound} of ${sessionState.totalRounds} • ${sessionState.formattedRemainingTime} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom Controls (Reset, Play/Pause, Settings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                val isRunning = sessionState.status == SessionStatus.RUNNING
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "Adjust",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standard vertical portrait layout arranging header navigation, central circular timer /
 * breath indicator, and bottom transport control buttons.
 *
 * @param sessionState Reactive timer state snapshot ([TimerSessionState]).
 * @param onTogglePlayPause Toggle play/pause callback.
 * @param onReset Reset timer callback.
 * @param onOpenSettings Open settings callback.
 * @param onExit Exit to home callback.
 * @param onTriggerPocketMode Manual pocket mode trigger callback.
 * @param onOpenTVMode TV leanback mode trigger callback.
 */
@Composable
private fun PortraitSessionLayout(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onTriggerPocketMode: () -> Unit,
    onOpenTVMode: () -> Unit
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenTVMode) {
                    Icon(
                        Icons.Outlined.Cast,
                        contentDescription = "Cast / TV Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
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
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
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
                        size = 280.dp,
                        strokeWidth = 6.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onOpenSettings() }
                        ) {
                            Text(
                                text = sessionState.formattedRemainingTime,
                                fontSize = 66.sp,
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
                            } else {
                                Text(
                                    text = "Tap to adjust duration",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                                size = 260.dp
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
