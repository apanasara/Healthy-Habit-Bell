/**
 * # PreparationVoiceGuide
 *
 * Dedicated audio coordinator and vocal guidance engine for the 5-second session preparation countdown.
 *
 * ## Architectural Role & Component Relationships
 * Audio subsystem component located in `com.habitbell.app.audio`:
 * - Coordinates with [com.habitbell.app.engine.BackgroundMusicManager] for smooth raised-cosine audio ducking.
 * - Coordinates with [com.habitbell.app.engine.AudioBellManager] for synchronized crystalline tingsha cymbal strikes (3, 2, 1).
 * - Delivers studio-mastered female vocal cues (`hi-IN-SwaraNeural`, Lata Mangeshkar profile) via [MediaPlayer].
 * - Gracefully falls back to native Android [TextToSpeech] if raw assets are unavailable.
 * - Bound directly to [com.habitbell.app.engine.TimerEngine] and [com.habitbell.app.engine.CentralSessionHandler].
 *
 * ## Acoustic Profile & Studio Directives
 * - Engine: Microsoft Natural Neural `hi-IN-SwaraNeural` (+52Hz pitch elevation, unhurried cadence).
 * - Lead Delay: 120ms anti-startle grace period after background music ducking begins.
 * - Playback Gain: Subdued whisper-level gain (`0.52f` default, range 0.15f..1.0f).
 * - Background Ducking: Duck to `0.20f` volume over 350ms, restored over 500ms.
 *
 * ## Concurrency & Thread Safety
 * Speech synthesis callbacks and [MediaPlayer] lifecycles execute safely on [Dispatchers.Main].
 * Ducking and delay scheduling run inside a dedicated [CoroutineScope] with a [SupervisorJob].
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
import com.habitbell.app.R
import com.habitbell.app.engine.AudioBellManager
import com.habitbell.app.engine.BackgroundMusicManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Audio coordinator for 5-second pre-session preparation countdown cues and cymbal strikes.
 *
 * @param context Android component or application context.
 * @param bgMusicManager Ambient music coordinator for volume ducking.
 * @param audioBellManager Meditative bell manager for countdown strikes.
 */
