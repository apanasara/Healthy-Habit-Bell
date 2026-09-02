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
                    text = "Interval Chime Bell",
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
                            name = if (name.isNotBlank()) name else "Custom Session",
                            type = TimerType.LINEAR,
                            category = category,
                            iconName = "alarm",
                            totalDurationSeconds = totalMinutes * 60,
                            intervalDurationSeconds = intervalSeconds,
                            theme = selectedTheme,
                            displayMode = displayMode,
                            pocketMode = pocketMode,
                            isFavorite = true
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
