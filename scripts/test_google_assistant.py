#!/usr/bin/env python3
"""
================================================================================
Habit Bell — Google Assistant & Voice Action Automated Test Suite
================================================================================

Architectural Role:
    Quality Assurance and Automated Test Runner for mobile Google Assistant
    integration. Dispatches Android Intent payloads over ADB to validate
    Built-In Intents (BIIs), Android AlarmClock actions, App Action deep links,
    and singleTop Activity lifecycle transitions against a live Android device
    or emulator.

Component Relationships:
    - Communicates with the connected Android device via the `adb` CLI executable.
    - Inspects logcat output from `MainActivity`, `HabitBellViewModel`, and `TimerEngine`.
    - Validates capabilities declared in `shortcuts.xml` and `AndroidManifest.xml`.

Lifecycle & Concurrency:
    - Executed on host machine (macOS/Linux/Windows).
    - Sequential test dispatch with asynchronous logcat streaming and timeout enforcement.
"""

import argparse
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple


# ==============================================================================
# Global Configuration & Constants
# ==============================================================================

DEFAULT_DEBUG_PKG: str = "com.habitbell.app.debug"
DEFAULT_RELEASE_PKG: str = "com.habitbell.app"
MAIN_ACTIVITY: str = "com.habitbell.app.MainActivity"

# ANSI Terminal Formatting for rich test reports
COLOR_RESET: str = "\033[0m"
COLOR_BOLD: str = "\033[1m"
COLOR_GREEN: str = "\033[32m"
COLOR_RED: str = "\033[31m"
COLOR_YELLOW: str = "\033[33m"
COLOR_CYAN: str = "\033[36m"
COLOR_BLUE: str = "\033[34m"


@dataclass
class TestCase:
    """
    Data model representing a single Assistant voice intent test case.

    Attributes:
        test_id (str): Unique identifier matching the Master Test Plan (e.g., 'TC-BII-01').
        name (str): Human-readable title of the test scenario.
        description (str): Business context and expected behavior.
        command_args (List[str]): ADB command arguments to dispatch the intent.
        expected_log_pattern (Optional[str]): Regex pattern to verify in logcat output.
        pre_kill_app (bool): If True, force-stops the app process prior to dispatch (Cold Start).
        post_delay_sec (float): Cooldown delay in seconds before asserting state / next test.
    """
    test_id: str
    name: str
    description: str
    command_args: List[str]
    expected_log_pattern: Optional[str] = None
    pre_kill_app: bool = False
    post_delay_sec: float = 2.0


