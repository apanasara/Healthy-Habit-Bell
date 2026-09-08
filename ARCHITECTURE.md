# Habit Bell — System Architecture & Developer Guide

## 1. Architectural Overview & System Topology

Habit Bell is an offline-first, distraction-free wellness operating system engineered for Android, Android Auto, Google Cast, and Android TV / Google TV. The codebase is organized according to **Clean Architecture** principles combined with **MVI / MVVM (Model-View-Intent / Model-View-ViewModel)** with Unidirectional Data Flow (UDF), powered by Kotlin Coroutines and reactive `StateFlow`.

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

#### 1. Living Room & TV Integration Subsystem (Google Cast & Miracast Screen Mirroring)
- **Living Room Strategy**: Living Room TV connectivity is exclusively handled through genuine TV streaming pipelines: **Google Cast** (cloud/LAN media receiver) and **Screen Mirroring (Miracast)**. The confusing on-phone "TV Dashboard Mode" (previously an oversized on-device display) has been completely removed from all navigation and UI surfaces.
- **Universal Single APK**: A single binary deployment targets smartphones, tablets, foldables, automotive head units, and Android TV / Google TV.
- **Sony Bravia Hardware Integration**: All modern Sony Bravia smart TVs run Google TV / Android TV with Chromecast built-in. Habit Bell provides first-class Sony compatibility out of the box via both native APK installation and Google Cast streaming.
- **Manifest Architecture**:
  - Declares `<category android:name="android.intent.category.LEANBACK_LAUNCHER" />` for TV app drawers.
  - Declares `android:banner="@drawable/tv_banner"` for high-resolution 16:9 Android TV launcher cards.
  - Features marked optional (`required="false"`): `android.software.leanback`, `android.hardware.touchscreen`, `android.hardware.microphone`, `android.hardware.telephony`, `android.hardware.camera`.

#### 2. Google Cast Framework (`com.habitbell.app.cast`)
- **Native Cast Integration**: Pure application TV streaming without screen mirroring using Google Play Services Cast Framework (`play-services-cast-framework:22.0.0`).
- **`CastOptionsProvider.kt`**: Registers the official Default Media Receiver application ID (`CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID` / `CC1AD845`).
- **`HabitBellCastManager.kt`**: Singleton session manager coordinating discovery, device connection, and media metadata transmission to Chromecast, Sony Bravia, and Google Cast-enabled TVs.
- **Cast Feedback Loop & 15-Second Reconnect Resolution (FLAW-2)**:
  - Decouples Cast player state from false pause events: In `RemoteMediaClient.Callback`, transient states (`PLAYER_STATE_BUFFERING`, `PLAYER_STATE_LOADING`, `PLAYER_STATE_IDLE`, `PLAYER_STATE_UNKNOWN`) are explicitly ignored. Only genuine user transitions (`PLAYER_STATE_PLAYING` and `PLAYER_STATE_PAUSED`) dispatch to `onRemotePlaybackAction`.
  - Implements `isDispatchingLocally` volatile re-entrancy flags on `loadSession`, `play()`, `pause()`, and `stop()` to eliminate echo feedback loops between mobile commands and Cast listener callbacks.
  - Implements `lastCastProfileId` tracking in `CentralSessionHandler` so resuming from pause calls `castManager.play()` rather than reloading the stream from zero, preventing continuous buffering cycles.
- **Profile-Specific Mindful Artwork & LAN Audio Streaming (FLAW-1)**:
  - Replaced external Pixabay CDN audio URLs with high-fidelity, zero-cloud LAN streaming of `tv/aum.mp3` served directly by `LocalCastWebServer` on port `8888`.
  - Dynamically binds session-specific high-resolution artwork (Sacred Lotus for Pranayama, Golden Dawn for Surya Namaskar, Mindful Eating Bowl, Forest Walk Path) tailored to the active profile.
- **`CastButton.kt`**: Jetpack Compose-native Cast button wrapping AndroidX MediaRouter's `MediaRouteButton` to display discovery states and trigger device selection dialogs.
- **Host Activity Architecture**: `MainActivity` inherits from `androidx.fragment.app.FragmentActivity` to provide the `FragmentManager` required by `MediaRouteButton` to display native Google Cast route picker dialogs across all Android platforms without runtime crashes.
- **`HabitBellChooserDialogFragment` & `HabitBellControllerDialogFragment`**: Public top-level subclasses of `MediaRouteChooserDialogFragment` and `MediaRouteControllerDialogFragment` implementing zero-arg public constructors and theme bundle arguments (`HabitBellMediaRouteTheme_Dark` / `Light`). This strictly complies with Android's `FragmentManager` contract and prevents `IllegalStateException: Fragment ... must be a public static class` crashes upon Cast icon taps.

