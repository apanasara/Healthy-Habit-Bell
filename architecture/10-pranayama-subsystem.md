# 10. Classical Hatha Yoga Pranayama Subsystem

This document details the authentic classical breathwork engine (`Chaturanga Pranayama`), physiological mechanics, procedural blooming lotus visualizer, and strict terminal boundary laws.

---

## 1. Classical Literature & Respiratory Physiology

The Pranayama subsystem implements classical yogic breath control (*Chaturanga Pranayama*) as documented in traditional Hatha Yoga literature (*Hatha Yoga Pradipika* by Swami Svatmarama, *Gheranda Samhita*, and *Patanjali Yoga Sutras*).

In *Hatha Yoga Pradipika* (HYP 2.2), Svatmarama establishes the inseparable link between breath and consciousness:
> *"When breath is still, the mind is still; the yogi achieves firmness, therefore one should restrain the breath."*

The practice regulates the four sacred limbs of the breath cycle:
1. **Puraka (पूरक - Inhalation)**: Conscious diaphragmatic intake drawing cosmic life force (*Prana*) into the torso.
2. **Antar Kumbhaka (अभ्यन्तर कुम्भक - Internal Retention)**: Preserving breath in full lungs, awakening the *Sushumna Nadi*, building internal pressure, and maximizing cellular oxygen diffusion. In accordance with classical Hatha Yoga (*HYP* 3.55-3.56), internal retention is practiced with **Tri-Bandha (त्रिबंध)**:
   - **Mūla Bandha (मूल बंध - Root Lock)**: Perineal/pelvic floor contraction (गुदा क्षेत्र या श्रोणि तल की मांसपेशियों का संकुचन) stimulating the parasympathetic pelvic splanchnic nerves and redirecting *Apana Vayu* upward into *Sushumna*.
   - **Madhyama Uḍḍīyāna Bandha (उड्डीयान बंध - Abdominal Lock)**: In *Antar Kumbhaka*, gentle inward engagement of the abdominal wall below the navel (पेट को अंदर एवं ऊपर की ओर खींचना) stabilizes intra-abdominal pressure against the descending diaphragm without compressing fully inflated lungs.
   - **Jālandhara Bandha (जालंधर बंध - Throat/Chin Lock)**: Placing the chin firmly into the jugular notch against the sternum/chest (ठोड़ी को कंठकूप / छाती से लगाना, *Kaṇṭha Kūpa*, *PYS* 3.30: *kaṇṭhakūpe kṣutpipāsānivṛttiḥ*). This mechanically stimulates the carotid sinus baroreceptors, triggering reflex vagal bradycardia that lowers heart rate, regulates intracranial arterial pressure during retention, and halts mental fluctuation.
3. **Rechaka (रेचक - Exhalation)**: Slow, prolonged exhalation expelling *Apana*, physical toxins, and mental tension.
4. **Bahya Kumbhaka (बाह्य कुम्भक - External Retention / Shunya Void)**: Resting in primordial emptiness between breaths, stimulating hypercapnic adaptation (CO₂ tolerance) and cerebral vasodilation (Bohr effect). In classical Hatha Yoga, **Tri-Bandha (त्रिबंध)** is also actively applied during Bahya Kumbhaka, where the diaphragm is naturally elevated into the thoracic cavity, facilitating full abdominal vacuum suction (*Pūrṇa Uḍḍīyāna Bandha* / पेट को अंदर एवं ऊपर खींचना), root seal (*Mūla Bandha* / श्रोणि तल संकुचन), and throat lock (*Jālandhara Bandha* / ठोड़ी को कंठकूप से लगाना).

Both retention phases unconditionally display the classical Devanagari guidance `त्रिबंध (मूल बंध • उड्डीयान बंध • जालंधर बंध)` along with English transliteration `Tri-Bandha: Mūla • Uḍḍīyāna • Jālandhara` positioned gracefully directly below the lotus flower and resting waterline, keeping the Upper HUD lean, spacious, and dedicated solely to phase nomenclature and the countdown numeral.

---

## 2. Proportional Ratio Stages & Visama Vritti Dynamics

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

---

## 3. Target Practice Rounds & Classical Yogic Stages

Grounded in *Hatha Yoga Pradipika* (2.12) & *Gheranda Samhita* (5.49):
- **12 Rounds (Adhama / Foundation)**: Default setting (~8 minutes 48 seconds at 4:16:8:16). Establishes foundational nadi cleansing and respiratory stability.
- **24 Rounds (Madhyama / Intermediate)**: Deepens metabolic down-regulation and prana circulation.
- **36 Rounds (Uttama / Advanced)**: Awaking Sushumna nadi and contemplative stillness.
- **Custom Steppers & Quick Chips**: Introduces 6, 12, 18, 24, 36 round presets with `-1`, `+1`, `+6` fine steppers (1 to 108 rounds).

---

## 4. Gentle Lady Voice Guidance Engine (`PranayamaVoiceGuide.kt`)

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

---

