package com.habitbell.app.health

/**
 * # HealthProviderType
 *
 * Source classification for pedometer metrics, workout recording, and step synchronization.
 *
 * ## Architectural Role & Relationships
 * - Classifies underlying health platform bridges within [StepDataSource].
 * - Selected by user or system auto-detection in [HealthStepManager].
 *
 * ## Concurrency & Lifecycle
 * Immutable enum available globally across process lifecycle.
 */
enum class HealthProviderType(val displayName: String, val description: String) {
    /**
     * Native Android Hardware Pedometer sensor ([android.hardware.Sensor.TYPE_STEP_COUNTER]).
     * Provides sub-second, zero-latency, 100% offline step counts without requiring external accounts.
     */
    HARDWARE_SENSOR(
        displayName = "Device Pedometer",
        description = "Instantaneous hardware sensor with sub-second interval chime precision"
    ),

    /**
     * Android Health Connect client platform ([androidx.health.connect.client.HealthConnectClient]).
     * Integrates bi-directionally with Google Fit, Samsung Health, Fitbit, Whoop, and Garmin.
     */
    HEALTH_CONNECT(
        displayName = "Google Health Connect",
        description = "Syncs workouts and steps with Google Fit, Samsung Health, and wearables"
    ),

    /**
     * Apple HealthKit companion sync bridge.
     * Integrates with Apple Watch, iPhone HealthKit, and tvOS companion instances via local network.
     */
    APPLE_HEALTH_BRIDGE(
        displayName = "Apple Health Bridge",
        description = "Exports walking workouts and step records for Apple HealthKit sync"
    ),

    /**
     * High-precision simulated step generator for automated unit testing, CI pipelines,
     * and emulator environments lacking physical accelerometer hardware.
     */
    SIMULATED(
        displayName = "Step Simulator",
        description = "Synthesizes rhythmic walking cadence for testing and emulators"
    )
}
