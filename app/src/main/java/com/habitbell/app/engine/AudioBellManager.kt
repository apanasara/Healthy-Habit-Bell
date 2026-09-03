package com.habitbell.app.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.SoundPool
import android.os.Build
import com.habitbell.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Dual-engine audio management service for meditative bell chimes and singing bowls.
 *
 * Provides two playback tiers:
 * 1. **Sampled SoundPool Engine**: Fast, low-latency playback of bundled uncompressed Tibetan singing bowl WAV/OGG assets.
 * 2. **Procedural Additive Synthesis Engine**: High-fidelity 44.1kHz PCM synthesis generating warm, boundary-free
 *    harmonic overtones with cosine attack envelopes, binaural beatings, and soft-clip tanh saturation.
 *
 * @param context Android context used for accessing system audio services and raw assets.
 */
class AudioBellManager(private val context: Context) {

    /** System AudioManager used for managing transient audio focus and stream ducking. */
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Dedicated SoundPool instance for low-latency playback of short bell audio clips. */
    private var soundPool: SoundPool? = null

    /** Resource identifier handle for the single interval chime sound sample in SoundPool. */
    private var intervalSoundId = 0

    /** Resource identifier handle for the three-bell completion chime sound sample in SoundPool. */
    private var completionSoundId = 0

    /** Flag indicating whether raw audio assets have finished asynchronous decoding into memory. */
    private var isLoaded = false

    /** Normalized volume gain scale factor (ranging from `0.0f` to `1.0f`). */
    private var bellVolume = 0.9f

