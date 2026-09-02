package com.habitbell.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.data.model.TimerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    profile: TimerProfile,
    currentTheme: ThemeMode,
    isZenMode: Boolean,
    isPocketMode: Boolean,
    isDisplayMode: Boolean,
    isAutoDim: Boolean,
    bellVolume: Float,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onZenModeToggle: (Boolean) -> Unit,
    onPocketModeToggle: (Boolean) -> Unit,
    onDisplayModeToggle: (Boolean) -> Unit,
    onAutoDimToggle: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTestBell: () -> Unit,
    onUpdateTime: (totalSec: Int, intervalSec: Int) -> Unit,
    onOpenTVMode: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Settings • ${profile.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 1. Timer Adjustments (Total Time & Interval Time)
            item {
                SettingsSectionHeader(title = "Timer")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${profile.totalDurationSeconds / 60} minutes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallAdjustButton(text = "-5m") {
                            val newTotal = (profile.totalDurationSeconds - 300).coerceAtLeast(60)
                            onUpdateTime(newTotal, profile.intervalDurationSeconds)
                        }
                        SmallAdjustButton(text = "+5m") {
                            val newTotal = profile.totalDurationSeconds + 300
                            onUpdateTime(newTotal, profile.intervalDurationSeconds)
                        }
                    }
                }

                if (profile.intervalDurationSeconds > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Interval Bell",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${profile.intervalDurationSeconds} seconds",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallAdjustButton(text = "30s") {
                                onUpdateTime(profile.totalDurationSeconds, 30)
                            }
                            SmallAdjustButton(text = "60s") {
                                onUpdateTime(profile.totalDurationSeconds, 60)
                            }
                            SmallAdjustButton(text = "3m") {
                                onUpdateTime(profile.totalDurationSeconds, 180)
                            }
                        }
                    }
                }
            }

            // 2. Sound (Bell Volume & Test)
            item {
                SettingsSectionHeader(title = "Sound")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tibetan Bell Volume",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onTestBell) {
                        Text("Test Chime", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Slider(
                    value = bellVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // 3. Display & Battery Optimizations
            item {
                SettingsSectionHeader(title = "Display & Battery")
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleRow(
                    title = "Display Mode",
                    subtitle = "Keeps screen awake with countdown visible",
                    checked = isDisplayMode,
                    onCheckedChange = onDisplayModeToggle
                )
                SettingsToggleRow(
                    title = "Pocket Mode",
                    subtitle = "Black AMOLED blanking with proximity & haptics",
                    checked = isPocketMode,
                    onCheckedChange = onPocketModeToggle
                )
                SettingsToggleRow(
                    title = "Auto Dimming",
                    subtitle = "Reduces brightness after 15s of stillness",
                    checked = isAutoDim,
                    onCheckedChange = onAutoDimToggle
                )
            }

            // 4. Themes (AMOLED, Eye Comfort, Dark, Light)
            item {
                SettingsSectionHeader(title = "Theme")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = currentTheme == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onThemeSelected(mode) }
                        ) {
                            Text(
                                text = when (mode) {
                                    ThemeMode.AMOLED -> "AMOLED"
                                    ThemeMode.EYE_COMFORT -> "Eye Comfort"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.LIGHT -> "Light"
                                },
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

            // 5. Casting & Devices (TV Dashboard)
            item {
                SettingsSectionHeader(title = "Casting & Living Room")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTVMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TV Dashboard Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Big screen oversized display readable across room",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. Zen Mode (DND / Focus)
            item {
                SettingsSectionHeader(title = "Zen Mode")
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleRow(
                    title = "Do Not Disturb Focus",
                    subtitle = "Suppress notifications during active sessions",
                    checked = isZenMode,
                    onCheckedChange = onZenModeToggle
                )
            }

            // 7. About Habit Bell
            item {
                SettingsSectionHeader(title = "About Habit Bell")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Habit Bell v0.1 • Distraction-Free Wellness Operating System. AMOLED-optimized, SoundPool acoustic chimes, zero busy-wait battery conservation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SmallAdjustButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
