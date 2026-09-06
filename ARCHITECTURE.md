# Habit Bell — System Architecture & Developer Guide

## 1. Architectural Overview

Habit Bell is designed as an offline-first, distraction-free wellness operating system for Android. The architecture follows modern Android best practices: **Clean Architecture** combined with **MVI / MVVM (Model-View-Intent / Model-View-ViewModel)** with Unidirectional Data Flow (UDF), powered by Kotlin Coroutines and `StateFlow`.

```
                  +-------------------------------------------------------+
                  |                     UI Layer                          |
                  |  Jetpack Compose Screens & Animated Components        |
                  |  (HomeScreen, SessionScreen, BreathIndicator, etc.)   |
                  +---------------------------^---------------------------+
                                              | Observes StateFlow
                                              | Dispatches User Intents
                  +---------------------------v---------------------------+
                  |                  ViewModel Layer                      |
                  |  HabitBellViewModel (State Holder & Orchestrator)     |
                  +-------------+---------------------------+-------------+
                                |                           |
                                | Controls                  | Reads / Persists
                                v                           v
+-------------------------------+-------+   +---------------+---------------------+
|             Engine Layer              |   |             Data Layer              |
|  - TimerEngine (State Machine)        |   |  - TimerRepository                  |
|  - AudioBellManager (Tone/SoundPool)  |   |  - SharedPreferences Cache          |
|  - HapticManager (Tactile Patterns)   |   |  - Predefined Profiles & Reminders  |
|  - BackgroundMusicManager             |   |  - Domain Models (TimerProfile,     |
|  - BatteryOptimizer & TimerService    |   |    PranayamaConfig, CompoundConfig) |
+---------------------------------------+   +-------------------------------------+
                                |
             +------------------+------------------+
             |                                     |
             v                                     v
+------------+-------------+          +------------+-------------+
|    Android Auto Layer    |          |    TV & Cast Subsystem   |
|  - HabitBellCarAppService|          |  - LocalCastWebServer    |
|  - HabitBellCarSession   |          |  - NSD (mDNS/Bonjour)    |
|  - HabitBellCarScreen    |          |  - Server-Sent Events    |
|  - HabitBellMediaService |          |  - Leanback TV Dashboard |
+--------------------------+          +--------------------------+
```

---

## 2. Layered Responsibilities

### 2.1. Presentation Layer (`com.habitbell.app.ui`)
- **Jetpack Compose**: Pure declarative UI without legacy XML layouts or View bindings.
- **Unidirectional Data Flow (UDF)**:
  - Screens emit events (e.g., `onStartTimer`, `onPauseTimer`, `onSelectProfile`).
  - Screens observe immutable `TimerSessionState` and repository state exposed via `StateFlow` from `HabitBellViewModel`.
- **Custom Components**:
  - `BreathIndicator`: Canvas-driven dynamic scaling visualizer for 4-phase Pranayama breathing cycles.
  - `CircularProgressRing`: High-precision remaining time ring with stroke sweeps and color transitions.
  - `PocketOverlay`: `#000000` AMOLED power-saving curtain with double-tap/long-press protection activated by proximity sensor.
  - `CompoundPoseCard`: Step indicator for multi-step sequences (e.g., Yoga postures, Reiki hand positions).

### 2.2. Central Session & Engine Layer (`com.habitbell.app.engine`)
- **`CentralSessionHandler`**:
  - The process-level single source of truth and session orchestrator.
  - Owns the authoritative `MediaSessionCompat` ("HabitBellMediaSession") and coordinates bidirectional synchronization across all 5 control surfaces:
    1. **Car HUD (Android Auto)**: Transport controls and metadata via MediaSession.
    2. **Mobile Display (`SessionScreen`)**: Real-time Compose animations and playback toggles.
    3. **Car App Library Screen (`HabitBellCarScreen`)**: In-vehicle list templates and progress tracking.
    4. **Wear OS / Smartwatch**: Mirrored Android media session transport controls.
    5. **Smart TV Dashboard (`LocalCastWebServer`)**: LAN web broadcast with synchronized play/pause actions.
- **`TimerEngine`**:
  - The central heartbeat of the app. Implements a finite state machine (`IDLE`, `RUNNING`, `PAUSED`, `COMPLETED`).
  - Supports three distinct timer topologies:
    1. **`LINEAR`**: Single duration countdown with periodic interval chime triggers (e.g. mindful eating, meditation).
    2. **`MULTI_INTERVAL`**: Multi-round, 4-phase Pranayama breathwork (`INHALE`, `HOLD_IN`, `EXHALE`, `HOLD_OUT`).
    3. **`COMPOUND`**: Multi-pose sequencer iterating through distinct named steps with specific durations.
  - Time drift prevention: Relies on `SystemClock.elapsedRealtime()` to avoid system sleep skew and clock manipulation.
