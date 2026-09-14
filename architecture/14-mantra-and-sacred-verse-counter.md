# 14. Mantra, Japa & Sacred Verse Counter Subsystem

This document details the telemetry-based, bead-tracking acoustic recitation counter engine (`com.habitbell.app.mantra`) supporting Gayatri Mantra, Maha Mrityunjaya, Aumkar Drone, Ram Naam Japa, Islamic Tasbih / Dhikr, Christian Jesus Prayer, and Universal Scripture recitations.

---

## 1. Architectural Role & Decoupling from Breath Counting

- **Autonomous Telemetry Subsystem**: While classical Pranayama (Section 10) executes rigid pacing counts (e.g. 4:16:8:16 Visama Vritti) and the Fast-Paced Breath Counter (Section 12) tracks rapid mechanical respiratory bursts (Kapalabhati/Bhastrika 60–120 BPM), sacred mantra recitation requires an **autonomous bead-counting acoustic telemetry engine**.
- **Multi-Tradition Support**: Provides first-class support for diverse spiritual traditions across a unified 108-bead / 100-bead / 33-knot architecture:
  1. **Vedic & Hindu**: Gayatri Mantra (गायत्री), Maha Mrityunjaya (महामृत्युंजय), Praṇava Aumkar (ॐकार), Ram Naam Japa (श्री राम).
  2. **Islamic**: Tasbih / Dhikr (سبحان الله • الحمد لله • الله أكبر - 33/33/34 beads).
  3. **Christian**: Jesus Prayer / Contemplative Prayer Rope (Chotki - 33/100 knots, "Lord Jesus Christ, have mercy on me").
  4. **Universal / Inter-Faith**: Universal Scripture & Sacred Verse with user-customizable timing envelopes.
- **Preservation Invariant**: The existing guided Pranayama and Fast-Paced Breath Counter subsystems remain 100% intact, active, and unchanged.

---

## 2. The Core Acoustic Problem & Intra-Verse Pause Bridging

### The Multi-Line Sloka Problem
Traditional mantras and sacred verses (e.g., the four-line Gayatri Mantra or the four-pada Maha Mrityunjaya) take 8 to 18 seconds to recite. Practitioners naturally pause for 0.3 to 1.2 seconds between lines (padas) to catch a breath. If processed by a standard acoustic energy detector, each breath pause would prematurely trigger a count or reset the duration tracker, erroneously registering 4 separate counts for a single verse recitation.

### The Mathematical Solution: Intra-Verse Pause Bridging
The subsystem introduces an **Intra-Verse Pause Bridging State Machine**:
1. **Cumulative Speech Duration Accumulation ($T_{\text{vocal}}$)**:
   While vocal formant energy exceeds the dynamic threshold ($E > \text{Threshold}$), speech duration continuously accumulates into $T_{\text{vocal}}$.
2. **Breath Pause Bridging ($T_{\text{silence}} < T_{\text{bridge}}$)**:
   When vocal energy drops (user breathes between verse lines), an intra-verse timer tracks the silence interval. If $T_{\text{silence}} < 1.2\text{s}$ (the bridge tolerance), the silence is seamlessly bridged, maintaining the active verse state without resetting $T_{\text{vocal}}$.
3. **Verse Confirmation & Completion Gate**:
   A completed recitation is confirmed and exactly **1 bead** is emitted if and only if:
   $$T_{\text{vocal}} \ge T_{\min} \quad \text{AND} \quad T_{\text{silence}} \ge T_{\text{inter\_pause}}$$
   Where:
   - For Gayatri Mantra: $T_{\min} = 6.5\text{s}$, $T_{\text{inter\_pause}} = 1.6\text{s}$.
   - For Maha Mrityunjaya: $T_{\min} = 7.5\text{s}$, $T_{\text{inter\_pause}} = 1.7\text{s}$.
   - For Universal Scripture: $T_{\min} = 5.0\text{s}$, $T_{\text{inter\_pause}} = 1.5\text{s}$.
