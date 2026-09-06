# Habit Bell — System Architecture & Developer Guide

## 1. Architectural Overview & System Topology

Habit Bell is an offline-first, distraction-free wellness operating system engineered for Android, Android Auto, Google Cast, and Android TV / Google TV. The codebase is organized according to **Clean Architecture** principles combined with **MVI / MVVM (Model-View-Intent / Model-View-ViewModel)** with Unidirectional Data Flow (UDF), powered by Kotlin Coroutines and reactive `StateFlow`.

```
                  +-------------------------------------------------------------------------+
                  |                             Presentation Layer                          |
                  |  - Jetpack Compose Screens (HomeScreen, SessionScreen, SettingsDrawer)  |
                  |  - Android TV & Google TV Leanback UI (TVDashboardScreen, D-Pad focus)  |
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
|  - AudioBellManager (SoundPool + Procedural)  |     |  - Domain Models (TimerProfile,     |
|  - BackgroundMusicManager (Aum / SAF / YouTube|     |    PranayamaConfig, CompoundConfig) |
|  - BatteryOptimizer & TimerService            |     +-------------------------------------+
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
+----------------------------+    |  5. Apple TV (AirPlay 2)   |    |  4. Step Simulator (CI)    |
                                  +----------------------------+    +----------------------------+
```

---

## 2. Layered Responsibilities & Core Subsystems

### 2.1. Central Session Handler (`CentralSessionHandler.kt`)
The `CentralSessionHandler` is the **process-level single source of truth** and authoritative orchestrator across the entire application runtime. It initializes and synchronizes the central `MediaSessionCompat` (`"HabitBellMediaSession"`), `TimerEngine`, `AudioBellManager`, `BackgroundMusicManager`, `BatteryOptimizer`, and `HabitBellCastManager`.

#### Multi-Surface Bidirectional Synchronization
The central session coordinates transport controls and metadata across **6 distinct control surfaces**:
1. **Automotive Head Unit (Android Auto)**: Media controls (play/pause/skip), progress scrubbers, and metadata displayed on the vehicle dashboard via `MediaSessionCompat`.
2. **Mobile Compose UI (`SessionScreen`)**: Real-time timer countdown, circular progress sweeps, dynamic breathing visualizers, and pocket mode.
3. **Android for Cars App Screen (`HabitBellCarScreen`)**: Template-based vehicle screen providing distraction-free timer selection and active session monitoring.
4. **Wear OS & Smartwatches**: Mirrored Android media session transport controls.
5. **Google Cast TV Receivers**: Cast receiver streaming session state, animated countdowns, and progress rings via the native Google Cast Framework.
6. **Smart TV Web Browsers**: Zero-cloud LAN web broadcast served by `LocalCastWebServer` on port `8888`.

---

### 2.2. Audio & Soundscape Engine
The audio architecture guarantees high-fidelity, boundary-free sound reproduction across both handset and vehicle audio systems.

#### 1. Dual-Engine Bell Chimes (`AudioBellManager.kt`)
- **Procedural Tone Synthesis**: Real-time sine-wave calculation with exponential decay envelope (e.g. 432Hz healing frequency, 528Hz Solfeggio frequency) synthesized directly to low-latency `AudioTrack` streams.
- **Harmonic Sample Audio (`SoundPool`)**:
  - **Option C (3-Bell Zen Tingsha)**: High-resolution countdown chime used for periodic interval bells.
  - **Temple Gong**: Rich, resonant low-frequency acoustic bell triggered upon session completion.
