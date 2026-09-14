package com.habitbell.app.mantra

import android.content.Context
import android.util.Log
import com.habitbell.app.data.model.MantraCounterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * # MantraCountManager
 *
 * Central domain orchestrator governing sacred mantra and verse tracking,
 * acoustic microphone DSP analysis, manual bead tap inputs, milestone cues, and Mala round transitions.
 *
 * ## Architectural Role & Component Relationships
 * - Bound directly to [com.habitbell.app.engine.CentralSessionHandler] alongside `BreathCountManager`.
 * - Coordinates between [AcousticMantraSensorProvider], [ManualTapMantraProvider], and [SimulatedMantraProvider].
 * - Pipes authoritative [MantraUpdate] telemetry into [com.habitbell.app.engine.TimerEngine].
 * - Manages the sacred progression: bead increment (1 to 108) -> half-Mala milestone -> full Mala completion.
 *
 * ## Lifecycle & Concurrency
 * - Scoped to process application context on [Dispatchers.Default] + [SupervisorJob].
 * - Emits thread-safe immutable states via Kotlin [StateFlow].
 *
 * @param context Android application context for permissions and hardware access (optional in tests).
 * @param acousticProvider Hardware-backed acoustic microphone DSP analyzer.
 * @param manualTapProvider Interactive touch-screen manual bead tap provider.
 * @param simulatedProvider Deterministic synthetic recitation generator.
 */
