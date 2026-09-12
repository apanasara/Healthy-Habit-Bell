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
The `CentralSessionHandler` is the **process-level single source of truth** and authoritative orchestrator across the entire application runtime. It initializes and synchronizes the central `MediaSessionCompat` (`"HabitBellMediaSession"`), `TimerEngine`, `AudioBellManager`, `BackgroundMusicManager`, `BluetoothAudioDisconnectionManager`, `BatteryOptimizer`, and `HabitBellCastManager`.

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
  - Linear 0%..100% dynamic volume scaling (`coerceIn(0, 100)`), eliminating arbitrary lower-bound attenuation clamps.
- **Raised-Cosine S-Curve Crossfader & Dynamic Gain Adaptation**:
  - `smoothFadeTo(targetGain, durationMs)`: Smoothly interpolates volume transitions via raised-cosine S-curve easing: `0.5 * (1 - cos(π * progress))`.
  - `duckVolume(0.20f, 350L)`: Temporarily and smoothly lowers ambient background audio during voice guidance cues to ensure crystalline vocal clarity.
  - `restoreVolume(500L)`: Elegantly restores ambient music back to configured gain without jarring steps or pops.
  - **Immediate Manual Override**: Slider adjustments cancel active fade animators to provide zero-latency, real-time auditory feedback. When adjusting volume while ducked, attenuated gain is recalculated proportionally from the new base volume without dropping the ducking state.
  - **State Sanitization on Pause/Stop**: Pausing or stopping playback immediately resets `isDucked = false` and terminates pending fade animations, preventing stale ducked gain when sessions resume.
  - **Post-Start Gain Enforcement**: Calls `mediaPlayer.setVolume()` both before and immediately after `start()` to overcome asynchronous Android media server gain resets.

#### 3. Melodious Anti-Startle Voice Guidance Subsystem (`PranayamaVoiceGuide.kt`)
- **Acoustic Design Rationale**:
  - Sudden, loud vocal instructions during deep breath retention (*Kumbhaka*) or contemplation trigger an abrupt sympathetic nervous startle response, shattering meditative absorption (*dhyana*).
  - Habit Bell eliminates jarring transitions through an unhurried, sweet, soft, high-frequency female voice profile matching the gentle, revered tonal swara of Bollywood singing legend **Lata Mangeshkar**.
- **Voice Profile Standard & Mastered Assets**:
  - **Acoustic Profile**: Natural high-frequency swara (`pitch: +52Hz`), meditative cadence (`rate: -45%` / `0.55x`), clean yogic pronunciation, and whisper-soft default gain (`0.52f`).
  - **Option 1 (Only Sanskrit)**: Traditional Sanskrit sacred cues (`R.raw.pranayama_purak_sanskrit`, `R.raw.pranayama_kumbhak_sanskrit`, `R.raw.pranayama_rechak_sanskrit`). Both internal (*Antar*) and external (*Bahya*) retention phases share the melodious *Kumbhak* cue per authentic yogic practice.
  - **Option 2 (Sanskrit + English)**: Bilingual cues (`R.raw.pranayama_purak_bilingual`, `R.raw.pranayama_kumbhak_bilingual`, `R.raw.pranayama_rechak_bilingual`). Spoken with unhurried, deeply soothing, sweet Lata-style cadence (~5.6s–5.9s duration).
  - **Step Timing vs. Voice Timing Law (< 6s Accommodation Guard)**: In `PranayamaVoiceGuide.kt`, when a phase's configured duration cannot fully accommodate the unhurried bilingual cue (< 6 seconds, e.g. a 4s or 2s/3s Purak step), Option 2 automatically and seamlessly falls back to the clean, melodious single-language Sanskrit cue (`R.raw.pranayama_purak_sanskrit`, ~2.2s). Steps with duration $\ge 6$s (e.g. 8s or 16s Kumbhak/Rechak) enjoy the full unhurried bilingual guidance, guaranteeing that no cue is ever rushed or clipped mid-word.
  - **Anti-Startle Lead Delay**: Inserts a 120ms gentle delay after ducking begins before audio playback, letting ambient music settle before the voice begins.
  - **Cross-Subsystem Reuse Guarantee & Surya Namaskar Tooling**:
    - This voice profile specification (`hi-IN-SwaraNeural`, `+52Hz`, gentle soothing swara) is established as the project-wide architectural standard.
    - **Reusable Generator Tool (`scripts/generate_surya_namaskar_voice.py`)**: A dedicated automated Python studio script is bundled in the repository. Any parallel developer or autonomous agent can run:
      ```bash
      python3 scripts/generate_surya_namaskar_voice.py
      ```
      This will autonomously synthesize all 24 Surya Namaskar audio assets (12 Asanas with sacred solar mantras `ॐ मित्राय नमः...` + 12 bilingual flow cues) directly into `app/src/main/res/raw/` matching the exact tonal swara and acoustic characteristics of the Pranayama voice cues.

#### 4. Peripheral & Bluetooth Disconnection Auto-Pause Subsystem (`BluetoothAudioDisconnectionManager.kt`)
- **Industry Media Player Parity**: Replicates standard Android media playback conventions (e.g. Spotify, YouTube Music, Audible) where external audio output disconnection immediately pauses playback, protecting users against sudden acoustic exposure through the mobile device's speaker.
- **Multi-Vector Disconnect Detection**:
  - **`AudioDeviceCallback` (API 26+)**: Registered on `AudioManager` to intercept endpoint hardware removals for `TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`, `TYPE_BLE_BROADCAST`, `TYPE_HEARING_AID`, and wired/USB headsets. Delivers unconditional hardware disconnect detection even when no sound is currently playing during a silent rest interval.
  - **`ACTION_AUDIO_BECOMING_NOISY` BroadcastReceiver**: Registered dynamically during `SessionStatus.RUNNING` with `ContextCompat.RECEIVER_EXPORTED` on Android 13+ to catch instantaneous system audio route flips from external peripherals to phone speakers.
  - **Automotive Host Lifecycle**: Bound to `HabitBellCarSession.onDestroy()` via `onCarDisconnected()` to guarantee running in-car sessions safely pause when leaving the vehicle.
