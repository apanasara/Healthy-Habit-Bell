package com.habitbell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.CompoundPose

/**
 * Visual card displaying the current posture, round index, and breathing cue
 * for compound sequential timers (such as Surya Namaskar or Reiki hand positions).
 *
 * @param pose Active posture metadata ([CompoundPose]) including names, duration, and breath cue.
 * @param currentRound Current 1-based repetition round number.
 * @param totalRounds Total target rounds configured for the sequence.
 * @param remainingSeconds Seconds remaining in the active pose.
 * @param modifier Composable layout modifier.
 */
@Composable
fun CompoundPoseCard(
    pose: CompoundPose,
    currentRound: Int,
    totalRounds: Int,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Round and Pose Index Header
        Text(
            text = "ROUND $currentRound OF $totalRounds • POSE ${pose.index} / 12",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Posture Name (e.g., "Pranamasana", "Bhujangasana")
        Text(
            text = pose.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Translation / Meaning (e.g., "Prayer Pose", "Cobra Pose")
        Text(
            text = pose.sanskritName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Synchronized Breath Cue Badge
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "🌬 ${pose.breathCue}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
