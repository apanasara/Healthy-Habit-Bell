/**
 * # SuryaVoicePlayer
 *
 * Coordinates audio playback and speech synthesis of voice cues for the Surya Namaskar sequencer.
 *
 * ## Architectural Role & Component Relationships
 * Audio subsystem component interfacing with Android MediaPlayer, TextToSpeech, and the process-level
 * BackgroundMusicManager:
 * - Delegates all vocal guidance and audio synthesis to the process-level [UnifiedVoiceEngine].
 * - Bound to [com.habitbell.app.engine.CentralSessionHandler] and [com.habitbell.app.engine.TimerEngine].
 * - Supports 4 distinct voice guidance modes: [VoiceCueMode.NONE], [VoiceCueMode.PRANIC],
 *   [VoiceCueMode.STEP_NAME], and [VoiceCueMode.SLOKA].
 * - Prioritizes high-definition studio-mastered audio clips (Edge-TTS `hi-IN-SwaraNeural`, Lata Mangeshkar tone)
 *   from `res/raw`, gracefully falling back to native Android TextToSpeech if an asset is unavailable.
 *
 * ## Acoustic Profile & Studio Standard
 * - Voice Profile: hi-IN-SwaraNeural (+52Hz pitch shift, unhurried -30% yogic cadence, Lata Mangeshkar meditative timbre)
 * - Lead Delay: 120ms anti-startle grace period after background music ducking begins before playback starts
 * - Audio Ducking: 0.20f target volume factor over 350ms, smoothly restored over 500ms
 * - Playback Gain: Subdued whisper-soft volume (0.52f default)
 *
 * ## Concurrency & Thread Safety
 * Speech dispatch and MediaPlayer actions run on the Main coroutine scope.
 */
package com.habitbell.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.annotation.RawRes
import com.habitbell.app.data.model.CompoundPose
import com.habitbell.app.engine.BackgroundMusicManager

/**
 * Enum representing the supported voice-cue modes for Surya Namaskar sequences.
 *
 * @property mode Numeric identifier persisted in SQLite Room database.
 */
enum class VoiceCueMode(val mode: Int) {
    NONE(0),
    PRANIC(1),
    STEP_NAME(2),
    SLOKA(3);

    /**
     * User-facing localized display name for UI chips, dialogs, and badges.
     */
    val displayName: String
        get() = when (this) {
            NONE -> "Silent / Bell"
            PRANIC -> "Breath Flow"
            STEP_NAME -> "Asana Name"
            SLOKA -> "Solar Mantra"
        }
}

/**
 * Voice cue player coordinating audio ducking, studio MediaPlayer playback, and TextToSpeech fallback.
 *
 * Delegates all vocal guidance and audio synthesis to the process-level [UnifiedVoiceEngine].
 *
 * @param context Android context used to access system speech engines and audio resources.
 * @param backgroundMusicManager Manager controlling ambient background music ducking.
 * @param unifiedVoiceEngine Single authoritative voice engine instance.
 */
class SuryaVoicePlayer(
    private val context: Context,
    private val backgroundMusicManager: BackgroundMusicManager,
    val unifiedVoiceEngine: UnifiedVoiceEngine = UnifiedVoiceEngine(context, backgroundMusicManager)
) : TextToSpeech.OnInitListener {

    override fun onInit(status: Int) {
        // Maintained for backward compatibility
    }

    /**
     * Dispatches voice guidance for an engaged Surya Namaskar posture.
     *
     * Prioritizes high-definition studio-mastered audio clips (Edge-TTS SwaraNeural) with
     * raised-cosine ducking and 120ms lead delay. Automatically falls back to native TTS
     * if the audio asset is missing.
     *
     * @param pose The active [CompoundPose] containing index, names, breath cues, and solar mantra.
     * @param mode The selected [VoiceCueMode] governing what information is spoken.
     * @param volume Whisper-soft gain factor (0.15f..1.0f, default 0.52f).
     */
    fun playPoseCue(
        pose: CompoundPose,
        mode: VoiceCueMode,
        volume: Float = 0.52f
    ) {
        unifiedVoiceEngine.playSuryaPoseCue(pose, mode, volume)
    }

    /**
     * Auditions a sample posture voice cue for immediate in-app UI settings preview.
     *
     * @param pose The sample [CompoundPose] to audition.
     * @param mode Selected [VoiceCueMode] to preview.
     * @param volume Preview volume gain (default 0.52f).
     */
    fun auditionPoseCue(
        pose: CompoundPose,
        mode: VoiceCueMode,
        volume: Float = 0.52f
    ) {
        playPoseCue(pose, mode, volume)
    }

    /**
     * Synthesizes speech through native Android TTS engine with ducking when raw assets are unavailable.
     *
     * @param text Text string to articulate via [TextToSpeech].
     * @param volume Subdued volume gain factor.
     */
    fun speakWithDucking(text: String, volume: Float = 0.65f) {
        unifiedVoiceEngine.speakWithDucking(text, speedMultiplier = 0.75f, volume = volume)
    }

    /**
     * Plays the voice cue for a given step with ducking and lead delay using a raw resource ID directly.
     *
     * @param resId Raw resource ID of the voice cue audio asset.
     * @param mode Selected [VoiceCueMode].
     */
    fun playCue(@RawRes resId: Int, mode: VoiceCueMode) {
        if (mode == VoiceCueMode.NONE) return
        unifiedVoiceEngine.playMasteredAudio(resId, volume = 0.52f)
    }

    /**
     * Halts ongoing speech synthesis or audio playback immediately and restores ambient volume.
     */
    fun stop() {
        unifiedVoiceEngine.stop()
    }

    /**
     * Releases speech and media player resources when the application session or process terminates.
     */
    fun release() {
        unifiedVoiceEngine.shutdown()
    }

    companion object {
        private const val TAG = "SuryaVoicePlayer"
    }
}