class PreparationVoiceGuide(
    private val context: Context,
    private val bgMusicManager: BackgroundMusicManager? = null,
    private val audioBellManager: AudioBellManager? = null
) : TextToSpeech.OnInitListener {

    private val TAG = "PreparationVoiceGuide"

    /** Main coroutine scope bound to UI dispatcher with SupervisorJob to isolate child errors. */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Active coroutine job managing current audio playback and ducking lifecycle. */
    private var playbackJob: Job? = null

    /** Active MediaPlayer instance for studio-mastered audio cues. */
    private var mediaPlayer: MediaPlayer? = null

    /** Native Android TextToSpeech engine handle for offline fallback synthesis. */
    private var tts: TextToSpeech? = null

    /** Flag indicating whether the TextToSpeech engine is successfully initialized. */
    @Volatile
    var isTtsReady: Boolean = false
        private set

    /** Master toggle governing whether preparation voice guidance cues are articulated. */
    var isVoiceEnabled: Boolean = true

    /** Master gain factor for preparation voice cue playback (normalized 0.15f..1.0f). Default 0.52f. */
    var voiceVolume: Float = 0.52f

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech engine for PreparationVoiceGuide", e)
        }
    }

    /**
     * TextToSpeech engine initialization callback.
     * Configures a melodious high-frequency female voice profile matching Lata-style tone.
     *
     * @param status [TextToSpeech.SUCCESS] or [TextToSpeech.ERROR].
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                configureGentleVoice(engine)
                setupUtteranceListener(engine)
                isTtsReady = true
                Log.d(TAG, "PreparationVoiceGuide TextToSpeech initialized successfully")
            }
        } else {
            Log.w(TAG, "PreparationVoiceGuide TextToSpeech initialization failed with status: $status")
            isTtsReady = false
        }
    }

    /**
     * Configures pitch elevation and speech cadence for peaceful, anti-startle vocal cues.
     *
     * @param engine Target [TextToSpeech] instance.
     */
    private fun configureGentleVoice(engine: TextToSpeech) {
        try {
            val preferredLocales = listOf(
                Locale("hi", "IN"),
                Locale("en", "IN"),
                Locale.US,
                Locale.getDefault()
            )

            var selectedLocale = Locale.US
            for (loc in preferredLocales) {
                val availability = engine.isLanguageAvailable(loc)
                if (availability >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = loc
                    selectedLocale = loc
                    break
                }
            }

            val voices = engine.voices
            if (!voices.isNullOrEmpty()) {
                val femaleVoice = voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    (voice.locale.language == selectedLocale.language) &&
                            (nameLower.contains("female") || nameLower.contains("swara") || nameLower.contains("fem"))
                } ?: voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    nameLower.contains("female") || nameLower.contains("fem")
                }

                if (femaleVoice != null) {
                    engine.voice = femaleVoice
                    Log.d(TAG, "Selected female TTS voice for preparation: ${femaleVoice.name}")
                }
            }

            // High sweet pitch (1.14f) and serene, unhurried cadence (0.70f)
            engine.setPitch(1.14f)
            engine.setSpeechRate(0.70f)
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring female TTS voice parameters", e)
        }
    }

    /**
     * Registers utterance completion listener to restore background music volume when speech concludes.
     *
     * @param engine Active [TextToSpeech] instance.
     */
    private fun setupUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    bgMusicManager?.restoreVolume(durationMs = 500L)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    bgMusicManager?.restoreVolume(durationMs = 300L)
                }
            }
        })
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
        if (!isVoiceEnabled) return

        playbackJob?.cancel()
        playbackJob = scope.launch {
            // Duck background ambient music smoothly over 350ms
            bgMusicManager?.duckVolume(duckedRatio = 0.20f, durationMs = 350L)

            // Anti-startle lead delay: 120ms grace period after ducking begins before voice playback
            delay(120L)

            // Trigger companion crystalline cymbal chime strike if bell manager is provided
            if (secondsRemaining in 1..3) {
                audioBellManager?.playCountdownStrike(secondsRemaining)
            }

            // Resolve raw audio asset resource ID
            val rawResId = when (secondsRemaining) {
                5 -> R.raw.prep_take_position
                3 -> R.raw.prep_three
                2 -> R.raw.prep_two
                1 -> R.raw.prep_one
                else -> null
            }

            val fallbackText = when (secondsRemaining) {
                5 -> "Take your position"
                3 -> "Three"
                2 -> "Two"
                1 -> "One"
                else -> null
            }

            if (rawResId != null) {
                playRawAudio(rawResId) {
                    // Fallback to TTS if MediaPlayer fails
                    if (fallbackText != null) {
                        speakTts(fallbackText)
                    } else {
                        bgMusicManager?.restoreVolume(durationMs = 500L)
                    }
                }
            } else if (fallbackText != null) {
                speakTts(fallbackText)
            } else {
                bgMusicManager?.restoreVolume(durationMs = 500L)
            }
        }
    }

    /**
     * Plays pre-mastered audio asset from `res/raw` via [MediaPlayer].
     *
     * @param resId Raw resource identifier.
     * @param onError Callback invoked if audio asset playback fails or errors out.
     */
    private fun playRawAudio(@RawRes resId: Int, onError: () -> Unit) {
        try {
            stopActiveMediaPlayer()

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            mediaPlayer = MediaPlayer.create(context, resId).apply {
                setAudioAttributes(attributes)
                val gain = voiceVolume.coerceIn(0.15f, 1.0f)
                setVolume(gain, gain)

                setOnCompletionListener {
                    stopActiveMediaPlayer()
                    bgMusicManager?.restoreVolume(durationMs = 500L)
                }

                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error during preparation cue: what=$what, extra=$extra")
                    stopActiveMediaPlayer()
                    onError()
                    true
                }

                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating MediaPlayer for preparation cue $resId", e)
            stopActiveMediaPlayer()
            onError()
        }
    }

    /**
     * Synthesizes cue text via native Android [TextToSpeech] offline fallback engine.
     *
     * @param text Spoken countdown phrase.
     */
    private fun speakTts(text: String) {
        if (!isTtsReady || tts == null) {
            bgMusicManager?.restoreVolume(durationMs = 500L)
            return
        }

        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceVolume.coerceIn(0.15f, 1.0f))
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "prep_utterance_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed for preparation text '$text'", e)
            bgMusicManager?.restoreVolume(durationMs = 500L)
        }
    }

    /**
     * Releases the active MediaPlayer instance safely.
     */
    private fun stopActiveMediaPlayer() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Halts any active preparation playback, cancels coroutine jobs, and restores ambient music volume.
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        stopActiveMediaPlayer()
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        bgMusicManager?.restoreVolume(durationMs = 300L)
    }

    /**
     * Releases hardware and TTS resources upon process or component teardown.
     */
    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS engine", e)
        } finally {
            tts = null
            isTtsReady = false
        }
    }
}