4. **Short Burst Rejection**:
   Spoken coughs, throat clearings, or single words ($T_{\text{vocal}} < T_{\min}$) followed by silence are silently discarded without advancing beads.

---

## 3. The 3 Acoustic Recitation Modalities (`MantraMode`)

1. **`EXTENDED_VERSE`** (Gayatri, Maha Mrityunjaya, Universal):
   - Formant bandpass filtered ($150\text{ Hz} - 2500\text{ Hz}$).
   - Intra-verse pause bridging accumulator.
   - Requires $T_{\text{vocal}} \ge 6.5\text{s} - 12.0\text{s}$ followed by inter-verse pause $\ge 1.6\text{s}$.
2. **`SHORT_JAPA`** (Ram Naam Japa, Islamic Tasbih, Jesus Prayer):
   - Fast rhythmic cadence tracking (30 to 120 CPM).
   - High-speed peak attack-decay hysteresis envelope (120ms to 800ms per chant).
   - Minimum refractory lockout ($280\text{ms}$) preventing double counts on prolonged syllables.
3. **`AUMKAR_DRONE`** (Praṇava Oṃkāra):
   - Normalized Autocorrelation Fundamental Pitch Tracking ($80\text{ Hz} - 250\text{ Hz}$).
   - Detects sustained harmonic resonance of vocal cords during deep "A-U-M" chanting.
   - Emits 1 bead upon release of sustained chant exceeding $2.2\text{s}$.

---

## 4. Domain Models & Telemetry Payload

- **`MantraTechnique`**: Enum defining `GAYATRI_MANTRA`, `MAHA_MRITYUNJAYA`, `AUMKAR`, `RAM_JAPA`, `TASBIH_DHIKR`, `JESUS_PRAYER`, `UNIVERSAL_VERSE`. Carries native script, canonical Roman names, default beads, timing envelopes, and spiritual descriptions.
- **`MantraMode`**: Enum defining `EXTENDED_VERSE`, `SHORT_JAPA`, `AUMKAR_DRONE`.
- **`MantraInputSourceType`**: Tri-state input modality: `ACOUSTIC_MIC` (hands-free microphone DSP), `MANUAL_BEAD_TAP` (tactile touch pad), and `SIMULATED` (deterministic automated testing).
- **`MantraInputEvent`**: Telemetry packet emitted from providers: `beadDelta`, `instantaneousCadenceCpm`, `audioAmplitudeRms`, `thresholdRms`, `isSpeechActive`, `activeVerseDurationSeconds`.
- **`MantraUpdate`**: Immutable reactive state holding:
  - `currentBead`, `targetBeads` (e.g. 54 of 108)
  - `currentMala`, `targetMalas` (e.g. Mala 1 of 3)
  - `totalSessionChants`, `cadenceCpm`
  - `audioAmplitudeRms`, `thresholdRms`, `isReciting`
  - `activeVerseDurationSeconds`
  - `isCompleted`
- **`MantraCounterConfig`**: Configuration model embedded in `TimerProfile`, specifying technique, target beads (108, 100, 54, 33, 21), target Malas, min verse duration, pause thresholds, input source, haptic toggles, and mic sensitivity.

---

## 5. Pluggable Data Sources (`MantraDataSource`)

### 1. `AcousticMantraSensorProvider.kt` (Hands-Free Acoustic DSP)
- **Audio Record Pipeline**: Low-latency 16 kHz 16-bit Mono `AudioRecord` buffer running on background coroutine (`Dispatchers.IO`).
- **2nd-Order Biquad Voice Bandpass Filter**: Center frequency $f_c = 800\text{ Hz}$, $Q = 0.5$, passband 150 Hz – 2500 Hz. Captures natural human speech formants (vowels, resonance) while rejecting low-frequency room rumble (60/100 Hz attenuated by >15 dB) and high-frequency mic hiss (7.5 kHz attenuated by >14 dB).
- **Dynamic Noise Floor Adaptation**: Tracks ambient room noise ($\alpha = 0.12$ fall, $\alpha = 0.02$ rise), establishing dynamic vocal threshold:
  $$\text{Threshold} = \left(\text{NoiseFloor} \times \frac{2.2}{\text{Sensitivity}} + \frac{0.002}{\text{Sensitivity}}\right).\text{coerceIn}(0.002f, 0.20f)$$
