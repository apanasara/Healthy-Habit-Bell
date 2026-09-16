package com.habitbell.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import com.habitbell.app.audio.UnifiedVoiceEngine
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.VoiceCueStyle

/**
 * # PranayamaVoiceGuide
 *
 * Dedicated speech synthesis and auditory guidance engine for classical Pranayama breathwork.
 *
 * ## Architectural Role & Subsystem Relationships
 * - **Audio Subsystem**: Operates within the Central Engine Layer alongside [AudioBellManager] and [BackgroundMusicManager].
 * - **Unified Delegation**: Delegates all voice synthesis and studio asset playback to the process-level
 *   [UnifiedVoiceEngine] ensuring a single TextToSpeech hardware handle and consistent Lata Mangeshkar profile.
 * - **Mastered Melodious Cues**: Delivers studio-mastered authentic Indian female voice cues in a sweet,
 *   high-frequency swara (reminiscent of classical Bollywood vocalists such as Lata Mangeshkar) with unhurried cadence.
 * - **Dynamic Audio Ducking**: Interfaces directly with [BackgroundMusicManager] to smoothly attenuate ambient
 *   background meditation drones during voice cues using raised-cosine crossfades.
 *
 * ## Concurrency & Lifecycle
 * - Thread-safe cue dispatching synchronized with [TimerEngine] phase transitions.
 * - Hardware resources (MediaPlayer, TTS) safely released on [stop] and [shutdown].
 *
 * @param context Component or application context.
 * @param bgMusicManager Reference to ambient music coordinator for volume ducking.
 * @param unifiedVoiceEngine Single authoritative voice engine instance (defaults to newly created or injected).
 */
class PranayamaVoiceGuide(
    private val context: Context,
    private val bgMusicManager: BackgroundMusicManager? = null,
    val unifiedVoiceEngine: UnifiedVoiceEngine = UnifiedVoiceEngine(context, bgMusicManager)
) : TextToSpeech.OnInitListener {

    /** Flag indicating whether the TTS engine is successfully initialized and ready. */
    val isInitialized: Boolean
        get() = unifiedVoiceEngine.isTtsReady

    /** Master toggle governing whether voice guidance cues are spoken. */
    var isVoiceEnabled: Boolean
        get() = unifiedVoiceEngine.isVoiceEnabled
        set(value) { unifiedVoiceEngine.isVoiceEnabled = value }

    /** Active linguistic cue presentation style. Defaults to classical Sanskrit. */
    var cueStyle: VoiceCueStyle
        get() = unifiedVoiceEngine.defaultCueStyle
        set(value) { unifiedVoiceEngine.defaultCueStyle = value }

    override fun onInit(status: Int) {
        // Maintained for backward compatibility
    }

    /**
     * Speaks the guided cue for a newly engaged Pranayama phase with smooth raised-cosine ducking.
     *
     * Prioritizes high-definition studio-mastered audio assets. Automatically falls back
     * to native TTS if asset resolution is unavailable.
     *
     * @param phase The active [PranayamaPhase] beginning execution.
     * @param style Optional override of [VoiceCueStyle]. Defaults to configured [cueStyle].
     * @param isTriBandhaVoiceEnabled Whether to articulate the Tri-Bandha prompt during retention phases.
     * @param volume Subdued voice gain (0.15f..1.0f, default 0.52f).
     * @param stepDurationSeconds Allocated duration of this phase in seconds. Used for smart duration clipping protection.
     */
    fun speakPhaseCue(
        phase: PranayamaPhase,
        style: VoiceCueStyle = cueStyle,
        isTriBandhaVoiceEnabled: Boolean = false,
        volume: Float = 0.52f,
        stepDurationSeconds: Int? = null
    ) {
        unifiedVoiceEngine.speakPranayamaCue(
            phase = phase,
            style = style,
            isTriBandhaVoiceEnabled = isTriBandhaVoiceEnabled,
            volume = volume,
            stepDurationSeconds = stepDurationSeconds
        )
    }

    /**
     * Auditions a sample spoken cue for settings preview based on selected [VoiceCueStyle].
     *
     * @param style Preferred voice cue style to sample. Defaults to active [cueStyle].
     * @param isTriBandhaVoiceEnabled Whether to audition the Tri-Bandha retention cue.
     * @param volume Subdued preview gain (default 0.52f).
     */
    fun auditionCue(
        style: VoiceCueStyle = cueStyle,
        isTriBandhaVoiceEnabled: Boolean = false,
        volume: Float = 0.52f
    ) {
        unifiedVoiceEngine.auditionPranayamaCue(
            style = style,
            isTriBandhaVoiceEnabled = isTriBandhaVoiceEnabled,
            volume = volume
        )
    }

    /**
     * Halts ongoing speech synthesis or audio playback immediately and restores ambient volume.
     */
    fun stop() {
        unifiedVoiceEngine.stop()
    }

    /**
     * Releases system TTS engine and frees audio handles.
     */
    fun shutdown() {
        unifiedVoiceEngine.shutdown()
    }
}
