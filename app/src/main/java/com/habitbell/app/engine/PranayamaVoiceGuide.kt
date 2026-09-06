package com.habitbell.app.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
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
 * - **TextToSpeech Hardware Handle**: Coordinates Android's native offline [TextToSpeech] engine, configuring
 *   a gentle, serene female voice profile with mindful cadence and pitch.
 * - **Dynamic Audio Ducking**: Interfaces directly with [BackgroundMusicManager] to gracefully attenuate ambient
 *   background meditation drones during voice cues, ensuring instruction clarity for practitioners.
 *
 * ## Concurrency & Lifecycle
 * - Initialized asynchronously via [TextToSpeech.OnInitListener].
 * - Thread-safe cue dispatching synchronized with [TimerEngine] phase transitions.
 * - Resource cleanup enforced in [shutdown].
 *
 * @param context Component or application context.
 * @param bgMusicManager Reference to ambient music coordinator for volume ducking.
 */
class PranayamaVoiceGuide(
    private val context: Context,
    private val bgMusicManager: BackgroundMusicManager? = null
) : TextToSpeech.OnInitListener {

    private val TAG = "PranayamaVoiceGuide"

    /** Native Android TextToSpeech engine handle. */
    private var tts: TextToSpeech? = null

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
     * Inspects available TTS system voices to prioritize a natural, gentle female timbre.
     * Falls back to high-comfort Indian or US English locales.
     *
     * @param engine Target [TextToSpeech] instance.
     */
    private fun configureGentleVoice(engine: TextToSpeech) {
        try {
            // Attempt to locate an English Indian or US female voice
            val preferredLocales = listOf(
                Locale("en", "IN"),
                Locale.US,
                Locale.UK,
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

            // Query voices for female attributes
            val voices = engine.voices
            if (!voices.isNullOrEmpty()) {
                val femaleVoice = voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    (voice.locale.language == selectedLocale.language) &&
                            (nameLower.contains("female") || nameLower.contains("fem") || nameLower.contains("#female"))
                } ?: voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    nameLower.contains("female") || nameLower.contains("fem")
                }

                if (femaleVoice != null) {
                    engine.voice = femaleVoice
                    Log.d(TAG, "Selected female TTS voice: ${femaleVoice.name}")
                }
            }

            // Set serene, slow meditative rate (0.85x) and warm gentle pitch (0.95f)
            engine.setSpeechRate(0.85f)
            engine.setPitch(0.95f)
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
            override fun onStart(utteranceId: String?) {
                // Background music is already ducked before speech invocation
            }

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
     * Speaks the guided cue for a newly engaged Pranayama phase with automatic audio ducking.
     *
     * @param phase The active [PranayamaPhase] beginning execution.
     * @param style Optional override of [VoiceCueStyle]. Defaults to configured [cueStyle].
     */
    fun speakPhaseCue(phase: PranayamaPhase, style: VoiceCueStyle = cueStyle) {
        if (!isVoiceEnabled || !isInitialized || tts == null) return

        val spokenText = when (style) {
            VoiceCueStyle.SANSKRIT -> when (phase) {
                PranayamaPhase.INHALE -> "Purak"
                PranayamaPhase.HOLD_IN -> "Kumbhak"
                PranayamaPhase.EXHALE -> "Rechak"
                PranayamaPhase.HOLD_OUT -> "Kumbhak"
            }
            VoiceCueStyle.ENGLISH -> when (phase) {
                PranayamaPhase.INHALE -> "Inhale"
                PranayamaPhase.HOLD_IN -> "Hold breath"
                PranayamaPhase.EXHALE -> "Exhale"
                PranayamaPhase.HOLD_OUT -> "Hold empty"
            }
            VoiceCueStyle.BILINGUAL -> when (phase) {
                PranayamaPhase.INHALE -> "Purak... Inhale"
                PranayamaPhase.HOLD_IN -> "Kumbhak... Hold"
                PranayamaPhase.EXHALE -> "Rechak... Exhale"
                PranayamaPhase.HOLD_OUT -> "Kumbhak... Hold empty"
            }
        }

        speakUtterance(spokenText)
    }

    /**
     * Auditions a sample spoken cue for settings preview based on selected [VoiceCueStyle].
     *
     * @param style Preferred voice cue style to sample. Defaults to active [cueStyle].
     */
    fun auditionCue(style: VoiceCueStyle = cueStyle) {
        if (!isInitialized || tts == null) return
        val sampleText = when (style) {
            VoiceCueStyle.SANSKRIT -> "Purak"
            VoiceCueStyle.BILINGUAL -> "Purak... Inhale"
            VoiceCueStyle.ENGLISH -> "Inhale"
        }
        speakUtterance(sampleText)
    }

    /**
     * Synthesizes and streams speech through Android audio pipeline, applying ducking.
     *
     * @param text The phrase to articulate.
     */
    private fun speakUtterance(text: String) {
        try {
            // Duck background ambient soundscape so the voice guidance is crystal clear
            bgMusicManager?.duckVolume(0.20f)

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val utteranceId = "pranayama_cue_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak Pranayama cue: $text", e)
            bgMusicManager?.restoreVolume()
        }
    }

    /**
     * Halts ongoing speech synthesis immediately and restores ambient volume.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        bgMusicManager?.restoreVolume()
    }

    /**
     * Releases system TTS engine and frees audio handles.
     */
    fun shutdown() {
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
