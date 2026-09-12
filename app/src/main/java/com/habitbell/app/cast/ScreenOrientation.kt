/**
 * # ScreenOrientation
 *
 * Target display orientation specification for mobile screen mirroring and TV display alignment.
 *
 * ## Architectural Role & Component Relationships
 * Domain enum in `com.habitbell.app.cast`:
 * - Consumed by [ScreenMirroringManager] to dictate requested activity orientation.
 * - Bound to [com.habitbell.app.MainActivity] window requested orientation lifecycle (`requestedOrientation`).
 * - Presented across [com.habitbell.app.ui.screens.SessionScreen], [com.habitbell.app.ui.screens.ModernHomeScreenSample],
 *   and [com.habitbell.app.ui.screens.SettingsDrawer].
 *
 * ## Concurrency & Thread Safety
 * Immutable enum type safe across all coroutine dispatchers and UI threads.
 */
package com.habitbell.app.cast

/**
 * Enumeration of allowable display orientation configurations for TV screen mirroring.
 */
enum class ScreenOrientation {
    /**
     * Vertical portrait display orientation (standard mobile device ergonomics, 9:16 aspect ratio).
     */
    PORTRAIT,

    /**
     * Horizontal landscape display orientation (standard widescreen TV / monitor ergonomics, 16:9 aspect ratio).
     */
    LANDSCAPE,

    /**
     * Dynamic device orientation driven organically by the smartphone hardware accelerometer/gyroscope sensors.
     */
    AUTO;

    /**
     * Toggles between [PORTRAIT] and [LANDSCAPE]. If currently [AUTO], defaults to [LANDSCAPE].
     *
     * @return Opposite fixed orientation ([LANDSCAPE] if [PORTRAIT] or [AUTO], [PORTRAIT] if [LANDSCAPE]).
     */
    fun toggle(): ScreenOrientation {
        return when (this) {
            PORTRAIT -> LANDSCAPE
            LANDSCAPE -> PORTRAIT
            AUTO -> LANDSCAPE
        }
    }
}
