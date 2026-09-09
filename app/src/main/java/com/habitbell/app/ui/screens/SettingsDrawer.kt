package com.habitbell.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.R
import com.habitbell.app.audio.VoiceCueMode
import com.habitbell.app.data.SuryaDatabase
import com.habitbell.app.data.model.*
import com.habitbell.app.engine.BackgroundSoundType
import com.habitbell.app.engine.BellSoundStyle
import com.habitbell.app.health.HealthProviderType
import com.habitbell.app.sync.SuryaSyncManager
import com.habitbell.app.ui.AnimatedPoseView
import com.habitbell.app.ui.components.CastButton
import com.habitbell.app.ui.viewmodel.SettingsDrawerTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    onUpdatePranayama: (purak: Int, antar: Int, rechak: Int, bahya: Int, rounds: Int, intervalBellEnabled: Boolean, intervalCadence: Int, voiceEnabled: Boolean, voiceStyle: VoiceCueStyle, tribandhaVoiceEnabled: Boolean, voiceVolume: Float) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onTestVoiceCue: (VoiceCueStyle, Boolean, Float) -> Unit = { _, _, _ -> },
    onTestPranayamaIntervalBell: () -> Unit = {},
    hasActivityPermission: Boolean = true,
    onRequestActivityPermission: () -> Unit = {},
    activeTab: SettingsDrawerTab = SettingsDrawerTab.TIMER,
    onTabSelected: (SettingsDrawerTab) -> Unit = {},
    onToggleSunMoonTheme: () -> Unit = {},
    onOpenSuryaEditor: () -> Unit = {}
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
                            onUpdatePranayama = onUpdatePranayama,
                            onTestVoiceCue = onTestVoiceCue,
                            onTestPranayamaIntervalBell = onTestPranayamaIntervalBell,
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
                            onPreviewBgMusic = onPreviewBgMusic,
                            onOpenSuryaEditor = onOpenSuryaEditor
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
    onUpdatePranayama: (purak: Int, antar: Int, rechak: Int, bahya: Int, rounds: Int, intervalBellEnabled: Boolean, intervalCadence: Int, voiceEnabled: Boolean, voiceStyle: VoiceCueStyle, tribandhaVoiceEnabled: Boolean, voiceVolume: Float) -> Unit,
    onTestVoiceCue: (VoiceCueStyle, Boolean, Float) -> Unit,
    onTestPranayamaIntervalBell: () -> Unit,
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
    onPreviewBgMusic: (Boolean) -> Unit,
    onOpenSuryaEditor: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // -------------------------------------------------------------
        // 1. Target Session Goal (Dynamic: Steps vs. Pranayama vs. Time)
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
        } else if (profile.type == TimerType.MULTI_INTERVAL || profile.pranayamaConfig != null) {
            // Dedicated Classical Pranayama Breathwork Configuration
            PranayamaSettingsSheet(
                profile = profile,
                onUpdatePranayama = onUpdatePranayama,
                onTestVoiceCue = onTestVoiceCue,
                onTestPranayamaIntervalBell = onTestPranayamaIntervalBell,
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
            return
        } else if (profile.type == TimerType.COMPOUND || profile.compoundConfig != null || profile.id.contains("surya", ignoreCase = true) || profile.name.contains("Surya", ignoreCase = true)) {
            // Dedicated Classical Surya Namaskar Sequence & Preset Configuration
            SuryaSettingsSheet(
                profile = profile,
                onOpenFullscreenEditor = onOpenSuryaEditor,
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
            return
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

                Spacer(modifier = Modifier.height(10.dp))

                // Screen Mirroring (Miracast / Any TV / Projectors)
                val screenMirrorContext = LocalContext.current
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                screenMirrorContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS))
                            } catch (e: Exception) {
                                try {
                                    screenMirrorContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cast,
                            contentDescription = "Screen Mirroring",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screen Mirroring (Miracast / Any TV)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Mirror live phone screen (breathing lotus, poses & timer) to Miracast dongles, projectors & TVs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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


/**
 * # PranayamaPhaseInputFieldRow
 *
 * Interactive numerical input row for a single timed Pranayama breathwork phase portion.
 * Combines direct text entry with fine -1s, +1s, +4s stepper buttons and unit labels.
 *
 * @param phaseTitle User-facing title (e.g., "1. Purak (Inhale)").
 * @param sanskritSubtitle Sanskrit script and meditative meaning.
 * @param seconds Current duration in seconds.
 * @param minSeconds Minimum allowable duration.
 * @param maxSeconds Maximum allowable duration.
 * @param onSecondsChange Callback invoked when duration changes.
 */
@Composable
private fun PranayamaPhaseInputFieldRow(
    phaseTitle: String,
    sanskritSubtitle: String,
    seconds: Int,
    minSeconds: Int,
    maxSeconds: Int,
    onSecondsChange: (Int) -> Unit
) {
    var textValue by remember(seconds) { mutableStateOf(seconds.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = phaseTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sanskritSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallAdjustButton(text = "-1") {
                val next = (seconds - 1).coerceIn(minSeconds, maxSeconds)
                textValue = next.toString()
                onSecondsChange(next)
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(3)
                    textValue = filtered
                    val num = filtered.toIntOrNull()
                    if (num != null) {
                        onSecondsChange(num.coerceIn(minSeconds, maxSeconds))
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                suffix = { Text("s", style = MaterialTheme.typography.labelSmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(78.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            SmallAdjustButton(text = "+1") {
                val next = (seconds + 1).coerceIn(minSeconds, maxSeconds)
                textValue = next.toString()
                onSecondsChange(next)
            }

            SmallAdjustButton(text = "+4") {
                val next = (seconds + 4).coerceIn(minSeconds, maxSeconds)
                textValue = next.toString()
                onSecondsChange(next)
            }
        }
    }
}

/**
 * # PranayamaSettingsSheet
 *
 * Dedicated, complete configuration sheet for classical Hatha Yoga Pranayama breathwork.
 *
 * Grounded in ancient Hatha Yoga literature (*Hatha Yoga Pradipika* & *Gheranda Samhita*),
 * this view provides a clean, focused, deeply spiritual settings interface:
 * 1. **Proportional Ratio Stages Dropdown**: Select ratios (Sama Vritti 1:1:1:1, Madhya 1:2:2:1, Visama Vritti 1:4:2:4, etc.)
 *    populating base seconds into the four phase input fields.
 * 2. **Four Phase Setting Portions (Input Fields)**: Dedicated numeric entry fields and steppers:
 *    - 1. Purak (Inhale) = 4 sec default
 *    - 2. Kumbhak (Hold In) = 16 sec default
 *    - 3. Rechak (Exhale) = 8 sec default
 *    - 4. Kumbhak (Hold Out) = 16 sec default
 * 3. **Target Practice Rounds**: 12 rounds default (Classical Adhama standard, 8m 48s), with custom entry.
 * 4. **Gentle Lady Voice Guidance**: Option A (Traditional Sanskrit) default, Option B (Bilingual) switch,
 *    and live audition button with automatic background audio ducking.
 * 5. **Milestone Interval Bell**: Default OFF to preserve meditative absorption; when enabled, chimes a soft 432 Hz warm Tibetan bowl.
 * 6. **Session Completion Bell**: Deep resonant Temple Gong (130.8 Hz).
 * 7. **Subtle Background Music**: Ambient sound toggle, Aum drone / YouTube / Custom file, and subtle volume slider.
 */
@Composable
private fun PranayamaSettingsSheet(
    profile: TimerProfile,
    onUpdatePranayama: (purak: Int, antar: Int, rechak: Int, bahya: Int, rounds: Int, intervalBellEnabled: Boolean, intervalCadence: Int, voiceEnabled: Boolean, voiceStyle: VoiceCueStyle, tribandhaVoiceEnabled: Boolean, voiceVolume: Float) -> Unit,
    onTestVoiceCue: (VoiceCueStyle, Boolean, Float) -> Unit,
    onTestPranayamaIntervalBell: () -> Unit,
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
    val initialConfig = profile.pranayamaConfig ?: PranayamaConfig(
        steps = listOf(
            PranayamaStep(PranayamaPhase.INHALE, 4),
            PranayamaStep(PranayamaPhase.HOLD_IN, 16),
            PranayamaStep(PranayamaPhase.EXHALE, 8),
            PranayamaStep(PranayamaPhase.HOLD_OUT, 16)
        ),
        targetRounds = 12,
        isIntervalBellEnabled = false,
        intervalBellRoundCadence = 5,
        isVoiceGuidanceEnabled = true,
        voiceCueStyle = VoiceCueStyle.SANSKRIT,
        isTriBandhaVoiceEnabled = true,
        voiceVolume = 0.52f
    )

    var purak by remember(profile.id, initialConfig.purakSeconds) { mutableStateOf(initialConfig.purakSeconds) }
    var antar by remember(profile.id, initialConfig.antarKumbhakSeconds) { mutableStateOf(initialConfig.antarKumbhakSeconds) }
    var rechak by remember(profile.id, initialConfig.rechakSeconds) { mutableStateOf(initialConfig.rechakSeconds) }
    var bahya by remember(profile.id, initialConfig.bahyaKumbhakSeconds) { mutableStateOf(initialConfig.bahyaKumbhakSeconds) }
    var rounds by remember(profile.id, initialConfig.targetRounds) { mutableStateOf(initialConfig.targetRounds) }
    var intervalBellEnabled by remember(profile.id, initialConfig.isIntervalBellEnabled) { mutableStateOf(initialConfig.isIntervalBellEnabled) }
    var intervalCadence by remember(profile.id, initialConfig.intervalBellRoundCadence) { mutableStateOf(initialConfig.intervalBellRoundCadence) }
    var voiceEnabled by remember(profile.id, initialConfig.isVoiceGuidanceEnabled) { mutableStateOf(initialConfig.isVoiceGuidanceEnabled) }
    var voiceStyle by remember(profile.id, initialConfig.voiceCueStyle) { mutableStateOf(initialConfig.voiceCueStyle) }
    var tribandhaVoiceEnabled by remember(profile.id, initialConfig.isTriBandhaVoiceEnabled) { mutableStateOf(initialConfig.isTriBandhaVoiceEnabled) }
    var voiceVolume by remember(profile.id, initialConfig.voiceVolume) { mutableStateOf(initialConfig.voiceVolume) }

    fun dispatchUpdate(
        newPurak: Int = purak,
        newAntar: Int = antar,
        newRechak: Int = rechak,
        newBahya: Int = bahya,
        newRounds: Int = rounds,
        newIntervalBellEnabled: Boolean = intervalBellEnabled,
        newIntervalCadence: Int = intervalCadence,
        newVoiceEnabled: Boolean = voiceEnabled,
        newVoiceStyle: VoiceCueStyle = voiceStyle,
        newTribandhaVoiceEnabled: Boolean = tribandhaVoiceEnabled,
        newVoiceVolume: Float = voiceVolume
    ) {
        purak = newPurak
        antar = newAntar
        rechak = newRechak
        bahya = newBahya
        rounds = newRounds
        intervalBellEnabled = newIntervalBellEnabled
        intervalCadence = newIntervalCadence
        voiceEnabled = newVoiceEnabled
        voiceStyle = newVoiceStyle
        tribandhaVoiceEnabled = newTribandhaVoiceEnabled
        voiceVolume = newVoiceVolume
        onUpdatePranayama(newPurak, newAntar, newRechak, newBahya, newRounds, newIntervalBellEnabled, newIntervalCadence, newVoiceEnabled, newVoiceStyle, newTribandhaVoiceEnabled, newVoiceVolume)
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    val currentRatioStage = remember(purak, antar, rechak, bahya) {
        PranayamaRatioStage.matchRatio(purak, antar, rechak, bahya)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // -------------------------------------------------------------
        // Card 1: Proportional Ratio Stages (Dropdown to Select Ratios)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Proportional Ratio Stages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Classical practitioner progression when Bahya Kumbhaka (external void) is included:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Dropdown Trigger Box
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentRatioStage.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentRatioStage.ratioText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select Ratio Stage",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        PranayamaRatioStage.values().filter { it != PranayamaRatioStage.CUSTOM }.forEach { stage ->
                            val isSelected = currentRatioStage == stage
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = stage.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stage.ratioText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    val base = purak.coerceAtLeast(1)
                                    dispatchUpdate(
                                        newPurak = base * stage.purakRatio,
                                        newAntar = base * stage.antarRatio,
                                        newRechak = base * stage.rechakRatio,
                                        newBahya = base * stage.bahyaRatio
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Base Inhale Scaling Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scale Base Inhale",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 3, 4, 5, 6).forEach { bSec ->
                            val isSelected = purak == bSec && currentRatioStage != PranayamaRatioStage.CUSTOM
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable {
                                    if (currentRatioStage != PranayamaRatioStage.CUSTOM) {
                                        dispatchUpdate(
                                            newPurak = bSec * currentRatioStage.purakRatio,
                                            newAntar = bSec * currentRatioStage.antarRatio,
                                            newRechak = bSec * currentRatioStage.rechakRatio,
                                            newBahya = bSec * currentRatioStage.bahyaRatio
                                        )
                                    } else {
                                        dispatchUpdate(newPurak = bSec)
                                    }
                                }
                            ) {
                                Text(
                                    text = "${bSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Card 2: Four Phase Setting Portions (Input Fields with Custom Seconds)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Four Phase Portions (Custom Seconds)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 1. Purak (Inhale) - default 4s
                PranayamaPhaseInputFieldRow(
                    phaseTitle = "1. Purak (Inhale)",
                    sanskritSubtitle = "पूरक • Inhalation",
                    seconds = purak,
                    minSeconds = 1,
                    maxSeconds = 60,
                    onSecondsChange = { dispatchUpdate(newPurak = it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 2. Kumbhak (Hold In) - default 16s
                PranayamaPhaseInputFieldRow(
                    phaseTitle = "2. Kumbhak (Hold In)",
                    sanskritSubtitle = "अभ्यन्तर कुम्भक • Internal retention",
                    seconds = antar,
                    minSeconds = 0,
                    maxSeconds = 60,
                    onSecondsChange = { dispatchUpdate(newAntar = it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 3. Rechak (Exhale) - default 8s
                PranayamaPhaseInputFieldRow(
                    phaseTitle = "3. Rechak (Exhale)",
                    sanskritSubtitle = "रेचक • Exhalation",
                    seconds = rechak,
                    minSeconds = 1,
                    maxSeconds = 60,
                    onSecondsChange = { dispatchUpdate(newRechak = it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 4. Kumbhak (Hold Out) - default 16s
                PranayamaPhaseInputFieldRow(
                    phaseTitle = "4. Kumbhak (Hold Out)",
                    sanskritSubtitle = "बाह्य कुम्भक • External void retention",
                    seconds = bahya,
                    minSeconds = 0,
                    maxSeconds = 60,
                    onSecondsChange = { dispatchUpdate(newBahya = it) }
                )
            }
        }

        // -------------------------------------------------------------
        // Card 3: Target Practice Rounds (Yogic Literature Grounding)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target Practice Rounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val cycleSec = purak + antar + rechak + bahya
                        val totalSec = cycleSec * rounds
                        val totalMin = totalSec / 60
                        val totalRemSec = totalSec % 60
                        Text(
                            text = "$rounds rounds (~${totalMin}m ${totalRemSec}s)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallAdjustButton(text = "-1") {
                            dispatchUpdate(newRounds = (rounds - 1).coerceAtLeast(1))
                        }
                        SmallAdjustButton(text = "+1") {
                            dispatchUpdate(newRounds = (rounds + 1).coerceAtMost(108))
                        }
                        SmallAdjustButton(text = "+6") {
                            dispatchUpdate(newRounds = (rounds + 6).coerceAtMost(108))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Classical Yogic Stages (Hatha Yoga Pradipika 2.12 & Gheranda Samhita 5.49): 12 rounds is the Adhama standard, 24 rounds is Madhyama, and 36 rounds is Uttama. Adjust as your capacity blossoms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "6 (Intro)" to 6,
                        "12 (Adhama)" to 12,
                        "18" to 18,
                        "24 (Madhyama)" to 24,
                        "36 (Uttama)" to 36
                    ).forEach { (label, rCount) ->
                        val isSelected = rounds == rCount
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { dispatchUpdate(newRounds = rCount) }
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

        // -------------------------------------------------------------
        // Card 4: Gentle Lady Voice Guidance (Option A Default vs Option B)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gentle Lady Voice Guide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Meditative voice whispers cues at phase starts. Ambient music ducks automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { dispatchUpdate(newVoiceEnabled = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }

                if (voiceEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Voice Cue Selection", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            VoiceCueStyle.SANSKRIT to "Option 1: Only Sanskrit ('Purak', 'Kumbhak', 'Rechak') [Default]",
                            VoiceCueStyle.BILINGUAL to "Option 2: Sanskrit + English ('Purak... Inhale', 'Kumbhak... Hold')"
                        ).forEach { (style, description) ->
                            val isSelected = voiceStyle == style
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dispatchUpdate(newVoiceStyle = style) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "✓ Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Soothing Voice Volume Slider (Whisper-Soft to Gentle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Soothing Voice Volume",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val volPercent = (voiceVolume * 100).toInt()
                            val toneDescription = when {
                                voiceVolume <= 0.35f -> "Gentle Whisper"
                                voiceVolume <= 0.65f -> "Meditative Soft (Default)"
                                else -> "Clear Swara"
                            }
                            Text(
                                text = "$volPercent% • $toneDescription",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value = voiceVolume,
                        onValueChange = { dispatchUpdate(newVoiceVolume = it) },
                        valueRange = 0.15f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Tri-Bandha Voice Guidance Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tri-Bandha Voice Cue",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Whispers 'Kumbhak... Tri-Bandha' during breath retention",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tribandhaVoiceEnabled,
                            onCheckedChange = { dispatchUpdate(newTribandhaVoiceEnabled = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onTestVoiceCue(voiceStyle, tribandhaVoiceEnabled, voiceVolume) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val label = if (tribandhaVoiceEnabled) {
                            when (voiceStyle) {
                                VoiceCueStyle.BILINGUAL -> "▶ Audition Option 2 ('Kumbhak... Hold with Tri-Bandha')"
                                VoiceCueStyle.ENGLISH -> "▶ Audition English Voice ('Hold... Tri-Bandha')"
                                else -> "▶ Audition Option 1 ('Kumbhak... Tri-Bandha')"
                            }
                        } else {
                            when (voiceStyle) {
                                VoiceCueStyle.BILINGUAL -> "▶ Audition Option 2 Voice ('Purak... Inhale')"
                                VoiceCueStyle.ENGLISH -> "▶ Audition English Voice ('Inhale')"
                                else -> "▶ Audition Option 1 Voice ('Purak')"
                            }
                        }
                        Text(label)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Card 5: Meditative Milestone & Session Ending Bells
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Milestone Interval Bell", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Periodic bell chime to track completed cycles without opening eyes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = intervalBellEnabled,
                        onCheckedChange = { dispatchUpdate(newIntervalBellEnabled = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }

                if (intervalBellEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🔔 Calibrated to a soft 432 Hz warm Tibetan singing bowl with gradual attack to preserve meditative absorption without triggering the startle reflex.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Bell Cadence (Every N Rounds)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3, 5, 6, 10).forEach { cad ->
                            val isSelected = intervalCadence == cad
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { dispatchUpdate(newIntervalCadence = cad) }
                            ) {
                                Text(
                                    text = "Every $cad",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onTestPranayamaIntervalBell,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶ Audition Gentle 432Hz Meditative Chime")
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Default: OFF (Complete silence between rounds to honor deep Dhyana meditation).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "🔔 Session Ending Bell: Deep resonant Temple Gong strikes gracefully upon completing all rounds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // -------------------------------------------------------------
        // Card 6: Subtle Ambient Background Soundscape
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = "Background Ambient Sound",
                    subtitle = "Subtle continuous drone (ducks during voice instructions)",
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

                    // Subtle Ambient Sound Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtle Ambient Volume", style = MaterialTheme.typography.bodyMedium)
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
    }
}

/**
 * # SuryaSettingsSheet
 *
 * Dedicated configuration sheet for Surya Namaskar (Sun Salutation) compound sequences.
 * Provides fine-grained controls for speed presets (Slow, Moderate, Fast), target rounds,
 * individual posture timing, voice guidance modes, solar mantras, ambient soundscapes, and Wear OS companion synchronization.
 *
 * Architectural Role: Primary UI configuration sheet for Surya Namaskar routines embedded within [SettingsDrawer].
 * Concurrency: State updates and Room database interactions are dispatched asynchronously onto `Dispatchers.IO`.
 *
 * @param profile Active Surya Namaskar [TimerProfile].
 * @param onOpenFullscreenEditor Callback navigating to the full sequence editor screen ([AppScreen.SURYA_TIMER]).
 * @param onTestOptionC Callback triggering test playback of Option C separator chime.
 * @param onTestGong Callback triggering test playback of Tibetan singing bowl / Temple Gong completion chime.
 * @param onStartQuickDemo Callback initiating a fast 10-second audition sequence.
 * @param isBgMusicEnabled Flag indicating whether background ambient drone is enabled.
 * @param bgMusicType The active [BackgroundSoundType] selection.
 * @param bgMusicCustomName Display name for custom picked audio file, if any.
 * @param bgMusicYouTubeUrl Configured YouTube audio URL stream link.
 * @param bgMusicVolume Normalized ambient background volume in range 0.0f..1.0f.
 * @param onBgMusicToggle Callback invoked when background ambient sound toggle switches state.
 * @param onBgMusicTypeSelected Callback invoked when sound source type changes.
 * @param onPickCustomAudio Callback opening SAF file picker for user-provided soundscapes.
 * @param onBgMusicYouTubeUrlChange Callback updating YouTube stream URL.
 * @param onBgMusicVolumeChange Callback updating normalized ambient volume.
 * @param onPreviewBgMusic Callback toggling temporary audition preview of selected ambient sound.
 */
@Composable
private fun SuryaSettingsSheet(
    profile: TimerProfile,
    onOpenFullscreenEditor: () -> Unit,
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { com.habitbell.app.data.SuryaDatabase.getInstance(context) }
    val stepDao = remember { database.stepDao() }
    val syncManager = remember { com.habitbell.app.sync.SuryaSyncManager(context, database) }

    // Ensure database is populated with the 12 classical postures
    LaunchedEffect(Unit) {
        com.habitbell.app.data.SuryaDatabase.seedIfEmpty(context)
    }

    val steps by stepDao.getAllSteps().collectAsState(initial = emptyList())
    var selectedPreset by remember { mutableStateOf<String?>("moderate") }
    var customPaceSeconds by remember { mutableStateOf(7) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var targetRounds by remember(profile.id) { mutableStateOf(profile.compoundConfig?.targetRounds ?: 5) }

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
            if (selectedPreset == "custom") {
                customPaceSeconds = firstDur
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // -------------------------------------------------------------
        // Card 1: Speed Presets (4 Presets: Slow 10s, Moderate 5s, Fast 3s, Custom)
        // -------------------------------------------------------------
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
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Speed Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Quickly adjust pace across all 12 postures (or select Custom to edit):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

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
                        val dur = when (presetKey) { "slow" -> 10; "fast" -> 3; "moderate" -> 5; else -> customPaceSeconds }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPreset = presetKey
                                    if (presetKey != "custom") {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val all = stepDao.getAllSteps().first()
                                            all.forEach { stepDao.update(it.copy(durationSeconds = dur)) }
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = label,
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

                // If Custom preset is selected, show editable custom timing controls
                if (selectedPreset == "custom") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "CUSTOM ASANA PACE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (customPaceSeconds > 1) {
                                    customPaceSeconds--
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val all = stepDao.getAllSteps().first()
                                        all.forEach { stepDao.update(it.copy(durationSeconds = customPaceSeconds)) }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text("-1s") }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "${customPaceSeconds}s",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                customPaceSeconds++
                                coroutineScope.launch(Dispatchers.IO) {
                                    val all = stepDao.getAllSteps().first()
                                    all.forEach { stepDao.update(it.copy(durationSeconds = customPaceSeconds)) }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text("+1s") }

                        listOf(4, 7, 8, 12, 15).forEach { sec ->
                            val isChipSelected = customPaceSeconds == sec
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isChipSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        customPaceSeconds = sec
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val all = stepDao.getAllSteps().first()
                                            all.forEach { stepDao.update(it.copy(durationSeconds = sec)) }
                                        }
                                    }
                            ) {
                                Text(
                                    text = "${sec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isChipSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isChipSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Card 2: Target Practice Rounds (3, 5, 12, 24, 108 rounds)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Target Practice Rounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A complete round cycles through all 12 classical postures:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$targetRounds Rounds (${targetRounds * 12} Asanas)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (targetRounds > 1) targetRounds-- },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("-1") }

                        Button(
                            onClick = { targetRounds++ },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("+1") }

                        Button(
                            onClick = { targetRounds += 5 },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("+5") }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 5, 12, 24, 108).forEach { r ->
                        val isSelected = targetRounds == r
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { targetRounds = r }
                        ) {
                            Text(
                                text = "$r",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Card 3: 12 Classical Postures Sequence Summary & Full Editor
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedPoseView(
                        drawableResId = R.drawable.avd_yoga_pranamasana,
                        size = 48.dp,
                        contentDescription = "Pranamasana"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("12 Posture Sequence & Voice Guidance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Individual step durations, Sanskrit voice cues & solar mantras", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Guidance for All Steps (Global selection)
                val currentVoiceMode = steps.firstOrNull()?.voiceCueMode ?: VoiceCueMode.STEP_NAME
                Text(
                    text = "VOICE GUIDANCE (ALL STEPS)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        VoiceCueMode.STEP_NAME to "Asana Name",
                        VoiceCueMode.SLOKA to "Solar Mantra",
                        VoiceCueMode.PRANIC to "Breath Flow",
                        VoiceCueMode.NONE to "Silent / Bell"
                    ).forEach { (mode, label) ->
                        val isSelected = currentVoiceMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val all = stepDao.getAllSteps().first()
                                        all.forEach { stepDao.update(it.copy(voiceCueMode = mode)) }
                                    }
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "POSTURES SEQUENCE PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Postures Preview List
                val displaySteps = if (steps.isNotEmpty()) steps else com.habitbell.app.data.default.DefaultProfiles.SURYA_NAMASKAR.compoundConfig?.poses?.mapIndexed { idx, p ->
                    StepEntity(
                        id = (idx + 1).toLong(),
                        name = "${p.name} (${p.sanskritName})",
                        orderIdx = idx,
                        isEnabled = true,
                        voiceCueMode = currentVoiceMode,
                        audioCue = "surya_${idx + 1}",
                        mantraEnabled = true,
                        assetRef = if (idx == 0 || idx == 11) "avd_yoga_pranamasana" else "",
                        durationSeconds = p.durationSeconds,
                        repetition = 1
                    )
                } ?: emptyList()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displaySteps.take(4).forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${step.orderIdx + 1}. ${step.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${step.durationSeconds}s • ${step.voiceCueMode.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (displaySteps.size > 4) {
                        Text(
                            text = "+ ${displaySteps.size - 4} more classical postures in full sequence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenFullscreenEditor,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Open Posture & Voice Cue Editor", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // -------------------------------------------------------------
        // Card 4: Companion Watch Synchronization (Phone ↔ Watch)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Companion Watch Synchronization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Push current 12-pose sequence, timings, and speed presets to your Wear OS watch over Wearable Data Layer API (/surya_sync):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        syncManager.pushSyncToWatch()
                        syncStatusMessage = "Sync payload sent to watch successfully!"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync to Wear OS Watch")
                }

                syncStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "✓ $msg",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Card 5: Background Ambient Soundscape
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Ambient Sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Continuous soothing frequency while this timer runs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isBgMusicEnabled,
                        onCheckedChange = onBgMusicToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (isBgMusicEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("SOUND SOURCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sources = listOf(
                            BackgroundSoundType.DEFAULT_AUM to "ॐ Aum",
                            BackgroundSoundType.YOUTUBE_LINK to "YouTube Audio",
                            BackgroundSoundType.CUSTOM_FILE to "Custom File"
                        )
                        sources.forEach { (type, label) ->
                            val isSelected = bgMusicType == type
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onBgMusicTypeSelected(type) }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 10.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
        // Card 6: Signature Acoustic Bells Audition
        // -------------------------------------------------------------
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
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Signature Acoustic Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Separator bell strikes on pose transition; deep resonant Temple Gong completes session:",
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("▶ Separator", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = onTestGong,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("▶ End Gong", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onStartQuickDemo,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⏱ 10s Demo", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