- **Automotive Audio Routing**:
  - Configured strictly with `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_MUSIC`.
  - **Architectural Rationale**: Routing as `USAGE_MEDIA` ensures that when connected to Android Auto or Bluetooth A2DP, interval bells and completion gongs play through vehicle stereo speakers rather than being isolated to the smartphone handset speaker.
  - **Transient Ducking**: Audio focus requests with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` smoothly lower background music and third-party media during bell strikes without interrupting playback.

#### 2. Ambient Soundscape Subsystem (`BackgroundMusicManager.kt`)
- **Bundled Ambient Drones**: High-definition continuous Aum chant drone bundled compile-time via `R.raw.aum`.
- **Local User Storage**: Seamless playback of custom audio files loaded via the Storage Access Framework (SAF).
- **Sandboxed YouTube Audio Streaming**:
  - Headless, ad-free YouTube audio extraction and streaming engine using an isolated `WebView`.
  - Injects custom JavaScript to suppress video canvas rendering, minimize CPU usage, and guarantee seamless looping and persistent custom URL playback.

---

### 2.3. Timer Engine & State Machine (`TimerEngine.kt`)
The heartbeat of the mindfulness runtime is a deterministic finite state machine operating with four distinct lifecycle states:
`IDLE` ➔ `RUNNING` ⇄ `PAUSED` ➔ `COMPLETED`

- **Timer Topologies**:
  1. **`LINEAR`**: Single duration countdown with customizable periodic interval chimes (e.g. Mindful Eating default 45m with 1m interval chime, Zen Meditation).
  2. **`MULTI_INTERVAL`**: 4-phase cyclic Pranayama breathwork (`INHALE`, `HOLD_IN`, `EXHALE`, `HOLD_OUT`) with dynamic ratio scaling and round counting.
  3. **`COMPOUND`**: Multi-step sequencer iterating through distinct named poses (Yoga sequences, Reiki hand placements) with transition bells.
- **Clock Manipulation & Sleep Skew Prevention**:
  - Relies on monotonic `SystemClock.elapsedRealtime()` calculations rather than wall-clock time (`System.currentTimeMillis()`) to protect against time drift, timezone changes, and device sleep states.
- **Ambient Auto-Dimming & Pocket Mode**:
  - Integrated 10-second countdown for ambient screen dimming.
  - **Pocket Mode**: Activated via proximity sensor. Automatically engages a pure `#000000` AMOLED power curtain (`PocketOverlay`) and switches chime feedback to a silent 3-pulse tactile vibration (`VibrationEffect`), keeping standard meditation silent.

---

### 2.4. TV & Living Room Subsystems

#### 1. Android TV & Google TV Leanback Support (Sony Bravia, TCL, Hisense, Chromecast)
- **Universal Single APK**: A single binary deployment targets smartphones, tablets, foldables, automotive head units, and Android TV / Google TV.
- **Sony Bravia Hardware Integration**: All modern Sony Bravia smart TVs run Google TV / Android TV with Chromecast built-in. Habit Bell provides first-class Sony compatibility out of the box via both native APK installation and Google Cast streaming.
- **Manifest Architecture**:
  - Declares `<category android:name="android.intent.category.LEANBACK_LAUNCHER" />` for TV app drawers.
  - Declares `android:banner="@drawable/tv_banner"` for high-resolution 16:9 Android TV launcher cards.
  - Features marked optional (`required="false"`): `android.software.leanback`, `android.hardware.touchscreen`, `android.hardware.microphone`, `android.hardware.telephony`, `android.hardware.camera`.
- **Remote Control & D-Pad Ergonomics**: `TVDashboardScreen` provides high-contrast D-pad focus traversal, large-format countdown typography, and oversized action buttons for 10-foot TV viewing.

#### 2. Google Cast Framework (`com.habitbell.app.cast`)
- **Native Cast Integration**: Pure application TV streaming without screen mirroring using Google Play Services Cast Framework (`play-services-cast-framework:22.0.0`).
- **`CastOptionsProvider.kt`**: Registers the official Default Media Receiver application ID (`CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`).
- **`HabitBellCastManager.kt`**: Singleton session manager coordinating discovery, device connection, and media metadata transmission to Chromecast, Sony Bravia, and Google Cast-enabled TVs.
- **`CastButton.kt`**: Jetpack Compose-native Cast button wrapping AndroidX MediaRouter's `MediaRouteButton` to display discovery states and trigger device selection dialogs.
- **Host Activity Architecture**: `MainActivity` inherits from `androidx.fragment.app.FragmentActivity` to provide the `FragmentManager` required by `MediaRouteButton` to display native Google Cast route picker dialogs across all Android platforms without runtime crashes.

