/**
 * # CompoundPoseCard
 *
 * Visual card displaying the current posture, round index, breathing cue, solar mantra,
 * 12-step cyclical flow indicator, and vector illustration for compound sequential timers.
 *
 * ## Architectural Role & Component Relationships
 * Presentation layer UI component in `com.habitbell.app.ui.components`:
 * - Hosted by [com.habitbell.app.ui.screens.SessionScreen] when `TimerType.COMPOUND` is active.
 * - Embeds [com.habitbell.app.ui.AnimatedPoseView] and [com.habitbell.app.ui.SuryaPoseAssets]
 *   to illustrate the active posture dynamically with its distinct silhouette.
 * - Displays live pose countdown and round progress derived from [com.habitbell.app.engine.TimerSessionState].
 *
 * ## Concurrency & Thread Safety
 * Strictly executed on the Main (UI) thread within Jetpack Compose recomposition cycles.
 */
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
import com.habitbell.app.ui.AnimatedPoseView
import com.habitbell.app.ui.SuryaPoseAssets

/**
 * Visual card displaying active posture illustration, round progress, countdown, and yogic cues.
 *
 * @param pose Active posture metadata ([CompoundPose]) including names, duration, mantra, and breath cue.
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
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Round and Pose Index Header
        Text(
            text = "ROUND $currentRound OF $totalRounds • POSE ${pose.index} / 12",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        // 12-Step Cyclical Flow Progress Indicator
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..12).forEach { stepIdx ->
                val isCurrent = stepIdx == pose.index
                val isCompleted = stepIdx < pose.index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.5.dp)
                        .size(
                            width = if (isCurrent) 20.dp else 6.dp,
                            height = 6.dp
                        )
                        .background(
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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

        // Solar Mantra (e.g., "☀️ ॐ मित्राय नमः")
        if (pose.mantra.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "☀️ ${pose.mantra}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Distinct Animated Posture Silhouette Illustration for active pose
        AnimatedPoseView(
            drawableResId = SuryaPoseAssets.getDrawableForStep(pose.index),
            size = 120.dp,
            contentDescription = pose.name
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Synchronized Breath Cue & Pose Countdown Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🌬 ${pose.breathCue}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${remainingSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
