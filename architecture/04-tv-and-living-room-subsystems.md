# 04. TV & Living Room Subsystems

Habit Bell treats the living room as a primary sanctuary for mindfulness, meditation, and yoga practice. This document details the multi-platform TV casting, mirroring, and telemetry subsystems.

---

## 1. Living Room & TV Integration Strategy

- **Living Room Strategy**: Living Room TV connectivity is exclusively handled through genuine TV streaming pipelines: **Google Cast** (cloud/LAN media receiver) and **Screen Mirroring (Miracast)**. The confusing on-phone "TV Dashboard Mode" (previously an oversized on-device display) has been completely removed from all navigation and UI surfaces.
- **Universal Single APK**: A single binary deployment targets smartphones, tablets, foldables, automotive head units, and Android TV / Google TV.
- **Sony Bravia Hardware Integration**: All modern Sony Bravia smart TVs run Google TV / Android TV with Chromecast built-in. Habit Bell provides first-class Sony compatibility out of the box via both native APK installation and Google Cast streaming.
- **Manifest Architecture**:
  - Declares `<category android:name="android.intent.category.LEANBACK_LAUNCHER" />` for TV app drawers.
  - Declares `android:banner="@drawable/tv_banner"` for high-resolution 16:9 Android TV launcher cards.
  - Features marked optional (`required="false"`): `android.software.leanback`, `android.hardware.touchscreen`, `android.hardware.microphone`, `android.hardware.telephony`, `android.hardware.camera`.

---

## 2. Google Cast Framework (`com.habitbell.app.cast`)

- **Native Cast Integration**: Pure application TV streaming without screen mirroring using Google Play Services Cast Framework (`play-services-cast-framework:22.0.0`).
- **`CastOptionsProvider.kt`**: Configures Google Cast framework options, bound to the registered Habit Bell Custom Web Receiver Application ID (`4662865D`) targeting `https://apanasara.github.io/Healthy-Habit-Bell/`, with automatic fallback to Google's Default Media Receiver (`CC1AD845`).
- **`HabitBellCastManager.kt`**: Singleton session manager coordinating discovery, device connection, and media metadata transmission to Chromecast, Sony Bravia, and Google Cast-enabled TVs.
- **Cast Feedback Loop & 15-Second Reconnect Resolution (FLAW-2)**:
  - Decouples Cast player state from false pause events: In `RemoteMediaClient.Callback`, transient states (`PLAYER_STATE_BUFFERING`, `PLAYER_STATE_LOADING`, `PLAYER_STATE_IDLE`, `PLAYER_STATE_UNKNOWN`) are explicitly ignored. Only genuine user transitions (`PLAYER_STATE_PLAYING` and `PLAYER_STATE_PAUSED`) dispatch to `onRemotePlaybackAction`.
  - Implements `isDispatchingLocally` volatile re-entrancy flags on `loadSession`, `play()`, `pause()`, and `stop()` to eliminate echo feedback loops between mobile commands and Cast listener callbacks.
  - Implements `lastCastProfileId` tracking in `CentralSessionHandler` so resuming from pause calls `castManager.play()` rather than reloading the stream from zero, preventing continuous buffering cycles.
- **Profile-Specific Mindful Artwork & Ad-Free Ambient YouTube Audio**:
  - Replaced legacy external Pixabay fallback stream (`DEFAULT_FALLBACK_STREAM_URL`) with direct embedded YouTube audio playback in the Custom Web Receiver.
  - When the Custom Web Receiver is active (`isCustomReceiver == true`), `HabitBellCastManager.loadSession()` bypasses CAF v3 `client.load(requestData)`, preventing the Google Cast Default Media Player from playing external dummy tracks or Pixabay MP3 overlays on top of the TV meditation visualizer.
  - Dynamically binds session-specific high-resolution artwork (Sacred Lotus for Pranayama, Golden Dawn for Surya Namaskar, Mindful Eating Bowl, Forest Walk Path) tailored to the active profile.
- **Mobile-to-TV Anti-Echo Audio Handover Protocol**:
  - To prevent acoustic phase interference and double-sound echo in the living room, `CentralSessionHandler` suspends mobile phone background music (`bgMusicManager.pause()`) when Google Cast is actively connected (`isCasting == true`).
  - The TV receiver takes full ownership of the acoustic soundscape, streaming the user's configured YouTube meditation audio or procedural chimes through the living room sound system.
  - If the Cast session disconnects while a session is running, `CentralSessionHandler` automatically resumes mobile phone background music (`bgMusicManager.start()`), providing continuous, uninterrupted practice.
  - Real-time parameter changes (adjusting ambient volume, toggling background audio, or updating YouTube URL in the Settings drawer) are immediately synchronized to the TV over the Cast message bus without restarting the timer.
