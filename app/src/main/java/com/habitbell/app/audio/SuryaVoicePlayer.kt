/**
 * # SuryaVoicePlayer
 *
 * Coordinates audio playback and speech synthesis of voice cues for the Surya Namaskar sequencer.
 *
 * ## Architectural Role & Component Relationships
 * Audio subsystem component interfacing with Android [MediaPlayer], [TextToSpeech], and the process-level
 * [BackgroundMusicManager]. Ducking is applied before speech starts, and volume is smoothly restored
 * upon cue completion.
 * - Bound to [com.habitbell.app.engine.CentralSessionHandler] and [com.habitbell.app.engine.TimerEngine].
 * - Supports 4 distinct voice guidance modes: [VoiceCueMode.NONE], [VoiceCueMode.PRANIC],
 *   [VoiceCueMode.STEP_NAME], and [VoiceCueMode.SLOKA].
 * - Prioritizes high-definition studio-mastered audio clips (Edge-TTS `hi-IN-SwaraNeural`, Lata Mangeshkar tone)
 *   from `res/raw`, gracefully falling back to native Android [TextToSpeech] if an asset is unavailable.
 *
 * ## Acoustic Profile & Studio Standard
 * - Voice Profile: hi-IN-SwaraNeural (+52Hz pitch shift, unhurried -30% yogic cadence, Lata Mangeshkar meditative timbre)
 * - Lead Delay: 120ms anti-startle grace period after background music ducking begins before playback starts
 * - Audio Ducking: 0.20f target volume factor over 350ms, smoothly restored over 500ms
 * - Playback Gain: Subdued whisper-soft volume (0.52f default)
 *
 * ## Concurrency & Thread Safety
 * Initialization occurs safely with application context. Speech dispatch and MediaPlayer actions
 * run on the Main coroutine scope; background music ducking operations are asynchronously animated.
 */
package com.habitbell.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RawRes
import com.habitbell.app.data.model.CompoundPose
import com.habitbell.app.engine.BackgroundMusicManager
import com.habitbell.app.ui.SuryaPoseAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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
 * @param context Android context used to access system speech engines and audio resources.
 * @param backgroundMusicManager Manager controlling ambient background music ducking.
 */
