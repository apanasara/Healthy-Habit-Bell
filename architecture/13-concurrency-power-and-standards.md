# 13. Concurrency, Power & Engineering Standards

This document establishes the process-wide threading architecture, hardware power management invariants, and collaboration standards for parallel developers and autonomous AI coding agents.

---

## 1. Concurrency & Threading Architecture

| Component | Scope / Execution Context | Dispatcher | Architectural Rationale |
| :--- | :--- | :--- | :--- |
| `TimerEngine` | `CoroutineScope(SupervisorJob())` | `Dispatchers.Default` | Offloads 1Hz countdown math, state machine transitions, and state emissions from the UI thread. |
| `TimerRepository` | Dedicated Coroutine Scope | `Dispatchers.IO` | Ensures JSON serialization and disk I/O do not cause UI frame drops. |
| `HabitBellViewModel` | `viewModelScope` | `Dispatchers.Main.immediate` | Dispatches UI actions and handles state updates bound to ViewModel lifecycle. |
| `HealthStepManager` | `CoroutineScope(SupervisorJob())` | `Dispatchers.Default` | Coordinates step telemetry across sensor callbacks and updates session state off UI thread. |
| `HardwarePedometerProvider` | Hardware Sensor Event Thread | `SensorManager.SENSOR_DELAY_UI` | Receives raw step counts from OS sensor subsystem and buffers timestamps for sliding-window cadence calculation. |
| `HealthConnectManager` | Dedicated Coroutine Scope | `Dispatchers.IO` | Handles async Health Connect client queries, permissions, and workout record insertions. |
| `LocalCastWebServer` | Daemon Thread Pool | Dedicated Socket Threads | Handles non-blocking raw socket requests, SSE streams, and asset delivery. |
| `HabitBellCastManager` | Main Thread / Google Play Services | `Dispatchers.Main` | Integrates with Cast Framework callbacks, UI updates, and async Cast session events. |
| `AirPlayCastManager` | `CoroutineScope(SupervisorJob())` + NSD | `Dispatchers.IO` | Dispatches Apple TV mDNS discovery events and handles RTSP / HTTP streaming asynchronously. |
| `DialTvDiscoverer` | `CoroutineScope(SupervisorJob())` | `Dispatchers.IO` | Manages SSDP UDP multicast socket probes and HTTP device descriptor XML parsing off the main thread. |
| `DisplayAutomationManager` | `CoroutineScope(SupervisorJob())` + Sensor Thread | `Dispatchers.Default` | Processes multi-sensor fusion (proximity, lux, gravity, significant motion), orchestrates 10s flat countdowns, and emits atomic `DisplayCurtainState`. |
| `BackgroundMusicManager` | Main Thread + Background Decode | `Dispatchers.Main` / Media | Coordinates headless WebView audio rendering, MediaPlayer playback, and audio focus ducking. |
| `PranayamaVoiceGuide` | System TTS Engine Callback Thread | `Dispatchers.Main` / AudioTrack | Coordinates offline Android TextToSpeech synthesis, gentle female voice selection, and dynamic background music ducking. |
| `SuryaDatabase` | Process Singleton / Room Pool | `Dispatchers.IO` | Manages SQLite connection pooling, schema migrations, and async DAO query executions. |
| `SuryaTimerViewModel` | `viewModelScope` | `Dispatchers.Main.immediate` | Observes reactive step StateFlows and dispatches preset updates and background seeding to IO. |
| `SuryaSyncManager` | Dedicated Sync Scope | `Dispatchers.IO` | Serializes configuration JSON payloads and pushes them over Wearable DataClient asynchronously. |
| `SuryaVoicePlayer` | Main Thread Coroutine Scope | `Dispatchers.Main` / AudioTrack | Manages speech cue MediaPlayer instances, 120ms lead delay, and background music ducking. |
| `AcousticBreathSensorProvider` | Dedicated IO Coroutine Job | `Dispatchers.IO` | Reads non-blocking 16kHz PCM audio buffers from `AudioRecord`, executes bandpass DSP / autocorrelation pitch detection, and computes RMS energy off UI thread. |
| `BreathCountManager` | `CoroutineScope(SupervisorJob())` | `Dispatchers.Default` | Manages breath state transitions (`STROKES` -> `RETENTION_HOLD` -> `REST`), milestone triggers, and telemetry emission. |

