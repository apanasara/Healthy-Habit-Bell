# Habit Bell — System Architecture & Developer Guide

Habit Bell is an offline-first, distraction-free wellness operating system engineered for Android, Android Auto, Google Cast, and Android TV / Google TV. The codebase is organized according to **Clean Architecture** principles combined with **MVI / MVVM (Model-View-Intent / Model-View-ViewModel)** with Unidirectional Data Flow (UDF), powered by Kotlin Coroutines and reactive `StateFlow`.

---

## 1. System Topology Overview

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

## 2. Modular Architecture Directory

To ensure fast reading, low token consumption for autonomous AI agents, and effortless modular maintenance, the system architecture is partitioned logically into the [`architecture/`](architecture/) directory:

| Document | Primary Domain / Subsystem | Key Classes & Handles |
| :--- | :--- | :--- |
| [**01. System Overview & Topology**](architecture/01-system-overview-and-topology.md) | Architectural tiers, Clean Architecture, MVI/MVVM UDF, reactive `StateFlow` | Master Topology, System Layers |
| [**02. Session & Timer Engines**](architecture/02-session-and-timer-engines.md) | Authoritative orchestrator, 5-state countdown FSM, monotonic sleep skew prevention | `CentralSessionHandler`, `TimerEngine` |
| [**03. Audio & Soundscape Engine**](architecture/03-audio-and-soundscape-engine.md) | Dual-engine bells, constant ambient volume, Lata voice profile, YouTube streaming, volume sync | `AudioBellManager`, `BackgroundMusicManager`, `PranayamaVoiceGuide`, `SystemVolumeObserver` |
| [**04. TV & Living Room Subsystems**](architecture/04-tv-and-living-room-subsystems.md) | Google Cast CAF v3, Miracast 1% backlight decoupling, WebCast, Apple TV, Web Receiver | `HabitBellCastManager`, `ScreenMirroringManager`, `LocalCastWebServer`, `DialTvDiscoverer` |
| [**05. Automotive & Voice Actions**](architecture/05-automotive-and-voice-actions.md) | Android Auto Car App Library v1.7.0, recents task removal (`onTaskRemoved`), Google Assistant | `HabitBellCarAppService`, `HabitBellCarSession`, `HabitBellCarScreen`, `HabitBellMediaService` |
| [**06. Presentation UI & Theming**](architecture/06-presentation-ui-and-theming.md) | Jetpack Compose, punch-hole safe geometry, state restoration, Sun/Moon circadian theming, branding | `SessionScreen`, `ModernHomeScreenSample`, `HabitBellTheme`, `CastButton` |
| [**07. Data Persistence & Presets**](architecture/07-data-persistence-and-presets.md) | In-memory cached repository, JSON persistence, 9 curated core wellness presets catalog | `TimerRepository`, `DefaultProfiles`, `TimerProfile` |
| [**08. Health & Step Tracking**](architecture/08-health-and-step-tracking.md) | Multi-provider step abstraction, Hardware Pedometer, Health Connect, Apple Health, step interval bells | `HealthStepManager`, `HardwarePedometerProvider`, `HealthConnectManager` |
| [**09. Settings & Display Automation**](architecture/09-settings-and-display-automation.md) | Dual-domain settings drawer, dedicated volume & cast sheets, AMOLED blackout curtain, sensor fusion | `SettingsDrawer`, `VolumeSettingsSheet`, `CastMirroringSheet`, `DisplayAutomationManager` |
| [**10. Classical Hatha Yoga Pranayama**](architecture/10-pranayama-subsystem.md) | Chaturanga breathwork, Tri-Bandha, Visama Vritti, procedural side-view lotus kinematics, terminal completion | `BreathIndicator`, `PranayamaVoiceGuide`, `PranayamaConfig` |
| [**11. Sūrya Namaskār Subsystem**](architecture/11-surya-namaskar-subsystem.md) | Room SQLite schema, animated vector postures, studio voice guide, Wear OS sync, live reflection | `SuryaDatabase`, `SuryaTimerScreen`, `SuryaVoicePlayer`, `SuryaSyncManager` |
| [**12. Fast-Paced Breath Counter**](architecture/12-fast-paced-breath-counter.md) | Unified Breathwork Counter (`kriya-breath-counter`), in-profile technique switching (Kapalabhati, Bhastrika, Bhramari), 3s ambient acoustic noise calibration, biquad IIR bandpass DSP, 3-stage hysteresis, manual tap | `AcousticBreathSensorProvider`, `BreathCountManager`, `BreathCounterContent` |
| [**13. Concurrency, Power & Standards**](architecture/13-concurrency-power-and-standards.md) | Master dispatchers & execution scopes table, hardware power management, developer & AI agent rules | Concurrency Matrix, Power Invariants, Engineering Standards |
| [**14. Mantra, Japa & Sacred Verse Counter**](architecture/14-mantra-and-sacred-verse-counter.md) | Unified Mantra & Sacred Verse Counter (`mantra-japa-counter`), multi-tradition support (Gayatri, Maha Mrityunjaya, Aumkar, Ram Japa, Tasbih, Jesus Prayer, Universal), Hardware AEC & Noise Suppression, ambient background music isolation, 3s ambient acoustic noise calibration, 800Hz voice formant biquad filter, intra-verse pause bridging, autocorrelation pitch tracking, 108-bead Mala ring | `AcousticMantraSensorProvider`, `MantraCountManager`, `MantraCounterContent`, `MantraCounterSettingsSheet` |

---

## 3. Autonomous Architecture Maintenance Protocol for AI Agents

Whenever an autonomous AI agent or engineer modifies, refactors, or extends a subsystem:
1. **Targeted Subsystem Updates**: Update the corresponding modular document under [`architecture/`](architecture/) (e.g. modify [`architecture/12-fast-paced-breath-counter.md`](architecture/12-fast-paced-breath-counter.md) when altering breath counting algorithms).
2. **Master Index Updates**: Update this root [`ARCHITECTURE.md`](ARCHITECTURE.md) only if introducing a new subsystem, altering global topology, or refactoring cross-cutting invariants.
3. **Token Efficiency**: Never read or load the entire architectural corpus when working on an isolated task; inspect only the specific modular document relevant to the feature under development.

---

## 4. Cross-Cutting Architectural Guarantees

- **Single Source of Truth**: `CentralSessionHandler` manages the lifecycle of the active session. All UI and automotive templates observe this state reactively.
- **Manual Play Commencement**: Navigating from the Home dashboard to a timer session loads the profile in `SessionStatus.IDLE` ("Ready") state without auto-running countdown; practice execution begins strictly when the user triggers the Play control.
- **Constant Ambient Volume Law (Requirement E2)**: Interval bells and session completion gongs layer additively over background music without requesting transient ducking audio focus.
- **Hardware Volume Law (Requirement E7)**: The in-app volume slider directly governs active hardware volume (`STREAM_MUSIC` on mobile, `CastSession` on TV).
- **Living Room Audio Handover**: When streaming to a Google Cast display or Custom Web Receiver, mobile background music is paused to eliminate acoustic echo while the TV receiver streams the user's configured ambient YouTube soundscape directly; upon Cast disconnection, mobile audio seamlessly resumes.
- **Zero-Internet Guarantee**: Offline operation is guaranteed across timer execution, acoustic procedural synthesis, and local screen mirroring.
