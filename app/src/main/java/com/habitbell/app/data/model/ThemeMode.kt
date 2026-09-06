package com.habitbell.app.data.model

/**
 * # ThemeMode
 *
 * Display themes optimized for wellness environments, circadian rhythms, and battery power.
 *
 * ## Architectural Role & Relationships
 * Governs the Material 3 color scheme applied globally across all UI screens.
 * Supports a circadian Sun (Day) / Moon (Night) duality, both engineered with
 * blue-light attenuation to protect melatonin secretion and prevent ocular fatigue.
 */
enum class ThemeMode {
    /**
     * Pure `#000000` AMOLED dark theme.
     * Pixels are completely powered off on OLED displays, maximizing power efficiency and minimizing visual glare.
     */
    AMOLED,

    /**
     * Circadian warm-amber nighttime color scheme designed for evening wind-down, reducing blue light exposure.
     */
    EYE_COMFORT,

    /**
     * Standard dark theme with neutral charcoal background surfaces.
     */
    DARK,

    /**
     * Blue-light-reduced warm daytime light theme (Sun mode) with gentle parchment tones.
     */
    LIGHT;

    /**
     * Whether this theme variant represents a Sun (Day / Light) configuration.
     */
    val isSunDayTheme: Boolean
        get() = this == LIGHT

    /**
     * Whether this theme variant represents a Moon (Night / Dark / AMOLED) configuration.
     */
    val isMoonNightTheme: Boolean
        get() = this != LIGHT
}
