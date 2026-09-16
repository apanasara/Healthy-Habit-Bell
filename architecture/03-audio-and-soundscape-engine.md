# 03. Audio & Soundscape Engine

The audio architecture guarantees high-fidelity, boundary-free sound reproduction across handset, vehicle audio systems, and living room displays without startling practitioners during contemplative stillness.

---

## 1. Dual-Engine Bell Chimes (`AudioBellManager.kt`)

- **Procedural Tone Synthesis**: Real-time sine-wave calculation with exponential decay envelope (e.g. 432Hz healing frequency, 528Hz Solfeggio frequency) synthesized directly to low-latency `AudioTrack` streams.
- **Harmonic Sample Audio (`SoundPool`)**:
  - **Option C (3-Bell Zen Tingsha)**: High-resolution countdown chime used for periodic interval bells.
  - **Temple Gong**: Rich, resonant low-frequency acoustic bell triggered upon session completion.
- **Automotive Audio Routing**:
  - Configured strictly with `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_MUSIC`.
  - **Constant Ambient Volume Layering (Requirement E2)**:
    - Interval bells, countdown strikes, single-strike Pranayama bells, and session completion gongs layer additively over ongoing background ambient music on the shared `USAGE_MEDIA` stream without requesting OS-level transient ducking audio focus.
    - This eliminates jarring volume dips and abrupt snap-backs, maintaining a constant ambient background volume throughout the entire meditative session as configured by the user.
    - Spoken vocal guidance continues to utilize the gentle raised-cosine software crossfader (`duckVolume` / `restoreVolume` over 350ms/500ms) to ensure vocal clarity.

---

## 2. Ambient Soundscape Subsystem (`BackgroundMusicManager.kt`)

- **Bundled Ambient Drones**: High-definition continuous Aum chant drone bundled compile-time via `R.raw.aum`.
- **Local User Storage**: Seamless playback of custom audio files loaded via the Storage Access Framework (SAF).
- **Sandboxed YouTube Audio Streaming**:
  - Headless, ad-free YouTube audio extraction and streaming engine using an isolated `WebView`.
  - Injects custom JavaScript to suppress video canvas rendering, minimize CPU usage, and guarantee seamless looping and persistent custom URL playback.
  - Linear 0%..100% dynamic volume scaling (`coerceIn(0, 100)`), eliminating arbitrary lower-bound attenuation clamps.
- **System Share Sheet Integration & Shortest URL Normalization**:
  - Registers `android.intent.action.SEND` with MIME type `text/plain` in `AndroidManifest.xml` on `MainActivity`, indexing Habitbell as a direct target in Android's native system Share Sheet.
  - **Multi-Topology URL Extraction (`extractVideoId`)**: Robust regular expression parsing matching 11-character video IDs across standard watch links (`watch?v=`), short links (`youtu.be/`), Shorts (`/shorts/`), 24/7 ambient live streams (`/live/`), mobile/music domains (`m.youtube.com`, `music.youtube.com`), and URL-encoded attribution redirects (`%2Fwatch%3Fv%3D`).
  - **Canonical Shortest URL Formatting (`toShortestYouTubeUrl`)**: Automatically converts any shared YouTube link or text block into the canonical 28-character shortest URL format (`https://youtu.be/<videoId>`), cleanly stripping extraneous tracking parameters (`?si=...`, `&feature=share`, `&t=...`).
  - **Two-Way Synchronization**: Automatically copies the canonical shortest URL into the Android system `ClipboardManager` and applies it to `HabitBellViewModel` / `BackgroundMusicManager` (`isBgMusicEnabled = true`, `soundType = YOUTUBE_LINK`), persisting to `SharedPreferences` (`bg_music_yt_url`).
  - **Context-Aware Visual Feedback**: Issues an informative `Toast` notification and, if no session is actively running, opens the Settings Drawer directly to `SettingsDrawerTab.TIMER` for instant stream auditioning via `▶ Test Stream`. If a meditation timer is currently running, stream audio updates seamlessly without interrupting the immersion screen.
- **Raised-Cosine S-Curve Crossfader & Dynamic Gain Adaptation**:
  - `smoothFadeTo(targetGain, durationMs)`: Smoothly interpolates volume transitions via raised-cosine S-curve easing: `0.5 * (1 - cos(π * progress))`.
  - `duckVolume(0.20f, 350L)`: Temporarily and smoothly lowers ambient background audio during voice guidance cues to ensure crystalline vocal clarity.
  - `restoreVolume(500L)`: Elegantly restores ambient music back to configured gain without jarring steps or pops.
  - **Immediate Manual Override**: Slider adjustments cancel active fade animators to provide zero-latency, real-time auditory feedback. When adjusting volume while ducked, attenuated gain is recalculated proportionally from the new base volume without dropping the ducking state.
  - **State Sanitization on Pause/Stop**: Pausing or stopping playback immediately resets `isDucked = false` and terminates pending fade animations, preventing stale ducked gain when sessions resume.
  - **Post-Start Gain Enforcement**: Calls `mediaPlayer.setVolume()` both before and immediately after `start()` to overcome asynchronous Android media server gain resets.

