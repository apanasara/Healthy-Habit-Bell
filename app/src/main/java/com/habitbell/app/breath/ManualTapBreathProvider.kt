package com.habitbell.app.breath

import com.habitbell.app.data.model.BreathCounterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * # ManualTapBreathProvider
 *
 * Interactive touch-screen breath stroke data provider implementing [BreathDataSource].
 *
 * ## Architectural Role & Relationships
 * - Acts as the primary zero-permission and fallback provider when microphone access is unavailable
 *   or when practicing in quiet meditation halls, temples, or noisy public spaces.
 * - Computes real-time cadence (BPM) based on a sliding window of recent touch intervals.
 * - Dispatches [BreathInputEvent] instances with `strokeDelta = 1` into [BreathCountManager].
 *
 * ## Lifecycle & Concurrency
 * Thread-safe state updates using [MutableStateFlow] and synchronized tap interval tracking.
 */
class ManualTapBreathProvider : BreathDataSource {

    override val inputSourceType: BreathInputSourceType = BreathInputSourceType.MANUAL_TAP

    override val isAvailable: Boolean = true

    /** Backing state flow emitting raw tap inputs. */
    private val _inputFlow = MutableStateFlow(BreathInputEvent())
    override val inputFlow: StateFlow<BreathInputEvent> = _inputFlow.asStateFlow()

    /** Timestamp of the most recent tap event in milliseconds. */
    private var lastTapTimeMillis: Long = 0L

    /** Sliding window ring buffer tracking the last 5 inter-tap intervals in milliseconds. */
    private val tapIntervals = ArrayDeque<Long>(5)

    /** Active configuration governing counting thresholds. */
    private var activeConfig: BreathCounterConfig? = null

    /** Lock protecting tap interval computations across threads. */
    private val tapLock = Any()

    override fun start(config: BreathCounterConfig) {
        activeConfig = config
        reset()
    }

    override fun pause() {
        // No background sensors to pause for manual tap
    }

    override fun resume() {
        // Ready for touch inputs
    }

    override fun stop() {
        reset()
        activeConfig = null
    }

    override fun reset() {
        synchronized(tapLock) {
            lastTapTimeMillis = 0L
            tapIntervals.clear()
            _inputFlow.value = BreathInputEvent()
        }
    }

    /**
     * Registers an interactive touch stroke on the screen, calculates cadence BPM,
     * and publishes a [BreathInputEvent].
     */
    override fun registerManualStroke() {
        val now = System.currentTimeMillis()
        var calculatedBpm = 0

        synchronized(tapLock) {
            if (lastTapTimeMillis > 0L) {
                val intervalMs = now - lastTapTimeMillis
                // Discard stale taps (> 3.5 seconds between taps) or extreme debounce (< 150 ms)
                if (intervalMs in 150..3500) {
                    if (tapIntervals.size >= 5) {
                        tapIntervals.removeFirst()
                    }
                    tapIntervals.addLast(intervalMs)

                    val averageInterval = tapIntervals.average()
                    if (averageInterval > 0) {
                        calculatedBpm = (60_000.0 / averageInterval).toInt().coerceIn(10, 200)
                    }
                } else if (intervalMs > 3500) {
                    // Stale pause, reset window
                    tapIntervals.clear()
                }
            }
            lastTapTimeMillis = now

            _inputFlow.value = BreathInputEvent(
                strokeDelta = 1,
                instantaneousCadenceBpm = calculatedBpm,
                audioAmplitudeRms = 0.85f,
                isHummingActive = false,
                activeHumDurationSeconds = 0f,
                timestampMillis = now
            )
        }
    }
}
