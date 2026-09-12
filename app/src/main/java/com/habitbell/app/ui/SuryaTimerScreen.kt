/**
 * # SuryaTimerScreen
 *
 * Jetpack Compose screen providing fine-grained configuration, speed preset selection,
 * animated posture previews, auditioning voice cues, and Wear OS companion synchronization
 * for Surya Namaskar sequences.
 *
 * ## Architectural Role & Component Relationships
 * Presentation layer component in `com.habitbell.app.ui`:
 * - Connects to [com.habitbell.app.viewmodel.SuryaTimerViewModel] for Room database mutations and reactive StateFlow collection.
 * - Embeds [com.habitbell.app.ui.AnimatedPoseView] and [com.habitbell.app.ui.SuryaPoseAssets] for accurate monochrome vector rendering.
 * - Dispatches watch synchronization requests via [SuryaTimerViewModel.syncWithWatch].
 *
 * ## Concurrency & Thread Safety
 * All Composable functions execute strictly on the Main/UI thread within Compose recomposition cycles.
 *
 * ## Lifecycle
 * Hosted inside [com.habitbell.app.MainActivity] when `AppScreen.SURYA_TIMER` is active.
 */
package com.habitbell.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitbell.app.R
import com.habitbell.app.audio.VoiceCueMode
import com.habitbell.app.ui.model.StepUiModel
import com.habitbell.app.viewmodel.SuryaTimerViewModel

/**
 * Main Surya Namaskar sequence customizer and companion device synchronization screen.
 *
 * @param viewModel ViewModel orchestrating Room database and Wear OS synchronization.
 * @param modifier Composable layout modifier.
 * @param onBack Callback invoked when navigating back to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuryaTimerScreen(
    viewModel: SuryaTimerViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val steps by viewModel.steps.collectAsState()
    val currentVoiceCueMode by viewModel.currentVoiceCueMode.collectAsState()

    // Detect preset based on existing steps, default to moderate
    var selectedPreset by remember { mutableStateOf<String?>("moderate") }
    var customPaceSeconds by remember { mutableStateOf(7) }

    LaunchedEffect(steps) {
        if (steps.isNotEmpty() && selectedPreset != "custom") {
            val firstDur = steps.first().durationSeconds
            val allSame = steps.all { it.durationSeconds == firstDur }
            selectedPreset = when {
                allSame && firstDur == 10 -> "slow"
                allSame && firstDur == 5 -> "moderate"
                allSame && firstDur == 3 -> "fast"
                else -> "custom"
            }
        }
    }

    val isCustomMode = selectedPreset == "custom"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Surya Namaskar Sequence",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ph_back),
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { viewModel.syncWithWatch() },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text("Sync Watch", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // -------------------------------------------------------------
            // 1. Preset Speed Selector Header (4 Presets: Slow, Moderate, Fast, Custom)
            // -------------------------------------------------------------
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = "Speed Presets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "slow" to "Slow (10s)",
                            "moderate" to "Moderate (5s)",
                            "fast" to "Fast (3s)",
                            "custom" to "Custom"
                        ).forEach { (presetKey, label) ->
                            val isSelected = selectedPreset == presetKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedPreset = presetKey
                                        if (presetKey != "custom") {
                                            val dur = when (presetKey) {
                                                "slow" -> 10
                                                "fast" -> 3
                                                else -> 5
                                            }
                                            viewModel.applyPresetDuration(dur)
                                        }
                                    }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp, horizontal = 2.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }

                    // If Custom is selected, display editable timing controls
                    if (isCustomMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Custom Pace Timing",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Custom timing active. Adjust duration across all postures or edit individual asanas below:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (customPaceSeconds > 1) {
                                                customPaceSeconds--
                                                viewModel.applyPresetDuration(customPaceSeconds)
                                            }
                                        }
                                    ) {
                                        Text("-1s")
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "${customPaceSeconds}s / pose",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            customPaceSeconds++
                                            viewModel.applyPresetDuration(customPaceSeconds)
                                        }
                                    ) {
                                        Text("+1s")
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.applyPresetDuration(customPaceSeconds) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Apply to All")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. Voice Guidance for All Steps (with Audition Preview)
            // -------------------------------------------------------------
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice Guidance (All Steps)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Applied uniformly across all 12 postures:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (currentVoiceCueMode != VoiceCueMode.NONE) {
                                OutlinedButton(
                                    onClick = { viewModel.auditionVoiceCue(1) },
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_ph_play),
                                        contentDescription = "Audition Voice Cue",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Audition", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                VoiceCueMode.STEP_NAME to "Asana Name",
                                VoiceCueMode.SLOKA to "Solar Mantra",
                                VoiceCueMode.PRANIC to "Breath Flow",
                                VoiceCueMode.NONE to "Silent / Bell"
                            )
                            modes.forEach { (mode, label) ->
                                val isSelected = currentVoiceCueMode == mode
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.applyVoiceCueModeToAllSteps(mode) }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .padding(vertical = 10.dp, horizontal = 2.dp)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. Ordered Classical Postures Sequence Cards
            // -------------------------------------------------------------
            itemsIndexed(steps, key = { _, it -> it.id }) { index, step ->
                StepRow(
                    stepIndex = index + 1,
                    step = step,
                    isCustomMode = isCustomMode,
                    onStepUpdate = viewModel::updateStep,
                    onAudition = { viewModel.auditionVoiceCue(index + 1) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Individual posture configuration card displaying duration, distinct posture silhouette,
 * and unified voice guidance indicator.
 *
 * @param stepIndex 1-based index (1 to 12) of the posture in the cycle.
 * @param step Immutable UI representation of the Surya Namaskar posture.
 * @param isCustomMode Whether Custom speed preset is active allowing timing modification.
 * @param onStepUpdate Callback invoked when the user adjusts timing or enabled state.
 * @param onAudition Callback invoked to audition this posture's voice cue.
 */
@Composable
private fun StepRow(
    stepIndex: Int,
    step: StepUiModel,
    isCustomMode: Boolean,
    onStepUpdate: (StepUiModel) -> Unit,
    onAudition: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = step.isEnabled,
                    onCheckedChange = { onStepUpdate(step.copy(isEnabled = it)) }
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Step number badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$stepIndex",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Posture Name & Sanskrit subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Play / Audition Button
                if (step.voiceCueMode != VoiceCueMode.NONE) {
                    IconButton(
                        onClick = onAudition,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ph_play),
                            contentDescription = "Play Pose Voice Cue",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dedicated Animated Posture Vector Illustration
                AnimatedPoseView(
                    drawableResId = SuryaPoseAssets.getDrawableForStep(stepIndex),
                    modifier = Modifier.padding(start = 4.dp),
                    size = 46.dp,
                    contentDescription = step.name
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Duration (Editable in Custom Mode) and Global Voice Cue Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (step.durationSeconds > 0) step.durationSeconds.toString() else "",
                    onValueChange = { v ->
                        if (isCustomMode) {
                            val sec = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                            onStepUpdate(step.copy(durationSeconds = sec))
                        }
                    },
                    readOnly = !isCustomMode,
                    label = { Text(if (isCustomMode) "Duration (s)" else "Duration (Preset)") },
                    supportingText = if (!isCustomMode) {
                        { Text("Select 'Custom' preset to edit", style = MaterialTheme.typography.labelSmall) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Unified Voice Cue Indicator (Not editable per step; configured globally)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                        Text(
                            text = "Voice Guidance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = step.voiceCueMode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
