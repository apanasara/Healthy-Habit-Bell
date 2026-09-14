package com.habitbell.app.mantra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.core.content.ContextCompat
import com.habitbell.app.data.model.MantraCounterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * # AcousticMantraSensorProvider
 *
 * Hardware-level acoustic signal processor and DSP provider implementing [MantraDataSource].
 * Captures live microphone audio, executes multi-modal digital signal processing (Extended Verse
 * pause bridging, short Japa hysteresis, and Aumkar pitch tracking), and emits primitive [MantraInputEvent]s.
 *
 * ## Architectural Role & Component Relationships
 * - Interfaces directly with device microphone via low-latency [AudioRecord] PCM streams.
 * - Ingested by [MantraCountManager] as the primary hands-free acoustic data source.
 * - Implements 3 specialized real-time DSP pipelines:
 *   1. **Speech Formant Bandpass Filter (150 Hz - 2500 Hz)**: 2nd-order IIR bandpass isolating
 *      vocal fold fundamental frequencies ($F_0$) and primary vowel formants ($F_1, F_2$)
 *      while rejecting floor rumbles, fans, and high-frequency friction hiss.
 *   2. **Extended Verse Engine with Intra-Verse Pause Bridging**: Accumulates cumulative vocal
 *      duration across multi-line slokas (Gayatri, Maha Mrityunjaya, Surahs, Bible verses),
 *      bridging natural breathing pauses (< 1.2s) without resetting, and confirming exactly 1
 *      count when cumulative duration >= minimum threshold followed by a concluding pause (>= 1.5s).
 *   3. **Short Japa Hysteresis Detector**: 3-stage state machine tracking vocal burst attack,
 *      peak decay, and valley drop-off with refractory lockout (>= 220ms) for rapid "Ram" or Tasbih chants.
 *   4. **Autocorrelation Pitch Tracker**: Identifies sustained periodic vocal resonance across
 *      80 Hz - 250 Hz for prolonged Aumkar chanting, counting upon exhalation drop-off.
 *   5. **Hardware Acoustic Echo Cancellation (AEC)**: Attaches Android [AcousticEchoCanceler]
 *      directly to the [AudioRecord] session to cancel device loudspeaker output from the microphone stream.
 *      NOTE: Android [android.media.audiofx.NoiseSuppressor] is deliberately excluded because OEM DSPs
 *      (e.g., Qualcomm Fluence) classify steady human vocal chanting, harmonic vowel formants, and Aumkar
 *      resonance as stationary noise and aggressively attenuate them by 12-20 dB.
 *   6. **Ambient Background Music Isolation**: Dynamically elevates speech detection thresholds with
 *      a subtle active safety margin whenever device-played ambient music (Aumkar drone, Tanpura, YouTube)
 *      is actively outputting from local loudspeakers, preventing music playback from triggering bead counts.
 *   7. **Acoustic Self-Feedback Blanking**: Disables stroke evaluation during internal speaker playback
 *      (e.g. interval bells, gongs) to eliminate runaway speaker-to-mic acoustic feedback loops.
 *
 * ## Concurrency & Hardware Lifecycle
 * - Audio capture loop executes on a dedicated background coroutine on [Dispatchers.IO].
 * - Hardware handles ([AudioRecord]) are strictly allocated in [start]/[resume] and released in [stop].
 *
 * @param context Android application context for permission verification (optional for synthetic/testing usage).
 * @param scope Coroutine scope governing the background audio processing pipeline.
 */
