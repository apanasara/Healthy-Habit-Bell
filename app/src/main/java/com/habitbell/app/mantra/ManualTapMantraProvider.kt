package com.habitbell.app.mantra

import android.os.SystemClock
import com.habitbell.app.data.model.MantraCounterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * # ManualTapMantraProvider
 *
 * Tactile touch-screen manual bead advance provider implementing [MantraDataSource].
 *
 * ## Architectural Role & Component Relationships
 * - Used as the fallback and intentional quiet-environment input source.
 * - Engineered for silent meditation halls, libraries, mosques, churches, or quiet whisper recitation.
 * - Computes rolling cadence (CPM) using a 5-tap sliding-window ring buffer based on monotonic [SystemClock.elapsedRealtime].
 *
 * ## Lifecycle & Thread Safety
 * Lightweight in-memory provider. Thread-safe across all coroutine dispatchers and Compose UI event handlers.
 */
class ManualTapMantraProvider : MantraDataSource {

    override val inputSourceType: MantraInputSourceType = MantraInputSourceType.MANUAL_BEAD_TAP

    override val isAvailable: Boolean = true

    private val _inputFlow = MutableStateFlow(MantraInputEvent())
    override val inputFlow: StateFlow<MantraInputEvent> = _inputFlow.asStateFlow()

    private var lastTapMonotonicMs: Long = 0L
    private val tapIntervals = ArrayDeque<Long>(5)
    private var activeConfig: MantraCounterConfig? = null

    override fun start(config: MantraCounterConfig) {
        activeConfig = config
        reset()
    }

    override fun pause() {
        // No-op for manual tap
    }

    override fun resume() {
        // No-op for manual tap
    }

    override fun stop() {
        reset()
        activeConfig = null
    }

    override fun reset() {
        lastTapMonotonicMs = 0L
        tapIntervals.clear()
        _inputFlow.value = MantraInputEvent()
    }

    override fun registerManualBead() {
        val nowMono = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        if (lastTapMonotonicMs > 0L) {
            val delta = nowMono - lastTapMonotonicMs
            if (delta in 200..60000) {
                if (tapIntervals.size >= 5) {
                    tapIntervals.removeFirst()
                }
                tapIntervals.addLast(delta)
            }
        }
        lastTapMonotonicMs = nowMono

        val rollingCpm = if (tapIntervals.isNotEmpty()) {
            val avgInterval = tapIntervals.average()
            (60000.0 / avgInterval).toInt().coerceIn(2, 180)
        } else 0

        _inputFlow.value = MantraInputEvent(
            beadDelta = 1,
            instantaneousCadenceCpm = rollingCpm,
            audioAmplitudeRms = 0.95f,
            thresholdRms = 0.05f,
            isSpeechActive = false,
            activeVerseDurationSeconds = 0f,
            timestampMillis = nowWall
        )
    }
}
