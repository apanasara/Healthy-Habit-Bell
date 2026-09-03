package com.habitbell.app.data.model

/**
 * Root domain entity representing a complete wellness timer profile.
 *
 * Encapsulates the timer typology, timing parameters, chime patterns, visual themes,
 * and hardware mode settings (Pocket Mode, Keep Screen On).
 *
 * @property id Unique identifier key (e.g. "eating-mindful-20", "pranayama-box-breath").
 * @property name User-facing display title for the profile.
 * @property type The timer execution topology ([TimerType.LINEAR], [TimerType.MULTI_INTERVAL], or [TimerType.COMPOUND]).
 * @property category Conceptual classification grouping (e.g. "Mindful Eating", "Energy Healing", "Breathwork").
 * @property iconName Material icon symbol identifier used for rendering cards.
 * @property totalDurationSeconds Total duration of the session in seconds (used for [TimerType.LINEAR]).
 * @property intervalDurationSeconds Periodic interval chime duration in seconds (used for [TimerType.LINEAR]).
 * @property bellPattern Chime strike pattern ([BellPattern.SINGLE], [BellPattern.DOUBLE], [BellPattern.THREE_BELL]).
 * @property theme Default visual theme applied when executing this profile ([ThemeMode]).
 * @property displayMode If true, keeps the display awake and renders animated visual cues.
 * @property pocketMode If true, enables proximity-sensor-based AMOLED black screen and tactile haptics.
 * @property isFavorite Whether the user has marked this profile as a favorite.
 * @property pranayamaConfig Breathwork step sequence parameters (required when type is [TimerType.MULTI_INTERVAL]).
 * @property compoundConfig Multi-pose sequence parameters (required when type is [TimerType.COMPOUND]).
 */
data class TimerProfile(
    val id: String,
    val name: String,
    val type: TimerType,
    val category: String,
    val iconName: String,
    val totalDurationSeconds: Int = 0,
    val intervalDurationSeconds: Int = 0,
    val bellPattern: BellPattern = BellPattern.THREE_BELL,
    val theme: ThemeMode = ThemeMode.AMOLED,
    val displayMode: Boolean = true,
    val pocketMode: Boolean = false,
    val isFavorite: Boolean = false,
    val pranayamaConfig: PranayamaConfig? = null,
    val compoundConfig: CompoundConfig? = null
)
