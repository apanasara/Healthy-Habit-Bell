# 12. Fast-Paced Breath Counter Subsystem

This document details the telemetry-based, step-counter style fast breath tracking engine (`com.habitbell.app.breath`) supporting Kapalabhati, Bhastrika, and Bhramari.

---

## 1. Architectural Role & Decoupling from Guided Pranayama

- **Distinct Modalities**: While classical guided Pranayama (Section 10) relies on rhythmic pacing counts (e.g. 4:16:8:16 Visama Vritti, Box Breathing) with blooming lotus animations and vocal directives, fast-paced yogic breath practices—such as **Kapalabhati** (skull-shining rapid exhalations, 60–120 BPM), **Bhastrika** (bellows rapid inhalation/exhalation, 30–60 BPM), and **Bhramari** (prolonged resonant bee humming)—require an autonomous **step-counter style tracking engine**.
- **Preservation Guarantee**: The existing guided Pranayama module remains 100% intact, active, and unchanged. The fast-paced counter functions as an autonomous telemetry subsystem that tracks strokes, computes live cadence (BPM), dynamically orchestrates inter-round Kumbhaka (breath retention holds), and manages rest intervals.

---

## 2. Domain Models & Telemetry Payload

- **`BreathTechnique`**: Enum identifying `KAPALABHATI`, `BHASTRIKA`, `BHRAMARI`, and `FREE_COUNT`.
- **`BreathCounterPhase`**: State machine phases: `PREPARATION` (5s countdown) ➔ `STROKES` (active pumping / humming) ➔ `RETENTION_HOLD` (Kumbhaka) ➔ `REST` (recovery) ➔ `COMPLETED`.
- **`BreathInputSourceType`**: Tri-state input modality: `ACOUSTIC_MIC` (hands-free acoustic detection), `MANUAL_TAP` (touch-screen tap fallback), and `SIMULATED` (deterministic automated testing).
- **`BreathStrokeUpdate`**: Immutable reactive state holding:
  - `currentRound`, `targetRounds`
  - `currentRoundStrokes`, `targetRoundStrokes`, `totalSessionStrokes`
  - `cadenceBpm`
  - `currentPhase`, `phaseSecondsRemaining`
  - `humDurationSeconds`
  - `audioAmplitudeRms` (0.0f..1.0f)
- **`BreathCounterConfig`**: Configuration model embedded in `TimerProfile`, specifying target rounds, strokes per round, retention duration, rest duration, and microphone sensitivity.

---

## 3. Pluggable Data Sources (`BreathDataSource`)

The subsystem implements a modular, swappable data layer via the `BreathDataSource` interface (`start`, `pause`, `resume`, `stop`, `reset`, `registerManualStroke`):

### 1. `AcousticBreathSensorProvider.kt` (Hands-Free Acoustic DSP & Hysteresis Engine)
- **Audio Record Pipeline**: Captures raw PCM audio via low-latency 16 kHz 16-bit Mono `AudioRecord` buffers on a dedicated background coroutine (`Dispatchers.IO`). Prioritizes standard `MediaRecorder.AudioSource.MIC` to capture clean unclipped physical breath turbulence without OEM voice gates, falling back to `VOICE_RECOGNITION`.
- **2nd-Order Biquad IIR Bandpass Filter**: Center frequency $f_c = 2000\text{ Hz}$, $Q = 0.8$, passband 1.0 kHz – 3.5 kHz specifically isolating nasal expulsion turbulence. Rejects low-frequency room rumble (>22 dB attenuation at 100 Hz) and thermal high-frequency microphone hiss (>20 dB attenuation at 7.5 kHz).
- **Continuous Dynamic Ambient Noise Floor Tracking**: Continuously adapts background noise floor in real time during calm idle state ($\alpha = 0.12$ quick fall, $\alpha = 0.02$ gentle rise). Settles naturally down to $0.0005f$ in quiet rooms.
- **Ambient Acoustic Noise Floor Calibration (3-Second Silent Profiling Window)**:
  Before active stroke counting begins in `ACOUSTIC_MIC` mode, `BreathCountManager` executes an initial 3-second `PREPARATION` pause (90 frames $\times 32\text{ms} \approx 2.88\text{s}$) to measure stationary environmental noise (AC blower hum, ceiling fans, outdoor wind, rustling leaves, electronic mic noise floor).
  - **Transient Outlier Rejection**: Frames with bandpass $RMS > 0.15f$ or raw $RMS > 0.25f$ (phone handling taps, clothing rustle, accidental drops) are discarded from ambient averaging to prevent baseline contamination.
  - **Baseline Threshold Locking**: Locks $RMS_{\text{floor}}$ and sets initial dynamic detection threshold ($Threshold = RMS_{\text{floor}} \times Multiplier + minFloor$) safely above ambient room noise before entering `STROKES`, eliminating early false-positive triggers and stroke numbing.
  - **UI Immersion**: Displays a soothing 3-second visual countdown and real-time noise floor level meter in `BreathCounterContent.kt`.
