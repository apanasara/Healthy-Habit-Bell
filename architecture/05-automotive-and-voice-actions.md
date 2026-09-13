# 05. Automotive & Voice Actions

This document details Habit Bell's automotive head-unit integration complying with Android for Cars design standards, background foreground service lifecycles, and Google Assistant voice actions.

---

## 1. Android Auto Subsystem (`com.habitbell.app.auto`, `com.habitbell.app.engine`)

Habit Bell provides deep automotive integration complying with Android for Cars design guidelines (Car App Library v1.7.0, Car API Level 8) and robust background foreground service execution:

- **`HabitBellCarAppService.kt`**: Top-level `CarAppService` entry point bound by the Android Auto host. Manifest category: `androidx.car.app.category.IOT` (wellness/timer/ambient routines). Uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` during development; production requires `HostValidator.Builder` with explicit allowlist.
- **`HabitBellCarSession.kt`**: Per-connection session lifecycle manager. Handles initial `onCreateScreen()` and reconnection via `onNewIntent()` for transient disconnect recovery. Coordinates `DisplayAutomationManager.setCarConnected()` state across connect/disconnect transitions with diagnostic lifecycle logging. Binds to `BluetoothAudioDisconnectionManager.onCarDisconnected()` to safely pause active sessions when unplugged from the vehicle.
- **`HabitBellCarScreen.kt`**: Driver-safe `ListTemplate` with 3 glanceable wellness routines (Posture, Breath, Eating). Optimized for the 2-second glance rule with shortened titles, duration indicators, and `ActionStrip` global Stop button during active sessions. State observer throttled to status-change and minute-boundary invalidation only (prevents 1Hz IPC flooding that destabilizes the Android Auto host Binder bridge). All `invalidate()` calls guarded by `Lifecycle.State.STARTED` check with `IllegalStateException` catch for host teardown race conditions.
- **`HabitBellMediaService.kt`**: Extends `MediaBrowserServiceCompat` to expose mindful audio routines to vehicle media drawers, system media controllers, and Wear OS. Emits driver-optimized `NotificationCompat.MediaStyle` ongoing notifications throttled to status transitions and 5-second intervals.
- **Session Token Sharing**: Both services bind directly to `CentralSessionHandler.sessionToken`, guaranteeing that media button presses on vehicle steering wheels instantly control the central timer engine with zero lag.

---

## 2. Background Service Lifecycle & Recents Task Dismissal (`onTaskRemoved` & `android:stopWithTask="true"`)

- **Problem Addressed (FLAW1)**: Prior to this architecture standard, clearing running apps from Android's Recents / App Switcher ("Clear All" / swipe away) left foreground media services running adrift in the background, causing continuous interval bell chimes, ambient audio streaming, wake lock retention, and an unkillable foreground notification.
- **Implementation & Teardown Flow**:
  - `HabitBellMediaService.kt` and `TimerService.kt` implement `override fun onTaskRemoved(rootIntent: Intent?)`.
  - When invoked by the Android OS upon task dismissal, `onTaskRemoved` immediately commands `sessionHandler.stop()`.
  - Halts `TimerEngine`, resets countdown state to `IDLE`, silences Tibetan bells in `AudioBellManager`, terminates ambient background music in `BackgroundMusicManager`, cancels haptic pulses in `HapticManager`, resets `HealthStepManager`, and releases power wake-locks in `BatteryOptimizer`.
  - Calls `stopForeground(STOP_FOREGROUND_REMOVE)` and `notificationManager.cancel(NOTIFICATION_ID)` to strip ongoing notifications from the system shade.
  - Commands `stopSelf()` to terminate the background service process and release system handles cleanly.
  - Manifest enforcement: Both services declare `android:stopWithTask="true"` in `AndroidManifest.xml`.
  - Clean Completion Termination: In `HabitBellMediaService.observeSessionState()`, transitioning to `SessionStatus.IDLE` or `SessionStatus.COMPLETED` calls `stopSelf()` to prevent idle service leakage.

---

## 3. Google Assistant & Voice Actions

- **App Actions & Shortcuts (`shortcuts.xml`)**: Maps built-in intents (`actions.intent.START_EXERCISE`, `actions.intent.STOP_EXERCISE`) to Habit Bell timer profiles.
- **Voice Invocations**:
  - *"OK Google, start mindful eating on Habit Bell"*
  - *"OK Google, start meditation on Habit Bell"*
- **Intent Deep-Linking**: `MainActivity` extracts voice intent parameters and passes them directly to `HabitBellViewModel` to launch the requested session immediately.
