/**
 * # SuryaVoicePlayer
 *
 * Handles audio playback and speech synthesis of voice cues for the Surya Namaskar timer.
 *
 * ## Architectural Role & Component Relationships
 * Audio subsystem component interfacing with Android [TextToSpeech], [MediaPlayer], and the process-level
 * [BackgroundMusicManager]. Ducking is applied before speech starts, and volume is smoothly restored
 * upon cue completion.
 * - Bound to [com.habitbell.app.engine.CentralSessionHandler] and [com.habitbell.app.engine.TimerEngine].
 * - Supports 4 distinct voice guidance modes: [VoiceCueMode.NONE], [VoiceCueMode.PRANIC],
 *   [VoiceCueMode.STEP_NAME], and [VoiceCueMode.SLOKA].
 *
 * ## Acoustic Profile
 * - Voice Profile: hi-IN-SwaraNeural (+52Hz pitch shift, Lata Mangeshkar meditative tone)
 * - Lead Delay: 120ms anti-startle grace period after background music ducking begins
 * - Audio Ducking: 0.20f target volume factor over 350ms, restored over 500ms
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
     * User-facing localized display name for UI chips and badges.
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
 * Voice cue player coordinating audio ducking, TextToSpeech articulation, and MediaPlayer playback.
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

    /** Native Android TextToSpeech engine handle for offline cue synthesis. */
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
            }
        })
    }

    /**
     * Dispatches voice guidance for an engaged Surya Namaskar posture based on active [mode].
     *
     * @param pose The active [CompoundPose] containing names, breath cues, and solar mantra.
     * @param mode The selected [VoiceCueMode] governing what information is spoken.
     */
    fun playPoseCue(pose: CompoundPose, mode: VoiceCueMode) {
        if (mode == VoiceCueMode.NONE) return

        val textToSpeak = when (mode) {
            VoiceCueMode.STEP_NAME -> pose.name
            VoiceCueMode.SLOKA -> if (pose.mantra.isNotBlank()) pose.mantra else pose.name
            VoiceCueMode.PRANIC -> pose.breathCue
            VoiceCueMode.NONE -> return
        }

        speakWithDucking(textToSpeak)
    }

    /**
     * Smoothly ducks background music, honors anti-startle delay, and synthesizes speech.
     *
     * @param text Text string to articulate via [TextToSpeech].
     */
    fun speakWithDucking(text: String) {
        currentJob?.cancel()
        currentJob = scope.launch {
            // Duck ambient music smoothly
            backgroundMusicManager.duckVolume(duckedRatio = 0.20f, durationMs = 350L)
            // Anti-startle lead delay
            delay(120L)

            val engine = tts
            if (isTtsReady && engine != null) {
                val utteranceId = "surya_cue_${System.currentTimeMillis()}"
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.65f)
                }
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                // If TTS is not yet initialized, restore volume after brief pause
                delay(1200L)
                backgroundMusicManager.restoreVolume(durationMs = 500L)
            }
        }
    }

    /**
     * Plays the voice cue for a given step with ducking and lead delay using a raw resource ID.
     *
     * @param resId Raw resource ID of the voice cue audio asset.
     * @param mode Selected [VoiceCueMode].
     */
    fun playCue(@RawRes resId: Int, mode: VoiceCueMode) {
        currentJob?.cancel()
        if (mode == VoiceCueMode.NONE) return

        currentJob = scope.launch {
            // Duck ambient music smoothly
            backgroundMusicManager.duckVolume(duckedRatio = 0.20f, durationMs = 350L)
            // Anti-startle lead delay
            delay(120L)

            val player = MediaPlayer.create(context, resId)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                setVolume(0.52f, 0.52f)
            } ?: return@launch

            player.start()
            while (player.isPlaying) {
                delay(50L)
            }
            player.release()

            // Restore ambient music volume smoothly
            backgroundMusicManager.restoreVolume(durationMs = 500L)
        }
    }

    /**
     * Stops any currently playing cue and cancels pending jobs, restoring background music.
     */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        backgroundMusicManager.restoreVolume(durationMs = 300L)
    }

    /**
     * Releases speech resources when application process terminates.
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
