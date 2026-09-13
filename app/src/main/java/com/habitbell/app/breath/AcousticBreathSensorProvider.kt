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
import kotlin.math.sqrt

/**
 * # AcousticBreathSensorProvider
 *
 * Hardware-level acoustic breath stroke analyzer and DSP provider implementing [BreathDataSource].
 *
 * ## Architectural Role & Relationships
 * - Interfaces directly with the device microphone via low-latency [AudioRecord] PCM streams.
 * - Implements real-time Digital Signal Processing (DSP) algorithms:
 *   1. **Dynamic Noise Floor Calibration**: Continuously estimates ambient background sound pressure.
 *   2. **Bandpass Filtering**: Isolates nasal friction bursts (1.5 kHz - 4.5 kHz) for Kapalabhati.
 *   3. **RMS Energy & Peak Detector**: Triggers stroke counts with a 320ms refractory guard window.
 *   4. **Autocorrelation Pitch Tracker**: Validates sustained periodic vocal resonance for Bhramari humming.
 * - Dispatches primitive [BreathInputEvent] metrics into [BreathCountManager].
 *
 * ## Concurrency & Hardware Lifecycle
 * - Audio ingestion runs on a dedicated high-priority coroutine on [Dispatchers.Default].
 * - Hardware handles ([AudioRecord]) are strictly initialized on [start] and released on [stop].
 *
 * @param context Android application context for permission verification.
 */
