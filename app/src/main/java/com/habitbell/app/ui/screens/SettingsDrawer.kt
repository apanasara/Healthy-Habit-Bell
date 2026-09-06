package com.habitbell.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.StepTriggerMode
import com.habitbell.app.data.model.ThemeMode
import com.habitbell.app.data.model.TimerProfile
import com.habitbell.app.engine.BackgroundSoundType
import com.habitbell.app.engine.BellSoundStyle
import com.habitbell.app.health.HealthProviderType
import com.habitbell.app.ui.components.CastButton
import com.habitbell.app.ui.viewmodel.SettingsDrawerTab

/**
 * # SettingsDrawer
 *
 * Modal bottom sheet configuration drawer providing unified, segregated controls for:
 * 1. **Dynamic Timer Settings**: Context-sensitive session target duration/steps, interval pacing,
 *    ambient soundscapes, and signature acoustic bell audition.
 * 2. **Global App Configuration**: System-wide Zen focus, blue-light-reduced Sun-Moon themes,
 *    master audio gain levels, pedometer/health platform bridges, and TV casting.
 *
 * ## Architectural Role & Relationships
 * - Presentation Layer Bottom Sheet invoked globally via [com.habitbell.app.MainActivity].
 * - Disaggregates timer-specific parameters from persistent system-wide hardware handles.
 * - Enforces the app's permanent signature sound identity (Option C triple bell + Temple Gong)
 *   without exposing confusing or brand-diluting sound timbre selectors.
 *
 * ## Concurrency & Thread Safety
 * Executed purely on Compose UI thread. State mutations are dispatched asynchronously
 * through reactive callbacks to the presentation ViewModel.
 *
 * @param profile Active [TimerProfile] currently targeted for session adjustments.
 * @param currentTheme Currently applied visual theme mode ([ThemeMode]).
 * @param isZenMode Whether minimalist Zen mode (DND) is active.
 * @param isPocketMode Whether proximity-based Pocket Mode blanking is toggled.
 * @param isDisplayMode Whether display is kept awake during active timer.
 * @param isAutoDim Whether display automatically dims during rest.
 * @param bellVolume Current gain level for Tibetan bell audio cues (0.0f..1.0f).
 * @param onDismiss Callback invoked when the sheet is swiped down or dismissed.
 * @param onThemeSelected Callback when the user selects a theme variant.
 * @param onZenModeToggle Callback to toggle Zen mode.
 * @param onPocketModeToggle Callback to toggle pocket mode.
 * @param onDisplayModeToggle Callback to toggle screen awake mode.
 * @param onAutoDimToggle Callback to toggle auto dimming.
 * @param onVolumeChange Callback when bell volume slider is adjusted.
 * @param onTestBell Callback to play a test chime and haptic pulse.
 * @param onUpdateTime Callback when total duration or interval sliders are modified.
 * @param onOpenTVMode Callback to launch TV Dashboard mode.
 * @param tvCastUrl Network URL of the embedded TV cast web server.
 * @param bellStyle Backwards-compatible bell sound timbre (enforced to Option C / Gong).
 * @param onBellStyleSelected Callback when bell style is selected.
 * @param onTestOptionC Callback to audition the signature Option C 3-bell sequence.
 * @param onTestGong Callback to audition the signature Temple Gong.
 * @param onStartQuickDemo Callback to run a 10s quick demo session.
 * @param isBgMusicEnabled Master toggle for ambient soundscapes.
 * @param bgMusicType Selected ambient sound strategy ([BackgroundSoundType]).
 * @param bgMusicCustomName Human-readable filename of selected local audio track.
 * @param bgMusicYouTubeUrl YouTube link for ambient background audio streaming.
 * @param bgMusicVolume Ambient background music gain level (0.0f..1.0f).
 * @param onBgMusicToggle Callback to toggle ambient music.
 * @param onBgMusicTypeSelected Callback to select sound strategy.
 * @param onPickCustomAudio Callback to launch system file picker for audio files.
 * @param onBgMusicYouTubeUrlChange Callback when YouTube URL input changes.
 * @param onBgMusicVolumeChange Callback when ambient music volume slider is adjusted.
 * @param onPreviewBgMusic Callback to audition or stop background ambient stream preview.
 * @param isCasting Whether an active Google Cast session is transmitting.
 * @param castDeviceName Human-readable display label of target TV.
 * @param onDisconnectCast Callback to disconnect the active Cast session.
 * @param selectedHealthProvider Currently active step provider bridge ([HealthProviderType]).
 * @param onHealthProviderSelected Callback when user changes health platform provider.
 * @param onTestStep Callback to inject synthetic steps for testing.
 * @param onUpdateSteps Callback when step goal or interval thresholds are adjusted.
 * @param hasActivityPermission Whether runtime sensor permission is granted.
 * @param onRequestActivityPermission Callback to trigger Android runtime permission request.
 * @param activeTab Currently displayed tab ([SettingsDrawerTab.TIMER] or [SettingsDrawerTab.GLOBAL]).
 * @param onTabSelected Callback invoked when user switches between Timer and Global tabs.
 * @param onToggleSunMoonTheme Callback to toggle between Sun Day and Moon Night eye-comfort modes.
 */
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
    onOpenTVMode: () -> Unit,
    tvCastUrl: String = "",
    bellStyle: BellSoundStyle = BellSoundStyle.ZEN_TINGSHA,
    onBellStyleSelected: (BellSoundStyle) -> Unit = {},
    onTestOptionC: () -> Unit = {},
    onTestGong: () -> Unit = {},
    onStartQuickDemo: () -> Unit = {},
    isBgMusicEnabled: Boolean = true,
    bgMusicType: BackgroundSoundType = BackgroundSoundType.DEFAULT_AUM,
    bgMusicCustomName: String? = null,
    bgMusicYouTubeUrl: String = "https://youtu.be/x6UITRjhijI",
    bgMusicVolume: Float = 0.35f,
    onBgMusicToggle: (Boolean) -> Unit = {},
    onBgMusicTypeSelected: (BackgroundSoundType) -> Unit = {},
    onPickCustomAudio: () -> Unit = {},
    onBgMusicYouTubeUrlChange: (String) -> Unit = {},
    onBgMusicVolumeChange: (Float) -> Unit = {},
    onPreviewBgMusic: (Boolean) -> Unit = {},
    isCasting: Boolean = false,
    castDeviceName: String? = null,
    onDisconnectCast: () -> Unit = {},
    selectedHealthProvider: HealthProviderType = HealthProviderType.HARDWARE_SENSOR,
    onHealthProviderSelected: (HealthProviderType) -> Unit = {},
    onTestStep: () -> Unit = {},
    onUpdateSteps: (goal: Int?, interval: Int?, mode: StepTriggerMode) -> Unit = { _, _, _ -> },
    hasActivityPermission: Boolean = true,
    onRequestActivityPermission: () -> Unit = {},
    activeTab: SettingsDrawerTab = SettingsDrawerTab.TIMER,
    onTabSelected: (SettingsDrawerTab) -> Unit = {},
    onToggleSunMoonTheme: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            var currentTab by remember(activeTab) { mutableStateOf(activeTab) }

            // Header with App Identity & Navigation Context
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (currentTab == SettingsDrawerTab.TIMER) "Timer • ${profile.name}" else "Global Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Quick Close Button
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary Domain Tab Row: [ ⏱ Timer Settings ] | [ ⚙️ Global Config ]
            TabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = currentTab == SettingsDrawerTab.TIMER,
                    onClick = {
                        currentTab = SettingsDrawerTab.TIMER
                        onTabSelected(SettingsDrawerTab.TIMER)
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Timer Settings", fontWeight = if (currentTab == SettingsDrawerTab.TIMER) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = currentTab == SettingsDrawerTab.GLOBAL,
                    onClick = {
                        currentTab = SettingsDrawerTab.GLOBAL
                        onTabSelected(SettingsDrawerTab.GLOBAL)
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Global Config", fontWeight = if (currentTab == SettingsDrawerTab.GLOBAL) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Content Pane based on Selected Tab
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (currentTab == SettingsDrawerTab.TIMER) {
                    // ==========================================
                    // DOMAIN 1: DYNAMIC TIMER-BASED SETTINGS
                    // ==========================================
                    item {
                        TimerSettingsContent(
                            profile = profile,
                            onUpdateTime = onUpdateTime,
                            onUpdateSteps = onUpdateSteps,
                            onTestOptionC = onTestOptionC,
                            onTestGong = onTestGong,
                            onStartQuickDemo = onStartQuickDemo,
                            isBgMusicEnabled = isBgMusicEnabled,
                            bgMusicType = bgMusicType,
                            bgMusicCustomName = bgMusicCustomName,
                            bgMusicYouTubeUrl = bgMusicYouTubeUrl,
                            bgMusicVolume = bgMusicVolume,
                            onBgMusicToggle = onBgMusicToggle,
                            onBgMusicTypeSelected = onBgMusicTypeSelected,
                            onPickCustomAudio = onPickCustomAudio,
                            onBgMusicYouTubeUrlChange = onBgMusicYouTubeUrlChange,
                            onBgMusicVolumeChange = onBgMusicVolumeChange,
                            onPreviewBgMusic = onPreviewBgMusic
                        )
                    }
                } else {
                    // ==========================================
                    // DOMAIN 2: GLOBAL APP CONFIGURATION
                    // ==========================================
                    item {
                        GlobalConfigContent(
                            currentTheme = currentTheme,
                            isZenMode = isZenMode,
                            isPocketMode = isPocketMode,
                            isDisplayMode = isDisplayMode,
                            isAutoDim = isAutoDim,
                            bellVolume = bellVolume,
                            bgMusicVolume = bgMusicVolume,
                            onThemeSelected = onThemeSelected,
                            onZenModeToggle = onZenModeToggle,
                            onPocketModeToggle = onPocketModeToggle,
                            onDisplayModeToggle = onDisplayModeToggle,
                            onAutoDimToggle = onAutoDimToggle,
                            onVolumeChange = onVolumeChange,
                            onBgMusicVolumeChange = onBgMusicVolumeChange,
                            onToggleSunMoonTheme = onToggleSunMoonTheme,
                            selectedHealthProvider = selectedHealthProvider,
                            onHealthProviderSelected = onHealthProviderSelected,
                            hasActivityPermission = hasActivityPermission,
                            onRequestActivityPermission = onRequestActivityPermission,
                            onTestStep = onTestStep,
                            isCasting = isCasting,
                            castDeviceName = castDeviceName,
                            onDisconnectCast = onDisconnectCast,
                            onOpenTVMode = onOpenTVMode,
                            tvCastUrl = tvCastUrl
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the context-sensitive Timer Settings tab content.
 * Dynamically switches between Step Goals vs. Duration Countdown depending on profile typology.
 */
@Composable
private fun TimerSettingsContent(
    profile: TimerProfile,
    onUpdateTime: (totalSec: Int, intervalSec: Int) -> Unit,
    onUpdateSteps: (goal: Int?, interval: Int?, mode: StepTriggerMode) -> Unit,
    onTestOptionC: () -> Unit,
    onTestGong: () -> Unit,
    onStartQuickDemo: () -> Unit,
    isBgMusicEnabled: Boolean,
    bgMusicType: BackgroundSoundType,
    bgMusicCustomName: String?,
    bgMusicYouTubeUrl: String,
    bgMusicVolume: Float,
    onBgMusicToggle: (Boolean) -> Unit,
    onBgMusicTypeSelected: (BackgroundSoundType) -> Unit,
    onPickCustomAudio: () -> Unit,
    onBgMusicYouTubeUrlChange: (String) -> Unit,
    onBgMusicVolumeChange: (Float) -> Unit,
    onPreviewBgMusic: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // -------------------------------------------------------------
        // 1. Target Session Goal (Dynamic: Steps vs. Time Countdown)
        // -------------------------------------------------------------
        if (profile.isStepTrackingEnabled) {
            // Walking & Movement Step Target Configuration
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Session Step Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Session ends with Temple Gong when step target is reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val stepGoals = listOf("None" to null, "1,000" to 1000, "2,000" to 2000, "3,000" to 3000, "5,000" to 5000)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        stepGoals.forEach { (label, count) ->
                            val isSelected = profile.stepGoal == count
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateSteps(count, profile.stepInterval, profile.stepTriggerMode)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }

            // Step Cadence Interval Cue
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Step Interval Chime (Cadence)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Plays signature triple bell separator every N steps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val intervals = listOf("None" to null, "250" to 250, "500" to 500, "1,000" to 1000)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        intervals.forEach { (label, count) ->
                            val isSelected = profile.stepInterval == count
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateSteps(profile.stepGoal, count, profile.stepTriggerMode)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Standard Linear Countdown Timing
            var totalMinutes by remember(profile.id, profile.totalDurationSeconds) {
                mutableStateOf((profile.totalDurationSeconds / 60).coerceAtLeast(1))
            }
            var intervalSec by remember(profile.id, profile.intervalDurationSeconds) {
                mutableStateOf(profile.intervalDurationSeconds)
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Session Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$totalMinutes minutes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallAdjustButton(text = "-1m") {
                                totalMinutes = (totalMinutes - 1).coerceAtLeast(1)
                                onUpdateTime(totalMinutes * 60, intervalSec)
                            }
                            SmallAdjustButton(text = "+1m") {
                                totalMinutes = (totalMinutes + 1).coerceAtMost(120)
                                onUpdateTime(totalMinutes * 60, intervalSec)
                            }
                            SmallAdjustButton(text = "+5m") {
                                totalMinutes = (totalMinutes + 5).coerceAtMost(120)
                                onUpdateTime(totalMinutes * 60, intervalSec)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = totalMinutes.toFloat(),
                        onValueChange = {
                            totalMinutes = it.toInt().coerceIn(1, 120)
                            onUpdateTime(totalMinutes * 60, intervalSec)
                        },
                        valueRange = 1f..60f,
                        steps = 58,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 15, 20, 25, 30, 45).forEach { m ->
                            val isSelected = totalMinutes == m
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        totalMinutes = m
                                        onUpdateTime(m * 60, intervalSec)
                                    }
                            ) {
                                Text(
                                    text = "${m}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Periodic Interval Chime
                    Text("Periodic Interval Separator", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (intervalSec > 0) {
                            if (intervalSec >= 60) "${intervalSec / 60}m (${intervalSec}s)" else "${intervalSec}s pacing"
                        } else "None (Bell only at end)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "None" to 0,
                            "15s" to 15,
                            "30s" to 30,
                            "1m" to 60,
                            "2m" to 120,
                            "3m" to 180
                        ).forEach { (label, sec) ->
                            val isSelected = intervalSec == sec
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        intervalSec = sec
                                        onUpdateTime(totalMinutes * 60, sec)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Ambient Background Soundscape (Per-Timer Routine Choice)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = "Background Ambient Sound",
                    subtitle = "Continuous soothing frequency while this timer runs",
                    checked = isBgMusicEnabled,
                    onCheckedChange = onBgMusicToggle
                )

                if (isBgMusicEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SOUND SOURCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isAumSelected = bgMusicType == BackgroundSoundType.DEFAULT_AUM
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAumSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onBgMusicTypeSelected(BackgroundSoundType.DEFAULT_AUM) }
                        ) {
                            Text(
                                text = "ॐ Aum",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isAumSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAumSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }

                        val isYtSelected = bgMusicType == BackgroundSoundType.YOUTUBE_LINK
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isYtSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { onBgMusicTypeSelected(BackgroundSoundType.YOUTUBE_LINK) }
                        ) {
                            Text(
                                text = "YouTube Audio",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isYtSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isYtSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }

                        val isCustomSelected = bgMusicType == BackgroundSoundType.CUSTOM_FILE
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onBgMusicTypeSelected(BackgroundSoundType.CUSTOM_FILE) }
                        ) {
                            Text(
                                text = "Custom File",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCustomSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    if (bgMusicType == BackgroundSoundType.YOUTUBE_LINK) {
                        Spacer(modifier = Modifier.height(10.dp))
                        var ytInput by remember(bgMusicYouTubeUrl) { mutableStateOf(bgMusicYouTubeUrl) }
                        OutlinedTextField(
                            value = ytInput,
                            onValueChange = {
                                ytInput = it
                                onBgMusicYouTubeUrlChange(it)
                            },
                            label = { Text("YouTube URL (Audio Stream)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ Ad-free background playback",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            var isTestingYt by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = {
                                    isTestingYt = !isTestingYt
                                    onPreviewBgMusic(isTestingYt)
                                }
                            ) {
                                Text(
                                    if (isTestingYt) "⏹ Stop" else "▶ Test Stream",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    if (bgMusicType == BackgroundSoundType.CUSTOM_FILE) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bgMusicCustomName ?: "No custom file chosen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onPickCustomAudio,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Choose File", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ambient Sound Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ambient Volume", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${(bgMusicVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = bgMusicVolume,
                        onValueChange = onBgMusicVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 3. Signature Bell Audition (Fixed App Sound Identity)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Signature Acoustic Identity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Habit Bell's unchangeable signature acoustics:\n• Separator Bell: Option C (3 bells: 2048Hz ➔ 1536Hz ➔ 1024Hz)\n• Session End: Deep resonant Temple Gong (130.8Hz)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTestOptionC,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text("▶ Separator Bell", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onTestGong,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("▶ End Gong", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onStartQuickDemo,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⏱ 10s Demo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Renders the persistent Global App Configuration tab content.
 * Houses Zen mode, Sun-Moon eye-comfort themes, master audio gain (bells and ambient music),
 * pedometer connectivity, TV casting, and battery blanking.
 *
 * @param currentTheme Active theme profile ([ThemeMode]).
 * @param isZenMode Whether Zen focus mode (DND) is active.
 * @param isPocketMode Whether proximity battery-blanking is active.
 * @param isDisplayMode Whether keep-screen-on wake-lock is engaged.
 * @param isAutoDim Whether auto-dimming during countdown is enabled.
 * @param bellVolume Master bell gain level (0.0f..1.0f).
 * @param bgMusicVolume Ambient background music gain level (0.0f..1.0f).
 * @param onThemeSelected Callback when theme profile is picked.
 * @param onZenModeToggle Callback to toggle DND.
 * @param onPocketModeToggle Callback to toggle pocket mode.
 * @param onDisplayModeToggle Callback to toggle display wake lock.
 * @param onAutoDimToggle Callback to toggle auto dim.
 * @param onVolumeChange Callback when bell master volume is adjusted (0.0f..1.0f).
 * @param onBgMusicVolumeChange Callback when ambient music volume slider is adjusted (0.0f..1.0f).
 * @param onToggleSunMoonTheme Callback to toggle between Sun Day and Moon Night eye-comfort modes.
 * @param selectedHealthProvider Currently active step provider bridge.
 * @param onHealthProviderSelected Callback when provider changes.
 * @param hasActivityPermission Whether runtime sensor permission is granted.
 * @param onRequestActivityPermission Callback to request permission.
 * @param onTestStep Callback to inject synthetic test steps.
 * @param isCasting Whether active TV casting is underway.
 * @param castDeviceName Target Cast receiver device name.
 * @param onDisconnectCast Callback to disconnect Cast session.
 * @param onOpenTVMode Callback to navigate to TV dashboard screen.
 * @param tvCastUrl Local HTTP playback URL for Smart TVs.
 */
@Composable
private fun GlobalConfigContent(
    currentTheme: ThemeMode,
    isZenMode: Boolean,
    isPocketMode: Boolean,
    isDisplayMode: Boolean,
    isAutoDim: Boolean,
    bellVolume: Float,
    bgMusicVolume: Float,
    onThemeSelected: (ThemeMode) -> Unit,
    onZenModeToggle: (Boolean) -> Unit,
    onPocketModeToggle: (Boolean) -> Unit,
    onDisplayModeToggle: (Boolean) -> Unit,
    onAutoDimToggle: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBgMusicVolumeChange: (Float) -> Unit,
    onToggleSunMoonTheme: () -> Unit,
    selectedHealthProvider: HealthProviderType,
    onHealthProviderSelected: (HealthProviderType) -> Unit,
    hasActivityPermission: Boolean,
    onRequestActivityPermission: () -> Unit,
    onTestStep: () -> Unit,
    isCasting: Boolean,
    castDeviceName: String?,
    onDisconnectCast: () -> Unit,
    onOpenTVMode: () -> Unit,
    tvCastUrl: String
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // -------------------------------------------------------------
        // 1. Zen Mode (DND Focus)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSectionHeader(title = "Zen Focus")
                Spacer(modifier = Modifier.height(6.dp))
                SettingsToggleRow(
                    title = "Do Not Disturb Focus",
                    subtitle = "Suppress distracting system notifications during active sessions",
                    checked = isZenMode,
                    onCheckedChange = onZenModeToggle
                )
            }
        }

        // -------------------------------------------------------------
        // 2. Sun-Moon Circadian Mode & Themes (Blue-Light Reduced)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        SettingsSectionHeader(title = "Sun-Moon & Themes")
                        Text(
                            text = "Blue-light reduced in both Day & Night modes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 1-Tap Sun / Moon Circadian Switcher Button
                    FilledTonalButton(
                        onClick = onToggleSunMoonTheme,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val icon = if (currentTheme.isSunDayTheme) Icons.Default.WbSunny else Icons.Default.Nightlight
                        val label = if (currentTheme.isSunDayTheme) "☀️ Day" else "🌙 Night"
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Theme Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = currentTheme == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onThemeSelected(mode) }
                        ) {
                            Text(
                                text = when (mode) {
                                    ThemeMode.AMOLED -> "AMOLED"
                                    ThemeMode.EYE_COMFORT -> "Eye Comfort"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.LIGHT -> "Light (Day)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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

        // -------------------------------------------------------------
        // 3. Master Audio Gain Levels
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSectionHeader(title = "Master Audio Gain")
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Bell Master Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bell Master Volume", style = MaterialTheme.typography.bodyMedium)
                    Text("${(bellVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Background / Ambient Music Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Background Ambient Volume", style = MaterialTheme.typography.bodyMedium)
                    Text("${(bgMusicVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = bgMusicVolume,
                    onValueChange = onBgMusicVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // -------------------------------------------------------------
        // 4. Pedometer & Health Platform Connectivity
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSectionHeader(title = "Pedometer & Health Connectivity")
                Spacer(modifier = Modifier.height(8.dp))

                Text("Active Step Provider", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HealthProviderType.values().forEach { provider ->
                        val isSelected = selectedHealthProvider == provider
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHealthProviderSelected(provider) }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
                                    )
                                    if (isSelected) {
                                        Text("Active", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Activity Permission status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sensor Permission", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (hasActivityPermission) "Granted • Sub-second hardware tracking" else "Required for device pedometer",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasActivityPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (!hasActivityPermission) {
                        Button(
                            onClick = onRequestActivityPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Test Step Button
                Button(
                    onClick = onTestStep,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Step Cadence (+250 steps)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // -------------------------------------------------------------
        // 5. Living Room & TV Casting
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSectionHeader(title = "Casting & Living Room")
                Spacer(modifier = Modifier.height(8.dp))

                // Google Cast Device
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CastButton(modifier = Modifier.size(36.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Google Cast • TV Streaming", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isCasting) "Connected: ${castDeviceName ?: "Living Room TV"}" else "Tap icon to stream to Chromecast or Google TV",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCasting) {
                        OutlinedButton(
                            onClick = onDisconnectCast,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TV Dashboard mode on phone
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTVMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TV Dashboard Mode (On Phone)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Oversized 110pt display readable across living room", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (tvCastUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart TV Browser Link", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(tvCastUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("TV URL", tvCastUrl))
                                }
                            ) {
                                Text("Copy Link", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 6. Hardware Battery Modes
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSectionHeader(title = "Display & Hardware Conservation")
                Spacer(modifier = Modifier.height(6.dp))
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
        }

        // -------------------------------------------------------------
        // 7. About Habit Bell
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSectionHeader(title = "About Habit Bell")
                Spacer(modifier = Modifier.height(4.dp))
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

/**
 * Reusable section header text with standardized styling.
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Reusable toggle switch row with title and subtitle.
 */
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
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * Reusable compact stepper button for fine adjustment.
 */
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
