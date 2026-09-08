package com.habitbell.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.habitbell.app.R
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.TimerType
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState
import com.habitbell.app.ui.components.BreathIndicator
import com.habitbell.app.ui.components.CastButton
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
 * @param onToggleTheme Callback to centrally cycle or toggle the application visual theme.
 * @param onUserInteraction Callback triggered when the user interacts with the display to wake from dimming.
 * @param modifier Composable layout modifier.
 */
@Composable
fun SessionScreen(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onToggleTheme: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Smooth visual luminance transition between power-saving dimmed state and active alert state
    val contentAlpha by animateFloatAsState(
        targetValue = if (sessionState.isDimmed) 0.35f else 1.0f,
        animationSpec = tween(durationMillis = 600),
        label = "SessionContentAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onUserInteraction()
                    }
                }
            }
            .alpha(contentAlpha)
    ) {
        if (isLandscape) {
            LandscapeSessionLayout(
                sessionState = sessionState,
                onTogglePlayPause = onTogglePlayPause,
                onReset = onReset,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                onToggleTheme = onToggleTheme
            )
        } else {
            PortraitSessionLayout(
                sessionState = sessionState,
                onTogglePlayPause = onTogglePlayPause,
                onReset = onReset,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                onToggleTheme = onToggleTheme
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
 * @param onToggleTheme Central theme switcher callback.
 */
@Composable
private fun LandscapeSessionLayout(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val buttonPillBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.75f)
    val buttonBorder = BorderStroke(
        1.dp,
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    )

    val isEating = sessionState.profile.category.contains("Eating", ignoreCase = true) ||
        sessionState.profile.id.contains("eating", ignoreCase = true)
    val isPranayama = sessionState.profile.type == TimerType.MULTI_INTERVAL

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Visualizer Area (Hero Lotus / Eating Bowl / Circular Progress / Pose)
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (isEating) {
                MindfulEatingLandscapeContent(
                    sessionState = sessionState,
                    onOpenSettings = onOpenSettings
                )
            } else when (sessionState.profile.type) {
                TimerType.MULTI_INTERVAL -> {
                    val phase = sessionState.currentPranayamaPhase
                    if (phase != null) {
                        BreathIndicator(
                            phase = phase,
                            remainingSeconds = sessionState.phaseRemainingSeconds,
                            phaseDuration = sessionState.phaseDurationSeconds,
                            size = 260.dp,
                            showHud = false,
                            modifier = Modifier.clickable { onOpenSettings() }
                        )
                    }
                }
                TimerType.LINEAR -> {
                    val progress = if (sessionState.profile.stepTriggerMode == com.habitbell.app.data.model.StepTriggerMode.STEPS_ONLY) {
                        sessionState.stepProgressFraction ?: sessionState.progressFraction
                    } else {
                        sessionState.progressFraction
                    }

                    CircularProgressRing(
                        progress = progress,
                        size = 210.dp,
                        strokeWidth = 5.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onOpenSettings() }
                        ) {
                            if (sessionState.profile.stepTriggerMode == com.habitbell.app.data.model.StepTriggerMode.STEPS_ONLY) {
                                Text(
                                    text = "%,d".format(sessionState.currentSteps),
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.ExtraLight,
                                    letterSpacing = (-1).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "steps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = sessionState.formattedRemainingTime,
                                    fontSize = 46.sp,
                                    fontWeight = FontWeight.ExtraLight,
                                    letterSpacing = (-1).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
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

        Spacer(modifier = Modifier.width(16.dp))

        // Right Column: Action Bar, Elevated HUD / Rhythm Capsule, and Controls
        Column(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(38.dp)
                        .background(buttonPillBg, CircleShape)
                        .border(buttonBorder, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_back),
                        contentDescription = "Exit Session",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = if (isEating) "Mindful Eating" else sessionState.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sessionState.profile.isCastSupported) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(buttonPillBg, CircleShape)
                                .border(buttonBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CastButton(modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(38.dp)
                            .background(buttonPillBg, CircleShape)
                            .border(buttonBorder, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = if (isDark) R.drawable.ic_ph_sun else R.drawable.ic_ph_moon),
                            contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .background(buttonPillBg, CircleShape)
                            .border(buttonBorder, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ph_tune),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Middle: Status / HUD (Lean design without redundant rhythm bar)
            if (isPranayama) {
                val phase = sessionState.currentPranayamaPhase
                if (phase != null) {
                    val primaryColor = MaterialTheme.colorScheme.primary

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = phase.sanskritName.uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                                color = primaryColor
                            )
                            Text(
                                text = phase.sanskritScript,
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryColor.copy(alpha = 0.85f)
                            )
                        }

                        // Tri-Bandha guidance in Landscape HUD during Kumbhaka
                        val bandhaText = phase.bandhaScript
                        val bandhaEng = phase.bandhaEnglish
                        if (bandhaText != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bandhaText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = primaryColor.copy(alpha = 0.85f)
                            )
                        }
                        if (bandhaEng != null) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = bandhaEng,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Light,
                                color = primaryColor.copy(alpha = 0.75f)
                            )
                        }

                        Text(
                            text = "${sessionState.phaseRemainingSeconds}",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraLight,
                            letterSpacing = (-1).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Round ${sessionState.currentRound} of ${sessionState.totalRounds} • ${sessionState.formattedRemainingTime} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isEating) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = sessionState.formattedRemainingTime,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = (-1).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ph_bell),
                                contentDescription = "Bite Bell",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Bite in ${sessionState.formattedNextBellTime}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
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
                    if (sessionState.isStepTrackingActive) {
                        Text(
                            text = "🚶 ${sessionState.formattedStepCount} • Cadence: ${sessionState.formattedCadence}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (sessionState.profile.intervalDurationSeconds > 0) {
                        Text(
                            text = "Bell in ${sessionState.formattedNextBellTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Bottom Transport Controls (Phosphor Line Icons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(46.dp)
                        .background(buttonPillBg, shape = CircleShape)
                        .border(buttonBorder, shape = CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_reset),
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val isRunning = sessionState.status == SessionStatus.RUNNING
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(62.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                ) {
                    Icon(
                        painter = painterResource(id = if (isRunning) R.drawable.ic_ph_pause else R.drawable.ic_ph_play),
                        contentDescription = if (isRunning) "Pause" else "Play",
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(46.dp)
                        .background(buttonPillBg, shape = CircleShape)
                        .border(buttonBorder, shape = CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_tune),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
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
 * @param onToggleTheme Central theme switcher callback.
 */
@Composable
private fun PortraitSessionLayout(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onToggleTheme: () -> Unit
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
        val isEating = sessionState.profile.category.contains("Eating", ignoreCase = true) ||
            sessionState.profile.id.contains("eating", ignoreCase = true)

        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val buttonPillBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.75f)
        val buttonBorder = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // Top Section: Clean Action Bar + Lowered Activity Title (100% immune to camera punch-hole)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Action Bar: Back button on left, Cast / Pocket / Settings on right
            // The top center is kept completely open so the camera cutout never overlaps any interactive element
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onExit,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sessionState.profile.isCastSupported) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(buttonPillBg, CircleShape)
                                .border(buttonBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CastButton(modifier = Modifier.size(24.dp))
                        }
                    }
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (sessionState.profile.type != TimerType.MULTI_INTERVAL) {
                Spacer(modifier = Modifier.height(14.dp))

                // Lowered Title: Centered, serene, and completely below the physical camera cutout
                Text(
                    text = if (isEating) "Mindful Eating" else sessionState.profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Center: Ultra Minimal Countdown & Visual Guide
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isEating) {
                MindfulEatingContent(
                    sessionState = sessionState,
                    onOpenSettings = onOpenSettings
                )
            } else when (sessionState.profile.type) {
                TimerType.LINEAR -> {
                    val progress = if (sessionState.profile.stepTriggerMode == com.habitbell.app.data.model.StepTriggerMode.STEPS_ONLY) {
                        sessionState.stepProgressFraction ?: sessionState.progressFraction
                    } else {
                        sessionState.progressFraction
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressRing(
                            progress = progress,
                            size = 280.dp,
                            strokeWidth = 6.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onOpenSettings() }
                            ) {
                                if (sessionState.profile.stepTriggerMode == com.habitbell.app.data.model.StepTriggerMode.STEPS_ONLY) {
                                    Text(
                                        text = "%,d".format(sessionState.currentSteps),
                                        fontSize = 58.sp,
                                        fontWeight = FontWeight.ExtraLight,
                                        letterSpacing = (-1).sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "steps",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 2.sp
                                    )
                                } else {
                                    Text(
                                        text = sessionState.formattedRemainingTime,
                                        fontSize = 66.sp,
                                        fontWeight = FontWeight.ExtraLight,
                                        letterSpacing = (-1).sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (sessionState.isStepTrackingActive) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "🚶 ${sessionState.formattedStepCount}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val nextStepBell = sessionState.nextStepBellSteps
                                    if (nextStepBell != null && nextStepBell > 0) {
                                        Text(
                                            text = "🔔 Next bell in %,d steps".format(nextStepBell),
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 1.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (sessionState.profile.intervalDurationSeconds > 0) {
                                        Text(
                                            text = "Next Bell in ${sessionState.formattedNextBellTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 1.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else if (sessionState.profile.intervalDurationSeconds > 0) {
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

                        if (sessionState.isStepTrackingActive) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "Cadence: ${sessionState.formattedCadence}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = sessionState.healthProvider.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                TimerType.MULTI_INTERVAL -> {
                    PranayamaPortraitContent(
                        sessionState = sessionState,
                        onOpenSettings = onOpenSettings
                    )
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

        // Bottom Controls (Play / Pause, Reset, Settings with Phosphor Line Icons)
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
                // Reset Button (Phosphor Line Icon)
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(54.dp)
                        .background(buttonPillBg, shape = CircleShape)
                        .border(buttonBorder, shape = CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_reset),
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Main Play/Pause Button (Phosphor Line Icon in primary container)
                val isRunning = sessionState.status == SessionStatus.RUNNING
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                ) {
                    Icon(
                        painter = painterResource(id = if (isRunning) R.drawable.ic_ph_pause else R.drawable.ic_ph_play),
                        contentDescription = if (isRunning) "Pause" else "Play",
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Settings Button (Phosphor Line Icon)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(54.dp)
                        .background(buttonPillBg, shape = CircleShape)
                        .border(buttonBorder, shape = CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_tune),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when (sessionState.status) {
                    SessionStatus.RUNNING -> if (isEating) "● Mindful Chewing Rhythm" else "● Active Mindful Session"
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

/**
 * Mindful Eating specific visualizer.
 * Minimizes cognitive load through prominent Phosphor bowl & bell icons,
 * warm candlelit radial aura, total meal ring, and an inner bite-cycle progress indicator.
 *
 * @param sessionState Reactive timer state snapshot ([TimerSessionState]).
 * @param onOpenSettings Open settings callback.
 */
@Composable
private fun MindfulEatingContent(
    sessionState: TimerSessionState,
    onOpenSettings: () -> Unit
) {
    val progress = sessionState.progressFraction
    val isRunning = sessionState.status == SessionStatus.RUNNING

    // Calculate normalized progress within the current bite interval (0.0f -> 1.0f)
    val intervalDuration = sessionState.profile.intervalDurationSeconds
    val biteProgress = if (intervalDuration > 0) {
        (1f - (sessionState.nextBellSeconds.toFloat() / intervalDuration.toFloat())).coerceIn(0f, 1f)
    } else 0f

    val animatedBiteProgress by animateFloatAsState(
        targetValue = biteProgress,
        animationSpec = tween(durationMillis = 800),
        label = "BiteProgress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Ambient candlelit radial aura behind the bowl
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isRunning) 0.15f else 0.05f),
                            Color.Transparent
                        ),
                        radius = 450f
                    )
                )
        )

        // Mealtime Circular Progress Ring
        CircularProgressRing(
            progress = progress,
            size = 300.dp,
            strokeWidth = 5.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpenSettings() }
            ) {
                // Phosphor Bowl Line Icon framed by the active Bite-Cycle progress ring
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Bite cycle progress ring (inner ring sweeping every bite interval)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val stroke = 3.dp.toPx()
                        val radius = (size.minDimension - stroke) / 2f

                        // Subtle track
                        drawCircle(
                            color = surfaceVariantColor.copy(alpha = 0.35f),
                            radius = radius,
                            style = Stroke(width = stroke)
                        )

                        // Active bite arc
                        if (intervalDuration > 0) {
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedBiteProgress,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Inner bowl icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_bowl),
                        contentDescription = "Mindful Eating",
                        tint = primaryColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Large, readable countdown numerals
                Text(
                    text = sessionState.formattedRemainingTime,
                    fontSize = 62.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1.5).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bite Pacing Rhythm Capsule (driven through icons)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceVariantColor.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ph_bell),
                            contentDescription = "Bite Bell",
                            tint = primaryColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = sessionState.formattedNextBellTime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        )
                        Text(
                            text = "• CHEW & SAVOR",
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Classical Pranayama multi-interval breathwork visualizer for portrait mode.
 * Features an enlarged heroic screen-width side-view blooming lotus, integrated non-overlapping HUD,
 * and minimalist round milestone status badge without redundant rhythm pills.
 *
 * @param sessionState Reactive timer state snapshot ([TimerSessionState]).
 * @param onOpenSettings Open settings callback.
 */
@Composable
private fun PranayamaPortraitContent(
    sessionState: TimerSessionState,
    onOpenSettings: () -> Unit
) {
    val phase = sessionState.currentPranayamaPhase ?: return
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val themePrimary = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Hero Enlarged Side-View Blooming Lotus covering full screen width
        BreathIndicator(
            phase = phase,
            remainingSeconds = sessionState.phaseRemainingSeconds,
            phaseDuration = sessionState.phaseDurationSeconds,
            showHud = true,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clickable { onOpenSettings() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Round Milestone Status Capsule
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.6f),
            border = BorderStroke(
                1.dp,
                if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Round ${sessionState.currentRound} of ${sessionState.totalRounds}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val pConfig = sessionState.profile.pranayamaConfig
                if (pConfig?.isIntervalBellEnabled == true) {
                    val intervalCadence = pConfig.intervalBellRoundCadence.takeIf { it > 0 } ?: 5
                    val roundsUntilBell = intervalCadence - ((sessionState.currentRound - 1) % intervalCadence)
                    Text(
                        text = "• 🔔 Milestone in $roundsUntilBell",
                        style = MaterialTheme.typography.labelSmall,
                        color = themePrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Total Remaining: ${sessionState.formattedRemainingTime}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}

/**
 * Mindful Eating specific visualizer scaled for horizontal / landscape displays.
 *
 * @param sessionState Reactive timer state snapshot ([TimerSessionState]).
 * @param onOpenSettings Open settings callback.
 */
@Composable
private fun MindfulEatingLandscapeContent(
    sessionState: TimerSessionState,
    onOpenSettings: () -> Unit
) {
    val progress = sessionState.progressFraction
    val isRunning = sessionState.status == SessionStatus.RUNNING

    val intervalDuration = sessionState.profile.intervalDurationSeconds
    val biteProgress = if (intervalDuration > 0) {
        (1f - (sessionState.nextBellSeconds.toFloat() / intervalDuration.toFloat())).coerceIn(0f, 1f)
    } else 0f

    val animatedBiteProgress by animateFloatAsState(
        targetValue = biteProgress,
        animationSpec = tween(durationMillis = 800),
        label = "BiteProgressLandscape"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onOpenSettings() },
        contentAlignment = Alignment.Center
    ) {
        // Ambient candlelit radial aura
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isRunning) 0.15f else 0.05f),
                            Color.Transparent
                        ),
                        radius = 350f
                    )
                )
        )

        // Mealtime Circular Progress Ring
        CircularProgressRing(
            progress = progress,
            size = 210.dp,
            strokeWidth = 5.dp
        ) {
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 3.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2f

                    drawCircle(
                        color = surfaceVariantColor.copy(alpha = 0.35f),
                        radius = radius,
                        style = Stroke(width = stroke)
                    )

                    if (intervalDuration > 0) {
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedBiteProgress,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_ph_bowl),
                    contentDescription = "Mindful Eating",
                    tint = primaryColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
