package com.habitbell.app.engine

import android.os.SystemClock
import com.habitbell.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SessionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

data class TimerSessionState(
    val status: SessionStatus = SessionStatus.IDLE,
    val profile: TimerProfile = DefaultProfiles.EATING,
    val remainingSeconds: Int = 1200,
    val totalSeconds: Int = 1200,
    val nextBellSeconds: Int = 30,
    val currentRound: Int = 1,
    val totalRounds: Int = 1,
    // Pranayama specific
    val currentPranayamaPhase: PranayamaPhase? = null,
    val phaseRemainingSeconds: Int = 0,
    val phaseDurationSeconds: Int = 0,
    // Compound / Surya Namaskar specific
    val currentPose: CompoundPose? = null,
    val poseRemainingSeconds: Int = 0
) {
    val progressFraction: Float
        get() = if (totalSeconds > 0) {
            1.0f - (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedRemainingTime: String
        get() {
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            return String.format("%02d:%02d", m, s)
        }

    val formattedNextBellTime: String
        get() {
            val m = nextBellSeconds / 60
            val s = nextBellSeconds % 60
            return String.format("%02d:%02d", m, s)
        }
}

class TimerEngine(
    private val audioManager: AudioBellManager,
    private val hapticManager: HapticManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(TimerSessionState())
    val state: StateFlow<TimerSessionState> = _state.asStateFlow()

    private var sessionStartRealtime: Long = 0L
    private var pausedElapsedRealtime: Long = 0L

    // For linear interval tracking
    private var currentIntervalRemaining: Int = 0

    // For Pranayama multi-interval
    private var pranayamaStepIndex = 0
    private var pranayamaRound = 1

    // For Compound sequencer
    private var compoundPoseIndex = 0
    private var compoundRound = 1

    fun loadProfile(profile: TimerProfile) {
        pause()
        when (profile.type) {
            TimerType.LINEAR -> {
                val total = profile.totalDurationSeconds
                val interval = if (profile.intervalDurationSeconds > 0) profile.intervalDurationSeconds else total
                currentIntervalRemaining = interval
                _state.value = TimerSessionState(
                    status = SessionStatus.IDLE,
                    profile = profile,
                    remainingSeconds = total,
                    totalSeconds = total,
                    nextBellSeconds = interval
                )
            }
            TimerType.MULTI_INTERVAL -> {
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

    fun startOrResume() {
        if (_state.value.status == SessionStatus.RUNNING) return
        if (_state.value.status == SessionStatus.COMPLETED) {
            reset()
        }

        _state.update { it.copy(status = SessionStatus.RUNNING) }
        sessionStartRealtime = SystemClock.elapsedRealtime()

        timerJob = scope.launch {
            while (isActive && _state.value.status == SessionStatus.RUNNING) {
                delay(1000L) // 1Hz battery-optimized heartbeat
                tickOneSecond()
            }
        }
    }

    fun pause() {
        if (_state.value.status == SessionStatus.RUNNING) {
            timerJob?.cancel()
            _state.update { it.copy(status = SessionStatus.PAUSED) }
        }
    }

    fun reset() {
        pause()
        loadProfile(_state.value.profile)
    }

    private fun tickOneSecond() {
        val current = _state.value
        if (current.remainingSeconds <= 1) {
            onSessionCompleted()
            return
        }

        when (current.profile.type) {
            TimerType.LINEAR -> tickLinear()
            TimerType.MULTI_INTERVAL -> tickPranayama()
            TimerType.COMPOUND -> tickCompound()
        }
    }

    private fun tickLinear() {
        val newRemaining = _state.value.remainingSeconds - 1
        var nextBell = currentIntervalRemaining - 1

        if (nextBell <= 0) {
            // Trigger interval bell & haptic!
            audioManager.playIntervalBell()
            hapticManager.triggerIntervalHaptic()
            val interval = _state.value.profile.intervalDurationSeconds
            nextBell = if (interval > 0) interval else newRemaining
            currentIntervalRemaining = nextBell
        } else {
            currentIntervalRemaining = nextBell
        }

        _state.update {
            it.copy(
                remainingSeconds = newRemaining,
                nextBellSeconds = nextBell
            )
        }
    }

    private fun tickPranayama() {
        val config = _state.value.profile.pranayamaConfig ?: return
        val currentStep = config.steps[pranayamaStepIndex]
        val newPhaseSec = _state.value.phaseRemainingSeconds - 1
        val newRemaining = _state.value.remainingSeconds - 1

        if (newPhaseSec <= 0) {
            // Move to next breath phase
            pranayamaStepIndex++
            if (pranayamaStepIndex >= config.steps.size) {
                pranayamaStepIndex = 0
                pranayamaRound++
            }

            if (pranayamaRound > config.targetRounds) {
                onSessionCompleted()
                return
            }

            val nextStep = config.steps[pranayamaStepIndex]
            hapticManager.triggerBreathPhaseHaptic()

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
            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    phaseRemainingSeconds = newPhaseSec
                )
            }
        }
    }

    private fun tickCompound() {
        val config = _state.value.profile.compoundConfig ?: return
        val newPoseSec = _state.value.poseRemainingSeconds - 1
        val newRemaining = _state.value.remainingSeconds - 1

        if (newPoseSec <= 0) {
            // Transition to next yoga pose
            compoundPoseIndex++
            if (compoundPoseIndex >= config.poses.size) {
                compoundPoseIndex = 0
                compoundRound++
                // Interval bell on round completion
                audioManager.playIntervalBell()
                hapticManager.triggerIntervalHaptic()
            } else {
                hapticManager.triggerBreathPhaseHaptic()
            }

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
            _state.update {
                it.copy(
                    remainingSeconds = newRemaining,
                    poseRemainingSeconds = newPoseSec
                )
            }
        }
    }

    private fun onSessionCompleted() {
        timerJob?.cancel()
        _state.update {
            it.copy(
                status = SessionStatus.COMPLETED,
                remainingSeconds = 0,
                nextBellSeconds = 0,
                phaseRemainingSeconds = 0,
                poseRemainingSeconds = 0
            )
        }
        // Three bells at completion + 3 vibrations (matching PRD!)
        audioManager.playCompletionBell()
        hapticManager.triggerCompletionHaptic()
    }
}