#### 3. Screen Mirroring Subsystem (Miracast / Wi-Fi Display / Any TV)
- **Universal Living Room Projection (FLAW-3)**: Provides 1-tap integration with Android OS Screen Mirroring (`android.provider.Settings.ACTION_CAST_SETTINGS` with fallback to `ACTION_WIRELESS_SETTINGS`) in `SettingsDrawer.kt`.
- **Zero-Latency Display Fallback**: Bridges non-Chromecast devices (Miracast dongles, projectors, FireTV, Roku, smart monitors) where Google Cast protocol is unavailable.
- **Full Visual Fidelity**: Projects the phone's full Compose canvas directly onto the TV screen, displaying real-time Pranayama breathing animations (blooming lotus, expanding breath ring), live countdowns, and Surya Namaskar posture cards with zero cloud reliance.

#### 4. Local TV WebCast (`LocalCastWebServer.kt` & `assets/tv/index.html`)
- **Zero-Cloud Local Casting**: Embedded lightweight multi-threaded HTTP server running on port `8888`.
- **Network Service Discovery (NSD)**: Registers an mDNS service (`_habitbell._tcp`) allowing any Smart TV browser on the same Wi-Fi network to discover and open the TV dashboard.
- **Enriched Real-Time State Contract (`/api/state`)**: Broadcasts comprehensive routine metadata including:
  - `pranayamaPhase`, `pranayamaDisplay`, `pranayamaSanskrit`, `phaseRemaining`, `phaseDuration`.
  - `poseName`, `poseSanskrit`, `poseBreath`, `poseRemaining` for compound yoga sequences.
  - `currentRound` and `totalRounds`.
- **Interactive TV Visualizer (`assets/tv/index.html`)**: Features an expanding/contracting breath visualizer ring (`.breath-ring.inhale`, `.hold-in`, `.exhale`, `.hold-out`) that morphs color, scale, and opacity in lockstep with the active breath phase, plus live Surya Namaskar asana guidance.
- **Local Media Streaming**: Serves `/media/aum.mp3` with byte-range streaming support directly from application assets.

#### 5. Samsung Smart TV (Tizen OS) & LG Smart TV (webOS) Ecosystem
- **Market Reach**: Samsung Tizen (~21%) and LG webOS (~12%) represent >33% of global connected smart TVs.
- **Packaged Web TV Suite**:
  - `tv-platforms/samsung-tizen/`: Packaged Tizen Web Application container (`.wgt`) with `config.xml` manifest and Samsung TV Remote key handling (`tizen.tvinputdevice.registerKey`).
  - `tv-platforms/lg-webos/`: Packaged LG webOS application (`.ipk`) with `appinfo.json` descriptor and Magic Remote pointer/D-pad mappings.
- **`DialTvDiscoverer.kt` Subsystem**:
  - Dispatches SSDP (Simple Service Discovery Protocol) M-SEARCH UDP multicast probes (`239.255.255.250:1900`) for DIAL services (`urn:dial-multiscreen-org:service:dial:1`) and UPnP `MediaRenderer`.
  - Auto-identifies Samsung and LG TVs on the local Wi-Fi and provides zero-click remote launching of the TV dashboard.

#### 6. Apple TV & AirPlay 2 Subsystem (`com.habitbell.app.cast`)
- **Market Context**: Apple TV (tvOS) dominates the premium streaming box sector. Because tvOS contains no web browser, Habit Bell deploys a dual-track strategy:
- **Track 1 — Direct AirPlay 2 Sender Protocol (`AirPlayCastManager.kt`)**:
  - Scans for nearby Apple TV devices on local Wi-Fi via mDNS / Bonjour (`_airplay._tcp.` and `_raop._tcp.`).
  - Maintains reactive `discoveredDevices: StateFlow<List<AirPlayDevice>>` for casting session metadata and audio to Apple TV hardware.
- **Track 2 — Native Apple TV Companion App (`tv-platforms/apple-tvos/`)**:
  - Native Swift 5.10+ / SwiftUI application built for tvOS 17+.
  - Features circular countdown stroke animation, Siri Remote Clickpad gestures, and real-time Bonjour mDNS discovery (`_http._tcp.`) auto-syncing with `LocalCastWebServer` on the Android device via Server-Sent Events.

---

