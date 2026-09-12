package com.habitbell.app.cast

/**
 * # ScreenOrientation
 *
 * Defines display orientation targets for Habit Bell on mobile and external TV mirroring displays.
 *
 * ## Architectural Role & Relationships
 * Dictates requested window orientation in [com.habitbell.app.MainActivity] and coordinates with
 * [ScreenMirroringManager] to ensure timer visualizations align with the physical orientation of
 * external television screens, projectors, and wireless mirroring monitors.
 *
 * ## Concurrency Model
 * Immutable enumeration; thread-safe for concurrent read operations across coroutine flows.
 */
enum class ScreenOrientation {
    /** Forced vertical orientation (sensor-assisted portrait mode). */
    PORTRAIT,

    /** Forced horizontal orientation (sensor-assisted landscape mode). */
    LANDSCAPE,

    /** Unconstrained orientation driven dynamically by hardware accelerometer sensors and system policy. */
    AUTO;

    /**
     * Toggles between [LANDSCAPE] (Horizontal) and [PORTRAIT] (Vertical).
     * If currently [AUTO], defaults to toggling into [LANDSCAPE].
     *
     * @return The opposite explicit [ScreenOrientation].
     */
    fun toggle(): ScreenOrientation = when (this) {
        LANDSCAPE -> PORTRAIT
        PORTRAIT -> LANDSCAPE
        AUTO -> LANDSCAPE
    }
}