---

## 3. Unified Voice Engine & Anti-Startle Guidance Subsystem (`UnifiedVoiceEngine.kt`)

### 3.1 Architectural Rationale & Single Engine Architecture
In earlier iterations of Habit Bell, vocal guidance was fragmented across 4 separate components (`PranayamaVoiceGuide`, `PreparationVoiceGuide`, `SuryaVoicePlayer`, and `AndroidDualCueSpeaker` in `HoldTimerEngine`). Each component maintained or requested separate `TextToSpeech` instances, causing:
1. **Audio Server Contention**: Multiple `TextToSpeech` hardware handles competing for OS media resources.
2. **Acoustic Inconsistency**: Differing voice locales, speech speeds, and pitch offsets between timers.
3. **Redundant Ducking Orchestration**: Duplicated ducking logic and media listener lifecycles.

To solve this, **`UnifiedVoiceEngine.kt`** (`com.habitbell.app.audio`) was established as the single, authoritative voice engine across all voice cues in Habit Bell. It implements `OnInitListener` and `DualCueSpeaker`, acting as a thread-safe process singleton accessible via `CentralSessionHandler.voiceEngine` and injected into `HoldTimerManager`, `PranayamaVoiceGuide`, `PreparationVoiceGuide`, and `SuryaVoicePlayer`.

```
                                  +-----------------------------+
                                  |     CentralSessionHandler   |
                                  +--------------+--------------+
                                                 | process singleton
                                                 v
+------------------------------------------------------------------------------------------------+
|                                    UnifiedVoiceEngine                                          |
|  - Single android.speech.tts.TextToSpeech hardware handle (hi-IN-SwaraNeural, +52Hz, 0.85f)    |
|  - Shared android.media.MediaPlayer pipeline with 120ms anti-startle delay & 350ms ducking     |
|  - Implements DualCueSpeaker (speak, stop, release) for HoldTimerEngine FSM                    |
+---------+----------------------------+-----------------------------+---------------------------+
          |                            |                             |                           |
          v                            v                             v                           v
  [1. Pranayama]             [2. Prep Countdown]           [3. Surya Namaskar]          [4. Hold Timer]
  - Purak / Kumbhak /        - T-5s "Take position"        - 12 Solar Mantras           - Round ordinal cues
    Rechak / Bahya           - T-3s, 2s, 1s numbers        - Asana flow cues            - Kumbhak / Hold
  - Tri-Bandha cues          - Option C strikes sync       - Asana durations            - Rechak / Rest
  - <6s auto-fallback        - Silence guard (mic)         - Dual speech modes          - Second ticks aloud
```

### 3.2 Master Vocal Profile Standard
- **Acoustic Profile**: Natural high-frequency female swara (`pitch: +52Hz` / ~1.16f multiplier), unhurried meditative cadence (`0.75x–0.85x`), and whisper-soft default gain (`0.52f`).
- **Tonal Identity**: Modelled after Bollywood singing legend **Lata Mangeshkar** (`hi-IN-SwaraNeural` voice locale with graceful, soothing cadence).
- **Anti-Startle Lead Delay**: Every spoken vocal instruction or studio audio asset enforces a **120ms lead delay** after ambient music ducking commences, allowing ambient drones to soften before voice entry.
- **Dynamic Raised-Cosine S-Curve Ducking**: Automatically attenuates background music to $0.20\text{f}$ over 350ms via `BackgroundMusicManager.duckVolume()`, restoring it to unity over 500ms via `restoreVolume()` upon phrase completion.

### 3.3 Four Functional Vocal Domains Supported by the Unified Engine

