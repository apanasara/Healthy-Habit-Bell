/**
 * # UnifiedVoiceEngine
 *
 * Centralized, authoritative voice guidance and speech synthesis coordinator for Habit Bell.
 *
 * ## Architectural Role & Component Relationships
 * Serves as the single source of truth and shared hardware coordinator for all vocal guidance
 * across the entire application:
 * - **Pranayama & Visama Vritti Breathwork**: Spoken breath flow cues (Purak, Kumbhak, Rechak, Bahya)
 *   in Sanskrit, Bilingual, or English styles with Tri-Bandha prompts.
 * - **Pre-Session Preparation Countdown**: 5-second mindful lead-in cues ("Take your position", 3, 2, 1)
 *   coordinated with companion tingsha cymbal strikes and strict acoustic silence during room noise scanning.
 * - **Sūrya Namaskār Sequencer**: Asana posture names, breath flows, and solar slokas.
 * - **Yoga & Physiotherapy Hold Timer**: Round ordinal announcements, hold entry cues, rest boundaries,
 *   cadence-controlled count-aloud loops, and clinician safety alerts.
 * - **Acoustic Breathwork & Mantra Counter**: Retention and release voice prompts.
 *
 * ## Acoustic Profile & Studio Directives
 * Grounded in contemplative mindfulness and anti-startle acoustic engineering:
 * - **Timbre**: Unhurried, sweet, meditative female voice matching the revered tonal swara of
 *   Bollywood singing legend **Lata Mangeshkar** (`hi-IN-SwaraNeural` or high-quality female TTS).
 * - **Pitch Elevation**: +52Hz equivalent elevated pitch (1.16f–1.18f) for peaceful clarity.
 * - **Meditation Cadence**: Unhurried pacing (0.70f–0.85f default) to foster autonomic nervous relaxation.
 * - **Anti-Startle Lead Delay**: 120ms grace period after background music ducking initiates before voice starts.
 * - **Raised-Cosine Audio Ducking**: Smoothly attenuates ambient background drone to 0.20f over 350ms,
 *   restoring volume over 500ms when speech/media completes.
 * - **Step Timing vs. Voice Timing Law**: For durations < 6s, automatically falls back to concise single-language
 *   Sanskrit cues (~2.2s) to strictly prevent mid-word audio clipping.
 *
 * ## Concurrency & Thread Safety
 * Speech dispatch, MediaPlayer lifecycles, and coroutine jobs execute safely on [Dispatchers.Main].
 * Safe against concurrent invocations; cancels in-flight utterances before launching new cues.
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
import com.habitbell.app.data.model.CompoundPose
import com.habitbell.app.data.model.PranayamaPhase
import com.habitbell.app.data.model.VoiceCueStyle
import com.habitbell.app.engine.AudioBellManager
import com.habitbell.app.engine.BackgroundMusicManager
import com.habitbell.app.holdtimer.DualCueSpeaker
import com.habitbell.app.ui.SuryaPoseAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Single authoritative voice engine orchestrating speech synthesis and studio-mastered audio cues.
 *
 * @param context Component or application context.
 * @param bgMusicManager Optional ambient music coordinator for smooth raised-cosine ducking.
 */
