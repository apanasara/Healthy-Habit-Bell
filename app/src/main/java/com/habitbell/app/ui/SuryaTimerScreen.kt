/**
 * # SuryaTimerScreen
 *
 * Jetpack Compose screen providing fine-grained configuration, speed preset selection,
 * animated posture previews, and Wear OS companion synchronization for Surya Namaskar sequences.
 *
 * ## Architectural Role & Component Relationships
 * Presentation layer component in `com.habitbell.app.ui`:
 * - Connects to [com.habitbell.app.viewmodel.SuryaTimerViewModel] for Room database mutations and reactive StateFlow collection.
 * - Embeds [com.habitbell.app.ui.AnimatedPoseView] for smooth AnimatedVectorDrawable rendering of classical poses.
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    var selectedPreset by remember { mutableStateOf<String?>("moderate") }

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
            // Preset Speed Selector Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "Speed Presets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedPreset == "slow",
                            onClick = {
                                selectedPreset = "slow"
                                viewModel.applyPresetDuration(10)
                            },
                            label = { Text("Slow (10s)") }
                        )
                        FilterChip(
                            selected = selectedPreset == "moderate",
                            onClick = {
                                selectedPreset = "moderate"
                                viewModel.applyPresetDuration(5)
                            },
                            label = { Text("Moderate (5s)") }
                        )
                        FilterChip(
                            selected = selectedPreset == "fast",
                            onClick = {
                                selectedPreset = "fast"
                                viewModel.applyPresetDuration(3)
                            },
                            label = { Text("Fast (3s)") }
                        )
                    }
                }
            }

            // Ordered Posture Cards
            items(steps, key = { it.id }) { step ->
                StepRow(
                    step = step,
                    onStepUpdate = viewModel::updateStep
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
 * Individual posture configuration card displaying duration, animated silhouette preview,
 * voice guidance style, and solar mantra toggle.
 *
 * @param step Immutable UI representation of the Surya Namaskar posture.
 * @param onStepUpdate Callback invoked when the user adjusts timing, toggle, or voice style.
 */
@Composable
private fun StepRow(
    step: StepUiModel,
    onStepUpdate: (StepUiModel) -> Unit
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

                // Animated Posture Vector Illustration
                AnimatedPoseView(
                    drawableResId = R.drawable.avd_yoga_pranamasana,
                    modifier = Modifier.padding(start = 8.dp),
                    size = 42.dp,
                    contentDescription = step.name
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Duration and Voice Cue Style Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (step.durationSeconds > 0) step.durationSeconds.toString() else "",
                    onValueChange = { v ->
                        val sec = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onStepUpdate(step.copy(durationSeconds = sec))
                    },
                    label = { Text("Duration (s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                VoiceCueDropdown(
                    selectedMode = step.voiceCueMode,
                    onModeSelected = { mode -> onStepUpdate(step.copy(voiceCueMode = mode)) },
                    modifier = Modifier.weight(1.4f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Solar Mantra Playback Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = step.mantraEnabled,
                    onCheckedChange = { onStepUpdate(step.copy(mantraEnabled = it)) }
                )
                Text(
                    text = "Solar Mantra Audio (ॐ मित्राय नमः...)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Dropdown menu for selecting voice cue mode for an individual posture.
 *
 * @param selectedMode Currently active [VoiceCueMode].
 * @param onModeSelected Callback dispatched when a new mode is picked.
 * @param modifier Composable layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceCueDropdown(
    selectedMode: VoiceCueMode,
    onModeSelected: (VoiceCueMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = VoiceCueMode.entries
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedMode.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Voice Cue") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.name) },
                    onClick = {
                        onModeSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
