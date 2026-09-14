package com.habitbell.app.breath

import android.content.Context
import android.util.Log
import com.habitbell.app.data.model.BreathCounterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * # BreathCountManager
 *
 * Authoritative orchestrator and central facade governing breath stroke tracking,
 * acoustic microphone DSP analysis, manual tap inputs, and multi-round state transitions.
 *
 * ## Architectural Role & Relationships
 * - Bound directly to [com.habitbell.app.engine.CentralSessionHandler] alongside `HealthStepManager`.
 * - Coordinates between [AcousticBreathSensorProvider], [ManualTapBreathProvider], and [SimulatedBreathProvider].
 * - Pipes authoritative [BreathStrokeUpdate] metrics into [com.habitbell.app.engine.TimerEngine].
 * - Manages the yogic multi-stage state machine:
 *   `PREPARATION` -> `STROKES` -> `RETENTION_HOLD` (*Antar Kumbhaka*) -> `REST` -> `NEXT ROUND` -> `COMPLETED`.
 *
 * ## Lifecycle & Concurrency
 * - Scoped to process application context on [Dispatchers.Default] + [SupervisorJob].
 * - Emits thread-safe immutable states via Kotlin [StateFlow].
 *
 * @param context Android application context for permissions and hardware access.
 */
class BreathCountManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Hardware-backed acoustic microphone DSP analyzer. */
    val acousticProvider: AcousticBreathSensorProvider = AcousticBreathSensorProvider(context, scope)

    /** Interactive touch-screen manual tap provider. */
    val manualTapProvider: ManualTapBreathProvider = ManualTapBreathProvider()

    /** Deterministic synthetic breath generator for tests and previews. */
    val simulatedProvider: SimulatedBreathProvider = SimulatedBreathProvider(scope)

    /** Active input provider selection. */
    private val _selectedInputSource = MutableStateFlow(resolveDefaultSource())
    val selectedInputSource: StateFlow<BreathInputSourceType> = _selectedInputSource.asStateFlow()

    /** Authoritative aggregated breath metrics stream. */
    private val _strokeFlow = MutableStateFlow(BreathStrokeUpdate())
    val strokeFlow: StateFlow<BreathStrokeUpdate> = _strokeFlow.asStateFlow()

    /** Active configuration governing the current session. */
    private var activeConfig: BreathCounterConfig? = null

    /** Job collecting primitive inputs from the active provider. */
    private var collectorJob: Job? = null

    /** Coroutine job managing timed phase countdowns (Retention hold or Rest). */
    private var countdownJob: Job? = null

    /** Callbacks for low-latency audio/haptic dispatching. */
    var onStrokeRegistered: ((strokeCount: Int, cadenceBpm: Int) -> Unit)? = null
    var onPhaseChanged: ((phase: BreathCounterPhase) -> Unit)? = null
    var onSessionCompleted: (() -> Unit)? = null

    init {
        observeActiveSource()
    }

    /**
     * Resolves default input mechanism based on microphone permission availability.
     */
    private fun resolveDefaultSource(): BreathInputSourceType {
        return if (acousticProvider.isAvailable) {
            BreathInputSourceType.ACOUSTIC_MIC
        } else {
            BreathInputSourceType.MANUAL_TAP
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
     * Returns the concrete [BreathDataSource] corresponding to [type].
     */
    fun getSourceForType(type: BreathInputSourceType): BreathDataSource {
        return when (type) {
            BreathInputSourceType.ACOUSTIC_MIC -> acousticProvider
            BreathInputSourceType.MANUAL_TAP -> manualTapProvider
            BreathInputSourceType.SIMULATED -> simulatedProvider
        }
    }

    /**
     * Switches the active input source mechanism.
     */
    fun selectInputSource(type: BreathInputSourceType) {
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
        _strokeFlow.update { it.copy(micSensitivity = clamped) }
    }

    /**
     * Temporarily blanks/mutes acoustic stroke evaluation to prevent phone speaker audio
     * (interval bells, voice guidance cues, stroke ticks) from self-triggering false strokes.
     *
     * @param durationMs Blanking duration in milliseconds.
     */
    fun blankAcousticDetection(durationMs: Long) {
        acousticProvider.blankDetection(durationMs)
    }

    /**
     * Ingests primitive events emitted by active data source and advances the state machine.
     */
    private fun handleInputEvent(event: BreathInputEvent) {
        val config = activeConfig ?: return
        val currentState = _strokeFlow.value

        // Only register strokes when actively in the STROKES phase
        if (currentState.currentPhase != BreathCounterPhase.STROKES) {
            // Forward audio amplitude and threshold for visual ripples and gauges even during preparation/retention/rest
            _strokeFlow.update {
                it.copy(
                    audioAmplitudeRms = event.audioAmplitudeRms,
                    thresholdRms = event.thresholdRms,
                    isCalibrating = event.isCalibrating
                )
            }
            return
        }

        if (event.strokeDelta > 0) {
            val newRoundStrokes = currentState.currentRoundStrokes + event.strokeDelta
            val newTotalStrokes = currentState.totalSessionStrokes + event.strokeDelta
            val targetForRound = config.strokesForRound(currentState.currentRound)

            _strokeFlow.update {
                it.copy(
                    currentRoundStrokes = newRoundStrokes,
                    totalSessionStrokes = newTotalStrokes,
                    cadenceBpm = event.instantaneousCadenceBpm,
                    audioAmplitudeRms = event.audioAmplitudeRms,
                    thresholdRms = event.thresholdRms,
                    micSensitivity = activeConfig?.micSensitivity ?: 1.0f,
                    humDurationSeconds = event.activeHumDurationSeconds,
                    timestampMillis = event.timestampMillis
                )
            }

            Log.i("BreathCountManager", "🔔 STROKE REGISTERED: $newRoundStrokes / $targetForRound (Total: $newTotalStrokes, Cadence: ${event.instantaneousCadenceBpm} BPM)")
            onStrokeRegistered?.invoke(newRoundStrokes, event.instantaneousCadenceBpm)

            // Check if active round target reached
            if (newRoundStrokes >= targetForRound) {
                advanceFromStrokesPhase(config, currentState.currentRound)
            }
        } else {
            _strokeFlow.update {
                it.copy(
                    audioAmplitudeRms = event.audioAmplitudeRms,
                    thresholdRms = event.thresholdRms,
                    micSensitivity = activeConfig?.micSensitivity ?: 1.0f,
                    humDurationSeconds = event.activeHumDurationSeconds
                )
            }
        }
    }

    /**
     * Transitions from rapid pumping strokes into retention hold (*Antar Kumbhaka*), rest, or next round.
     */
    private fun advanceFromStrokesPhase(config: BreathCounterConfig, currentRound: Int) {
        if (config.retentionSeconds > 0) {
            startRetentionPhase(config, currentRound)
        } else if (config.restSeconds > 0) {
            startRestPhase(config, currentRound)
        } else {
            advanceToNextRound(config, currentRound)
        }
    }

    /**
     * Commences internal breath retention (*Antar Kumbhaka*) countdown.
     */
    private fun startRetentionPhase(config: BreathCounterConfig, currentRound: Int) {
        countdownJob?.cancel()
        blankAcousticDetection(1800L)
        _strokeFlow.update {
            it.copy(
                currentPhase = BreathCounterPhase.RETENTION_HOLD,
                phaseRemainingSeconds = config.retentionSeconds,
                phaseDurationSeconds = config.retentionSeconds
            )
        }
        onPhaseChanged?.invoke(BreathCounterPhase.RETENTION_HOLD)

        countdownJob = scope.launch {
            var remaining = config.retentionSeconds
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _strokeFlow.update { it.copy(phaseRemainingSeconds = remaining) }
            }
            if (isActive) {
                if (config.restSeconds > 0) {
                    startRestPhase(config, currentRound)
                } else {
                    advanceToNextRound(config, currentRound)
                }
            }
        }
    }

    /**
     * Commences passive resting stillness countdown between rounds.
     */
    private fun startRestPhase(config: BreathCounterConfig, currentRound: Int) {
        countdownJob?.cancel()
        blankAcousticDetection(1500L)
        _strokeFlow.update {
            it.copy(
                currentPhase = BreathCounterPhase.REST,
                phaseRemainingSeconds = config.restSeconds,
                phaseDurationSeconds = config.restSeconds
            )
        }
        onPhaseChanged?.invoke(BreathCounterPhase.REST)

        countdownJob = scope.launch {
            var remaining = config.restSeconds
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _strokeFlow.update { it.copy(phaseRemainingSeconds = remaining) }
            }
            if (isActive) {
                advanceToNextRound(config, currentRound)
            }
        }
    }

    /**
     * Advances to the next sequential round, or completes the session if all rounds are finished.
     */
    private fun advanceToNextRound(config: BreathCounterConfig, completedRound: Int) {
        countdownJob?.cancel()
        if (completedRound < config.targetRounds) {
            val nextRound = completedRound + 1
            val nextTarget = config.strokesForRound(nextRound)
            // Blank acoustic detection during the round transition settling interval
            blankAcousticDetection(2500L)
            _strokeFlow.update {
                it.copy(
                    currentRound = nextRound,
                    currentRoundStrokes = 0,
                    targetRoundStrokes = nextTarget,
                    currentPhase = BreathCounterPhase.STROKES,
                    phaseRemainingSeconds = 0,
                    phaseDurationSeconds = 0
                )
            }
            onPhaseChanged?.invoke(BreathCounterPhase.STROKES)
        } else {
            blankAcousticDetection(2500L)
            _strokeFlow.update {
                it.copy(
                    currentPhase = BreathCounterPhase.COMPLETED,
                    phaseRemainingSeconds = 0,
                    phaseDurationSeconds = 0
                )
            }
            onPhaseChanged?.invoke(BreathCounterPhase.COMPLETED)
            onSessionCompleted?.invoke()
        }
    }

    /**
     * Initiates the 3-second ambient room acoustic noise calibration window during pre-session preparation.
     * Profiles stationary environmental noise (AC blower, wind, fan, leaves) in absolute silence
     * while the user settles into posture, ensuring trigger thresholds are locked before active counting begins.
     *
     * @param config Active configuration determining target rounds, strokes, and sensitivity.
     * @param durationSec Duration of the silent room profiling window in seconds (default 3s).
     */
    fun startPreparationCalibration(config: BreathCounterConfig, durationSec: Int = 3) {
        activeConfig = config
        countdownJob?.cancel()

        if (config.defaultInputMode == BreathInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable) {
            selectInputSource(BreathInputSourceType.ACOUSTIC_MIC)
        } else if (config.defaultInputMode == BreathInputSourceType.MANUAL_TAP) {
            selectInputSource(BreathInputSourceType.MANUAL_TAP)
        }

        val initialTarget = config.strokesForRound(1)
        val isAcoustic = _selectedInputSource.value == BreathInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable

        if (isAcoustic) {
            _strokeFlow.value = BreathStrokeUpdate(
                currentRoundStrokes = 0,
                targetRoundStrokes = initialTarget,
                totalSessionStrokes = 0,
                currentRound = 1,
                targetRounds = config.targetRounds,
                cadenceBpm = 0,
                currentPhase = BreathCounterPhase.PREPARATION,
                phaseRemainingSeconds = durationSec,
                phaseDurationSeconds = durationSec,
                thresholdRms = 0.05f,
                micSensitivity = config.micSensitivity,
                isCalibrating = true
            )
            onPhaseChanged?.invoke(BreathCounterPhase.PREPARATION)

            getSourceForType(BreathInputSourceType.ACOUSTIC_MIC).start(config)

            countdownJob = scope.launch {
                var remaining = durationSec
                while (remaining > 0 && isActive) {
                    delay(1000L)
                    remaining--
                    _strokeFlow.update { it.copy(phaseRemainingSeconds = remaining) }
                }
                if (isActive) {
                    blankAcousticDetection(500L)
                    _strokeFlow.update {
                        it.copy(
                            currentPhase = BreathCounterPhase.STROKES,
                            phaseRemainingSeconds = 0,
                            phaseDurationSeconds = 0,
                            isCalibrating = false
                        )
                    }
                    onPhaseChanged?.invoke(BreathCounterPhase.STROKES)
                }
            }
        }
    }

    /**
     * Initializes and starts a new breath counting session.
     *
     * When using [BreathInputSourceType.ACOUSTIC_MIC], executes an initial 3-second
     * [BreathCounterPhase.PREPARATION] silent profiling window to sample environmental background
     * noise (AC blower, wind, fan, leaves) and lock dynamic trigger thresholds before beginning active stroke counting.
     * In [BreathInputSourceType.MANUAL_TAP] mode, begins directly in [BreathCounterPhase.STROKES].
     *
     * @param config Active configuration determining target rounds, strokes, and sensitivity.
     */
    fun startSession(config: BreathCounterConfig) {
        activeConfig = config

        // Apply config's preferred input mode if available
        if (config.defaultInputMode == BreathInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable) {
            selectInputSource(BreathInputSourceType.ACOUSTIC_MIC)
        } else if (config.defaultInputMode == BreathInputSourceType.MANUAL_TAP) {
            selectInputSource(BreathInputSourceType.MANUAL_TAP)
        }

        val initialTarget = config.strokesForRound(1)
        val isAcoustic = _selectedInputSource.value == BreathInputSourceType.ACOUSTIC_MIC && acousticProvider.isAvailable

        // If already calibrated during pre-session preparation, transition directly into STROKES
        if (isAcoustic && !_strokeFlow.value.isCalibrating && _strokeFlow.value.currentPhase == BreathCounterPhase.STROKES) {
            Log.i("BreathCountManager", "Session commencing: already pre-calibrated during preparation countdown")
            return
        }
        if (isAcoustic && _strokeFlow.value.isCalibrating) {
            Log.i("BreathCountManager", "Session commencing: room calibration in progress (${_strokeFlow.value.phaseRemainingSeconds}s remaining)")
            return
        }

        countdownJob?.cancel()

        if (isAcoustic) {
            val calibrationDurationSec = 3
            _strokeFlow.value = BreathStrokeUpdate(
                currentRoundStrokes = 0,
                targetRoundStrokes = initialTarget,
                totalSessionStrokes = 0,
                currentRound = 1,
                targetRounds = config.targetRounds,
                cadenceBpm = 0,
                currentPhase = BreathCounterPhase.PREPARATION,
                phaseRemainingSeconds = calibrationDurationSec,
                phaseDurationSeconds = calibrationDurationSec,
                thresholdRms = 0.05f,
                micSensitivity = config.micSensitivity,
                isCalibrating = true
            )
            onPhaseChanged?.invoke(BreathCounterPhase.PREPARATION)

            getSourceForType(BreathInputSourceType.ACOUSTIC_MIC).start(config)

            countdownJob = scope.launch {
                var remaining = calibrationDurationSec
                while (remaining > 0 && isActive) {
                    delay(1000L)
                    remaining--
                    _strokeFlow.update { it.copy(phaseRemainingSeconds = remaining) }
                }
                if (isActive) {
                    blankAcousticDetection(500L)
                    _strokeFlow.update {
                        it.copy(
                            currentPhase = BreathCounterPhase.STROKES,
                            phaseRemainingSeconds = 0,
                            phaseDurationSeconds = 0,
                            isCalibrating = false
                        )
                    }
                    onPhaseChanged?.invoke(BreathCounterPhase.STROKES)
                }
            }
        } else {
            _strokeFlow.value = BreathStrokeUpdate(
                currentRoundStrokes = 0,
                targetRoundStrokes = initialTarget,
                totalSessionStrokes = 0,
                currentRound = 1,
                targetRounds = config.targetRounds,
                cadenceBpm = 0,
                currentPhase = BreathCounterPhase.STROKES,
                phaseRemainingSeconds = 0,
                phaseDurationSeconds = 0,
                thresholdRms = 0.05f,
                micSensitivity = config.micSensitivity,
                isCalibrating = false
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
        _strokeFlow.value = BreathStrokeUpdate()
    }

    /**
     * Registers a manual stroke trigger (touch tap on screen).
     */
    fun registerManualStroke() {
        // Can always register tap stroke, even if acoustic mic is the primary provider
        handleInputEvent(
            BreathInputEvent(
                strokeDelta = 1,
                instantaneousCadenceBpm = manualTapProvider.inputFlow.value.instantaneousCadenceBpm,
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