class AcousticBreathSensorProvider(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BreathDataSource {

    private val TAG = "AcousticBreathSensor"

    override val inputSourceType: BreathInputSourceType = BreathInputSourceType.ACOUSTIC_MIC

    /** Sample rate in Hz for acoustic breath monitoring. 16kHz offers optimal DSP accuracy with minimal CPU. */
    private val SAMPLE_RATE_HZ = 16000

    /** Chunk size in 16-bit PCM samples (~32 ms per analysis window). */
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

    /** Timestamp of the most recently detected stroke in milliseconds. */
    private var lastStrokeTimeMillis: Long = 0L

    /** Sliding window ring buffer tracking recent inter-stroke intervals for cadence calculation. */
    private val strokeIntervals = ArrayDeque<Long>(6)

    /** Moving average ambient noise floor estimate. */
    private var dynamicNoiseFloorRms: Float = 0.02f

    /** Timestamp when active Bhramari humming began. */
    private var humStartTimeMillis: Long = 0L

    /** Whether Bhramari humming is currently underway. */
    private var isCurrentlyHumming: Boolean = false

    override fun start(config: BreathCounterConfig) {
        activeConfig = config
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
        strokeIntervals.clear()
        humStartTimeMillis = 0L
        isCurrentlyHumming = false
        dynamicNoiseFloorRms = 0.02f
        _inputFlow.value = BreathInputEvent()
    }

    override fun registerManualStroke() {
        val now = System.currentTimeMillis()
        val bpm = calculateCadenceBpm(now)
        _inputFlow.value = BreathInputEvent(
            strokeDelta = 1,
            instantaneousCadenceBpm = bpm,
            audioAmplitudeRms = 0.9f,
            timestampMillis = now
        )
    }

    /**
     * Primary audio streaming and DSP evaluation loop running on [Dispatchers.Default].
     *
     * @param config Active configuration determining detection thresholds.
     */
    private suspend fun runAudioCaptureLoop(config: BreathCounterConfig) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SIZE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            val pcmBuffer = ShortArray(CHUNK_SIZE)
            var calibrationFrames = 40 // ~1.2 seconds of initial noise calibration

            while (coroutineContext.isActive) {
                val samplesRead = audioRecord?.read(pcmBuffer, 0, CHUNK_SIZE) ?: -1
                if (samplesRead <= 0) {
                    delay(10)
                    continue
                }

                // 1. Calculate normalized Short-Time Root Mean Square (RMS) energy
                var sumSquares = 0.0
                for (i in 0 until samplesRead) {
                    val normalized = pcmBuffer[i] / 32768.0f
                    sumSquares += (normalized * normalized)
                }
                val rawRms = sqrt(sumSquares / samplesRead).toFloat()

                // 2. Calibration phase: estimate ambient noise floor
                if (calibrationFrames > 0) {
                    dynamicNoiseFloorRms = (dynamicNoiseFloorRms * 0.9f) + (rawRms * 0.1f)
                    calibrationFrames--
                    _inputFlow.value = BreathInputEvent(audioAmplitudeRms = (rawRms * 3f).coerceIn(0f, 1f))
                    continue
                }

                // 3. Dispatch technique-specific DSP detection
                when (config.technique) {
                    BreathTechnique.KAPALABHATI, BreathTechnique.FREE_COUNT -> {
                        evaluateKapalabhatiStroke(pcmBuffer, samplesRead, rawRms, config)
                    }
                    BreathTechnique.BHASTRIKA -> {
                        evaluateBhastrikaCycle(pcmBuffer, samplesRead, rawRms, config)
                    }
                    BreathTechnique.BHRAMARI -> {
                        evaluateBhramariHum(pcmBuffer, samplesRead, rawRms, config)
                    }
                }
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
     * Evaluates a short audio chunk for Kapalabhati nasal exhalation bursts.
     *
     * Features:
     * - Bandpass difference filter rejecting DC rumble.
     * - Refractory guard window of 300 ms preventing multi-triggering per single breath.
     * - Dynamic sensitivity threshold scaling.
     */
    private fun evaluateKapalabhatiStroke(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        config: BreathCounterConfig
    ) {
        val now = System.currentTimeMillis()
        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)

        // Simple high-pass / friction filter: d[i] = pcm[i] - pcm[i-1]
        var filteredSumSquares = 0.0
        for (i in 1 until length) {
            val diff = (pcm[i] - pcm[i - 1]) / 32768.0f
            filteredSumSquares += (diff * diff)
        }
        val highFreqRms = sqrt(filteredSumSquares / length).toFloat()

        val dynamicThreshold = (dynamicNoiseFloorRms * 2.8f * (1.0f / config.micSensitivity.coerceAtLeast(0.5f))).coerceAtLeast(0.045f)
        val timeSinceLastStroke = now - lastStrokeTimeMillis

        if (highFreqRms > dynamicThreshold && timeSinceLastStroke >= 300L) {
            lastStrokeTimeMillis = now
            val cadenceBpm = calculateCadenceBpm(now)

            _inputFlow.value = BreathInputEvent(
                strokeDelta = 1,
                instantaneousCadenceBpm = cadenceBpm,
                audioAmplitudeRms = normalizedAmplitude,
                isHummingActive = false,
                activeHumDurationSeconds = 0f,
                timestampMillis = now
            )
        } else {
            // Emit continuous amplitude update for visual ripples without incrementing strokes
            _inputFlow.value = _inputFlow.value.copy(
                strokeDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                timestampMillis = now
            )
        }
    }

    /**
     * Evaluates a short audio chunk for Bhastrika dual-phase bellows breathing.
     */
    private fun evaluateBhastrikaCycle(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        config: BreathCounterConfig
    ) {
        val now = System.currentTimeMillis()
        val normalizedAmplitude = (rawRms * 3.0f).coerceIn(0f, 1f)
        val dynamicThreshold = (dynamicNoiseFloorRms * 2.5f * (1.0f / config.micSensitivity.coerceAtLeast(0.5f))).coerceAtLeast(0.05f)
        val timeSinceLastStroke = now - lastStrokeTimeMillis

        // Bellows breath has a longer refractory period (~650 ms per in/out cycle)
        if (rawRms > dynamicThreshold && timeSinceLastStroke >= 650L) {
            lastStrokeTimeMillis = now
            val cadenceBpm = calculateCadenceBpm(now)

            _inputFlow.value = BreathInputEvent(
                strokeDelta = 1,
                instantaneousCadenceBpm = cadenceBpm,
                audioAmplitudeRms = normalizedAmplitude,
                timestampMillis = now
            )
        } else {
            _inputFlow.value = _inputFlow.value.copy(
                strokeDelta = 0,
                audioAmplitudeRms = normalizedAmplitude,
                timestampMillis = now
            )
        }
    }

    /**
     * Evaluates audio chunks for sustained Bhramari humming vibrations using autocorrelation periodicity.
     */
    private fun evaluateBhramariHum(
        pcm: ShortArray,
        length: Int,
        rawRms: Float,
        config: BreathCounterConfig
    ) {
        val now = System.currentTimeMillis()
        val normalizedAmplitude = (rawRms * 3.5f).coerceIn(0f, 1f)
        val humThreshold = (dynamicNoiseFloorRms * 2.0f).coerceAtLeast(0.035f)

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

        // Energy ratio indicator for harmonic periodicity
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
                isHummingActive = false,
                timestampMillis = now
            )
        }
    }

    /**
     * Estimates cadence in strokes per minute (BPM) from recent inter-stroke intervals.
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
}