### 2.5. Android Auto Subsystem (`com.habitbell.app.auto`)
Habit Bell provides deep automotive integration complying with Android for Cars design guidelines (Car App Library v1.7.0, Car API Level 8):
- **`HabitBellCarAppService.kt`**: Top-level `CarAppService` entry point bound by the Android Auto host. Manifest category: `androidx.car.app.category.IOT` (wellness/timer/ambient routines). Uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` during development; production requires `HostValidator.Builder` with explicit allowlist.
- **`HabitBellCarSession.kt`**: Per-connection session lifecycle manager. Handles initial `onCreateScreen()` and reconnection via `onNewIntent()` for transient disconnect recovery. Coordinates `DisplayAutomationManager.setCarConnected()` state across connect/disconnect transitions with diagnostic lifecycle logging.
- **`HabitBellCarScreen.kt`**: Driver-safe `ListTemplate` with 3 glanceable wellness routines (Posture, Breath, Eating). Optimized for the 2-second glance rule with shortened titles, duration indicators, and `ActionStrip` global Stop button during active sessions. State observer throttled to status-change and minute-boundary invalidation only (prevents 1Hz IPC flooding that destabilizes the Android Auto host Binder bridge). All `invalidate()` calls guarded by `Lifecycle.State.STARTED` check with `IllegalStateException` catch for host teardown race conditions.
- **`HabitBellMediaService.kt`**: Extends `MediaBrowserServiceCompat` to provide media library browsability in automotive media drawers. Notification updates throttled to every 5 seconds to eliminate IPC spam.
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
- **Punch-Hole Cutout Safe Geometry (`SessionScreen.kt`)**:
  - Modern smartphones feature centered or offset physical camera punch-holes. When immersive session mode engages (`setStatusBarHidden(true)`), standard system status bar insets collapse to zero.
  - `SessionScreen` enforces `Modifier.displayCutoutPadding()` and implements a **decoupled header architecture**: the top action bar splits navigation (`Back`) to the far-left and controls (`Cast`, `Pocket Mode`, `Settings`) to the far-right, leaving the top-center column completely unobstructed.
  - The session title (e.g. "Mindful Eating") is placed on a secondary centered row beneath the action bar with safe vertical breathing room (`Spacer(14.dp)`), guaranteeing 100% immunity from camera punch-holes across all hardware form factors.
- **Dynamic Theme-Aware Google Cast Surface (`CastButton.kt`, `styles.xml`)**:
  - `MediaRouteButton` relies on underlying Android View AppCompat styling. To ensure high-contrast visibility across varying background luminances, `styles.xml` defines two dedicated themes:
    - `HabitBellMediaRouteTheme.Dark`: Based on `Theme.AppCompat.NoActionBar`, routing `colorControlNormal` to warm white (`#EDE8DE`).
    - `HabitBellMediaRouteTheme.Light`: Based on `Theme.AppCompat.Light.NoActionBar`, routing `colorControlNormal` to dark charcoal (`#2E261F`).
  - `CastButton` dynamically samples `MaterialTheme.colorScheme.background.luminance()` and recomposes within `key(isDark)` to switch themes instantly whenever the user toggles between Day (Sun) and Night (Moon) modes.
- **Icon-Driven Minimal Presentation (Phosphor Line 1.5px)**:
  - Replaces text-heavy UI with minimalist 1.5px line icons to eliminate cognitive reading stress.
  - Mindful Eating sessions incorporate a concentric dual-ring layout: an outer total mealtime ring (45m) and an animated inner bite-pacing arc framing a Phosphor bowl glyph, paired with a subtle chew-and-savor pacing bell indicator.
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

### 2.10. Dual-Domain Settings Architecture & Acoustic Identity (`SettingsDrawer.kt`)

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
  - Immediate contextual **Ambient Volume Slider** ($0\%..100\%$) for instant gain leveling without requiring navigation away from the timer tab.

#### 2. Domain 2: Persistent Global Configuration (`SettingsDrawerTab.GLOBAL`)
- **Zen Focus (Do Not Disturb)**: Suppresses distracting system notifications during active mindfulness sessions.
- **Sun-Moon Circadian Mode with Blue-Light Attenuation**:
  - **Sun (Day Mode)**: Blue-light-reduced warm parchment palette (`#FAF6EE` background, gentle amber `#D97706` accents) engineered to prevent ocular fatigue and daylight glare without harsh cool blue emissions.
  - **Moon (Night Mode)**: Circadian wind-down palette featuring warm amber tones on deep charcoal (`#16130F`) or pure `#000000` AMOLED to power off OLED pixels entirely.
  - 1-tap Sun ☀️ ⇄ Moon 🌙 toggle plus granular theme selection (`AMOLED`, `EYE_COMFORT`, `DARK`, `LIGHT`).
- **Master Audio Gain Controls**: Side-by-side volume sliders for both **Bell Master Gain** and **Background Ambient Gain** ($0\%..100\%$).
- **Pedometer & Health Platform Connectivity**: Centralized selection of active step providers (`Hardware Sensor`, `Health Connect`, `Apple Health Bridge`, `Step Simulator`), sensor permission status indicators, and synthetic step injection tools.
- **Living Room & TV Casting**: Embedded Google Cast route controls (`CastButton`), Miracast Screen Mirroring shortcut, and Smart TV browser link copy.
- **Hardware Battery Protections**: Proximity-driven AMOLED Pocket Mode blanking, Auto-Dimming, and Display Awake management.