#### 3. Local TV WebCast (`LocalCastWebServer.kt`)
- **Zero-Cloud Local Casting**: Embedded lightweight multi-threaded HTTP server running on port `8888`.
- **Network Service Discovery (NSD)**: Registers an mDNS service (`_habitbell._tcp`) allowing any Smart TV browser on the same Wi-Fi network to discover and open the TV dashboard.
- **Server-Sent Events & Real-Time Sync**: Exposes `/api/state` for real-time SSE broadcasts of timer progress and `/api/action/toggle` for bidirectional remote playback control from the TV browser.

#### 4. Samsung Smart TV (Tizen OS) & LG Smart TV (webOS) Ecosystem
- **Market Reach**: Samsung Tizen (~21%) and LG webOS (~12%) represent >33% of global connected smart TVs.
- **Packaged Web TV Suite**:
  - `tv-platforms/samsung-tizen/`: Packaged Tizen Web Application container (`.wgt`) with `config.xml` manifest and Samsung TV Remote key handling (`tizen.tvinputdevice.registerKey`).
  - `tv-platforms/lg-webos/`: Packaged LG webOS application (`.ipk`) with `appinfo.json` descriptor and Magic Remote pointer/D-pad mappings.
- **`DialTvDiscoverer.kt` Subsystem**:
  - Dispatches SSDP (Simple Service Discovery Protocol) M-SEARCH UDP multicast probes (`239.255.255.250:1900`) for DIAL services (`urn:dial-multiscreen-org:service:dial:1`) and UPnP `MediaRenderer`.
  - Auto-identifies Samsung and LG TVs on the local Wi-Fi and provides zero-click remote launching of the TV dashboard.

#### 5. Apple TV & AirPlay 2 Subsystem (`com.habitbell.app.cast`)
- **Market Context**: Apple TV (tvOS) dominates the premium streaming box sector. Because tvOS contains no web browser, Habit Bell deploys a dual-track strategy:
- **Track 1 — Direct AirPlay 2 Sender Protocol (`AirPlayCastManager.kt`)**:
  - Scans for nearby Apple TV devices on local Wi-Fi via mDNS / Bonjour (`_airplay._tcp.` and `_raop._tcp.`).
  - Maintains reactive `discoveredDevices: StateFlow<List<AirPlayDevice>>` for casting session metadata and audio to Apple TV hardware.
- **Track 2 — Native Apple TV Companion App (`tv-platforms/apple-tvos/`)**:
  - Native Swift 5.10+ / SwiftUI application built for tvOS 17+.
  - Features circular countdown stroke animation, Siri Remote Clickpad gestures, and real-time Bonjour mDNS discovery (`_http._tcp.`) auto-syncing with `LocalCastWebServer` on the Android device via Server-Sent Events.

---

### 2.5. Android Auto Subsystem (`com.habitbell.app.auto`)
Habit Bell provides deep automotive integration complying with Android for Cars design guidelines:
- **`HabitBellCarAppService.kt` & `HabitBellCarSession.kt`**: Entry point for Android Auto projecting template-based screens (`HabitBellCarScreen`) to the in-dash screen.
- **`HabitBellMediaService.kt`**: Extends `MediaBrowserServiceCompat` to provide media library browsability in automotive media drawers (`CATEGORY_PROJECTION`, `CATEGORY_CAR_MODE`).
- **Session Token Sharing**: Both services bind directly to `CentralSessionHandler.sessionToken`, guaranteeing that media button presses on vehicle steering wheels instantly control the central timer engine with zero lag.

---

