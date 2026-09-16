/**
 * # PreparationVoiceGuide
 *
 * Dedicated audio coordinator and vocal guidance engine for the 5-second session preparation countdown.
 *
 * ## Architectural Role & Component Relationships
 * Audio subsystem component located in `com.habitbell.app.audio`:
 * - Coordinates with [UnifiedVoiceEngine] as a lightweight facade for speech and studio playback.
 * - Coordinates with [com.habitbell.app.engine.BackgroundMusicManager] for smooth raised-cosine audio ducking.
 * - Coordinates with [com.habitbell.app.engine.AudioBellManager] for synchronized crystalline tingsha cymbal strikes (3, 2, 1).
 * - Delivers studio-mastered female vocal cues (`hi-IN-SwaraNeural`, Lata Mangeshkar profile) via [android.media.MediaPlayer].
 * - Gracefully falls back to native Android [android.speech.tts.TextToSpeech] if raw assets are unavailable.
 * - Bound directly to [com.habitbell.app.engine.TimerEngine] and [com.habitbell.app.engine.CentralSessionHandler].
 *
 * ## Acoustic Profile & Studio Directives
 * - Engine: Microsoft Natural Neural `hi-IN-SwaraNeural` (+52Hz pitch elevation, unhurried cadence).
 * - Lead Delay: 120ms anti-startle grace period after background music ducking begins.
 * - Playback Gain: Subdued whisper-level gain (`0.52f` default, range 0.15f..1.0f).
 * - Background Ducking: Duck to `0.20f` volume over 350ms, restored over 500ms.
 *
 * ## Concurrency & Thread Safety
 * Speech synthesis callbacks and MediaPlayer lifecycles execute safely on [kotlinx.coroutines.Dispatchers.Main].
 */
package com.habitbell.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import com.habitbell.app.engine.AudioBellManager
import com.habitbell.app.engine.BackgroundMusicManager

/**
 * Audio coordinator for 5-second pre-session preparation countdown cues and cymbal strikes.
 *
 * @param context Android component or application context.
 * @param bgMusicManager Ambient music coordinator for volume ducking.
 * @param audioBellManager Meditative bell manager for countdown strikes.
 * @param unifiedVoiceEngine Single authoritative voice engine instance (defaults to newly created or injected).
 */
class PreparationVoiceGuide(
    private val context: Context,
    private val bgMusicManager: BackgroundMusicManager? = null,
    private val audioBellManager: AudioBellManager? = null,
    val unifiedVoiceEngine: UnifiedVoiceEngine = UnifiedVoiceEngine(context, bgMusicManager)
) : TextToSpeech.OnInitListener {

    /** Flag indicating whether the TextToSpeech engine is successfully initialized. */
    val isTtsReady: Boolean
        get() = unifiedVoiceEngine.isTtsReady

    /** Master toggle governing whether preparation voice guidance cues are articulated. */
    var isVoiceEnabled: Boolean
        get() = unifiedVoiceEngine.isVoiceEnabled
        set(value) { unifiedVoiceEngine.isVoiceEnabled = value }

    /** Master gain factor for preparation voice cue playback (normalized 0.15f..1.0f). Default 0.52f. */
    var voiceVolume: Float
        get() = unifiedVoiceEngine.masterVoiceVolume
        set(value) { unifiedVoiceEngine.masterVoiceVolume = value }

    /**
     * Master toggle indicating whether room acoustic noise calibration is active during preparation.
     * When true, all countdown chime strikes (3, 2, 1) and spoken numeric cues ("Three", "Two", "One")
     * are strictly silenced during seconds 1..3 to prevent loudspeaker self-feedback from corrupting
     * the ambient microphone noise floor measurement. The initial vocal cue at T=5s ("Take your position")
     * is preserved.
     */
    var isAcousticCalibrationActive: Boolean = false

    override fun onInit(status: Int) {
        // Maintained for backward compatibility
    }

    /**
     * Plays the appropriate vocal cue and chime strike for the given countdown second.
     *
     * Cues:
     * - `5`: "Take your position"
     * - `3`: "Three" + Option C strike 3 chime
     * - `2`: "Two" + Option C strike 2 chime
     * - `1`: "One" + Option C strike 1 chime
     *
     * @param secondsRemaining Preparation countdown seconds remaining (1..5).
     */
    fun playPreparationCue(secondsRemaining: Int) {
        unifiedVoiceEngine.playPreparationCue(
            secondsRemaining = secondsRemaining,
            isAcousticCalibrationActive = isAcousticCalibrationActive,
            audioBellManager = audioBellManager,
            volume = voiceVolume
        )
    }

    /**
     * Halts any active preparation playback, cancels coroutine jobs, and restores ambient music volume.
     */
    fun stop() {
        isAcousticCalibrationActive = false
        unifiedVoiceEngine.stop()
    }

    /**
     * Releases hardware and TTS resources upon process or component teardown.
     */
    fun shutdown() {
        unifiedVoiceEngine.shutdown()
    }
}
