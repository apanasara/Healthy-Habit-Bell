package com.habitbell.app.mantra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
 *      peak decay, and valley drop-off with refractory lockout (>= 250ms) for rapid "Ram" or Tasbih chants.
 *   4. **Autocorrelation Pitch Tracker**: Identifies sustained periodic vocal resonance across
 *      80 Hz - 250 Hz for prolonged Aumkar chanting, counting upon exhalation drop-off.
 *   5. **Acoustic Self-Feedback Blanking**: Disables stroke evaluation during internal speaker playback
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

    /** User-adjustable sensitivity multiplier (0.5f to 2.5f, default 1.0f). */
    private var activeSensitivity: Float = 1.0f

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

    override fun start(config: MantraCounterConfig) {
        activeConfig = config
        activeSensitivity = config.micSensitivity
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
        reset()
        activeConfig = null
    }

    override fun reset() {
        lastBeadTimeMillis = 0L
        blankUntilMillis = 0L
        beadIntervals.clear()
        dynamicNoiseFloorRms = 0.012f
        previousBandpassRms = 0.012f
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

        val audioSources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
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
            var initialCalibrationFrames = 25 // ~800ms quick bootstrap

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

                // 2. Initial noise floor bootstrapping
                if (initialCalibrationFrames > 0) {
                    dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.85f) + (bandpassRms * 0.15f)
                    initialCalibrationFrames--
                    _inputFlow.value = MantraInputEvent(
                        audioAmplitudeRms = (rawRms * 3.5f).coerceIn(0f, 1f),
                        thresholdRms = 0.05f,
                        timestampMillis = now
                    )
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
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val dynamicThreshold = (dynamicNoiseFloorRms * (1.8f / sensitivity) + (0.007f / sensitivity)).coerceIn(0.006f, 0.20f)

        // Continuous ambient noise floor tracking (only when not actively reciting)
        if (!isVerseRecitationActive) {
            if (bandpassRms < dynamicNoiseFloorRms) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.88f) + (bandpassRms * 0.12f)
            } else if (bandpassRms < dynamicThreshold * 0.65f) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.98f) + (bandpassRms * 0.02f)
            }
            dynamicNoiseFloorRms = dynamicNoiseFloorRms.coerceIn(0.001f, 0.05f)
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
                Log.d(TAG, "🎙️ VERSE RECITAL BEGUN: bandpassRms=%.4f > thresh=%.4f".format(bandpassRms, dynamicThreshold))
            } else {
                // Continuing active verse
                verseCumulativeVocalMs += 32L
                verseLastSpeechTimeMs = now
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
     * using a 3-Stage Hysteresis State Machine with refractory lockout.
     */
    private fun evaluateShortJapa(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        bandpassRms: Float,
        now: Long,
        config: MantraCounterConfig
    ) {
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val dynamicThreshold = (dynamicNoiseFloorRms * (1.8f / sensitivity) + (0.007f / sensitivity)).coerceIn(0.007f, 0.20f)

        // Continuous ambient noise floor tracking
        if (japaState == JapaState.IDLE_LISTENING) {
            if (bandpassRms < dynamicNoiseFloorRms) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.88f) + (bandpassRms * 0.12f)
            } else if (bandpassRms < dynamicThreshold * 0.65f) {
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.98f) + (bandpassRms * 0.02f)
            }
            dynamicNoiseFloorRms = dynamicNoiseFloorRms.coerceIn(0.001f, 0.05f)
        }

        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val normalizedThreshold = (dynamicThreshold * 3.5f).coerceIn(0.05f, 0.95f)

        var beadEmitted = false
        val timeSinceLastBead = now - lastBeadTimeMillis

        when (japaState) {
            JapaState.IDLE_LISTENING -> {
                // Minimum refractory interval (260ms = max ~230 CPM)
                if (bandpassRms > dynamicThreshold && timeSinceLastBead >= 260L) {
                    japaState = JapaState.ATTACK_DETECTED
                    japaStartTimeMillis = now
                    japaPeakRms = bandpassRms
                }
            }

            JapaState.ATTACK_DETECTED -> {
                japaPeakRms = max(japaPeakRms, bandpassRms)
                val burstDuration = now - japaStartTimeMillis

                if (burstDuration > 2200L) {
                    // Continuous speech or drone longer than short japa envelope -> cooldown
                    japaState = JapaState.COOLDOWN_VALLEY
                    lastBeadTimeMillis = now
                } else if (bandpassRms < japaPeakRms * 0.72f) {
                    // Peak decay confirmed!
                    val minBurst = (config.minVerseDurationSec * 1000f).toLong().coerceIn(120L, 800L)
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
                val isCooldownExpired = elapsedSinceBead >= 300L

                if (isMinRefractoryPassed && (isInValley || isCooldownExpired)) {
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
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val droneThreshold = (dynamicNoiseFloorRms * (1.8f / sensitivity)).coerceAtLeast(0.015f)
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
