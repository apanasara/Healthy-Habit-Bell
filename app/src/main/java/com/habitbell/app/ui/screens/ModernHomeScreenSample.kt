package com.habitbell.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.R
import com.habitbell.app.data.model.*

/**
 * Architectural Role: Visual-first, low-cognitive-load Home Screen prototype for Habit Bell.
 * Lifecycle: Rendered during active UI review to validate icon-driven navigation using Phosphor Line Icons (Light 1.5px).
 *
 * Core UX Strategy:
 * - Reduces reading stress: Users navigate through intuitive visual line icons rather than reading verbose paragraphs.
 * - Icon-anchored cards: Every habit card is anchored by a high-legibility Phosphor Light line icon.
 * - Minimalist, high-signal typography: Clear numbers and short labels.
 * - Central Theme Adaptability: Dynamically derives all visual aesthetics from MaterialTheme.colorScheme,
 *   seamlessly adhering to the user's global theme preference (AMOLED, Dark, Eye Comfort, or Sun Light).
 */

/**
 * Intent category pairing Phosphor line icon with a concise label.
 */
private data class IntentCategory(
    val title: String,
    val iconRes: Int?
)

/**
 * Primary Composable for the icon-driven, low-cognitive-load Home Screen sample.
 *
 * @param profiles Complete catalog of wellness timer profiles.
 * @param favorites User-marked favorite profiles.
 * @param reminders Scheduled routine reminders.
 * @param isZenMode Whether Zen focus mode is active.
 * @param onSelectProfile Callback when a profile is selected to start a session.
 * @param onToggleZenMode Callback to toggle Zen focus mode.
 * @param onCycleTheme Callback to cycle active theme variants.
 * @param onCreateNewClick Callback to launch custom profile creation.
 * @param onOpenSettings Callback to open the settings drawer.
 * @param modifier Composable layout modifier.
 */
@Composable
fun ModernHomeScreenSample(
    profiles: List<TimerProfile>,
    favorites: List<TimerProfile>,
    reminders: List<RoutineReminder>,
    isZenMode: Boolean,
    onSelectProfile: (TimerProfile) -> Unit,
    onToggleZenMode: () -> Unit,
    onCycleTheme: () -> Unit,
    onCreateNewClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf(
        IntentCategory("All", null),
        IntentCategory("Meditation", R.drawable.ic_ph_lotus),
        IntentCategory("Breathwork", R.drawable.ic_ph_wind),
        IntentCategory("Movement", R.drawable.ic_ph_walk),
        IntentCategory("Health", R.drawable.ic_ph_drop)
    )

    // Filter profiles based on intent category
    val filteredProfiles = remember(selectedCategory, profiles) {
        if (selectedCategory == "All") {
            profiles
        } else {
            profiles.filter { it.category.contains(selectedCategory, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ModernZenTopBar(
                isZenMode = isZenMode,
                onToggleZenMode = onToggleZenMode,
                onCycleTheme = onCycleTheme,
                onOpenSettings = onOpenSettings
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(Unit) { listState.scrollToItem(0) }
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Hero Atmospheric Focus Card (Visual-first mindful anchor)
            item {
                val heroProfile = favorites.firstOrNull() ?: profiles.firstOrNull()
                heroProfile?.let { profile ->
                    HeroFocusCard(
                        profile = profile,
                        nextReminder = reminders.firstOrNull(),
                        onStart = { onSelectProfile(profile) }
                    )
                }
            }

            // 2. Icon-Driven Intent Category Bar (Instant scanning without heavy reading)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = cat.title == selectedCategory
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                        val surfaceColor = MaterialTheme.colorScheme.surface
                        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                        val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) primaryColor else surfaceColor,
                            border = BorderStroke(1.dp, if (isSelected) primaryColor else borderColor),
                            modifier = Modifier.clickable { selectedCategory = cat.title }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                cat.iconRes?.let { iconRes ->
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        tint = if (isSelected) onPrimaryColor else onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = cat.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) onPrimaryColor else onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Section Header: Visual Practices
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRACTICES",
                        fontSize = 11.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+ NEW",
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onCreateNewClick() }
                    )
                }
            }

            // 4. Icon-Anchored Practice Rows (High visual recognition, zero clutter)
            items(filteredProfiles, key = { it.id }) { profile ->
                ModernProfileRow(
                    profile = profile,
                    onClick = { onSelectProfile(profile) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

/**
 * Resolves the appropriate Phosphor Light Vector Drawable for a given timer profile.
 *
 * @param profile Target [TimerProfile].
 * @return Drawable resource identifier matching the profile's domain.
 */
private fun resolvePhosphorIcon(profile: TimerProfile): Int {
    return when {
        profile.category.contains("Eating", ignoreCase = true) -> R.drawable.ic_ph_bowl
        profile.category.contains("Healing", ignoreCase = true) || profile.category.contains("Reiki", ignoreCase = true) -> R.drawable.ic_ph_sparkle
        profile.category.contains("Breath", ignoreCase = true) || profile.type == TimerType.MULTI_INTERVAL -> R.drawable.ic_ph_wind
        profile.category.contains("Walking", ignoreCase = true) || profile.category.contains("Movement", ignoreCase = true) || profile.type == TimerType.COMPOUND -> R.drawable.ic_ph_walk
        profile.category.contains("Health", ignoreCase = true) || profile.category.contains("Hydration", ignoreCase = true) -> R.drawable.ic_ph_drop
        profile.category.contains("Reading", ignoreCase = true) -> R.drawable.ic_ph_book
        profile.name.contains("Meditat", ignoreCase = true) || profile.category.contains("Mindful", ignoreCase = true) -> R.drawable.ic_ph_lotus
        else -> R.drawable.ic_ph_bell
    }
}

/**
 * Hero card with prominent Phosphor icon, ambient glow, and instant 1-tap start.
 */
@Composable
private fun HeroFocusCard(
    profile: TimerProfile,
    nextReminder: RoutineReminder?,
    onStart: () -> Unit
) {
    val iconRes = resolvePhosphorIcon(profile)
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStart() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent),
                        radius = 500f
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nextReminder?.timeString?.let { "NEXT AT $it" } ?: "RECOMMENDED PRACTICE",
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ph_waves),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Prominent Phosphor Icon badge
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(primaryColor.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, primaryColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            text = profile.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = onSurfaceColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val durationText = when (profile.type) {
                            TimerType.LINEAR -> "${profile.totalDurationSeconds / 60}m • Tibetan Bell"
                            TimerType.MULTI_INTERVAL -> "Breathwork • ${profile.pranayamaConfig?.targetRounds ?: 20} rounds"
                            TimerType.COMPOUND -> "Movement • 12 Poses"
                        }
                        Text(
                            text = durationText,
                            fontSize = 13.sp,
                            color = onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Single Primary Action: Clean Start Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(primaryColor)
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = onPrimaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Begin",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onPrimaryColor
                    )
                }
            }
        }
    }
}

