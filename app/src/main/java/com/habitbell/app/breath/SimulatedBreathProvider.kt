package com.habitbell.app.breath

import com.habitbell.app.data.model.BreathCounterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * # SimulatedBreathProvider
 *
 * Synthetic deterministic breath stroke generator implementing [BreathDataSource].
 *
 * ## Architectural Role & Relationships
 * - Used during automated unit tests, Compose UI previews, and robot testing.
 * - Emits steady simulated breath strokes at a configurable pace (default 75 BPM for Kapalabhati,
 *   35 BPM for Bhastrika, or 10-second resonant humming cycles for Bhramari).
 *
 * ## Lifecycle & Concurrency
 * - Bound to a private [CoroutineScope] on [Dispatchers.Default] with [SupervisorJob].
 * - Safely cancelled on [stop] or [pause].
 */
class SimulatedBreathProvider(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BreathDataSource {

    override val inputSourceType: BreathInputSourceType = BreathInputSourceType.SIMULATED

    override val isAvailable: Boolean = true

    private val _inputFlow = MutableStateFlow(BreathInputEvent())
    override val inputFlow: StateFlow<BreathInputEvent> = _inputFlow.asStateFlow()

    private var tickerJob: Job? = null
    private var activeConfig: BreathCounterConfig? = null

    override fun start(config: BreathCounterConfig) {
        activeConfig = config
        resume()
    }

    override fun pause() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun resume() {
        tickerJob?.cancel()
        val config = activeConfig ?: return

        tickerJob = scope.launch {
            when (config.technique) {
                BreathTechnique.KAPALABHATI, BreathTechnique.FREE_COUNT -> {
                    val intervalMs = 800L // 75 BPM
                    while (isActive) {
                        delay(intervalMs)
                        _inputFlow.value = BreathInputEvent(
                            strokeDelta = 1,
                            instantaneousCadenceBpm = 75,
                            audioAmplitudeRms = 0.82f,
                            isHummingActive = false,
                            activeHumDurationSeconds = 0f,
                            timestampMillis = System.currentTimeMillis()
                        )
                    }
                }
                BreathTechnique.BHASTRIKA -> {
                    val intervalMs = 1700L // ~35 BPM
                    while (isActive) {
                        delay(intervalMs)
                        _inputFlow.value = BreathInputEvent(
                            strokeDelta = 1,
                            instantaneousCadenceBpm = 35,
                            audioAmplitudeRms = 0.90f,
                            isHummingActive = false,
                            activeHumDurationSeconds = 0f,
                            timestampMillis = System.currentTimeMillis()
                        )
                    }
                }
                BreathTechnique.BHRAMARI -> {
                    // Simulate 10-second humming tone followed by 3-second inhale breath
                    while (isActive) {
                        for (sec in 1..10) {
                            delay(1000L)
                            _inputFlow.value = BreathInputEvent(
                                strokeDelta = 0,
                                instantaneousCadenceBpm = 0,
                                audioAmplitudeRms = 0.75f,
                                isHummingActive = true,
                                activeHumDurationSeconds = sec.toFloat(),
                                timestampMillis = System.currentTimeMillis()
                            )
                        }
                        // Hum ended: fire round increment
                        _inputFlow.value = BreathInputEvent(
                            strokeDelta = 1,
                            instantaneousCadenceBpm = 0,
                            audioAmplitudeRms = 0.1f,
                            isHummingActive = false,
                            activeHumDurationSeconds = 10.0f,
                            timestampMillis = System.currentTimeMillis()
                        )
                        delay(3000L) // Rest/inhale pause
                    }
                }
            }
        }
    }

    override fun stop() {
        pause()
        reset()
        activeConfig = null
    }

    override fun reset() {
        _inputFlow.value = BreathInputEvent()
    }

    override fun registerManualStroke() {
        _inputFlow.value = BreathInputEvent(
            strokeDelta = 1,
            instantaneousCadenceBpm = 60,
            audioAmplitudeRms = 0.9f,
            timestampMillis = System.currentTimeMillis()
        )
    }
}