#### 3. Permanent Signature Acoustic Identity (Zero Timbre Configuration)
- **Brand Sound Integrity**: All user-facing chime timbre selection dropdowns/chips (`Tingsha`, `Singing Bowl`, `Temple Gong`, `Crystal Quartz`) are intentionally removed.
- **Acoustic Enforcement**:
  - **Separator (Interval) Bell**: Exclusively configured to the **Option C Triple Bell** ($2048\text{ Hz} \rightarrow 1536\text{ Hz} \rightarrow 1024\text{ Hz}$) — an acoustically distinct, non-startling mindful pacing cue.
  - **Session Completion**: Exclusively configured to the deep resonant **Temple Gong** ($130.8\text{ Hz}$) — grounding, full-bodied resolution.
- **Dedicated Audition Card**: Provides zero-configuration sample buttons (`[▶ Separator Bell]`, `[▶ End Gong]`, `[⏱ 10s Demo]`) allowing users to familiarize themselves with the separator cue before commencing practice.

---

### 2.11. Unified Display Automation Subsystem (`DisplayAutomationManager.kt`, `DisplayAutomationOverlay.kt`)

The Unified Display Automation Subsystem orchestrates intelligent screen power state management, peripheral awareness, and touch/lift interactions across 4 distinct contextual environments.

#### 1. Core Architectural Role & Hardware Sensor Fusion
Managed directly by `CentralSessionHandler`, `DisplayAutomationManager` coordinates low-power continuous hardware sensors:
- **Optical Proximity Sensor (`Sensor.TYPE_PROXIMITY`)**: Detects physical obstruction within $< 5\text{ cm}$ of the front bezel receiver.
- **Ambient Light Sensor (`Sensor.TYPE_LIGHT`)**: Measures surrounding illuminance in lux ($\text{lx}$). Pocket classification requires $< 10.0\text{ lux}$ to prevent false-positives under bright external illumination.
- **3-Axis Gravity Sensor (`Sensor.TYPE_GRAVITY` / `TYPE_ACCELEROMETER`)**: Isolates Earth's gravitational acceleration vector ($9.81\text{ m/s}^2$).
  - **Flat Surface Detection**: When resting flat face-up on a tabletop, $z \ge 8.8\text{ m/s}^2$, $|x| < 3.0\text{ m/s}^2$, and $|y| < 3.0\text{ m/s}^2$.
  - **Lift & Tilt Detection**: When tilted toward the user, $z < 7.5\text{ m/s}^2$ and $|y| > 3.5\text{ m/s}^2$, or acceleration vector jerk delta $\Delta a = |\vec{a}_{t} - \vec{a}_{t-1}| > 1.2\text{ m/s}^2$.
- **Significant Motion Hardware Trigger (`Sensor.TYPE_SIGNIFICANT_MOTION`)**: Low-power hardware interrupt that fires instantly upon physical pickup without CPU polling.

#### 2. AMOLED Zero-Power Blackout Curtain (Option A Implementation)
- **Power Optimization**: Rendered at the root window hierarchy in `MainActivity` via `DisplayAutomationOverlay`. On OLED/AMOLED panels, pure `#000000` pixels are completely de-energized ($0\text{ mW}$ emission penalty).
- **Frictionless Zero-Latency Wake**: Unlike standard Android keyguard screen locks (`FLAG_DISMISS_KEYGUARD`, system power manager locks), the blackout curtain avoids lockscreen friction, pin codes, and biometric fingerprint hurdles. A single tap anywhere on the screen or physical phone lift instantly lifts the curtain.
- **Haptic & Visual Badging**: Discreet contextual badges (Golden Lock, Car HUD, Smart TV, or Smart Watch icon) rendered with low-luminance accents to communicate active mode without disrupting nighttime dark adaptation.

#### 3. Contextual Environmental Priority Hierarchy
1. **Pocket Mode (Highest Priority)**:
   - Evaluated via `evaluatePocketMode()`: activated either by manual user toggle (`isPocketModeManual == true`) or automatic optical sensor fusion (proximity obstructed $< 5\text{ cm}$ AND ambient lux $< 10\text{ lx}$).
   - Automatically silences visual distractions and blocks accidental screen touches in pockets/bags while delivering tactile haptic chimes.
2. **Car Mode (Automotive HUD)**:
   - Engaged automatically when connected to Android Auto (`HabitBellCarSession`) or vehicle CarPlay.
   - Mobile screen defaults to `#000000` blackout curtain while vehicle in-dash head unit displays timer templates and media scrubbers.
   - Users can tap the screen or lift the device to access mobile settings and timer controls without interrupting car audio.
3. **Smart TV Cast Mode (Chromecast, Apple TV / AirPlay 2, and WebCast)**:
   - Automatically activates when streaming to living room televisions via Google Cast or Apple TV tvOS.
   - Mobile screen blacks out to conserve handset battery while the big screen displays high-visibility session rings and progress sweeps.
4. **Smart Watch Mode (Wear OS)**:
   - Engaged during paired smartwatch sessions, allowing wrist transport controls while the phone display rests powered off.

