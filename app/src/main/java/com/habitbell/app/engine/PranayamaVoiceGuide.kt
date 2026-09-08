package com.habitbell.app.engine

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.habitbell.app.R
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.VoiceCueStyle
import java.util.*

/**
 * # PranayamaVoiceGuide
 *
 * Dedicated speech synthesis and auditory guidance engine for classical Pranayama breathwork.
 *
 * ## Architectural Role & Subsystem Relationships
 * - **Audio Subsystem**: Operates within the Central Engine Layer alongside [AudioBellManager] and [BackgroundMusicManager].
 * - **Mastered Melodious Cues**: Delivers studio-mastered authentic Indian female voice cues in a sweet,
 *   high-frequency swara (reminiscent of classical Bollywood vocalists such as Lata Mangeshkar) with unhurried cadence.
 * - **TextToSpeech Hardware Handle**: Coordinates Android's native offline [TextToSpeech] engine as an
 *   adaptive fallback, configuring a gentle, serene female voice profile with mindful cadence and pitch.
 * - **Dynamic Audio Ducking**: Interfaces directly with [BackgroundMusicManager] to smoothly attenuate ambient
 *   background meditation drones during voice cues using raised-cosine crossfades.
 *
 * ## Concurrency & Lifecycle
 * - Thread-safe cue dispatching synchronized with [TimerEngine] phase transitions.
 * - Hardware resources (MediaPlayer, TTS) safely released on [stop] and [shutdown].
 *
 * @param context Component or application context.
 * @param bgMusicManager Reference to ambient music coordinator for volume ducking.
 */