- **Monotonic Debounce Guard**: Employs a 1000ms (`DEBOUNCE_THRESHOLD_MS`) gate via `SystemClock.elapsedRealtime()` to cleanly throttle near-simultaneous callback and broadcast dispatches.
- **Lifecycle & Power Optimization**: Active hardware and broadcast listeners are registered strictly during `SessionStatus.RUNNING` and unregistered upon pause, completion, or idle to ensure zero background battery drain.
- **Zero-Permission Privacy & Settings Integration**: Operates without requiring dangerous `BLUETOOTH_CONNECT` runtime permissions. Configurable via **Settings Drawer > Global Config** (`isPauseOnBluetoothDisconnect`, default `true`).

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
- **`CastOptionsProvider.kt`**: Configures Google Cast framework options, bound to the registered Habit Bell Custom Web Receiver Application ID (`4662865D`) targeting `https://apanasara.github.io/Healthy-Habit-Bell/`, with automatic fallback to Google's Default Media Receiver (`CC1AD845`).
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

#### 3. Screen Mirroring Subsystem (Miracast / Wi-Fi Display / Any TV / Chromecast)
- **Universal Living Room Projection (FLAW-3)**: Provides 1-tap integration with Android OS Screen Mirroring (`android.provider.Settings.ACTION_CAST_SETTINGS` with fallback to `ACTION_WIRELESS_SETTINGS`) in `SettingsDrawer.kt`.
- **Zero-Internet Local Operation**: Unlike Google Cast SDK which mandates an active internet connection to download cloud receiver shells from Google servers, Screen Mirroring operates 100% peer-to-peer over local Wi-Fi, making it the bulletproof market standard for offline classes, studios, and living rooms without internet access.
- **Full Visual Fidelity**: Projects the phone's full Compose canvas directly onto the TV screen, displaying real-time Pranayama breathing animations (blooming lotus, expanding breath ring), live countdowns, and Surya Namaskar posture cards with zero cloud reliance.
- **Hardware Backlight Decoupling & Battery Conservation (Requirement C.1)**:
  - During active countdowns across all profile types (Linear, Pranayama, Surya Namaskar, Compound), the app sets `WindowManager.LayoutParams.screenBrightness = 0.01f` (1% minimal hardware backlight).
  - Crucially, setting `screenBrightness` modulates only the phone's physical display panel LED/OLED driver; it does **not** alter the GPU rendering buffer (`SurfaceFlinger`).
  - As a result, the mirrored TV screen receives uncompromised pixel color and luminance, remaining at **100% full, vivid brightness**, while the smartphone draws minimal battery current and remains cool to the touch.
- **Unconditional Screen Awake Lock (Prevents 3–4 Min "Connection Lost")**:
  - `MainActivity` dynamically binds `FLAG_KEEP_SCREEN_ON` for the entire duration of `SessionStatus.RUNNING`.
  - This prevents Android OS display sleep from turning the screen off and terminating the real-time H.264 screen capture encoder (`MediaProjection` / Cast Mirroring pipeline).
- **Low-Latency Wi-Fi Lock (Prevents 26-Min Doze Disconnect)**:
  - `CentralSessionHandler` acquires `WIFI_MODE_FULL_LOW_LATENCY` (`WifiLock`) via `BatteryOptimizer` on session start.
  - This prevents the Wi-Fi radio from entering DTIM sleep during prolonged stationary sessions, eliminating packet loss and stream stalling beyond 25–30 minutes.
- **Interactive Touch Grace Period**:
  - Any tap on the Compose root window triggers `HabitBellViewModel.onUserTouchDisplay()`, which dispatches `TimerEngine.wakeScreenTemporarily(6)`.
  - The phone screen physically brightens for 6 seconds for effortless user interaction, then automatically re-dims to 1% while the TV stays continuously illuminated.

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

#### 7. Google Cast Custom Web Receiver & Bidirectional Telemetry Protocol (`docs/index.html`, `tv-platforms/google-cast-receiver/`, & `app/src/main/assets/tv/`)
- **Architectural Role & TV Sandboxing**: Solves the browserless TV and phone distraction challenges by executing a dedicated, cloud-hosted Custom Web Receiver directly within the Google Cast Application Framework (CAF v3) hardware sandbox on Chromecasts, Google TVs, and Sony Bravia displays. Allows the user's phone to dim or sleep while the TV independently renders the meditation canvas.
- **Identical Triplicate Target Synchrony**: The receiver codebase is mirrored across three 100% identical targets kept in bit-level synchrony:
  1. `tv-platforms/google-cast-receiver/index.html`: Authoritative reference source.
  2. `docs/index.html`: GitHub Pages production endpoint serving Cast Application ID `4662865D` (`https://apanasara.github.io/Healthy-Habit-Bell/`).
  3. `app/src/main/assets/tv/index.html`: Embedded offline web server asset bundled inside the Android APK on port 8888 for zero-internet LAN casting.
