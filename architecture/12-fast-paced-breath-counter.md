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
- **Continuous Dynamic Ambient Noise Floor Tracking**: Continuously adapts background noise floor in real time during calm idle state ($\alpha = 0.12$ quick fall, $\alpha = 0.02$ gentle rise). Dynamic stroke threshold scales:
  $$\text{Threshold} = \left(\text{NoiseFloor} \times \frac{1.8}{\text{Sensitivity}} + \frac{0.007}{\text{Sensitivity}}\right)$$
- **3-Stage Hysteresis State Machine**:
  - `IDLE_LISTENING`: Enforces 180ms minimum refractory interval (~333 BPM ceiling). Triggers attack when bandpass energy exceeds calibrated threshold.
  - `ATTACK_DETECTED`: Tracks local peak energy; validates physiological burst duration (20ms – 220ms). Aborts sustained noise (>220ms without decay) to prevent false runaway counting. Confirms exactly 1 stroke upon peak decay ($E < E_{\text{peak}} \times 0.75$).
  - `COOLDOWN_VALLEY`: Enforces 150ms minimum valley check; transitions back to `IDLE_LISTENING` once signal drops below $90\%$ threshold or after 240ms safety timeout without corrupting stroke timestamps.
- **Acoustic Self-Feedback Mitigation & Clean Sensing**:
  - Decoupled speaker audio during hands-free `ACOUSTIC_MIC` mode: stroke feedback is delivered exclusively through tactile micro-haptics (`HapticManager.triggerStrokeHaptic()`) and live screen ripple canvas biofeedback. This prevents the phone's 2048 Hz metallic tingsha chime from reverberating into the microphone and deafening the detector for 2.3 seconds.
  - Audible stroke chimes are safely reserved for `MANUAL_TAP` mode where the microphone is inactive.
  - `blankDetection(2000L)` temporarily mutes acoustic evaluation during Kumbhaka retention bells and voice prompts.
  - Interactive Sensitivity Selector chips (`Low 0.7x`, `Med 1.0x`, `High 1.5x`) and live real-time RMS needle gauge rendered on `BreathCounterContent.kt`.
- **Technique-Specific Digital Signal Processing (DSP)**:
  - **Kapalabhati**: 2000 Hz Biquad bandpass filter + 3-stage hysteresis state machine tuned for 20ms–220ms passive-active abdominal recoil expulsions.
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
