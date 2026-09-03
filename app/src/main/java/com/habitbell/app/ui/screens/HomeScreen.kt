package com.habitbell.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbell.app.data.model.*
import com.habitbell.app.ui.theme.BellGold
import com.habitbell.app.ui.theme.EyeComfortAmber

@Composable
fun HomeScreen(
    profiles: List<TimerProfile>,
    favorites: List<TimerProfile>,
    recentProfiles: List<TimerProfile>,
    reminders: List<RoutineReminder>,
    currentTheme: ThemeMode,
    isZenMode: Boolean,
    onSelectProfile: (TimerProfile) -> Unit,
    onConfigureProfile: (TimerProfile) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleZenMode: () -> Unit,
    onCycleTheme: () -> Unit,
    onCreateNewClick: () -> Unit,
    onOpenTVMode: (TimerProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            HomeTopBar(
                currentTheme = currentTheme,
                isZenMode = isZenMode,
                onToggleZenMode = onToggleZenMode,
                onCycleTheme = onCycleTheme
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNewClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Timer")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Daily Routine Reminders (Phase 4)
            item {
                RoutineReminderSection(
                    reminders = reminders,
                    profiles = profiles,
                    onStartProfile = onSelectProfile
                )
            }

            // 2. Favorites (PRD: "⭐️ Favorites")
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "⭐️ Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favorites, key = { it.id }) { fav ->
                            FavoriteCard(
                                profile = fav,
                                onClick = { onSelectProfile(fav) },
                                onTVClick = { onOpenTVMode(fav) },
                                onSettingsClick = { onConfigureProfile(fav) }
                            )
                        }
                    }
                }
            }

            // 3. Recent Sessions (PRD: "Recent Sessions")
            if (recentProfiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentProfiles, key = { "recent_${it.id}" }) { profile ->
                            RecentChip(profile = profile, onClick = { onSelectProfile(profile) })
                        }
                    }
                }
            }

            // 4. All Timers (PRD: "All Timers")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Timers",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "+ Create New",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onCreateNewClick() }
                    )
                }
            }

            items(profiles, key = { it.id }) { profile ->
                TimerProfileListItem(
                    profile = profile,
                    onClick = { onSelectProfile(profile) },
                    onToggleFav = { onToggleFavorite(profile.id) },
                    onTVClick = { onOpenTVMode(profile) },
                    onSettingsClick = { onConfigureProfile(profile) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    currentTheme: ThemeMode,
    isZenMode: Boolean,
    onToggleZenMode: () -> Unit,
    onCycleTheme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Habit Bell",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Wellness Operating System",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Zen Mode Indicator / Toggle
            IconButton(
                onClick = onToggleZenMode,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isZenMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isZenMode) Icons.Default.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOff,
                    contentDescription = "Zen Mode",
                    tint = if (isZenMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Theme Switcher Button
            IconButton(
                onClick = onCycleTheme,
                modifier = Modifier.size(40.dp)
            ) {
                val icon = when (currentTheme) {
                    ThemeMode.AMOLED -> Icons.Default.Brightness2
                    ThemeMode.EYE_COMFORT -> Icons.Default.WbSunny
                    ThemeMode.DARK -> Icons.Default.Nightlight
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Theme: ${currentTheme.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RoutineReminderSection(
    reminders: List<RoutineReminder>,
    profiles: List<TimerProfile>,
    onStartProfile: (TimerProfile) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Daily Routine Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                reminders.forEach { rem ->
                    val profile = profiles.find { it.id == rem.profileId }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = profile != null) {
                                profile?.let { onStartProfile(it) }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = rem.timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = rem.title.split(" ").first(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    profile: TimerProfile,
    onClick: () -> Unit,
    onTVClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(168.dp)
            .height(138.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (profile.category) {
                    "Mindful Eating" -> "🍽"
                    "Energy Healing" -> "✨"
                    "Breathwork" -> "🧘"
                    "Yoga Sequences" -> "🌞"
                    else -> "🔔"
                }
                Text(text = icon, fontSize = 24.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTVClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Cast,
                            contentDescription = "Cast to TV",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = when (profile.type) {
                        TimerType.LINEAR -> "${profile.totalDurationSeconds / 60}m • ${profile.intervalDurationSeconds}s bells"
                        TimerType.MULTI_INTERVAL -> "${profile.pranayamaConfig?.targetRounds ?: 0} rounds"
                        TimerType.COMPOUND -> "${profile.compoundConfig?.targetRounds ?: 0} rounds"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentChip(
    profile: TimerProfile,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun TimerProfileListItem(
    profile: TimerProfile,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    onTVClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                val emoji = when (profile.category) {
                    "Mindful Eating" -> "🍽"
                    "Energy Healing" -> "✨"
                    "Breathwork" -> "🧘"
                    "Yoga Sequences" -> "🌞"
                    "Movement" -> "🚶"
                    "Focus" -> "📖"
                    "Daily Health" -> "💧"
                    else -> "🔔"
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 20.sp)
                }

                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when (profile.type) {
                            TimerType.LINEAR -> "${profile.totalDurationSeconds / 60} min • ${profile.intervalDurationSeconds}s interval bells"
                            TimerType.MULTI_INTERVAL -> "Breathwork • ${profile.pranayamaConfig?.targetRounds} rounds"
                            TimerType.COMPOUND -> "Yoga • 12 Poses • ${profile.compoundConfig?.targetRounds} rounds"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTVClick) {
                    Icon(
                        Icons.Outlined.Cast,
                        contentDescription = "Cast / TV Dashboard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Configure Timer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        imageVector = if (profile.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (profile.isFavorite) BellGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
