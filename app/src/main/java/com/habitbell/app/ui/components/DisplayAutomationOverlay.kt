package com.habitbell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.engine.DisplayCurtainMode
import com.habitbell.app.engine.DisplayCurtainState

/**
 * Pure `#000000` AMOLED blackout curtain for automated display states.
 *
 * ## Architectural Role & Concurrency Model
 * Rendered at the root window level in `MainActivity`. Shuts off OLED pixels completely
 * during Pocket Mode, Car HUD driving sessions, Smart TV big-screen casting, and Smart Watch sessions.
 * Provides zero-latency dismissal upon user touch or Lift-to-Wake.
 *
 * @param state Immutable [DisplayCurtainState] defining active mode, titles, and connected device info.
 * @param onDismiss Callback invoked when the user taps anywhere on the screen to wake the UI.
 * @param modifier Layout modifier.
 */
@Composable
fun DisplayAutomationOverlay(
    state: DisplayCurtainState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (state.mode) {
        DisplayCurtainMode.POCKET -> Icons.Outlined.Lock
        DisplayCurtainMode.CAR_HUD -> Icons.Outlined.DirectionsCar
        DisplayCurtainMode.TV_CAST -> Icons.Outlined.Tv
        DisplayCurtainMode.WATCH -> Icons.Outlined.Watch
        DisplayCurtainMode.NONE -> Icons.Outlined.Lock
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 100% pure black: OLED displays turn off individual emissive pixels completely
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Suppress ripple animation to maintain complete darkness
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF141414),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier.padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = state.title,
                        tint = Color(0xFFD4AF37), // Low-luminance gold accent
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Text(
                text = state.title,
                color = Color(0xFFCCCCCC),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = state.subtitle,
                color = Color(0xFF666666),
                fontSize = 12.sp,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier
                    .clickable { onDismiss() }
            ) {
                Text(
                    text = "Tap or lift phone to wake",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}
