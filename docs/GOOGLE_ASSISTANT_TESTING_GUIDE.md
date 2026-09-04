# Google Assistant & App Actions Complete Testing Guide

This guide provides step-by-step instructions for testing **Google Assistant App Actions**, **Built-in Intents (BII)**, and **Voice Commands** on Android mobile devices for **Habit Bell**.

---

## 1. Overview of Assistant Integration in Habit Bell

Habit Bell supports voice interactions through three complementary mechanisms:

1. **App Actions Built-in Intents (BIIs)** defined in `shortcuts.xml`:
   - `actions.intent.START_TIMER` (supports query keywords like *eating*, *posture*, *reading*, *walking*, and duration parameters).
   - `actions.intent.STOP_TIMER` (`habitbell://action/stop`).
   - `actions.intent.PAUSE_TIMER` (`habitbell://action/pause`).
   - `actions.intent.RESUME_TIMER` (`habitbell://action/resume`).
2. **Standard Android Voice Clock Actions** defined in `AndroidManifest.xml`:
   - `android.provider.AlarmClock.ACTION_SET_TIMER`
   - `android.provider.AlarmClock.ACTION_DISMISS_TIMER`
   - `android.provider.AlarmClock.ACTION_SHOW_TIMERS`
3. **Static Launcher Shortcuts**:
   - `shortcut_mindful_eating` (`habitbell://start?profile=eating`)
   - `shortcut_posture` (`habitbell://start?profile=posture`)

---

## 2. Option A: Automated CLI Testing via ADB (Fastest & No Cloud Needed)

We have created automated test scripts in the `scripts/` directory:

### Run the Interactive Menu
```bash
./scripts/test_assistant_intents.sh
```
This presents an interactive menu allowing you to instantly trigger any voice intent (Start, Pause, Resume, Stop, Shortcuts) with one keystroke.

### Run Direct One-Liners
```bash
# Start default timer
./scripts/test_assistant_intents.sh start

# Start Mindful Eating (10 min)
./scripts/test_assistant_intents.sh eating

# Start Posture Timer (5 min)
./scripts/test_assistant_intents.sh posture

# Pause running timer
./scripts/test_assistant_intents.sh pause

# Resume timer
./scripts/test_assistant_intents.sh resume

# Stop timer
./scripts/test_assistant_intents.sh stop
```

### Run the Full Automated Assertion Suite (Python)
```bash
python3 scripts/test_google_assistant.py
```
This executes 15+ automated test cases covering cold start, hot `singleTop` delivery, parameter parsing, logcat assertions, and negative fallbacks, producing a detailed pass/fail report.

---

## 3. Option B: Android Studio App Actions Test Tool

The **Google Assistant plugin for Android Studio** allows you to create a local Assistant preview without having to deploy the app to Google Play Store production.

### Step 1: Install the Plugin
1. In Android Studio, go to **Settings / Preferences > Plugins**.
2. Search for **Google Assistant** (or **App Actions Test Tool**).
3. Click **Install** and restart Android Studio if prompted.

### Step 2: Sign In to Android Studio
1. In Android Studio, look at the upper-right corner avatar icon.
2. **Critical**: Sign in using the **same Google Account** that is currently signed into your Android test phone/emulator.
3. This account must have access to the Google Play Console for the application (or be an authorized tester / owner).

### Step 3: Open the App Actions Test Tool
1. In the Android Studio menu, click:
   **Tools > Google Assistant > App Actions Test Tool**.
2. A tool window will open on the right or bottom of the IDE.
3. Select your active device in the device dropdown.
4. Ensure your app's `shortcuts.xml` is detected.
5. Enter an **App name** (e.g. `Habit Bell`).
6. Click **Create Preview**.
   > *Note: Previews are valid for 24 hours. After 24 hours, simply click "Update Preview".*

### Step 4: Trigger and Inspect BIIs
1. In the tool window, under **App Action**, choose an intent:
   - `actions.intent.START_TIMER`
   - `actions.intent.STOP_TIMER`
   - `actions.intent.PAUSE_TIMER`
   - `actions.intent.RESUME_TIMER`
2. Enter parameter test values:
   - `timer.name`: `eating` or `posture`
   - `timer.duration`: `PT10M` (ISO-8601 for 10 minutes)
3. Click **Run App Action**.
4. Observe your connected device: Google Assistant will pop up with the query and immediately invoke Habit Bell.

---

## 4. Option C: Live Voice Testing ("Hey Google") on Device

To test using natural spoken voice:

### Step 1: Device Configuration
1. Open the **Google** app on your phone.
2. Tap your profile picture > **Settings > Google Assistant**.
3. Under **Hey Google & Voice Match**:
   - Turn on **Hey Google**.
   - Ensure your voice model is trained.
4. Under **Lock Screen**:
   - Turn on **Assistant responses on lock screen**.

### Step 2: Recommended Utterances

| Desired Action | Spoken Voice Utterance |
| :--- | :--- |
| **Start Default Timer** | *"Hey Google, start a timer on Habit Bell"* |
| **Start Eating Profile** | *"Hey Google, start mindful eating on Habit Bell"* |
| **Start Posture Profile** | *"Hey Google, start posture timer on Habit Bell"* |
| **Custom Duration** | *"Hey Google, set a 15 minute timer on Habit Bell"* |
| **Pause Session** | *"Hey Google, pause timer on Habit Bell"* |
| **Resume Session** | *"Hey Google, resume timer on Habit Bell"* |
| **Stop Session** | *"Hey Google, stop timer on Habit Bell"* |

---

## 5. Troubleshooting Common Issues

### Issue 1: "Sorry, I couldn't find that in Habit Bell"
- **Cause**: Google Assistant has not synced your app's preview or your package name in `shortcuts.xml` does not match the installed package.
- **Fix**:
  1. Check `shortcuts.xml`: Notice line 8 specifies `android:targetPackage="com.habitbell.app.debug"`. If you are running a release build (`com.habitbell.app`), change this or run the debug build.
  2. In Android Studio App Actions Test Tool, click **Update Preview**.
  3. Clear Google app cache: `Settings > Apps > Google > Storage > Clear Cache`.

### Issue 2: Assistant opens the default Android Clock app instead of Habit Bell
- **Cause**: Voice command was missing the explicit app name qualification (*"on Habit Bell"*).
- **Fix**: Always include the invocation name: *"Hey Google, set timer **on Habit Bell**"*.

### Issue 3: Preview expired in App Actions Test Tool
- **Cause**: Google Assistant test previews automatically expire after 24 hours.
- **Fix**: Re-open **Tools > Google Assistant > App Actions Test Tool** and click **Update Preview**.
