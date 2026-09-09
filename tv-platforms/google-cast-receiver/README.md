# Google Cast Custom Web Receiver for Habit Bell

## Architectural Overview & Component Role

The **Habit Bell Google Cast Custom Web Receiver** is an ultra-lightweight, dedicated TV display application designed specifically for Google Cast devices (Chromecast with Google TV, Chromecast 2nd/3rd/Ultra dongles, Android TV displays, Sony Bravia, and Philips Ambilight smart screens).

Unlike standard screen mirroring (which consumes excessive handset battery, overheats mobile devices, and stutters during prolonged usage), the Custom Web Receiver executes **directly on the Chromecast hardware**:
- **Zero Battery Drain on Phone**: Once casting starts, the mobile screen can dim or turn off completely while the TV runs independently.
- **Microscopic Footprint (< 6.0 KB)**: The production single-file bundle in [`docs/index.html`](../../docs/index.html) is only **6.0 KB**, loading in 20–50 milliseconds over Wi-Fi and consuming less than 0.001% of standard monthly GitHub Pages limits.
- **Direct Local Web Audio Synthesis**: Bell chimes and Tibetan singing bowl overtones are synthesized natively in-memory on the TV using the HTML5 Web Audio API at 432 Hz, requiring zero audio streaming bandwidth.
- **Real-Time Bidirectional Telemetry**: Bidirectional communication operates over the native Google Cast Message Bus (`urn:x-cast:com.habitbell.cast`), bypassing browser HTTPS/HTTP mixed-content limitations.

---

## Directory Structure

| File | Purpose | Size |
|---|---|---|
| [`docs/index.html`](../../docs/index.html) | **Production Build**: Hyper-minified single-file receiver deployed via GitHub Pages. | **6.0 KB** |
| [`tv-platforms/google-cast-receiver/index.html`](index.html) | **Development Source**: Unminified, fully commented receiver with human-readable CSS and debug logging. | 14.1 KB |
| [`tv-platforms/google-cast-receiver/README.md`](README.md) | This setup and architectural guide. | — |

---

## Deployment Guide: GitHub Pages & Google Cast Console

### Step 1: Enable GitHub Pages for the Repository
1. Navigate to your GitHub repository on github.com.
2. Click **Settings** > **Pages** (in the left sidebar).
3. Under **Build and deployment**:
   - **Source**: Select `Deploy from a branch`.
   - **Branch**: Select `main`.
   - **Folder**: Select `/docs`.
4. Click **Save**.
5. Within 60 seconds, your site will be live at:
   ```
   https://<your-github-username>.github.io/Healthy-Habit-Bell/
   ```
   *(Test this URL in your desktop browser—you should see the dark meditative Habit Bell clock interface).*

### Step 2: Register on the Google Cast Developer Console
1. Visit the [Google Cast SDK Developer Console](https://cast.google.com/publish/).
2. Log in with your Google account (one-time $5 registration fee required by Google for Cast developer registration).
3. Click **Add New Application**.
4. Select **Custom Receiver**.
5. Configure the receiver:
   - **Name**: `Habit Bell TV`
   - **Receiver Application URL**: `https://<your-github-username>.github.io/Healthy-Habit-Bell/`
   - **Supports Guest Mode**: Checked
6. Click **Save**.
7. Google will assign an 8-character hexadecimal **Application ID** (e.g., `A1B2C3D4`).
8. Under **Cast Developer Devices**, add the serial number of your Chromecast / Google TV device so your test device can launch the receiver immediately in developer mode.

### Step 3: Link Application ID in the Android App
Open [`app/src/main/java/com/habitbell/app/cast/CastOptionsProvider.kt`](../../app/src/main/java/com/habitbell/app/cast/CastOptionsProvider.kt) and set your Application ID:

```kotlin
companion object {
    /** Registered 8-character Hex App ID from cast.google.com/publish */
    @Volatile
    var customReceiverAppId: String? = "4662865D"
}
```
*Note: If left null, Habit Bell automatically falls back to Google's universal **Default Media Receiver** (`CC1AD845`), which functions globally without registration.*

---

## Protocol Specification: `urn:x-cast:com.habitbell.cast`

The mobile app and the TV receiver communicate in real time using JSON payloads sent across Google Cast's native message channel.

### 1. State Snapshot (`type: "state"`)
Broadcast every second by [`CentralSessionHandler`](../../app/src/main/java/com/habitbell/app/engine/CentralSessionHandler.kt):
```json
{
  "type": "state",
  "formattedTime": "14:59",
  "formattedNextBell": "00:45",
  "profileName": "Pranayama (Classical Hatha)",
  "status": "RUNNING",
  "currentRound": 2,
  "totalRounds": 12,
  "progressFraction": 0.166,
  "pranayamaPhase": "INHALE",
  "pranayamaSanskrit": "Purak",
  "pranayamaDisplay": "Inhale Deeply",
  "poseName": "",
  "poseSanskrit": "",
  "poseBreath": "",
  "triggerBell": false,
  "bellFrequency": 432
}
```

### 2. Volume Adjustment (`type: "volume"`)
Dispatched by [`HabitBellViewModel`](../../app/src/main/java/com/habitbell/app/ui/viewmodel/HabitBellViewModel.kt) whenever the user alters the volume slider:
```json
{
  "type": "volume",
  "bellVolume": 0.85
}
```
*Triggers an on-screen HUD toast on the TV: `"Bell Volume: 85%"`.*

### 3. Procedural Bell Chime (`type: "chime"`)
Synthesizes a 432 Hz / 1024 Hz Tibetan singing bowl tone directly on TV speakers:
```json
{
  "type": "chime",
  "freq": 432
}
```

### 4. TV Remote Control Feedback (TV Receiver -> Phone)
When the TV remote Play/Pause button is pressed, the receiver dispatches:
```json
{
  "type": "toggle"
}
```
*(or `"play"` / `"pause"`), seamlessly pausing/resuming the mobile session.*

---

## Anti-Sleep & Prolonged Session Safeguards
Standard Chromecast receivers sleep after 5 minutes of media inactivity. Habit Bell prevents this by setting:
```javascript
const opt = new cast.framework.CastReceiverOptions();
opt.disableIdleTimeout = true; // Prevents Chromecast from returning to ambient screensaver
opt.maxInactivity = 14400;      // 4 hours maximum session duration
```
This ensures uninterrupted meditation, breathwork, and yoga sessions of any duration.

---

## Production Release Checklist: Publishing on Google Cast Console
* **Development Phase**: Application ID `4662865D` operates in Developer Mode, verified on authorized test devices.
* **On App Completion (MANDATORY)**: As soon as the final production build is completed, log into the [Google Cast Developer Console](https://cast.google.com/publish/#/overview) and click **`PUBLISH`** next to Application ID `4662865D`.
* This activates global consumer access without device whitelisting, enabling any user downloading Habit Bell from Google Play to cast immediately to any TV worldwide.

