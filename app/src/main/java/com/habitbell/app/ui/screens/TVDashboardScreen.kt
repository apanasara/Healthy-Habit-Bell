package com.habitbell.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.engine.SessionStatus
import com.habitbell.app.engine.TimerSessionState

@Composable
fun TVDashboardScreen(
    sessionState: TimerSessionState,
    onTogglePlayPause: () -> Unit,
    onExitTVMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Living room TV pure black backdrop
            .padding(40.dp)
    ) {
        // Exit TV Mode Button
        IconButton(
            onClick = onExitTVMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .background(Color(0xFF1E1E1E), CircleShape)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Exit TV Mode",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Activity Title (e.g. "Eating")
            Text(
                text = sessionState.profile.name.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Giant Countdown (110sp - readable from 15+ feet away)
            Text(
                text = sessionState.formattedRemainingTime,
                fontSize = 110.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-2).sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Wide Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF222222))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sessionState.progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // "Next Bell in 00:20" or Pranayama / Compound sub-indicator
            if (sessionState.profile.intervalDurationSeconds > 0) {
                Text(
                    text = "Next Bell in ${sessionState.formattedNextBellTime}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (sessionState.currentPranayamaPhase != null) {
                Text(
                    text = "${sessionState.currentPranayamaPhase?.displayName} • Round ${sessionState.currentRound} / ${sessionState.totalRounds}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (sessionState.currentPose != null) {
                Text(
                    text = "${sessionState.currentPose?.name} (${sessionState.currentPose?.sanskritName}) • Round ${sessionState.currentRound} / ${sessionState.totalRounds}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Living Room TV Remote Control Play / Pause
            val isRunning = sessionState.status == SessionStatus.RUNNING
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(72.dp),
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
        }
    }
}