#### Domain 1: Classical Hatha Yoga Pranayama
- **Option 1 (Only Sanskrit)**: Traditional Sanskrit sacred cues (`R.raw.pranayama_purak_sanskrit`, `R.raw.pranayama_kumbhak_sanskrit`, `R.raw.pranayama_rechak_sanskrit`). Both internal (*Antar*) and external (*Bahya*) retention phases share the melodious *Kumbhak* cue.
- **Option 2 (Sanskrit + English)**: Bilingual guidance (`R.raw.pranayama_purak_bilingual`, `R.raw.pranayama_kumbhak_bilingual`, `R.raw.pranayama_rechak_bilingual`).
- **Step Timing vs. Voice Timing Law (< 6s Accommodation Guard)**: When a phase duration cannot fully accommodate the unhurried bilingual cue (< 6 seconds, e.g. 4s Purak step), Option 2 automatically falls back to the clean, single-language Sanskrit cue (`R.raw.pranayama_purak_sanskrit`, ~2.2s). Steps with duration $\ge 6$s enjoy full bilingual guidance.
- **Tri-Bandha Guidance**: Whispers *"Kumbhak... Tri-Bandha"* during internal breath retention when enabled in settings.

#### Domain 2: Pre-Session Preparation Countdown
- **T = 5s**: Articulates *"Take your position"* (`R.raw.prep_take_position`).
- **T = 3s, 2s, 1s**: Pronounces *"Three"* (`R.raw.prep_three`), *"Two"* (`R.raw.prep_two`), and *"One"* (`R.raw.prep_one`), paired with Option C tingsha strikes.
- **Strict Acoustic Silence Guard**: When `isAcousticCalibrationActive` is true (Breathwork / Mantra Counter in acoustic mic mode), all voice numbers and chimes are strictly suppressed to guarantee clean environmental noise profiling.

#### Domain 3: Sūrya Namaskār Subsystem
- Synthesizes 12 sacred solar mantras (`ॐ मित्राय नमः...`, `ॐ रवये नमः...`) and posture guidance matching the identical Swara acoustic profile.
- Standalone generator script: `python3 scripts/generate_surya_namaskar_voice.py`.

#### Domain 4: Voice-Driven Yoga / Physiotherapy Hold Timer
- **Phase Transition Cues**:
  - **Hold Commencement**: Announces spoken round ordinal ("First", "Second", "Third"...) followed by phase cue.
    - Sanskrit: *"First... कुम्भक"* (or `R.raw.pranayama_kumbhak_sanskrit` asset).
    - Bilingual: *"First Round... कुम्भक Hold"* (or `R.raw.pranayama_kumbhak_bilingual` asset).
    - English: *"First Round... Hold"*.
  - **Rest Commencement**: Announces recovery rest.
    - Sanskrit: *"रेचक... विश्राम"* (or `R.raw.pranayama_rechak_sanskrit`).
    - Bilingual: *"रेचक... Rest"* (or `R.raw.pranayama_rechak_bilingual`).
    - English: *"Rest"*.
- **Count Aloud Each Second**: When enabled (`isCountAloudEnabled`), articulates countdown numbers ("one", "two", "three"...) on each second tick.
- **Auditioning from Settings Drawer**: `UnifiedVoiceEngine.auditionHoldCue(style, volume, speed)` allows practitioners to audition cues directly inside `HoldTimerSettingsSheet`.

### 3.4 Future Developer & AI Agent Usage Guide
To reuse the voice engine in any new timer, mindfulness practice, or subsystem:
```kotlin
// 1. Obtain engine instance from CentralSessionHandler
val voiceEngine = sessionHandler.voiceEngine

// 2. Play a high-definition studio-mastered audio asset with 120ms anti-startle delay and ducking:
voiceEngine.playMasteredAudio(R.raw.pranayama_purak_sanskrit, volume = 0.52f)

// 3. Synthesize natural speech with the master Lata Swara profile and ducking:
voiceEngine.speakWithDucking(
    text = "Exhale slowly through the nose",
    speedMultiplier = 0.85f,
    volume = 0.52f
)

// 4. Inject as DualCueSpeaker into any countdown or state machine:
val speaker: DualCueSpeaker = voiceEngine
speaker.speak("Hold posture steady", 0.85f)
```

---

## 4. Peripheral & Bluetooth Disconnection Auto-Pause Subsystem (`BluetoothAudioDisconnectionManager.kt`)

- **Industry Media Player Parity**: Replicates standard Android media playback conventions (e.g. Spotify, YouTube Music, Audible) where external audio output disconnection immediately pauses playback, protecting users against sudden acoustic exposure through the mobile device's speaker.
- **Multi-Vector Disconnect Detection**:
  - **`AudioDeviceCallback` (API 26+)**: Registered on `AudioManager` to intercept endpoint hardware removals for `TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`, `TYPE_BLE_BROADCAST`, `TYPE_HEARING_AID`, and wired/USB headsets. Delivers unconditional hardware disconnect detection even when no sound is currently playing during a silent rest interval.
  - **`ACTION_AUDIO_BECOMING_NOISY` BroadcastReceiver**: Registered dynamically during `SessionStatus.RUNNING` with `ContextCompat.RECEIVER_EXPORTED` on Android 13+ to catch instantaneous system audio route flips from external peripherals to phone speakers.
  - **Automotive Host Lifecycle**: Bound to `HabitBellCarSession.onDestroy()` via `onCarDisconnected()` to guarantee running in-car sessions safely pause when leaving the vehicle.
