package com.habitbell.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

/**
 * # HealthStepManager
 *
 * Central orchestrator and facade governing step tracking, health providers, and workout logging.
 *
 * ## Architectural Role & Relationships
 * - Bound to [com.habitbell.app.engine.CentralSessionHandler] as the authoritative health subsystem.
 * - Coordinates between [HardwarePedometerProvider], [HealthConnectManager], [AppleHealthBridgeManager],
 *   and [SimulatedStepProvider].
 * - Pipes active step updates into [com.habitbell.app.engine.TimerEngine] for real-time interval chime evaluation.
 * - Exports completed walking workouts to Google Fit / Samsung Health via Health Connect.
 *
 * ## Lifecycle & Concurrency
 * - Scoped to process application context on [Dispatchers.Main.immediate] and [Dispatchers.Default].
 * - Thread-safe state emissions using Kotlin [StateFlow].
 *
 * @param context Android application context for permissions and hardware service discovery.
 */
class HealthStepManager(private val context: Context) {

    /** Coroutine scope bound to manager lifecycle. */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Low-latency hardware pedometer sensor provider. */
    val hardwarePedometer: HardwarePedometerProvider = HardwarePedometerProvider(context)

    /** Android Health Connect integration (Google Fit, Samsung Health, wearables). */
    val healthConnect: HealthConnectManager = HealthConnectManager(context)

    /** Apple HealthKit cross-platform bridge. */
    val appleHealthBridge: AppleHealthBridgeManager = AppleHealthBridgeManager()

    /** High-precision synthetic step simulator. */
    val simulatedProvider: SimulatedStepProvider = SimulatedStepProvider()

    /** Backing state for active health data provider selection. */
    private val _selectedProviderType = MutableStateFlow(resolveDefaultProvider())

    /** Public read-only stream of currently selected health data source. */
    val selectedProviderType: StateFlow<HealthProviderType> = _selectedProviderType.asStateFlow()

    /** Job collecting step updates from the active provider. */
    private var collectorJob: Job? = null

    /** Backing state flow for combined authoritative step metrics. */
    private val _activeStepUpdate = MutableStateFlow(
        StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    )

    /** Authoritative stream of current session step metrics. */
    val activeStepUpdate: StateFlow<StepUpdate> = _activeStepUpdate.asStateFlow()

    /** Timestamp when current walking session commenced. */
    private var sessionStartTime: Instant? = null

    init {
        observeActiveProvider()
    }

    /**
     * Resolves the optimal default health provider based on hardware availability.
     *
     * @return [HealthProviderType.HARDWARE_SENSOR] if physical sensor is detected,
     *         otherwise [HealthProviderType.HEALTH_CONNECT] or [HealthProviderType.SIMULATED].
     */
    private fun resolveDefaultProvider(): HealthProviderType {
        return when {
            hardwarePedometer.isAvailable -> HealthProviderType.HARDWARE_SENSOR
            healthConnect.isAvailable -> HealthProviderType.HEALTH_CONNECT
            else -> HealthProviderType.SIMULATED
        }
    }

    /**
     * Rebinds the collector job whenever the active provider selection changes.
     */
    private fun observeActiveProvider() {
        collectorJob?.cancel()
        val currentSource = getSourceForType(_selectedProviderType.value)

        collectorJob = scope.launch {
            currentSource.stepFlow.collect { update ->
                _activeStepUpdate.value = update
                // If running with hardware sensor, also keep Health Connect and Apple bridge updated in sync
                if (_selectedProviderType.value == HealthProviderType.HARDWARE_SENSOR) {
                    healthConnect.updateSessionSteps(update.sessionSteps, update.cadenceStepsPerMinute)
                    appleHealthBridge.ingestCompanionSteps(update.sessionSteps, update.cadenceStepsPerMinute)
                }
            }
        }
    }

    /**
     * Returns the concrete [StepDataSource] instance corresponding to a [HealthProviderType].
     *
     * @param type Target provider type.
     * @return Concrete data source instance.
     */
    fun getSourceForType(type: HealthProviderType): StepDataSource {
        return when (type) {
            HealthProviderType.HARDWARE_SENSOR -> hardwarePedometer
            HealthProviderType.HEALTH_CONNECT -> healthConnect
            HealthProviderType.APPLE_HEALTH_BRIDGE -> appleHealthBridge
            HealthProviderType.SIMULATED -> simulatedProvider
        }
    }

    /**
     * Switches the active step data provider.
     *
     * @param type Desired health data source.
     */
    fun selectProvider(type: HealthProviderType) {
        if (_selectedProviderType.value == type) return
        val previousSource = getSourceForType(_selectedProviderType.value)
        val currentSteps = _activeStepUpdate.value.sessionSteps
        previousSource.stop()

        _selectedProviderType.value = type
        observeActiveProvider()

        val newSource = getSourceForType(type)
        newSource.start(currentSteps)
    }

    /**
     * Verifies if the runtime permission for physical activity recognition has been granted.
     *
     * @return True if [Manifest.permission.ACTIVITY_RECOGNITION] is granted (or on Android < 10 where not required).
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Starts tracking steps for an active walking session.
     *
     * @param initialSteps Optional starting offset.
     */
    fun startSession(initialSteps: Int = 0) {
        sessionStartTime = Instant.now()
        val source = getSourceForType(_selectedProviderType.value)
        source.start(initialSteps)
    }

    /**
     * Temporarily pauses step accumulation during timer pause.
     */
    fun pauseSession() {
        val source = getSourceForType(_selectedProviderType.value)
        source.pause()
    }

    /**
     * Resumes step accumulation following a timer pause.
     */
    fun resumeSession() {
        val source = getSourceForType(_selectedProviderType.value)
        source.resume()
    }

    /**
     * Stops active step tracking.
     */
    fun stopSession() {
        val source = getSourceForType(_selectedProviderType.value)
        source.stop()
    }

    /**
     * Resets session steps and clears session timestamps.
     */
    fun resetSession() {
        sessionStartTime = null
        val source = getSourceForType(_selectedProviderType.value)
        source.reset()
    }

    /**
     * Records and synchronizes a completed walking session to Health Connect
     * (Google Fit, Samsung Health, wearables).
     *
     * @param sessionTitle Title description of the session.
     * @param completedSteps Total steps taken during the session.
     * @return True if sync was successful, false otherwise.
     */
    suspend fun recordCompletedWalkingSession(
        sessionTitle: String,
        completedSteps: Int
    ): Boolean {
        val start = sessionStartTime ?: Instant.now().minusSeconds(60)
        val end = Instant.now()

        // Sync to Health Connect (Google Fit / Samsung Health)
        return healthConnect.writeWalkingSession(
            sessionTitle = sessionTitle,
            totalSteps = completedSteps,
            startTime = start,
            endTime = end
        )
    }

    /**
     * Cleans up coroutine jobs and sensor listeners on shutdown.
     */
    fun destroy() {
        collectorJob?.cancel()
        hardwarePedometer.stop()
        healthConnect.stop()
        simulatedProvider.stop()
        appleHealthBridge.stop()
        scope.cancel()
    }
}