### 2.6. Google Assistant & Voice Actions
- **App Actions & Shortcuts (`shortcuts.xml`)**: Maps built-in intents (`actions.intent.START_EXERCISE`, `actions.intent.STOP_EXERCISE`) to Habit Bell timer profiles.
- **Voice Invocations**:
  - *"OK Google, start mindful eating on Habit Bell"*
  - *"OK Google, start meditation on Habit Bell"*
- **Intent Deep-Linking**: `MainActivity` extracts voice intent parameters and passes them directly to `HabitBellViewModel` to launch the requested session immediately.

---

### 2.7. Presentation Layer & Immersive Display
- **Jetpack Compose**: 100% declarative UI built with Material 3 design tokens.
- **Distraction-Free Immersion**: When a timer session transitions to `RUNNING`, `MainActivity` uses `WindowInsetsControllerCompat` to hide the system status bar and navigation bar (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), preventing notification distractions during mindfulness sessions.
- **Key Visual Components**:
  - `BreathIndicator`: Canvas-drawn dynamic expanding/contracting circle visualizing the 4 phases of Pranayama.
  - `CircularProgressRing`: High-precision remaining-time stroke animation with smooth color interpolation.
  - `CompoundPoseCard`: Step indicator for multi-pose yoga and reiki sequences.
  - `PocketOverlay`: Pure black `#000000` AMOLED overlay with double-tap unlock protection.

---

### 2.8. Data & Persistence Layer (`com.habitbell.app.data`)
- **`TimerRepository.kt`**: Clean repository managing persistence via encrypted / standard `SharedPreferences` serialized as JSON.
- **Reactive State Flow**: In-memory caching ensures instantaneous reactivity across the UI layer and platform background services.
- **Domain Models**:
  - `TimerProfile`: Core aggregate defining duration, interval, bell pattern, sound style, and custom metadata.
  - `PranayamaConfig`: 4-phase breathing cycle specifications.
  - `CompoundConfig`: Sequence of pose definitions, durations, and transition sounds.
  - `RoutineReminder`: Scheduled daily habit reminders.

---

### 2.9. Health & Step Tracking Subsystem (`com.habitbell.app.health`)

The health subsystem elevates Habit Bell into an embodied, distraction-free walking meditation and wellness tracker. It abstracts step telemetry, cadence monitoring, and workout persistence across fragmented health ecosystems:

#### 1. Architecture & Multi-Provider Abstraction
- **`StepDataSource.kt`**: Unified interface establishing the reactive contract:
  - `providerType: HealthProviderType`
  - `isAvailable: Boolean`
  - `stepFlow: StateFlow<StepUpdate>`
  - Lifecycle hooks: `start(initialSessionSteps)`, `pause()`, `resume()`, `stop()`, `reset()`.
- **`StepUpdate.kt`**: Immutable DTO capturing `sessionSteps` (steps accumulated during this session), `rawCumulativeSteps` (hardware/platform boot count), `cadenceStepsPerMinute` (SPM), and `timestampMillis`.
- **`HealthProviderType.kt`**: Source classification (`HARDWARE_SENSOR`, `HEALTH_CONNECT`, `APPLE_HEALTH_BRIDGE`, `SIMULATED`).

#### 2. Provider Implementations
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

#### 3. Step-Based Interval Bells & Session Completion Math
- **Dynamic Cadence Tracking**: Live calculation of steps per minute (SPM) rendered on Session HUDs.
- **Interval Bell Countdown**:
  - Mathematical interval tracking: `stepsIntoInterval = currentSteps % stepInterval`.
  - `nextStepBellSteps = stepInterval - stepsIntoInterval`.
  - Whenever `currentSteps % stepInterval == 0` during active walking, `AudioBellManager` plays the configured interval bell (Option C Zen Tingsha) with an accompanying distinct 2-pulse tactile vibration.
