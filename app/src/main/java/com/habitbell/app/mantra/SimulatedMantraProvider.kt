package com.habitbell.app.mantra

import com.habitbell.app.data.model.MantraCounterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext

/**
 * # SimulatedMantraProvider
 *
 * Deterministic synthetic telemetry generator implementing [MantraDataSource].
 *
 * ## Architectural Role & Component Relationships
 * - Used for automated testing, CI pipelines, and Jetpack Compose UI previews.
 * - Emits periodic beads matching realistic recitation cadences for the active technique.
 *
 * @param scope Coroutine scope governing the background simulation loop.
 */
class SimulatedMantraProvider(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : MantraDataSource {

    override val inputSourceType: MantraInputSourceType = MantraInputSourceType.SIMULATED

    override val isAvailable: Boolean = true

    private val _inputFlow = MutableStateFlow(MantraInputEvent())
    override val inputFlow: StateFlow<MantraInputEvent> = _inputFlow.asStateFlow()

    private var simulationJob: Job? = null
    private var activeConfig: MantraCounterConfig? = null

    override fun start(config: MantraCounterConfig) {
        activeConfig = config
        resume()
    }

    override fun pause() {
        simulationJob?.cancel()
        simulationJob = null
    }

    override fun resume() {
        pause()
        val config = activeConfig ?: return

        simulationJob = scope.launch {
            val intervalMs = when (config.technique.defaultMode) {
                MantraMode.EXTENDED_VERSE -> ((config.minVerseDurationSec + config.interVersePauseThresholdSec) * 1000L).toLong().coerceAtLeast(4000L)
                MantraMode.SHORT_JAPA -> 1200L
                MantraMode.AUMKAR_DRONE -> 3500L
            }

            val cpm = (60000.0 / intervalMs).toInt()

            while (coroutineContext.isActive) {
                delay(intervalMs)
                _inputFlow.value = MantraInputEvent(
                    beadDelta = 1,
                    instantaneousCadenceCpm = cpm,
                    audioAmplitudeRms = 0.85f,
                    thresholdRms = 0.05f,
                    isSpeechActive = false,
                    activeVerseDurationSeconds = 0f,
                    timestampMillis = System.currentTimeMillis()
                )
            }
        }
    }

    override fun stop() {
        pause()
        reset()
        activeConfig = null
    }

    override fun reset() {
        _inputFlow.value = MantraInputEvent()
    }

    override fun registerManualBead() {
        _inputFlow.value = MantraInputEvent(
            beadDelta = 1,
            instantaneousCadenceCpm = 20,
            audioAmplitudeRms = 0.90f,
            thresholdRms = 0.05f,
            isSpeechActive = false,
            activeVerseDurationSeconds = 0f,
            timestampMillis = System.currentTimeMillis()
        )
    }
}
