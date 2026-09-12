/**
 * # CastMirroringSheet
 *
 * Dedicated modal bottom sheet providing isolated, comprehensive controls for Living Room TV Streaming,
 * Screen Mirroring (Miracast), and TV Screen Orientation (Requirement E8).
 *
 * ## Architectural Role & Component Relationships
 * Presentation Layer Bottom Sheet in `com.habitbell.app.ui.screens`:
 * - Decouples Google Cast and Miracast TV workflows completely from global settings.
 * - Directly accessible from the top-right Cast/TV icon on Home and Session screens.
 * - Integrates [com.habitbell.app.ui.components.CastButton] for native Google Cast framework discovery.
 * - Coordinates with [com.habitbell.app.cast.ScreenMirroringManager] for orientation switching and display keep-awake.
 * - Exposes embedded HTTP web server URLs for Smart TVs with built-in browsers (Samsung Tizen / LG webOS).
 *
 * ## Concurrency & Thread Safety
 * Executed purely on Compose UI thread. State changes and orientation mutations dispatch
 * asynchronously through reactive callbacks to [com.habitbell.app.ui.viewmodel.HabitBellViewModel].
 *
 * ## Lifecycle
 * Hosted inside [com.habitbell.app.MainActivity] and presented on top of the active screen
 * when `uiState.isCastSheetOpen == true`.
 */
package com.habitbell.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.cast.ScreenOrientation
import com.habitbell.app.ui.components.CastButton

/**
 * Renders the dedicated TV Casting & Screen Mirroring bottom sheet (Requirement E8).
 *
 * @param isCasting Whether an active Google Cast session is currently transmitting.
 * @param castDeviceName Human-readable display label of target TV.
 * @param isScreenMirroringActive Whether external display / Miracast connection is detected.
 * @param isScreenMirroringManual Whether user manually forced Screen Mirroring mode on.
 * @param screenMirroringTargetOrientation Currently configured TV screen orientation ([ScreenOrientation]).
 * @param externalDisplayName Display name of connected secondary screen, if detected.
 * @param tvCastUrl Local HTTP playback URL for Smart TVs with web browsers.
 * @param onDismiss Callback invoked when the sheet is swiped down or dismissed.
 * @param onDisconnectCast Callback to cleanly terminate the active Google Cast connection.
 * @param onToggleScreenMirroringMode Callback to toggle forced screen mirroring keep-awake and controls.
 * @param onSetScreenOrientation Callback when a specific orientation is chosen ([ScreenOrientation.PORTRAIT], [ScreenOrientation.LANDSCAPE], [ScreenOrientation.AUTO]).
 * @param onToggleScreenOrientation Callback to toggle between horizontal landscape and vertical portrait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastMirroringSheet(
    isCasting: Boolean,
    castDeviceName: String?,
    isScreenMirroringActive: Boolean,
    isScreenMirroringManual: Boolean,
    screenMirroringTargetOrientation: ScreenOrientation,
    externalDisplayName: String?,
    tvCastUrl: String,
    onDismiss: () -> Unit,
    onDisconnectCast: () -> Unit,
    onToggleScreenMirroringMode: (Boolean) -> Unit,
    onSetScreenOrientation: (ScreenOrientation) -> Unit,
    onToggleScreenOrientation: () -> Unit
) {
    val context = LocalContext.current

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
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Living Room & TV Casting",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Chromecast, Miracast screen mirroring & TV orientation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close TV Casting Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // -------------------------------------------------------------
                // 1. Google Cast Subsystem
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CastButton(modifier = Modifier.size(36.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Google Cast • TV Streaming",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCasting) "Connected: ${castDeviceName ?: "Living Room TV"}" else "Tap icon to stream to Chromecast or Google TV",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCasting) {
                                    OutlinedButton(
                                        onClick = onDisconnectCast,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. Screen Mirroring (Miracast / Any TV) & System Settings Shortcut
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
                            // Mirroring Mode Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Screen Mirroring Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isScreenMirroringActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (externalDisplayName != null) "● $externalDisplayName" else "● Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Keep screen awake and enable TV orientation controls when casting via Quick Settings or Smart View",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isScreenMirroringManual || isScreenMirroringActive,
                                    onCheckedChange = onToggleScreenMirroringMode,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Deep Link to Android Cast Settings
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                                        } catch (e: Exception) {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
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
                                        contentDescription = "Cast Settings",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Open System Cast Settings (Miracast)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Connect to Miracast TVs, wireless dongles & projectors",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
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
                        }
                    }
                }

                // -------------------------------------------------------------
                // 3. TV Screen Orientation Controls
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
                            Text(
                                text = "TV Screen Orientation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Rotate screen layout for horizontal TVs or vertical wall displays",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val orientations = listOf(
                                    ScreenOrientation.PORTRAIT to "Vertical",
                                    ScreenOrientation.LANDSCAPE to "Horizontal",
                                    ScreenOrientation.AUTO to "Auto"
                                )
                                orientations.forEach { (orientation, label) ->
                                    val isSelected = screenMirroringTargetOrientation == orientation
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSetScreenOrientation(orientation) }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = onToggleScreenOrientation,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ScreenRotation,
                                    contentDescription = "Rotate Screen",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rotate Screen ⇄",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // 4. Smart TV Web Browser Link (Samsung Tizen / LG webOS)
                // -------------------------------------------------------------
                if (tvCastUrl.isNotBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Smart TV Browser Stream",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Open this link on Samsung Tizen or LG webOS browser",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tvCastUrl,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
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
                }
            }
        }
    }
}
