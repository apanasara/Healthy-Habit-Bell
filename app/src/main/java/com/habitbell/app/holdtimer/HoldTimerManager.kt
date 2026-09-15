package com.habitbell.app.holdtimer

import android.content.Context
import com.habitbell.app.data.model.HoldTimerConfig
import com.habitbell.app.engine.AudioBellManager
import com.habitbell.app.engine.HapticManager
import kotlinx.coroutines.flow.StateFlow

/**
 * # HoldTimerManager
 *
 * Authoritative facade and coordinator governing the Voice-Driven Yoga & Physiotherapy
 * Hold Timer subsystem in Habit Bell.
 *
 * ## Architectural Role & Component Relationships
 * - Bound directly to [com.habitbell.app.engine.CentralSessionHandler] alongside `BreathCountManager` and `MantraCountManager`.
 * - Bridges [HoldTimerEngine] FSM, [VoiceCommandSource] hands-free microphone input,
 *   [HoldTimerSessionLogger] telemetry, [AudioBellManager] procedural chimes, and [HapticManager] vibration waveforms.
 * - Exposes the reactive [HoldTimerSessionState] [StateFlow] consumed by `HabitBellViewModel` and `HoldTimerScreen`.
 *
 * ## Lifecycle & Concurrency
 * Bound to the application context lifecycle. Thread-safe across UI and background coroutine dispatchers.
 *
 * @param context Android context for TTS, speech recognizer, and haptics (optional in unit tests).
 * @param engine Core countdown and dual-cue scheduling engine.
 * @param voiceSource Hands-free speech recognition source.
 * @param hapticManager Hardware haptic vibration coordinator.
 * @param bellManager Procedural acoustic bell and chime coordinator.
 */
class HoldTimerManager(
    private val context: Context? = null,
    val bgMusicManager: com.habitbell.app.engine.BackgroundMusicManager? = null,
    val engine: HoldTimerEngine = HoldTimerEngine(
        speaker = context?.let { AndroidDualCueSpeaker(it, bgMusicManager) }
    ),
    val voiceSource: VoiceCommandSource = context?.let { AndroidSpeechCommandSource(it) } ?: SimulatedVoiceCommandSource(),
    private val hapticManager: HapticManager? = context?.let { HapticManager(it) },
    private val bellManager: AudioBellManager? = null
) {

    /** Authoritative reactive session state stream. */
    val sessionState: StateFlow<HoldTimerSessionState> = engine.sessionState

    /** Session telemetry logger instance. */
    val logger: HoldTimerSessionLogger = engine.logger

    init {
        // Wire voice command recognition into engine
        voiceSource.onCommandRecognized = { command ->
            engine.handleVoiceCommand(command)
        }

        // Wire haptic pulse on each second tick
        engine.onHapticTick = {
            hapticManager?.triggerStrokeHaptic()
        }

        // Wire bell on round transitions
        engine.onMilestoneBell = {
            bellManager?.playIntervalBell()
        }
    }

    /**
     * Prepares and initializes a hold timer session with the target [config].
     *
     * @param config Target hold timer timing and safety configuration.
     * @param enableVoiceListener Whether hands-free microphone speech recognition should start immediately.
     */
    fun initializeSession(config: HoldTimerConfig, enableVoiceListener: Boolean = true) {
        engine.loadConfig(config)
        if (enableVoiceListener) {
            voiceSource.startListening()
        }
    }

    /**
     * Begins timer countdown execution.
     */
    fun startSession() {
        engine.startSession()
    }

    /** Pauses countdown. */
    fun pause() {
        engine.pause()
    }

    /** Resumes countdown. */
    fun resume() {
        engine.resume()
    }

    /** Advances immediately to the next round. */
    fun skipToNextRound() {
        engine.skipToNextRound()
    }

    /** Stops and finishes session. */
    fun stop() {
        voiceSource.stopListening()
        engine.stop()
    }

    /** Resets session state back to preparation. */
    fun reset() {
        engine.reset()
    }

    /** Updates active hold duration in seconds. */
    fun updateHoldDuration(seconds: Int) {
        engine.updateHoldDuration(seconds)
    }

    /** Updates active rest duration in seconds. */
    fun updateRestDuration(seconds: Int) {
        engine.updateRestDuration(seconds)
    }

    /** Updates target repeat rounds. */
    fun updateRepeatCount(repeats: Int) {
        engine.updateRepeatCount(repeats)
    }

    /** Updates voice speech rate multiplier. */
    fun updateVoiceSpeed(speed: Float) {
        engine.updateVoiceSpeed(speed)
    }

    /** Returns current active hold timer configuration. */
    fun getActiveConfig(): HoldTimerConfig = engine.getActiveConfig()

    /** Releases all native resources. */
    fun release() {
        voiceSource.release()
        engine.speaker?.release()
    }
}