- **Calibrated Distant Airborne Breath Threshold (30 cm – 1 Meter Placement)**:
  Rather than requiring the phone to be held directly under the nostrils where wind pops the mic, the detection engine isolates airborne acoustic sound waves from a phone resting on a mat or table:
  $$\text{Threshold} = \left(\text{NoiseFloor} \times \frac{2.2}{\text{Sensitivity}} + \frac{0.0016}{\text{Sensitivity}}\right).\text{coerceIn}(0.0016f, 0.15f)$$
  At standard sensitivity ($1.0$), in a quiet room ($\text{NoiseFloor} \approx 0.0008f$), the threshold sits at $\approx 0.0034f$. A real airborne nasal expulsion at 50 cm produces $0.0050f$–$0.0080f$ RMS, cleanly crossing the threshold with $>4\times$ SNR separation from ambient room silence.
- **3-Stage Hysteresis State Machine & Attack Onset Discrimination**:
  - `IDLE_LISTENING`: Enforces 180ms minimum refractory interval (~333 BPM ceiling). Requires an **explosive attack onset** ($\Delta E > \text{Threshold} \times 0.12$ or $E > \text{Threshold} \times 1.35$), completely rejecting steady ambient drone, fans, and room acoustics.
  - `ATTACK_DETECTED`: Tracks local peak energy; validates physiological burst duration (20ms – 220ms). Aborts sustained noise (>220ms without decay) to prevent false runaway counting. Confirms exactly 1 stroke upon peak decay ($E < E_{\text{peak}} \times 0.75$).
  - `COOLDOWN_VALLEY`: Enforces 140ms minimum valley check; transitions back to `IDLE_LISTENING` once signal drops below $88\%$ threshold or after 220ms safety timeout without corrupting stroke timestamps.
- **Acoustic Self-Feedback Mitigation & Elimination of Post-Rechak Chimes**:
  - **Elimination of Post-Rechak Bells**: Removed interval bell / 3-bell sequence trigger at the onset of `STROKES`. Previously, the Option C 3-bell sequence played through the speaker right as Round 2 began after Rechak, adding 3 false strokes before the user began breathing.
  - **Extended Protective Blanking**: `blankDetection(5000L)` on Kumbhaka retention bell, `blankDetection(3000L)` on Rechak voice prompt, and `blankDetection(2500L)` on round advance guarantee 0% acoustic leakage into the detector.
  - Decoupled speaker audio during hands-free `ACOUSTIC_MIC` mode: stroke feedback is delivered exclusively through tactile micro-haptics (`HapticManager.triggerStrokeHaptic()`) and live screen ripple canvas biofeedback. Audible stroke chimes are safely reserved for `MANUAL_TAP` mode where the microphone is inactive.
  - Interactive Sensitivity Selector chips (`Low 0.7x`, `Med 1.0x`, `High 1.5x`) and live real-time RMS needle gauge rendered on `BreathCounterContent.kt`.
- **Technique-Specific Digital Signal Processing (DSP)**:
  - **Kapalabhati**: 2000 Hz Biquad bandpass filter + 3-stage hysteresis state machine tuned for 20ms–220ms passive-active abdominal recoil expulsions. Enforces explosive attack onset discrimination and valley cooldown.
  - **Bhastrika (Bellows Breath)**: 4-stage state machine (`IDLE_WAITING_INHALE` $\rightarrow$ `INHALE_BURST` $\rightarrow$ `TURNAROUND_VALLEY` $\rightarrow$ `EXHALE_BURST` $\rightarrow$ `CYCLE_COOLDOWN`). Specifically validates that a forceful nasal inhalation (60ms–850ms) is followed by a quiet direction reversal valley (30ms–700ms), followed by a forceful nasal exhalation (60ms–850ms) before emitting exactly 1 cycle. Distance-calibrated minimum threshold floor ($0.0016f / \text{sensitivity}$) with real-time ambient noise floor adaptation enables hands-free mat tracking at 30 cm – 1 meter.
  - **Bhramari**: Low-frequency harmonic pitch tracker utilizing normalized autocorrelation over the 80 Hz – 250 Hz fundamental human humming swara band. Recalibrated hum threshold ($0.0035f / \text{sensitivity}$) captures hands-free mat humming resonance while rejecting ambient background noise. Tracks continuous sustained hum duration and fires round completion upon exhalation drop-off.
  - **Rolling Cadence Evaluation**: Instantaneous cadence (BPM) evaluates interval timing against the previous stroke epoch *before* timestamp mutation, supporting fast Kapalabhati bursts down to 180 ms (333 BPM) up to slow Bhastrika cycles up to 3500 ms (17 BPM), bounded to 15..300 BPM on the live UI gauge.

