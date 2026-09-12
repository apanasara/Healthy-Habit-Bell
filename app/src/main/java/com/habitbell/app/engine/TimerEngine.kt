package com.habitbell.app.engine

import android.os.SystemClock
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lifecycle states of an active or inactive wellness timer session.
 */
enum class SessionStatus {
    /** Timer is idle, initialized with a profile, waiting to start. */
    IDLE,

    /** 5-second preparation countdown lead time to lay down phone and take position. */
    PREPARING,

    /** Timer is actively counting down with background heartbeat running. */
    RUNNING,

    /** Timer has been temporarily halted, maintaining remaining time offsets. */
    PAUSED,

    /** Timer has naturally completed its full configured duration or rounds. */
    COMPLETED
}

/**
 * Immutable snapshot representing the complete reactive state of an active timer session.
 *
 * This data structure is observed by UI Composables, Android Auto templates, TV Cast servers,
 * and ongoing Notification channels to render unified countdown timings and visual progress.
 *
 * @property status Current execution state of the session ([SessionStatus.IDLE], [SessionStatus.PREPARING], [SessionStatus.RUNNING], etc.).
 * @property profile Active [TimerProfile] configuration governing duration, intervals, and bells.
 * @property remainingSeconds Total seconds remaining until the entire session completes.
 * @property totalSeconds Total configured duration of the session in seconds.
 * @property nextBellSeconds Seconds remaining until the next interval chime triggers (for linear timers).
 * @property currentRound Current 1-based repetition cycle for multi-interval or compound timers.
 * @property totalRounds Total target repetition cycles configured.
 * @property currentPranayamaPhase Active breathwork phase ([PranayamaPhase.INHALE], [PranayamaPhase.HOLD_IN], etc.) if applicable.
 * @property phaseRemainingSeconds Seconds remaining in the current active breathwork phase.
 * @property phaseDurationSeconds Total duration allocated for the current active breathwork phase in seconds.
 * @property currentPose Active pose step details ([CompoundPose]) for compound sequencers.
 * @property poseRemainingSeconds Seconds remaining in the active compound pose step.
 * @property isVisualAlertActive True during visual alert windows (1s pre-alert and chime duration).
 * @property isDimmed True when session is resting between intervals and screen is auto-dimmed.
 * @property currentSteps Total steps accumulated during active session.
 * @property targetSteps Optional target steps required for session completion.
 * @property nextStepBellSteps Steps remaining until next step interval chime.
 * @property stepCadence Live cadence in steps per minute (SPM).
 * @property isStepTrackingActive Whether step monitoring is active for this session.
 * @property healthProvider Active health platform source providing step metrics.
 * @property preparationSecondsRemaining Seconds remaining in the pre-session preparation countdown (5..0).
 * @property totalPreparationSeconds Total duration configured for pre-session preparation in seconds (default 5s).
 */
