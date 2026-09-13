# 07. Data Persistence & Presets

This document details Habit Bell's local data storage, repository pattern, domain models, and curated presets catalog.

---

## 1. Data & Persistence Layer (`com.habitbell.app.data`)

- **`TimerRepository.kt`**: Clean repository managing persistence via encrypted / standard `SharedPreferences` serialized as JSON.
- **Reactive State Flow**: In-memory caching ensures instantaneous reactivity across the UI layer and platform background services.
- **Domain Models**:
  - `TimerProfile`: Core aggregate defining duration, interval, bell pattern, sound style, and custom metadata.
  - `PranayamaConfig`: 4-phase breathing cycle specifications.
  - `CompoundConfig`: Sequence of pose definitions, durations, and transition sounds.
  - `RoutineReminder`: Scheduled daily habit reminders.

---

## 2. Curated Default Profiles Catalog (`DefaultProfiles.kt`)

Exposes an unmodifiable curated list of 9 core wellness presets (`ALL_PRESETS`) designed for immediate, distraction-free practice across Mobile, Living Room Cast, and Android Auto:

1. **`PRANAYAMA_HATHA` ("Pranayama (Hatha Yoga)", `pranayama-hatha-classical`)**: Classical 4-phase breathwork engine with blooming lotus visualization, ratio stages (1:1:1:1 Box Breath, 1:4:2:4 Visama Vritti, etc.), and Tri-Bandha guidance.
2. **`BREATH_COUNTER` ("Breathwork Counter", `kriya-breath-counter`)**: Unified acoustic and tap breath tracking profile consolidating Kapalabhati, Bhastrika, and Bhramari with dynamic in-profile technique switching, customizable round counts, strokes per round, Antar Kumbhaka retention holds, and recovery rest pacing.
3. **`EATING` ("Mindful Eating", `eating-mindful-20`)**: 20-minute silent eating session with 60-second bite-pacing interval chimes.
4. **`REIKI` ("Reiki Healing", `reiki-self-treatment-35`)**: 35-minute hand-position transitions with 3-minute gentle bells.
5. **`SURYA_NAMASKAR` ("Surya Namaskar (Sun Salutation)", `surya-namaskar-classical`)**: 12 classical cyclical asanas with mantra chants and synchronized poses.
6. **`MINDFUL_WALKING` ("Mindful Walking", `walking-meditation-15`)**: Paced walking habit with 120-second mindfulness cues.
7. **`STEP_WALK_MEDITATION` ("Step Walk Meditation (3k Steps)", `step-walk-meditation-3k`)**: Goal-oriented walking with hardware pedometer integration.
8. **`POWER_STEP_WALK` ("Power Step Walk (5k Steps)", `power-step-walk-5k`)**: High-cadence walking workout with real-time SPM cadence feedback.
9. **`HYDRATION` ("Hydration & Posture Reset", `hydration-posture-hourly`)**: 60-minute interval reminders for water and spinal posture realignment.

### Catalog De-Duplication & Unified Breathwork Consolidation
- **Removal of Redundant Standalones**: Standalone presets **Pranayama (Box Breath)** (`pranayama-box-breath`), **Pranayama (4-7-8 Deep Relax)** (`pranayama-478-relax`), and **Mindful Reading** (`mindful-reading-30`) have been excised. Box Breathing (Sama Vritti 1:1:1:1) is consolidated directly into the ratio stages of the canonical `PRANAYAMA_HATHA` engine.
- **Unified Breathwork Counter**: Standalone breath counting entries (`KAPALABHATI_COUNTER`, `BHASTRIKA_COUNTER`, `BHRAMARI_COUNTER`) are consolidated into a single unified `BREATH_COUNTER` preset with seamless in-profile technique selection.
- **Backwards Compatibility**: Backward-compatible accessor aliases (`DefaultProfiles.KAPALABHATI_COUNTER`, `BHASTRIKA_COUNTER`, `BHRAMARI_COUNTER`) and legacy ID hydration in `TimerRepository.getProfileById` resolve seamlessly to `BREATH_COUNTER` configured with the corresponding technique.
- **Mantra Counter Scope Separation**: In accordance with user design directives, Aumkar chanting is intentionally excluded from the breath counter and reserved for an upcoming independent mantra counter engine.
