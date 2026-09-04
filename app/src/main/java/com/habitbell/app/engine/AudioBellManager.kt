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

enum class BellSoundStyle {
    ZEN_TINGSHA,
    TIBETAN_BOWL,
    TEMPLE_GONG,
    CRYSTAL_QUARTZ
}

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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var soundPool: SoundPool? = null

    private var intervalSoundId = 0
    private var completionSoundId = 0
    private var templeGongSoundId = 0
    private var crystalQuartzSoundId = 0

    private var isLoaded = false
    private var bellVolume = 0.9f
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Resource identifier handle for individual countdown strikes (Option C Tingsha cymbals). */
    private var strike3SoundId = 0
    private var strike2SoundId = 0
    private var strike1SoundId = 0

    /** Active bell sound timbre style. Defaults to user-approved Zen Tingsha. */
    var bellStyle: BellSoundStyle = BellSoundStyle.ZEN_TINGSHA

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) isLoaded = true
                }
            }

        loadSounds()
    }

    private fun loadSounds() {
        try {
            soundPool?.let { pool ->
                intervalSoundId = pool.load(context, R.raw.tibetan_bell_interval, 1)
                completionSoundId = pool.load(context, R.raw.tibetan_bell_complete, 1)
                strike3SoundId = pool.load(context, R.raw.tingsha_strike_3s, 1)
                strike2SoundId = pool.load(context, R.raw.tingsha_strike_2s, 1)
                strike1SoundId = pool.load(context, R.raw.tingsha_strike_1s, 1)
                templeGongSoundId = pool.load(context, R.raw.temple_gong, 1)
                crystalQuartzSoundId = pool.load(context, R.raw.crystal_quartz, 1)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioBellManager", "Error loading SoundPool assets", e)
        }
    }

    fun setVolume(volume: Float) {
        bellVolume = volume.coerceIn(0f, 1f)
    }

    /**
     * Triggers the interval bell according to the configured [bellStyle].
     */
    fun playIntervalBell() {
        requestTransientAudioFocus()
        when (bellStyle) {
            BellSoundStyle.ZEN_TINGSHA -> {
                if (isLoaded && strike1SoundId != 0) {
                    soundPool?.play(strike1SoundId, bellVolume, bellVolume, 1, 0, 1.0f)
                } else {
                    scope.launch { playSynthesizedChime(f0 = 1024.0, durationSeconds = 7.5, volumeScale = 0.95f) }
                }
            }
            BellSoundStyle.TEMPLE_GONG -> {
                if (isLoaded && templeGongSoundId != 0) {
                    soundPool?.play(templeGongSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
                } else {
                    scope.launch { playSynthesizedChime(f0 = 324.0, durationSeconds = 8.5, volumeScale = 0.90f) }
                }
            }
            BellSoundStyle.CRYSTAL_QUARTZ -> {
                if (isLoaded && crystalQuartzSoundId != 0) {
                    soundPool?.play(crystalQuartzSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
                } else {
                    scope.launch { playSynthesizedChime(f0 = 528.0, durationSeconds = 8.0, volumeScale = 0.90f) }
                }
            }
            BellSoundStyle.TIBETAN_BOWL -> {
                if (isLoaded && intervalSoundId != 0) {
                    soundPool?.play(intervalSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
                } else {
                    scope.launch { playSynthesizedChime(f0 = 432.0, durationSeconds = 8.5, volumeScale = 0.88f) }
                }
            }
        }
    }

    /**
     * Plays an individual countdown chime strike during the final 3 seconds (Option C Zen Tingsha Triad).
     *
     * @param secondsRemaining Number of seconds remaining:
     *   - 3: Strike 1 (2048 Hz crystalline bell, 45% volume)
     *   - 2: Strike 2 (1536 Hz centering bell, 70% volume)
     *   - 1: Strike 3 (1024 Hz deep resonance finish, 100% volume)
     */
    fun playCountdownStrike(secondsRemaining: Int) {
        requestTransientAudioFocus()
        when (secondsRemaining) {
            3 -> {
                if (isLoaded && strike3SoundId != 0) {
                    soundPool?.play(strike3SoundId, bellVolume * 0.45f, bellVolume * 0.45f, 2, 0, 1.0f)
                } else {
                    scope.launch {
                        playSynthesizedChime(f0 = 2048.0, durationSeconds = 4.0, volumeScale = 0.45f)
                    }
                }
            }
            2 -> {
                if (isLoaded && strike2SoundId != 0) {
                    soundPool?.play(strike2SoundId, bellVolume * 0.70f, bellVolume * 0.70f, 2, 0, 1.0f)
                } else {
                    scope.launch {
                        playSynthesizedChime(f0 = 1536.0, durationSeconds = 4.5, volumeScale = 0.70f)
                    }
                }
            }
            1 -> {
                if (isLoaded && strike1SoundId != 0) {
                    soundPool?.play(strike1SoundId, bellVolume, bellVolume, 3, 0, 1.0f)
                } else {
                    scope.launch {
                        playSynthesizedChime(f0 = 1024.0, durationSeconds = 7.5, volumeScale = 1.00f)
                    }
                }
            }
        }
    }

    /**
     * Triggers the 3-bell session completion cue (Option C Zen Tingsha Triad).
     *
     * Emits a crystalline harmonic progression (2048Hz -> 1536Hz -> 1024Hz) with natural acoustic
     * reverberation and zero abrupt audio boundaries.
     */
    fun playCompletionBell() {
        requestTransientAudioFocus()
        if (isLoaded && completionSoundId != 0) {
            soundPool?.play(completionSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
        } else {
            // Option C Triad progression: 2048Hz -> 1536Hz -> 1024Hz
            scope.launch {
                playSynthesizedChime(f0 = 2048.0, durationSeconds = 4.0, volumeScale = 0.45f)
                kotlinx.coroutines.delay(1000)
                playSynthesizedChime(f0 = 1536.0, durationSeconds = 4.5, volumeScale = 0.70f)
                kotlinx.coroutines.delay(1000)
                playSynthesizedChime(f0 = 1024.0, durationSeconds = 7.5, volumeScale = 1.00f)
            }
        }
    }

    /**
     * Auditions the approved Option C 3-bell countdown sequence:
     * - Strike 1 (3s remaining): 2048 Hz (high vibration crystalline cue, 45% volume)
     * - Strike 2 (2s remaining): 1536 Hz (centering chime, 70% volume)
     * - Strike 3 (1s remaining / finish): 1024 Hz (deep resonance finale, 100% volume with 7.5s sustain)
     */
    fun playOptionCPreview() {
        playCompletionBell()
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