- **CAF v3 Compliance & Media Player Architecture**:
  - Embedded `<cast-media-player style="display:none;"></cast-media-player>` enables CAF v3 `PlayerManager` to bind cleanly, preventing session initialization crashes when the Android sender attaches `RemoteMediaClient` and `CastMediaOptions`.
  - Custom namespace declaration: Explicitly pre-registers `options.customNamespaces = { ['urn:x-cast:com.habitbell.cast']: cast.framework.system.MessageType.JSON }` prior to `context.start(options)`.
  - Safe payload deserialization: Handles both pre-parsed JSON objects and raw string transmissions via `const msg = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;`, eliminating `SyntaxError: Unexpected token o in JSON at position 1` crashes that previously dropped sender state packets.
- **Bit-Identical Mobile Timer UI Harmonization (`SessionScreen.kt` Parity)**:
  The receiver UI is engineered to match the Android mobile timer experience (`SessionScreen.kt`) bit-identically across visual structure, typography, component layout, and dynamic modes:
  1. **Top Action Bar**:
     - Back button (`ic_ph_back`) with circular frosted surface (`rgba(255,255,255,0.06)`).
     - Live casting status indicator pill: Cast icon (`ic_ph_tv`) with pulsing emerald beacon and `"CASTING TO TV"` tracking badge.
     - Centered session header displaying profile title and subtitle.
     - Sound / Tibetan Bowl indicator pill (`ic_ph_bowl`) indicating active acoustic bell frequency.
  2. **Four Dynamic Timer Screen Topologies**:
     - **SPLASH / Standby Screen**: Ambient golden prana breathing glow, sacred Habit Bell crest, dual-state connection status (amber pulsing standby transitioning to emerald connected), and queued routine preview card with profile metadata.
     - **LINEAR / General Timer Mode (`screenMode == 'GENERAL'`)**:
       - Primary SVG circular progress ring (radius 180, circumference 1131px) with gradient stroke and animated glowing progress head dot tracking exact completion percentage.
       - Ultra-crisp, extra-large timer countdown typography (`MM:SS` or `HH:MM:SS`) with elapsed/total subtext (`02:15 elapsed • 15:00 total`).
       - Health & step-tracking telemetry pill (`👟 1,420 steps • 112 spm • Bell in 580 steps`) dynamically rendered when mindful walking/step tracking is engaged.
       - Session bell chime indicator (`🔔 Tibetan Bell every 5m`).
     - **MINDFUL EATING Mode (`screenMode == 'EATING'`)**:
       - Warm candlelight amber color palette (`#E5A93C`, `#D97706`).
       - **Concentric Dual SVG Rings**:
         - Outer meal ring (radius 180, circumference 1131px): Tracks total meal duration countdown.
         - Inner bite pacing ring (radius 140, circumference 880px): Tracks active bite chewing interval with pulsing amber stroke.
       - Centered Zen dining bowl and chopsticks vector icon (`ic_ph_bowl`).
       - Bite pacing pill: `"🔔 Bite Bell in 00:42 • CHEW & SAVOR"`.
       - Mindful eating guidelines carousel rotating evidence-based eating habits every 12 seconds ("Chew each bite 30–40 times", "Rest fork between bites", "Tune in to satiety cues").
       - Radial chime ripple wave expanding outwards from the center bowl upon interval bell completion.
     - **PRANAYAMA Mode (`screenMode == 'PRANAYAMA'`)**:
       - **Heroic 13-Petal Side-View Blooming Lotus SVG** across 7 depth tiers (outer wings, mid-lateral wings, chalice petals, central erect spine, and emerald `#10B981` calyx/stem).
       - **Smooth Kinematic Bloom Physics**:
         - *Pūraka (Inhale)*: Petals lift and unfurl organically into full bloom with cubic bezier expansion.
         - *Antar Kumbhaka (Hold In)*: Sustained open flower floating gently on calm aquatic waves.
         - *Recaka (Exhale)*: Petals fold softly inward into a serene closed bud.
         - *Bāhya Kumbhaka (Hold Out / Void)*: Slender resting bud suspended in stillness.
       - **Dynamic Breath-Phase Color Harmony**: Luminous Cyan (`#4ECDC4`) for Pūraka, Radiant Amber (`#E5A93C`) for Antar Kumbhaka, Meditative Lavender (`#A78BFA`) for Recaka, and Celestial Azure (`#60A5FA`) for Bāhya Kumbhaka.
       - **Upper Sanskrit HUD & Devanagari Banner**: Elevated Sanskrit script display (`पूरक`, `कुम्भक`, `रेचक`, `शून्यक`), English guidance (`Inhale Deeply`), and large phase countdown numeral (`4`).
       - Round milestone tracker (`"Round 3 of 12"`) and interval bell indicator (`"🔔 Interval bell in 2 rounds"`).
     - **SURYA NAMASKAR Mode (`screenMode == 'SURYA'`)**:
       - Dedicated compound sequencer layout for 12-step Sun Salutation flows.
       - **Vector Posture Silhouettes**: 8 unique vector postures extracted directly from Android vector drawables (`yoga_pranamasana.xml`, `yoga_hastauttanasana.xml`, `yoga_padahastasana.xml`, `yoga_ashwa_sanchalanasana.xml`, `yoga_dandasana.xml`, `yoga_ashtanga_namaskara.xml`, `yoga_bhujangasana.xml`, `yoga_parvatasana.xml`) accurately mapped to steps 1 through 12.
       - 12-step solar progress indicator with numbered dot nodes and active step glow.
       - Posture headline displaying pose index, English name, and Sanskrit name (`Pose 1/12 • Pranamasana (Prayer Pose)`).
       - Sacred solar mantra card rendering traditional Devanagari invocation (`ॐ मित्राय नमः`).
       - Breath cue badge with dynamic inhalation/exhalation color accents (`Inhale & Exhale gently`).
       - Step countdown timer and master round badge (`Round 1 of 6`).
  3. **Bottom Transport Control Bar**:
     - Frosted floating pill container mirroring `SessionScreen.kt` transport controls.
     - Reset action button (`ic_ph_reset`) sending bidirectional reset command to Android sender.
     - Master Play / Pause button (`ic_ph_play` / `ic_ph_pause`) with pulsing golden prana halo.
     - Tune / Settings icon (`ic_ph_tune`) matching mobile layout balance.
