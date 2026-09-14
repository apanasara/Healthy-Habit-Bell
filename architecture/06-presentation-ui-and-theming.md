# 06. Presentation UI & Theming

This document details Habit Bell's declarative Jetpack Compose presentation layer, system bar immersion, punch-hole camera geometry, central theme engine, and brand assets.

---

## 1. Presentation Layer & Immersive Display

- **Jetpack Compose**: 100% declarative UI built with Material 3 design tokens.
- **Distraction-Free Immersion**: When a timer session transitions to `RUNNING`, `MainActivity` uses `WindowInsetsControllerCompat` to hide the system status bar and navigation bar (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), preventing notification distractions during mindfulness sessions.
- **Ongoing Session Auto-Load & State Restoration Architecture (FLAW2)**:
  - **Problem Addressed (FLAW2)**: When opening or returning to the app while a session was ongoing in the background, the UI previously defaulted to `AppScreen.HOME`, hiding the active session and forcing users to start another timer just to access the stop button.
  - **ViewModel Dynamic Destination Routing**: `HabitBellViewModel` initializes `_uiState` dynamically: if `sessionState.status == RUNNING || sessionState.status == PAUSED`, the initial destination is set to `AppScreen.SESSION`, restoring active display mode and pocket mode configurations immediately.
  - **Activity Entry Synchronization**: `MainActivity.kt` executes `viewModel.checkAndRestoreOngoingSession()` across `onCreate()`, `onNewIntent()`, and `onResume()`. Any launch intent with `EXTRA_NAVIGATE_TO_SESSION` or any foreground return during an active session routes instantly to `AppScreen.SESSION`.
  - **Splash Screen Bypass**: When `sessionState.status` is `RUNNING` or `PAUSED`, `MainActivity` automatically bypasses the splash screen overlay (`showSplashOverlay = false`) for instant access to playback controls.
  - **Home-to-Session Manual Play Landing (`selectProfileSession`)**: When tapping a wellness profile or hero card on the Home dashboard (`ModernHomeScreenSample.kt`), the app invokes `HabitBellViewModel.selectProfileSession(profile)`. This loads the profile in `SessionStatus.IDLE` ("Ready") state and navigates directly to `AppScreen.SESSION`. The timer does NOT auto-run; countdown, ambient audio, and step tracking commence strictly when the user taps the central Play button (`FilledIconButton`) on `SessionScreen`.
  - **Home Screen Active Session Banner (`ActiveSessionBanner`)**: In `ModernHomeScreenSample.kt`, if the user explicitly navigates to the Home dashboard while a session is active, a prominent top banner renders the active profile name, remaining time countdown, pulsing status badge, and 1-tap "Stop" and "Open / Resume" buttons.
- **Punch-Hole Cutout Safe Geometry (`SessionScreen.kt`)**:
  - Modern smartphones feature centered or offset physical camera punch-holes. When immersive session mode engages (`setStatusBarHidden(true)`), standard system status bar insets collapse to zero.
  - `SessionScreen` enforces `Modifier.displayCutoutPadding()` and implements a **decoupled header architecture**: the top action bar splits navigation (`Back`) to the far-left and controls (`Cast`, `Pocket Mode`, `Settings`) to the far-right, leaving the top-center column completely unobstructed.
  - The session title (e.g. "Mindful Eating") is placed on a secondary centered row beneath the action bar with safe vertical breathing room (`Spacer(14.dp)`), guaranteeing 100% immunity from camera punch-holes across all hardware form factors.
- **Dynamic Theme-Aware Google Cast Surface (`CastButton.kt`, `styles.xml`)**:
  - `MediaRouteButton` relies on underlying Android View AppCompat styling. To ensure high-contrast visibility across varying background luminances, `styles.xml` defines two dedicated themes:
    - `HabitBellMediaRouteTheme.Dark`: Based on `Theme.AppCompat.NoActionBar`, routing `colorControlNormal` to warm white (`#EDE8DE`).
    - `HabitBellMediaRouteTheme.Light`: Based on `Theme.AppCompat.Light.NoActionBar`, routing `colorControlNormal` to dark charcoal (`#2E261F`).
  - `CastButton` dynamically samples `MaterialTheme.colorScheme.background.luminance()` and recomposes within `key(isDark)` to switch themes instantly whenever the user toggles between Day (Sun) and Night (Moon) modes.
- **Icon-Driven Minimal Presentation (Phosphor Line 1.5px)**:
  - Replaces text-heavy UI with minimalist 1.5px line icons to eliminate cognitive reading stress.
  - Mindful Eating sessions incorporate a concentric dual-ring layout: an outer total mealtime ring (45m) and an animated inner bite-pacing arc framing a Phosphor bowl glyph, paired with a subtle chew-and-savor pacing bell indicator.
- **Key Visual Components**:
  - `BreathIndicator`: Canvas-drawn dynamic expanding/contracting circle visualizing the 4 phases of Pranayama.
  - `CircularProgressRing`: High-precision remaining-time stroke animation with smooth color interpolation.
  - `CompoundPoseCard`: Step indicator for multi-pose yoga and reiki sequences.
  - `PocketOverlay`: Pure black `#000000` AMOLED overlay with double-tap unlock protection.

---

## 2. Central Theme Architecture & Dialog Stability

