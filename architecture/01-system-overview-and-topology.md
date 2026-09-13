# 01. System Overview & System Topology

Habit Bell is an offline-first, distraction-free wellness operating system engineered for Android, Android Auto, Google Cast, and Android TV / Google TV. The codebase is organized according to **Clean Architecture** principles combined with **MVI / MVVM (Model-View-Intent / Model-View-ViewModel)** with Unidirectional Data Flow (UDF), powered by Kotlin Coroutines and reactive `StateFlow`.

---

## 1. Architectural Topology Diagram

```
                  +-------------------------------------------------------------------------+
                  |                             Presentation Layer                          |
                  |  - Jetpack Compose Screens (HomeScreen, SessionScreen, SettingsDrawer)  |
                  |  - Living Room Ecosystem (Google Cast, Miracast Wireless Display)       |
                  |  - Animated Components (BreathIndicator, CircularProgressRing, Overlay) |
                  |  - System Bar Immersion (WindowInsetsControllerCompat status bar hide)  |
                  |  - Universal Cast Surface (CastButton, MediaRouteDialog integration)    |
                  +------------------------------------^------------------------------------+
                                                       | Observes StateFlow / Cast State
                                                       | Dispatches User / Remote Intents
                  +------------------------------------v------------------------------------+
                  |                              ViewModel Layer                            |
                  |  HabitBellViewModel (Single UI State Orchestrator & Action Dispatcher)  |
                  +---------------------+-----------------------------+---------------------+
                                        |                             |
                                        | Controls                    | Reads / Persists
                                        v                             v
+---------------------------------------+-------+     +---------------+---------------------+
|                  Central Engine Layer         |     |                  Data Layer         |
|  - CentralSessionHandler (Authoritative Hub)  |     |  - TimerRepository                  |
|  - MediaSessionCompat ("HabitBellMediaSession)|     |  - SharedPreferences JSON Store    |
|  - TimerEngine (1Hz FSM Countdown Core)       |     |  - Predefined Profiles & Reminders  |
|  - BreathCountManager (Fast Breath Counter)   |     |  - Domain Models (TimerProfile,     |
|  - AudioBellManager (SoundPool + Procedural)  |     |    PranayamaConfig, CompoundConfig) |
|  - BackgroundMusicManager (Aum / SAF / YouTube|     +-------------------------------------+
|  - BatteryOptimizer & TimerService            |
|  - HapticManager (Pocket-Mode Vibrations)     |
|  - HealthStepManager (Multi-Platform Steps)   |
+---------------------------------------+-------+
                                        |
      +---------------------------------+---------------------------------+
      |                                 |                                 |
      v                                 v                                 v
+-----+----------------------+    +-----+----------------------+    +-----+----------------------+
|     Android Auto Layer     |    |    TV & Cast Subsystems    |    |   Health & Wearables       |
|  - HabitBellCarAppService  |    |  1. Google Cast Framework  |    |  1. Google Health Connect  |
|  - HabitBellCarSession     |    |     (HabitBellCastManager) |    |     (Google Fit, Samsung)  |
|  - HabitBellCarScreen      |    |  2. Local TV Webcast (SSE) |    |  2. Apple Health Bridge    |
|  - HabitBellMediaService   |    |  3. Android TV & Google TV |    |     (HealthKit JSON/tvOS)  |
|  - Automotive Media Routing|    |     (Sony, TCL, Leanback)  |    |  3. Hardware Pedometer     |
|    (USAGE_MEDIA -> Car HUD)|    |  4. Samsung & LG (DIAL)    |    |     (Offline Step Sensor)  |
|                            |    |  5. Apple TV (AirPlay 2)   |    |  4. Step Simulator (CI)    |
+----------------------------+    +----------------------------+    +----------------------------+
```

---

## 2. Core Architectural Tenets

1. **Unidirectional Data Flow (UDF)**:
   - State flows down from domain managers through `HabitBellViewModel` via immutable `StateFlow<TimerUiState>` and `StateFlow<TimerSessionState>`.
   - Actions and events flow up via explicit user intents or hardware callbacks to `HabitBellViewModel` and `CentralSessionHandler`.

2. **Decoupled Single Responsibility**:
   - Each subsystem manages an isolated aspect of the application lifecycle (timing, audio synthesis, casting, step counting, display automation).
   - The authoritative coordination between these subsystems is delegated entirely to `CentralSessionHandler`.

3. **Multi-Surface Continuity**:
   - The runtime maintains a single authoritative media session (`MediaSessionCompat`) synchronized across Mobile, Android Auto, Wear OS, Google Cast, and TV WebCast.
   - Any transport action dispatched on one surface (e.g. steering wheel play/pause) reflects immediately across all surfaces with zero state drift.

4. **Offline-First & Distraction-Free**:
   - Every core feature—interval bells, procedural tone synthesis, yogic breath tracking, and screen mirroring—functions without active internet connectivity.
   - Zero ads, zero tracking, and pure `#000000` AMOLED blackout mechanisms preserve meditative tranquility.