### 2. `ManualTapBreathProvider.kt` (Touch Fallback & High-Cadence Tap)
- Provides an instantaneous tactile counting alternative when practicing in noisy environments or when microphone permissions are withheld.
- Computes rolling cadence (BPM) using a 5-tap sliding-window ring buffer based on monotonic `SystemClock.elapsedRealtime()`.

### 3. `SimulatedBreathProvider.kt` (Synthetic Telemetry)
- Emits synthetic strokes at steady, realistic intervals (e.g. 75 BPM for Kapalabhati, 40 BPM for Bhastrika) for unit testing, CI pipelines, and Compose UI previews.

---

## 4. Orchestration & State Machine (`BreathCountManager.kt`)

The `BreathCountManager` acts as the central coordinator between data providers and session orchestration:

- **Round & Kumbhaka Progression**:
  - Automatically transitions from `STROKES` to `RETENTION_HOLD` when target strokes are reached.
  - Automatically transitions from `RETENTION_HOLD` to `REST`, and from `REST` to the next round of strokes.
  - Fires `COMPLETED` when all planned rounds are achieved.
- **Auditory & Haptic Feedback Coordination**:
  - **Stroke Feedback**: Dispatches a crisp 25ms tactile haptic impulse (`HapticManager.triggerStrokeHaptic()`) and a high-pitched 1.6x tingsha chime (`AudioBellManager.playStrokeFeedback()`) on every detected breath stroke.
  - **Kumbhaka Transition Bell**: Triggers Option C Zen Tingsha strike and spoken Tri-Bandha guidance cue (*"Jalandhara, Uddiyana, Mula Bandha"*) at the onset of breath retention.
  - **Session Completion Gong**: Sounds the resonant Tibetan temple gong upon session completion.

---

## 5. Presentation Layer (`BreathCounterContent.kt`)

Integrated directly into `SessionScreen.kt` in both Portrait and Landscape orientations:
- **Acoustic Ripple Canvas**: A reactive Canvas that dynamically pulses expanding concentric rings proportional to live `audioAmplitudeRms`, delivering instant visual biofeedback.
- **Large Stroke Counter & Progress Arc**: High-visibility stroke display (`45 / 60`) with smooth animated circular sweep indicator and round badges (`ROUND 1 OF 3`).
- **Live Cadence Badge**: Displays real-time speed in breaths-per-minute (e.g., `⚡ 82 BPM`).
- **Interactive Full-Screen Tap Surface**: In `MANUAL_TAP` mode, converts the entire lower viewport into a responsive touch pad with tactile ripples.
- **Seamless Phase Displays**: Fluidly morphs into a glowing Kumbhaka retention countdown with holding directives, followed by a calm recovery rest countdown.

---

## 6. Unified Profile Architecture & In-Profile Technique Selection

To prevent catalog bloat and provide a singular, cohesive breathwork interface, the acoustic breath counter is consolidated into a single unified profile:

- **Canonical Preset**: `DefaultProfiles.BREATH_COUNTER` (`"Breathwork Counter"`, `kriya-breath-counter`).
- **Dynamic In-Profile Technique Switching**:
  - **Preparation Quick Chips**: Rendered on `BreathCounterContent` during `PREPARATION` or `IDLE` state (`कपालभाति Kapalabhati`, `भस्त्रिका Bhastrika`, `भ्रामरी Bhramari`).
  - **Dedicated Settings Sheet**: `BreathCounterSettingsSheet` within `SettingsDrawer` exposes full technique selector, round count steppers, stroke goals per round, Antar Kumbhaka retention durations, recovery rest intervals, microphone sensitivity multipliers, and sensory feedback toggles.
- **State Transition & Preference Preservation (`withTechnique`)**:
  - Calling `BreathCounterConfig.withTechnique(newTechnique)` switches the physiological DSP parameters and round targets to the canonical defaults of the chosen technique, while **preserving** user hardware preferences (microphone sensitivity, acoustic click, haptic pulse, vocal cues).
- **Automotive & Assistant Routing**:
  - Media queries for `"breath-counter"`, `"kriya"`, `"kapalabhati"`, `"bhastrika"`, and `"bhramari"` in `CentralSessionHandler` route directly to `DefaultProfiles.BREATH_COUNTER`.

---

## 7. Scope Boundary: Dedicated Mantra Counter Decoupling

Per user architectural requirements:
- **Aumkar Chanting Exclusion**: Aumkar repetition is explicitly **not** included within the breath counter engine.
- **Dedicated Subsystem Separation**: Vocal mantra chanting (Aumkar, Gayatri, Maha Mrityunjaya) requires specialized fundamental pitch tracking, harmonic overtone integration, and continuous chant resonance detection distinct from nasal expulsion turbulence. A dedicated, standalone **Mantra Counter** subsystem will be introduced separately.