- **Bidirectional Custom Message Bus (`urn:x-cast:com.habitbell.cast`) & Handshake Protocol**:
  - **Receiver Readiness Handshake**: Receiver emits `{ type: 'ready' }` upon startup (`EventType.READY`) and upon sender connection (`EventType.SENDER_CONNECTED`). Android `HabitBellCastManager` triggers `onReceiverReady`, causing `CentralSessionHandler` to immediately dispatch the current session snapshot (even if `IDLE`).
  - **Immediate Telemetry Synchronization**: Binds telemetry push to `castManager.isCasting.collect` regardless of session running status, instantly updating the TV from standby to active profile preview when the user taps Cast from the mobile home screen.
  - **Extended Telemetry Schema**:
    - `screenMode`: `'SPLASH' | 'GENERAL' | 'EATING' | 'PRANAYAMA' | 'SURYA'`
    - Session metrics: `remainingSeconds`, `elapsedSeconds`, `totalDurationSeconds`, `progress`, `currentRound`, `totalRounds`, `status`
    - Mindful eating metrics: `intervalDurationSeconds`, `nextBellSeconds`
    - Pranayama metrics: `pranayamaPhase`, `pranayamaSanskrit`, `pranayamaDisplay`, `pranayamaScript` (Devanagari), `phaseDurationSeconds`, `phaseRemainingSeconds`, `isIntervalBellEnabled`, `roundsUntilBell`
    - Surya Namaskar metrics: `poseIndex` (1..12), `poseName`, `poseSanskrit`, `poseBreath`, `poseMantra`, `poseRemainingSeconds`
    - Step / Cadence metrics: `isStepTrackingActive`, `currentSteps`, `formattedStepCount`, `formattedCadence`, `nextStepBellSteps`
  - **TV Remote & Web Receiver Feedback (Receiver -> Phone)**:
    - Captures hardware Play/Pause remote key events via CAF v3 and routes them back to `HabitBellCastManager.onRemotePlaybackAction` to synchronize mobile state.
    - Captures web receiver UI Reset click and routes `{ type: 'reset' }` back to `HabitBellCastManager.onRemoteResetAction` -> `CentralSessionHandler.reset()`.
- **Zero-Bandwidth In-Memory Web Audio Synthesis**:
  - Synthesizes authentic Tibetan singing bowl chimes directly inside the TV's browser hardware via HTML5 `AudioContext`.
  - Combines 432 Hz fundamental sine wave with 2.76 harmonic overtone (1192.3 Hz), shaped by a 20 ms linear attack ramp and an exponential acoustic decay envelope, delivering rich living room acoustics with zero audio streaming data transfer.
- **Prolonged Session Anti-Sleep Guard**:
  - Configures `CastReceiverOptions.disableIdleTimeout = true` and `options.maxInactivity = 21600` (6 hours).
  - Guarantees that Chromecast dongles will never revert to ambient art screensavers during prolonged meditation, breathwork, or yoga sequences.
- **Production Release Mandate (Cast Console Publishing)**:
  - Application ID `4662865D` operates in Unpublished / Developer Mode during active engineering and device validation on registered hardware.
  - **MANDATORY RELEASE ACTION**: Upon final completion of the Habit Bell product build and prior to Google Play Store public release, this application MUST be published via the Google Cast Developer Console (`https://cast.google.com/publish/#/overview`) by clicking **`PUBLISH`** next to App ID `4662865D`. This eliminates the need for device serial number registration, allowing all consumer Chromecasts, Google TVs, and Sony Bravia displays worldwide to run the custom receiver out of the box.

---

