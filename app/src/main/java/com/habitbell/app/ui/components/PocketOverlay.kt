package com.habitbell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure `#000000` AMOLED power-saving curtain for Hardware Pocket Mode.
 *
 * When the user places the phone in their pocket or face down on a table during mindful eating
 * or walking meditation, this overlay renders a 100% black screen. On OLED and AMOLED displays,
 * black pixels are completely powered down, eliminating battery drain and screen glare.
 *
 * Touching anywhere on the screen invokes [onDismiss] to unlock and reveal the session UI.
 *
 * @param onDismiss Callback invoked when the user taps anywhere to dismiss the pocket curtain.
 * @param modifier Composable layout modifier.
 */
@Composable
fun PocketOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Pocket Mode Active",
                tint = Color(0xFF333333), // Subdued low luminance to preserve battery
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Pocket Mode Active",
                color = Color(0xFF444444),
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap anywhere to wake display",
                color = Color(0xFF2B2B2B),
                fontSize = 11.sp
            )
        }
    }
}
