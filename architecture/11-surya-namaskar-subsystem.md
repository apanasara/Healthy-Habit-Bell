# 11. Sūrya Namaskār Subsystem

This document details the data persistence, animated vector visualization, studio-mastered voice guidance, companion Wear OS sync, and live timer reflection pipeline for classical Surya Namaskar practice.

---

## 1. Room Database Architecture (`SuryaDatabase.kt`)

- **Single Source of Local Schema**: Implements an offline-first SQLite database managed via AndroidX Room (`androidx.room:room-runtime:2.6.1`, `room-ktx`, with `ksp` compiler).
- **Process Singleton Pattern**: Thread-safe initialization using double-checked locking in `SuryaDatabase.getInstance(context)`. Configured with `fallbackToDestructiveMigration()` for streamlined schema evolution.
- **Relational Entity Model**:
  - `StepEntity`: Stored under table `"steps"`. Models individual postures with `@PrimaryKey(autoGenerate = true) val id: Long`, `name`, `orderIdx`, `isEnabled`, `voiceCueMode: VoiceCueMode`, `audioCue: String`, `mantraEnabled: Boolean`, `assetRef: String`, and timing fields (`durationSeconds`, `repetition`, `puraka`, `kumbhaka`, `rekha`).
  - `PresetEntity`: Stored under table `"presets"`. Maps preset identifiers (`"slow"`, `"moderate"`, `"fast"`) to JSON-serialized duration configurations.
  - `SettingEntity`: Stored under table `"settings"`. Key-value store for user-configured audio styles and companion sync preferences.
- **Type Converters (`SuryaTypeConverters`)**: Serializes non-primitive enum types like `VoiceCueMode` (`NONE(0)`, `PRANIC(1)`, `STEP_NAME(2)`, `SLOKA(3)`) to/from integer columns.
- **Data Access Objects (DAOs)**:
  - `StepDao`: Exposes reactive `getAllSteps(): Flow<List<StepEntity>>`, `getStepById(id)`, `insert()`, `update()`, and `delete()`.
  - `PresetDao`: CRUD access for speed preset entities.
  - `SettingDao`: Key-value query and mutation with reactive `getAllSettings(): Flow<List<SettingEntity>>`.

---

## 2. Presentation Layer, Settings Sheet & Animated Silhouette Visuals (`SuryaTimerScreen.kt`, `SettingsDrawer.kt`, `AnimatedPoseView.kt`)

- **Reactive UI Flow**: `SuryaTimerViewModel` exposes `steps: StateFlow<List<StepUiModel>>` mapped directly from `StepDao.getAllSteps()`. On initial database creation, `SuryaDatabase.seedIfEmpty(context)` automatically seeds all 12 classical postures from `DefaultProfiles.SURYA_NAMASKAR`.
- **Dedicated Settings Drawer Integration (`SuryaSettingsSheet`)**:
  - Embedded within `SettingsDrawer.kt` when opening settings for `TimerType.COMPOUND` or profiles named "Surya Namaskar".
  - **4 Speed Presets**: Slow 10s, Moderate 5s, Fast 3s, and Custom [X]s. Selecting Custom reveals a dedicated pace editor with `-1s` / `+1s` micro-steppers and quick chips (`4s`, `7s`, `8s`, `12s`, `15s`).
  - **Target Practice Rounds**: Interactive steppers (`-1`, `+1`) and quick-select chips (`3`, `5`, `12`, `24`, `108` rounds), displaying total asanas count (e.g. 12 rounds = 144 asanas).
  - **Global Voice Guidance Modes**: Single-tap global selector across all 12 steps for `Asana Name`, `Solar Mantra`, `Breath Flow`, and `Silent / Bell`.
  - **12 Posture Sequence Preview**: Illustrated list displaying the sequence of postures with duration tags and vector silhouette artwork (`R.drawable.avd_yoga_pranamasana`).
  - **Fullscreen Editor Transition**: Direct button navigation to `AppScreen.SURYA_TIMER` (`SuryaTimerScreen`) for modifying per-posture Sanskrit voice cues, durations, and solar mantra audio playback.
  - **Companion Watch Synchronization**: Quick-sync button invoking `SuryaSyncManager.pushSyncToWatch()` to send current sequence configuration to Wear OS devices over the Wearable Data Layer API.
  - **Background Ambient Soundscape**: Toggle and source selectors (ॐ Aum drone, YouTube audio, custom audio file) with real-time volume slider.
  - **Signature Acoustic Identity**: Audition buttons for Option C separator chime, Tibetan Singing Bowl / Temple Gong, and quick 10-second demo session.
- **Animated Silhouette Rendering (`AnimatedPoseView.kt`)**:
  - Bridges Android's native `AnimatedVectorDrawable` (`avd_yoga_pranamasana.xml`) into Jetpack Compose via `AndroidView` with `ImageView`.
  - Employs `(drawable as? Animatable)?.start()` inside `DisposableEffect` for lifecycle-aware, zero-leak entrance animations.
  - Two concurrent visual animations: 12dp vertical rise translation (`translateY` 12dp → 0dp over 800ms) paired with progressive fill opacity (`fillAlpha` 0.0 → 1.0 over 600ms) with quadratic deceleration.