#### 4. Flat Inactivity Timeout (10-Second Grace Period)
- When the phone is resting flat on a surface during external display sessions (Car, TV, Watch) and the user taps the screen to adjust settings, a background coroutine timer begins a **10-second countdown** (`_inactivityCountdown: 10..1`).
- If no further touch interaction occurs for 10 seconds while the phone remains flat, the AMOLED blackout curtain smoothly re-engages.
- Physically lifting or tilting the phone immediately cancels the countdown and keeps the display awake until placed down flat.

#### 5. Lift-to-Wake State Machine & Pocket Mode Dismissal
- **Zero-Friction Physical Lift**: When the phone is lifted from a flat surface or taken out of a pocket/bag, `onDeviceMovedOrLifted()` is triggered via 3-axis gravity vector analysis ($z < 7.5\text{ m/s}^2, |y| > 3.5\text{ m/s}^2$) or acceleration jerk delta ($\Delta a > 1.2\text{ m/s}^2$).
- **Multi-Mode Curtain Clearance**: In `DisplayAutomationManager.combine(_isPickedUp)`, all active curtain modes—including `DisplayCurtainMode.POCKET`, `TV_CAST`, `WATCH`, and `CAR_HUD`—are immediately deactivated (`isActive = false`), restoring full mobile visibility without requiring unlock or pin gestures.
- **Manual Override Clearing**: Physical movement or lift automatically resets `_isManualPocket = false`, preventing persistent blackouts.
- **Temporary Wake Grace Period**: User touch or lift sets `_isTemporarilyAwake = true`, ensuring that `evaluatePocketMode()` does not re-blank the display even if optical sensors remain transiently shaded.

---

### 2.6. Central Theme Architecture & Dialog Stability

#### 1. Unified Central Theme Engine
Habit Bell enforces a consistent, centralized visual theme hierarchy governed exclusively by `HabitBellViewModel.selectedTheme`:
- **Single Source of Truth**: The user's active theme selection (`ThemeMode.AMOLED`, `ThemeMode.DARK`, `ThemeMode.EYE_COMFORT`, `ThemeMode.LIGHT`) governs the entire application container (`HabitBellTheme`).
- **Profile Decoupling**: Individual wellness timer profiles (`TimerProfile`) define timing parameters, pacing bells, and sensor triggers, but do NOT override the user's central theme preference when starting a session.
- **MaterialTheme Dynamic Binding**: All UI surfaces (`HomeScreen`, `SessionScreen`, `ModernHomeScreenSample`, `SettingsDrawer`) dynamically bind container, card, border, and typography colors to `MaterialTheme.colorScheme` tokens, guaranteeing flawless contrast across dark and light palettes.
- **Symmetrical Sun ☀️ / Moon 🌙 Toggle**: Top action bars on both Home and Session screens feature a high-legibility theme action:
  - Renders `ic_ph_sun` in dark modes to switch to Light (Day / Warm Parchment).
  - Renders `ic_ph_moon` in Light mode to switch to Dark (AMOLED / Pure Black).

#### 2. Google Cast MediaRoute Dialog Factory & Background Stability
Native `MediaRouteButton` interactions in Jetpack Compose require strict background opacity to comply with AndroidX `MediaRouterThemeHelper` contrast calculations:
- **Crash Prevention**: Inheriting translucent window backgrounds causes `androidx.core.graphics.ColorUtils.calculateContrast` to throw `IllegalArgumentException: background can not be translucent: #0`.
- **HabitBellMediaRouteDialogFactory**: Wraps `MediaRouteChooserDialog` and `MediaRouteControllerDialog` instantiation within an explicit, non-translucent `ContextThemeWrapper` applying `R.style.HabitBellMediaRouteTheme_Dark` or `R.style.HabitBellMediaRouteTheme_Light`.
---

### 2.12. Classical Hatha Yoga Pranayama Subsystem (`com.habitbell.app.engine`, `com.habitbell.app.ui.components.BreathIndicator`)

The Pranayama subsystem implements classical yogic breath control (*Chaturanga Pranayama*) as documented in traditional Hatha Yoga literature (*Hatha Yoga Pradipika* by Swami Svatmarama, *Gheranda Samhita*, and *Patanjali Yoga Sutras*).

#### 1. Classical Literature & Respiratory Physiology
In *Hatha Yoga Pradipika* (HYP 2.2), Svatmarama establishes the inseparable link between breath and consciousness:
> *"When breath is still, the mind is still; the yogi achieves firmness, therefore one should restrain the breath."*