- **`CastButton.kt`**: Jetpack Compose-native Cast button wrapping AndroidX MediaRouter's `MediaRouteButton` to display discovery states and trigger device selection dialogs.
- **Host Activity Architecture**: `MainActivity` inherits from `androidx.fragment.app.FragmentActivity` to provide the `FragmentManager` required by `MediaRouteButton` to display native Google Cast route picker dialogs across all Android platforms without runtime crashes.
- **`HabitBellChooserDialogFragment` & `HabitBellControllerDialogFragment`**: Public top-level subclasses of `MediaRouteChooserDialogFragment` and `MediaRouteControllerDialogFragment` implementing zero-arg public constructors and theme bundle arguments (`HabitBellMediaRouteTheme_Dark` / `Light`). This strictly complies with Android's `FragmentManager` contract and prevents `IllegalStateException: Fragment ... must be a public static class` crashes upon Cast icon taps.

---

## 3. Screen Mirroring Subsystem (Miracast / Wi-Fi Display / Any TV / Chromecast)

- **Universal Living Room Projection (FLAW-3)**: Provides 1-tap integration with Android OS Screen Mirroring (`android.provider.Settings.ACTION_CAST_SETTINGS` with fallback to `ACTION_WIRELESS_SETTINGS`) in `SettingsDrawer.kt`.
- **Zero-Internet Local Operation**: Unlike Google Cast SDK which mandates an active internet connection to download cloud receiver shells from Google servers, Screen Mirroring operates 100% peer-to-peer over local Wi-Fi, making it the bulletproof market standard for offline classes, studios, and living rooms without internet access.
- **Full Visual Fidelity**: Projects the phone's full Compose canvas directly onto the TV screen, displaying real-time Pranayama breathing animations (blooming lotus, expanding breath ring), live countdowns, and Surya Namaskar posture cards with zero cloud reliance.
- **Hardware Backlight Decoupling & Battery Conservation (Requirement C.1)**:
  - During active countdowns across all profile types (Linear, Pranayama, Surya Namaskar, Compound), the app sets `WindowManager.LayoutParams.screenBrightness = 0.01f` (1% minimal hardware backlight).
  - Crucially, setting `screenBrightness` modulates only the phone's physical display panel LED/OLED driver; it does **not** alter the GPU rendering buffer (`SurfaceFlinger`).
  - As a result, the mirrored TV screen receives uncompromised pixel color and luminance, remaining at **100% full, vivid brightness**, while the smartphone draws minimal battery current and remains cool to the touch.
- **Unconditional Screen Awake Lock (Prevents 3–4 Min "Connection Lost")**:
  - `MainActivity` dynamically binds `FLAG_KEEP_SCREEN_ON` for the entire duration of `SessionStatus.RUNNING` or whenever `isScreenMirroringActive` is true.
  - This prevents Android OS display sleep from turning the screen off and terminating the real-time H.264 screen capture encoder (`MediaProjection` / Cast Mirroring pipeline).
- **Low-Latency Wi-Fi Lock (Prevents 26-Min Doze Disconnect)**:
  - `CentralSessionHandler` acquires `WIFI_MODE_FULL_LOW_LATENCY` (`WifiLock`) via `BatteryOptimizer` on session start.
  - This prevents the Wi-Fi radio from entering DTIM sleep during prolonged stationary sessions, eliminating packet loss and stream stalling beyond 25–30 minutes.
- **Interactive Touch Grace Period**:
  - Any tap on the Compose root window triggers `HabitBellViewModel.onUserTouchDisplay()`, which dispatches `TimerEngine.wakeScreenTemporarily(6)`.
  - The phone screen physically brightens for 6 seconds for effortless user interaction, then automatically re-dims to 1% while the TV stays continuously illuminated.
