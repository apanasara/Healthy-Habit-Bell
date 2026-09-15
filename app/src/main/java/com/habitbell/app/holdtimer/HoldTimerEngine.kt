package com.habitbell.app.holdtimer

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import com.habitbell.app.data.model.HoldTimerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * # DualCueSpeaker
 *
 * Pluggable speech synthesis contract driving hands-free spoken cues.
 */
interface DualCueSpeaker {
    /**
     * Synthesizes and announces spoken text.
     *
     * @param text Spoken word or phrase.
     * @param speedMultiplier Cadence/rate multiplier (1.0f = normal).
     */
    fun speak(text: String, speedMultiplier: Float = 1.0f)

    /** Halts any currently speaking or queued utterance. */
    fun stop()

    /** Releases underlying platform audio resources. */
    fun release()
}

/**
 * # AndroidDualCueSpeaker
 *
 * Native Android [TextToSpeech] implementation of [DualCueSpeaker].
 *
 * @param context Android context for TTS engine initialization.
 */
class AndroidDualCueSpeaker(context: Context) : DualCueSpeaker, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.05f) // High-clarity pleasant vocal pitch
            isInitialized = true
        } else {
            Log.e("AndroidDualCueSpeaker", "Failed to initialize Android TextToSpeech (status $status)")
        }
    }

    override fun speak(text: String, speedMultiplier: Float) {
        if (!isInitialized) return
        tts?.setSpeechRate(speedMultiplier.coerceIn(0.5f, 2.0f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hold_cue_${System.currentTimeMillis()}")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

/**
 * # HoldTimerEngine
 *
 * Core FSM orchestrator and Dual-Cue countdown scheduler for the Voice-Driven
 * Yoga / Physiotherapy Hold Timer subsystem.
 *
 * ## Architectural Role & Component Relationships
 * - Manages the active hold session coroutine lifecycle.
 * - Coordinates round ordinal announcements, hold count-up cues, and rest countdown cues via [DualCueSpeaker].
 * - Ingests runtime [VoiceHoldCommand] updates from `VoiceHoldTimerParser` to modify parameters on-the-fly.
 * - Enforces clinician safety limits, sounding spoken warnings when hold durations exceed [HoldTimerConfig.maxHoldSec].
 * - Emits reactive [HoldTimerSessionState] telemetry consumed by Jetpack Compose UI.
 *
 * ## Lifecycle & Concurrency
 * State machine runs on [Dispatchers.Default] with monotonic time tracking to prevent background drift.
 *
 * @param speaker Speech synthesis delegate.
 * @param logger Telemetry logging delegate.
 * @param coroutineScope External coroutine scope (defaults to Default dispatcher + SupervisorJob).
 */
class HoldTimerEngine(
    var speaker: DualCueSpeaker? = null,
    val logger: HoldTimerSessionLogger = HoldTimerSessionLogger(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _sessionState = MutableStateFlow(HoldTimerSessionState())
    val sessionState: StateFlow<HoldTimerSessionState> = _sessionState.asStateFlow()

    private var activeConfig: HoldTimerConfig = HoldTimerConfig.DEFAULT_YOGA_PHYSIO
    private var sessionJob: Job? = null

    // Pause / Resume signaling
    private var isPausedInternal = false
    private var skipRequested = false

    /** Tactile tick callback dispatched synchronously on each second. */
    var onHapticTick: (() -> Unit)? = null

    /** Bell sound callback dispatched at round transitions. */
    var onMilestoneBell: (() -> Unit)? = null

    /** Session completion callback. */
    var onSessionCompleted: (() -> Unit)? = null

    /** Data export callback dispatched upon voice intent. */
    var onExportRequested: ((format: String) -> Unit)? = null

    /**
     * Initializes a new session with the given [config] without starting countdown immediately.
     *
     * @param config Target hold timer configuration.
     */
    fun loadConfig(config: HoldTimerConfig) {
        sessionJob?.cancel()
        activeConfig = config
        logger.reset()
        isPausedInternal = false
        skipRequested = false

        val safetyWarning = if (!config.isHoldDurationSafe()) {
            "Warning: hold time (${config.holdDurationSec}s) exceeds clinician limit (${config.maxHoldSec}s)"
        } else {
            null
        }

        _sessionState.value = HoldTimerSessionState(
            phase = HoldTimerPhase.PREPARATION,
            currentRound = 1,
            totalRounds = config.repeatCount,
            currentSecond = 0,
            totalSecondsInPhase = config.holdDurationSec,
            ttsSpeed = config.ttsSpeed,
            isPaused = false,
            safetyWarning = safetyWarning,
            feedbackMessage = "Ready • Say 'Hey Yoga, start' or tap Play"
        )
    }

    /**
     * Commences countdown execution across all configured rounds.
     */
    fun startSession() {
        sessionJob?.cancel()
        sessionJob = coroutineScope.launch {
            runCountdownLoop()
        }
    }

    /**
     * Pauses the ongoing session.
     */
    fun pause() {
        isPausedInternal = true
        _sessionState.update { it.copy(isPaused = true, feedbackMessage = "Session Paused") }
        speaker?.stop()
    }

    /**
     * Resumes the paused session.
     */
    fun resume() {
        isPausedInternal = false
        _sessionState.update { it.copy(isPaused = false, feedbackMessage = "Resuming...") }
    }

    /**
     * Immediately advances to the next round.
     */
    fun skipToNextRound() {
        skipRequested = true
        speaker?.stop()
    }

    /**
     * Cancels and stops the active session.
     */
    fun stop() {
        sessionJob?.cancel()
        speaker?.stop()
        _sessionState.update { it.copy(phase = HoldTimerPhase.COMPLETED, isPaused = false, feedbackMessage = "Session Stopped") }
    }

    /**
     * Updates hold duration on-the-fly during an ongoing session.
     *
     * @param seconds New hold duration in seconds.
     */
    fun updateHoldDuration(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(1)
        val exceedsSafety = safeSeconds > activeConfig.maxHoldSec

        activeConfig = activeConfig.copy(holdDurationSec = safeSeconds)

        val warningMsg = if (exceedsSafety) {
            val warningText = "Warning: hold time exceeds the recommended limit of ${activeConfig.maxHoldSec} seconds"
            speaker?.speak(warningText, activeConfig.ttsSpeed)
            warningText
        } else {
            speaker?.speak("Hold set to $safeSeconds seconds", activeConfig.ttsSpeed)
            null
        }

        _sessionState.update {
            it.copy(
                totalSecondsInPhase = if (it.phase == HoldTimerPhase.HOLD || it.phase == HoldTimerPhase.PREPARATION) safeSeconds else it.totalSecondsInPhase,
                safetyWarning = warningMsg,
                feedbackMessage = "Hold duration adjusted to ${safeSeconds}s"
            )
        }
    }

    /**
     * Updates scheduled repeat count on-the-fly.
     *
     * @param repeats New repeat count.
     */
    fun updateRepeatCount(repeats: Int) {
        val safeRepeats = repeats.coerceAtLeast(1)
        activeConfig = activeConfig.copy(repeatCount = safeRepeats)
        speaker?.speak("Repeats set to $safeRepeats", activeConfig.ttsSpeed)
        _sessionState.update {
            it.copy(
                totalRounds = safeRepeats,
                feedbackMessage = "Rounds set to $safeRepeats"
            )
        }
    }

    /**
     * Adjusts the speech cadence and count pacing adaptively.
     *
     * @param delta Speed multiplier change (e.g. -0.15f for slower, +0.15f for faster).
     */
    fun adjustPace(delta: Float) {
        val currentSpeed = _sessionState.value.ttsSpeed
        val newSpeed = (currentSpeed + delta).coerceIn(0.6f, 1.8f)
        activeConfig = activeConfig.copy(ttsSpeed = newSpeed)

        val message = if (delta < 0) "Pacing slowed to ${String.format(Locale.US, "%.2f", newSpeed)}x" else "Pacing increased to ${String.format(Locale.US, "%.2f", newSpeed)}x"
        speaker?.speak(if (delta < 0) "Slower" else "Faster", newSpeed)

        _sessionState.update {
            it.copy(
                ttsSpeed = newSpeed,
                feedbackMessage = message
            )
        }
    }

    /**
     * Executes an incoming [VoiceHoldCommand].
     *
     * @param command Parsed voice command.
     */
    fun handleVoiceCommand(command: VoiceHoldCommand) {
        _sessionState.update { it.copy(lastRecognizedCommand = command.toString()) }
        when (command) {
            is VoiceHoldCommand.CreateTimer -> {
                loadConfig(command.config)
                startSession()
            }
            is VoiceHoldCommand.AdjustHoldDuration -> updateHoldDuration(command.seconds)
            is VoiceHoldCommand.AdjustRepeatCount -> updateRepeatCount(command.repeats)
            is VoiceHoldCommand.AdjustPace -> adjustPace(command.speedDelta)
            is VoiceHoldCommand.Pause -> pause()
            is VoiceHoldCommand.Resume -> resume()
            is VoiceHoldCommand.NextRound -> skipToNextRound()
            is VoiceHoldCommand.Stop -> stop()
            is VoiceHoldCommand.ExportData -> onExportRequested?.invoke(command.format)
            is VoiceHoldCommand.Unknown -> {
                Log.d("HoldTimerEngine", "Unrecognized voice command: ${command.rawUtterance}")
            }
        }
    }

    /**
     * Internal countdown loop orchestrating rounds, holds, rests, and dual cues.
     */
    private suspend fun runCountdownLoop() {
        // Clinician safety alert at session launch if hold exceeds limit
        if (!activeConfig.isHoldDurationSafe()) {
            val warning = "Warning: hold time exceeds the recommended limit of ${activeConfig.maxHoldSec} seconds"
            speaker?.speak(warning, activeConfig.ttsSpeed)
            delay(2500)
        }

        for (round in 1..activeConfig.repeatCount) {
            if (skipRequested) skipRequested = false

            val roundName = activeConfig.getRoundName(round)
            onMilestoneBell?.invoke()

            // 1. Announce Round Level Cue ("First", "Second", ...)
            speaker?.speak(roundName, activeConfig.ttsSpeed)
            _sessionState.update {
                it.copy(
                    phase = HoldTimerPhase.HOLD,
                    currentRound = round,
                    totalRounds = activeConfig.repeatCount,
                    currentSecond = 0,
                    totalSecondsInPhase = activeConfig.holdDurationSec,
                    feedbackMessage = "$roundName Round • Begin Hold"
                )
            }
            delay(1200)

            // 2. Active Hold Phase Countdown Loop
            var actualHoldSec = 0
            for (sec in 1..activeConfig.holdDurationSec) {
                if (skipRequested) break
                awaitPause()

                val tickStart = SystemClock.elapsedRealtime()
                actualHoldSec = sec
                _sessionState.update { it.copy(currentSecond = sec) }

                // Count out loud: "One", "Two", "Three" ...
                if (activeConfig.isCountAloudEnabled) {
                    speaker?.speak(numberWord(sec), activeConfig.ttsSpeed)
                }
                if (activeConfig.isHapticTickEnabled) {
                    onHapticTick?.invoke()
                }

                // Monotonic drift-compensated sleep
                val targetTickMs = (1000L / activeConfig.ttsSpeed).toLong()
                val elapsed = SystemClock.elapsedRealtime() - tickStart
                val remaining = (targetTickMs - elapsed).coerceAtLeast(10L)
                delay(remaining)
            }

            // 3. Inter-Round Rest Phase (if configured and not last round)
            val restSec = activeConfig.restDurationSec
            if (restSec > 0 && round < activeConfig.repeatCount && !skipRequested) {
                onMilestoneBell?.invoke()
                speaker?.speak("Rest", activeConfig.ttsSpeed)
                _sessionState.update {
                    it.copy(
                        phase = HoldTimerPhase.REST,
                        currentSecond = 0,
                        totalSecondsInPhase = restSec,
                        feedbackMessage = "Rest • Recover"
                    )
                }
                delay(1000)

                for (rSec in 1..restSec) {
                    if (skipRequested) break
                    awaitPause()

                    val tickStart = SystemClock.elapsedRealtime()
                    _sessionState.update { it.copy(currentSecond = rSec) }

                    if (activeConfig.isCountAloudEnabled) {
                        speaker?.speak(numberWord(rSec), activeConfig.ttsSpeed)
                    }
                    if (activeConfig.isHapticTickEnabled) {
                        onHapticTick?.invoke()
                    }

                    val targetTickMs = (1000L / activeConfig.ttsSpeed).toLong()
                    val elapsed = SystemClock.elapsedRealtime() - tickStart
                    val remaining = (targetTickMs - elapsed).coerceAtLeast(10L)
                    delay(remaining)
                }
            }

            // Log completed round telemetry
            logger.logRound(
                roundIndex = round,
                targetHoldSec = activeConfig.holdDurationSec,
                actualHoldSec = actualHoldSec,
                restSec = if (round < activeConfig.repeatCount) restSec else 0,
                speedRating = activeConfig.ttsSpeed
            )
        }

        // Conclude Session
        onMilestoneBell?.invoke()
        speaker?.speak("Session complete", activeConfig.ttsSpeed)
        _sessionState.update {
            it.copy(
                phase = HoldTimerPhase.COMPLETED,
                currentSecond = it.totalSecondsInPhase,
                feedbackMessage = "Practice Complete • Well Done"
            )
        }
        onSessionCompleted?.invoke()
    }

    private suspend fun awaitPause() {
        while (isPausedInternal) {
            delay(100)
        }
    }

    /**
     * Converts a numeric second count into spoken English number words (1..100).
     */
    private fun numberWord(number: Int): String {
        val ones = arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
        )
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

        return when {
            number in 1..19 -> ones[number]
            number in 20..99 -> {
                val tenPart = tens[number / 10]
                val onePart = ones[number % 10]
                if (onePart.isEmpty()) tenPart else "$tenPart $onePart"
            }
            number == 100 -> "one hundred"
            else -> number.toString()
        }
    }
}