- **Step Trigger Policies (`StepTriggerMode`)**:
  - `TIME_ONLY`: Session completes only when total configured timer seconds expire.
  - `STEPS_ONLY`: Session runs until the target step goal is achieved (e.g. exactly 3,000 steps).
  - `TIME_OR_STEPS`: Whichever target is reached first (time expires or step goal met) triggers completion.
- **Multi-Surface Car HUD & Media Synchronization**:
  - Active step counts and cadence are dynamically injected into `MediaSessionCompat` metadata subtitles (e.g. `"1,250 / 3,000 steps • 108 SPM • Next bell: 250 steps"`).
  - Automatically rendered on Android Auto vehicle displays, smartwatch notification cards, and locked screen media players.

---

### 2.7. Dual-Domain Settings Architecture & Acoustic Identity (`SettingsDrawer.kt`)

To eliminate vertical clutter and decouple dynamic session parameters from persistent system hardware settings, the configuration drawer is structured into two strict architectural domains mediated by a top segmented `TabRow` (`SettingsDrawerTab`):

```
┌─────────────────────────────────────────────────────────────┐
│   [ ⏱ Timer Settings ({profile.name}) ]  |  [ ⚙️ Global Config ] │
├─────────────────────────────────────────────────────────────┤
│ • Dynamic Target (Time slider or Step Goal)                  │
│ • Dynamic Interval Cue (Time interval or Step cadence)      │
│ • Ambient Soundscape (Aum / YouTube / Custom audio)         │
│ • Signature Acoustic Audition (Option C Chime & Gong)        │
└─────────────────────────────────────────────────────────────┘
```

#### 1. Domain 1: Dynamic Timer Settings (`SettingsDrawerTab.TIMER`)
- **Adaptive Session Targets**:
  - **Walking & Movement Profiles** (`isStepTrackingEnabled == true`): Displays target Step Goal chips (`None`, `1,000`, `2,000`, `3,000`, `5,000` steps) with automated completion evaluation.
  - **Linear Timers** (`TimerType.LINEAR`): Displays continuous session duration slider ($1\text{m}..60\text{m}$), fine stepper buttons (`-1m`, `+1m`, `+5m`), and instant preset chips (`10m`, `15m`, `20m`, etc.).
- **Adaptive Interval Pacing Cues**:
  - Automatically switches between Step Interval cadence (`None`, `250`, `500`, `1,000` steps) for physical locomotion vs. Periodic Interval bell chips (`None`, `15s`, `30s`, `1m`, `2m`, `3m`) for linear countdowns.
- **Per-Timer Ambient Soundscape Selection**:
  - Contextual toggle enabling/disabling continuous soundscapes per profile.
  - Source selection between bundled 432Hz Aum loop, sandboxed ad-free YouTube audio stream, or local audio file via Storage Access Framework (SAF).

#### 2. Domain 2: Persistent Global Configuration (`SettingsDrawerTab.GLOBAL`)
- **Zen Focus (Do Not Disturb)**: Suppresses distracting system notifications during active mindfulness sessions.
- **Sun-Moon Circadian Mode with Blue-Light Attenuation**:
  - **Sun (Day Mode)**: Blue-light-reduced warm parchment palette (`#FAF6EE` background, gentle amber `#D97706` accents) engineered to prevent ocular fatigue and daylight glare without harsh cool blue emissions.
  - **Moon (Night Mode)**: Circadian wind-down palette featuring warm amber tones on deep charcoal (`#16130F`) or pure `#000000` AMOLED to power off OLED pixels entirely.
  - 1-tap Sun ☀️ ⇄ Moon 🌙 toggle plus granular theme selection (`AMOLED`, `EYE_COMFORT`, `DARK`, `LIGHT`).
- **Master Audio Gain Controls**: Side-by-side volume sliders for Bell Master Gain and Ambient Background Gain.
- **Pedometer & Health Platform Connectivity**: Centralized selection of active step providers (`Hardware Sensor`, `Health Connect`, `Apple Health Bridge`, `Step Simulator`), sensor permission status indicators, and synthetic step injection tools.
- **Living Room & TV Casting**: Embedded Google Cast route controls (`CastButton`), TV Dashboard mode launcher, and Smart TV browser link copy.
- **Hardware Battery Protections**: Proximity-driven AMOLED Pocket Mode blanking, Auto-Dimming, and Display Awake management.

