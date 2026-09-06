package com.habitbell.app.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * # HardwarePedometerProvider
 *
 * Direct hardware pedometer provider interfacing with native Android sensors.
 *
 * ## Architectural Role & Relationships
 * - Primary real-time step source implementing [StepDataSource].
 * - Utilizes [Sensor.TYPE_STEP_COUNTER] (primary) and [Sensor.TYPE_STEP_DETECTOR] (fallback).
 * - Delivers microsecond-latency step events directly to [HealthStepManager] without cloud round-trips.
 *
 * ## Concurrency & Hardware Optimization
 * - Sensor callbacks are executed on system sensor looper and marshaled safely to [MutableStateFlow].
 * - Computes rolling cadence (steps per minute) using a 10-second sliding temporal queue.
 *
 * @param context Android context for [SensorManager] resolution.
 */
class HardwarePedometerProvider(context: Context) : StepDataSource, SensorEventListener {

    /** System sensor manager handle. */
    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Hardware cumulative step counter sensor (reboot-based cumulative count). */
    private val stepCounterSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** Hardware discrete step detector sensor (triggers exactly 1.0f on each footfall). */
    private val stepDetectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    /** Coroutine scope for dispatching state updates off the main looper. */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Provider identity constant. */
    override val providerType: HealthProviderType = HealthProviderType.HARDWARE_SENSOR

    /** True if either a step counter or step detector sensor is physically present on the device. */
    override val isAvailable: Boolean
        get() = stepCounterSensor != null || stepDetectorSensor != null

    /** Backing mutable state flow for reactive step metrics. */
    private val _stepFlow = MutableStateFlow(
        StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    )

    /** Public immutable step updates stream. */
    override val stepFlow: StateFlow<StepUpdate> = _stepFlow.asStateFlow()

    /** Baseline hardware counter reading recorded at the start or resumption of a session. */
    private var baselineSensorCount: Long = -1L

    /** Total session steps accumulated prior to the most recent pause/resume cycle. */
    private var accumulatedPriorSteps: Int = 0

    /** Flag indicating whether the provider is currently listening to hardware sensors. */
    private var isListening: Boolean = false

    /** Flag indicating if tracking is temporarily paused. */
    private var isPaused: Boolean = false

    /** Timestamp deque recording recent step timestamps for sliding-window cadence calculation. */
    private val stepTimestamps = ConcurrentLinkedDeque<Long>()

    /**
     * Starts monitoring hardware step sensors.
     *
     * @param initialSessionSteps Optional step count offset to restore previous session state.
     */
    @Synchronized
    override fun start(initialSessionSteps: Int) {
        if (isListening && !isPaused) return

        accumulatedPriorSteps = initialSessionSteps
        baselineSensorCount = -1L
        isPaused = false

        registerSensorListeners()
        isListening = true
    }

    /**
     * Temporarily freezes step accumulation while holding accumulated counts.
     */
    @Synchronized
    override fun pause() {
        if (!isListening || isPaused) return
        isPaused = true
        unregisterSensorListeners()
        accumulatedPriorSteps = _stepFlow.value.sessionSteps
        baselineSensorCount = -1L
        stepTimestamps.clear()
        _stepFlow.update { it.copy(cadenceStepsPerMinute = 0) }
    }

    /**
     * Resumes step accumulation following a pause.
     */
    @Synchronized
    override fun resume() {
        if (!isPaused) return
        isPaused = false
        baselineSensorCount = -1L
        registerSensorListeners()
    }

    /**
     * Terminates hardware monitoring and clears active listeners.
     */
    @Synchronized
    override fun stop() {
        unregisterSensorListeners()
        isListening = false
        isPaused = false
        stepTimestamps.clear()
        _stepFlow.update { it.copy(cadenceStepsPerMinute = 0) }
    }

    /**
     * Resets all step counts, baselines, and cadence metrics back to zero.
     */
    @Synchronized
    override fun reset() {
        stop()
        accumulatedPriorSteps = 0
        baselineSensorCount = -1L
        stepTimestamps.clear()
        _stepFlow.value = StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Registers system sensor event callbacks for available pedometer sensors.
     */
    private fun registerSensorListeners() {
        val manager = sensorManager ?: return

        // Prefer TYPE_STEP_COUNTER for reliable, low-power continuous accumulation
        if (stepCounterSensor != null) {
            manager.registerListener(
                this,
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        } else if (stepDetectorSensor != null) {
            // Fallback to TYPE_STEP_DETECTOR if continuous counter is unavailable
            manager.registerListener(
                this,
                stepDetectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    /**
     * Unregisters hardware listeners to release hardware resources and conserve power.
     */
    private fun unregisterSensorListeners() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Hardware callback invoked when a step event is detected by the OS sensor subsystem.
     *
     * @param event Hardware [SensorEvent] containing step readings.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isPaused) return
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val rawCount = event.values[0].toLong()

                if (baselineSensorCount < 0L) {
                    baselineSensorCount = rawCount
                }

                // Compute delta since session started
                val delta = (rawCount - baselineSensorCount).coerceAtLeast(0L).toInt()
                val currentSessionSteps = accumulatedPriorSteps + delta

                recordCadenceStep(now)
                val cadence = calculateCadence(now)

                _stepFlow.value = StepUpdate(
                    sessionSteps = currentSessionSteps,
                    rawCumulativeSteps = rawCount,
                    cadenceStepsPerMinute = cadence,
                    timestampMillis = now
                )
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // Fallback detector triggers once per detected footstep (event.values[0] == 1.0f)
                if (event.values.isNotEmpty() && event.values[0] > 0.5f) {
                    accumulatedPriorSteps++
                    recordCadenceStep(now)
                    val cadence = calculateCadence(now)

                    _stepFlow.value = StepUpdate(
                        sessionSteps = accumulatedPriorSteps,
                        rawCumulativeSteps = _stepFlow.value.rawCumulativeSteps + 1L,
                        cadenceStepsPerMinute = cadence,
                        timestampMillis = now
                    )
                }
            }
        }
    }

    /**
     * Records timestamp for cadence estimation and trims expired measurements outside 10s window.
     *
     * @param timestampMillis Epoch millisecond of current step.
     */
    private fun recordCadenceStep(timestampMillis: Long) {
        stepTimestamps.addLast(timestampMillis)
        val cutoff = timestampMillis - 10_000L // 10-second sliding temporal window
        while (stepTimestamps.isNotEmpty() && (stepTimestamps.peekFirst() ?: Long.MAX_VALUE) < cutoff) {
            stepTimestamps.pollFirst()
        }
    }

    /**
     * Calculates current walking cadence in steps per minute (SPM).
     *
     * @param timestampMillis Current epoch millisecond.
     * @return Estimated steps per minute normalized to a 60-second projection.
     */
    private fun calculateCadence(timestampMillis: Long): Int {
        val count = stepTimestamps.size
        if (count < 2) return 0

        val first = stepTimestamps.peekFirst() ?: return 0
        val windowSeconds = ((timestampMillis - first) / 1000.0).coerceAtLeast(1.0)
        return ((count / windowSeconds) * 60.0).toInt().coerceIn(0, 240)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for step counters
    }
}
