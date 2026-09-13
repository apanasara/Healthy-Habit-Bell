# 08. Health & Step Tracking Subsystem

The health subsystem elevates Habit Bell into an embodied, distraction-free walking meditation and wellness tracker. It abstracts step telemetry, cadence monitoring, and workout persistence across fragmented health ecosystems.

---

## 1. Architecture & Multi-Provider Abstraction

- **`StepDataSource.kt`**: Unified interface establishing the reactive contract:
  - `providerType: HealthProviderType`
  - `isAvailable: Boolean`
  - `stepFlow: StateFlow<StepUpdate>`
  - Lifecycle hooks: `start(initialSessionSteps)`, `pause()`, `resume()`, `stop()`, `reset()`.
- **`StepUpdate.kt`**: Immutable DTO capturing:
  - `sessionSteps`: Steps accumulated during active session.
  - `rawCumulativeSteps`: Hardware/platform boot count.
  - `cadenceStepsPerMinute`: Instantaneous SPM.
  - `timestampMillis`: Telemetry event timestamp.
- **`HealthProviderType.kt`**: Source classification (`HARDWARE_SENSOR`, `HEALTH_CONNECT`, `APPLE_HEALTH_BRIDGE`, `SIMULATED`).

---

## 2. Provider Implementations

- **Native Hardware Pedometer (`HardwarePedometerProvider.kt`)**:
  - Direct listener for Android's hardware `Sensor.TYPE_STEP_COUNTER`.
  - **Zero Latency & 100% Offline**: Delivers sub-second step detection without network, external accounts, or cloud dependencies.
  - **Temporal Sliding Window Cadence**: Computes instantaneous cadence (steps per minute) using timestamped step buffers over a 5-second sliding window, filtering sensor noise and jitter.
- **Google Health Connect (`HealthConnectManager.kt`)**:
  - Integrates AndroidX Health Connect (`androidx.health.connect:connect-client:1.1.0-alpha11`).
  - **Bi-Directional Ecosystem Sync**: Connects with Google Fit, Samsung Health, Fitbit, Whoop, and Garmin.
  - **Workout Recording**: Writes `ExerciseSessionRecord` (type: `EXERCISE_TYPE_WALKING`) and corresponding `StepsRecord` aggregates to the Health Connect datastore upon session completion.
  - **Permission Contract**: Handles runtime permission rationale flows for `HealthPermission.getReadPermission(StepsRecord::class)`, `HealthPermission.getWritePermission(StepsRecord::class)`, and `HealthPermission.getWritePermission(ExerciseSessionRecord::class)`.
- **Apple Health Bridge (`AppleHealthBridgeManager.kt`)**:
  - Generates Apple HealthKit-compliant JSON workout descriptors (`HKWorkoutActivityTypeWalking`, `HKQuantityTypeIdentifierStepCount`).
  - Coordinates cross-platform synchronization with `tv-platforms/apple-tvos` companion instances and HealthKit export tools.
- **Deterministic Step Simulator (`SimulatedStepProvider.kt`)**:
  - Generates rhythmic walking cadence (~108 steps per minute) for automated JUnit tests, CI pipelines, and emulator environments lacking physical accelerometer sensors.
  - Supports discrete step injection via `injectSteps(count)` for instant boundary testing.
- **Central Health Orchestrator (`HealthStepManager.kt`)**:
  - Central singleton managing active provider selection, lifecycle delegation, and runtime permission verification (`ACTIVITY_RECOGNITION`).

---

## 3. Step-Based Interval Bells & Session Completion Math

- **Dynamic Cadence Tracking**: Live calculation of steps per minute (SPM) rendered on Session HUDs.
- **Interval Bell Countdown**:
  - Mathematical interval tracking:
    $$\text{stepsIntoInterval} = \text{currentSteps} \pmod{\text{stepInterval}}$$
    $$\text{nextStepBellSteps} = \text{stepInterval} - \text{stepsIntoInterval}$$
  - Whenever `currentSteps % stepInterval == 0` during active walking, `AudioBellManager` plays the configured interval bell (Option C Zen Tingsha) with an accompanying distinct 2-pulse tactile vibration.
- **Step Trigger Policies (`StepTriggerMode`)**:
  - `TIME_ONLY`: Session completes only when total configured timer seconds expire.
  - `STEPS_ONLY`: Session runs until the target step goal is achieved (e.g. exactly 3,000 steps).
  - `TIME_OR_STEPS`: Whichever target is reached first (time expires or step goal met) triggers completion.
- **Multi-Surface Car HUD & Media Synchronization**:
  - Active step counts and cadence are dynamically injected into `MediaSessionCompat` metadata subtitles (e.g. `"1,250 / 3,000 steps • 108 SPM • Next bell: 250 steps"`).
  - Automatically rendered on Android Auto vehicle displays, smartwatch notification cards, and locked screen media players.
