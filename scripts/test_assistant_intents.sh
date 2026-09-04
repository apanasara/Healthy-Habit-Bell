#!/usr/bin/env bash
# ==============================================================================
# Habit Bell — Interactive Google Assistant & Intent Simulation Shell Tool
# ==============================================================================
#
# Architectural Role:
#   Provides developers and QA engineers with a rapid, interactive CLI tool to
#   dispatch Google Assistant Built-In Intents (BIIs), Android AlarmClock actions,
#   and deep links without requiring speech-to-text hotword inputs.
#
# Dependencies:
#   - adb (Android Debug Bridge) installed and accessible in PATH.
#   - Connected Android phone or running emulator.
#
# Usage:
#   ./test_assistant_intents.sh [command]
#
# Available commands:
#   start     - Start timer (actions.intent.START_TIMER)
#   eating    - Start Mindful Eating timer (10 mins)
#   posture   - Start Posture timer (5 mins)
#   pause     - Pause running timer (actions.intent.PAUSE_TIMER)
#   resume    - Resume paused timer (actions.intent.RESUME_TIMER)
#   stop      - Stop timer (actions.intent.STOP_TIMER)
#   shortcuts - Test launcher static shortcuts
#   all       - Run Python automated test suite
#   (none)    - Interactive selection menu
# ==============================================================================

set -euo pipefail

# ANSI Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Auto-detect target package
PACKAGE="com.habitbell.app.debug"
if adb shell pm list packages | grep -q "com.habitbell.app.debug"; then
    PACKAGE="com.habitbell.app.debug"
elif adb shell pm list packages | grep -q "com.habitbell.app"; then
    PACKAGE="com.habitbell.app"
fi

ACTIVITY="${PACKAGE}/com.habitbell.app.MainActivity"

print_header() {
    echo -e "${CYAN}============================================================${NC}"
    echo -e "${CYAN} Habit Bell — Google Assistant Intent Simulation Tool${NC}"
    echo -e "${CYAN} Target Package: ${GREEN}${PACKAGE}${NC}"
    echo -e "${CYAN}============================================================${NC}"
}

cmd_start_default() {
    echo -e "${GREEN}==> Simulating: 'Hey Google, start a timer on Habit Bell'${NC}"
    adb shell am start -a android.intent.action.VIEW -n "${ACTIVITY}"
}

cmd_start_eating() {
    echo -e "${GREEN}==> Simulating: 'Hey Google, set mindful eating timer on Habit Bell'${NC}"
    adb shell am start -a android.intent.action.SET_TIMER \
        --ei android.intent.extra.alarm.LENGTH 600 \
        --es android.intent.extra.alarm.MESSAGE "Mindful Eating" \
        -n "${ACTIVITY}"
}

cmd_start_posture() {
    echo -e "${GREEN}==> Simulating: 'Hey Google, set 5 minute posture timer on Habit Bell'${NC}"
    adb shell am start -a android.intent.action.VIEW \
        -e timerName "posture" \
        -e timerDuration "300" \
        -n "${ACTIVITY}"
}

cmd_pause() {
    echo -e "${YELLOW}==> Simulating: 'Hey Google, pause timer on Habit Bell'${NC}"
    adb shell am start -a android.intent.action.VIEW \
        -d "habitbell://action/pause" \
        -n "${ACTIVITY}"
}

cmd_resume() {
    echo -e "${GREEN}==> Simulating: 'Hey Google, resume timer on Habit Bell'${NC}"
    adb shell am start -a android.intent.action.VIEW \
        -d "habitbell://action/resume" \
        -n "${ACTIVITY}"
}

cmd_stop() {
    echo -e "${RED}==> Simulating: 'Hey Google, stop timer on Habit Bell'${NC}"
    adb shell am start -a android.intent.action.VIEW \
        -d "habitbell://action/stop" \
        -n "${ACTIVITY}"
}

cmd_shortcuts() {
    echo -e "${CYAN}==> Triggering Static Launcher Shortcut: Mindful Eating${NC}"
    adb shell am start -a android.intent.action.VIEW \
        -d "habitbell://start?profile=eating" \
        -n "${ACTIVITY}"
}

cmd_run_all() {
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    python3 "${SCRIPT_DIR}/test_google_assistant.py"
}

# Command dispatching
ACTION="${1:-menu}"

case "${ACTION}" in
    start)
        print_header
        cmd_start_default
        ;;
    eating)
        print_header
        cmd_start_eating
        ;;
    posture)
        print_header
        cmd_start_posture
        ;;
    pause)
        print_header
        cmd_pause
        ;;
    resume)
        print_header
        cmd_resume
        ;;
    stop)
        print_header
        cmd_stop
        ;;
    shortcuts)
        print_header
        cmd_shortcuts
        ;;
    all)
        print_header
        cmd_run_all
        ;;
    menu|*)
        print_header
        echo "1) Start Default Timer (actions.intent.START_TIMER)"
        echo "2) Start Mindful Eating Timer (AlarmClock SET_TIMER - 600s)"
        echo "3) Start Posture Timer (BII with timerName & timerDuration - 300s)"
        echo "4) Pause Timer (actions.intent.PAUSE_TIMER)"
        echo "5) Resume Timer (actions.intent.RESUME_TIMER)"
        echo "6) Stop Timer (actions.intent.STOP_TIMER)"
        echo "7) Trigger Launcher Shortcut (Mindful Eating)"
        echo "8) Run Entire Automated Python Test Suite"
        echo "q) Quit"
        echo ""
        read -rp "Select an option [1-8]: " choice
        case "${choice}" in
            1) cmd_start_default ;;
            2) cmd_start_eating ;;
            3) cmd_start_posture ;;
            4) cmd_pause ;;
            5) cmd_resume ;;
            6) cmd_stop ;;
            7) cmd_shortcuts ;;
            8) cmd_run_all ;;
            *) echo "Exiting." ;;
        esac
        ;;
esac
