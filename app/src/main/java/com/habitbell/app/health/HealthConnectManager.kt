package com.habitbell.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * # HealthConnectManager
 *
 * Android Health Connect client integration enabling bi-directional health platform synchronization.
 *
 * ## Architectural Role & Relationships
 * - Bridges Habit Bell with Google Fit, Samsung Health, Fitbit, Whoop, and connected fitness wearables.
 * - Queries aggregate daily and session step metrics.
 * - Writes walking workout sessions ([ExerciseSessionRecord]) and discrete step data ([StepsRecord])
 *   to Health Connect upon session completion.
 *
 * ## Lifecycle & Concurrency
 * - Operations run asynchronously on [Dispatchers.IO].
 * - Handles SDK availability verification across Android 14+ (integrated in OS) and Android 9-13 (Play Store APK).
 *
 * @param context Android application context for Health Connect client resolution.
 */
class HealthConnectManager(private val context: Context) : StepDataSource {

    override val providerType: HealthProviderType = HealthProviderType.HEALTH_CONNECT

    /**
     * Determines whether Health Connect is supported and installed on the host device.
     */
    override val isAvailable: Boolean
        get() = try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }

    /** Lazy handle to the Health Connect client instance. */
    private val healthConnectClient: HealthConnectClient? by lazy {
        if (isAvailable) {
            try {
                HealthConnectClient.getOrCreate(context)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    /** Set of required health permissions for reading and writing steps and workouts. */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    /** Backing mutable state flow for reactive step metrics. */
    private val _stepFlow = MutableStateFlow(
        StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    )

    override val stepFlow: StateFlow<StepUpdate> = _stepFlow.asStateFlow()

    /** Session start time recorded for post-session workout export. */
    private var sessionStartTime: Instant? = null

    /** Accumulated steps in the current session. */
    private var sessionStepCount: Int = 0

    /** Flag indicating whether tracking is currently active. */
    private var isTracking: Boolean = false

    /**
     * Verifies if all necessary Health Connect permissions have been granted by the user.
     *
     * @return True if read/write permissions for steps and exercise records are granted.
     */
    suspend fun hasAllPermissions(): Boolean = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext false
        try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Commences active walking session tracking for Health Connect export.
     *
     * @param initialSessionSteps Optional baseline offset for resuming an interrupted session.
     */
    override fun start(initialSessionSteps: Int) {
        sessionStartTime = Instant.now()
        sessionStepCount = initialSessionSteps
        isTracking = true
        _stepFlow.value = StepUpdate(
            sessionSteps = sessionStepCount,
            rawCumulativeSteps = sessionStepCount.toLong(),
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Temporarily halts active step accumulation.
     */
    override fun pause() {
        isTracking = false
    }

    /**
     * Resumes step accumulation.
     */
    override fun resume() {
        isTracking = true
    }

    /**
     * Halts tracking without resetting accumulated step counts.
     */
    override fun stop() {
        isTracking = false
    }

    /**
     * Resets session step metrics and clears session start timestamp.
     */
    override fun reset() {
        isTracking = false
        sessionStartTime = null
        sessionStepCount = 0
        _stepFlow.value = StepUpdate(
            sessionSteps = 0,
            rawCumulativeSteps = 0L,
            cadenceStepsPerMinute = 0,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Increments session steps from external provider updates (e.g. synchronized hardware sensor).
     *
     * @param steps Total steps accumulated in current session.
     * @param cadence Estimated cadence in steps per minute.
     */
    fun updateSessionSteps(steps: Int, cadence: Int) {
        if (!isTracking) return
        sessionStepCount = steps
        _stepFlow.value = StepUpdate(
            sessionSteps = steps,
            rawCumulativeSteps = steps.toLong(),
            cadenceStepsPerMinute = cadence,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /**
     * Reads today's total aggregate step count from Health Connect (Google Fit / Samsung Health).
     *
     * @return Total step count recorded for the current calendar day, or 0 if unavailable.
     */
    suspend fun readTodayTotalSteps(): Long = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext 0L
        try {
            val startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS)
            val now = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            response.records.sumOf { it.count }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Exports a completed walking workout session and step records into Health Connect.
     * Synchronizes immediately to Google Fit, Samsung Health, and wearable dashboards.
     *
     * @param sessionTitle Title description of the walking workout (e.g. "Mindful Walking Meditation").
     * @param totalSteps Total discrete steps taken during the completed walking session.
     * @param startTime Beginning timestamp of the walking session.
     * @param endTime Completion timestamp of the walking session.
     * @return True if records were successfully inserted into Health Connect, false otherwise.
     */
    suspend fun writeWalkingSession(
        sessionTitle: String,
        totalSteps: Int,
        startTime: Instant,
        endTime: Instant
    ): Boolean = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext false
        if (totalSteps <= 0 || endTime.isBefore(startTime)) return@withContext false

        try {
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

            // 1. Create discrete StepsRecord
            val stepsRecord = StepsRecord(
                count = totalSteps.toLong(),
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
            )

            // 2. Create ExerciseSessionRecord (Walking)
            val exerciseRecord = ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                title = sessionTitle,
                notes = "Recorded via Habit Bell Mindful Walking Timer",
                metadata = Metadata(recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY)
            )

            // Insert records atomically into Health Connect
            client.insertRecords(listOf(stepsRecord, exerciseRecord))
            true
        } catch (_: Exception) {
            false
        }
    }
}
