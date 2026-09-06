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
 * @property status Current execution state of the session ([SessionStatus.IDLE], [SessionStatus.RUNNING], etc.).
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
    val healthProvider: com.habitbell.app.health.HealthProviderType = com.habitbell.app.health.HealthProviderType.HARDWARE_SENSOR
) {
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
    private val audioManager: AudioBellManager,
    private val hapticManager: HapticManager
) {
    /**
     * External predicate evaluated to verify if Hardware Pocket Mode is currently engaged.
     * Architectural Rule: Haptic vibration cues are strictly restricted to Pocket Mode to maintain
     * silence during ambient and open-air meditation.
     */
    var isPocketModeActive: () -> Boolean = { false }

    /** Voice guidance coordinator articulating gentle vocal cues for Pranayama phase transitions. */
    var voiceGuide: PranayamaVoiceGuide? = null

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
                val firstStep = config.steps.firstOrNull() ?: return
                val totalSeconds = config.steps.sumOf { it.durationSeconds } * config.targetRounds
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
     * Launches a non-blocking coroutine ticker on [Dispatchers.Default] pulsing once every 1,000 milliseconds.
     * If the session was previously completed, it resets to the beginning before starting.
     */
    fun startOrResume() {
        if (_state.value.status == SessionStatus.RUNNING) return
        val wasIdle = _state.value.status == SessionStatus.IDLE
        if (_state.value.status == SessionStatus.COMPLETED) {
            reset()
        }

        _state.update { it.copy(status = SessionStatus.RUNNING) }
        sessionStartRealtime = SystemClock.elapsedRealtime()

        // If initiating a Pranayama session from start, articulate the initial phase cue
        if (wasIdle && _state.value.profile.type == TimerType.MULTI_INTERVAL) {
            val config = _state.value.profile.pranayamaConfig
            val firstPhase = _state.value.currentPranayamaPhase
            if (config != null && config.isVoiceGuidanceEnabled && firstPhase != null && !isPocketModeActive()) {
                voiceGuide?.speakPhaseCue(firstPhase, config.voiceCueStyle)
            }
        }

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
        if (_state.value.status == SessionStatus.RUNNING) {
            timerJob?.cancel()
            timerJob = null
            visualAlertRemainingTicks = 0
            hapticManager.cancel()
            voiceGuide?.stop()
            _state.update { it.copy(status = SessionStatus.PAUSED, isDimmed = false, isVisualAlertActive = false) }
        }
    }

    /**
     * Stops the active session completely and resets state back to [SessionStatus.IDLE].
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
        visualAlertRemainingTicks = 0
        hapticManager.cancel()
        voiceGuide?.stop()
        _state.update { it.copy(status = SessionStatus.IDLE, isDimmed = false, isVisualAlertActive = false) }
    }

    /**
     * Terminates all internal coroutines and releases hardware resources.
     * Should be called during ViewModel onCleared or Service destruction.
     */
    fun destroy() {
        timerJob?.cancel()
        timerJob = null
        hapticManager.cancel()
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
                hapticManager.triggerIntervalHaptic()
            } else {
                // Audible 3-bell sequence + brighten display
                audioManager.playIntervalBell()
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
    private fun tickOneSecond() {
        val current = _state.value
        // If 1 second or less remains, evaluate completion semantics
        if (current.remainingSeconds <= 1) {
            if (current.profile.stepTriggerMode == StepTriggerMode.STEPS_ONLY) {
                // In STEPS_ONLY mode, countdown reaches 00:00 but session continues until step goal is met
                _state.update { it.copy(remainingSeconds = 0) }
                return
            }
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
                hapticManager.triggerIntervalHaptic()
            } else {
                // Open Air / Mobile Display Mode: Play Option C 3-bell sequence
                audioManager.playIntervalBell()
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
     * between Inhale, Retention, Exhale, and Empty Hold.
     */
    private fun tickPranayama() {
        val config = _state.value.profile.pranayamaConfig ?: return
        val newPhaseSec = _state.value.phaseRemainingSeconds - 1
        val newRemaining = _state.value.remainingSeconds - 1

        if (newPhaseSec <= 0) {
            // Advance to the subsequent breathwork phase
            pranayamaStepIndex++
            var completedFullCycle = false
            if (pranayamaStepIndex >= config.steps.size) {
                // Completed one full breath cycle (all 4 phases)
                pranayamaStepIndex = 0
                pranayamaRound++
                completedFullCycle = true
            }

            // Verify if target repetition rounds have been reached
            if (pranayamaRound > config.targetRounds) {
                onSessionCompleted()
                return
            }

            // Milestone interval chime check! (chimes every N completed rounds if enabled)
            if (completedFullCycle && config.isIntervalBellEnabled) {
                val cadence = config.intervalBellRoundCadence.takeIf { it > 0 } ?: 5
                if ((pranayamaRound - 1) % cadence == 0) {
                    if (isPocketModeActive()) {
                        hapticManager.triggerIntervalHaptic()
                    } else {
                        // Play dedicated gentle, non-startling 432 Hz meditative singing bowl
                        audioManager.playPranayamaIntervalBell()
                        visualAlertRemainingTicks = 5
                    }
                }
            }

            val nextStep = config.steps[pranayamaStepIndex]
            if (isPocketModeActive()) {
                hapticManager.triggerBreathPhaseHaptic()
            } else {
                // Articulate gentle lady voice instruction for the upcoming phase
                if (config.isVoiceGuidanceEnabled) {
                    voiceGuide?.speakPhaseCue(nextStep.phase, config.voiceCueStyle)
                }
            }

            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    currentRound = pranayamaRound,
                    currentPranayamaPhase = nextStep.phase,
                    phaseRemainingSeconds = nextStep.durationSeconds,
                    phaseDurationSeconds = nextStep.durationSeconds
                )
            }
        } else {
            // Decrement active phase countdown
            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    phaseRemainingSeconds = newPhaseSec
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
        val newPoseSec = _state.value.poseRemainingSeconds - 1
        val newRemaining = _state.value.remainingSeconds - 1

        if (newPoseSec <= 0) {
            // Advance to the next sequential posture
            compoundPoseIndex++
            if (compoundPoseIndex >= config.poses.size) {
                // Sequence completed one full round
                compoundPoseIndex = 0
                compoundRound++
                audioManager.playIntervalBell()
                if (isPocketModeActive()) {
                    hapticManager.triggerIntervalHaptic()
                }
            } else {
                if (isPocketModeActive()) {
                    hapticManager.triggerBreathPhaseHaptic()
                }
            }

            // Verify if sequence target rounds have finished
            if (compoundRound > config.targetRounds) {
                onSessionCompleted()
                return
            }

            val nextPose = config.poses[compoundPoseIndex]
            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    currentRound = compoundRound,
                    currentPose = nextPose,
                    poseRemainingSeconds = nextPose.durationSeconds
                )
            }
        } else {
            // Decrement active pose countdown
            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    poseRemainingSeconds = newPoseSec
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
            hapticManager.triggerCompletionHaptic()
        } else {
            // Open Air / Display Mode: Trigger session completion chime: Deep Resonant Temple Gong
            audioManager.playCompletionBell()
        }
    }
}