---

## 3. Companion Wear OS Synchronization (`SuryaSyncManager.kt`)

- **Wearable Data Layer Client**: Pushes serialized timer configuration payloads across the Google Play Services `Wearable.getDataClient(context)` bridge.
- **Payload Contract (`/surya_sync`)**: Packs step models, presets, and settings into a unified JSON descriptor via `JsonUtil` (Google Gson) transferred as an urgent `PutDataMapRequest`.
- **Zero-Latency Push**: Executed asynchronously on `Dispatchers.IO` when the user taps "Sync Watch" on the phone interface.

---

## 4. Audio Guidance & Studio Voice Engine (`SuryaVoicePlayer.kt` & `SuryaPoseAssets.kt`)

- **Studio-Mastered SwaraNeural Audio Profile**: Plays high-definition, studio-mastered audio files (`res/raw/surya_*.mp3`) synthesized with Microsoft Natural Neural voice `hi-IN-SwaraNeural` (+52Hz pitch, unhurried -30% yogic cadence, Lata Mangeshkar meditative timbre).
- **Dynamic Mode Cues**:
  - `VoiceCueMode.STEP_NAME`: Articulates classical Sanskrit Asana name with breathing guidance (e.g., "प्रणामासन, Inhale and Exhale", "हस्तउत्तानासन, Inhale").
  - `VoiceCueMode.SLOKA`: Chants the respective classical Solar Mantra followed by the Asana (e.g. "ॐ मित्राय नमः, प्रणामासन", "ॐ रवये नमः, हस्तउत्तानासन").
  - `VoiceCueMode.PRANIC`: Guides yogic breath flow and posture transitions with high-fidelity studio clips.
  - `VoiceCueMode.NONE`: Silent / Bell mode emitting no spoken cues, preserving meditative silence.
- **Anti-Startle Lead Delay & Raised-Cosine Ducking**: Inserts an anti-startle 120ms lead delay after smooth background music ducking (`duckVolume(duckedRatio = 0.20f, durationMs = 350L)`) before speech playback starts, and gently restores background audio over 500ms upon completion.
- **Anti-Clipping Step Duration Guard**: In accordance with the project's Step Timing vs. Voice Timing Law, if an allocated step duration is short (< 4 seconds, such as in the Fast 3s preset), bilingual cues automatically fall back to the concise Sanskrit solar mantra so the spoken audio is never clipped mid-sentence by the next transition.
- **Offline Android TextToSpeech Fallback**: Native Android `TextToSpeech` engine configured with sweet high-pitch (`1.28f`) and unhurried cadence (`0.75f`) acts strictly as a resilient offline fallback if a raw audio resource is unavailable.
- **Posture Vector Asset Catalog (`SuryaPoseAssets.kt`)**: Maps the 12 cyclical postures to their dedicated monochrome vector silhouettes (`yoga_pranamasana`, `yoga_hastauttanasana`, `yoga_padahastasana`, `yoga_ashwa_sanchalanasana`, `yoga_dandasana`, `yoga_ashtanga_namaskara`, `yoga_bhujangasana`, `yoga_parvatasana`) for accurate rendering in `SuryaTimerScreen` and `CompoundPoseCard`.

---

## 5. Live Timer Reflection & Bidirectional Persistence Pipeline

### Bidirectional Event Pipeline
```
SuryaSettingsSheet / SuryaTimerScreen
             |
             v (onUpdateSurya / syncStepsToEngineAndRepository)
     HabitBellViewModel.updateActiveSuryaSettings(...)
             |
             +--------------------------------------------+
             |                                            |
             v (writes SharedPreferences + Room)          v (live-reloads active profile)
     TimerRepository.updateSuryaSettings(...)      TimerEngine.loadProfile(updatedProfile)
             |                                            |
             v                                            v
   _profiles StateFlow                        StateFlow<TimerSessionState>
             |                                            |
             v                                            v
   HomeScreen & Drawer UI                      SessionScreen Active Countdown
                                               - Remaining Pose Seconds (${remainingSeconds}s)
                                               - Total Session Countdown (MM:SS)
                                               - Round Counter (ROUND X OF Y • POSE Z / 12)
                                               - Solar Mantra (☀️ ॐ मित्राय नमः)
```

### Real-Time Dynamic Recalculation
- Immediately recalculates total session duration: `totalDurationSeconds = targetRounds * poses.sumOf { it.durationSeconds }`.
- Re-evaluates pose countdown intervals and displays the active pose remaining seconds badge dynamically inside `CompoundPoseCard`.
- Seamlessly persists across application restarts via SharedPreferences key `"surya_config_v1"` and Room `StepDao` updates.
