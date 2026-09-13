# 09. Settings & Display Automation

This document details the dual-domain settings architecture, dedicated bottom sheets for volume and TV casting, and the sensor-fused display automation subsystem.

---

## 1. Dual-Domain Settings Architecture (`SettingsDrawer.kt`)

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

### Domain 1: Dynamic Timer Settings (`SettingsDrawerTab.TIMER`)
- **Adaptive Session Targets**:
  - **Walking & Movement Profiles** (`isStepTrackingEnabled == true`): Displays target Step Goal chips (`None`, `1,000`, `2,000`, `3,000`, `5,000` steps) with automated completion evaluation.
  - **Linear Timers** (`TimerType.LINEAR`): Displays continuous session duration slider ($1\text{m}..60\text{m}$), fine stepper buttons (`-1m`, `+1m`, `+5m`), and instant preset chips (`10m`, `15m`, `20m`, etc.).
- **Adaptive Interval Pacing Cues**:
  - Automatically switches between Step Interval cadence (`None`, `250`, `500`, `1,000` steps) for physical locomotion vs. Periodic Interval bell chips (`None`, `15s`, `30s`, `1m`, `2m`, `3m`) for linear countdowns.
- **Per-Timer Ambient Soundscape Selection**:
  - Contextual toggle enabling/disabling continuous soundscapes per profile.
  - Source selection between bundled 432Hz Aum loop, sandboxed ad-free YouTube audio stream, or local audio file via Storage Access Framework (SAF).
  - **De-duplicated Audio Surface**: Redundant volume sliders have been cleanly excised from Timer Settings, Pranayama Settings, and Surya Namaskar Sheets, routing all gain management exclusively through the unified Master Audio Gain controls in Global Config.

### Domain 2: Persistent Global Configuration & Bifurcation (Requirement E5)
To eliminate vertical scroll clutter and reduce cognitive load, Global App Configuration has been logically bifurcated into three distinct, non-scroll-heavy panels managed via a segmented category chip selector (`GlobalSettingsCategory`):

- **Quick Action Navigation Tiles**: Prominently pinned at the top of Global Config for 1-tap direct navigation to:
  - **Volume & Audio Settings** (Requirement E6): Opens the dedicated volume sheet.
  - **Living Room & TV Casting** (Requirement E8): Opens the dedicated TV casting & screen mirroring sheet.
- **Category 1: Theme & Display (`GlobalSettingsCategory.THEME_DISPLAY`)**:
  - **Zen Focus (Do Not Disturb)**: Suppresses distracting system notifications during active mindfulness sessions.
  - **Sun-Moon Circadian Mode with Blue-Light Attenuation**:
    - **Sun (Day Mode)**: Blue-light-reduced warm parchment palette (`#FAF6EE` background, gentle amber `#D97706` accents) preventing ocular fatigue without harsh blue spectrum emissions.
    - **Moon (Night Mode)**: Circadian wind-down palette featuring warm amber tones on deep charcoal (`#16130F`) or pure `#000000` AMOLED.
    - 1-tap Sun ☀️ ⇄ Moon 🌙 toggle plus granular theme selection (`AMOLED`, `EYE_COMFORT`, `DARK`, `LIGHT`).
  - **Display Management**: Display Awake (`FLAG_KEEP_SCREEN_ON`) and Auto-Dimming during countdown rest periods.
- **Category 2: Sensors & Health (`GlobalSettingsCategory.SENSORS_HEALTH`)**:
  - Centralized step provider bridges (`Hardware Sensor`, `Health Connect`, `Apple Health Bridge`, `Step Simulator`).
  - Runtime sensor permission status indicators and Android `ACTIVITY_RECOGNITION` permission launcher.
  - Synthetic step injection debugging tool (`+250 Steps`).
- **Category 3: Automation & Battery (`GlobalSettingsCategory.AUTOMATION_BATTERY`)**:
  - Proximity-driven AMOLED Pocket Mode blanking.
  - Bluetooth Disconnect Auto-Pause toggle for peripheral wireless headphones.
  - 5-Second Preparation Countdown toggle (`is_prep_countdown_enabled`).
  - About Habit Bell operating system version info.

---