- **TV Screen Orientation & Rotation Subsystem (`ScreenOrientation.kt` & `ScreenMirroringManager.kt`)**:
  - **Hardware & Manual Mirroring Sensing**: `ScreenMirroringManager` registers `DisplayManager.DisplayListener` callbacks on the system `DisplayManager`, automatically identifying external HDMI monitors, Miracast receivers, and Wi-Fi Display sinks (`display.displayId != Display.DEFAULT_DISPLAY`). A manual toggle in `SettingsDrawer` provides a fallback for system-level Google Cast Screen Mirroring ("Cast Screen / Audio" in Quick Settings / Google Home) and Samsung Smart View.
  - **Dynamic Activity Window Reorientation**: When orientation changes, `MainActivity` updates `requestedOrientation` dynamically between `ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE` (16:9 widescreen TV), `SCREEN_ORIENTATION_SENSOR_PORTRAIT` (vertical smart displays/monitors), and `SCREEN_ORIENTATION_UNSPECIFIED` (device accelerometer tracking).
  - **Multi-Surface Compose Controls**:
    - **`SessionScreen` Top Action Bar**: Dedicated rotate icon buttons positioned beside `CastButton` in both `LandscapeSessionLayout` (`Icons.Outlined.StayCurrentPortrait` to rotate back to vertical) and `PortraitSessionLayout` (`Icons.Outlined.StayCurrentLandscape` to rotate into widescreen TV layout).
    - **`SessionScreen` Transient Mirroring Orientation Pill**: An ephemeral floating notification pill (`TV Mirroring: Horizontal • Tap to Rotate Vertical` / `TV Mirroring: Vertical • Tap to Rotate Horizontal`) rendered at top center. It automatically fades out and slides up after 4.0 seconds (or upon tapping the integrated close button) so as never to persistently obstruct the top action bar, session titles, or back navigation. It re-triggers briefly upon orientation changes to provide instant confirmation of display aspect mode.
    - **`ModernHomeScreenSample` Top Bar**: Surfaces dynamic rotate button in `ModernZenTopBar` when mirroring is active.
    - **`SettingsDrawer` (Living Room Section)**: Features the active display status badge, "Screen Mirroring Mode" toggle, segmented orientation buttons ("Vertical", "Horizontal", "Auto"), and a prominent "Rotate Screen ⇄" quick action button.
  - **Configuration Synchronization**: `MainActivity.onConfigurationChanged` broadcasts window dimension updates into `ScreenMirroringManager.notifyConfigurationChanged()` to maintain tight alignment between device sensors and UI state flows.

---

## 4. Local TV WebCast (`LocalCastWebServer.kt` & `assets/tv/index.html`)

- **Zero-Cloud Local Casting**: Embedded lightweight multi-threaded HTTP server running on port `8888`.
- **Network Service Discovery (NSD)**: Registers an mDNS service (`_habitbell._tcp`) allowing any Smart TV browser on the same Wi-Fi network to discover and open the TV dashboard.
- **Enriched Real-Time State Contract (`/api/state`)**: Broadcasts comprehensive routine metadata including:
  - `pranayamaPhase`, `pranayamaDisplay`, `pranayamaSanskrit`, `phaseRemaining`, `phaseDuration`.
  - `poseName`, `poseSanskrit`, `poseBreath`, `poseRemaining` for compound yoga sequences.
  - `currentRound` and `totalRounds`.
- **Interactive TV Visualizer (`assets/tv/index.html`)**: Features an expanding/contracting breath visualizer ring (`.breath-ring.inhale`, `.hold-in`, `.exhale`, `.hold-out`) that morphs color, scale, and opacity in lockstep with the active breath phase, plus live Surya Namaskar asana guidance.
- **Local Media Streaming**: Serves `/media/aum.mp3` with byte-range streaming support directly from application assets.

---

## 5. Samsung Smart TV (Tizen OS) & LG Smart TV (webOS) Ecosystem

- **Market Reach**: Samsung Tizen (~21%) and LG webOS (~12%) represent >33% of global connected smart TVs.
- **Packaged Web TV Suite**:
  - `tv-platforms/samsung-tizen/`: Packaged Tizen Web Application container (`.wgt`) with `config.xml` manifest and Samsung TV Remote key handling (`tizen.tvinputdevice.registerKey`).
  - `tv-platforms/lg-webos/`: Packaged LG webOS application (`.ipk`) with `appinfo.json` descriptor and Magic Remote pointer/D-pad mappings.
