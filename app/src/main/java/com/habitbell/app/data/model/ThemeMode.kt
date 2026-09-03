package com.habitbell.app.data.model

/**
 * Display themes optimized for wellness environments, circadian rhythms, and battery power.
 */
enum class ThemeMode {
    /**
     * Pure `#000000` AMOLED dark theme.
     * Pixels are completely powered off on OLED displays, maximizing power efficiency and minimizing visual glare.
     */
    AMOLED,

    /**
     * Circadian warm-amber color scheme designed for evening wind-down, reducing blue light exposure.
     */
    EYE_COMFORT,

    /**
     * Standard dark theme with neutral charcoal background surfaces.
     */
    DARK,

    /**
     * Crisp light theme for bright daylight conditions.
     */
    LIGHT
}
