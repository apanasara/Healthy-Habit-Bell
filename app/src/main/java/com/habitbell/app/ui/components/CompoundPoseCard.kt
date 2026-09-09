/**
 * # CompoundPoseCard
 *
 * Visual card displaying the current posture, round index, breathing cue, solar mantra,
 * and animated vector illustration for compound sequential timers (such as Surya Namaskar).
 *
 * ## Architectural Role & Component Relationships
 * Presentation layer UI component in `com.habitbell.app.ui.components`:
 * - Hosted by [com.habitbell.app.ui.screens.SessionScreen] when `TimerType.COMPOUND` is active.
 * - Embeds [com.habitbell.app.ui.AnimatedPoseView] to illustrate the active posture dynamically.
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
import com.habitbell.app.R
import com.habitbell.app.data.model.CompoundPose
import com.habitbell.app.ui.AnimatedPoseView

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

        Spacer(modifier = Modifier.height(10.dp))

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

        // Solar Mantra (e.g., "ॐ मित्राय नमः")
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

        // Animated Posture Illustration
        AnimatedPoseView(
            drawableResId = R.drawable.avd_yoga_pranamasana,
            size = 110.dp,
            contentDescription = pose.name
        )

        Spacer(modifier = Modifier.height(12.dp))

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