### 2.5. Android Auto & Foreground Services Subsystem (`com.habitbell.app.auto`, `com.habitbell.app.engine`)
Habit Bell provides deep automotive integration complying with Android for Cars design guidelines (Car App Library v1.7.0, Car API Level 8) and robust background foreground service execution:
- **`HabitBellCarAppService.kt`**: Top-level `CarAppService` entry point bound by the Android Auto host. Manifest category: `androidx.car.app.category.IOT` (wellness/timer/ambient routines). Uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` during development; production requires `HostValidator.Builder` with explicit allowlist.
- **`HabitBellCarSession.kt`**: Per-connection session lifecycle manager. Handles initial `onCreateScreen()` and reconnection via `onNewIntent()` for transient disconnect recovery. Coordinates `DisplayAutomationManager.setCarConnected()` state across connect/disconnect transitions with diagnostic lifecycle logging. Binds to `BluetoothAudioDisconnectionManager.onCarDisconnected()` to safely pause active sessions when unplugged from the vehicle.
- **`HabitBellCarScreen.kt`**: Driver-safe `ListTemplate` with 3 glanceable wellness routines (Posture, Breath, Eating). Optimized for the 2-second glance rule with shortened titles, duration indicators, and `ActionStrip` global Stop button during active sessions. State observer throttled to status-change and minute-boundary invalidation only (prevents 1Hz IPC flooding that destabilizes the Android Auto host Binder bridge). All `invalidate()` calls guarded by `Lifecycle.State.STARTED` check with `IllegalStateException` catch for host teardown race conditions.
- **`HabitBellMediaService.kt`**: Extends `MediaBrowserServiceCompat` to expose mindful audio routines to vehicle media drawers, system media controllers, and Wear OS. Emits driver-optimized `NotificationCompat.MediaStyle` ongoing notifications throttled to status transitions and 5-second intervals.
- **Session Token Sharing**: Both services bind directly to `CentralSessionHandler.sessionToken`, guaranteeing that media button presses on vehicle steering wheels instantly control the central timer engine with zero lag.

#### Background Service Lifecycle & Recents Task Dismissal (`onTaskRemoved` & `android:stopWithTask="true"`)
- **Problem Addressed (FLAW1)**: Prior to this architecture standard, clearing running apps from Android's Recents / App Switcher ("Clear All" / swipe away) left foreground media services running adrift in the background, causing continuous interval bell chimes, ambient audio streaming, wake lock retention, and an unkillable foreground notification.
- **Implementation & Teardown Flow**:
  - `HabitBellMediaService.kt` and `TimerService.kt` implement `override fun onTaskRemoved(rootIntent: Intent?)`.
  - When invoked by the Android OS upon task dismissal, `onTaskRemoved` immediately commands `sessionHandler.stop()`.
  - Halts `TimerEngine`, resets countdown state to `IDLE`, silences Tibetan bells in `AudioBellManager`, terminates ambient background music in `BackgroundMusicManager`, cancels haptic pulses in `HapticManager`, resets `HealthStepManager`, and releases power wake-locks in `BatteryOptimizer`.
  - Calls `stopForeground(STOP_FOREGROUND_REMOVE)` and `notificationManager.cancel(NOTIFICATION_ID)` to strip ongoing notifications from the system shade.
  - Commands `stopSelf()` to terminate the background service process and release system handles cleanly.
  - Manifest enforcement: Both services declare `android:stopWithTask="true"` in `AndroidManifest.xml`.
  - Clean Completion Termination: In `HabitBellMediaService.observeSessionState()`, transitioning to `SessionStatus.IDLE` or `SessionStatus.COMPLETED` calls `stopSelf()` to prevent idle service leakage.

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
- **Ongoing Session Auto-Load & State Restoration Architecture (FLAW2)**:
  - **Problem Addressed (FLAW2)**: When opening or returning to the app while a session was ongoing in the background, the UI previously defaulted to `AppScreen.HOME`, hiding the active session and forcing users to start another timer just to access the stop button.
  - **ViewModel Dynamic Destination Routing**: `HabitBellViewModel` initializes `_uiState` dynamically: if `sessionState.status == RUNNING || sessionState.status == PAUSED`, the initial destination is set to `AppScreen.SESSION`, restoring active display mode and pocket mode configurations immediately.
  - **Activity Entry Synchronization**: `MainActivity.kt` executes `viewModel.checkAndRestoreOngoingSession()` across `onCreate()`, `onNewIntent()`, and `onResume()`. Any launch intent with `EXTRA_NAVIGATE_TO_SESSION` or any foreground return during an active session routes instantly to `AppScreen.SESSION`.
  - **Splash Screen Bypass**: When `sessionState.status` is `RUNNING` or `PAUSED`, `MainActivity` automatically bypasses the splash screen overlay (`showSplashOverlay = false`) for instant access to playback controls.
  - **Home Screen Active Session Banner (`ActiveSessionBanner`)**: In `ModernHomeScreenSample.kt`, if the user explicitly navigates to the Home dashboard while a session is active, a prominent top banner renders the active profile name, remaining time countdown, pulsing status badge, and 1-tap "Stop" and "Open / Resume" buttons.
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
  - **De-duplicated Audio Surface**: Redundant volume sliders have been cleanly excised from Timer Settings, Pranayama Settings, and Surya Namaskar Sheets, routing all gain management exclusively through the unified Master Audio Gain controls in Global Config.

#### 2. Domain 2: Persistent Global Configuration (`SettingsDrawerTab.GLOBAL`)
- **Zen Focus (Do Not Disturb)**: Suppresses distracting system notifications during active mindfulness sessions.
- **Sun-Moon Circadian Mode with Blue-Light Attenuation**:
  - **Sun (Day Mode)**: Blue-light-reduced warm parchment palette (`#FAF6EE` background, gentle amber `#D97706` accents) engineered to prevent ocular fatigue and daylight glare without harsh cool blue emissions.
  - **Moon (Night Mode)**: Circadian wind-down palette featuring warm amber tones on deep charcoal (`#16130F`) or pure `#000000` AMOLED to power off OLED pixels entirely.
  - 1-tap Sun ☀️ ⇄ Moon 🌙 toggle plus granular theme selection (`AMOLED`, `EYE_COMFORT`, `DARK`, `LIGHT`).
- **Master Audio Gain & Ambient Sound Controls**:
  - **Bell Master Volume Slider** ($0\%..100\%$) with persistent storage in SharedPreferences (`bell_volume`) and immediate `[▶ Test Bell Chime]` audition button.
  - **Global Ambient Soundscape Mute Switch (`isBgMusicEnabled`)**: Centrally located in Global Config alongside volume sliders for 1-tap soundscape toggling across all timers.
  - **Background Ambient Volume Slider** ($0\%..100\%$) with active slider override, ducking compensation, and real-time audition preview toggle (`[▶ Test Ambient Sound]` / `[⏹ Stop Ambient Sound]`).
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

### 2.7. Unified Visual Identity, Adaptive Icons & Splash Screen Architecture

Habit Bell enforces a unified, high-contrast visual identity centered on the sacred blooming lotus flower cradling a resonant Tibetan mindfulness bell.