class MantraCountManager(
    private val context: Context? = null,
    val acousticProvider: AcousticMantraSensorProvider = AcousticMantraSensorProvider(context),
    val manualTapProvider: ManualTapMantraProvider = ManualTapMantraProvider(),
    val simulatedProvider: SimulatedMantraProvider = SimulatedMantraProvider()
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Active input provider selection. */
    private val _selectedInputSource = MutableStateFlow(resolveDefaultSource())
    val selectedInputSource: StateFlow<MantraInputSourceType> = _selectedInputSource.asStateFlow()

    /** Authoritative aggregated mantra metrics stream. */
    private val _mantraFlow = MutableStateFlow(MantraUpdate())
    val mantraFlow: StateFlow<MantraUpdate> = _mantraFlow.asStateFlow()

    /** Active configuration governing the current session. */
    private var activeConfig: MantraCounterConfig? = null

    /** Job collecting primitive inputs from the active provider. */
    private var collectorJob: Job? = null

    /** Coroutine job managing the ambient acoustic noise calibration countdown. */
    private var countdownJob: Job? = null

    /** Callbacks for low-latency audio/haptic dispatching. */
    var onBeadRegistered: ((beadCount: Int, targetBeads: Int, malaRound: Int, cadenceCpm: Int) -> Unit)? = null
    var onMilestoneReached: ((beadCount: Int) -> Unit)? = null
    var onSessionCompleted: (() -> Unit)? = null

    init {
        observeActiveSource()
    }

    /**
     * Resolves default input mechanism based on microphone permission availability.
     */
    private fun resolveDefaultSource(): MantraInputSourceType {
        return if (acousticProvider.isAvailable) {
            MantraInputSourceType.ACOUSTIC_MIC
        } else {
            MantraInputSourceType.MANUAL_BEAD_TAP
        }
    }

    /**
     * Rebinds input collectors when the active source selection changes.
     */
    private fun observeActiveSource() {
        collectorJob?.cancel()
        val currentSource = getSourceForType(_selectedInputSource.value)

        collectorJob = scope.launch {
            currentSource.inputFlow.collect { event ->
                handleInputEvent(event)
            }
        }
    }

    /**
     * Returns the concrete [MantraDataSource] corresponding to [type].
     */
    fun getSourceForType(type: MantraInputSourceType): MantraDataSource {
        return when (type) {
            MantraInputSourceType.ACOUSTIC_MIC -> acousticProvider
            MantraInputSourceType.MANUAL_BEAD_TAP -> manualTapProvider
            MantraInputSourceType.SIMULATED -> simulatedProvider
        }
    }

    /**
     * Switches the active input source mechanism.
     */
    fun selectInputSource(type: MantraInputSourceType) {
        if (_selectedInputSource.value == type) return
        val currentSource = getSourceForType(_selectedInputSource.value)
        currentSource.stop()

        _selectedInputSource.value = type
        observeActiveSource()

        activeConfig?.let { config ->
            getSourceForType(type).start(config)
        }
    }

    /**
     * Dynamically updates the microphone detection sensitivity (0.5f to 2.5f).
     *
     * @param sensitivity Multiplier scaling the acoustic trigger threshold.
     */
    fun setMicSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.5f, 2.5f)
        activeConfig = activeConfig?.copy(micSensitivity = clamped)
        acousticProvider.setSensitivity(clamped)
        _mantraFlow.update { it.copy(micSensitivity = clamped) }
    }

    /**
     * Temporarily blanks/mutes acoustic evaluation to prevent loudspeaker audio
     * (interval bells, gongs, vocal prompts) from self-triggering false counts.
     *
     * @param durationMs Blanking duration in milliseconds.
     */
    fun blankAcousticDetection(durationMs: Long) {
        acousticProvider.blankDetection(durationMs)
    }

    /**
     * Ingests primitive events emitted by active data source and advances bead state.
     */
    private fun handleInputEvent(event: MantraInputEvent) {
        val config = activeConfig ?: return
        val currentState = _mantraFlow.value

        if (currentState.isCompleted) return

        // If in ambient calibration phase, gate beads but forward live amplitude/threshold
        if (currentState.isCalibrating) {
            _mantraFlow.update {
                it.copy(
                    audioAmplitudeRms = event.audioAmplitudeRms,
                    thresholdRms = event.thresholdRms
                )
            }
            return
        }

        if (event.beadDelta > 0) {
            val newBead = currentState.currentBead + event.beadDelta
            val newTotal = currentState.totalSessionChants + event.beadDelta

            _mantraFlow.update {
                it.copy(
                    currentBead = newBead,
                    totalSessionChants = newTotal,
                    cadenceCpm = event.instantaneousCadenceCpm,
                    audioAmplitudeRms = event.audioAmplitudeRms,
                    thresholdRms = event.thresholdRms,
                    isReciting = false,
                    activeVerseDurationSeconds = 0f,
                    micSensitivity = activeConfig?.micSensitivity ?: 1.0f,
                    timestampMillis = event.timestampMillis
                )
            }

            Log.i("MantraCountManager", "📿 BEAD REGISTERED: $newBead / ${config.targetBeads} (Mala ${currentState.currentMala}/${config.targetMalas}, Total: $newTotal, Cadence: ${event.instantaneousCadenceCpm} CPM)")
            onBeadRegistered?.invoke(newBead, config.targetBeads, currentState.currentMala, event.instantaneousCadenceCpm)

            // Check half-Mala milestone (e.g. 54 of 108)
            if (config.targetBeads >= 54 && newBead == config.targetBeads / 2) {
                onMilestoneReached?.invoke(newBead)
            }

            // Check if active Mala round target reached
            if (newBead >= config.targetBeads) {
                if (currentState.currentMala < config.targetMalas) {
                    // Advance to next Mala round
                    val nextMala = currentState.currentMala + 1
                    blankAcousticDetection(2000L)
                    _mantraFlow.update {
                        it.copy(
                            currentBead = 0,
                            currentMala = nextMala
                        )
                    }
                } else {
                    // Session fully completed!
                    blankAcousticDetection(8000L)
                    _mantraFlow.update {
                        it.copy(isCompleted = true)
                    }
                    onSessionCompleted?.invoke()
                }
            }
        } else {
            _mantraFlow.update {
                it.copy(
                    audioAmplitudeRms = event.audioAmplitudeRms,
                    thresholdRms = event.thresholdRms,
                    isReciting = event.isSpeechActive,
                    activeVerseDurationSeconds = event.activeVerseDurationSeconds,
                    micSensitivity = activeConfig?.micSensitivity ?: 1.0f
                )
            }
        }
    }

    /**
     * Initiates the 3-second ambient room acoustic noise calibration window during pre-session preparation.
     * Profiles stationary environmental noise (AC, fan, wind) in absolute silence while establishing
     * speech formant trigger thresholds before active recitation counting begins.
     *
     * @param config Active configuration determining target Malas, beads, and sensitivity.
     * @param durationSec Duration of the silent room profiling window in seconds (default 3s).
     */
    fun startPreparationCalibration(config: MantraCounterConfig, durationSec: Int = 3) {
        activeConfig = config
        countdownJob?.cancel()

        if (config.defaultInputMode == MantraInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable) {
            selectInputSource(MantraInputSourceType.ACOUSTIC_MIC)
        } else if (config.defaultInputMode == MantraInputSourceType.MANUAL_BEAD_TAP) {
            selectInputSource(MantraInputSourceType.MANUAL_BEAD_TAP)
        }

        val isAcoustic = _selectedInputSource.value == MantraInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable

        if (isAcoustic) {
            _mantraFlow.value = MantraUpdate(
                currentBead = 0,
                targetBeads = config.targetBeads,
                currentMala = 1,
                targetMalas = config.targetMalas,
                totalSessionChants = 0,
                cadenceCpm = 0,
                audioAmplitudeRms = 0f,
                thresholdRms = 0.05f,
                isReciting = false,
                activeVerseDurationSeconds = 0f,
                micSensitivity = config.micSensitivity,
                isCalibrating = true,
                calibrationSecondsRemaining = durationSec,
                technique = config.technique,
                isCompleted = false
            )

            getSourceForType(_selectedInputSource.value).start(config)

            countdownJob = scope.launch {
                var remaining = durationSec
                while (remaining > 0 && isActive) {
                    delay(1000L)
                    remaining--
                    _mantraFlow.update { it.copy(calibrationSecondsRemaining = remaining) }
                }
                if (isActive) {
                    blankAcousticDetection(500L)
                    _mantraFlow.update {
                        it.copy(
                            isCalibrating = false,
                            calibrationSecondsRemaining = 0
                        )
                    }
                }
            }
        }
    }

    /**
     * Initializes and starts a new mantra recitation session.
     * When using [MantraInputSourceType.ACOUSTIC_MIC], executes an initial 3-second
     * ambient calibration silent pause to profile room noise (AC, fan, wind) and establish
     * dynamic speech formant thresholds before bead counting starts.
     *
     * @param config Active configuration determining target Malas, beads, and sensitivity.
     */
    fun startSession(config: MantraCounterConfig) {
        activeConfig = config

        if (config.defaultInputMode == MantraInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable) {
            selectInputSource(MantraInputSourceType.ACOUSTIC_MIC)
        } else if (config.defaultInputMode == MantraInputSourceType.MANUAL_BEAD_TAP) {
            selectInputSource(MantraInputSourceType.MANUAL_BEAD_TAP)
        }

        val isAcoustic = _selectedInputSource.value == MantraInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable

        // If already calibrated during preparation countdown, continue directly into recitation
        if (isAcoustic && !_mantraFlow.value.isCalibrating && _mantraFlow.value.currentMala == 1 && _mantraFlow.value.currentBead == 0 && _mantraFlow.value.calibrationSecondsRemaining == 0 && _mantraFlow.value.targetBeads == config.targetBeads) {
            Log.i("MantraCountManager", "Session commencing: already pre-calibrated during preparation countdown")
            return
        }
        if (isAcoustic && _mantraFlow.value.isCalibrating) {
            Log.i("MantraCountManager", "Session commencing: room calibration in progress (${_mantraFlow.value.calibrationSecondsRemaining}s remaining)")
            return
        }

        countdownJob?.cancel()

        if (isAcoustic) {
            val calibrationDurationSec = 3
            _mantraFlow.value = MantraUpdate(
                currentBead = 0,
                targetBeads = config.targetBeads,
                currentMala = 1,
                targetMalas = config.targetMalas,
                totalSessionChants = 0,
                cadenceCpm = 0,
                audioAmplitudeRms = 0f,
                thresholdRms = 0.05f,
                isReciting = false,
                activeVerseDurationSeconds = 0f,
                micSensitivity = config.micSensitivity,
                isCalibrating = true,
                calibrationSecondsRemaining = calibrationDurationSec,
                technique = config.technique,
                isCompleted = false
            )

            getSourceForType(_selectedInputSource.value).start(config)

            countdownJob = scope.launch {
                var remaining = calibrationDurationSec
                while (remaining > 0 && isActive) {
                    delay(1000L)
                    remaining--
                    _mantraFlow.update { it.copy(calibrationSecondsRemaining = remaining) }
                }
                if (isActive) {
                    blankAcousticDetection(500L)
                    _mantraFlow.update {
                        it.copy(
                            isCalibrating = false,
                            calibrationSecondsRemaining = 0
                        )
                    }
                }
            }
        } else {
            _mantraFlow.value = MantraUpdate(
                currentBead = 0,
                targetBeads = config.targetBeads,
                currentMala = 1,
                targetMalas = config.targetMalas,
                totalSessionChants = 0,
                cadenceCpm = 0,
                audioAmplitudeRms = 0f,
                thresholdRms = 0.05f,
                isReciting = false,
                activeVerseDurationSeconds = 0f,
                micSensitivity = config.micSensitivity,
                isCalibrating = false,
                calibrationSecondsRemaining = 0,
                technique = config.technique,
                isCompleted = false
            )
            getSourceForType(_selectedInputSource.value).start(config)
        }
    }

    /**
     * Pauses the active counting session.
     */
    fun pauseSession() {
        getSourceForType(_selectedInputSource.value).pause()
    }

    /**
     * Resumes the paused counting session.
     */
    fun resumeSession() {
        getSourceForType(_selectedInputSource.value).resume()
    }

    /**
     * Terminates the active counting session and halts hardware listeners.
     */
    fun stopSession() {
        countdownJob?.cancel()
        getSourceForType(_selectedInputSource.value).stop()
        activeConfig = null
    }

    /**
     * Resets metrics back to clean baseline.
     */
    fun resetSession() {
        countdownJob?.cancel()
        getSourceForType(_selectedInputSource.value).reset()
        _mantraFlow.value = MantraUpdate()
    }

    /**
     * Registers a manual bead trigger (touch tap on screen).
     */
    fun registerManualBead() {
        handleInputEvent(
            MantraInputEvent(
                beadDelta = 1,
                instantaneousCadenceCpm = manualTapProvider.inputFlow.value.instantaneousCadenceCpm,
                audioAmplitudeRms = 0.9f,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    /**
     * Releases all held resources on application shutdown.
     */
    fun destroy() {
        stopSession()
        scope.cancel()
    }
}