- **`DialTvDiscoverer.kt` Subsystem**:
  - Dispatches SSDP (Simple Service Discovery Protocol) M-SEARCH UDP multicast probes (`239.255.255.250:1900`) for DIAL services (`urn:dial-multiscreen-org:service:dial:1`) and UPnP `MediaRenderer`.
  - Auto-identifies Samsung and LG TVs on the local Wi-Fi and provides zero-click remote launching of the TV dashboard.

---

## 6. Apple TV & AirPlay 2 Subsystem (`com.habitbell.app.cast`)

- **Market Context**: Apple TV (tvOS) dominates the premium streaming box sector. Because tvOS contains no web browser, Habit Bell deploys a dual-track strategy:
- **Track 1 — Direct AirPlay 2 Sender Protocol (`AirPlayCastManager.kt`)**:
  - Scans for nearby Apple TV devices on local Wi-Fi via mDNS / Bonjour (`_airplay._tcp.` and `_raop._tcp.`).
  - Maintains reactive `discoveredDevices: StateFlow<List<AirPlayDevice>>` for casting session metadata and audio to Apple TV hardware.
- **Track 2 — Native Apple TV Companion App (`tv-platforms/apple-tvos/`)**:
  - Native Swift 5.10+ / SwiftUI application built for tvOS 17+.
  - Features circular countdown stroke animation, Siri Remote Clickpad gestures, and real-time Bonjour mDNS discovery (`_http._tcp.`) auto-syncing with `LocalCastWebServer` on the Android device via Server-Sent Events.

---

## 7. Google Cast Custom Web Receiver & Bidirectional Telemetry Protocol (`docs/index.html`, `tv-platforms/google-cast-receiver/`, & `app/src/main/assets/tv/`)

- **Architectural Role & TV Sandboxing**: Solves the browserless TV and phone distraction challenges by executing a dedicated, cloud-hosted Custom Web Receiver directly within the Google Cast Application Framework (CAF v3) hardware sandbox on Chromecasts, Google TVs, and Sony Bravia displays. Allows the user's phone to dim or sleep while the TV independently renders the meditation canvas.
- **Identical Triplicate Target Synchrony**: The receiver codebase is mirrored across three 100% identical targets kept in bit-level synchrony:
  1. `tv-platforms/google-cast-receiver/index.html`: Authoritative reference source.
  2. `docs/index.html`: GitHub Pages production endpoint serving Cast Application ID `4662865D` (`https://apanasara.github.io/Healthy-Habit-Bell/`).
  3. `app/src/main/assets/tv/index.html`: Embedded offline web server asset bundled inside the Android APK on port 8888 for zero-internet LAN casting.
- **CAF v3 Compliance & Media Player Architecture**:
  - Embedded `<cast-media-player style="display:none;"></cast-media-player>` enables CAF v3 `PlayerManager` to bind cleanly, preventing session initialization crashes when the Android sender attaches `RemoteMediaClient` and `CastMediaOptions`.
  - Custom namespace declaration: Explicitly pre-registers `options.customNamespaces = { ['urn:x-cast:com.habitbell.cast']: cast.framework.system.MessageType.JSON }` prior to `context.start(options)`.
  - Safe payload deserialization: Handles both pre-parsed JSON objects and raw string transmissions via `const msg = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;`, eliminating `SyntaxError: Unexpected token o in JSON at position 1` crashes that previously dropped sender state packets.