## 5. Meditative Milestone & Session Ending Bells

- **Milestone Interval Bell (Default: OFF)**:
  - Preserves deep *Dhyana* meditative absorption where absolute silence between rounds is vital.
  - Practitioner can toggle **ON** in settings with configurable cadence (Every 3, 5, 6, 10 rounds; default cadence: 5 rounds).
  - **Acoustic Design**: Calibrated to a gentle 432 Hz warm Tibetan singing bowl (`R.raw.tibetan_bell_interval` at soft 0.38f volume) with gradual mallet attack curve, engineered specifically to preserve meditative absorption without triggering the sympathetic startle reflex.
- **Session Completion Bell**: Deep resonant **Temple Gong** (`130.8 Hz`) strikes gracefully upon completing all rounds.
- **Pocket Mode Safeguard**: In Pocket Mode, audible chimes and voice guidance are replaced with distinct multi-pulse tactile haptic vibrations.

---

## 6. Classical Side-View Blooming Lotus, Dynamic Prana Aura & Landscape Layout (`BreathIndicator.kt`, `SessionScreen.kt`)

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

---

## 7. Dedicated Pranayama Settings Architecture (`SettingsDrawer.kt`)

When `profile.pranayamaConfig != null`, `SettingsDrawer` completely isolates the configuration surface into `PranayamaSettingsSheet`:
1. **Ratio Stages Dropdown**: Sama Vritti (1:1:1:1), Madhya (1:2:2:1), Visama Vritti (1:4:2:4), Gentle Void (1:4:2:1), Half Void (1:4:2:2), Custom.
2. **Base Inhale Scaling**: Quick 2s, 3s, 4s, 5s, 6s proportional recalculation chips.
3. **Four Phase Input Fields**: Direct numerical text entry and -1s, +1s, +4s steppers for Purak, Kumbhak (In), Rechak, Kumbhak (Out).
4. **Target Practice Rounds**: 12 rounds default (*Adhama* standard), with custom steppers and classic stage presets.
5. **Gentle Lady Voice Guide**: Option A (Sanskrit) default vs Option B (Bilingual) switch, dedicated Tri-Bandha voice cue toggle (`isTriBandhaVoiceEnabled`), and dynamic live audition button.
6. **Meditative Interval Bell**: Default OFF toggle, cadence selector, and 432 Hz audition button.
7. **Subtle Background Music**: Ambient sound toggle, Aum drone / YouTube / Custom file, and subtle volume slider.

---

## 8. Dynamic Step Skipping & Strict Terminal Completion Law (`PranayamaConfig.kt`, `TimerEngine.kt`)

- **Dynamic 0-Second Step Skipping (`activeSteps` Law)**:
  - In `PranayamaConfig.kt`, the computed property `val activeSteps: List<PranayamaStep>` dynamically filters `steps.filter { it.durationSeconds > 0 }.ifEmpty { steps }`.
  - Breathwork phases configured with `0s` duration (e.g., *Antar Kumbhaka* internal retention = 0s in beginner/anxiety-relief patterns, or *Bahya Kumbhaka* external retention = 0s in classical 3-phase breathwork) are completely bypassed during runtime execution.
  - Zero-second steps consume 0 clock ticks, trigger no audio bells, voice prompts, or haptic pulses, and never manifest as 0s ghost states on the visual HUD.
  - The canonical 4-element `steps` list (`[INHALE, HOLD_IN, EXHALE, HOLD_OUT]`) is strictly preserved for Room database entity serialization, Settings Drawer sliders, and proportional ratio stage recalculation.
- **Strict Terminal Boundary & Zero Puraka Spillover Law**:
  - In `TimerEngine.kt` (`tickPranayama()`), cycle completion is evaluated at the true terminal step of the active configuration: `isLastStepInRound = (pranayamaStepIndex >= activeSteps.size - 1)`.
  - When *Bahya Kumbhaka* is active (`durationSeconds > 0`), the cycle concludes on *Bahya Kumbhaka*; when *Bahya Kumbhaka* is 0s, the cycle cleanly concludes on *Rechaka* (Exhalation).
  - When the final active step completes on the terminal round (`pranayamaRound >= config.targetRounds`), `TimerEngine` immediately dispatches `onSessionCompleted()`.
  - State preservation: Does NOT wrap `pranayamaStepIndex` to 0, does NOT increment `pranayamaRound` beyond `targetRounds`, does NOT announce or transition into *Puraka*, and firmly pins `currentPranayamaPhase` to the final active phase (`HOLD_OUT` or `EXHALE`) with `phaseRemainingSeconds = 0` and `sessionState = SessionStatus.COMPLETED`.
  - Eliminates post-completion breath cycle overrun where the app previously lingered or jumped into *Puraka* after completing the configured number of rounds.
- **Compound Timer Completion Parity**:
  - The same deterministic terminal boundary logic is enforced in `tickCompound()` (`isLastPoseInRound` and `compoundRound >= config.targetRounds`), preventing wrap-around on posture sequences.