- **Ambient Acoustic Noise Floor Calibration (3-Second Silent Profiling Window)**:
  Before active recitation begins in `ACOUSTIC_MIC` mode, `MantraCountManager` executes an initial 3-second silent pause (90 frames $\times 32\text{ms} \approx 2.88\text{s}$) to measure stationary room background sound (AC blowers, ceiling fans, wind, leaves, mic noise floor).
  - **Outlier Rejection**: Rejects sharp transient spikes ($RMS > 0.20f$ bandpass or $> 0.30f$ raw) such as table taps or dropped mala beads.
  - **Dynamic Baseline Locking**: Computes steady ambient noise floor baseline and locks the speech formant trigger threshold before japa/verse detection begins.
  - **Gated Bead Emission**: Prevents phantom bead triggers during the initial settling period while continuously streaming live amplitude/threshold metrics to the UI.
- **Autocorrelation Pitch Estimator**:
  $$R(\text{lag}) = \frac{\sum_{i} x[i] \cdot x[i + \text{lag}]}{\sqrt{\sum x[i]^2 \cdot \sum x[i+\text{lag}]^2}}$$
  Searches lags between $64$ and $200$ samples at 16 kHz ($80\text{ Hz} - 250\text{ Hz}$). Successfully validates fundamental Aum frequency (e.g., 136.1 Hz Vedic C#).
- **Protective Acoustic Blanking**: `blankAcousticDetection(3500L)` on milestone chime and `blankAcousticDetection(8000L)` on completion temple gong prevents acoustic feedback from triggering false beads.

### 2. `ManualTapMantraProvider.kt` (Tactile Virtual Mala)
- Interactive full-screen touch pad enabling counting in silent rooms, transit, or when mic permissions are withheld.
- Rolling Chants Per Minute (CPM) calculation using a 6-tap sliding-window ring buffer based on monotonic `SystemClock.elapsedRealtime()`.

### 3. `SimulatedMantraProvider.kt` (Synthetic Telemetry)
- Deterministic synthetic generator for Compose previews and automated unit tests.

---

## 6. The Central Orchestrator (`MantraCountManager.kt`)

- Coordinates between providers and emits authoritative `MantraUpdate` StateFlow.
- Manages bead progression from 1 to 108.
- Fires `onMilestoneReached` on the 54th bead (half-Mala milestone) to play a single tingsha chime and distinct dual-pulse haptic.
- Advances Mala rounds upon reaching 108, triggering `onSessionCompleted` with a resonance temple gong and long haptic wave when the final Mala finishes.

---

## 7. Full-Screen Visual Biofeedback UI (`MantraCounterContent.kt`)

- **Circular Mala Canvas (108 Beads)**:
  - Polar coordinate projection renders 108 equidistant beads around a circular trajectory.
  - Meru (Guru) bead prominently anchored at the top ($\theta = -\pi/2$) with a distinct teardrop geometry.
  - Completed beads illuminate with golden/primary aura; pending beads remain serene subtle beads.
- **Central Biofeedback Ripple Halo**:
  - Live audio amplitude dynamically drives radial ripple rings expanding from center.
- **Active Verse Duration Stopwatch**:
  - Displays live recitation duration (e.g. `11.4s`) with dynamic color transitions:
    - Dim / grey during line pauses.
    - Vibrant gold / primary while vocalizing.
- **Recitation Cadence Badge**:
  - Real-time Chants Per Minute (`CPM`) display.
- **Full-Screen Touch Tap Pad**:
  - In `MANUAL_BEAD_TAP` mode, the entire canvas acts as a tactile virtual Mala surface with instant tactile click feedback.

---

## 8. Settings & Configuration (`MantraCounterSettingsSheet.kt`)

Embedded directly inside `SettingsDrawer.kt` when `profile.mantraConfig != null`:
1. **Sacred Technique Selection**: Instant switching between Gayatri, Maha Mrityunjaya, Aumkar, Ram Japa, Tasbih, Jesus Prayer, and Universal Verse.
2. **Mala Bead Goals**: Quick presets (108, 100, 54, 33, 21) and steppers.
3. **Verse Duration Envelopes**: Configurable min vocal duration ($T_{\min}$) and completion pause ($T_{\text{pause}}$).
4. **Acoustic Sensor Sensitivity**: Gentle (0.7x), Balanced (1.0x), Sensitive (1.5x), Whisper (2.2x).
5. **Sensory Feedback**: Toggles for tactile bead clicks and milestone bells.
6. **Ambient Accompaniment**: Tanpura drone, Aum soundscape, or YouTube stream volume slider.

---

## 9. Verification & Automated Test Suite (`MantraCounterEngineTest.kt`)

- `testMantraCounterDefaultProfiles`: Verifies all 7 canonical techniques, default bead targets, timing envelopes, and preference preservation.
- `testExistingProfilesIsolation`: Confirms strict non-regression across guided Pranayama, Surya Namaskar, Mindful Walking, and Eating profiles.
- `testMantraUpdateMathAndFormatting`: Verifies progress fractions, half-Mala detection, and UI strings.
- `testManualTapMantraProviderCadence`: Validates touch bead delta increments.
- `testMantraCountManagerBeadProgressionAndMilestones`: Confirms half-Mala milestone trigger (at 54) and completion gong trigger (at 108).
- `testVoiceBandpassFilterFrequencyResponse`: Confirms passband transmission at 800 Hz and >15 dB attenuation of room rumble (60 Hz) and sensor hiss (7500 Hz).
- `testAutocorrelationPitchEstimationForAumkar`: Confirms pitch detection accuracy on synthetic 136.1 Hz Aumkar tone within ±4 Hz.
- `testTimerSessionStateWithMantraCounter`: Validates end-to-end integration into `TimerSessionState`.

---

## 10. Autonomous Engineering Invariants

1. **Zero Battery Waste**: AudioRecord captures at 16 kHz mono only while the session is actively running; completely paused/released in background or IDLE states.
2. **100% Offline Edge Execution**: Zero external cloud dependencies; all DSP bandpass filtering, noise floor tracking, and autocorrelation run entirely on-device on `Dispatchers.IO`.
3. **Strict Zero-Regression Guarantee**: Classical guided Pranayama and Fast-Paced Breath Counter remain completely unchanged and untangled.

---

## 11. Profile Initialization & Lifecycle Routing Invariants

1. **Explicit Engine State Hydration**:
   In `TimerEngine.loadProfile(profile)`, `if (profile.isMantraCountingEnabled)` is evaluated before timer typology branching. The engine immediately initializes `TimerSessionState` with `status = SessionStatus.IDLE`, `currentRound = 1`, `totalRounds = targetMalas`, and a non-null default `mantraUpdate = MantraUpdate(...)`. This eliminates stale profile retention (preventing Mindful Eating or Pranayama fallback when launching Mantra Counter).
2. **Progression Tick Decoupling**:
   In `TimerEngine.tickOneSecond()`, sessions with `profile.isMantraCountingEnabled` are intercepted immediately after `isBreathCountingEnabled`. Countdown and round progression are governed autonomously by `MantraCountManager`, preventing accidental dispatch into `tickPranayama()`.
3. **Defensive Presentation Routing**:
   In `SessionScreen.kt` (both Portrait and Landscape layouts), multi-interval dispatch checks `sessionState.isMantraCountingActive` directly, provisioning an instant non-null fallback `MantraUpdate` if the reactive flow hasn't emitted its first event. This strictly prevents the UI from falling through to `PranayamaPortraitContent`.
4. **Home Screen Intent Categorization & Badging**:
   In `ModernHomeScreenSample.kt`, the "Meditation" intent category includes `isMantraCountingEnabled`. Profiles resolve the dedicated Phosphor sparkle icon, `"SACRED JAPA"` subtitle, and bead badge (`"${targetBeads}b"`).