#### 1. Canonical Branding Asset Repository (`branding/`)
Authoritative vector artwork and multi-platform raster source files are permanently tracked in version control under `Healthy-Habit-Bell/branding/`:
- **`HabitBell_Inkscape.svg` & `HabitBell.svg`**: Master Inkscape scalable vector graphics containing coordinate-exact path nodes and drop shadow filter definitions.
- **`HabitBell_ChromeCast.png`**: Production 512×512 32-bit RGBA raster icon formatted specifically for the **Google Cast SDK Developer Console** and Google Play Store listings.
- **`HabitBell_Transparent.png`**: High-resolution 1027×893 transparent PNG master representing the glowing golden lotus and bell silhouette.
- **`HabitBell_Black.webp`**: Lossless WebP asset for web and companion application distribution.

#### 2. Automated Multi-Density Asset Pipeline (`scripts/generate_branding_assets.js`)
An automated Node.js automation pipeline utilizing headless Google Chrome and macOS `sips` renders subpixel-accurate graphics across all target platforms:
- **Android Adaptive Icons (`mipmap-*/ic_launcher_foreground.png`)**:
  - Rendered across `mdpi` (108px), `hdpi` (162px), `xhdpi` (216px), `xxhdpi` (324px), and `xxxhdpi` (432px).
  - Scaled strictly to 50% of the adaptive canvas width with a 25% boundary safety margin. This guarantees 0% visual clipping regardless of OEM launcher shape masking (circles, rounded rectangles, squircles, or teardrops).
- **Legacy Square & Circular Icons (`ic_launcher.png`, `ic_launcher_round.png`)**:
  - Direct hardware-clipped circular and square raster outputs across all density tiers (48px to 192px).
- **Android TV / Google TV Leanback Launcher (`tv_banner.png`)**:
  - 320×180 16:9 widescreen launcher banner featuring radial amber-gold glow and high-legibility typography.
- **Smart TV Ecosystems**:
  - LG webOS: 1920×1080 Full HD splash screen (`splash.png`) and tray icons (80×80 and 130×130).
  - Samsung Tizen: 117×117 app tile icon (`icon.png`).
  - Google Cast Receiver: 512×512 receiver app icon and transparent backdrop logo.

#### 3. Dual-Stage Splash Screen Architecture
To eliminate cold-boot latency and white screen flashes on both modern and legacy Android runtimes:
- **Stage 1 — Native OS Window Splash (`splash_background.xml` & `values-v31/styles.xml`)**:
  - Renders a lightweight `<layer-list>` drawable with solid obsidian dark background (`#060709`) and centered 160dp `@drawable/ic_splash_logo` during initial process fork and JVM warm-up.
  - On Android 12+ (API 31+), `Theme.HabitBell` binds `android:windowSplashScreenBackground`, `android:windowSplashScreenAnimatedIcon`, and `android:windowSplashScreenIconBackgroundColor`.
- **Stage 2 — In-App Serene Compose Handoff (`SplashScreen.kt`)**:
  - Hosted within `MainActivity`'s root `Box` as an `AnimatedVisibility` overlay.
  - Executes a subtle breathing scale (0.92f → 1.0f) and alpha fade-in (650ms) using `FastOutSlowInEasing`.
  - Automatically fades out smoothly (400ms) to reveal `ModernHomeScreenSample` on cold boot.
  - **Voice & Deep Link Bypass**: Automated intents (`ACTION_SET_TIMER`, `SURYA_TIMER`, `ACTION_VIEW`) immediately bypass the in-app splash animation (`showSplashOverlay = false`) to guarantee zero-latency execution for Google Assistant commands.

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
4. **Bahya Kumbhaka (बाह्य कुम्भक - External Retention / Shunya Void)**: Resting in primordial emptiness between breaths, stimulating hypercapnic adaptation (CO₂ tolerance) and cerebral vasodilation (Bohr effect). In classical Hatha Yoga, **Tri-Bandha (त्रिबन्ध)** is also actively applied during Bahya Kumbhaka, where the diaphragm is naturally elevated into the thoracic cavity, facilitating full abdominal vacuum suction (*Pūrṇa Uḍḍīyāna Bandha*), root seal (*Mūla Bandha*), and suprasternal jugular lock (*Kūpa Bandha*).

Both retention phases unconditionally display the classical Devanagari guidance `त्रिबन्ध (मूलबन्ध • उड्डीयान बन्ध • कूपबन्ध)` along with English transliteration `Tri-Bandha: Mūla • Uḍḍīyāna • Kūpa` positioned gracefully directly below the lotus flower and resting waterline, keeping the Upper HUD lean, spacious, and dedicated solely to phase nomenclature and the countdown numeral.

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
- **Tri-Bandha Voice Guidance Toggle (`isTriBandhaVoiceEnabled`, Default: ON)**:
  - When enabled, the gentle voice specifically whispers the sacred Tri-Bandha instruction upon entering both internal and external retentions:
    - *Sanskrit*: `"Kumbhak... Tri-Bandha"`
    - *Bilingual*: `"Kumbhak... Hold with Tri-Bandha"`
    - *English*: `"Hold... Tri-Bandha"`
  - Decoupled from the visual HUD (the HUD always presents Tri-Bandha unconditionally during Kumbhaka, while the auditory voice prompt is controlled by this user toggle).
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
  5. **Gentle Lady Voice Guide**: Option A (Sanskrit) default vs Option B (Bilingual) switch, dedicated Tri-Bandha voice cue toggle (`isTriBandhaVoiceEnabled`), and dynamic live audition button.
  6. **Meditative Interval Bell**: Default OFF toggle, cadence selector, and 432 Hz audition button.
  7. **Subtle Background Music**: Ambient sound toggle, Aum drone / YouTube / Custom file, and subtle volume slider.