class SuryaVoicePlayer(
    private val context: Context,
    private val backgroundMusicManager: BackgroundMusicManager
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    /** Active media player instance for studio-mastered audio cues. */
    private var activeMediaPlayer: MediaPlayer? = null

    /** Native Android TextToSpeech engine handle for offline cue synthesis fallback. */
    private var tts: TextToSpeech? = null

    /** Guard flag indicating whether TTS engine finished initialization successfully. */
    @Volatile
    private var isTtsReady: Boolean = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech engine for SuryaVoicePlayer", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                configureYogicVoice(engine)
                setupUtteranceListener(engine)
                isTtsReady = true
                Log.d(TAG, "SuryaVoicePlayer TextToSpeech initialized successfully")
            }
        } else {
            Log.w(TAG, "SuryaVoicePlayer TextToSpeech initialization failed with status: $status")
        }
    }

    /**
     * Configures pitch (+52Hz pitch shift) and speech cadence (-30% rate) for meditative yogic guidance.
     *
     * @param engine Target [TextToSpeech] instance.
     */
    private fun configureYogicVoice(engine: TextToSpeech) {
        try {
            val hindiLocale = Locale("hi", "IN")
            val result = engine.setLanguage(hindiLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.ENGLISH
            }
        } catch (_: Exception) {
            engine.language = Locale.getDefault()
        }

        // Meditative Lata tone: +52Hz equivalent pitch elevation and unhurried -30% cadence
        engine.setPitch(1.28f)
        engine.setSpeechRate(0.75f)
    }

    /**
     * Sets utterance completion listeners to restore background music volume smoothly.
     *
     * @param engine Active [TextToSpeech] instance.
     */
    private fun setupUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                backgroundMusicManager.restoreVolume(durationMs = 500L)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                backgroundMusicManager.restoreVolume(durationMs = 500L)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                backgroundMusicManager.restoreVolume(durationMs = 500L)
                Log.w(TAG, "SuryaVoicePlayer TTS utterance error: $errorCode for id: $utteranceId")
            }
        })
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
        if (mode == VoiceCueMode.NONE) return

        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val rawRes = SuryaPoseAssets.getAudioResource(
            stepIndex = pose.index,
            mode = mode,
            stepDurationSeconds = pose.durationSeconds
        )

        if (rawRes != null) {
            playMasteredAudio(rawRes, safeVol)
        } else {
            val textToSpeak = when (mode) {
                VoiceCueMode.STEP_NAME -> pose.name
                VoiceCueMode.SLOKA -> if (pose.mantra.isNotBlank()) "${pose.mantra}, ${pose.name}" else pose.name
                VoiceCueMode.PRANIC -> "${pose.name}, ${pose.breathCue}"
                VoiceCueMode.NONE -> return
            }
            speakWithDucking(textToSpeak, safeVol)
        }
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
     * Plays a high-definition studio-mastered audio asset via [MediaPlayer] with smooth
     * background music ducking and anti-startle lead delay.
     *
     * @param resId Raw resource ID from [com.habitbell.app.R.raw].
     * @param volume Floating-point volume gain (0.15f..1.0f).
     */
    private fun playMasteredAudio(@RawRes resId: Int, volume: Float) {
        currentJob?.cancel()
        currentJob = scope.launch {
            stopMediaPlayer()
            // 1. Duck ambient music smoothly
            backgroundMusicManager.duckVolume(duckedRatio = 0.20f, durationMs = 350L)

            // 2. Anti-startle lead delay allowing ambient drone to soften before voice entry
            delay(120L)

            try {
                activeMediaPlayer = MediaPlayer.create(context, resId)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                    )
                    setVolume(volume, volume)
                    setOnCompletionListener { mp ->
                        backgroundMusicManager.restoreVolume(durationMs = 500L)
                        mp.release()
                        if (activeMediaPlayer == mp) {
                            activeMediaPlayer = null
                        }
                    }
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing mastered Surya Namaskar audio asset", e)
                backgroundMusicManager.restoreVolume(durationMs = 500L)
            }
        }
    }

    /**
     * Synthesizes speech through native Android TTS engine with ducking when raw assets are unavailable.
     *
     * @param text Text string to articulate via [TextToSpeech].
     * @param volume Subdued volume gain factor.
     */
    fun speakWithDucking(text: String, volume: Float = 0.65f) {
        currentJob?.cancel()
        currentJob = scope.launch {
            stopMediaPlayer()
            backgroundMusicManager.duckVolume(duckedRatio = 0.20f, durationMs = 350L)
            delay(120L)

            val engine = tts
            if (isTtsReady && engine != null) {
                val utteranceId = "surya_cue_${System.currentTimeMillis()}"
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                }
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                delay(1200L)
                backgroundMusicManager.restoreVolume(durationMs = 500L)
            }
        }
    }

    /**
     * Plays the voice cue for a given step with ducking and lead delay using a raw resource ID directly.
     *
     * @param resId Raw resource ID of the voice cue audio asset.
     * @param mode Selected [VoiceCueMode].
     */
    fun playCue(@RawRes resId: Int, mode: VoiceCueMode) {
        if (mode == VoiceCueMode.NONE) return
        playMasteredAudio(resId, volume = 0.52f)
    }

    /**
     * Safely stops and releases active [MediaPlayer] instances.
     */
    private fun stopMediaPlayer() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        activeMediaPlayer = null
    }

    /**
     * Halts ongoing speech synthesis or audio playback immediately and restores ambient volume.
     */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        stopMediaPlayer()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        backgroundMusicManager.restoreVolume(durationMs = 300L)
    }

    /**
     * Releases speech and media player resources when the application session or process terminates.
     */
    fun release() {
        stop()
        try {
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "SuryaVoicePlayer"
    }
}