    /** Background coroutine scope for offloading procedural PCM buffer computation. */
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        initSoundPool()
    }

    /**
     * Initializes the [SoundPool] configured specifically for sonification and assistance cues.
     */
    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, _, status ->
                    // Status 0 indicates successful sample decoding
                    if (status == 0) isLoaded = true
                }
            }

        loadSounds()
    }

    /**
     * Attempts to dynamically load raw Tibetan bell sound assets (`tibetan_bell_interval` and `tibetan_bell_complete`).
     */
    private fun loadSounds() {
        try {
            soundPool?.let { pool ->
                val intervalResId = context.resources.getIdentifier("tibetan_bell_interval", "raw", context.packageName)
                val completeResId = context.resources.getIdentifier("tibetan_bell_complete", "raw", context.packageName)

                if (intervalResId != 0) {
                    intervalSoundId = pool.load(context, intervalResId, 1)
                }
                if (completeResId != 0) {
                    completionSoundId = pool.load(context, completeResId, 1)
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to procedural synthesis if resources are unavailable
        }
    }

    /**
     * Sets the playback volume for all bell notifications.
     *
     * @param volume Normalized floating-point volume value between `0.0f` (silent) and `1.0f` (full gain).
     */
    fun setVolume(volume: Float) {
        bellVolume = volume.coerceIn(0f, 1f)
    }

    /**
     * Triggers a single interval bell cue.
     *
     * If pre-rendered assets are loaded, plays the sampled Tibetan bell via [SoundPool].
     * Otherwise, synthesizes a soothing, warm 432Hz harmonic chime with an 8.5s natural decay.
     */
    fun playIntervalBell() {
        requestTransientAudioFocus()
        if (isLoaded && intervalSoundId != 0) {
            soundPool?.play(intervalSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
        } else {
            // Procedural synthesis fallback: 432Hz healing frequency with soft woolen mallet attack
            scope.launch {
                playSynthesizedChime(f0 = 432.0, durationSeconds = 8.5, volumeScale = 0.88f)
            }
        }
    }

    /**
     * Triggers the 3-bell session completion cue.
     *
     * Emits a descending harmonic progression (528Hz -> 432Hz -> 360Hz) with natural acoustic
     * reverberation and zero abrupt audio boundaries.
     */
    fun playCompletionBell() {
        requestTransientAudioFocus()
        if (isLoaded && completionSoundId != 0) {
            soundPool?.play(completionSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
        } else {
            // Triad progression: 528Hz (Transformation) -> 432Hz (Harmonic Balance) -> 360Hz (Grounding)
            scope.launch {
                playSynthesizedChime(f0 = 528.0, durationSeconds = 6.0, volumeScale = 0.42f)
                kotlinx.coroutines.delay(2200)
                playSynthesizedChime(f0 = 432.0, durationSeconds = 7.5, volumeScale = 0.72f)
                kotlinx.coroutines.delay(2600)
                playSynthesizedChime(f0 = 360.0, durationSeconds = 8.0, volumeScale = 1.00f)
            }
        }
    }

    /**
     * Requests temporary ducking audio focus so background music decreases in volume while the bell resonates.
     */
    private fun requestTransientAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* Background media automatically restores volume */ }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    /**
     * Procedural harmonic soothing bell synthesis with soft wool mallet attack and pure overtones.
     * Ultra-smooth S-curve cosine fadeout eliminates any audible boundary cutoff.
     *
     * Mathematical Model:
     * - **Attack**: Raised inverted cosine envelope over 50ms mimicking soft felt striking bronze.
     * - **Decay**: Exponential curve with harmonic damping factors.
     * - **Beating**: 1.08Hz low-frequency modulation producing Tibetan bowl acoustic resonance.
     * - **Overtones**: Fundamental ($f_0$), Sub-octave ($0.5 f_0$), Minor Third ($1.5 f_0$), Octave ($2.0 f_0$), Fifth ($3.0 f_0$).
     * - **Saturation**: Hyperbolic tangent `tanh(x)` soft-knee clipping preventing digital distortion.
     *
     * @param f0 Fundamental oscillation frequency in Hertz (e.g., 432.0, 528.0).
     * @param durationSeconds Total sound duration including the natural reverberant tail in seconds.
     * @param volumeScale Relative amplitude multiplier (0.0f..1.0f) for balancing chord components.
     */
    private fun playSynthesizedChime(f0: Double, durationSeconds: Double, volumeScale: Float = 1.0f) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * durationSeconds).toInt()
            val buffer = ShortArray(numSamples)
            val attackSamples = (sampleRate * 0.050).toInt() // 50ms soft wool mallet impact
            val fadeOutStartSec = durationSeconds - 2.0 // Last 2.0s dedicated to smooth cosine fadeout

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate

                // 1. Attack Envelope (Raised Cosine impact) & Exponential Decay
                val attack = if (i < attackSamples) {
                    0.5 * (1.0 - Math.cos(Math.PI * i / attackSamples))
                } else {
                    Math.exp(-(t - 0.050) * 0.52)
                }

                // 2. Seamless Tail Fadeout (Eliminates click on buffer termination)
                val tailFade = if (t >= fadeOutStartSec) {
                    0.5 * (1.0 + Math.cos(Math.PI * (t - fadeOutStartSec) / 2.0))
                } else 1.0

                // 3. Acoustic Beating & Harmonic Overtones
                val beating = 1.0 + 0.07 * sin(2 * Math.PI * 1.08 * t)
                val sFund = sin(2 * Math.PI * f0 * t)
                val sSub = 0.25 * sin(2 * Math.PI * (f0 * 0.5) * t) * Math.exp(-t * 0.35)
                val sThird = 0.18 * sin(2 * Math.PI * (f0 * 1.5) * t) * Math.exp(-t * 0.75)
                val sOct = 0.20 * sin(2 * Math.PI * (f0 * 2.0) * t) * Math.exp(-t * 1.0)
                val sFifth = 0.06 * sin(2 * Math.PI * (f0 * 3.0) * t) * Math.exp(-t * 1.5)

                // 4. Combined Waveform & Saturation
                val sample = (sFund + sSub + sThird + sOct + sFifth) * attack * tailFade * beating * bellVolume * volumeScale
                val saturated = tanh(sample * 0.85) * 0.95
                buffer[i] = (saturated * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

            // 5. Output via dedicated Static AudioTrack
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
        } catch (_: Exception) {
            // Silence any audio buffer allocation errors under memory pressure
        }
    }

    /**
     * Releases hardware [SoundPool] instances and frees audio decoder memory.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
