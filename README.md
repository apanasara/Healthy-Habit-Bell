# Habit Bell — Wellness Operating System (Android)

Habit Bell is a distraction-free wellness timer ecosystem and personal wellness operating system engineered to help users practice mindful eating, pranayama, reiki, yoga sequences, meditation, hydration, and daily health habits.

## 📖 Architecture & Developer Documentation
For comprehensive architectural specifications, concurrency models, and module breakdown, consult:
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Architectural blueprint, threading guarantees, and design standards.

## 🏛️ Codebase Structure
```
app/src/main/java/com/habitbell/app/
├── HabitBellApplication.kt       # Application entry point
├── MainActivity.kt               # Jetpack Compose Host Activity
├── auto/                         # Android Auto (CarAppService, MediaService)
├── cast/                         # Local TV WebCast HTTP & NSD Server
├── data/                         # Domain models, repositories & default profiles
├── engine/                       # TimerEngine, Audio, Haptics, Power & Services
└── ui/                           # Jetpack Compose Screens, ViewModels, Components & Theme
```

## 📐 Documentation Standards
This repository adheres strictly to **KDoc** and **Google Kotlin Style Guide** documentation standards:
- Every class, function, parameter, and return value is documented with semantic descriptions and units.
- Non-trivial codeblocks are annotated with algorithmic explanations.
- Concurrency contracts and hardware dependencies (e.g., AudioTrack, Vibrator, NSD) are explicitly detailed.
