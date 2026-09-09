package com.habitbell.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.R
import kotlinx.coroutines.delay

/**
 * Architectural Role: In-App Branded Entry Screen for Habit Bell.
 *
 * Component Relationships:
 * - Hosted by `MainActivity` as an initial overlay before transitioning to `AppScreen.HOME`.
 * - Complements the native Android 12+ OS window splash screen (`splash_background.xml`)
 *   by providing a seamless, hardware-accelerated animated handoff into Jetpack Compose.
 *
 * Lifecycle & Concurrency:
 * - Lifecycle: Active for a maximum duration of 1,100ms on application cold start.
 * - Concurrency: Animates via Kotlin coroutines within `LaunchedEffect` on the main UI dispatcher.
 *   Dispatches `onTimeout` upon completion or on user tap.
 *
 * @param onTimeout Callback dispatched when the splash screen animation concludes or is dismissed by tap.
 * @param modifier Composable layout modifier.
 */
@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }

    // Execute gentle serene breathing zoom and alpha fade
    LaunchedEffect(Unit) {
        // Step 1: Smooth fade-in and scale expansion
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
        alpha.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )

        // Step 2: Mindful pause
        delay(400)

        // Step 3: Transition to Home
        onTimeout()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060709))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Immediate skip on user touch
                onTimeout()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value.coerceIn(0f, 1f))
        ) {
            // Official Golden Lotus & Bell Branding Symbol
            Image(
                painter = painterResource(id = R.drawable.ic_splash_logo),
                contentDescription = "Habit Bell Official Logo",
                modifier = Modifier
                    .size(180.dp)
                    .padding(bottom = 24.dp)
            )

            // Primary App Title
            Text(
                text = "Habit Bell",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                color = Color(0xFFFFFFFF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Meditative Subtitle
            Text(
                text = "Mindful Wellness & Living Room Timer",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp,
                color = Color(0xFFE5A93C)
            )
        }
    }
}
