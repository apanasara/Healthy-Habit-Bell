# Habit Bell for LG Smart TV (webOS)

## Overview
Habit Bell for LG Smart TVs targets **LG webOS (4.0+)**. It leverages the identical modern web client engine, optimized for the 10-foot TV viewing distance, and interfaces with the LG Magic Remote (pointer, wheel, and D-pad controls).

## Architecture & Integration
- **Runtime**: webOS Web Application (`.ipk` package).
- **Core Files**: `appinfo.json` + `index.html` (from `app/src/main/assets/tv/`).
- **Input Controls**:
  - **Remote Keycodes**: Play (415), Pause (19), Stop (413), Back/Return (461), OK/Enter (13).
  - **Magic Remote**: Native point-and-click focus navigation on on-screen controls.
  - **Color Keys**: Red (Toggle Drone), Green (-5 min), Yellow (+5 min).

## Build & Packaging Instructions

### Prerequisites
1. Install [webOS TV CLI](https://webostv.developer.lge.com/develop/tools/cli-installation) (`ares-package`, `ares-install`, `ares-setup-device`).
2. Enable Developer Mode on the LG TV via the LG Developer Mode app.

### Packaging Steps
```bash
# 1. Navigate to the LG webOS platform directory
cd tv-platforms/lg-webos

# 2. Copy the latest web assets
cp ../../app/src/main/assets/tv/index.html ./
cp ../../app/src/main/assets/tv/aum.mp3 ./

# 3. Package into an .ipk file
ares-package ./

# 4. Install onto the LG Smart TV
ares-install --device <YOUR_LG_TV_NAME> com.habitbell.tv.webos_1.0.0_all.ipk
```
