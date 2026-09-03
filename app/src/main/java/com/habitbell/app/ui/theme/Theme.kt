package com.habitbell.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.habitbell.app.data.model.ThemeMode

/** Pure `#000000` AMOLED color scheme for OLED displays. */
private val AmoledColorScheme = darkColorScheme(
    primary = BellGold,
    secondary = EyeComfortAmber,
    background = AmoledBlack,
    surface = AmoledSurface,
    surfaceVariant = AmoledCard,
    onPrimary = AmoledBlack,
    onBackground = AmoledText,
    onSurface = AmoledText,
    onSurfaceVariant = AmoledMuted
)

/** Circadian warm amber color scheme for evening wind-down. */
private val EyeComfortColorScheme = darkColorScheme(
    primary = EyeComfortAmber,
    secondary = EyeComfortWarmGold,
    background = EyeComfortDarkBg,
    surface = EyeComfortSurface,
    surfaceVariant = Color(0xFF2D251D),
    onPrimary = EyeComfortDarkBg,
    onBackground = EyeComfortWarmText,
    onSurface = EyeComfortWarmText,
    onSurfaceVariant = EyeComfortMuted
)

/** Standard neutral dark theme. */
private val StandardDarkColorScheme = darkColorScheme(
    primary = BellGold,
    secondary = ZenGreen,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2C2C2E),
    onPrimary = DarkBg,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkMuted
)

/** Standard crisp daytime light theme. */
private val StandardLightColorScheme = lightColorScheme(
    primary = EyeComfortAmber,
    secondary = ZenGreen,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = Color(0xFFE5E5EA),
    onPrimary = LightSurface,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightMuted
)

/**
 * Top-level Material3 Theme wrapper for Habit Bell.
 *
 * Dynamically resolves color schemes according to the selected [ThemeMode].
 *
 * @param themeMode The active theme configuration ([ThemeMode.AMOLED], [ThemeMode.EYE_COMFORT], etc.).
 * @param content The composable subtree wrapped with this styling theme.
 */
@Composable
fun HabitBellTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.AMOLED -> AmoledColorScheme
        ThemeMode.EYE_COMFORT -> EyeComfortColorScheme
        ThemeMode.DARK -> StandardDarkColorScheme
        ThemeMode.LIGHT -> StandardLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
