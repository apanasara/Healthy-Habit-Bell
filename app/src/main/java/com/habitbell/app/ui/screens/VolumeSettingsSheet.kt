/**
 * # VolumeSettingsSheet
 *
 * Dedicated modal bottom sheet providing isolated, comprehensive audio and volume controls (Requirement E6).
 *
 * ## Architectural Role & Component Relationships
 * Presentation Layer Bottom Sheet in `com.habitbell.app.ui.screens`:
 * - Decouples audio configuration completely from the global settings tree, eliminating vertical scroll fatigue.
 * - Always accessible via the dedicated top-right speaker action icon across all application screens.
 * - Bridges directly to [com.habitbell.app.engine.AudioBellManager] for acoustic Tibetan and Option C chime levels.
 * - Connects to [com.habitbell.app.engine.BackgroundMusicManager] and [com.habitbell.app.audio.SystemVolumeObserver]
 *   for unified, hardware-synchronized ambient soundscape manipulation (Requirement E7).
 *
 * ## Concurrency & Thread Safety
 * Executed purely on Compose UI thread. Gain modifications and preview playback invocations
 * dispatch asynchronously through reactive callbacks to [com.habitbell.app.ui.viewmodel.HabitBellViewModel].
 *
 * ## Lifecycle
 * Hosted inside [com.habitbell.app.MainActivity] and presented on top of the active screen
 * when `uiState.isVolumeSheetOpen == true`.
 */
package com.habitbell.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.engine.BackgroundSoundType