#### 3. Permanent Signature Acoustic Identity (Zero Timbre Configuration)
- **Brand Sound Integrity**: All user-facing chime timbre selection dropdowns/chips (`Tingsha`, `Singing Bowl`, `Temple Gong`, `Crystal Quartz`) are intentionally removed.
- **Acoustic Enforcement**:
  - **Separator (Interval) Bell**: Exclusively configured to the **Option C Triple Bell** ($2048\text{ Hz} \rightarrow 1536\text{ Hz} \rightarrow 1024\text{ Hz}$) — an acoustically distinct, non-startling mindful pacing cue.
  - **Session Completion**: Exclusively configured to the deep resonant **Temple Gong** ($130.8\text{ Hz}$) — grounding, full-bodied resolution.
- **Dedicated Audition Card**: Provides zero-configuration sample buttons (`[▶ Separator Bell]`, `[▶ End Gong]`, `[⏱ 10s Demo]`) allowing users to familiarize themselves with the separator cue before commencing practice.

---

## 3. Concurrency & Threading Architecture

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
| `BackgroundMusicManager` | Main Thread + Background Decode | `Dispatchers.Main` / Media | Coordinates headless WebView audio rendering, MediaPlayer playback, and audio focus ducking. |

---

## 4. Hardware & Power Management

1. **CPU WakeLock (`BatteryOptimizer.kt`)**: Acquires `PowerManager.PARTIAL_WAKE_LOCK` (`"HabitBell:TimerWakeLock"`) during active countdowns to prevent the OS from suspending the CPU when the screen turns off.
2. **Wi-Fi Multicast Lock**: Acquires `WifiManager.MulticastLock` (`"HabitBellTVMulticast"`) when TV Webcast is active to ensure mDNS / Bonjour packets pass through Android's network power-saving filters.
3. **Proximity Sensor Monitoring**: Monitors device proximity in active sessions to automatically toggle Pocket Mode and the `#000000` AMOLED power curtain.
4. **Automotive Audio Focus**: Requests transient audio focus ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) with `USAGE_MEDIA` to ensure clean audio routing to car audio systems without disrupting navigation directions.
5. **Pedometer & Activity Recognition Management**: Registers hardware step counter sensors with `SENSOR_DELAY_UI` only during active walking timer sessions; unregisters immediately upon pause, stop, or completion to prevent battery drain. Dynamically checks and requests `Manifest.permission.ACTIVITY_RECOGNITION` on Android 10+ (API 29+).

---

## 5. Standards for Parallel Developers & Autonomous AI Agents

To ensure seamless collaboration across parallel developers and autonomous AI coding agents:

1. **Mandatory Documentation Standards**:
   - **KDoc on All Public APIs**: Every class, interface, and function must include full KDoc describing architectural role, concurrency model, `@param` units of measure, `@return` semantics, and `@throws` exceptions.
   - **Inline Explanations**: Non-trivial algorithms, state machine transitions, audio synthesis formulas, and hardware workarounds must include explanatory comments.
2. **Feature Branch Lifecycle & Zero-Lag Sync**:
   - Every feature must be developed on a dedicated feature branch.
   - On completion, open a Pull Request targeting `origin/main` and complete the merge operation.
   - Always verify zero lag with upstream: `git rev-list --left-right --count origin/main...main` returning `0 0`.
3. **Autonomous Architecture Maintenance (`ARCHITECTURE.md`)**:
   - **Rule**: Whenever any architectural change, new platform subsystem, or major feature is introduced, the developer/agent MUST autonomously update `ARCHITECTURE.md` as part of that change.
   - **Goal**: Maintain this document as the living, authoritative blueprint of Habit Bell.
