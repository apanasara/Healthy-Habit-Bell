package com.habitbell.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.data.model.TimerProfile
import com.habitbell.app.data.model.TimerType
import java.util.UUID

/**
 * Screen enabling users to configure and persist custom timer profiles with personalized
 * durations, interval chime periods, theme selections, and power modes.
 *
 * @param onSave Callback receiving the newly created [TimerProfile] to save and launch.
 * @param onCancel Callback to dismiss the creation screen and return to Home.
 * @param modifier Composable layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTimerScreen(
    onSave: (TimerProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Meditation") }
    var totalMinutes by remember { mutableStateOf(15) }
    var intervalSeconds by remember { mutableStateOf(60) }
    var selectedTheme by remember { mutableStateOf(ThemeMode.AMOLED) }
    var displayMode by remember { mutableStateOf(true) }
    var pocketMode by remember { mutableStateOf(false) }
    var isStepTracking by remember { mutableStateOf(false) }
    var stepGoal by remember { mutableStateOf<Int?>(2000) }
    var stepInterval by remember { mutableStateOf<Int?>(500) }
    var triggerMode by remember { mutableStateOf(com.habitbell.app.data.model.StepTriggerMode.TIME_OR_STEPS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Timer Profile") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Timer Name (e.g. Reiki, Walking)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            item {
                Text(
                    text = "Total Duration: $totalMinutes minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = totalMinutes.toFloat(),
                    onValueChange = { totalMinutes = it.toInt() },
                    valueRange = 1f..90f,
                    steps = 88,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            item {
                Text(
                    text = "Time Interval Chime Bell",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val intervals = listOf(
                        "None" to 0,
                        "30s" to 30,
                        "1m" to 60,
                        "3m" to 180,
                        "5m" to 300
                    )
                    intervals.forEach { (label, sec) ->
                        val isSelected = intervalSeconds == sec
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { intervalSeconds = sec }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Walking & Step Tracking", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                        Text("Count steps via Pedometer / Health Connect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isStepTracking,
                        onCheckedChange = {
                            isStepTracking = it
                            if (it) {
                                category = "Movement"
                                pocketMode = true
                            }
                        }
                    )
                }
            }

            if (isStepTracking) {
                item {
                    Text(
                        text = "Step Goal (Completion Bell)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val goals = listOf(
                            "None" to null,
                            "1k" to 1000,
                            "2k" to 2000,
                            "3k" to 3000,
                            "5k" to 5000
                        )
                        goals.forEach { (label, count) ->
                            val isSelected = stepGoal == count
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { stepGoal = count }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Step Interval Bell (Chime every N steps)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val stepIntervals = listOf(
                            "None" to null,
                            "250" to 250,
                            "500" to 500,
                            "1,000" to 1000
                        )
                        stepIntervals.forEach { (label, count) ->
                            val isSelected = stepInterval == count
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { stepInterval = count }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Session Completion Trigger",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            "Time or Steps" to com.habitbell.app.data.model.StepTriggerMode.TIME_OR_STEPS,
                            "Steps Only" to com.habitbell.app.data.model.StepTriggerMode.STEPS_ONLY,
                            "Time Only" to com.habitbell.app.data.model.StepTriggerMode.TIME_ONLY
                        )
                        modes.forEach { (label, mode) ->
                            val isSelected = triggerMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { triggerMode = mode }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = selectedTheme == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTheme = mode }
                        ) {
                            Text(
                                text = mode.name.replace("_", " "),
                                fontSize = 10.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Display Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                        Text("Keep screen awake during session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = displayMode, onCheckedChange = { displayMode = it })
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pocket Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                        Text("Blank screen with haptics & audio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = pocketMode, onCheckedChange = { pocketMode = it })
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val profile = TimerProfile(
                            id = "custom-${UUID.randomUUID().toString().take(8)}",
                            name = if (name.isNotBlank()) name else if (isStepTracking) "Mindful Step Walk" else "Custom Session",
                            type = TimerType.LINEAR,
                            category = if (isStepTracking && category == "Meditation") "Movement" else category,
                            iconName = if (isStepTracking) "directions_walk" else "alarm",
                            totalDurationSeconds = totalMinutes * 60,
                            intervalDurationSeconds = intervalSeconds,
                            theme = selectedTheme,
                            displayMode = displayMode,
                            pocketMode = pocketMode,
                            isFavorite = true,
                            stepGoal = if (isStepTracking) stepGoal else null,
                            stepInterval = if (isStepTracking) stepInterval else null,
                            stepTriggerMode = if (isStepTracking) triggerMode else com.habitbell.app.data.model.StepTriggerMode.TIME_ONLY
                        )
                        onSave(profile)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Start Profile", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