## 2. Dedicated Volume Controller Subsystem (Requirement E6)

Global volume controls are completely decoupled from the main settings drawer into a dedicated, persistent bottom sheet (`VolumeSettingsSheet.kt`) that is **always available at the top right corner** across all application screens (`ModernHomeScreenSample`, `SessionScreen` in portrait and landscape):

- **Decoupled Bell Controls**:
  - **Interval Bell Volume Slider** ($0\%..100\%$, `intervalVolume`): Governs intermediate pacing cues (Option C triple bell cadence, countdown lead-in strikes, Pranayama phase cues). Includes an immediate `[▶ Test Option C]` audition button.
  - **Completion Gong Volume Slider** ($0\%..100\%$, `bellVolume`): Governs the end-of-session resonant Temple Gong chime. Includes an immediate `[▶ Test Gong]` audition button.
- **Global Ambient Soundscape Engine**:
  - **Master Soundscape Toggle (`isBgMusicEnabled`)**: 1-tap master switch to silence or enable background sound across all timer sessions.
  - **Direct Hardware / TV Volume Synchronization (Requirement E7)**: Displays active audio endpoint with real-time bi-directional synchronization:
    - TV Mode (`isCasting == true`): Synchronizes directly with connected TV master volume via `HabitBellCastManager` and `CastSession.setVolume`.
    - Phone Mode (`isCasting == false`): Synchronizes directly with device media stream (`AudioManager.STREAM_MUSIC`) via `SystemVolumeObserver`.
  - **Soundscape Strategy Selector**: Select between ॐ Continuous Aum drone (`DEFAULT_AUM`), YouTube audio link stream (`YOUTUBE_LINK`), or Custom local audio file (`CUSTOM_FILE`) via Storage Access Framework (SAF).
  - **Ambient Audition Button**: Real-time `[▶ Test Ambient Sound]` / `[⏹ Stop Ambient Sound]` preview button.

---

## 3. Decoupled Living Room & TV Casting Subsystem (Requirement E8)

Google Cast and Miracast Screen Mirroring are completely decoupled from global settings into a dedicated, persistent bottom sheet (`CastMirroringSheet.kt`) directly accessible via top-right TV action buttons:

- **Native Google Cast Discovery**: Integrates AndroidX `MediaRouteButton` wrapped via `CastButton` for native Cast framework discovery (Chromecast, Google TV, Android TV) without third-party browser hops.
- **Miracast Screen Mirroring Controls**: Dedicated master toggle, external display name indicator, and dynamic target orientation modes (`Auto`, `Vertical (Portrait)`, `Horizontal (Landscape)`, and 1-tap `Rotate Screen ⇄`).
- **Smart TV Browser Connectivity**: Local embedded HTTP playback link (`tvCastUrl`) with 1-tap clipboard copy and QR launch code for web browsers on Samsung Tizen, LG webOS, and Apple TV.

---

## 4. Permanent Signature Acoustic Identity (Zero Timbre Configuration)

- **Brand Sound Integrity**: All user-facing chime timbre selection dropdowns/chips (`Tingsha`, `Singing Bowl`, `Temple Gong`, `Crystal Quartz`) are intentionally removed.
- **Acoustic Enforcement**:
  - **Separator (Interval) Bell**: Exclusively configured to the **Option C Triple Bell** ($2048\text{ Hz} \rightarrow 1536\text{ Hz} \rightarrow 1024\text{ Hz}$) — an acoustically distinct, non-startling mindful pacing cue.
  - **Session Completion**: Exclusively configured to the deep resonant **Temple Gong** ($130.8\text{ Hz}$) — grounding, full-bodied resolution.
- **Dedicated Audition Card**: Provides zero-configuration sample buttons (`[▶ Separator Bell]`, `[▶ End Gong]`, `[⏱ 10s Demo]`) allowing users to familiarize themselves with the separator cue before commencing practice.

---

## 5. Unified Display Automation Subsystem (`DisplayAutomationManager.kt`, `DisplayAutomationOverlay.kt`)

The Unified Display Automation Subsystem orchestrates intelligent screen power state management, peripheral awareness, and touch/lift interactions across 4 distinct contextual environments.