- Bypasses generic timer countdown and signature 3-bell cards, maintaining a serene, focused user experience.

---

### 2.13. Surya Namaskār Timer & Room Persistence Subsystem (`com.habitbell.app.data`, `com.habitbell.app.ui`, `com.habitbell.app.sync`, `com.habitbell.app.audio`)

The Surya Namaskar subsystem provides comprehensive data persistence, animated vector visualization, speed presets, audio guidance, and companion Wear OS synchronization for classical Sun Salutation practices:

#### 1. Room Database Architecture (`SuryaDatabase.kt`)
- **Single Source of Local Schema**: Implements an offline-first SQLite database managed via AndroidX Room (`androidx.room:room-runtime:2.6.1`, `room-ktx`, with `ksp` compiler).
- **Process Singleton Pattern**: Thread-safe initialization using double-checked locking in `SuryaDatabase.getInstance(context)`. Configured with `fallbackToDestructiveMigration()` for streamlined schema evolution.
- **Relational Entity Model**:
  - `StepEntity`: Stored under table `"steps"`. Models individual postures with `@PrimaryKey(autoGenerate = true) val id: Long`, `name`, `orderIdx`, `isEnabled`, `voiceCueMode: VoiceCueMode`, `audioCue: String`, `mantraEnabled: Boolean`, `assetRef: String`, and timing fields (`durationSeconds`, `repetition`, `puraka`, `kumbhaka`, `rekha`).
  - `PresetEntity`: Stored under table `"presets"`. Maps preset identifiers (`"slow"`, `"moderate"`, `"fast"`) to JSON-serialized duration configurations.
  - `SettingEntity`: Stored under table `"settings"`. Key-value store for user-configured audio styles and companion sync preferences.
- **Type Converters (`SuryaTypeConverters`)**: Serializes non-primitive enum types like `VoiceCueMode` (`NONE(0)`, `PRANIC(1)`, `STEP_NAME(2)`, `SLOKA(3)`) to/from integer columns.
- **Data Access Objects (DAOs)**:
  - `StepDao`: Exposes reactive `getAllSteps(): Flow<List<StepEntity>>`, `getStepById(id)`, `insert()`, `update()`, and `delete()`.
  - `PresetDao`: CRUD access for speed preset entities.
  - `SettingDao`: Key-value query and mutation with reactive `getAllSettings(): Flow<List<SettingEntity>>`.

#### 2. Presentation Layer, Settings Sheet & Animated Silhouette Visuals (`SuryaTimerScreen.kt`, `SettingsDrawer.kt`, `AnimatedPoseView.kt`)
- **Reactive UI Flow**: `SuryaTimerViewModel` exposes `steps: StateFlow<List<StepUiModel>>` mapped directly from `StepDao.getAllSteps()`. On initial database creation, `SuryaDatabase.seedIfEmpty(context)` automatically seeds all 12 classical postures from `DefaultProfiles.SURYA_NAMASKAR`.
- **Dedicated Settings Drawer Integration (`SuryaSettingsSheet`)**:
  - Embedded within `SettingsDrawer.kt` when opening settings for `TimerType.COMPOUND` or profiles named "Surya Namaskar".
  - **4 Speed Presets**: Slow 10s, Moderate 5s, Fast 3s, and Custom [X]s. Selecting Custom reveals a dedicated pace editor with `-1s` / `+1s` micro-steppers and quick chips (`4s`, `7s`, `8s`, `12s`, `15s`).
  - **Target Practice Rounds**: Interactive steppers (`-1`, `+1`) and quick-select chips (`3`, `5`, `12`, `24`, `108` rounds), displaying total asanas count (e.g. 12 rounds = 144 asanas).
  - **Global Voice Guidance Modes**: Single-tap global selector across all 12 steps for `Asana Name`, `Solar Mantra`, `Breath Flow`, and `Silent / Bell`.
  - **12 Posture Sequence Preview**: Illustrated list displaying the sequence of postures with duration tags and vector silhouette artwork (`R.drawable.avd_yoga_pranamasana`).
  - **Fullscreen Editor Transition**: Direct button navigation to `AppScreen.SURYA_TIMER` (`SuryaTimerScreen`) for modifying per-posture Sanskrit voice cues, durations, and solar mantra audio playback.
  - **Companion Watch Synchronization**: Quick-sync button invoking `SuryaSyncManager.pushSyncToWatch()` to send current sequence configuration to Wear OS devices over the Wearable Data Layer API.
  - **Background Ambient Soundscape**: Toggle and source selectors (ॐ Aum drone, YouTube audio, custom audio file) with real-time volume slider.
  - **Signature Acoustic Identity**: Audition buttons for Option C separator chime, Tibetan Singing Bowl / Temple Gong, and quick 10-second demo session.
- **Animated Silhouette Rendering (`AnimatedPoseView.kt`)**:
  - Bridges Android's native `AnimatedVectorDrawable` (`avd_yoga_pranamasana.xml`) into Jetpack Compose via `AndroidView` with `ImageView`.
  - Employs `(drawable as? Animatable)?.start()` inside `DisposableEffect` for lifecycle-aware, zero-leak entrance animations.
  - Two concurrent visual animations: 12dp vertical rise translation (`translateY` 12dp → 0dp over 800ms) paired with progressive fill opacity (`fillAlpha` 0.0 → 1.0 over 600ms) with quadratic deceleration.

