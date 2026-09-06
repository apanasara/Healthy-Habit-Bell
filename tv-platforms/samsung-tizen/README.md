# Habit Bell for Samsung Smart TV (Tizen OS)

## Overview
Habit Bell for Samsung Smart TVs runs on **Tizen OS (5.0+)** via the Samsung TV Web Engine. It shares the identical high-contrast, distraction-free 10-foot UI designed for living rooms, and integrates directly with Samsung Smart Remote input hardware (`tizen.tvinputdevice`).

## Architecture & Integration
- **Runtime**: Tizen Web Application (`.wgt` container).
- **Core Files**: `config.xml` + `index.html` (from `app/src/main/assets/tv/`).
- **Input Controls**:
  - **Play / Pause**: MediaPlay (415), MediaPause (19), MediaPlayPause (10252), Space (32).
  - **Stop / Reset**: MediaStop (413), 'R' key (82).
  - **Audio Toggle**: Red Color Key (403), 'M' key (77).
  - **Quick Adjust**: Green Color Key (404: -5m), Yellow Color Key (405: +5m).
  - **Return / Exit**: Samsung Tizen Return Key (10009).

## Build & Packaging Instructions

### Prerequisites
1. Install [Tizen Studio](https://developer.tizen.org/development/tizen-studio/download) with the **Samsung TV Extensions**.
2. Generate an active Samsung Author & Distributor Certificate via the Certificate Manager.

### Packaging Steps
```bash
# 1. Navigate to the Samsung Tizen platform directory
cd tv-platforms/samsung-tizen

# 2. Copy the latest web assets
cp ../../app/src/main/assets/tv/index.html ./
cp ../../app/src/main/assets/tv/aum.mp3 ./

# 3. Build and package into a .wgt container
tizen build-web
tizen package -t wgt -s <Your_Samsung_Security_Profile>

# 4. Deploy and install on a Samsung Smart TV (Developer Mode enabled)
sdb connect <TV_IP_ADDRESS>
tizen install -n HabitBellTV.wgt -t <TV_TARGET_ID>
```

### Direct Wi-Fi WebCast (Zero-Install Alternative)
Users on Samsung Smart TVs can alternatively open the built-in Samsung TV Web Browser and navigate to:
`http://<PHONE_IP>:8888`
The dashboard will auto-sync timer countdowns and interval bells over the local Wi-Fi with 0 cloud dependencies.