The practice regulates the four sacred limbs of the breath cycle:
1. **Puraka (पूरक - Inhalation)**: Conscious diaphragmatic intake drawing cosmic life force (*Prana*) into the torso.
2. **Antar Kumbhaka (अभ्यन्तर कुम्भक - Internal Retention)**: Preserving breath in full lungs, awakening the *Sushumna Nadi*, building internal pressure, and maximizing cellular oxygen diffusion. In accordance with classical Hatha Yoga (*HYP* 3.55-3.56), internal retention is practiced with **Tri-Bandha (त्रिबन्ध)**:
   - **Mūla Bandha (मूलबन्ध - Root Lock)**: Perineal/pelvic floor contraction stimulating the parasympathetic pelvic splanchnic nerves and redirecting *Apana Vayu* upward into *Sushumna*.
   - **Madhyama Uḍḍīyāna Bandha (उड्डीयान बन्ध - Abdominal Lock)**: In *Antar Kumbhaka*, gentle inward engagement of the lower abdominal wall below the navel (*Madhyama/Laghu Uḍḍīyāna*) stabilizes intra-abdominal pressure against the descending diaphragm without compressing fully inflated lungs.
   - **Kūpa Bandha (कूपबन्ध / जालंधर बन्ध - Throat Lock)**: Resting the chin firmly into the jugular notch (*Kaṇṭha Kūpa*, *PYS* 3.30: *kaṇṭhakūpe kṣutpipāsānivṛttiḥ*). This mechanically stimulates the carotid sinus baroreceptors, triggering the reflex vagal bradycardia that lowers heart rate, regulates intracranial arterial pressure during retention, and halts mental fluctuation.
3. **Rechaka (रेचक - Exhalation)**: Slow, prolonged exhalation expelling *Apana*, physical toxins, and mental tension.
4. **Bahya Kumbhaka (बाह्य कुम्भक - External Retention / Shunya Void)**: Resting in primordial emptiness between breaths, stimulating hypercapnic adaptation (CO₂ tolerance) and cerebral vasodilation (Bohr effect).

#### 2. Proportional Ratio Stages & Visama Vritti Dynamics
When Bahya Kumbhaka (external void) is included, practitioners advance through classical proportional stages selected via the **Proportional Ratio Stages Dropdown**:
1. **Sama Vritti (Equalized / Box)**: `1 : 1 : 1 : 1` (e.g. 4s : 4s : 4s : 4s) — Balances the nervous system and develops breath discipline.
2. **Madhya (Intermediate Stage)**: `1 : 2 : 2 : 1` (e.g. 4s : 8s : 8s : 4s) — Introduces retention with mild external void.
3. **Visama Vritti (Classical Advanced)**: `1 : 4 : 2 : 4` (e.g. 4s : 16s : 8s : 16s) [Default] — Deep Hatha Yoga standard activating prana sublimation and hypercapnic adaptation.
4. **Visama Vritti (Gentle Void)**: `1 : 4 : 2 : 1` (e.g. 4s : 16s : 8s : 4s) — Full internal retention with gentle void entry.
5. **Visama Vritti (Half Void)**: `1 : 4 : 2 : 2` (e.g. 4s : 16s : 8s : 8s) — Intermediate external void challenge.
6. **Custom User Ratios**: Manual seconds entry per phase portion.

- **Base Inhale Scaling**: Quick scalar multipliers (2s, 3s, 4s, 5s, 6s) instantly recalculate all four phase seconds according to the selected ratio.
- **Four Phase Setting Portions (Direct Input Fields)**:
  - 1. **Purak (Inhale)**: 4 seconds default (`min: 1s, max: 60s`)
  - 2. **Kumbhak (Hold In)**: 16 seconds default (`min: 0s, max: 60s`)
  - 3. **Rechak (Exhale)**: 8 seconds default (`min: 1s, max: 60s`)
  - 4. **Kumbhak (Hold Out)**: 16 seconds default (`min: 0s, max: 60s`)
  - Features direct numerical `OutlinedTextField` editing with fine `-1s`, `+1s`, and `+4s` adjustment buttons.

#### 3. Target Practice Rounds & Classical Yogic Stages
Grounded in *Hatha Yoga Pradipika* (2.12) & *Gheranda Samhita* (5.49):
- **12 Rounds (Adhama / Foundation)**: Default setting (~8 minutes 48 seconds at 4:16:8:16). Establishes foundational nadi cleansing and respiratory stability.
- **24 Rounds (Madhyama / Intermediate)**: Deepens metabolic down-regulation and prana circulation.
- **36 Rounds (Uttama / Advanced)**: Awaking Sushumna nadi and contemplative stillness.
- **Custom Steppers & Quick Chips**: Introduces 6, 12, 18, 24, 36 round presets with `-1`, `+1`, `+6` fine steppers (1 to 108 rounds).