### Unified Central Theme Engine
Habit Bell enforces a consistent, centralized visual theme hierarchy governed exclusively by `HabitBellViewModel.selectedTheme`:
- **Single Source of Truth**: The user's active theme selection (`ThemeMode.AMOLED`, `ThemeMode.DARK`, `ThemeMode.EYE_COMFORT`, `ThemeMode.LIGHT`) governs the entire application container (`HabitBellTheme`).
- **Profile Decoupling**: Individual wellness timer profiles (`TimerProfile`) define timing parameters, pacing bells, and sensor triggers, but do NOT override the user's central theme preference when starting a session.
- **MaterialTheme Dynamic Binding**: All UI surfaces (`HomeScreen`, `SessionScreen`, `ModernHomeScreenSample`, `SettingsDrawer`) dynamically bind container, card, border, and typography colors to `MaterialTheme.colorScheme` tokens, guaranteeing flawless contrast across dark and light palettes.
- **Symmetrical Sun ☀️ / Moon 🌙 Toggle**: Top action bars on both Home and Session screens feature a high-legibility theme action:
  - Renders `ic_ph_sun` in dark modes to switch to Light (Day / Warm Parchment).
  - Renders `ic_ph_moon` in Light mode to switch to Dark (AMOLED / Pure Black).

### Google Cast MediaRoute Dialog Factory & Background Stability
Native `MediaRouteButton` interactions in Jetpack Compose require strict background opacity to comply with AndroidX `MediaRouterThemeHelper` contrast calculations:
- **Crash Prevention**: Inheriting translucent window backgrounds causes `androidx.core.graphics.ColorUtils.calculateContrast` to throw `IllegalArgumentException: background can not be translucent: #0`.
- **HabitBellMediaRouteDialogFactory**: Wraps `MediaRouteChooserDialog` and `MediaRouteControllerDialog` instantiation within an explicit, non-translucent `ContextThemeWrapper` applying `R.style.HabitBellMediaRouteTheme_Dark` or `R.style.HabitBellMediaRouteTheme_Light`.

---

## 3. Unified Visual Identity, Adaptive Icons & Splash Screen Architecture

Habit Bell enforces a unified, high-contrast visual identity centered on the sacred blooming lotus flower cradling a resonant Tibetan mindfulness bell.

### 1. Canonical Branding Asset Repository (`branding/`)
Authoritative vector artwork and multi-platform raster source files are permanently tracked in version control under `Healthy-Habit-Bell/branding/`:
- **`HabitBell_Inkscape.svg` & `HabitBell.svg`**: Master Inkscape scalable vector graphics containing coordinate-exact path nodes and drop shadow filter definitions.
- **`HabitBell_ChromeCast.png`**: Production 512×512 32-bit RGBA raster icon formatted specifically for the **Google Cast SDK Developer Console** and Google Play Store listings.
- **`HabitBell_Transparent.png`**: High-resolution 1027×893 transparent PNG master representing the glowing golden lotus and bell silhouette.
- **`HabitBell_Black.webp`**: Lossless WebP asset for web and companion application distribution.

### 2. Automated Multi-Density Asset Pipeline (`scripts/generate_branding_assets.js`)
An automated Node.js automation pipeline utilizing headless Google Chrome and macOS `sips` renders subpixel-accurate graphics across all target platforms:
- **Android Adaptive Icons (`mipmap-*/ic_launcher_foreground.png`)**:
  - Rendered across `mdpi` (108px), `hdpi` (162px), `xhdpi` (216px), `xxhdpi` (324px), and `xxxhdpi` (432px).
  - Scaled strictly to 50% of the adaptive canvas width with a 25% boundary safety margin. This guarantees 0% visual clipping regardless of OEM launcher shape masking (circles, rounded rectangles, squircles, or teardrops).
- **Legacy Square & Circular Icons (`ic_launcher.png`, `ic_launcher_round.png`)**:
  - Direct hardware-clipped circular and square raster outputs across all density tiers (48px to 192px).
- **Android TV / Google TV Leanback Launcher (`tv_banner.png`)**:
  - 320×180 16:9 widescreen launcher banner featuring radial amber-gold glow and high-legibility typography.
- **Smart TV Ecosystems**:
  - LG webOS: 1920×1080 Full HD splash screen (`splash.png`) and tray icons (80×80 and 130×130).
  - Samsung Tizen: 117×117 app tile icon (`icon.png`).
  - Google Cast Receiver: 512×512 receiver app icon and transparent backdrop logo.

### 3. Dual-Stage Splash Screen Architecture
To eliminate cold-boot latency and white screen flashes on both modern and legacy Android runtimes:
- **Stage 1 — Native OS Window Splash (`splash_background.xml` & `values-v31/styles.xml`)**:
  - Renders a lightweight `<layer-list>` drawable with solid obsidian dark background (`#060709`) and centered 160dp `@drawable/ic_splash_logo` during initial process fork and JVM warm-up.
  - On Android 12+ (API 31+), `Theme.HabitBell` binds `android:windowSplashScreenBackground`, `android:windowSplashScreenAnimatedIcon`, and `android:windowSplashScreenIconBackgroundColor`.
- **Stage 2 — In-App Serene Compose Handoff (`SplashScreen.kt`)**:
  - Hosted within `MainActivity`'s root `Box` as an `AnimatedVisibility` overlay.
  - Executes a subtle breathing scale (0.92f → 1.0f) and alpha fade-in (650ms) using `FastOutSlowInEasing`.
  - Automatically fades out smoothly (400ms) to reveal `ModernHomeScreenSample` on cold boot.
  - **Voice & Deep Link Bypass**: Automated intents (`ACTION_SET_TIMER`, `SURYA_TIMER`, `ACTION_VIEW`) immediately bypass the in-app splash animation (`showSplashOverlay = false`) to guarantee zero-latency execution for Google Assistant commands.