class UnifiedVoiceEngine(
    private val context: Context,
    val bgMusicManager: BackgroundMusicManager? = null
) : TextToSpeech.OnInitListener, DualCueSpeaker {

    private val TAG = "UnifiedVoiceEngine"

    /** Main coroutine scope bound to Main dispatcher with SupervisorJob to isolate child errors. */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Active coroutine job managing current audio playback and ducking lifecycle. */
    private var activePlaybackJob: Job? = null

    /** Shared Android TextToSpeech engine handle. */
    private var tts: TextToSpeech? = null

    /** Active MediaPlayer instance for studio-mastered audio cues. */
    private var activeMediaPlayer: MediaPlayer? = null

    /** Flag indicating whether the TextToSpeech engine is successfully initialized. */
    @Volatile
    var isTtsReady: Boolean = false
        private set

    /** Master toggle governing whether voice guidance cues are spoken. Default true. */
    var isVoiceEnabled: Boolean = true

    /** Master voice volume gain factor (normalized 0.15f..1.0f). Default 0.52f (subdued meditative soft). */
    var masterVoiceVolume: Float = 0.52f

    /** Default linguistic cue presentation style. Defaults to classical Sanskrit. */
    var defaultCueStyle: VoiceCueStyle = VoiceCueStyle.SANSKRIT

    /** Default speech rate multiplier (0.5f..2.0f). Default 0.85f (calm/unhurried). */
    var defaultSpeechRate: Float = 0.85f

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech engine", e)
        }
    }

    /**
     * TextToSpeech engine initialization callback.
     * Configures the meditative Lata Mangeshkar acoustic profile and utterance listeners.
     *
     * @param status [TextToSpeech.SUCCESS] or [TextToSpeech.ERROR].
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                configureLataVoiceProfile(engine)
                setupUtteranceListener(engine)
                isTtsReady = true
                Log.d(TAG, "UnifiedVoiceEngine TextToSpeech initialized successfully")
            }
        } else {
            Log.w(TAG, "UnifiedVoiceEngine TextToSpeech initialization failed with status: $status")
            isTtsReady = false
        }
    }

    /**
     * Configures available TTS system voices to prioritize a natural, sweet, melodious female timbre.
     * Prioritizes high-comfort Indian English (en_IN) or Hindi (hi_IN) female voices matching
     * the gentle, revered tonal swara of Lata Mangeshkar.
     *
     * @param engine Target [TextToSpeech] instance.
     */
    private fun configureLataVoiceProfile(engine: TextToSpeech) {
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
            Log.d(TAG, "Configuring TTS voice profile. Selected locale: $selectedLocale, total voices: ${voices?.size ?: 0}")
            if (!voices.isNullOrEmpty()) {
                // Prioritize female voices across Google TTS, Samsung, and Android AOSP engines.
                // Google TTS uses identifiers like hi-in-x-hie-local (female), en-in-x-end-local (female), etc.
                val femaleVoice = voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    val matchesLanguage = voice.locale.language == selectedLocale.language || voice.locale.country == "IN"
                    matchesLanguage && (
                        nameLower.contains("-hie-") || nameLower.contains("-end-") ||
                        nameLower.contains("-enc-") || nameLower.contains("-ena-") ||
                        nameLower.contains("-ene-") || nameLower.contains("female") ||
                        nameLower.contains("swara") || nameLower.contains("#female") ||
                        nameLower.contains("-sfg-") || nameLower.contains("-tpd-")
                    )
                } ?: voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    nameLower.contains("-hie-") || nameLower.contains("-end-") ||
                    nameLower.contains("-enc-") || nameLower.contains("-ena-") ||
                    nameLower.contains("-ene-") || nameLower.contains("female") ||
                    nameLower.contains("swara")
                } ?: voices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase()
                    (voice.locale.language == "hi" || (voice.locale.language == "en" && voice.locale.country == "IN")) &&
                        !nameLower.contains("-hid-") && !nameLower.contains("-hic-") && !nameLower.contains("-enb-")
                }

                if (femaleVoice != null) {
                    engine.voice = femaleVoice
                    engine.language = femaleVoice.locale
                    Log.d(TAG, "Selected female TTS voice: ${femaleVoice.name} (locale: ${femaleVoice.locale})")
                } else {
                    Log.d(TAG, "No female voice matched. Active default voice: ${engine.voice?.name}")
                }
            }

            // Elevated sweet pitch (+52Hz equivalent 1.20f) and serene unhurried cadence
            engine.setPitch(1.20f)
            engine.setSpeechRate(defaultSpeechRate)
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

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch {
                    bgMusicManager?.restoreVolume(durationMs = 300L)
                }
                Log.w(TAG, "TTS utterance error: $errorCode for id: $utteranceId")
            }
        })
    }

    // =========================================================================
    // DOMAIN 1: PRANAYAMA & VISAMA VRITTI BREATHWORK
    // =========================================================================

    /**
     * Resolves the high-definition studio-mastered audio resource corresponding to [phase] and [style].
     *
     * ## Step Timing vs. Voice Timing Law:
     * High-fidelity, melodious bilingual cues ("Purak... Inhale", "Kumbhak... Hold") require ~5.6s–5.9s
     * to articulate fully in an unhurried, sweet, meditative Lata-style swara without feeling rushed.
     * If [style] is [VoiceCueStyle.BILINGUAL] but [stepDurationSeconds] is less than 6 seconds (e.g., a 4s
     * or 2s/3s Purak step), the engine automatically falls back to the clean single-language Sanskrit cue
     * ("Purak", 2.2s). This strictly prevents mid-word audio clipping (e.g. "Purak... In-") while
     * preserving genuine soothing pacing for steps that can accommodate it (e.g., 8s or 16s Kumbhak).
     *
     * @param phase Active breathwork phase ([PranayamaPhase]).
     * @param style Linguistic delivery style ([VoiceCueStyle]).
     * @param stepDurationSeconds Duration allocated for this breath phase in seconds (optional).
     * @return Raw resource ID, or null if TTS fallback should be utilized.
     */
    fun resolvePranayamaAudioResource(
        phase: PranayamaPhase,
        style: VoiceCueStyle,
        stepDurationSeconds: Int? = null
    ): Int? {
        val effectiveStyle = if (style == VoiceCueStyle.BILINGUAL && stepDurationSeconds != null && stepDurationSeconds < 6) {
            VoiceCueStyle.SANSKRIT
        } else {
            style
        }

        return when (effectiveStyle) {
            VoiceCueStyle.SANSKRIT -> when (phase) {
                PranayamaPhase.INHALE -> R.raw.pranayama_purak_sanskrit
                PranayamaPhase.HOLD_IN -> R.raw.pranayama_kumbhak_sanskrit
                PranayamaPhase.EXHALE -> R.raw.pranayama_rechak_sanskrit
                PranayamaPhase.HOLD_OUT -> R.raw.pranayama_kumbhak_sanskrit
            }
            VoiceCueStyle.BILINGUAL -> when (phase) {
                PranayamaPhase.INHALE -> R.raw.pranayama_purak_bilingual
                PranayamaPhase.HOLD_IN -> R.raw.pranayama_kumbhak_bilingual
                PranayamaPhase.EXHALE -> R.raw.pranayama_rechak_bilingual
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
     * @param style Optional override of [VoiceCueStyle]. Defaults to [defaultCueStyle].
     * @param isTriBandhaVoiceEnabled Whether to articulate the Tri-Bandha prompt during retention phases.
     * @param volume Subdued voice gain (0.15f..1.0f, default [masterVoiceVolume]).
     * @param stepDurationSeconds Allocated duration of this phase in seconds.
     */
    fun speakPranayamaCue(
        phase: PranayamaPhase,
        style: VoiceCueStyle = defaultCueStyle,
        isTriBandhaVoiceEnabled: Boolean = false,
        volume: Float = masterVoiceVolume,
        stepDurationSeconds: Int? = null
    ) {
        if (!isVoiceEnabled) return

        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val rawRes = resolvePranayamaAudioResource(phase, style, stepDurationSeconds)

        if (rawRes != null) {
            playMasteredAudio(rawRes, safeVol)
        } else {
            val effectiveStyle = if (style == VoiceCueStyle.BILINGUAL && stepDurationSeconds != null && stepDurationSeconds < 6) {
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
            speakWithDucking(text, speedMultiplier = 0.65f, volume = safeVol)
        }
    }

    /**
     * Auditions a sample spoken cue for settings preview based on selected [VoiceCueStyle].
     *
     * @param style Preferred voice cue style to sample. Defaults to [defaultCueStyle].
     * @param isTriBandhaVoiceEnabled Whether to audition the Tri-Bandha retention cue.
     * @param volume Subdued preview gain (default [masterVoiceVolume]).
     */
    fun auditionPranayamaCue(
        style: VoiceCueStyle = defaultCueStyle,
        isTriBandhaVoiceEnabled: Boolean = false,
        volume: Float = masterVoiceVolume
    ) {
        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val samplePhase = if (isTriBandhaVoiceEnabled) PranayamaPhase.HOLD_IN else PranayamaPhase.INHALE
        val rawRes = resolvePranayamaAudioResource(samplePhase, style, stepDurationSeconds = 8)

        if (rawRes != null) {
            playMasteredAudio(rawRes, safeVol)
        } else {
            speakPranayamaCue(samplePhase, style, isTriBandhaVoiceEnabled, safeVol, stepDurationSeconds = 8)
        }
    }

    // =========================================================================
    // DOMAIN 2: PRE-SESSION PREPARATION COUNTDOWN
    // =========================================================================

    /**
     * Plays the appropriate vocal cue and chime strike for the given countdown second.
     *
     * Cues:
     * - `5`: "Take your position" (`R.raw.prep_take_position`)
     * - `3`: "Three" (`R.raw.prep_three`) + Option C strike 3 chime
     * - `2`: "Two" (`R.raw.prep_two`) + Option C strike 2 chime
     * - `1`: "One" (`R.raw.prep_one`) + Option C strike 1 chime
     *
     * @param secondsRemaining Preparation countdown seconds remaining (1..5).
     * @param isAcousticCalibrationActive Strict acoustic silence guard during mic noise calibration.
     * @param audioBellManager Meditative bell manager for companion chime strikes.
     * @param volume Vocal gain factor (default [masterVoiceVolume]).
     */
    fun playPreparationCue(
        secondsRemaining: Int,
        isAcousticCalibrationActive: Boolean = false,
        audioBellManager: AudioBellManager? = null,
        volume: Float = masterVoiceVolume
    ) {
        if (!isVoiceEnabled) return

        // Strict Acoustic Silence Protocol: suppress chimes & numbers during seconds 1..3 for room calibration
        if (isAcousticCalibrationActive && secondsRemaining in 1..3) {
            Log.d(TAG, "Suppressed preparation cue & chime at T=${secondsRemaining}s to preserve acoustic silence for room scanning")
            return
        }

        val safeVol = volume.coerceIn(0.15f, 1.0f)
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

        activePlaybackJob?.cancel()
        activePlaybackJob = scope.launch {
            bgMusicManager?.duckVolume(duckedRatio = 0.20f, durationMs = 350L)
            delay(120L)

            if (secondsRemaining in 1..3) {
                audioBellManager?.playCountdownStrike(secondsRemaining)
            }

            if (rawResId != null) {
                playMasteredAudio(rawResId, safeVol)
            } else if (fallbackText != null) {
                speakWithDucking(fallbackText, speedMultiplier = 0.70f, volume = safeVol)
            } else {
                bgMusicManager?.restoreVolume(durationMs = 500L)
            }
        }
    }

    // =========================================================================
    // DOMAIN 3: SŪRYA NAMASKĀR SEQUENCER
    // =========================================================================

    /**
     * Dispatches voice guidance for an engaged Surya Namaskar posture.
     *
     * @param pose The active [CompoundPose] containing index, names, breath cues, and solar mantra.
     * @param mode The selected [VoiceCueMode] governing what information is spoken.
     * @param volume Subdued gain factor (default [masterVoiceVolume]).
     */
    fun playSuryaPoseCue(
        pose: CompoundPose,
        mode: VoiceCueMode,
        volume: Float = masterVoiceVolume
    ) {
        if (mode == VoiceCueMode.NONE || !isVoiceEnabled) return

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
            speakWithDucking(textToSpeak, speedMultiplier = 0.75f, volume = safeVol)
        }
    }

    // =========================================================================
    // DOMAIN 4: YOGA & PHYSIOTHERAPY HOLD TIMER (DUAL-CUE SPEAKER IMPLEMENTATION)
    // =========================================================================

    /**
     * Articulates spoken text through the unified voice engine, implementing [DualCueSpeaker].
     *
     * @param text Spoken word or phrase.
     * @param speedMultiplier Cadence multiplier (1.0f = normal).
     */
    override fun speak(text: String, speedMultiplier: Float) {
        speak(text, speedMultiplier, masterVoiceVolume)
    }

    /**
     * Synthesizes and announces spoken text with explicit volume control.
     *
     * @param text Spoken word or phrase.
     * @param speedMultiplier Cadence/rate multiplier.
     * @param volume Spoken volume gain factor (0.15f..1.0f).
     */
    fun speak(text: String, speedMultiplier: Float, volume: Float) {
        if (!isVoiceEnabled) return
        speakWithDucking(text, speedMultiplier, volume)
    }

    /**
     * Articulates a hold timer round start cue with studio asset or unified TTS fallback.
     *
     * @param roundOrdinal Spoken ordinal, e.g. "First", "Second", "Third".
     * @param style Linguistic delivery style ([VoiceCueStyle]).
     * @param volume Spoken gain factor.
     * @param speedMultiplier Cadence multiplier.
     */
    fun speakHoldStartCue(
        roundOrdinal: String,
        style: VoiceCueStyle = defaultCueStyle,
        volume: Float = masterVoiceVolume,
        speedMultiplier: Float = defaultSpeechRate
    ) {
        if (!isVoiceEnabled) return

        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val rawRes = when (style) {
            VoiceCueStyle.SANSKRIT -> R.raw.pranayama_kumbhak_sanskrit
            VoiceCueStyle.BILINGUAL -> R.raw.pranayama_kumbhak_bilingual
            VoiceCueStyle.ENGLISH -> R.raw.hold_cue_english
        }
        playMasteredAudio(rawRes, safeVol)
    }

    /**
     * Articulates an inter-round rest boundary cue with studio asset or unified TTS fallback.
     *
     * @param style Linguistic delivery style ([VoiceCueStyle]).
     * @param volume Spoken gain factor.
     * @param speedMultiplier Cadence multiplier.
     */
    fun speakRestCue(
        style: VoiceCueStyle = defaultCueStyle,
        volume: Float = masterVoiceVolume,
        speedMultiplier: Float = defaultSpeechRate
    ) {
        if (!isVoiceEnabled) return

        val safeVol = volume.coerceIn(0.15f, 1.0f)
        val rawRes = when (style) {
            VoiceCueStyle.SANSKRIT -> R.raw.pranayama_rechak_sanskrit
            VoiceCueStyle.BILINGUAL -> R.raw.pranayama_rechak_bilingual
            VoiceCueStyle.ENGLISH -> R.raw.rest_cue_english
        }
        playMasteredAudio(rawRes, safeVol)
    }

    /**
     * Articulates session completion with studio-mastered Lata voice asset or unified TTS fallback.
     *
     * @param volume Spoken gain factor.
     * @param speedMultiplier Cadence multiplier.
     */
    fun speakSessionCompleteCue(
        volume: Float = masterVoiceVolume,
        speedMultiplier: Float = defaultSpeechRate
    ) {
        if (!isVoiceEnabled) return
        val safeVol = volume.coerceIn(0.15f, 1.0f)
        playMasteredAudio(R.raw.hold_session_complete, safeVol)
    }

    /**
     * Auditions a sample spoken cue for the Hold Timer settings drawer preview.
     *
     * @param style Preferred voice cue style to audition.
     * @param volume Vocal preview gain factor.
     * @param speedMultiplier Cadence multiplier.
     */
    fun auditionHoldCue(
        style: VoiceCueStyle = defaultCueStyle,
        volume: Float = masterVoiceVolume,
        speedMultiplier: Float = defaultSpeechRate
    ) {
        Log.d(TAG, "auditionHoldCue triggered: style=$style, volume=$volume, speed=$speedMultiplier")
        speakHoldStartCue(
            roundOrdinal = "First",
            style = style,
            volume = volume,
            speedMultiplier = speedMultiplier
        )
    }

    // =========================================================================
    // CORE AUDIO INFRASTRUCTURE: STUDIO ASSET PLAYBACK & DUCKED TTS SYNTHESIS
    // =========================================================================

    /**
     * Plays a high-definition studio-mastered audio asset via [MediaPlayer] with smooth
     * background music ducking and 120ms anti-startle lead delay.
     *
     * @param resId Raw resource ID from [R.raw].
     * @param volume Floating-point volume gain (0.15f..1.0f).
     */
    fun playMasteredAudio(@RawRes resId: Int, volume: Float) {
        Log.d(TAG, "playMasteredAudio called for resId=$resId, volume=$volume")
        activePlaybackJob?.cancel()
        activePlaybackJob = scope.launch {
            stopActiveMediaPlayer()

            // 1. Duck ambient background music smoothly over 350ms
            bgMusicManager?.duckVolume(duckedRatio = 0.20f, durationMs = 350L)

            // 2. Anti-startle lead delay allowing ambient drone to soften before voice entry
            delay(120L)

            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                activeMediaPlayer = MediaPlayer.create(context, resId, attributes, 0)?.apply {
                    val safeVol = volume.coerceIn(0.15f, 1.0f)
                    setVolume(safeVol, safeVol)

                    setOnCompletionListener { mp ->
                        bgMusicManager?.restoreVolume(durationMs = 500L)
                        mp.release()
                        if (activeMediaPlayer == mp) {
                            activeMediaPlayer = null
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "MediaPlayer error playing cue $resId: what=$what, extra=$extra")
                        stopActiveMediaPlayer()
                        bgMusicManager?.restoreVolume(durationMs = 500L)
                        true
                    }

                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating MediaPlayer for resource $resId", e)
                stopActiveMediaPlayer()
                bgMusicManager?.restoreVolume(durationMs = 500L)
            }
        }
    }

    /**
     * Synthesizes speech through native Android TTS engine with ducking and lead delay.
     *
     * @param text Text string to articulate via [TextToSpeech].
     * @param speedMultiplier Speech rate multiplier.
     * @param volume Volume gain factor (0.15f..1.0f).
     */
    fun speakWithDucking(text: String, speedMultiplier: Float = 0.85f, volume: Float = masterVoiceVolume) {
        activePlaybackJob?.cancel()
        activePlaybackJob = scope.launch {
            bgMusicManager?.duckVolume(duckedRatio = 0.20f, durationMs = 350L)
            delay(120L)

            val engine = tts
            if (isTtsReady && engine != null) {
                val safeSpeed = speedMultiplier.coerceIn(0.5f, 2.0f)
                engine.setSpeechRate(safeSpeed)
                engine.setPitch(1.16f)

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.15f, 1.0f))
                }
                val utteranceId = "unified_voice_${System.currentTimeMillis()}"
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                // Approximate speaking delay if TTS engine is unavailable
                delay(1200L)
                bgMusicManager?.restoreVolume(durationMs = 500L)
            }
        }
    }

    /**
     * Safely stops and releases active [MediaPlayer] instances.
     */
    private fun stopActiveMediaPlayer() {
        try {
            activeMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping active MediaPlayer", e)
        } finally {
            activeMediaPlayer = null
        }
    }

    /**
     * Halts ongoing speech synthesis or audio playback immediately and restores ambient volume.
     */
    override fun stop() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
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
     * Releases system TTS engine and frees audio handles.
     */
    override fun release() {
        shutdown()
    }

    /**
     * Releases hardware resources upon process or component teardown.
     */
    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS engine", e)
        } finally {
            tts = null
            isTtsReady = false
        }
    }
}