#### 4. Gentle Lady Voice Guidance Engine (`PranayamaVoiceGuide.kt`)
- **System Integration**: Wraps Android's native offline `TextToSpeech` engine, guaranteeing 100% offline reliability without network latency or APK bloat.
- **Gentle Female Voice Profile**: Scans system TTS voices for female attributes with `Locale("en", "IN")` or `Locale.US`, setting a slow, mindful speech rate (`0.85f`) and warm pitch (`0.95f`).
- **Voice Cue Styles**:
  - **Option A (Traditional Sanskrit) [Default]**: Whispers authentic cues (`"Purak"`, `"Kumbhak"`, `"Rechak"`, `"Kumbhak"`).
  - **Option B (Bilingual Guided)**: Combines Sanskrit roots with English instructions (`"Purak... Inhale"`, `"Kumbhak... Hold"`, `"Rechak... Exhale"`, `"Kumbhak... Hold empty"`).
  - Switchable in the dedicated Pranayama Settings Sheet with a live audition button.
- **Dynamic Background Audio Ducking**:
  - Synchronously commands `BackgroundMusicManager.duckVolume(0.20f)` to smoothly attenuate ambient meditation drones down to ~15%–20% gain during speech.
  - Automatically restores normal volume upon `UtteranceProgressListener.onDone` or error.

#### 5. Meditative Milestone & Session Ending Bells
- **Milestone Interval Bell (Default: OFF)**:
  - Preserves deep *Dhyana* meditative absorption where absolute silence between rounds is vital.
  - Practitioner can toggle **ON** in settings with configurable cadence (Every 3, 5, 6, 10 rounds; default cadence: 5 rounds).
  - **Acoustic Design**: Calibrated to a gentle 432 Hz warm Tibetan singing bowl (`R.raw.tibetan_bell_interval` at soft 0.38f volume) with gradual mallet attack curve, engineered specifically to preserve meditative absorption without triggering the sympathetic startle reflex.
- **Session Completion Bell**: Deep resonant **Temple Gong** (`130.8 Hz`) strikes gracefully upon completing all rounds.
- **Pocket Mode Safeguard**: In Pocket Mode, audible chimes and voice guidance are replaced with distinct multi-pulse tactile haptic vibrations.

#### 6. Classical Side-View Blooming Lotus, Dynamic Prana Aura & Landscape Layout (`BreathIndicator.kt`, `SessionScreen.kt`)
- **Sacred Side-View Lotus Architecture**: Renders 13 organic curved petals structured across 7 distinct depth tiers (outermost horizontal wings -> lateral wings -> chalice petals -> central erect spine) drawn using smooth two-semicircular cubic Bezier curves.
- **C2-Continuous Kinematics (Zero-Flicker Transitions)**:
  - **Pūraka (Inhale)**: Petals gracefully unfurl outward into full bloom from waterline with smooth cubic lift (`bloom: 0.0f -> 1.0f`).
  - **Antar Kumbhaka (Hold In)**: Fully open flower hovers soothingly with continuous living aquatic floating and subtle sinusoidal lateral sway with C2 boundary velocity matching ($v=0$).
  - **Recaka (Exhale)**: Petals fold gently inward towards center as the flower descends smoothly to the waterline, closing into a serene resting bud (`bloom: 1.0f -> 0.0f`).
  - **Bāhya Kumbhaka (Hold Out / Void)**: Closed bud rests tranquilly at the waterline in Shunya stillness with subtle bobbing.
  - Guarantees seamless, zero-flicker cyclic transitions across all four phases ($1 \rightarrow 2 \rightarrow 3 \rightarrow 4 \rightarrow 1$).
- **Theme-Harmonized Calyx Leaves ("Patte") & Stem**:
  - Dynamically adapts the 3 calyx leaves, vertical stem, and central receptacle seed to the active theme palette:
    - **AMOLED Dark Mode**: Luminous chartreuse/emerald green (`#A3E635` / `#84CC16`) with an ethereal glow.
    - **Warm Parchment Mode**: Natural earthy sage/olive green (`#84CC16` / `#65A30D`) harmonized with warm linen tones.
- **Unified Single-Color Theme Lotus Palette**:
  - Eliminates phase-dependent color switching in favor of a serene, cohesive single theme color bound directly to `MaterialTheme.colorScheme.primary`:
    - **Sun Day / Light Mode**: Warm Amber (`SunDayAmber` `#D97706`).
    - **AMOLED / Dark Mode**: Bell Gold (`BellGold` `#D4AF37`) / Theme Primary.
    - **Eye Comfort Mode**: Warm Amber (`EyeComfortAmber` `#E29D47`).
- **Dark Mode Petal Outline Elimination**:
  - In dark mode, petal stroke outlines are completely removed (`strokeColor = Color.Transparent`), allowing the luminous semi-transparent layered petals to blend organically against AMOLED pure black.
  - In light mode, subtle tone-on-tone contours (`primaryPranaColor` with adaptive alpha) preserve delicate petal definition against warm parchment backgrounds.
- **Screen-Width Responsive Geometry & Zero Numeral/Text Overlap**:
  - Visualizer scales responsively to fill the full screen width (`fillMaxWidth()`) in portrait mode, with maximum petal length calibrated against canvas dimensions (`minOf(canvasW * 0.48f, canvasH * 0.42f)`).
  - Canvas geometry anchors the waterline at `0.77 * canvasHeight`, preserving ample clear space above the bloom for elevated Sanskrit nomenclature (`PŪRAKA` / `पूरक`), Devanagari script, and countdown numerals without petal overlap.