/**
 * Clean, icon-anchored practice row allowing instant scanning without reading fatigue.
 */
@Composable
private fun ModernProfileRow(
    profile: TimerProfile,
    onClick: () -> Unit
) {
    val iconRes = resolvePhosphorIcon(profile)
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val backgroundColor = MaterialTheme.colorScheme.background

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Phosphor Light Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(primaryColor.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, primaryColor.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = profile.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitle = when (profile.type) {
                        TimerType.LINEAR -> {
                            val intervalM = profile.intervalDurationSeconds / 60
                            if (intervalM > 0 && profile.intervalDurationSeconds < profile.totalDurationSeconds) {
                                "${intervalM}m BELL INTERVAL"
                            } else {
                                profile.category.uppercase()
                            }
                        }
                        TimerType.MULTI_INTERVAL -> "EQUALIZED BREATH"
                        TimerType.COMPOUND -> "ASANA FLOW"
                    }
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = onSurfaceVariant
                    )
                }
            }

            // Compact duration badge
            val badgeText = when (profile.type) {
                TimerType.LINEAR -> "${profile.totalDurationSeconds / 60}m"
                TimerType.MULTI_INTERVAL -> "${profile.pranayamaConfig?.targetRounds ?: 20}r"
                TimerType.COMPOUND -> "${profile.compoundConfig?.targetRounds ?: 12}p"
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = backgroundColor,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Top bar with Phosphor line icons for theme and settings.
 */
@Composable
private fun ModernZenTopBar(
    isZenMode: Boolean,
    onToggleZenMode: () -> Unit,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_splash_logo),
                contentDescription = "Habit Bell Logo",
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = "Habit Bell",
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp,
                color = onSurfaceColor
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zen Mode Pill Toggle
            Surface(
                shape = CircleShape,
                color = if (isZenMode) primaryColor.copy(alpha = 0.15f) else surfaceColor,
                border = BorderStroke(1.dp, if (isZenMode) primaryColor else borderColor),
                modifier = Modifier.clickable { onToggleZenMode() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isZenMode) primaryColor else onSurfaceVariant, CircleShape)
                    )
                    Text(
                        text = if (isZenMode) "Zen On" else "Zen",
                        fontSize = 11.sp,
                        color = if (isZenMode) primaryColor else onSurfaceVariant
                    )
                }
            }

            // Central Theme Switcher: Sun ☀️ in Dark Mode, Moon 🌙 in Light Mode
            IconButton(onClick = onCycleTheme, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(id = if (isDark) R.drawable.ic_ph_sun else R.drawable.ic_ph_moon),
                    contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "Settings",
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
