# Habit Bell for Apple TV (tvOS)

## Overview
Habit Bell for Apple TV is a native **Swift & SwiftUI** application engineered for tvOS 17+. It provides a distraction-free 10-foot living room meditation timer, synchronizing with the Habit Bell Android handset over local Wi-Fi via Bonjour mDNS and low-latency HTTP Server-Sent Events (SSE).

## Architecture & Integration
- **Framework**: SwiftUI + Combine.
- **Remote Control**: Native Apple TV Siri Remote (Clickpad / Touch surface) navigation and physical Play/Pause button support.
- **Local Network Sync**:
  - Automatically discovers the Android handset advertising `_http._tcp.` (`HabitBellTV`) on port 8888.
  - Subscribes to `/api/state` for 1Hz real-time countdown, progress fraction, and next bell status.
  - Dispatches `/api/action/toggle` and `/api/action/stop` on button taps.

## Build & Deployment Instructions

### Prerequisites
1. Xcode 15 or newer with the tvOS SDK installed.
2. An active Apple Developer Account (for TestFlight / App Store submission or local device provisioning).

### Running in tvOS Simulator
1. Open Xcode and open `tv-platforms/apple-tvos`.
2. Select target: `HabitBellTV` -> `Apple TV 4K (3rd generation) Simulator`.
3. Press `Cmd + R` to build and run.
4. When Habit Bell is running on an Android handset connected to the same Wi-Fi network, the Apple TV app will auto-discover it and mirror the session in real time.