- **Lean Interface Architecture**:
  - Eliminates redundant multi-capsule rhythm bars to provide an ultra-clean, distraction-free breathwork environment.
  - Portrait mode arranges: Action Bar $\rightarrow$ Activity Title $\rightarrow$ Screen-Width Side-View Lotus $\rightarrow$ Round Milestone Badge $\rightarrow$ Transport Controls.
- **Responsive 2-Column Landscape Layout (`LandscapeSessionLayout`)**:
  - **Left Column**: Dedicated pure side-view lotus visualizer floating tranquilly over waterline ripples and breathing radial prana aura without text clutter.
  - **Right Column**: Integrated action bar with Phosphor pill buttons, elevated Sanskrit HUD, large countdown numeral, round counter, and unified Phosphor transport controls.
  - Mindful Eating in landscape similarly leverages a 2-column layout rendering the mealtime bowl with active bite-cycle arc on the left and meal countdown + bite capsule on the right.

#### 7. Dedicated Pranayama Settings Architecture (`SettingsDrawer.kt`)
- When `profile.pranayamaConfig != null`, `SettingsDrawer` completely isolates the configuration surface into `PranayamaSettingsSheet`:
  1. **Ratio Stages Dropdown**: Sama Vritti (1:1:1:1), Madhya (1:2:2:1), Visama Vritti (1:4:2:4), Gentle Void (1:4:2:1), Half Void (1:4:2:2), Custom.
  2. **Base Inhale Scaling**: Quick 2s, 3s, 4s, 5s, 6s proportional recalculation chips.
  3. **Four Phase Input Fields**: Direct numerical text entry and -1s, +1s, +4s steppers for Purak, Kumbhak (In), Rechak, Kumbhak (Out).
  4. **Target Practice Rounds**: 12 rounds default (*Adhama* standard), with custom steppers and classic stage presets.
  5. **Gentle Lady Voice Guide**: Option A (Sanskrit) default vs Option B (Bilingual) switch, with live audition.
  6. **Meditative Interval Bell**: Default OFF toggle, cadence selector, and 432 Hz audition button.
  7. **Subtle Background Music**: Ambient sound toggle, Aum drone / YouTube / Custom file, and subtle volume slider.
- Bypasses generic timer countdown and signature 3-bell cards, maintaining a serene, focused user experience.

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
| `DisplayAutomationManager` | `CoroutineScope(SupervisorJob())` + Sensor Thread | `Dispatchers.Default` | Processes multi-sensor fusion (proximity, lux, gravity, significant motion), orchestrates 10s flat countdowns, and emits atomic `DisplayCurtainState`. |
| `BackgroundMusicManager` | Main Thread + Background Decode | `Dispatchers.Main` / Media | Coordinates headless WebView audio rendering, MediaPlayer playback, and audio focus ducking. |
| `PranayamaVoiceGuide` | System TTS Engine Callback Thread | `Dispatchers.Main` / AudioTrack | Coordinates offline Android TextToSpeech synthesis, gentle female voice selection, and dynamic background music ducking. |

---

## 4. Hardware & Power Management

1. **CPU WakeLock (`BatteryOptimizer.kt`)**: Acquires `PowerManager.PARTIAL_WAKE_LOCK` (`"HabitBell:TimerWakeLock"`) during active countdowns to prevent the OS from suspending the CPU when the screen turns off.
2. **Wi-Fi Multicast Lock**: Acquires `WifiManager.MulticastLock` (`"HabitBellTVMulticast"`) when TV Webcast is active to ensure mDNS / Bonjour packets pass through Android's network power-saving filters.
3. **Proximity Sensor Monitoring**: Monitors device proximity in active sessions to automatically toggle Pocket Mode and the `#000000` AMOLED power curtain.
4. **Automotive Audio Focus**: Requests transient audio focus ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) with `USAGE_MEDIA` to ensure clean audio routing to car audio systems without disrupting navigation directions.
5. **Pedometer & Activity Recognition Management**: Registers hardware step counter sensors with `SENSOR_DELAY_UI` only during active walking timer sessions; unregisters immediately upon pause, stop, or completion to prevent battery drain. Dynamically checks and requests `Manifest.permission.ACTIVITY_RECOGNITION` on Android 10+ (API 29+).
6. **Multi-Sensor Display Automation**: Powers off OLED pixels using `#000000` blackout curtain across Pocket Mode, Android Auto Car HUD, Smart TV casting, and Wear OS companion states. Employs hardware `TYPE_SIGNIFICANT_MOTION` trigger and Z-axis gravity vector analysis for battery-efficient, zero-latency Lift-to-Wake and 10-second flat inactivity timeout.

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