### 1. Core Architectural Role & Hardware Sensor Fusion
Managed directly by `CentralSessionHandler`, `DisplayAutomationManager` coordinates low-power continuous hardware sensors:
- **Optical Proximity Sensor (`Sensor.TYPE_PROXIMITY`)**: Detects physical obstruction within $< 5\text{ cm}$ of the front bezel receiver.
- **Ambient Light Sensor (`Sensor.TYPE_LIGHT`)**: Measures surrounding illuminance in lux ($\text{lx}$). Pocket classification requires $< 10.0\text{ lux}$ to prevent false-positives under bright external illumination.
- **3-Axis Gravity Sensor (`Sensor.TYPE_GRAVITY` / `TYPE_ACCELEROMETER`)**: Isolates Earth's gravitational acceleration vector ($9.81\text{ m/s}^2$).
  - **Flat Surface Detection**: When resting flat face-up on a tabletop, $z \ge 8.8\text{ m/s}^2$, $|x| < 3.0\text{ m/s}^2$, and $|y| < 3.0\text{ m/s}^2$.
  - **Lift & Tilt Detection**: When tilted toward the user, $z < 7.5\text{ m/s}^2$ and $|y| > 3.5\text{ m/s}^2$, or acceleration vector jerk delta $\Delta a = |\vec{a}_{t} - \vec{a}_{t-1}| > 1.2\text{ m/s}^2$.
- **Significant Motion Hardware Trigger (`Sensor.TYPE_SIGNIFICANT_MOTION`)**: Low-power hardware interrupt that fires instantly upon physical pickup without CPU polling.

### 2. AMOLED Zero-Power Blackout Curtain (Option A Implementation)
- **Power Optimization**: Rendered at the root window hierarchy in `MainActivity` via `DisplayAutomationOverlay`. On OLED/AMOLED panels, pure `#000000` pixels are completely de-energized ($0\text{ mW}$ emission penalty).
- **Frictionless Zero-Latency Wake**: Unlike standard Android keyguard screen locks (`FLAG_DISMISS_KEYGUARD`, system power manager locks), the blackout curtain avoids lockscreen friction, pin codes, and biometric fingerprint hurdles. A single tap anywhere on the screen or physical phone lift instantly lifts the curtain.
- **Haptic & Visual Badging**: Discreet contextual badges (Golden Lock, Car HUD, Smart TV, or Smart Watch icon) rendered with low-luminance accents to communicate active mode without disrupting nighttime dark adaptation.

### 3. Contextual Environmental Priority Hierarchy
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

### 4. Flat Inactivity Timeout (10-Second Grace Period)
- When the phone is resting flat on a surface during external display sessions (Car, TV, Watch) and the user taps the screen to adjust settings, a background coroutine timer begins a **10-second countdown** (`_inactivityCountdown: 10..1`).
- If no further touch interaction occurs for 10 seconds while the phone remains flat, the AMOLED blackout curtain smoothly re-engages.
- Physically lifting or tilting the phone immediately cancels the countdown and keeps the display awake until placed down flat.

### 5. Lift-to-Wake State Machine & Pocket Mode Dismissal
- **Zero-Friction Physical Lift**: When the phone is lifted from a flat surface or taken out of a pocket/bag, `onDeviceMovedOrLifted()` is triggered via 3-axis gravity vector analysis ($z < 7.5\text{ m/s}^2, |y| > 3.5\text{ m/s}^2$) or acceleration jerk delta ($\Delta a > 1.2\text{ m/s}^2$).
- **Multi-Mode Curtain Clearance**: In `DisplayAutomationManager.combine(_isPickedUp)`, all active curtain modes—including `DisplayCurtainMode.POCKET`, `TV_CAST`, `WATCH`, and `CAR_HUD`—are immediately deactivated (`isActive = false`), restoring full mobile visibility without requiring unlock or pin gestures.
- **Manual Override Clearing**: Physical movement or lift automatically resets `_isManualPocket = false`, preventing persistent blackouts.
- **Temporary Wake Grace Period**: User touch or lift sets `_isTemporarilyAwake = true`, ensuring that `evaluatePocketMode()` does not re-blank the display even if optical sensors remain transiently shaded.