class PranayamaVoiceGuide(
    private val context: Context,
    private val bgMusicManager: BackgroundMusicManager? = null
) : TextToSpeech.OnInitListener {

    private val TAG = "PranayamaVoiceGuide"

    /** Main thread handler for scheduling audio ducking delays and resource cleanup. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Native Android TextToSpeech engine handle. */
    private var tts: TextToSpeech? = null

    /** Active media player instance for studio-mastered audio cues. */
    private var mediaPlayer: MediaPlayer? = null

    /** Flag indicating whether the TTS engine is successfully initialized and ready. */
    @Volatile
    var isInitialized: Boolean = false
        private set

    /** Master toggle governing whether voice guidance cues are spoken. */
    var isVoiceEnabled: Boolean = true

    /** Active linguistic cue presentation style. Defaults to classical Sanskrit. */
    var cueStyle: VoiceCueStyle = VoiceCueStyle.SANSKRIT

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech engine", e)
        }
    }

    /**
     * TextToSpeech engine initialization callback.
     * Selects a high-quality, gentle female voice and configures meditative pitch and speech rate.
     *
     * @param status [TextToSpeech.SUCCESS] or [TextToSpeech.ERROR].
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                configureGentleVoice(engine)
                setupUtteranceListener(engine)
                isInitialized = true
                Log.d(TAG, "PranayamaVoiceGuide TextToSpeech initialized successfully")
            }
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status: $status")
            isInitialized = false
        }
    }

    /**
     * Inspects available TTS system voices to prioritize a natural, sweet, melodious female timbre.
     * Prioritizes high-comfort Indian English (en_IN) or Hindi (hi_IN) female voices.
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
                            (nameLower.contains("female") || nameLower.contains("swara") || nameLower.contains("fem") || nameLower.contains("#female"))
                } ?: voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    nameLower.contains("female") || nameLower.contains("fem")
                }

                if (femaleVoice != null) {
                    engine.voice = femaleVoice
                    Log.d(TAG, "Selected female TTS voice: ${femaleVoice.name}")
                }
            }

            // Serene, slow meditative rate (0.55x) and sweet, melodious high pitch (1.14f)
            engine.setSpeechRate(0.55f)
            engine.setPitch(1.14f)
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring female TTS voice parameters", e)
        }
    }

    /**
     * Configures utterance completion listeners to coordinate dynamic audio ducking
     * with [BackgroundMusicManager].
     *
     * @param engine Active [TextToSpeech] instance.
     */
    private fun setupUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                bgMusicManager?.restoreVolume()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                bgMusicManager?.restoreVolume()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                bgMusicManager?.restoreVolume()
                Log.w(TAG, "TTS utterance error: $errorCode for id: $utteranceId")
            }
        })
    }

    /**
     * Resolves the high-definition studio-mastered audio resource corresponding to [phase] and [style].
     *
     * If [style] is [VoiceCueStyle.BILINGUAL] but [stepDurationSeconds] is too short (less than 4s),
     * it gracefully falls back to the concise authentic Sanskrit cue to guarantee that voice prompts
     * like "Inhale" or "Exhale" are never abruptly cut in half when the subsequent phase starts.
     *
     * @param phase Active breathwork phase ([PranayamaPhase]).
     * @param style Linguistic delivery style ([VoiceCueStyle]).
     * @param stepDurationSeconds Duration allocated for this breath phase in seconds (optional).
     * @return Raw resource ID, or null if TTS fallback should be utilized.
     */
    private fun resolveAudioResource(
        phase: PranayamaPhase,
        style: VoiceCueStyle,
        stepDurationSeconds: Int? = null
    ): Int? {
        // When step duration is less than 4 seconds, bilingual cue (~3.0s) would collide with the next step.
        // Fall back to clean single-word Sanskrit cue (~2.0s) so speech is never clipped mid-word.
        val effectiveStyle = if (style == VoiceCueStyle.BILINGUAL && stepDurationSeconds != null && stepDurationSeconds < 4) {
            VoiceCueStyle.SANSKRIT
        } else {
            style
        }

        return when (effectiveStyle) {
            VoiceCueStyle.SANSKRIT -> when (phase) {
                PranayamaPhase.INHALE -> R.raw.pranayama_purak_sanskrit
                PranayamaPhase.HOLD_IN -> R.raw.pranayama_kumbhak_sanskrit
                PranayamaPhase.EXHALE -> R.raw.pranayama_rechak_sanskrit
                // In traditional 4-step Pranayama, both retention steps (internal and external) are Kumbhaka
                PranayamaPhase.HOLD_OUT -> R.raw.pranayama_kumbhak_sanskrit
            }
            VoiceCueStyle.BILINGUAL -> when (phase) {
                PranayamaPhase.INHALE -> R.raw.pranayama_purak_bilingual
                PranayamaPhase.HOLD_IN -> R.raw.pranayama_kumbhak_bilingual
                PranayamaPhase.EXHALE -> R.raw.pranayama_rechak_bilingual
                // In traditional 4-step Pranayama, both retention steps (internal and external) are Kumbhaka
                PranayamaPhase.HOLD_OUT -> R.raw.pranayama_kumbhak_bilingual
            }
            VoiceCueStyle.ENGLISH -> null
        }
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
        if (!isVoiceEnabled) return

        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val rawRes = resolveAudioResource(phase, style, stepDurationSeconds)

        if (rawRes != null) {
            playMasteredAudio(rawRes, safeVol)
        } else {
            speakWithTts(phase, style, isTriBandhaVoiceEnabled, safeVol, stepDurationSeconds)
        }
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
        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val samplePhase = if (isTriBandhaVoiceEnabled) PranayamaPhase.HOLD_IN else PranayamaPhase.INHALE
        val rawRes = resolveAudioResource(samplePhase, style, stepDurationSeconds = 8)

        if (rawRes != null) {
            playMasteredAudio(rawRes, safeVol)
        } else {
            speakWithTts(samplePhase, style, isTriBandhaVoiceEnabled, safeVol, stepDurationSeconds = 8)
        }
    }

    /**
     * Plays a high-definition studio-mastered audio asset with smooth ducking and lifecycle management.
     *
     * @param resId Raw resource ID from [R.raw].
     * @param volume Floating-point volume gain (0.0f..1.0f).
     */
    private fun playMasteredAudio(resId: Int, volume: Float) {
        try {
            stopMediaPlayer()
            bgMusicManager?.duckVolume(0.20f, 350L)

            // Lead delay allowing ambient background music to glide down smoothly before voice entry
            mainHandler.postDelayed({
                try {
                    mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                        setVolume(volume, volume)
                        setOnCompletionListener { mp ->
                            bgMusicManager?.restoreVolume(500L)
                            mp.release()
                            mediaPlayer = null
                        }
                        start()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing mastered Pranayama audio cue", e)
                    bgMusicManager?.restoreVolume()
                }
            }, 120L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate mastered audio cue", e)
            bgMusicManager?.restoreVolume()
        }
    }

    /**
     * Synthesizes speech through native Android TTS engine when raw assets are unavailable.
     */
    private fun speakWithTts(
        phase: PranayamaPhase,
        style: VoiceCueStyle,
        isTriBandhaVoiceEnabled: Boolean,
        volume: Float,
        stepDurationSeconds: Int? = null
    ) {
        if (!isInitialized || tts == null) return

        val effectiveStyle = if (style == VoiceCueStyle.BILINGUAL && stepDurationSeconds != null && stepDurationSeconds < 4) {
            VoiceCueStyle.SANSKRIT
        } else {
            style
        }

        val text = when (effectiveStyle) {
            VoiceCueStyle.SANSKRIT -> when (phase) {
                PranayamaPhase.INHALE -> "पूरक..."
                PranayamaPhase.HOLD_IN -> if (isTriBandhaVoiceEnabled) "कुम्भक... त्रिबन्ध..." else "कुम्भक..."
                PranayamaPhase.EXHALE -> "रेचक..."
                PranayamaPhase.HOLD_OUT -> if (isTriBandhaVoiceEnabled) "कुम्भक... त्रिबन्ध..." else "कुम्भक..."
            }
            VoiceCueStyle.BILINGUAL -> when (phase) {
                PranayamaPhase.INHALE -> "पूरक Inhale"
                PranayamaPhase.HOLD_IN -> if (isTriBandhaVoiceEnabled) "कुम्भक Hold with Tri-Bandha" else "कुम्भक Hold"
                PranayamaPhase.EXHALE -> "रेचक Exhale"
                PranayamaPhase.HOLD_OUT -> if (isTriBandhaVoiceEnabled) "कुम्भक Hold with Tri-Bandha" else "कुम्भक Hold"
            }
            VoiceCueStyle.ENGLISH -> when (phase) {
                PranayamaPhase.INHALE -> "Inhale"
                PranayamaPhase.HOLD_IN -> if (isTriBandhaVoiceEnabled) "Hold... Tri-Bandha" else "Hold"
                PranayamaPhase.EXHALE -> "Exhale"
                PranayamaPhase.HOLD_OUT -> "Hold"
            }
        }

        try {
            bgMusicManager?.duckVolume(0.20f, 350L)
            mainHandler.postDelayed({
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                }
                val utteranceId = "pranayama_cue_${System.currentTimeMillis()}"
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }, 120L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak Pranayama cue: $text", e)
            bgMusicManager?.restoreVolume()
        }
    }

    /**
     * Safely stops and releases active [MediaPlayer] instances.
     */
    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    /**
     * Halts ongoing speech synthesis or audio playback immediately and restores ambient volume.
     */
    fun stop() {
        stopMediaPlayer()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        bgMusicManager?.restoreVolume()
    }

    /**
     * Releases system TTS engine and frees audio handles.
     */
    fun shutdown() {
        stopMediaPlayer()
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TextToSpeech", e)
        }
    }
}