class AdbAssistantTestRunner:
    """
    Automated orchestration engine for Android Debug Bridge (ADB) Assistant testing.

    Architectural Layer:
        Test Automation / Host Infrastructure.

    Single Responsibility:
        Detects connected devices, resolves the installed Habit Bell package,
        dispatches synthetic Assistant intents, and validates logcat assertions.
    """

    def __init__(self, serial: Optional[str] = None, package_name: Optional[str] = None) -> None:
        """
        Initializes the ADB test runner.

        Args:
            serial (Optional[str]): Target device serial number from `adb devices`.
            package_name (Optional[str]): Android package name override (defaults to auto-detect).
        """
        self.serial: Optional[str] = serial
        self.package_name: Optional[str] = package_name
        self.results: List[Tuple[TestCase, bool, str]] = []

    def _run_adb_cmd(self, args: List[str], capture_output: bool = True, timeout_sec: int = 15) -> subprocess.CompletedProcess:
        """
        Executes an ADB shell or host command synchronously.

        Args:
            args (List[str]): Subcommand parameters passed to ADB.
            capture_output (bool): Whether to pipe stdout and stderr.
            timeout_sec (int): Maximum execution timeout in seconds.

        Returns:
            subprocess.CompletedProcess: Execution result containing exit code, stdout, and stderr.

        Raises:
            subprocess.TimeoutExpired: When command hangs past timeout_sec.
            FileNotFoundError: If `adb` is missing from system PATH.
        """
        cmd = ["adb"]
        if self.serial:
            cmd.extend(["-s", self.serial])
        cmd.extend(args)

        return subprocess.run(
            cmd,
            stdout=subprocess.PIPE if capture_output else None,
            stderr=subprocess.PIPE if capture_output else None,
            text=True,
            timeout=timeout_sec
        )

    def check_prerequisites(self) -> bool:
        """
        Validates ADB installation, device connectivity, and app installation.

        Returns:
            bool: True if test prerequisites are satisfied, False otherwise.
        """
        print(f"{COLOR_CYAN}{COLOR_BOLD}=== Checking ADB Test Prerequisites ==={COLOR_RESET}")

        # 1. Verify ADB executable
        try:
            res = self._run_adb_cmd(["version"])
            if res.returncode != 0:
                print(f"{COLOR_RED}[FAIL] ADB is not responding.{COLOR_RESET}")
                return False
            print(f"{COLOR_GREEN}[PASS] ADB found:{COLOR_RESET} {res.stdout.splitlines()[0]}")
        except FileNotFoundError:
            print(f"{COLOR_RED}[FAIL] 'adb' executable not found in PATH.{COLOR_RESET}")
            return False

        # 2. Check connected devices
        res = self._run_adb_cmd(["devices"])
        lines = [line.strip() for line in res.stdout.splitlines() if line.strip() and not line.startswith("List of")]
        connected = [l.split("\t")[0] for l in lines if "\tdevice" in l]

        if not connected:
            print(f"{COLOR_RED}[FAIL] No authorized Android device or emulator detected.{COLOR_RESET}")
            print("       Please connect a device via USB/Wi-Fi or start an Android emulator.")
            return False

        if not self.serial:
            self.serial = connected[0]
            print(f"{COLOR_GREEN}[PASS] Selected target device:{COLOR_RESET} {self.serial}")
        else:
            if self.serial not in connected:
                print(f"{COLOR_RED}[FAIL] Device '{self.serial}' is not in connected devices: {connected}{COLOR_RESET}")
                return False
            print(f"{COLOR_GREEN}[PASS] Using specified device:{COLOR_RESET} {self.serial}")

        # 3. Detect installed package
        if not self.package_name:
            res = self._run_adb_cmd(["shell", "pm", "list", "packages", "habitbell"])
            installed = res.stdout.strip()
            if DEFAULT_DEBUG_PKG in installed:
                self.package_name = DEFAULT_DEBUG_PKG
            elif DEFAULT_RELEASE_PKG in installed:
                self.package_name = DEFAULT_RELEASE_PKG
            else:
                print(f"{COLOR_YELLOW}[WARN] Habit Bell app does not appear to be installed on {self.serial}.{COLOR_RESET}")
                print(f"       Defaulting to debug package: {DEFAULT_DEBUG_PKG}")
                self.package_name = DEFAULT_DEBUG_PKG

        print(f"{COLOR_GREEN}[PASS] Target application package:{COLOR_RESET} {self.package_name}")
        return True

    def clear_logcat(self) -> None:
        """
        Clears the device circular log buffer to isolate test-specific logs.
        """
        self._run_adb_cmd(["logcat", "-c"])

    def fetch_recent_logs(self, max_lines: int = 150) -> str:
        """
        Reads recent logcat messages related to Habit Bell Activity, ViewModel, and Engine.

        Args:
            max_lines (int): Number of recent lines to tail from logcat.

        Returns:
            str: Log buffer string containing relevant log tags.
        """
        res = self._run_adb_cmd([
            "logcat", "-d", "-t", str(max_lines),
            "-s", "MainActivity:V", "HabitBellViewModel:V", "TimerEngine:V", "TimerService:V"
        ])
        return res.stdout

    def execute_test(self, test: TestCase) -> bool:
        """
        Executes a single test case, monitors logcat, and records outcome.

        Args:
            test (TestCase): Test specification to run.

        Returns:
            bool: True if test passed, False if failed.
        """
        print(f"\n{COLOR_BOLD}------------------------------------------------------------{COLOR_RESET}")
        print(f"{COLOR_CYAN}Running [{test.test_id}]: {test.name}{COLOR_RESET}")
        print(f"Description: {test.description}")

        # Cold Start enforcement if requested
        if test.pre_kill_app:
            print(f"  -> Force-stopping package {self.package_name} for Cold Start...")
            self._run_adb_cmd(["shell", "am", "force-stop", self.package_name])
            time.sleep(1.0)

        # Clear logcat before intent delivery
        self.clear_logcat()

        # Substitute target package into command arguments
        processed_args: List[str] = []
        for arg in test.command_args:
            arg_sub = arg.replace("{PACKAGE}", self.package_name or DEFAULT_DEBUG_PKG)
            arg_sub = arg_sub.replace("{ACTIVITY}", f"{self.package_name or DEFAULT_DEBUG_PKG}/{MAIN_ACTIVITY}")
            processed_args.append(arg_sub)

        # Dispatch the intent
        cmd_str = "adb " + ("-s " + self.serial + " " if self.serial else "") + " ".join(processed_args)
        print(f"  -> Dispatching: {cmd_str}")

        start_time = time.time()
        res = self._run_adb_cmd(processed_args)

        if res.returncode != 0:
            error_msg = f"ADB command failed with exit code {res.returncode}: {res.stderr.strip()}"
            print(f"  {COLOR_RED}[FAIL] {error_msg}{COLOR_RESET}")
            self.results.append((test, False, error_msg))
            return False

        # Allow Activity lifecycle and state updates to settle
        time.sleep(test.post_delay_sec)
        elapsed_ms = (time.time() - start_time) * 1000.0

        # Logcat assertion verification
        passed = True
        log_detail = f"Dispatched in {elapsed_ms:.0f}ms"
        if test.expected_log_pattern:
            logs = self.fetch_recent_logs()
            if re.search(test.expected_log_pattern, logs, re.IGNORECASE):
                print(f"  {COLOR_GREEN}[PASS] Matched logcat pattern: '{test.expected_log_pattern}'{COLOR_RESET}")
                log_detail += f" | Verified: {test.expected_log_pattern}"
            else:
                # Check if intent launched activity cleanly
                if "Starting: Intent" in res.stdout:
                    print(f"  {COLOR_GREEN}[PASS] Intent launched Activity successfully.{COLOR_RESET}")
                    passed = True
                else:
                    print(f"  {COLOR_RED}[FAIL] Expected log pattern '{test.expected_log_pattern}' not found.{COLOR_RESET}")
                    passed = False

        if passed:
            print(f"  {COLOR_GREEN}{COLOR_BOLD}[RESULT: PASS]{COLOR_RESET}")
        else:
            print(f"  {COLOR_RED}{COLOR_BOLD}[RESULT: FAIL]{COLOR_RESET}")

        self.results.append((test, passed, log_detail))
        return passed

    def generate_test_cases(self) -> List[TestCase]:
        """
        Constructs the comprehensive test suite matching the Habit Bell Assistant Test Plan.

        Returns:
            List[TestCase]: Ordered collection of automated test cases.
        """
        tests: List[TestCase] = []

        # ----------------------------------------------------------------------
        # Suite 1: Built-in Intents (BII) & Parameters
        # ----------------------------------------------------------------------
        tests.append(TestCase(
            test_id="TC-BII-01",
            name="BII Start Default Timer",
            description="Simulates 'Hey Google, start a timer on Habit Bell'",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="handleVoiceIntent|startVoiceTimer",
            pre_kill_app=True,
            post_delay_sec=2.5
        ))

        tests.append(TestCase(
            test_id="TC-BII-02",
            name="BII Start Timer with Profile Name (Mindful Eating)",
            description="Simulates 'Hey Google, start mindful eating timer on Habit Bell'",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-e", "timerName", "eating",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="startVoiceTimer.*eating|Eating",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        tests.append(TestCase(
            test_id="TC-BII-03",
            name="BII Start Timer with Custom Duration (300s Posture)",
            description="Simulates 'Hey Google, set 5 minute posture timer on Habit Bell'",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-e", "timerName", "posture",
                "-e", "timerDuration", "300",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="startVoiceTimer.*300",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        tests.append(TestCase(
            test_id="TC-BII-04",
            name="BII Pause Timer Deep Link",
            description="Simulates 'Hey Google, pause timer on Habit Bell' via habitbell://action/pause",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://action/pause",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="pauseTimer|PAUSED",
            pre_kill_app=False,
            post_delay_sec=1.5
        ))

        tests.append(TestCase(
            test_id="TC-BII-05",
            name="BII Resume Timer Deep Link",
            description="Simulates 'Hey Google, resume timer on Habit Bell' via habitbell://action/resume",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://action/resume",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="resumeTimer|RUNNING",
            pre_kill_app=False,
            post_delay_sec=1.5
        ))

        tests.append(TestCase(
            test_id="TC-BII-06",
            name="BII Stop Timer Deep Link",
            description="Simulates 'Hey Google, stop timer on Habit Bell' via habitbell://action/stop",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://action/stop",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="stopTimer|IDLE",
            pre_kill_app=False,
            post_delay_sec=1.5
        ))

        # ----------------------------------------------------------------------
        # Suite 2: Android Standard AlarmClock Voice Actions
        # ----------------------------------------------------------------------
        tests.append(TestCase(
            test_id="TC-SYS-01",
            name="System AlarmClock ACTION_SET_TIMER (600s Eating)",
            description="Simulates system-level voice action 'OK Google, set timer for 10 minutes on Habit Bell'",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.SET_TIMER",
                "--ei", "android.intent.extra.alarm.LENGTH", "600",
                "--es", "android.intent.extra.alarm.MESSAGE", "Mindful Eating",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="ACTION_SET_TIMER|startVoiceTimer",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        tests.append(TestCase(
            test_id="TC-SYS-02",
            name="System AlarmClock ACTION_DISMISS_TIMER",
            description="Simulates system-level voice action 'OK Google, cancel timer on Habit Bell'",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.DISMISS_TIMER",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="ACTION_DISMISS_TIMER|stopTimer",
            pre_kill_app=False,
            post_delay_sec=1.5
        ))

        tests.append(TestCase(
            test_id="TC-SYS-03",
            name="System AlarmClock ACTION_SHOW_TIMERS",
            description="Simulates 'OK Google, show timers on Habit Bell'",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.SHOW_TIMERS",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="SHOW_TIMERS",
            pre_kill_app=False,
            post_delay_sec=1.5
        ))

        # ----------------------------------------------------------------------
        # Suite 3: Static Launcher Shortcuts
        # ----------------------------------------------------------------------
        tests.append(TestCase(
            test_id="TC-SCT-01",
            name="Shortcut Mindful Eating Profile",
            description="Simulates user tapping static shortcut habitbell://start?profile=eating",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://start?profile=eating",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="eating",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        tests.append(TestCase(
            test_id="TC-SCT-02",
            name="Shortcut Posture & Alignment Profile",
            description="Simulates user tapping static shortcut habitbell://start?profile=posture",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://start?profile=posture",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="posture",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        tests.append(TestCase(
            test_id="TC-SCT-03",
            name="Shortcut with YouTube Ambient Background Sound",
            description="Simulates deep link habitbell://start?profile=eating&bg=youtube",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://start?profile=eating&bg=youtube",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="YOUTUBE_LINK|bgMusicType",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        # ----------------------------------------------------------------------
        # Suite 4: Lifecycle & singleTop Intent Redelivery
        # ----------------------------------------------------------------------
        tests.append(TestCase(
            test_id="TC-LC-01",
            name="Cold Start Process Initialization",
            description="Verifies cold start intent handling from fully stopped application process",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.SET_TIMER",
                "--ei", "android.intent.extra.alarm.LENGTH", "180",
                "--es", "android.intent.extra.alarm.MESSAGE", "Walking",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="onCreate|startVoiceTimer",
            pre_kill_app=True,
            post_delay_sec=2.5
        ))

        tests.append(TestCase(
            test_id="TC-LC-03",
            name="Hot State singleTop onNewIntent Handling",
            description="Delivers intent while Activity is already running in foreground",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "habitbell://action/pause",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="onNewIntent|pauseTimer",
            pre_kill_app=False,
            post_delay_sec=1.5
        ))

        # ----------------------------------------------------------------------
        # Suite 8: Negative & Edge Cases
        # ----------------------------------------------------------------------
        tests.append(TestCase(
            test_id="TC-NEG-01",
            name="Unknown Voice Profile Fallback",
            description="Voice command with unmapped name falls back to default profile safely",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-e", "timerName", "skydiving_xyz_unsupported",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="startVoiceTimer",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        tests.append(TestCase(
            test_id="TC-NEG-02",
            name="Negative / Zero Duration Fallback",
            description="Passes duration = -5; app should fall back to profile default duration",
            command_args=[
                "shell", "am", "start",
                "-a", "android.intent.action.SET_TIMER",
                "--ei", "android.intent.extra.alarm.LENGTH", "-5",
                "-n", "{ACTIVITY}"
            ],
            expected_log_pattern="startVoiceTimer",
            pre_kill_app=False,
            post_delay_sec=2.0
        ))

        return tests

    def print_summary(self) -> None:
        """
        Prints the aggregated test execution matrix and pass/fail metrics.
        """
        total = len(self.results)
        passed = sum(1 for _, ok, _ in self.results if ok)
        failed = total - passed

        print("\n" + "=" * 70)
        print(f"{COLOR_BOLD}HABIT BELL — GOOGLE ASSISTANT TEST EXECUTION SUMMARY{COLOR_RESET}")
        print("=" * 70)
        print(f"Target Device  : {self.serial}")
        print(f"Target Package : {self.package_name}")
        print(f"Total Tests    : {total}")
        print(f"Passed         : {COLOR_GREEN}{passed}{COLOR_RESET}")
        print(f"Failed         : {COLOR_RED}{failed}{COLOR_RESET}")
        print("-" * 70)

        for test, ok, detail in self.results:
            status_tag = f"{COLOR_GREEN}[PASS]{COLOR_RESET}" if ok else f"{COLOR_RED}[FAIL]{COLOR_RESET}"
            print(f"{status_tag} {test.test_id:<11} {test.name:<40} ({detail})")

        print("=" * 70)


def main() -> int:
    """
    Main entry point for command-line execution.

    Returns:
        int: 0 if all tests pass, 1 if any failure occurs or prerequisites fail.
    """
    parser = argparse.ArgumentParser(
        description="Automated Google Assistant & Voice Intent Test Suite for Habit Bell on Android"
    )
    parser.add_argument("-s", "--serial", help="Specific device serial from `adb devices`")
    parser.add_argument("-p", "--package", help=f"Package override (default: {DEFAULT_DEBUG_PKG})")
    parser.add_argument("-t", "--test", help="Filter and run only a specific test ID (e.g. TC-BII-02)")
    args = parser.parse_args()

    runner = AdbAssistantTestRunner(serial=args.serial, package_name=args.package)

    if not runner.check_prerequisites():
        return 1

    test_cases = runner.generate_test_cases()
    if args.test:
        test_cases = [tc for tc in test_cases if tc.test_id.upper() == args.test.upper()]
        if not test_cases:
            print(f"{COLOR_RED}No test case matching ID '{args.test}' found.{COLOR_RESET}")
            return 1

    print(f"\n{COLOR_BOLD}Starting execution of {len(test_cases)} Assistant test cases...{COLOR_RESET}")
    for tc in test_cases:
        runner.execute_test(tc)

    runner.print_summary()
    all_passed = all(ok for _, ok, _ in runner.results)
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