#### 3. Companion Wear OS Synchronization (`SuryaSyncManager.kt`)
- **Wearable Data Layer Client**: Pushes serialized timer configuration payloads across the Google Play Services `Wearable.getDataClient(context)` bridge.
- **Payload Contract (`/surya_sync`)**: Packs step models, presets, and settings into a unified JSON descriptor via `JsonUtil` (Google Gson) transferred as an urgent `PutDataMapRequest`.
- **Zero-Latency Push**: Executed asynchronously on `Dispatchers.IO` when the user taps "Sync Watch" on the phone interface.

#### 4. Audio Guidance & Studio Voice Engine (`SuryaVoicePlayer.kt` & `SuryaPoseAssets.kt`)
- **Studio-Mastered SwaraNeural Audio Profile**: Plays high-definition, studio-mastered audio files (`res/raw/surya_*.mp3`) synthesized with Microsoft Natural Neural voice `hi-IN-SwaraNeural` (+52Hz pitch, unhurried -30% yogic cadence, Lata Mangeshkar meditative timbre).
- **Dynamic Mode Cues**:
  - `VoiceCueMode.STEP_NAME`: Articulates classical Sanskrit Asana name with breathing guidance (e.g., "प्रणामासन, Inhale and Exhale", "हस्तउत्तानासन, Inhale").
  - `VoiceCueMode.SLOKA`: Chants the respective classical Solar Mantra followed by the Asana (e.g. "ॐ मित्राय नमः, प्रणामासन", "ॐ रवये नमः, हस्तउत्तानासन").
  - `VoiceCueMode.PRANIC`: Guides yogic breath flow and posture transitions with high-fidelity studio clips.
  - `VoiceCueMode.NONE`: Silent / Bell mode emitting no spoken cues, preserving meditative silence.
- **Anti-Startle Lead Delay & Raised-Cosine Ducking**: Inserts an anti-startle 120ms lead delay after smooth background music ducking (`duckVolume(duckedRatio = 0.20f, durationMs = 350L)`) before speech playback starts, and gently restores background audio over 500ms upon completion.
- **Anti-Clipping Step Duration Guard**: In accordance with the project's Step Timing vs. Voice Timing Law, if an allocated step duration is short (< 4 seconds, such as in the Fast 3s preset), bilingual cues automatically fall back to the concise Sanskrit solar mantra so the spoken audio is never clipped mid-sentence by the next transition.
- **Offline Android TextToSpeech Fallback**: Native Android `TextToSpeech` engine configured with sweet high-pitch (`1.28f`) and unhurried cadence (`0.75f`) acts strictly as a resilient offline fallback if a raw audio resource is unavailable.
- **Posture Vector Asset Catalog (`SuryaPoseAssets.kt`)**: Maps the 12 cyclical postures to their dedicated monochrome vector silhouettes (`yoga_pranamasana`, `yoga_hastauttanasana`, `yoga_padahastasana`, `yoga_ashwa_sanchalanasana`, `yoga_dandasana`, `yoga_ashtanga_namaskara`, `yoga_bhujangasana`, `yoga_parvatasana`) for accurate rendering in `SuryaTimerScreen` and `CompoundPoseCard`.

#### 5. Live Timer Reflection & Bidirectional Persistence Pipeline
- **Problem Solved**: Historically, adjusting Surya Namaskar settings in `SuryaSettingsSheet` or `SuryaTimerScreen` only mutated Room SQLite tables, while `CentralSessionHandler` and `TimerEngine` executed against immutable `TimerProfile` models stored in `TimerRepository`. As a result, modified pose timings and round counts failed to reflect into active countdowns.
- **Bidirectional Event Pipeline**:
  ```
  SuryaSettingsSheet / SuryaTimerScreen
               |
               v (onUpdateSurya / syncStepsToEngineAndRepository)
       HabitBellViewModel.updateActiveSuryaSettings(...)
               |
               +--------------------------------------------+
               |                                            |
               v (writes SharedPreferences + Room)          v (live-reloads active profile)
       TimerRepository.updateSuryaSettings(...)      TimerEngine.loadProfile(updatedProfile)
               |                                            |
               v                                            v
     _profiles StateFlow                        StateFlow<TimerSessionState>
               |                                            |
               v                                            v
     HomeScreen & Drawer UI                      SessionScreen Active Countdown
                                                 - Remaining Pose Seconds (${remainingSeconds}s)
                                                 - Total Session Countdown (MM:SS)
                                                 - Round Counter (ROUND X OF Y • POSE Z / 12)
                                                 - Solar Mantra (☀️ ॐ मित्राय नमः)
  ```
- **Real-Time Dynamic Recalculation**:
  - Immediately recalculates total session duration: `totalDurationSeconds = targetRounds * poses.sumOf { it.durationSeconds }`.
  - Re-evaluates pose countdown intervals and displays the active pose remaining seconds badge dynamically inside `CompoundPoseCard`.
  - Seamlessly persists across application restarts via SharedPreferences key `"surya_config_v1"` and Room `StepDao` updates.

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
| `SuryaDatabase` | Process Singleton / Room Pool | `Dispatchers.IO` | Manages SQLite connection pooling, schema migrations, and async DAO query executions. |
| `SuryaTimerViewModel` | `viewModelScope` | `Dispatchers.Main.immediate` | Observes reactive step StateFlows and dispatches preset updates and background seeding to IO. |
| `SuryaSyncManager` | Dedicated Sync Scope | `Dispatchers.IO` | Serializes configuration JSON payloads and pushes them over Wearable DataClient asynchronously. |
| `SuryaVoicePlayer` | Main Thread Coroutine Scope | `Dispatchers.Main` / AudioTrack | Manages speech cue MediaPlayer instances, 120ms lead delay, and background music ducking. |

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
