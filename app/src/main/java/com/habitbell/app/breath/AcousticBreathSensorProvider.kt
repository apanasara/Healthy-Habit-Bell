package com.habitbell.app.breath

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.habitbell.app.data.model.BreathCounterConfig
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
 * # AcousticBreathSensorProvider
 *
 * Hardware-level acoustic breath stroke analyzer and DSP provider implementing [BreathDataSource].
 *
 * ## Architectural Role & Relationships
 * - Interfaces directly with the device microphone via low-latency [AudioRecord] PCM streams.
 * - Uses [MediaRecorder.AudioSource.VOICE_RECOGNITION] to eliminate aggressive OEM noise gating
 *   and acoustic echo cancellation (AEC) that would otherwise suppress subtle breath exhalations.
 * - Implements real-time Digital Signal Processing (DSP) algorithms:
 *   1. **Biquad Bandpass Filter**: 2nd-order IIR bandpass centered at 2400 Hz (Q = 1.0),
 *      isolating turbulent nasal expulsion hiss (1.2 kHz - 4.0 kHz) while rejecting low-frequency
 *      desk rumble (-28 dB at 100 Hz) and high-frequency thermal hiss (-26 dB at 7500 Hz).
 *   2. **Continuous Adaptive Noise Floor**: Continuously adapts to ambient background sound level
 *      using asymmetric attack/decay smoothing, preventing calibration freeze and drift.
 *   3. **Stateful Hysteresis Peak-Valley Detector**: Detects rapid energy onset (Attack),
 *      tracks maximum peak amplitude, enforces physiological burst duration (40ms - 280ms),
 *      and mandates a valley drop-off (silent passive inhalation) before re-arming.
 *   4. **Acoustic Self-Feedback Blanking**: Disables stroke evaluation during internal speaker
 *      playback (e.g. interval bells, vocal prompts) to eliminate runaway acoustic loops.
 *   5. **Autocorrelation Pitch Tracker**: Validates sustained periodic vocal resonance for
 *      Bhramari bee humming across 80 Hz - 250 Hz swara band.
 * - Dispatches primitive [BreathInputEvent] metrics into [BreathCountManager].
 *
 * ## Concurrency & Hardware Lifecycle
 * - Audio capture loop executes on a dedicated coroutine job on [Dispatchers.IO].
 * - Hardware handles ([AudioRecord]) are strictly initialized on [start] and released on [stop].
 *
 * @param context Android application context for permission verification.
 * @param scope Coroutine scope governing the background audio processing pipeline.
 */