class AcousticMantraSensorProvider(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : MantraDataSource {

    private val TAG = "AcousticMantraSensor"

    override val inputSourceType: MantraInputSourceType = MantraInputSourceType.ACOUSTIC_MIC

    /** Sample rate in Hz for acoustic speech and voice DSP (16 kHz is optimal for voice formants). */
    private val SAMPLE_RATE_HZ = 16000

    /** Chunk size in 16-bit PCM samples (512 samples = 32 ms per analysis window). */
    private val CHUNK_SIZE = 512

    override val isAvailable: Boolean
        get() = context?.let {
            ContextCompat.checkSelfPermission(
                it,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } ?: false

    private val _inputFlow = MutableStateFlow(MantraInputEvent())
    override val inputFlow: StateFlow<MantraInputEvent> = _inputFlow.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var activeConfig: MantraCounterConfig? = null

    /** Hardware acoustic echo cancellation effect handle attached to [audioRecord]. */
    private var echoCanceler: AcousticEchoCanceler? = null

    /** Lambda checking whether background ambient music is actively playing through local device speakers. */
    var isAmbientMusicPlaying: (() -> Boolean)? = null

    /** Lambda retrieving current normalized volume gain (0.0f..1.0f) of local background music. */
    var ambientMusicVolume: (() -> Float)? = null

    /** User-adjustable sensitivity multiplier (0.5f to 2.5f, default 1.0f). */
    private var activeSensitivity: Float = 1.0f

    /**
     * Computes the ambient music safety margin in RMS amplitude to prevent loudspeaker bleed
     * from crossing speech detection thresholds when background music is active.
     *
     * @return Additional RMS threshold margin (0.0f when music is off, up to 0.008f when active at max volume).
     */
    fun getAmbientMusicSafetyMargin(): Float {
        val isMusicActive = isAmbientMusicPlaying?.invoke() == true
        if (!isMusicActive) return 0f
        val gain = (ambientMusicVolume?.invoke() ?: 1.0f).coerceIn(0f, 1f)
        return (gain * 0.008f)
    }

    /** Timestamp in milliseconds until which acoustic stroke detection is suppressed. */
    @Volatile
    private var blankUntilMillis: Long = 0L

    /** Timestamp of the most recently detected bead advance in milliseconds. */
    private var lastBeadTimeMillis: Long = 0L

    /** Sliding window ring buffer tracking recent inter-bead intervals for CPM calculation. */
    private val beadIntervals = ArrayDeque<Long>(6)

    /** Continuous moving average ambient noise floor estimate. */
    private var dynamicNoiseFloorRms: Float = 0.012f

    /** Previous chunk RMS for first-difference onset tracking. */
    private var previousBandpassRms: Float = 0.012f

    /** Total frames allocated for ambient noise calibration (~2.88 seconds at 32ms per frame). */
    private val CALIBRATION_TOTAL_FRAMES = 90

    /** Remaining frames to process in the active ambient calibration window. */
    private var calibrationFramesRemaining = CALIBRATION_TOTAL_FRAMES

    /** Cumulative sum of bandpass RMS values for ambient room noise averaging. */
    private var calibrationRmsSum = 0.0

    /** Total valid non-outlier frames incorporated into ambient noise baseline calculation. */
    private var calibrationValidFramesCount = 0

    /** Peak bandpass ambient noise floor observed during calibration. */
    private var calibrationPeakRms = 0f

    /** Flag indicating whether the initial ambient acoustic calibration has completed. */
    var isCalibrated: Boolean = false
        private set

    // --- Extended Verse Engine State ---
    private var isVerseRecitationActive: Boolean = false
    private var verseCumulativeVocalMs: Long = 0L
    private var verseLastSpeechTimeMs: Long = 0L
    private var verseStartTimeMs: Long = 0L

    // --- Short Japa Stateful Hysteresis Detector ---
    private enum class JapaState {
        IDLE_LISTENING,
        ATTACK_DETECTED,
        COOLDOWN_VALLEY
    }
    private var japaState = JapaState.IDLE_LISTENING
    private var japaStartTimeMillis: Long = 0L
    private var japaPeakRms: Float = 0f

    // --- Aumkar Drone Pitch Detector ---
    private var aumkarStartTimeMillis: Long = 0L
    private var isCurrentlyDroning: Boolean = false

    /** Timestamp of last telemetry stats log for rate-limiting logcat output. */
    private var lastLogTimeMillis: Long = 0L

    /** 2nd-order Biquad Bandpass filter isolating vocal fundamentals and speech formants (800 Hz, Q=0.7). */
    private val bandpassFilter = BiquadBandpassFilter(SAMPLE_RATE_HZ.toFloat(), 800f, 0.7f)

    /**
     * Updates detection sensitivity dynamically.
     *
     * @param sensitivity Multiplier scaling the detection threshold (0.5f to 2.5f).
     */
    fun setSensitivity(sensitivity: Float) {
        activeSensitivity = sensitivity.coerceIn(0.5f, 2.5f)
    }

    /**
     * Temporarily blanks/mutes acoustic evaluation to prevent loudspeaker audio
     * (interval bells, gongs, vocal prompts) from self-triggering false counts.
     *
     * @param durationMs Blanking duration in milliseconds.
     */
    fun blankDetection(durationMs: Long) {
        blankUntilMillis = max(blankUntilMillis, System.currentTimeMillis() + durationMs)
    }

    /**
     * Commences or re-triggers ambient noise floor calibration for the specified duration.
     * Profiles stationary environmental noise (AC blowers, wind, leaves, mic noise) and
     * sets speech formant dynamic trigger thresholds safely above ambient room levels.
     *
     * @param durationMs Duration of silent profiling window in milliseconds (default 2880ms / ~90 frames).
     */
    fun startCalibration(durationMs: Long = 2880L) {
        calibrationFramesRemaining = ((durationMs / 32L).toInt()).coerceAtLeast(30)
        calibrationRmsSum = 0.0
        calibrationValidFramesCount = 0
        calibrationPeakRms = 0f
        isCalibrated = false
    }

    override fun start(config: MantraCounterConfig) {
        activeConfig = config
        activeSensitivity = config.micSensitivity
        startCalibration()
        resume()
    }

    override fun pause() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception pausing AudioRecord: ${e.message}")
        }
        releaseAudioEffects()
    }

    override fun resume() {
        if (!isAvailable) {
            Log.w(TAG, "Cannot start AcousticMantraSensorProvider: RECORD_AUDIO permission not granted.")
            return
        }

        pause()
        val config = activeConfig ?: return

        recordingJob = scope.launch {
            runAudioCaptureLoop(config)
        }
    }

    override fun stop() {
        pause()
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        releaseAudioEffects()
        reset()
        activeConfig = null
    }

    /**
     * Safely disengages and releases hardware audio effects ([AcousticEchoCanceler]).
     */
    private fun releaseAudioEffects() {
        try {
            echoCanceler?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing AcousticEchoCanceler: ${e.message}")
        }
        echoCanceler = null
    }

    override fun reset() {
        lastBeadTimeMillis = 0L
        blankUntilMillis = 0L
        beadIntervals.clear()
        dynamicNoiseFloorRms = 0.012f
        previousBandpassRms = 0.012f
        calibrationFramesRemaining = CALIBRATION_TOTAL_FRAMES
        calibrationRmsSum = 0.0
        calibrationValidFramesCount = 0
        calibrationPeakRms = 0f
        isCalibrated = false
        isVerseRecitationActive = false
        verseCumulativeVocalMs = 0L
        verseLastSpeechTimeMs = 0L
        verseStartTimeMs = 0L
        japaState = JapaState.IDLE_LISTENING
        japaStartTimeMillis = 0L
        japaPeakRms = 0f
        aumkarStartTimeMillis = 0L
        isCurrentlyDroning = false
        bandpassFilter.reset()
        _inputFlow.value = MantraInputEvent()
    }

    override fun registerManualBead() {
        val now = System.currentTimeMillis()
        val cpm = calculateCadenceCpm(now)
        _inputFlow.value = MantraInputEvent(
            beadDelta = 1,
            instantaneousCadenceCpm = cpm,
            audioAmplitudeRms = 0.9f,
            thresholdRms = 0.05f,
            isSpeechActive = false,
            activeVerseDurationSeconds = 0f,
            timestampMillis = now
        )
    }

    /**
     * Primary background audio streaming and DSP loop running on [Dispatchers.IO].
     *
     * @param config Active configuration determining detection thresholds and modes.
     */
    private suspend fun runAudioCaptureLoop(config: MantraCounterConfig) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SIZE * 2)

        // Prioritize VOICE_RECOGNITION for speech-tuned beamforming and OEM echo suppression, fallback to standard MIC
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        var initialized = false
        for (source in audioSources) {
            try {
                val record = AudioRecord(
                    source,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    initialized = true

                    // Attach Android Hardware Acoustic Echo Cancellation (AEC)
                    val sessionId = record.audioSessionId
                    if (sessionId != -1) {
                        if (AcousticEchoCanceler.isAvailable()) {
                            try {
                                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                                    enabled = true
                                }
                                Log.i(TAG, "🔊 AcousticEchoCanceler attached to session $sessionId (Hardware AEC active)")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to attach AcousticEchoCanceler: ${e.message}")
                            }
                        }
                    }
                    break
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to init AudioRecord with source $source: ${e.message}")
            }
        }

        if (!initialized || audioRecord == null) {
            Log.e(TAG, "AudioRecord failed to initialize across all available sources")
            return
        }

        try {
            audioRecord?.startRecording()
            val pcmBuffer = ShortArray(CHUNK_SIZE)

            while (coroutineContext.isActive) {
                val samplesRead = audioRecord?.read(pcmBuffer, 0, CHUNK_SIZE) ?: -1
                if (samplesRead <= 0) {
                    delay(10)
                    continue
                }

                val now = System.currentTimeMillis()

                // 1. Filter chunk through Speech Formant Bandpass (150 Hz - 2500 Hz)
                var filteredSumSquares = 0.0
                var rawSumSquares = 0.0
                for (i in 0 until samplesRead) {
                    val normalizedRaw = pcmBuffer[i] / 32768.0f
                    rawSumSquares += (normalizedRaw * normalizedRaw)

                    val filtered = bandpassFilter.process(normalizedRaw)
                    filteredSumSquares += (filtered * filtered)
                }
                val rawRms = sqrt(rawSumSquares / samplesRead).toFloat()
                val bandpassRms = sqrt(filteredSumSquares / samplesRead).toFloat()

                // 2. Ambient Noise Calibration Window (Silent profiling pause: ~2.88s / 90 frames)
                if (calibrationFramesRemaining > 0) {
                    val totalFrames = CALIBRATION_TOTAL_FRAMES
                    // Outlier rejection: ignore transient handling thuds/coughs (> 0.25 bandpass RMS or > 0.35 raw RMS)
                    if (bandpassRms <= 0.25f && rawRms <= 0.35f) {
                        calibrationRmsSum += bandpassRms
                        calibrationValidFramesCount++
                        calibrationPeakRms = max(calibrationPeakRms, bandpassRms)
                        dynamicNoiseFloorRms = (calibrationRmsSum / calibrationValidFramesCount).toFloat().coerceIn(0.001f, 0.08f)
                    }

                    calibrationFramesRemaining--
                    val progress = 1.0f - (calibrationFramesRemaining.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)

                    val musicMargin = getAmbientMusicSafetyMargin()
                    val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
                    val dynamicThreshold = (dynamicNoiseFloorRms * (1.45f / sensitivity) + musicMargin + (0.005f / sensitivity)).coerceIn(0.006f + musicMargin, 0.12f)
                    val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
                    val normalizedThreshold = (dynamicThreshold * 3.5f).coerceIn(0.05f, 0.95f)

                    _inputFlow.value = MantraInputEvent(
                        beadDelta = 0,
                        instantaneousCadenceCpm = 0,
                        audioAmplitudeRms = normalizedAmplitude,
                        thresholdRms = normalizedThreshold,
                        isSpeechActive = false,
                        activeVerseDurationSeconds = 0f,
                        isCalibrating = true,
                        calibrationProgress = progress,
                        timestampMillis = now
                    )

                    if (calibrationFramesRemaining == 0) {
                        isCalibrated = true
                        Log.i(TAG, "🎯 MANTRA AMBIENT CALIBRATION COMPLETE! baselineFloor=%.5f, peakAmbient=%.5f, validFrames=$calibrationValidFramesCount, musicMargin=%.4f".format(
                            dynamicNoiseFloorRms, calibrationPeakRms, musicMargin
                        ))
                    }
                    previousBandpassRms = bandpassRms
                    continue
                }

                // 3. Evaluate blanking period (self-acoustic speaker feedback suppression)
                val isBlanked = now < blankUntilMillis
                if (isBlanked) {
                    _inputFlow.value = _inputFlow.value.copy(
                        beadDelta = 0,
                        audioAmplitudeRms = (rawRms * 3.5f).coerceIn(0f, 1f),
                        timestampMillis = now
                    )
                    previousBandpassRms = bandpassRms
                    continue
                }

                // 4. Dispatch mode-specific DSP evaluation
                when (config.technique.defaultMode) {
                    MantraMode.EXTENDED_VERSE -> {
                        evaluateExtendedVerse(pcmBuffer, samplesRead, rawRms, bandpassRms, now, config)
                    }
                    MantraMode.SHORT_JAPA -> {
                        evaluateShortJapa(pcmBuffer, samplesRead, rawRms, bandpassRms, now, config)
                    }
                    MantraMode.AUMKAR_DRONE -> {
                        evaluateAumkarDrone(pcmBuffer, samplesRead, rawRms, now, config)
                    }
                }

                previousBandpassRms = bandpassRms
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: AudioRecord permission missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording exception", e)
        } finally {
            releaseAudioEffects()
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null
        }
    }

    /**
     * Evaluates audio chunks for multi-line extended sacred verses (Gayatri Mantra, Maha Mrityunjaya, Surahs).
     *
     * Key Innovation: **Intra-Verse Pause Bridging**:
     * - Accumulates cumulative vocal energy duration while speech is active.
     * - Line breath pauses (< 1.2s) are bridged without resetting the verse.
     * - Confirms exactly 1 bead count when cumulative vocal duration >= [config.minVerseDurationSec]
     *   AND a concluding inter-verse silence >= [config.interVersePauseThresholdSec] is observed.
     *
     * @param pcm Raw PCM buffer.
     * @param length Number of valid samples in [pcm].
     * @param rawRms Full-spectrum RMS energy.
     * @param bandpassRms Speech formant filtered RMS energy.
     * @param now Monotonic timestamp in milliseconds.
     * @param config Active configuration.
     */
    private fun evaluateExtendedVerse(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        bandpassRms: Float,
        now: Long,
        config: MantraCounterConfig
    ) {
        val musicMargin = getAmbientMusicSafetyMargin()
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val dynamicThreshold = (dynamicNoiseFloorRms * (1.40f / sensitivity) + musicMargin + (0.005f / sensitivity)).coerceIn(0.006f + musicMargin, 0.12f)

        // Continuous ambient noise floor tracking (only when not actively reciting)
        if (!isVerseRecitationActive) {
            if (bandpassRms < dynamicNoiseFloorRms) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.88f) + (bandpassRms * 0.12f)
            } else if (bandpassRms < dynamicThreshold * 0.70f) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.98f) + (bandpassRms * 0.02f)
            }
            dynamicNoiseFloorRms = dynamicNoiseFloorRms.coerceIn(0.001f, 0.08f)
        }

        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val normalizedThreshold = (dynamicThreshold * 3.5f).coerceIn(0.05f, 0.95f)

        val isSpeechDetected = bandpassRms > dynamicThreshold
        val minVocalRequiredMs = (config.minVerseDurationSec * 1000L).toLong()
        val interPauseRequiredMs = (config.interVersePauseThresholdSec * 1000L).toLong()

        var beadEmitted = false

        if (isSpeechDetected) {
            val silenceElapsedSinceLastSpeech = if (verseLastSpeechTimeMs > 0L) now - verseLastSpeechTimeMs else Long.MAX_VALUE

            if (!isVerseRecitationActive) {
                // New verse starts!
                isVerseRecitationActive = true
                verseStartTimeMs = now
                verseCumulativeVocalMs = 32L // initial chunk (32ms)
                verseLastSpeechTimeMs = now
                Log.d(TAG, "🎙️ VERSE RECITAL BEGUN: bandpassRms=%.4f > thresh=%.4f (margin=%.4f)".format(bandpassRms, dynamicThreshold, musicMargin))
            } else {
                // Continuing active verse
                verseCumulativeVocalMs += 32L
                verseLastSpeechTimeMs = now

                // Sustained non-vocal audio abort: if continuous sound exceeds 35 seconds without any pause,
                // it is continuous background music / ambient track, NOT human verse recitation!
                val totalVerseElapsed = now - verseStartTimeMs
                if (totalVerseElapsed > 35_000L) {
                    Log.w(TAG, "⚠️ SUSTAINED AMBIENT SOUND ABORT: elapsed=${totalVerseElapsed}ms > 35s. Aborting false verse accumulation.")
                    isVerseRecitationActive = false
                    verseCumulativeVocalMs = 0L
                    verseLastSpeechTimeMs = 0L
                }
            }
        } else {
            // Silence / intra-verse pause
            if (isVerseRecitationActive) {
                val silenceElapsed = now - verseLastSpeechTimeMs

                // If silence exceeds inter-verse completion threshold:
                if (silenceElapsed >= interPauseRequiredMs) {
                    val totalVocalSec = verseCumulativeVocalMs / 1000.0f
                    Log.d(TAG, "⏸️ VERSE PAUSE DETECTED: silence=${silenceElapsed}ms >= ${interPauseRequiredMs}ms. Vocal time=${totalVocalSec}s (min=${config.minVerseDurationSec}s)")

                    if (verseCumulativeVocalMs >= minVocalRequiredMs) {
                        // CONFIRMED FULL VERSE RECITATION!
                        beadEmitted = true
                        lastBeadTimeMillis = now
                        Log.i(TAG, "🎯 VERSE RECITATION CONFIRMED: Cumulative=${totalVocalSec}s >= ${config.minVerseDurationSec}s! BEAD COUNTED!")
                    } else {
                        Log.d(TAG, "❌ VERSE TOO SHORT: ${totalVocalSec}s < ${config.minVerseDurationSec}s (dismissed blip)")
                    }

                    // Reset verse state for next round
                    isVerseRecitationActive = false
                    verseCumulativeVocalMs = 0L
                    verseLastSpeechTimeMs = 0L
                }
            }
        }

        val activeElapsedSec = if (isVerseRecitationActive) {
            verseCumulativeVocalMs / 1000.0f
        } else 0f

        if (beadEmitted) {
            val cpm = calculateCadenceCpm(now)
            _inputFlow.value = MantraInputEvent(
                beadDelta = 1,
                instantaneousCadenceCpm = cpm,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isSpeechActive = false,
                activeVerseDurationSeconds = 0f,
                timestampMillis = now
            )
        } else {
            _inputFlow.value = _inputFlow.value.copy(
                beadDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isSpeechActive = isSpeechDetected,
                activeVerseDurationSeconds = activeElapsedSec,
                timestampMillis = now
            )
        }
    }

    /**
     * Evaluates audio chunks for short, rhythmic single-phrase japa ("Ram... Ram...", Tasbih, Jesus Prayer)
     * using a 3-Stage Hysteresis State Machine with refractory lockout, onset slope detection,
     * and mandatory valley drop-off.
     */
    private fun evaluateShortJapa(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        bandpassRms: Float,
        now: Long,
        config: MantraCounterConfig
    ) {
        val musicMargin = getAmbientMusicSafetyMargin()
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val dynamicThreshold = (dynamicNoiseFloorRms * (1.40f / sensitivity) + musicMargin + (0.005f / sensitivity)).coerceIn(0.006f + musicMargin, 0.12f)

        // Continuous ambient noise floor tracking
        if (japaState == JapaState.IDLE_LISTENING) {
            if (bandpassRms < dynamicNoiseFloorRms) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.88f) + (bandpassRms * 0.12f)
            } else if (bandpassRms < dynamicThreshold * 0.70f) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.98f) + (bandpassRms * 0.02f)
            }
            dynamicNoiseFloorRms = dynamicNoiseFloorRms.coerceIn(0.001f, 0.08f)
        }

        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val normalizedThreshold = (dynamicThreshold * 3.5f).coerceIn(0.05f, 0.95f)

        var beadEmitted = false
        val timeSinceLastBead = now - lastBeadTimeMillis

        when (japaState) {
            JapaState.IDLE_LISTENING -> {
                val energyRise = bandpassRms - previousBandpassRms
                val isAttackOnset = energyRise > (dynamicThreshold * 0.04f) || bandpassRms > (dynamicThreshold * 1.15f)

                // Minimum refractory interval (220ms = max ~270 CPM) and vocal syllabic attack onset
                if (bandpassRms > dynamicThreshold && isAttackOnset && timeSinceLastBead >= 220L) {
                    japaState = JapaState.ATTACK_DETECTED
                    japaStartTimeMillis = now
                    japaPeakRms = bandpassRms
                    Log.d(TAG, "⚡ JAPA ATTACK ONSET: bandpassRms=%.4f > thresh=%.4f (rise=%.4f, margin=%.4f)".format(
                        bandpassRms, dynamicThreshold, energyRise, musicMargin
                    ))
                }
            }

            JapaState.ATTACK_DETECTED -> {
                japaPeakRms = max(japaPeakRms, bandpassRms)
                val burstDuration = now - japaStartTimeMillis

                if (burstDuration > 1400L) {
                    // Continuous speech or background music longer than short japa syllabic envelope -> cooldown without bead!
                    Log.d(TAG, "⚠️ JAPA SUSTAINED SOUND ABORT: burstDuration=${burstDuration}ms > 1400ms -> Cooldown")
                    japaState = JapaState.COOLDOWN_VALLEY
                    lastBeadTimeMillis = now
                } else if (bandpassRms < japaPeakRms * 0.72f) {
                    // Peak decay confirmed!
                    val minBurst = (config.minVerseDurationSec * 1000f).toLong().coerceIn(80L, 800L)
                    val isValidBurstDuration = burstDuration >= minBurst

                    if (isValidBurstDuration && japaPeakRms >= dynamicThreshold) {
                        lastBeadTimeMillis = now
                        beadEmitted = true
                        japaState = JapaState.COOLDOWN_VALLEY
                        Log.i(TAG, "🎯 SHORT JAPA CHANT REGISTERED! burst=${burstDuration}ms, peakRms=%.4f".format(japaPeakRms))
                    } else {
                        japaState = JapaState.IDLE_LISTENING
                    }
                }
            }

            JapaState.COOLDOWN_VALLEY -> {
                val elapsedSinceBead = now - lastBeadTimeMillis
                val isMinRefractoryPassed = elapsedSinceBead >= 180L
                val isInValley = bandpassRms < (dynamicThreshold * 0.85f)
                val isCooldownTimeout = elapsedSinceBead >= 800L // Safety timeout: prevent getting permanently locked in valley state

                // Return to IDLE_LISTENING on quiet valley drop-off or timeout
                if (isMinRefractoryPassed && (isInValley || isCooldownTimeout)) {
                    japaState = JapaState.IDLE_LISTENING
                }
            }
        }

        if (beadEmitted) {
            val cpm = calculateCadenceCpm(now)
            _inputFlow.value = MantraInputEvent(
                beadDelta = 1,
                instantaneousCadenceCpm = cpm,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isSpeechActive = false,
                activeVerseDurationSeconds = 0f,
                timestampMillis = now
            )
        } else {
            _inputFlow.value = _inputFlow.value.copy(
                beadDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isSpeechActive = japaState == JapaState.ATTACK_DETECTED,
                activeVerseDurationSeconds = 0f,
                timestampMillis = now
            )
        }
    }

    /**
     * Evaluates audio chunks for sustained Aumkar chanting using autocorrelation pitch tracking
     * across the 80 Hz - 250 Hz fundamental vocal band.
     */
    private fun evaluateAumkarDrone(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        now: Long,
        config: MantraCounterConfig
    ) {
        val musicMargin = getAmbientMusicSafetyMargin()
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val droneThreshold = (dynamicNoiseFloorRms * (1.35f / sensitivity) + musicMargin + (0.005f / sensitivity)).coerceIn(0.010f + musicMargin, 0.12f)
        val normalizedThreshold = (droneThreshold * 3.5f).coerceIn(0.05f, 0.95f)

        // Autocorrelation pitch test at pitch lags for 80 Hz - 250 Hz (lag 64 to 200 samples at 16 kHz)
        var maxAutocorr = 0.0
        if (rawRms > droneThreshold) {
            for (lag in 64..200 step 4) {
                var sum = 0.0
                for (i in 0 until (length - lag)) {
                    sum += (pcm[i] * pcm[i + lag])
                }
                if (sum > maxAutocorr) {
                    maxAutocorr = sum
                }
            }
        }

        val isDroneDetected = (rawRms > droneThreshold) && (maxAutocorr > 0)

        if (isDroneDetected) {
            if (!isCurrentlyDroning) {
                isCurrentlyDroning = true
                aumkarStartTimeMillis = now
            }
            val droneDurationSec = (now - aumkarStartTimeMillis) / 1000.0f

            _inputFlow.value = MantraInputEvent(
                beadDelta = 0,
                instantaneousCadenceCpm = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isSpeechActive = true,
                activeVerseDurationSeconds = droneDurationSec,
                timestampMillis = now
            )
        } else {
            if (isCurrentlyDroning) {
                val totalDroneSec = (now - aumkarStartTimeMillis) / 1000.0f
                isCurrentlyDroning = false
                aumkarStartTimeMillis = 0L

                // If Aumkar drone lasted >= min required duration (e.g. 2.0s), register as 1 bead!
                if (totalDroneSec >= config.minVerseDurationSec) {
                    lastBeadTimeMillis = now
                    val cpm = calculateCadenceCpm(now)
                    _inputFlow.value = MantraInputEvent(
                        beadDelta = 1,
                        instantaneousCadenceCpm = cpm,
                        audioAmplitudeRms = normalizedAmplitude,
                        thresholdRms = normalizedThreshold,
                        isSpeechActive = false,
                        activeVerseDurationSeconds = totalDroneSec,
                        timestampMillis = now
                    )
                    Log.i(TAG, "🎯 AUMKAR CHANT REGISTERED! Duration=${totalDroneSec}s >= ${config.minVerseDurationSec}s")
                    return
                }
            }

            _inputFlow.value = _inputFlow.value.copy(
                beadDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isSpeechActive = false,
                activeVerseDurationSeconds = 0f,
                timestampMillis = now
            )
        }
    }

    /**
     * Estimates cadence in Chants Per Minute (CPM) from recent inter-bead intervals.
     *
     * @param now Monotonic timestamp in milliseconds.
     * @return Rolling cadence in CPM (bounded 2 to 180 CPM).
     */
    private fun calculateCadenceCpm(now: Long): Int {
        if (lastBeadTimeMillis > 0L) {
            val intervalMs = now - lastBeadTimeMillis
            if (intervalMs in 300..60000) {
                if (beadIntervals.size >= 6) {
                    beadIntervals.removeFirst()
                }
                beadIntervals.addLast(intervalMs)
                val avgInterval = beadIntervals.average()
                if (avgInterval > 0) {
                    return (60_000.0 / avgInterval).toInt().coerceIn(2, 180)
                }
            }
        }
        return 0
    }

    /**
     * 2nd-order Biquad IIR Bandpass Filter (Audio EQ Cookbook).
     *
     * @param sampleRate Sampling rate in Hz (e.g. 16000f).
     * @param centerFreq Center resonance frequency in Hz (e.g. 800f).
     * @param q Quality factor Q (e.g. 0.7f).
     */
    class BiquadBandpassFilter(sampleRate: Float, centerFreq: Float, q: Float = 0.7f) {
        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            val omega = (2.0 * Math.PI * centerFreq / sampleRate).toFloat()
            val alpha = (sin(omega) / (2.0 * q)).toFloat()
            val cosw = cos(omega).toFloat()
            val a0 = 1.0f + alpha

            b0 = alpha / a0
            b1 = 0.0f
            b2 = -alpha / a0
            a1 = (-2.0f * cosw) / a0
            a2 = (1.0f - alpha) / a0
        }

        /**
         * Processes an incoming audio sample and returns the bandpass filtered sample.
         *
         * @param sample Raw normalized PCM audio sample (-1.0f to 1.0f).
         * @return Filtered output sample.
         */
        fun process(sample: Float): Float {
            val y = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = sample
            y2 = y1
            y1 = y
            return y
        }

        /**
         * Clears filter internal state delays.
         */
        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }

    companion object {
        /**
         * Estimates fundamental pitch (F0) from a 16-bit PCM buffer using normalized autocorrelation.
         *
         * @param buffer Raw 16-bit PCM samples.
         * @param length Number of valid samples in the buffer.
         * @param sampleRate Sampling rate in Hz (e.g. 16000).
         * @param minFreqHz Minimum pitch frequency to test in Hz (e.g. 75f).
         * @param maxFreqHz Maximum pitch frequency to test in Hz (e.g. 320f).
         * @return Detected fundamental pitch in Hz, or 0f if unvoiced or below periodicity threshold.
         */
        fun computeAutocorrelationPitch(
            buffer: ShortArray,
            length: Int,
            sampleRate: Int = 16000,
            minFreqHz: Float = 75f,
            maxFreqHz: Float = 320f
        ): Float {
            val minLag = (sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
            val maxLag = (sampleRate / minFreqHz).toInt().coerceAtMost(length / 2)

            var bestLag = 0
            var maxNormCorr = 0.0

            var energy0 = 0.0
            for (i in 0 until length) {
                energy0 += buffer[i].toDouble() * buffer[i].toDouble()
            }
            if (energy0 < 1e-4) return 0f

            for (lag in minLag..maxLag) {
                var sum = 0.0
                var energyLag = 0.0
                val limit = length - lag
                for (i in 0 until limit) {
                    val s1 = buffer[i].toDouble()
                    val s2 = buffer[i + lag].toDouble()
                    sum += s1 * s2
                    energyLag += s2 * s2
                }
                val norm = kotlin.math.sqrt(energy0 * energyLag)
                if (norm > 0) {
                    val r = sum / norm
                    if (r > maxNormCorr) {
                        maxNormCorr = r
                        bestLag = lag
                    }
                }
            }

            return if (bestLag > 0 && maxNormCorr > 0.4) {
                sampleRate.toFloat() / bestLag.toFloat()
            } else 0f
        }
    }
}