---

## 2. Hardware & Power Management

1. **CPU WakeLock (`BatteryOptimizer.kt`)**: Acquires `PowerManager.PARTIAL_WAKE_LOCK` (`"HabitBell:TimerWakeLock"`) during active countdowns to prevent the OS from suspending the CPU when the screen turns off.
2. **Wi-Fi Multicast Lock**: Acquires `WifiManager.MulticastLock` (`"HabitBellTVMulticast"`) when TV Webcast is active to ensure mDNS / Bonjour packets pass through Android's network power-saving filters.
3. **Proximity Sensor Monitoring**: Monitors device proximity in active sessions to automatically toggle Pocket Mode and the `#000000` AMOLED power curtain.
4. **Automotive Audio Routing & Ambient Volume Preservation (Requirement E2)**: Routes bell and soundscape streams via `AudioAttributes.USAGE_MEDIA` with `CONTENT_TYPE_MUSIC` to car audio systems while keeping ambient volume constant without intrusive ducking or volume snapping.
5. **Pedometer & Activity Recognition Management**: Registers hardware step counter sensors with `SENSOR_DELAY_UI` only during active walking timer sessions; unregisters immediately upon pause, stop, or completion to prevent battery drain. Dynamically checks and requests `Manifest.permission.ACTIVITY_RECOGNITION` on Android 10+ (API 29+).
6. **Multi-Sensor Display Automation**: Powers off OLED pixels using `#000000` blackout curtain across Pocket Mode, Android Auto Car HUD, Smart TV casting, and Wear OS companion states. Employs hardware `TYPE_SIGNIFICANT_MOTION` trigger and Z-axis gravity vector analysis for battery-efficient, zero-latency Lift-to-Wake and 10-second flat inactivity timeout.
7. **Microphone Audio Recording Lifecycle & Privacy/Power Conservation**: Registers `AudioRecord` with 16kHz 16-bit Mono strictly during active `STROKES` phases of breath counting sessions. Immediately halts recording, flushes buffers, and releases hardware handles during `RETENTION_HOLD` (Kumbhaka), `REST`, pause states, and session termination, ensuring zero background battery drain and absolute user privacy.

---

## 3. Standards for Parallel Developers & Autonomous AI Agents

To ensure seamless collaboration across parallel developers and autonomous AI coding agents:

1. **Mandatory Documentation Standards**:
   - **KDoc on All Public APIs**: Every class, interface, and function must include full KDoc describing architectural role, concurrency model, `@param` units of measure, `@return` semantics, and `@throws` exceptions.
   - **Inline Explanations**: Non-trivial algorithms, state machine transitions, audio synthesis formulas, and hardware workarounds must include explanatory comments.
2. **Feature Branch Lifecycle & Zero-Lag Sync**:
   - Every feature or refactor must be developed on a dedicated branch.
   - On completion, open a Pull Request targeting `origin/main` and complete the merge operation.
   - Always verify zero lag with upstream: `git rev-list --left-right --count origin/main...main` returning `0 0`.
3. **Autonomous Architecture Maintenance Protocol**:
   - **Rule**: Whenever any architectural change, new platform subsystem, or major feature is introduced, the developer/agent MUST autonomously update the relevant modular documentation in `architecture/<subsystem>.md` (and `ARCHITECTURE.md` if changing topology or adding a new subsystem) as part of that change, without requiring user prompting.
   - **Goal**: Maintain the modular documentation suite as the living, authoritative blueprint of Habit Bell while keeping context windows lean and token consumption minimal.