/**
 * Renders the dedicated Volume & Audio Settings bottom sheet.
 *
 * Provides dedicated controls for:
 * 1. **Interval Bell Volume**: Independent gain slider and audition button for Option C 3-bell sequences.
 * 2. **Completion Bell (Gong) Volume**: Independent gain slider and audition button for concluding Temple Gongs.
 * 3. **Global Ambient Audio Selection**: Background soundscape master toggle, direct hardware output gain
 *    (Phone media stream vs. Google Cast TV master volume), sound strategy chips (ॐ Aum, YouTube, Custom file),
 *    and audio preview auditioning.
 *
 * @param intervalVolume Gain level for periodic interval chimes and countdown strikes, normalized `[0.0f, 1.0f]`.
 * @param bellVolume Gain level for session completion Temple Gong cues, normalized `[0.0f, 1.0f]`.
 * @param bgMusicVolume Gain level for background ambient soundscape or active hardware stream, normalized `[0.0f, 1.0f]`.
 * @param isBgMusicEnabled Master toggle switch for ambient background frequencies.
 * @param bgMusicType Selected ambient sound strategy ([BackgroundSoundType]).
 * @param bgMusicCustomName Human-readable filename of selected local audio file, or null if none.
 * @param bgMusicYouTubeUrl Configured YouTube stream URL for meditation background music.
 * @param isCasting Whether an active Google Cast session is currently transmitting to a TV.
 * @param castDeviceName Human-readable display label of target TV receiver.
 * @param onDismiss Callback invoked when the sheet is swiped down or dismissed.
 * @param onIntervalVolumeChange Callback when interval volume slider is adjusted, passing normalized `[0.0f, 1.0f]`.
 * @param onBellVolumeChange Callback when completion bell volume slider is adjusted, passing normalized `[0.0f, 1.0f]`.
 * @param onBgMusicVolumeChange Callback when ambient music volume slider is adjusted, passing normalized `[0.0f, 1.0f]`.
 * @param onBgMusicToggle Callback to enable or mute background ambient sound.
 * @param onBgMusicTypeSelected Callback when user changes ambient sound source strategy.
 * @param onPickCustomAudio Callback to launch system Storage Access Framework picker for audio files.
 * @param onBgMusicYouTubeUrlChange Callback when YouTube URL text field input changes.
 * @param onTestIntervalBell Callback to audition the signature Option C 3-bell sequence.
 * @param onTestCompletionBell Callback to audition the signature Temple Gong.
 * @param onPreviewBgMusic Callback to start or stop ambient sound stream preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSettingsSheet(
    intervalVolume: Float,
    bellVolume: Float,
    bgMusicVolume: Float,
    isBgMusicEnabled: Boolean,
    bgMusicType: BackgroundSoundType,
    bgMusicCustomName: String?,
    bgMusicYouTubeUrl: String,
    isCasting: Boolean,
    castDeviceName: String?,
    onDismiss: () -> Unit,
    onIntervalVolumeChange: (Float) -> Unit,
    onBellVolumeChange: (Float) -> Unit,
    onBgMusicVolumeChange: (Float) -> Unit,
    onBgMusicToggle: (Boolean) -> Unit,
    onBgMusicTypeSelected: (BackgroundSoundType) -> Unit,
    onPickCustomAudio: () -> Unit,
    onBgMusicYouTubeUrlChange: (String) -> Unit,
    onTestIntervalBell: () -> Unit,
    onTestCompletionBell: () -> Unit,
    onPreviewBgMusic: (Boolean) -> Unit
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
            // Header: Icon, Title & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Volume & Audio Controls",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Independent gain levels and soundscape settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close Volume Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // -------------------------------------------------------------
                // 1. Interval Bell Volume Controller & Test Button
                // -------------------------------------------------------------
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Interval Bell Volume",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Periodic cadence markers and countdown strikes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${(intervalVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Slider(
                                value = intervalVolume,
                                onValueChange = onIntervalVolumeChange,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = onTestIntervalBell,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "▶ Test Interval Bell",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. Completion Bell (Gong) Volume Controller & Test Button
                // -------------------------------------------------------------
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Completion Bell (Gong) Volume",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Deep resonant gong honoring session completion",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${(bellVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Slider(
                                value = bellVolume,
                                onValueChange = onBellVolumeChange,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = onTestCompletionBell,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "▶ Test Completion Gong",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // 3. Global Ambient Audio Options & Hardware Output (E7)
                // -------------------------------------------------------------
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBgMusicToggle(!isBgMusicEnabled) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Background Ambient Sound",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Continuous soothing frequency while timers run",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isBgMusicEnabled,
                                    onCheckedChange = onBgMusicToggle,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            if (isBgMusicEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(14.dp))

                                // Hardware / System Volume Slider (Direct Hardware Sync per E7)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isCasting) "TV Volume (${castDeviceName ?: "Google Cast"})" else "Phone Media Volume",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${(bgMusicVolume * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = if (isCasting) "Directly controlling connected TV master volume" else "Directly controlling mobile device media volume",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Slider(
                                    value = bgMusicVolume,
                                    onValueChange = onBgMusicVolumeChange,
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Ambient Sound Strategy Selector Chips
                                Text(
                                    text = "AMBIENT SOUND SOURCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                            modifier = Modifier
                                                .padding(vertical = 10.dp)
                                                .wrapContentWidth(Alignment.CenterHorizontally)
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
                                            modifier = Modifier
                                                .padding(vertical = 10.dp)
                                                .wrapContentWidth(Alignment.CenterHorizontally)
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
                                            modifier = Modifier
                                                .padding(vertical = 10.dp)
                                                .wrapContentWidth(Alignment.CenterHorizontally)
                                        )
                                    }
                                }

                                // YouTube URL input block
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
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "✓ Ad-free background audio streaming",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Custom File picker block
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
                                        OutlinedButton(
                                            onClick = onPickCustomAudio,
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Choose File", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Audition / Test Ambient Sound button
                                var isPreviewingAmbient by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isPreviewingAmbient = !isPreviewingAmbient
                                            onPreviewBgMusic(isPreviewingAmbient)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isPreviewingAmbient) "⏹ Stop Ambient Sound" else "▶ Test Ambient Sound",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