- **Monotonic Debounce Guard**: Employs a 1000ms (`DEBOUNCE_THRESHOLD_MS`) gate via `SystemClock.elapsedRealtime()` to cleanly throttle near-simultaneous callback and broadcast dispatches.
- **Lifecycle & Power Optimization**: Active hardware and broadcast listeners are registered strictly during `SessionStatus.RUNNING` and unregistered upon pause, completion, or idle to ensure zero background battery drain.
- **Zero-Permission Privacy & Settings Integration**: Operates without requiring dangerous `BLUETOOTH_CONNECT` runtime permissions. Configurable via **Settings Drawer > Global Config** (`isPauseOnBluetoothDisconnect`, default `true`).

---

## 5. Pre-Session Preparation Countdown & Voice Guidance Subsystem (`PreparationVoiceGuide.kt`)

- **Mindful Transition Architecture**:
  - Eliminates the cognitive rush and abruptness of immediate timer starts by providing an unhurried, 5-second lead-in countdown (`5..4..3..2..1`) before any mindful session commences.
  - **Physical Posture Preparation**: Affords users ample time to place or lay down their smartphone, adjust cushions, settle posture, and align their breath before active timing and bell tracking begins.
- **Vocal & Acoustic Choreography**:
  - **At T = 5s**: Articulates soothing vocal cue *"Take your position"* (`R.raw.prep_take_position`) synthesized via the project-standard melodious female voice (`hi-IN-SwaraNeural`, `+52Hz` pitch, Lata Mangeshkar profile), prompting user posture alignment.
  - **At T = 3s, 2s, 1s (Non-Acoustic Sessions)**: Pronounces distinct, unhurried numeric vocal cues *"Three"* (`R.raw.prep_three`), *"Two"* (`R.raw.prep_two`), and *"One"* (`R.raw.prep_one`), each paired simultaneously with an Option C crystalline tingsha cymbal strike (`AudioBellManager.playCountdownStrike(secondsRemaining)`).
  - **At T = 3s, 2s, 1s (Acoustic Mic Mode — Strict Acoustic Silence Protocol)**: When `isAcousticCalibrationActive` is true (Breathwork or Mantra Counter in `ACOUSTIC_MIC` mode), countdown chime strikes and vocal numbers are strictly silenced to preserve 100% acoustic stillness for the 3-second ambient room noise calibration.
  - **At T = 0s**: Preparation concludes automatically, transitioning the engine to `SessionStatus.RUNNING`. For acoustic sessions, active tracking begins immediately with the baseline noise floor pre-locked, bypassing opening bells to prevent false triggers; for linear non-acoustic timers, triggers the opening interval bell chime to inaugurate the practice.
  - **Background Music Ducking & Anti-Startle Delay**: Background music is smoothly ducked to 20% gain over 350ms, followed by a 120ms anti-startle acoustic settle delay before vocal cue playback. Restores volume smoothly over 500ms upon phrase completion.
  - **Multi-Engine Fallback**: If `MediaPlayer` encounters an audio server error, automatically degrades gracefully to Android native `TextToSpeech`.
- **Bypass & Resumption Semantics**:
  - **Start Now / Skip**: An explicit "Start Now" action button on `SessionScreen` allows users to bypass remaining countdown seconds and initiate the session immediately.
  - **Pause/Resume Idempotence**: Resuming an existing paused session directly re-enters `SessionStatus.RUNNING` without re-triggering the 5-second preparation countdown.
  - **User Configurable Toggle**: Can be enabled or disabled globally via **Settings Drawer > Global Config** (`is_prep_countdown_enabled`, default `true`).

---

## 6. Direct Hardware & Connected Device Volume Synchronization Subsystem (Requirement E7)

- **Problem Statement & Cognitive Duality Resolution**:
  - In legacy audio architectures, apps typically apply an internal software gain multiplier (`MediaPlayer.setVolume` or YouTube IFrame volume) completely decoupled from Android's hardware stream volume (`AudioManager.STREAM_MUSIC`) or the TV's Google Cast device volume (`CastSession.setVolume`).
  - This created severe user confusion due to multiplicative attenuation (e.g. 30% in-app gain $\times$ 30% system volume $= 9\%$ effective output) and lack of feedback when using physical phone volume rocker keys or TV remote controls.
