# 02. Session & Timer Engines

This document details the central orchestrator and deterministic timing core of Habit Bell: the `CentralSessionHandler` and the `TimerEngine`.

---

## 1. Central Session Handler (`CentralSessionHandler.kt`)

The `CentralSessionHandler` is the **process-level single source of truth** and authoritative orchestrator across the entire application runtime. It initializes and synchronizes the central `MediaSessionCompat` (`"HabitBellMediaSession"`), `TimerEngine`, `AudioBellManager`, `BackgroundMusicManager`, `BluetoothAudioDisconnectionManager`, `BatteryOptimizer`, and `HabitBellCastManager`.

### Multi-Surface Bidirectional Synchronization
The central session coordinates transport controls and metadata across **6 distinct control surfaces**:
1. **Automotive Head Unit (Android Auto)**: Media controls (play/pause/skip), progress scrubbers, and metadata displayed on the vehicle dashboard via `MediaSessionCompat`.
2. **Mobile Compose UI (`SessionScreen`)**: Real-time timer countdown, circular progress sweeps, dynamic breathing visualizers, and pocket mode.
3. **Android for Cars App Screen (`HabitBellCarScreen`)**: Template-based vehicle screen providing distraction-free timer selection and active session monitoring.
4. **Wear OS & Smartwatches**: Mirrored Android media session transport controls.
5. **Google Cast TV Receivers**: Cast receiver streaming session state, animated countdowns, and progress rings via the native Google Cast Framework.
6. **Smart TV Web Browsers**: Zero-cloud LAN web broadcast served by `LocalCastWebServer` on port `8888`.

### Profile Preparation vs. Active Commencement
`CentralSessionHandler` decouples session initialization from automatic execution:
- **`loadProfile(profile: TimerProfile)`**: Initializes `TimerEngine` with profile duration, intervals, and postures in `SessionStatus.IDLE` ("Ready") state and updates `MediaSessionCompat` metadata without starting the countdown ticker or audio. Invoked when landing on the timer screen from the Home screen.
- **`startProfile(profile: TimerProfile, skipPreparation: Boolean)`**: Invokes `loadProfile` and immediately calls `engine.startOrResume()`. Reserved for hands-free contexts such as Google Assistant voice actions and Android Auto direct selection.
- **`togglePlayPause()`**: When called from `SessionStatus.IDLE`, seamlessly resumes execution, triggering the 5-second preparation countdown (if enabled) or entering active countdown.

---

## 2. Timer Engine & State Machine (`TimerEngine.kt`)

The heartbeat of the mindfulness runtime is a deterministic finite state machine operating with five distinct lifecycle states:

```
[IDLE] ───> [PREPARING] ───> [RUNNING] <───> [PAUSED] ───> [COMPLETED]
  │               │              │                           │
  └───────────────┴──────────────┴───────────────────────────┘
                                 │
                            (* ➔ IDLE)
```

### State Transitions
- **`IDLE ➔ PREPARING`**: Triggered on `startOrResume()` when starting fresh and `isPreparationCountdownEnabled == true`. Launches 5-second lead-in countdown with vocal guidance.
- **`PREPARING ➔ RUNNING`**: Triggered automatically when `preparationSecondsRemaining == 0` or immediately when user invokes `skipPreparation()`. Rings opening bell chime and begins 1Hz active practice ticker.
- **`IDLE ➔ RUNNING`**: Direct transition when starting fresh with preparation disabled or skipped.
- **`RUNNING ⇄ PAUSED`**: Halts 1Hz ticker without resetting session progress. Resuming from `PAUSED` transitions directly to `RUNNING` without preparation delay.
- **`RUNNING ➔ COMPLETED`**: Triggered when `remainingSeconds == 0` or final round/pose finishes. Rings resonant temple completion gong.
- **`* ➔ IDLE`**: Reset or stop halts audio, releases wake locks, and clears preparation counters.

### Timer Topologies
1. **`LINEAR`**: Single duration countdown with customizable periodic interval chimes (e.g. Mindful Eating default 45m with 1m interval chime, Zen Meditation).
2. **`MULTI_INTERVAL`**: 4-phase cyclic Pranayama breathwork (`INHALE`, `HOLD_IN`, `EXHALE`, `HOLD_OUT`) with dynamic ratio scaling, dynamic 0-second step skipping via `activeSteps`, strict terminal boundary completion (zero Puraka spillover), and round counting.
3. **`COMPOUND`**: Multi-step sequencer iterating through distinct named poses (Yoga sequences, Reiki hand placements) with transition bells and terminal round boundary completion.

### Clock Manipulation & Sleep Skew Prevention
- Relies strictly on monotonic `SystemClock.elapsedRealtime()` calculations rather than wall-clock time (`System.currentTimeMillis()`) to protect against time drift, timezone updates, NTP adjustments, and device sleep states.

### Ambient Auto-Dimming & Pocket Mode Integration
- Integrated 10-second countdown for ambient screen dimming.
- **Pocket Mode**: Activated via proximity sensor or manual trigger. Automatically engages a pure `#000000` AMOLED power curtain (`PocketOverlay`) and switches chime feedback to a silent 3-pulse tactile vibration (`VibrationEffect`), keeping standard meditation silent.