data class TimerSessionState(
    val status: SessionStatus = SessionStatus.IDLE,
    val profile: TimerProfile = DefaultProfiles.EATING,
    val remainingSeconds: Int = DefaultProfiles.EATING.totalDurationSeconds,
    val totalSeconds: Int = DefaultProfiles.EATING.totalDurationSeconds,
    val nextBellSeconds: Int = DefaultProfiles.EATING.intervalDurationSeconds,
    // Multi-interval Pranayama tracking
    val currentRound: Int = 1,
    val totalRounds: Int = 1,
    val currentPranayamaPhase: PranayamaPhase? = null,
    val phaseRemainingSeconds: Int = 0,
    val phaseDurationSeconds: Int = 0,
    // Compound Sequencer tracking
    val currentPose: CompoundPose? = null,
    val poseRemainingSeconds: Int = 0,
    // Display Mode auto-dimming and visual alert indicators
    val isVisualAlertActive: Boolean = false,
    val isDimmed: Boolean = false,
    // Step and Health Metrics
    val currentSteps: Int = 0,
    val targetSteps: Int? = null,
    val nextStepBellSteps: Int? = null,
    val stepCadence: Int = 0,
    val isStepTrackingActive: Boolean = false,
    val healthProvider: com.habitbell.app.health.HealthProviderType = com.habitbell.app.health.HealthProviderType.HARDWARE_SENSOR,
    // Pre-session preparation countdown tracking
    val preparationSecondsRemaining: Int = 0,
    val totalPreparationSeconds: Int = 5
) {
    /**
     * Whether the session is currently in the 5-second lead-in preparation countdown.
     */
    val isPreparing: Boolean
        get() = status == SessionStatus.PREPARING
    /**
     * Normalized completion progress ranging from `0.0f` (start) to `1.0f` (complete).
     * Used directly by progress rings, arc canvases, and car dashboard gauges.
     */
    val progressFraction: Float
        get() = if (totalSeconds > 0) {
            1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
        } else 0f

    /**
     * Normalized step completion progress ranging from `0.0f` to `1.0f`.
     */
    val stepProgressFraction: Float?
        get() = targetSteps?.let { target ->
            if (target > 0) (currentSteps.toFloat() / target.toFloat()).coerceIn(0f, 1f) else null
        }

    /**
     * Formatted human-readable remaining time string in `MM:SS` format.
     */
    val formattedRemainingTime: String
        get() {
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            return String.format("%02d:%02d", m, s)
        }

    /**
     * Formatted human-readable countdown string to the next interval bell in `MM:SS` format.
     */
    val formattedNextBellTime: String
        get() {
            val m = nextBellSeconds / 60
            val s = nextBellSeconds % 60
            return String.format("%02d:%02d", m, s)
        }

    /**
     * Formatted human-readable step count display (e.g. "1,250 / 2,000 steps" or "1,250 steps").
     */
    val formattedStepCount: String
        get() = if (targetSteps != null && targetSteps > 0) {
            "%,d / %,d steps".format(currentSteps, targetSteps)
        } else {
            "%,d steps".format(currentSteps)
        }

    /**
     * Formatted human-readable cadence string (e.g. "108 steps/min").
     */
    val formattedCadence: String
        get() = "$stepCadence steps/min"
}

/**
 * Core state machine and execution heartbeat for all wellness timers in Habit Bell.
 *
 * Operates on a battery-optimized 1Hz coroutine tick cycle on [Dispatchers.Default].
 * Handles three distinct timer topologies:
 * 1. **Linear**: Continuous countdown with periodic interval chime triggers (eating, meditation).
 * 2. **Multi-Interval (Pranayama)**: 4-phase cyclic breathwork (Inhale, Hold, Exhale, Hold).
 * 3. **Compound**: Multi-step sequential posture tracking (Yoga sequences, Reiki hand positions).
 *
 * @param audioManager Manager handling sound synthesis and Tibetan bowl audio cues.
 * @param hapticManager Manager generating tactile vibration pulses.
 */
