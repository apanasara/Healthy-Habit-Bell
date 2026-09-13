package com.habitbell.app.mantra

/**
 * # MantraUpdate
 *
 * Immutable reactive state snapshot representing live mantra recitation progress,
 * bead metrics, acoustic amplitude, and Mala round progression.
 *
 * ## Architectural Role & Component Relationships
 * - Emitted via Kotlin [kotlinx.coroutines.flow.StateFlow] by [MantraCountManager].
 * - Ingested by [com.habitbell.app.engine.CentralSessionHandler] and [com.habitbell.app.engine.TimerEngine].
 * - Observed by [com.habitbell.app.ui.components.MantraCounterContent] for rendering 108-bead rings,
 *   cadence badges, active verse timers, and acoustic ripple halos.
 *
 * ## Lifecycle & Concurrency
 * Immutable value carrier data class. Thread-safe across all coroutine dispatchers and Compose recomposition scopes.
 *
 * @property currentBead Current bead count in the active Mala round (e.g. 1 to 108).
 * @property targetBeads Target beads required to complete one Mala (default 108).
 * @property currentMala Current 1-based Mala repetition round (e.g. 1, 2, 3).
 * @property targetMalas Total target Mala rounds planned for this session.
 * @property totalSessionChants Cumulative chants completed across all Malas in this session.
 * @property cadenceCpm Real-time recitation cadence in Chants Per Minute (CPM).
 * @property audioAmplitudeRms Normalized full-spectrum acoustic energy (0.0f to 1.0f).
 * @property thresholdRms Normalized detection threshold level (0.0f to 1.0f).
 * @property isReciting True when active speech energy is detected in the current window.
 * @property activeVerseDurationSeconds Cumulative vocal duration of ongoing recitation in seconds.
 * @property micSensitivity Multiplier adjusting detection sensitivity (0.5f to 2.5f).
 * @property technique Active sacred recitation modality ([MantraTechnique]).
 * @property isCompleted True when all target Malas and beads have been achieved.
 * @property timestampMillis Monotonic system timestamp in milliseconds.
 */
data class MantraUpdate(
    val currentBead: Int = 0,
    val targetBeads: Int = 108,
    val currentMala: Int = 1,
    val targetMalas: Int = 1,
    val totalSessionChants: Int = 0,
    val cadenceCpm: Int = 0,
    val audioAmplitudeRms: Float = 0f,
    val thresholdRms: Float = 0.05f,
    val isReciting: Boolean = false,
    val activeVerseDurationSeconds: Float = 0f,
    val micSensitivity: Float = 1.0f,
    val technique: MantraTechnique = MantraTechnique.GAYATRI_MANTRA,
    val isCompleted: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    /**
     * Normalized progress fraction (0.0f to 1.0f) across the active Mala round.
     */
    val malaProgressFraction: Float
        get() = if (targetBeads > 0) {
            (currentBead.toFloat() / targetBeads.toFloat()).coerceIn(0f, 1f)
        } else 0f

    /**
     * Total target chants across all configured Mala rounds.
     */
    val totalTargetChants: Int
        get() = targetBeads * targetMalas

    /**
     * Overall session progress fraction (0.0f to 1.0f) across all target Malas.
     */
    val overallProgressFraction: Float
        get() = if (totalTargetChants > 0) {
            (totalSessionChants.toFloat() / totalTargetChants.toFloat()).coerceIn(0f, 1f)
        } else 0f

    /**
     * Human-readable bead progress display string (e.g. "45 / 108").
     */
    val formattedBeadDisplay: String
        get() = "$currentBead / $targetBeads"

    /**
     * Human-readable Mala round display string (e.g. "MALA 1 OF 3").
     */
    val formattedMalaDisplay: String
        get() = if (targetMalas > 1) "MALA $currentMala OF $targetMalas" else "MALA 1"

    /**
     * Human-readable cadence display string (e.g. "24 CPM" or "-- CPM").
     */
    val formattedCadenceDisplay: String
        get() = if (cadenceCpm > 0) "$cadenceCpm CPM" else "-- CPM"

    /**
     * Human-readable active verse timer display (e.g. "11.2s").
     */
    val formattedVerseTimer: String
        get() = "%.1fs".format(activeVerseDurationSeconds)
}