- **`AudioBellManager`**:
  - Dual-mode audio engine supporting low-latency procedural frequency synthesis and pre-rendered harmonic Tibetan singing bowl / chime samples (`SoundPool`).
  - **Automotive Audio Routing**: Configured with `AudioAttributes.USAGE_MEDIA` and `CONTENT_TYPE_MUSIC`. This guarantees bell chimes route over the vehicle audio system (Android Auto / Bluetooth A2DP) rather than isolating to the mobile handset speaker, while transient ducking smoothly lowers background music during the chime.
- **`BackgroundMusicManager`**: Ambient soundscape coordinator supporting bundled Aum drones, local SAF storage audio files, and ad-free sandboxed YouTube streaming.
- **`HapticManager`**: Tactile feedback generation using modern `VibrationEffect` with legacy fallback. **Constraint**: Haptics are strictly reserved for Pocket Mode to keep regular meditation silent.
- **`TimerService` / `HabitBellMediaService`**: Foreground service maintaining CPU execution (`PARTIAL_WAKE_LOCK`) and updating persistent `MediaStyle` notification linked to `CentralSessionHandler.sessionToken`.

### 2.3. Data & Domain Layer (`com.habitbell.app.data`)
- **Models**:
  - `TimerProfile`: Main aggregate defining duration, interval, bell chime pattern, and optional sub-configurations.
  - `PranayamaConfig`: Phase ratio definition (Inhale, Hold, Exhale, Hold) and round counts.
  - `CompoundConfig`: Sequence of poses, individual durations, switch bells, and repetition counts.
  - `RoutineReminder`: Daily habit scheduled reminders.
- **Repository (`TimerRepository`)**:
  - Clean repository abstraction managing persistence via encrypted/standard `SharedPreferences` serialized as JSON.
  - Emits in-memory updates through `StateFlow` to guarantee instantaneous reactivity across UI and platform services.

### 2.4. Cross-Platform & Peripheral Subsystems
- **Android Auto (`com.habitbell.app.auto`)**:
  - Integrates with Android for Cars App Library (`HabitBellCarScreen`) and `MediaBrowserServiceCompat` (`HabitBellMediaService`).
  - Directly binds to `CentralSessionHandler.sessionToken`. Ensures that actions from car steering wheel buttons, Car HUD, or mobile display remain 100% synchronized with 0 lag.
- **Local TV WebCast (`com.habitbell.app.cast`)**:
  - Zero-cloud local casting via embedded lightweight HTTP server (`LocalCastWebServer`) running on port `8888`.
  - Exposes `/api/state` and `/api/action/toggle` endpoints to keep Smart TV web browsers synchronized with the central session handler.

---

## 3. Concurrency & Threading Model

| Component | Coroutine Scope / Thread | Dispatcher | Rationale |
| :--- | :--- | :--- | :--- |
| `TimerEngine` | `CoroutineScope(SupervisorJob())` | `Dispatchers.Default` | Offloads 1-second countdown calculations and state emissions from the UI thread. |
| `TimerRepository` | `CoroutineScope` | `Dispatchers.IO` | Ensures disk I/O (SharedPreferences serialization) does not cause frame drops. |
| `HabitBellViewModel` | `viewModelScope` | `Dispatchers.Main.immediate` | Dispatches UI actions and handles state updates bound to ViewModel lifecycle. |
| `LocalCastWebServer` | Daemon Thread Pool | Dedicated Socket Threads | Non-blocking raw socket connection handling and HTTP asset streaming. |

---

## 4. Documentation Standards for Contributors and AI Agents

All source code in Habit Bell adheres strictly to the **Google Kotlin Style Guide** and **KDoc standards**:

1. **Every Class & Interface** must declare its role in the architecture, its lifecycle, and its threading expectations.
2. **Every Public and Protected Function** must include:
   - Summary of purpose and side-effects.
   - `@param` tags documenting units of measure (e.g., seconds vs. milliseconds, normalized `0.0f..1.0f`).
   - `@return` tags explaining data or Flow semantics.
   - `@throws` tags for checked or runtime exceptions that callers must handle.
3. **Internal Codeblocks** must include inline comments explaining algorithmic decisions, mathematical formulas, state machine invariants, and hardware platform workarounds.
4. **All Variables & Properties** must clearly state whether they represent mutable internal state, public reactive flows, configuration values, or hardware handles.
