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
- **Audio Record Pipeline**: Captures raw PCM audio via low-latency 16 kHz 16-bit Mono `AudioRecord` buffers on a dedicated background coroutine (`Dispatchers.IO`). Prioritizes `MediaRecorder.AudioSource.VOICE_RECOGNITION` to bypass aggressive OEM noise suppression/gating that clips breath turbulence, falling back to `MIC` if unavailable.
- **2nd-Order Biquad IIR Bandpass Filter**: Center frequency $f_c = 2400\text{ Hz}$, $Q = 1.0$, passband 1.2 kHz – 4.0 kHz isolating sharp nasal expulsion turbulence. Rejects low-frequency room rumble (>23 dB attenuation at 100 Hz) and thermal high-frequency microphone hiss (>20 dB attenuation at 7.5 kHz).
- **Continuous Dynamic Ambient Noise Floor Tracking**: Continuously adapts background noise floor in real time when in the calm idle state (quick downward tracking $\alpha = 0.12$, gentle upward tracking $\alpha = 0.02$). Dynamic stroke threshold dynamically scales:
  $$\text{Threshold} = \left(\text{NoiseFloor} \times \frac{2.4}{\text{Sensitivity}} + \frac{0.022}{\text{Sensitivity}}\right)$$
- **3-Stage Hysteresis State Machine**:
  - `IDLE_LISTENING`: Monitors for sharp energy rise onset ($\Delta E > \text{Threshold} \times 0.20$ or $E > \text{Threshold} \times 1.15$). Enforces 420ms minimum refractory lockout (142 BPM ceiling).
  - `ATTACK_DETECTED`: Tracks peak energy; validates physiological burst duration (35ms – 300ms). Confirms exactly 1 stroke upon peak decay ($E < E_{\text{peak}} \times 0.72$).
  - `COOLDOWN_VALLEY`: Mandates quiet passive inhalation valley drop ($E < \text{Threshold} \times 0.70$) before re-arming to `IDLE_LISTENING`, eliminating false double-triggering.
- **Acoustic Self-Feedback Mitigation & Blanking**:
  - `blankDetection(durationMs)` allows `CentralSessionHandler` to temporarily mute acoustic detection (320ms on stroke chime, 2000ms on phase transition cues) so speaker audio does not create an uncontrolled counting feedback loop.
  - Interactive Sensitivity Selector chips (`Low 0.7x`, `Med 1.0x`, `High 1.5x`) and live real-time RMS needle gauge rendered on `BreathCounterContent.kt`.
- **Technique-Specific Digital Signal Processing (DSP)**:
  - **Kapalabhati**: Biquad bandpass filter + 3-stage hysteresis state machine tuned for 35ms–300ms passive-active abdominal recoil expulsions.
  - **Bhastrika**: Dual-phase RMS energy peak detector capturing both forceful inhalation and sharp exhalation phases within a 650ms minimum bellows cycle envelope.
  - **Bhramari**: Low-frequency harmonic pitch tracker utilizing normalized autocorrelation over the 80 Hz – 250 Hz fundamental human humming swara band. Tracks continuous sustained hum duration and fires round completion upon exhalation drop-off.

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