class TimerEngine(
    private val audioManager: AudioBellManager? = null,
    private val hapticManager: HapticManager? = null
) {
    /**
     * External predicate evaluated to verify if Hardware Pocket Mode is currently engaged.
     * Architectural Rule: Haptic vibration cues are strictly restricted to Pocket Mode to maintain
     * silence during ambient and open-air meditation.
     */
    var isPocketModeActive: () -> Boolean = { false }

    /** Voice guidance coordinator articulating gentle vocal cues for Pranayama phase transitions. */
    var voiceGuide: PranayamaVoiceGuide? = null

    /** Voice guidance player articulating classical Asana cues and Solar Mantras for Surya Namaskar. */
    var suryaVoicePlayer: com.habitbell.app.audio.SuryaVoicePlayer? = null

    /** Voice guidance player articulating 5-second countdown cues and strikes before session begins. */
    var preparationVoiceGuide: com.habitbell.app.audio.PreparationVoiceGuide? = null

    /** Toggle determining whether the 5-second preparation countdown is executed before starting. */
    var isPreparationCountdownEnabled: Boolean = true

    /** Configured duration of the preparation lead-in countdown in seconds (default 5s). */
    var preparationCountdownSeconds: Int = 5

    /** Coroutine scope bound to Default dispatcher with a SupervisorJob to prevent cancellation cascading. */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Handle to the active background coroutine job executing the 1Hz ticker loop. */
    private var timerJob: Job? = null

    /** Backing mutable state flow holding the authoritative session state. */
    private val _state = MutableStateFlow(TimerSessionState())

    /** Public read-only stream emitting real-time updates as the timer advances. */
    val state: StateFlow<TimerSessionState> = _state.asStateFlow()

    /** Timestamp recorded via [SystemClock.elapsedRealtime] when session started or resumed. */
    private var sessionStartRealtime: Long = 0L

    /** Accumulated active elapsed realtime prior to pause, used for drift compensation. */
    private var pausedElapsedRealtime: Long = 0L

    /** Steps accumulated at the most recent step interval chime trigger. */
    private var lastStepBellTriggerCount: Int = 0

    /** Remaining seconds until the next bell chime in the current linear interval. */
    private var currentIntervalRemaining: Int = 0

    /** Current step index (0..3) within the active Pranayama breathwork cycle. */
    private var pranayamaStepIndex = 0

    /** Current 1-based round index of the active Pranayama session. */
    private var pranayamaRound = 1

    /** Current pose index within the configured compound sequence. */
    private var compoundPoseIndex = 0

    /** Current 1-based round index of the active compound sequence. */
    private var compoundRound = 1

    /** Countdown ticks keeping the screen un-dimmed during and immediately after bell chimes. */
    private var visualAlertRemainingTicks: Int = 0

    /**
     * Temporarily wakes the screen from its dimmed power-saving state for a brief grace period.
     *
     * @param seconds Number of seconds to keep display un-dimmed before auto-dimming resumes.
     */
    fun wakeScreenTemporarily(seconds: Int = 5) {
        visualAlertRemainingTicks = seconds
        _state.update { it.copy(isVisualAlertActive = true, isDimmed = false) }
    }

    /**
     * Loads a [TimerProfile] into the engine, resetting all internal step counters and
     * initializing state to [SessionStatus.IDLE].
     *
     * @param profile The target timer profile containing durations, interval configs, or step sequences.
     */
    fun loadProfile(profile: TimerProfile) {
        pause()
        visualAlertRemainingTicks = 0
        lastStepBellTriggerCount = 0
        when (profile.type) {
            TimerType.LINEAR -> {
                // Initialize linear countdown parameters
                val total = profile.totalDurationSeconds
                val interval = if (profile.intervalDurationSeconds > 0) profile.intervalDurationSeconds else total
                currentIntervalRemaining = interval
                val stepInterval = profile.stepInterval
                val initialNextStepBell = if (stepInterval != null && stepInterval > 0) stepInterval else null

                _state.value = TimerSessionState(
                    status = SessionStatus.IDLE,
                    profile = profile,
                    remainingSeconds = total,
                    totalSeconds = total,
                    nextBellSeconds = interval,
                    isVisualAlertActive = false,
                    isDimmed = false,
                    currentSteps = 0,
                    targetSteps = profile.stepGoal,
                    nextStepBellSteps = initialNextStepBell,
                    stepCadence = 0,
                    isStepTrackingActive = profile.isStepTrackingEnabled
                )
            }
            TimerType.MULTI_INTERVAL -> {
                // Initialize Pranayama multi-phase breathwork parameters
                val config = profile.pranayamaConfig ?: return
                val activeSteps = config.activeSteps
                val firstStep = activeSteps.firstOrNull() ?: return
                val totalSeconds = activeSteps.sumOf { it.durationSeconds } * config.targetRounds
                pranayamaStepIndex = 0
                pranayamaRound = 1
                _state.value = TimerSessionState(
                    status = SessionStatus.IDLE,
                    profile = profile,
                    remainingSeconds = totalSeconds,
                    totalSeconds = totalSeconds,
                    currentRound = 1,
                    totalRounds = config.targetRounds,
                    currentPranayamaPhase = firstStep.phase,
                    phaseRemainingSeconds = firstStep.durationSeconds,
                    phaseDurationSeconds = firstStep.durationSeconds
                )
            }
            TimerType.COMPOUND -> {
                // Initialize Compound posture sequencer parameters
                val config = profile.compoundConfig ?: return
                val firstPose = config.poses.firstOrNull() ?: return
                val totalSeconds = config.poses.sumOf { it.durationSeconds } * config.targetRounds
                compoundPoseIndex = 0
                compoundRound = 1
                _state.value = TimerSessionState(
                    status = SessionStatus.IDLE,
                    profile = profile,
                    remainingSeconds = totalSeconds,
                    totalSeconds = totalSeconds,
                    currentRound = 1,
                    totalRounds = config.targetRounds,
                    currentPose = firstPose,
                    poseRemainingSeconds = firstPose.durationSeconds
                )
            }
        }
    }

    /**
     * Initiates or resumes countdown execution.
     *
     * If the session is initiated from [SessionStatus.IDLE] and preparation countdown is enabled,
     * it enters [SessionStatus.PREPARING] for 5 seconds with auditory cues ("Take your position", 3, 2, 1)
     * before automatically transitioning to [SessionStatus.RUNNING].
     *
     * @param skipPreparation If true, bypasses the 5-second preparation lead-in and starts running immediately.
     */
    fun startOrResume(skipPreparation: Boolean = false) {
        if (_state.value.status == SessionStatus.RUNNING || _state.value.status == SessionStatus.PREPARING) return
        val wasIdle = _state.value.status == SessionStatus.IDLE
        if (_state.value.status == SessionStatus.COMPLETED) {
            reset()
        }

        if (wasIdle && isPreparationCountdownEnabled && !skipPreparation && preparationCountdownSeconds > 0) {
            startPreparation()
            return
        }

        transitionToRunning(wasIdle)
    }

    /**
     * Initiates the 5-second preparation countdown lead-in with voice guidance and cymbal strikes.
     */
    private fun startPreparation() {
        val totalPrep = preparationCountdownSeconds
        _state.update {
            it.copy(
                status = SessionStatus.PREPARING,
                preparationSecondsRemaining = totalPrep,
                totalPreparationSeconds = totalPrep,
                isDimmed = false,
                isVisualAlertActive = false
            )
        }
        preparationVoiceGuide?.playPreparationCue(totalPrep)

        timerJob?.cancel()
        timerJob = scope.launch {
            var remaining = totalPrep
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _state.update { it.copy(preparationSecondsRemaining = remaining) }
                if (remaining > 0) {
                    preparationVoiceGuide?.playPreparationCue(remaining)
                } else {
                    transitionToRunning(wasIdle = true)
                }
            }
        }
    }

    /**
     * Skips the ongoing preparation countdown and commences active session countdown immediately.
     */
    fun skipPreparation() {
        if (_state.value.status == SessionStatus.PREPARING) {
            timerJob?.cancel()
            transitionToRunning(wasIdle = true)
        }
    }

    /**
     * Transitions from idle or preparation state into [SessionStatus.RUNNING].
     * Triggers opening bell chime for linear sessions, activates posture or breathwork cues,
     * and launches the 1Hz heartbeat tick loop.
     *
     * @param wasIdle True if this is the start of a fresh session rather than resuming from pause.
     */
    private fun transitionToRunning(wasIdle: Boolean) {
        preparationVoiceGuide?.stop()
        _state.update { it.copy(status = SessionStatus.RUNNING, preparationSecondsRemaining = 0) }
        sessionStartRealtime = SystemClock.elapsedRealtime()

        // Ring opening bell for linear timers to mark the commencement of mindful practice
        if (wasIdle && _state.value.profile.type == TimerType.LINEAR) {
            audioManager?.playIntervalBell()
        }

        // If initiating a Pranayama session from start, articulate the initial phase cue
        if (wasIdle && _state.value.profile.type == TimerType.MULTI_INTERVAL) {
            val config = _state.value.profile.pranayamaConfig
            val firstPhase = _state.value.currentPranayamaPhase
            val phaseDuration = _state.value.phaseDurationSeconds
            if (config != null && config.isVoiceGuidanceEnabled && firstPhase != null && !isPocketModeActive()) {
                voiceGuide?.speakPhaseCue(firstPhase, config.voiceCueStyle, config.isTriBandhaVoiceEnabled, config.voiceVolume, phaseDuration)
            }
        }

        // If initiating a Surya Namaskar compound session from start, articulate the initial posture cue
        if (wasIdle && _state.value.profile.type == TimerType.COMPOUND) {
            val config = _state.value.profile.compoundConfig
            val firstPose = _state.value.currentPose
            if (config != null && firstPose != null && !isPocketModeActive()) {
                suryaVoicePlayer?.playPoseCue(firstPose, config.voiceCueMode)
            }
        }

        timerJob?.cancel()
        timerJob = scope.launch {
            // Heartbeat loop optimized for battery conservation: 1Hz tick rate
            while (isActive && _state.value.status == SessionStatus.RUNNING) {
                delay(1000L)
                tickOneSecond()
            }
        }
    }

    /**
     * Pauses the ongoing timer session, halting the ticker loop and cancelling any pending haptic cues.
     */
    fun pause() {
        if (_state.value.status == SessionStatus.RUNNING || _state.value.status == SessionStatus.PREPARING) {
            timerJob?.cancel()
            timerJob = null
            visualAlertRemainingTicks = 0
            hapticManager?.cancel()
            voiceGuide?.stop()
            suryaVoicePlayer?.stop()
            preparationVoiceGuide?.stop()
            _state.update { it.copy(status = SessionStatus.PAUSED, isDimmed = false, isVisualAlertActive = false, preparationSecondsRemaining = 0) }
        }
    }

    /**
     * Stops the active session completely and resets state back to [SessionStatus.IDLE].
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        visualAlertRemainingTicks = 0
        hapticManager?.cancel()
        voiceGuide?.stop()
        suryaVoicePlayer?.stop()
        preparationVoiceGuide?.stop()
        _state.update { it.copy(status = SessionStatus.IDLE, isDimmed = false, isVisualAlertActive = false, preparationSecondsRemaining = 0) }
    }

    /**
     * Terminates all internal coroutines and releases hardware resources.
     * Should be called during ViewModel onCleared or Service destruction.
     */
    fun destroy() {
        timerJob?.cancel()
        timerJob = null
        voiceGuide?.shutdown()
        suryaVoicePlayer?.release()
        preparationVoiceGuide?.shutdown()
        hapticManager?.cancel()
        scope.cancel()
    }

    /**
     * Ingests real-time step count and cadence updates from [com.habitbell.app.health.HealthStepManager].
     *
     * Evaluates:
     * 1. **Step Interval Chime**: Rings Option C 3-bell sequence (or silent 3-pulse tactile vibration in Pocket Mode)
     *    and wakes the display every [TimerProfile.stepInterval] steps.
     * 2. **Step Goal Completion**: Rings Temple Gong completion chime when [TimerProfile.stepGoal] is achieved.
     *
     * @param steps Total steps accumulated strictly within the active walking session.
     * @param cadence Estimated cadence in steps per minute.
     */
    fun onStepCountUpdated(steps: Int, cadence: Int) {
        if (_state.value.status != SessionStatus.RUNNING) return
        val currentProfile = _state.value.profile
        if (!currentProfile.isStepTrackingEnabled) return

        val stepInterval = currentProfile.stepInterval
        var triggerStepBell = false
        var nextStepBell = _state.value.nextStepBellSteps

        // Check periodic step interval chime boundary (e.g. every 500 steps)
        if (stepInterval != null && stepInterval > 0) {
            val stepsSinceLastBell = steps - lastStepBellTriggerCount
            if (stepsSinceLastBell >= stepInterval && steps > 0) {
                triggerStepBell = true
                lastStepBellTriggerCount = (steps / stepInterval) * stepInterval
                nextStepBell = stepInterval
            } else {
                nextStepBell = (stepInterval - (stepsSinceLastBell % stepInterval)).coerceAtLeast(1)
            }
        }

        // Check session completion via step goal (e.g. reaching 2,000 steps)
        val stepGoal = currentProfile.stepGoal
        val isGoalReached = stepGoal != null && stepGoal > 0 && steps >= stepGoal

        _state.update {
            it.copy(
                currentSteps = steps,
                nextStepBellSteps = nextStepBell,
                stepCadence = cadence
            )
        }

        if (isGoalReached && currentProfile.stepTriggerMode != StepTriggerMode.TIME_ONLY) {
            // Target step goal reached: complete session with Temple Gong!
            onSessionCompleted()
            return
        }

        if (triggerStepBell) {
            if (isPocketModeActive()) {
                // Pocket Mode: 3 distinct heavy tactile pulses
                hapticManager?.triggerIntervalHaptic()
            } else {
                // Audible 3-bell sequence + brighten display
                audioManager?.playIntervalBell()
                visualAlertRemainingTicks = 5
            }
        }
    }

    /**
     * Resets the active timer session back to initial values according to the currently assigned profile.
     */
    fun reset() {
        pause()
        loadProfile(_state.value.profile)
    }

    /**
     * Heartbeat handler invoked every 1,000ms by the coroutine loop.
     * Evaluates boundary completion and dispatches to profile-specific topology tick algorithms.
     */
    internal fun tickOneSecond() {
        val current = _state.value
        // If 1 second or less remains in linear mode, evaluate completion semantics
        if (current.profile.type == TimerType.LINEAR && current.remainingSeconds <= 1) {
            if (current.profile.stepTriggerMode == StepTriggerMode.STEPS_ONLY) {
                // In STEPS_ONLY mode, countdown reaches 00:00 but session continues until step goal is met
                _state.update { it.copy(remainingSeconds = 0) }
                return
            }
            onSessionCompleted()
            return
        }

        // Defensive guard: if countdown has elapsed completely, finalize session
        if (current.remainingSeconds <= 0) {
            onSessionCompleted()
            return
        }

        when (current.profile.type) {
            TimerType.LINEAR -> tickLinear()
            TimerType.MULTI_INTERVAL -> tickPranayama()
            TimerType.COMPOUND -> tickCompound()
        }
    }

    /**
     * Advances linear timer countdown and evaluates interval chime boundaries.
     * Enforces the silent Pocket Mode rule (muting audio bell, triggering 3 heavy vibrations)
     * and the Display Mode dimming lifecycle (pre-alert un-dimming at 1s, bright during chime, auto-dimming after chime).
     */
    private fun tickLinear() {
        val newRemaining = _state.value.remainingSeconds - 1
        var nextBell = currentIntervalRemaining - 1
        val inPocket = isPocketModeActive()

        // Check if an interval boundary has been reached
        if (nextBell <= 0) {
            if (inPocket) {
                // Pocket Mode: Absolute public eating silence. DO NOT play audible bell!
                // Trigger 3 heavy vibration pulses
                hapticManager?.triggerIntervalHaptic()
            } else {
                // Open Air / Mobile Display Mode: Play Option C 3-bell sequence
                audioManager?.playIntervalBell()
                // Keep screen bright during 3-bell playback (~4.5s)
                visualAlertRemainingTicks = 5
            }

            // Reset interval countdown
            val interval = _state.value.profile.intervalDurationSeconds
            nextBell = if (interval > 0) interval else newRemaining
            currentIntervalRemaining = nextBell
        } else {
            currentIntervalRemaining = nextBell
        }

        // Determine screen auto-dimming and visual alert state
        val visualAlert: Boolean
        val dimmed: Boolean
        if (visualAlertRemainingTicks > 0) {
            visualAlertRemainingTicks--
            visualAlert = true
            dimmed = false
        } else if (nextBell == 1) {
            // 1 second before interval bell triggers: un-dim screen as visual pre-alert!
            visualAlert = true
            dimmed = false
        } else {
            // Normal resting session countdown: dimmed for dominant battery optimization
            visualAlert = false
            dimmed = true
        }

        _state.update {
            it.copy(
                remainingSeconds = newRemaining,
                nextBellSeconds = nextBell,
                isVisualAlertActive = visualAlert,
                isDimmed = dimmed
            )
        }
    }

    /**
     * Advances Pranayama multi-interval breathwork countdown and manages phase transitions
     * between Inhale (Puraka), Internal Retention (Antar Kumbhaka), Exhale (Rechaka),
     * and External Retention (Bahya Kumbhaka).
     *
     * ## Execution Rules & Boundary Conditions
     * 1. **Zero-Second Step Skipping**: Any breathwork phase with `durationSeconds <= 0` (such as
     *    disabled Antar Kumbhaka or Bahya Kumbhaka) is completely excluded from [PranayamaConfig.activeSteps].
     *    The state machine never visits, delays for, or speaks voice cues for zero-duration steps.
     * 2. **Clean Final-Phase Completion**: When the final active breath phase of the final configured round
     *    concludes (Rechaka if Bahya Kumbhaka is 0s, or Bahya Kumbhaka if > 0s), the session completes
     *    immediately without advancing [pranayamaStepIndex] to 0 or spilling into Puraka.
     * 3. **State Preservation**: On completion, [TimerSessionState.currentPranayamaPhase] remains pinned
     *    to the final completed phase with `phaseRemainingSeconds = 0` and `currentRound == targetRounds`.
     */
    private fun tickPranayama() {
        val config = _state.value.profile.pranayamaConfig ?: return
        val activeSteps = config.activeSteps
        if (activeSteps.isEmpty()) return

        val newPhaseSec = _state.value.phaseRemainingSeconds - 1
        val newRemaining = _state.value.remainingSeconds - 1

        if (newPhaseSec <= 0) {
            val isLastStepInRound = pranayamaStepIndex >= activeSteps.size - 1

            if (isLastStepInRound) {
                // Completed all active breath phases of the current round
                if (pranayamaRound >= config.targetRounds) {
                    // All target practice rounds have finished! Conclude session strictly at the final phase
                    // without resetting to Puraka or incrementing round counter past target.
                    onSessionCompleted()
                    return
                }

                // Advance to the subsequent practice round and reset step pointer to the initial active phase
                pranayamaRound++
                pranayamaStepIndex = 0

                // Milestone interval chime check (chimes every N completed rounds if enabled)
                if (config.isIntervalBellEnabled) {
                    val cadence = config.intervalBellRoundCadence.takeIf { it > 0 } ?: 5
                    if ((pranayamaRound - 1) % cadence == 0) {
                        if (isPocketModeActive()) {
                            hapticManager?.triggerIntervalHaptic()
                        } else {
                            // Play dedicated gentle, non-startling 432 Hz meditative singing bowl
                            audioManager?.playPranayamaIntervalBell()
                            visualAlertRemainingTicks = 5
                        }
                    }
                }
            } else {
                // Advance to the next active sequential breathwork phase within the current round
                pranayamaStepIndex++
            }

            val nextStep = activeSteps[pranayamaStepIndex]
            if (isPocketModeActive()) {
                hapticManager?.triggerBreathPhaseHaptic()
            } else {
                // Articulate gentle lady voice instruction for the upcoming active phase
                if (config.isVoiceGuidanceEnabled) {
                    voiceGuide?.speakPhaseCue(
                        nextStep.phase,
                        config.voiceCueStyle,
                        config.isTriBandhaVoiceEnabled,
                        config.voiceVolume,
                        nextStep.durationSeconds
                    )
                }
            }

            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    currentRound = pranayamaRound,
                    currentPranayamaPhase = nextStep.phase,
                    phaseRemainingSeconds = nextStep.durationSeconds,
                    phaseDurationSeconds = nextStep.durationSeconds,
                    isVisualAlertActive = visualAlertRemainingTicks > 0,
                    isDimmed = visualAlertRemainingTicks == 0
                )
            }
        } else {
            // Decrement active phase countdown with battery-optimized dimming
            val visualAlert: Boolean
            val dimmed: Boolean
            if (visualAlertRemainingTicks > 0) {
                visualAlertRemainingTicks--
                visualAlert = true
                dimmed = false
            } else if (newPhaseSec == 1) {
                // 1 second before next breath phase (Inhale -> Hold -> Exhale): un-dim as visual cue
                visualAlert = true
                dimmed = false
            } else {
                visualAlert = false
                dimmed = true
            }

            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    phaseRemainingSeconds = newPhaseSec,
                    isVisualAlertActive = visualAlert,
                    isDimmed = dimmed
                )
            }
        }
    }

    /**
     * Advances Compound sequence countdown, transitioning through sequential poses
     * and multi-round cycles.
     */
    private fun tickCompound() {
        val config = _state.value.profile.compoundConfig ?: return
        val poses = config.poses
        if (poses.isEmpty()) return

        val newPoseSec = _state.value.poseRemainingSeconds - 1
        val newRemaining = _state.value.remainingSeconds - 1

        if (newPoseSec <= 0) {
            val isLastPoseInRound = compoundPoseIndex >= poses.size - 1

            if (isLastPoseInRound) {
                if (compoundRound >= config.targetRounds) {
                    onSessionCompleted()
                    return
                }

                // Sequence completed one full round; advance to next round
                compoundPoseIndex = 0
                compoundRound++
                audioManager?.playIntervalBell()
                if (isPocketModeActive()) {
                    hapticManager?.triggerIntervalHaptic()
                }
            } else {
                // Advance to the next sequential posture within the current round
                compoundPoseIndex++
                if (isPocketModeActive()) {
                    hapticManager?.triggerBreathPhaseHaptic()
                }
            }

            val nextPose = poses[compoundPoseIndex]
            if (!isPocketModeActive()) {
                suryaVoicePlayer?.playPoseCue(nextPose, config.voiceCueMode)
            }
            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    currentRound = compoundRound,
                    currentPose = nextPose,
                    poseRemainingSeconds = nextPose.durationSeconds,
                    isVisualAlertActive = visualAlertRemainingTicks > 0,
                    isDimmed = visualAlertRemainingTicks == 0
                )
            }
        } else {
            // Decrement active pose countdown with battery-optimized dimming
            val visualAlert: Boolean
            val dimmed: Boolean
            if (visualAlertRemainingTicks > 0) {
                visualAlertRemainingTicks--
                visualAlert = true
                dimmed = false
            } else if (newPoseSec == 1) {
                // 1 second before next posture transition: un-dim as visual cue
                visualAlert = true
                dimmed = false
            } else {
                visualAlert = false
                dimmed = true
            }

            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    poseRemainingSeconds = newPoseSec,
                    isVisualAlertActive = visualAlert,
                    isDimmed = dimmed
                )
            }
        }
    }

    /**
     * Finalizes session completion: halts coroutine ticker, zero-out countdown offsets,
     * and triggers completion feedback (Temple Gong in open air, or silent vibration in Pocket Mode).
     */
    private fun onSessionCompleted() {
        timerJob?.cancel()
        visualAlertRemainingTicks = 0
        suryaVoicePlayer?.stop()
        _state.update {
            it.copy(
                status = SessionStatus.COMPLETED,
                remainingSeconds = 0,
                nextBellSeconds = 0,
                phaseRemainingSeconds = 0,
                poseRemainingSeconds = 0,
                isVisualAlertActive = true,
                isDimmed = false
            )
        }

        val inPocket = isPocketModeActive()
        if (inPocket) {
            // Pocket Mode: Silent completion, distinct sustained vibration pattern
            hapticManager?.triggerCompletionHaptic()
        } else {
            // Open Air / Display Mode: Trigger session completion chime: Deep Resonant Temple Gong
            audioManager?.playCompletionBell()
        }
    }
}