- **Bit-Identical Mobile Timer UI Harmonization (`SessionScreen.kt` LandscapeSessionLayout Parity)**:
  The receiver UI is engineered to match the Android mobile timer experience in landscape mode (`SessionScreen.kt`'s `LandscapeSessionLayout`) bit-identically across visual structure, 16:9 two-column widescreen division, component sizing, and dynamic topologies:
  1. **Two-Column Horizontal Split Architecture**:
     - **Left Column (Visualizer Area, weight 1.15)**: Dedicated to the hero visualizer centerpiece across all topologies.
       - *Linear*: SVG circular progress ring (radius 190, circumference 1194px) with animated glowing progress head dot, large timer numerals (`88px`), step count, elapsed/total subtext (`00:00 elapsed • 15:00 total`).
       - *Mindful Eating (`MindfulEatingLandscapeContent` Parity)*: Concentric dual rings: Outer meal countdown ring (radius 190, circumference 1194px) with animated glowing head dot + inner bite-pacing arc (radius 54, circumference 339px) with rounded caps sweeping clockwise from 12 o'clock; authentic Phosphor food bowl icon (`ic_ph_bowl.xml`); candlelit radial breathing aura; right column meal countdown numerals (`78px`), bite capsule (`🔔 Bite in MM:SS • CHEW & SAVOR`), and rotating 12-second mindful eating guidelines carousel.
       - *Pranayama (`BreathIndicator.kt` 1:1 Procedural Canvas Engine)*: High-performance HTML5 canvas rendering 13 curved petals across 7 depth layers with dual angle/length morphing, 3-leaf calyx with dynamic receptacle, waterline ripples, C2 floating wave and lateral angular sway, and breathing prana radial aura.
       - *Surya Namaskar (`CompoundPoseCard` Parity)*: Dedicated card featuring the active pose name, Sanskrit translation, Devanagari solar invocation (`☀️ ॐ मित्राय नमः`), 12-step flow progress bar, 150px vector posture silhouette, synchronized breath cue badge, and pose countdown pill.
     - **Right Column (Info & Transport Controls, weight 1.05)**:
       - **Top Action Bar**: Frosted Back button (`ic_ph_back`), session profile title, Cast indicator badge (`ic_ph_tv` with emerald beacon), and Tibetan bowl sound pill (`ic_ph_bowl`).
       - **Middle Status & HUD**: Dynamic phase titles, Devanagari banners, giant seconds countdowns (`96px`), round milestone capsules, and cadence counters.
       - **Bottom Transport Controls**: Floating frosted pill with Reset (`ic_ph_reset`), master Play/Pause with golden glow (`ic_ph_play`/`ic_ph_pause`), and Settings (`ic_ph_tune`).
- **Bidirectional Custom Message Bus (`urn:x-cast:com.habitbell.cast`) & Handshake Protocol**:
  - Receiver emits `{ type: 'ready' }` upon startup and sender connection, triggering immediate session snapshot synchronization.
  - Binds telemetry push to `castManager.isCasting.collect` regardless of session running status, instantly updating the TV from standby to active profile preview.
  - Remote key events and web receiver clicks (`{ type: 'reset' }`, play/pause) route directly to `HabitBellCastManager` and `CentralSessionHandler`.
  - **Extended Telemetry Schema**: Transmits `bgMusicType` (e.g. `YOUTUBE_LINK`), `bgMusicEnabled` (`boolean`), `youtubeVideoId` (extracted 11-char ID, defaults to `x6UITRjhijI`), `youtubeUrl`, and `bgMusicVolume` (`0.0f..1.0f`).
- **Embedded Ad-Free Headless YouTube Audio Engine**:
  - Embedded offscreen `#ytAudioPlayerContainer` hosting a sandboxed YouTube IFrame player (`https://www.youtube.com/iframe_api`).
  - Streams the user's configured/default YouTube meditation track (`https://youtu.be/x6UITRjhijI`) directly through the TV receiver hardware.
  - Automatic 500ms ad-skipping interval detects and clears preroll/midroll ad overlays.
  - Automatic infinite looping triggers replay on track end (`YT.PlayerState.ENDED`), matching prolonged mindfulness sessions.
  - Synchronous play/pause state mapping: Timer running states automatically play the audio; pause or idle transitions pause the player; volume level dynamically tracks mobile slider settings (0–100%).
  - Immediate local response: Receiver play/pause buttons and physical TV remote keys (MediaPlay, MediaPause, MediaPlayPause) toggle playback immediately and send back control intents to the Android sender.
- **Zero-Bandwidth In-Memory Web Audio Synthesis**:
  - Synthesizes authentic Tibetan singing bowl chimes directly inside TV browser hardware via HTML5 `AudioContext` (432 Hz fundamental sine wave with 2.76 overtone at 1192.3 Hz).
- **Prolonged Session Anti-Sleep Guard**:
  - Configures `CastReceiverOptions.disableIdleTimeout = true` and `options.maxInactivity = 21600` (6 hours) to prevent Chromecasts from falling asleep during long sessions.
- **Production Release Mandate (Cast Console Publishing)**:
  - App ID `4662865D` operates in Unpublished / Developer Mode during active engineering.
  - **MANDATORY RELEASE ACTION**: Upon final completion of the Habit Bell product build and prior to Google Play Store public release, this application MUST be published via the Google Cast Developer Console (`https://cast.google.com/publish/#/overview`) by clicking **`PUBLISH`** next to App ID `4662865D`.
