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

class AudioBellManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var soundPool: SoundPool? = null

    private var intervalSoundId = 0
    private var completionSoundId = 0
    private var isLoaded = false

    private var bellVolume = 0.9f
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        initSoundPool()
    }

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
                    if (status == 0) isLoaded = true
                }
            }

        loadSounds()
    }

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
        } catch (_: Exception) {}
    }

    fun setVolume(volume: Float) {
        bellVolume = volume.coerceIn(0f, 1f)
    }

    fun playIntervalBell() {
        requestTransientAudioFocus()
        if (isLoaded && intervalSoundId != 0) {
            soundPool?.play(intervalSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
        } else {
            // Soothing warm 432Hz chime with 8.5s duration and smooth boundary-free finish
            scope.launch {
                playSynthesizedChime(f0 = 432.0, durationSeconds = 8.5, volumeScale = 0.88f)
            }
        }
    }

    fun playCompletionBell() {
        requestTransientAudioFocus()
        if (isLoaded && completionSoundId != 0) {
            soundPool?.play(completionSoundId, bellVolume, bellVolume, 1, 0, 1.0f)
        } else {
            // User Requirement 5: Bell 3-2-1 with smooth seamless decay and zero boundary
            scope.launch {
                playSynthesizedChime(f0 = 528.0, durationSeconds = 6.0, volumeScale = 0.42f)
                kotlinx.coroutines.delay(2200)
                playSynthesizedChime(f0 = 432.0, durationSeconds = 7.5, volumeScale = 0.72f)
                kotlinx.coroutines.delay(2600)
                playSynthesizedChime(f0 = 360.0, durationSeconds = 8.0, volumeScale = 1.00f)
            }
        }
    }

    private fun requestTransientAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* Background music resumes automatically */ }
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
     */
    private fun playSynthesizedChime(f0: Double, durationSeconds: Double, volumeScale: Float = 1.0f) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * durationSeconds).toInt()
            val buffer = ShortArray(numSamples)
            val attackSamples = (sampleRate * 0.050).toInt()
            val fadeOutStartSec = durationSeconds - 2.0

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val attack = if (i < attackSamples) {
                    0.5 * (1.0 - Math.cos(Math.PI * i / attackSamples))
                } else {
                    Math.exp(-(t - 0.050) * 0.52)
                }

                val tailFade = if (t >= fadeOutStartSec) {
                    0.5 * (1.0 + Math.cos(Math.PI * (t - fadeOutStartSec) / 2.0))
                } else 1.0

                val beating = 1.0 + 0.07 * sin(2 * Math.PI * 1.08 * t)
                val sFund = sin(2 * Math.PI * f0 * t)
                val sSub = 0.25 * sin(2 * Math.PI * (f0 * 0.5) * t) * Math.exp(-t * 0.35)
                val sThird = 0.18 * sin(2 * Math.PI * (f0 * 1.5) * t) * Math.exp(-t * 0.75)
                val sOct = 0.20 * sin(2 * Math.PI * (f0 * 2.0) * t) * Math.exp(-t * 1.0)
                val sFifth = 0.06 * sin(2 * Math.PI * (f0 * 3.0) * t) * Math.exp(-t * 1.5)

                val sample = (sFund + sSub + sThird + sOct + sFifth) * attack * tailFade * beating * bellVolume * volumeScale
                val saturated = tanh(sample * 0.85) * 0.95
                buffer[i] = (saturated * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

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
        } catch (_: Exception) {}
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