- **Unified Hardware Volume Law**:
  - The in-app Ambient Volume slider is architected to directly inspect and govern the physical hardware volume of the **active audio endpoint**:
  - **In Mobile App Mode**:
    - **`SystemVolumeObserver.kt` Subsystem**: Registers a lifecycle-aware `ContentObserver` on Android's `Settings.System.CONTENT_URI` listening for changes to `AudioManager.STREAM_MUSIC`.
    - **Bi-Directional Hardware Synchronization**:
      - Dragging the in-app slider directly invokes `audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)`, adjusting device media output without intrusive system HUD overlays.
      - Pressing the smartphone's physical volume rocker buttons, Bluetooth headset buttons, or steering wheel volume knobs triggers `ContentObserver.onChange()`, instantly updating `SystemVolumeObserver.volume` and moving the in-app Compose slider in real-time.
  - **In Chromecast Mode**:
    - **`HabitBellCastManager.kt` Volume Integration**: Binds `Cast.Listener` to the active `CastSession` and implements `onVolumeChanged()`.
    - **Bi-Directional TV Remote Synchronization**:
      - Dragging the in-app slider calls `castManager.setDeviceVolume(clamped)` which invokes `CastSession.setVolume(double)` and broadcasts custom JSON volume telemetry (`{"type":"volume", "volume": volume}`) across `urn:x-cast:com.habitbell.cast` to the living room TV.
      - Adjusting the TV volume using the physical TV remote, Google TV remote, or Google Home app triggers `Cast.Listener.onVolumeChanged()`, which updates `HabitBellCastManager.castVolume` and updates the in-app slider dynamically.
- **Dynamic Mode-Switching & Reactive Flow Topology**:
  - `HabitBellViewModel.kt` utilizes Kotlin Coroutines `castManager.isCasting.collectLatest`:
    - When `isCasting == true`, it cancels mobile observation and collects `castManager.castVolume`, initializing the slider to the TV's current volume level.
    - When `isCasting == false`, it cancels Cast observation and collects `systemVolumeObserver.volume`, initializing the slider to the phone's current media volume.
- **Lazy Lifecycle Scoping & Battery Conservation**:
  - To prevent unnecessary OS-wide event dispatching and maximize device battery longevity during hours-long workout or meditation sessions, continuous observation is strictly scoped to active UI presentation:
    - `isVolumeUiActive = (isVolumeSheetOpen || isSettingsDrawerOpen) && isAppForeground`.
    - **Dormant by Default**: `SystemVolumeObserver` does NOT register a `ContentObserver` upon instantiation or while running sessions with closed settings.
    - **Activation on UI Reveal**: When the user opens the Volume Settings Sheet or Settings Drawer while the app is in the foreground:
      1. `register()` attaches the `ContentObserver` to Android's `Settings.System.CONTENT_URI`.
      2. `readCurrentNormalizedVolume()` immediately synchronizes the slider state to the exact current stream level before the first frame renders.
      3. Real-time collection mirrors any physical phone rocker button presses or Bluetooth headset adjustments onto the slider.
    - **Suspension on Dismiss or Background**: Dismissing the sheet/drawer or backgrounding the activity (`MainActivity.onStop`) immediately invokes `unregister()`, cleanly severing the `ContentObserver`.
    - **Acoustic Continuity**: Because the audio engine plays at unity gain ($1.0\text{f}$) directly into Android's `STREAM_MUSIC` hardware bus, physical phone buttons or car knobs continue to govern acoustic output volume natively without requiring active app observation.
- **Unity Gain & Unimpaired Voice Guidance Ducking (`BackgroundMusicManager.kt`)**:
  - Internal player output gain defaults to unity ($1.0\text{f}$), delegating master acoustic attenuation entirely to the phone or TV hardware.
  - During voice guidance cues (e.g., Pranayama breath pacing or pre-session preparation countdown), `duckVolume(0.20f, 350L)` attenuates internal gain to $0.20\text{f}$ and restores it to $1.0\text{f}$ upon completion, guaranteeing crystal-clear vocal clarity without modifying the user's master system volume setting.
- **Context-Aware Presentation Layer (`SettingsDrawer.kt`)**:
  - The UI dynamically labels the volume section and displays the active target device:
    - TV Mode: `"TV Volume (<Device Name>)"` with `"Directly controlling connected TV master volume"`.
    - Mobile Mode: `"Phone Media Volume"` with `"Directly controlling mobile device media volume"`.