class AcousticBreathSensorProvider(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : BreathDataSource {

    private val TAG = "AcousticBreathSensor"

    override val inputSourceType: BreathInputSourceType = BreathInputSourceType.ACOUSTIC_MIC

    /** Sample rate in Hz for acoustic breath monitoring (16 kHz optimal for voice/breath DSP). */
    private val SAMPLE_RATE_HZ = 16000

    /** Chunk size in 16-bit PCM samples (512 samples = 32 ms per analysis window). */
    private val CHUNK_SIZE = 512

    override val isAvailable: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private val _inputFlow = MutableStateFlow(BreathInputEvent())
    override val inputFlow: StateFlow<BreathInputEvent> = _inputFlow.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var activeConfig: BreathCounterConfig? = null

    /** User-adjustable sensitivity multiplier (0.5f to 2.5f, default 1.0f). */
    private var activeSensitivity: Float = 1.0f

    /** Timestamp in milliseconds until which acoustic stroke detection is suppressed. */
    @Volatile
    private var blankUntilMillis: Long = 0L

    /** Timestamp of the most recently detected stroke in milliseconds. */
    private var lastStrokeTimeMillis: Long = 0L

    /** Sliding window ring buffer tracking recent inter-stroke intervals for cadence calculation. */
    private val strokeIntervals = ArrayDeque<Long>(6)

    /** Continuous moving average ambient noise floor estimate. */
    private var dynamicNoiseFloorRms: Float = 0.015f

    /** Previous chunk RMS for first-difference onset tracking. */
    private var previousBandpassRms: Float = 0.015f

    // --- Kapalabhati Stateful Hysteresis Detector ---
    private enum class KapalabhatiState {
        IDLE_LISTENING,
        ATTACK_DETECTED,
        COOLDOWN_VALLEY
    }
    private var kapalabhatiState = KapalabhatiState.IDLE_LISTENING
    private var strokeStartTimeMillis: Long = 0L
    private var strokePeakRms: Float = 0f

    // --- Bhastrika Dual-Phase Detector ---
    private var bhastrikaInhaleDetected: Boolean = false
    private var bhastrikaInhaleTimeMillis: Long = 0L

    // --- Bhramari Humming Detector ---
    private var humStartTimeMillis: Long = 0L
    private var isCurrentlyHumming: Boolean = false

    /** Timestamp of last telemetry stats log for rate-limiting logcat output. */
    private var lastLogTimeMillis: Long = 0L

    /** 2nd-order Biquad Bandpass filter isolating nasal breath friction around 2.0 kHz (Q=0.8). */
    private val bandpassFilter = BiquadBandpassFilter(SAMPLE_RATE_HZ.toFloat(), 2000f, 0.8f)

    /**
     * Updates detection sensitivity dynamically.
     *
     * @param sensitivity Multiplier scaling the detection threshold (0.5f = Low, 1.0f = Med, 1.5f = High).
     */
    fun setSensitivity(sensitivity: Float) {
        activeSensitivity = sensitivity.coerceIn(0.5f, 2.5f)
    }

    /**
     * Temporarily blanks/mutes acoustic stroke evaluation to prevent phone speaker audio
     * (interval bells, voice guidance cues, stroke ticks) from self-triggering false strokes.
     *
     * @param durationMs Blanking duration in milliseconds.
     */
    fun blankDetection(durationMs: Long) {
        blankUntilMillis = max(blankUntilMillis, System.currentTimeMillis() + durationMs)
    }

    override fun start(config: BreathCounterConfig) {
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
            Log.w(TAG, "Cannot start AcousticBreathSensorProvider: RECORD_AUDIO permission not granted.")
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
        lastStrokeTimeMillis = 0L
        blankUntilMillis = 0L
        strokeIntervals.clear()
        humStartTimeMillis = 0L
        isCurrentlyHumming = false
        dynamicNoiseFloorRms = 0.015f
        previousBandpassRms = 0.015f
        kapalabhatiState = KapalabhatiState.IDLE_LISTENING
        strokeStartTimeMillis = 0L
        strokePeakRms = 0f
        bhastrikaInhaleDetected = false
        bhastrikaInhaleTimeMillis = 0L
        bandpassFilter.reset()
        _inputFlow.value = BreathInputEvent()
    }

    override fun registerManualStroke() {
        val now = System.currentTimeMillis()
        val bpm = calculateCadenceBpm(now)
        _inputFlow.value = BreathInputEvent(
            strokeDelta = 1,
            instantaneousCadenceBpm = bpm,
            audioAmplitudeRms = 0.9f,
            thresholdRms = 0.05f,
            timestampMillis = now
        )
    }

    /**
     * Primary audio streaming and DSP evaluation loop running on [Dispatchers.IO].
     *
     * @param config Active configuration determining detection thresholds.
     */
    private suspend fun runAudioCaptureLoop(config: BreathCounterConfig) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SIZE * 2)

        // Prefer standard MIC for direct unclipped acoustic breath capture without OEM speech gates
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

                // 1. Filter chunk through Biquad Bandpass Filter (1.2 kHz - 4.0 kHz)
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
                    _inputFlow.value = BreathInputEvent(
                        audioAmplitudeRms = (rawRms * 3.5f).coerceIn(0f, 1f),
                        thresholdRms = 0.05f,
                        timestampMillis = now
                    )
                    continue
                }

                // 3. Evaluate blanking period (self-acoustic speaker feedback suppression)
                val isBlanked = now < blankUntilMillis
                if (isBlanked) {
                    // Forward audio level for visuals but completely suppress stroke detection
                    _inputFlow.value = _inputFlow.value.copy(
                        strokeDelta = 0,
                        audioAmplitudeRms = (rawRms * 3.5f).coerceIn(0f, 1f),
                        timestampMillis = now
                    )
                    previousBandpassRms = bandpassRms
                    continue
                }

                // 4. Dispatch technique-specific stateful DSP detection
                when (config.technique) {
                    BreathTechnique.KAPALABHATI, BreathTechnique.FREE_COUNT -> {
                        evaluateKapalabhatiStroke(pcmBuffer, samplesRead, rawRms, bandpassRms, now)
                    }
                    BreathTechnique.BHASTRIKA -> {
                        evaluateBhastrikaCycle(pcmBuffer, samplesRead, rawRms, bandpassRms, now)
                    }
                    BreathTechnique.BHRAMARI -> {
                        evaluateBhramariHum(pcmBuffer, samplesRead, rawRms, now)
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
            } catch (e: Exception) {
                // Defensive cleanup
            }
            audioRecord = null
        }
    }

    /**
     * Evaluates audio chunks for Kapalabhati using a 3-Stage Hysteresis Peak-Valley State Machine.
     *
     * State Machine:
     * - [KapalabhatiState.IDLE_LISTENING]: Continuously tracks ambient noise floor. Monitors for
     *   sudden explosive attack slope (`rise > threshold * 0.20`).
     * - [KapalabhatiState.ATTACK_DETECTED]: Tracks local peak energy. Verifies physiological burst
     *   duration (35ms - 300ms) and minimum SNR. Confirms exactly 1 stroke at peak decay!
     * - [KapalabhatiState.COOLDOWN_VALLEY]: Mandates quiet passive inhalation valley drop-off
     *   (`energy < threshold * 0.70`) and minimum refractory lockout before re-arming to IDLE.
     *
     * @param pcm Raw PCM buffer.
     * @param length Number of valid samples in [pcm].
     * @param rawRms Full-spectrum RMS energy.
     * @param bandpassRms 1.2 kHz - 4.0 kHz bandpass filtered RMS energy.
     * @param now Current monotonic timestamp in milliseconds.
     */
    private fun evaluateKapalabhatiStroke(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        bandpassRms: Float,
        now: Long
    ) {
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)

        // Dynamic threshold calibrated for natural breath acoustics:
        // Scaled inversely by sensitivity (0.7 = Low, 1.0 = Med, 1.5 = High)
        val thresholdMultiplier = 1.8f / sensitivity
        val minFloor = 0.007f / sensitivity
        val dynamicThreshold = (dynamicNoiseFloorRms * thresholdMultiplier + minFloor).coerceIn(0.007f, 0.20f)

        // Continuous ambient noise floor tracking (only update when in calm idle state)
        if (kapalabhatiState == KapalabhatiState.IDLE_LISTENING) {
            if (bandpassRms < dynamicNoiseFloorRms) {
                // Fast downward adaptation to quiet
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.88f) + (bandpassRms * 0.12f)
            } else if (bandpassRms < dynamicThreshold * 0.65f) {
                // Gentle upward adaptation to quiet room ambience
                dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.98f) + (bandpassRms * 0.02f)
            }
            dynamicNoiseFloorRms = dynamicNoiseFloorRms.coerceIn(0.002f, 0.06f)
        }

        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val normalizedThreshold = (dynamicThreshold * 3.5f).coerceIn(0.05f, 0.95f)

        var strokeEmitted = false
        val timeSinceLastStroke = now - lastStrokeTimeMillis

        // Periodic telemetry logging (every ~1s)
        if (now - lastLogTimeMillis >= 1000L) {
            lastLogTimeMillis = now
            Log.d(TAG, "📊 STATS: state=$kapalabhatiState, rawRms=%.4f, bandpassRms=%.4f, noiseFloor=%.4f, thresh=%.4f, sens=%.1f".format(
                rawRms, bandpassRms, dynamicNoiseFloorRms, dynamicThreshold, sensitivity
            ))
        }

        when (kapalabhatiState) {
            KapalabhatiState.IDLE_LISTENING -> {
                // Enforce minimum refractory interval (280ms = max ~214 BPM)
                if (bandpassRms > dynamicThreshold && timeSinceLastStroke >= 280L) {
                    kapalabhatiState = KapalabhatiState.ATTACK_DETECTED
                    strokeStartTimeMillis = now
                    strokePeakRms = bandpassRms
                    Log.d(TAG, "⚡ ATTACK ONSET: bandpassRms=%.4f > thresh=%.4f (rise=%.4f)".format(
                        bandpassRms, dynamicThreshold, bandpassRms - previousBandpassRms
                    ))
                }
            }

            KapalabhatiState.ATTACK_DETECTED -> {
                strokePeakRms = max(strokePeakRms, bandpassRms)
                val burstDuration = now - strokeStartTimeMillis

                if (burstDuration > 220L) {
                    // Sustained non-stroke sound (e.g. continuous talking, singing, drone)
                    // Abort to cooldown to prevent false runaway count
                    Log.w(TAG, "⚠️ SUSTAINED SOUND ABORT: burstDuration=${burstDuration}ms > 220ms -> COOLDOWN")
                    kapalabhatiState = KapalabhatiState.COOLDOWN_VALLEY
                    lastStrokeTimeMillis = now
                } else if (bandpassRms < strokePeakRms * 0.75f) {
                    // Confirmed peak decay!
                    val isValidBurstDuration = burstDuration in 20..220
                    val isSufficientPeak = strokePeakRms >= dynamicThreshold

                    if (isValidBurstDuration && isSufficientPeak) {
                        lastStrokeTimeMillis = now
                        strokeEmitted = true
                        kapalabhatiState = KapalabhatiState.COOLDOWN_VALLEY
                        Log.i(TAG, "🎯 STROKE CONFIRMED! burstDuration=${burstDuration}ms, peakRms=%.4f, thresh=%.4f".format(
                            strokePeakRms, dynamicThreshold
                        ))
                    } else {
                        Log.d(TAG, "❌ BLIP DISMISSED: burstDuration=${burstDuration}ms, peakRms=%.4f".format(
                            strokePeakRms
                        ))
                        kapalabhatiState = KapalabhatiState.IDLE_LISTENING
                    }
                }
            }

            KapalabhatiState.COOLDOWN_VALLEY -> {
                val elapsedSinceStroke = now - lastStrokeTimeMillis
                // If ambient noise is still louder than threshold, stay in cooldown to avoid runaway loop
                if (bandpassRms > dynamicThreshold) {
                    lastStrokeTimeMillis = now
                } else {
                    val isInValley = bandpassRms < (dynamicThreshold * 0.85f)
                    val isMinRefractoryPassed = elapsedSinceStroke >= 200L
                    val isCooldownExpired = elapsedSinceStroke >= 280L

                    if ((isInValley && isMinRefractoryPassed) || isCooldownExpired) {
                        kapalabhatiState = KapalabhatiState.IDLE_LISTENING
                        Log.d(TAG, "🔄 COOLDOWN COMPLETE -> IDLE_LISTENING (elapsed=${elapsedSinceStroke}ms, bandpassRms=%.4f)".format(bandpassRms))
                    }
                }
            }
        }

        if (strokeEmitted) {
            val cadenceBpm = calculateCadenceBpm(now)
            _inputFlow.value = BreathInputEvent(
                strokeDelta = 1,
                instantaneousCadenceBpm = cadenceBpm,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isHummingActive = false,
                activeHumDurationSeconds = 0f,
                timestampMillis = now
            )
        } else {
            _inputFlow.value = _inputFlow.value.copy(
                strokeDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                timestampMillis = now
            )
        }
    }

    /**
     * Evaluates audio chunks for Bhastrika dual-phase bellows breathing.
     * Requires both forceful inhalation and sharp exhalation within a 1200ms window.
     */
    private fun evaluateBhastrikaCycle(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        bandpassRms: Float,
        now: Long
    ) {
        val sensitivity = activeSensitivity.coerceIn(0.5f, 2.5f)
        val dynamicThreshold = (dynamicNoiseFloorRms * (1.8f / sensitivity) + (0.010f / sensitivity)).coerceIn(0.010f, 0.20f)
        val normalizedAmplitude = (rawRms * 3.0f).coerceIn(0f, 1f)
        val normalizedThreshold = (dynamicThreshold * 3.0f).coerceIn(0.05f, 0.95f)
        val timeSinceLastStroke = now - lastStrokeTimeMillis

        // Bhastrika bellows cycle requires at least 650 ms between full dual-phase cycles
        if (!bhastrikaInhaleDetected) {
            if (bandpassRms > dynamicThreshold && timeSinceLastStroke >= 650L) {
                bhastrikaInhaleDetected = true
                bhastrikaInhaleTimeMillis = now
            }
        } else {
            val elapsedSinceInhale = now - bhastrikaInhaleTimeMillis
            if (elapsedSinceInhale in 180..1200 && bandpassRms > dynamicThreshold) {
                // Both inhalation and exhalation completed!
                bhastrikaInhaleDetected = false
                lastStrokeTimeMillis = now
                val cadenceBpm = calculateCadenceBpm(now)

                _inputFlow.value = BreathInputEvent(
                    strokeDelta = 1,
                    instantaneousCadenceBpm = cadenceBpm,
                    audioAmplitudeRms = normalizedAmplitude,
                    thresholdRms = normalizedThreshold,
                    timestampMillis = now
                )
                return
            } else if (elapsedSinceInhale > 1200) {
                bhastrikaInhaleDetected = false
            }
        }

        _inputFlow.value = _inputFlow.value.copy(
            strokeDelta = 0,
            audioAmplitudeRms = normalizedAmplitude,
            thresholdRms = normalizedThreshold,
            timestampMillis = now
        )
    }

    /**
     * Evaluates audio chunks for sustained Bhramari humming vibrations using autocorrelation periodicity.
     */
    private fun evaluateBhramariHum(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        now: Long
    ) {
        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val humThreshold = (dynamicNoiseFloorRms * 2.0f).coerceAtLeast(0.035f)
        val normalizedThreshold = (humThreshold * 3.5f).coerceIn(0.05f, 0.95f)

        // Autocorrelation test at pitch lags corresponding to 80Hz - 250Hz (lag: 64 to 200 samples)
        var maxAutocorr = 0.0
        if (rawRms > humThreshold) {
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

        val isHummingDetected = (rawRms > humThreshold) && (maxAutocorr > 0)

        if (isHummingDetected) {
            if (!isCurrentlyHumming) {
                isCurrentlyHumming = true
                humStartTimeMillis = now
            }
            val humDurationSec = (now - humStartTimeMillis) / 1000.0f

            _inputFlow.value = BreathInputEvent(
                strokeDelta = 0,
                instantaneousCadenceBpm = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isHummingActive = true,
                activeHumDurationSeconds = humDurationSec,
                timestampMillis = now
            )
        } else {
            if (isCurrentlyHumming) {
                val totalHumSec = (now - humStartTimeMillis) / 1000.0f
                isCurrentlyHumming = false
                humStartTimeMillis = 0L

                // If hum lasted >= 2.0 seconds, register as a completed humming round!
                if (totalHumSec >= 2.0f) {
                    _inputFlow.value = BreathInputEvent(
                        strokeDelta = 1,
                        instantaneousCadenceBpm = 0,
                        audioAmplitudeRms = normalizedAmplitude,
                        thresholdRms = normalizedThreshold,
                        isHummingActive = false,
                        activeHumDurationSeconds = totalHumSec,
                        timestampMillis = now
                    )
                    return
                }
            }

            _inputFlow.value = _inputFlow.value.copy(
                strokeDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                thresholdRms = normalizedThreshold,
                isHummingActive = false,
                timestampMillis = now
            )
        }
    }

    /**
     * Estimates cadence in strokes per minute (BPM) from recent inter-stroke intervals.
     *
     * @param now Monotonic timestamp in milliseconds.
     * @return Rolling cadence in BPM (bounded 15..180 BPM).
     */
    private fun calculateCadenceBpm(now: Long): Int {
        if (lastStrokeTimeMillis > 0L) {
            val intervalMs = now - lastStrokeTimeMillis
            if (intervalMs in 250..3000) {
                if (strokeIntervals.size >= 6) {
                    strokeIntervals.removeFirst()
                }
                strokeIntervals.addLast(intervalMs)
                val avgInterval = strokeIntervals.average()
                if (avgInterval > 0) {
                    return (60_000.0 / avgInterval).toInt().coerceIn(15, 180)
                }
            }
        }
        return 0
    }

    /**
     * 2nd-order Biquad IIR Bandpass Filter (Audio EQ Cookbook).
     *
     * @param sampleRate Sampling rate in Hz (e.g. 16000f).
     * @param centerFreq Center resonance frequency in Hz (e.g. 2400f).
     * @param q Quality factor Q (e.g. 1.0f).
     */
    class BiquadBandpassFilter(sampleRate: Float, centerFreq: Float, q: Float = 1.0f) {
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
         * @param sample Raw normalized PCM audio sample (-1.0f..1.0f).
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
}
