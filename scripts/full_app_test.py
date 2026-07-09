#!/usr/bin/env python3
"""End-to-end smoke test for GrammPlayer on a connected Android TV device/emulator."""

import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
PKG = "com.aes.grammplayer"
OUT = Path(__file__).resolve().parents[1] / "test-screenshots" / "full-app"
SER = "emulator-5554"

KEY_BACK = 4
KEY_DPAD_DOWN = 20
KEY_DPAD_CENTER = 23
KEY_DPAD_RIGHT = 22


@dataclass
class StepResult:
    name: str
    passed: bool
    activity: str = ""
    notes: str = ""
    fatal: list[str] = field(default_factory=list)


def adb(*args: str, serial: str = SER) -> str:
    cmd = [str(ADB), "-s", serial, *args]
    return subprocess.run(cmd, capture_output=True, text=True).stdout.strip()


def adb_shell(cmd: str, serial: str = SER) -> str:
    return adb("shell", cmd, serial=serial)


def keyevent(code: int, serial: str = SER) -> None:
    adb_shell(f"input keyevent {code}", serial=serial)


def tap(x: int, y: int, serial: str = SER) -> None:
    adb_shell(f"input tap {x} {y}", serial=serial)


def clear_logcat(serial: str = SER) -> None:
    adb("logcat", "-c", serial=serial)


def fatal_errors(since_marker: str = "", serial: str = SER) -> list[str]:
    out = adb("logcat", "-d", serial=serial)
    lines = out.splitlines()
    if since_marker:
        try:
            idx = next(i for i, line in enumerate(lines) if since_marker in line)
            lines = lines[idx:]
        except StopIteration:
            pass
    fatals = []
    for line in lines:
        if "AndroidRuntime" in line and "FATAL EXCEPTION" in line:
            fatals.append(line)
        if "FATAL EXCEPTION" in line or "Process: com.aes.grammplayer" in line:
            if "FATAL" in line or "java.lang" in line:
                fatals.append(line.strip())
    return fatals[:5]


def resumed_activity(serial: str = SER) -> str:
    out = adb_shell("dumpsys activity activities", serial=serial)
    for line in out.splitlines():
        if "topResumedActivity" in line:
            m = re.search(r"com\.aes\.grammplayer/[^\s}]+", line)
            if m:
                return m.group(0).split("/")[-1]
    return "unknown"


def wait_activity(expected_suffix: str, timeout: float = 12.0, serial: str = SER) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        act = resumed_activity(serial)
        if expected_suffix in act:
            return True
        time.sleep(0.5)
    return False


def screenshot(name: str, serial: str = SER) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.png"
    with path.open("wb") as f:
        subprocess.run(
            [str(ADB), "-s", serial, "exec-out", "screencap", "-p"],
            stdout=f,
            check=False,
        )


def start_activity(component: str, extras: str = "", serial: str = SER) -> None:
    cmd = f"am start -W -n {PKG}/{component}"
    if extras:
        cmd += f" {extras}"
    adb_shell(cmd, serial=serial)


def run_step(name: str, component: str, expected_suffix: str, wait: float = 4.0,
             extras: str = "", interact=None, serial: str = SER) -> StepResult:
    marker = f"FULL_APP_TEST:{name}"
    adb_shell(f"log -t FULL_APP_TEST '{marker}'", serial=serial)
    clear_logcat(serial)
    start_activity(component, extras=extras, serial=serial)
    time.sleep(wait)
    if interact:
        interact(serial)
        time.sleep(2.0)
    act = resumed_activity(serial)
    fatals = fatal_errors(serial=serial)
    passed = expected_suffix in act and not fatals
    screenshot(name.replace(" ", "_").lower(), serial=serial)
    return StepResult(name=name, passed=passed, activity=act, notes=expected_suffix, fatal=fatals)


def login_test_user(serial: str = SER) -> StepResult:
    marker = "FULL_APP_TEST:login_flow"
    adb_shell(f"log -t FULL_APP_TEST '{marker}'", serial=serial)
    clear_logcat(serial)
    start_activity(".ui.features.authentication.LoginActivity", serial=serial)
    time.sleep(2)
    # country code 1, phone 00 => +100 test account
    tap(360, 420, serial)
    adb_shell("input text 1", serial=serial)
    tap(620, 420, serial)
    adb_shell("input text 00", serial=serial)
    tap(960, 520, serial)  # submit
    time.sleep(3)
    # auth code step
    tap(640, 500, serial)
    adb_shell("input text 12345", serial=serial)
    tap(960, 580, serial)
    time.sleep(8)
    act = resumed_activity(serial)
    fatals = fatal_errors(serial=serial)
    passed = "MainActivity" in act and not fatals
    screenshot("login_flow", serial=serial)
    return StepResult(name="Login (test user +100)", passed=passed, activity=act, fatal=fatals)


def cold_start_flow(serial: str = SER) -> list[StepResult]:
    results = []
    adb_shell(f"pm clear {PKG}", serial=serial)
    time.sleep(1)
    clear_logcat(serial)
    start_activity(".ui.features.onboarding.OnboardingActivity", serial=serial)
    time.sleep(12)  # TDLib init

    act = resumed_activity(serial)
    fatals = fatal_errors(serial=serial)
    screenshot("cold_start", serial=serial)
    results.append(StepResult(
        name="Cold start / splash",
        passed=not fatals and act != "unknown",
        activity=act,
        fatal=fatals,
    ))

    if "OnboardingActivity" in act or "onboarding" in act.lower():
        tap(960, 620, serial)  # get started
        time.sleep(2)
    act = resumed_activity(serial)
    if "TermsActivity" in act:
        tap(640, 560, serial)  # checkbox area
        time.sleep(0.5)
        tap(640, 640, serial)  # login/proceed
        time.sleep(2)
        results.append(login_test_user(serial))
    elif "LoginActivity" in act:
        results.append(login_test_user(serial))
    elif "MainActivity" in act:
        results.append(StepResult(name="Login (skipped)", passed=True, activity="MainActivity",
                                  notes="Already authenticated"))
    else:
        results.append(StepResult(name="Auth flow", passed=False, activity=act,
                                  notes="Expected Terms, Login, or Main"))
    return results


def screen_tests(serial: str = SER, skip_clear: bool = True) -> list[StepResult]:
    results = []
    if not skip_clear:
        adb_shell(f"pm clear {PKG}", serial=serial)
        time.sleep(1)

    def open_history_details(s):
        start_activity(".ui.features.history.HistoryGridActivity", serial=s)
        time.sleep(8)
        keyevent(KEY_DPAD_DOWN, s)
        time.sleep(0.5)
        keyevent(KEY_DPAD_DOWN, s)
        time.sleep(0.5)
        keyevent(KEY_DPAD_CENTER, s)
        time.sleep(5)

    screens = [
        ("Main dashboard", ".ui.features.dashboard.MainActivity", "MainActivity", 3.0, ""),
        ("Chats grid", ".ui.features.chats.ChatsGridActivity", "ChatsGridActivity", 5.0, ""),
        ("Messages grid", ".ui.features.messages.MessageGridActivity", "MessageGridActivity", 5.0,
         "--el chat_id 1 --es chat_title MoviesChannel"),
        ("History grid", ".ui.features.history.HistoryGridActivity", "HistoryGridActivity", 6.0, ""),
        ("Settings", ".ui.features.settings.SettingsActivity", "SettingsActivity", 3.0, ""),
        ("Terms screen", ".ui.features.onboarding.TermsActivity", "TermsActivity", 2.0, ""),
    ]

    for name, component, expected, wait, extras in screens:
        res = run_step(name, component, expected, wait=wait, extras=extras, serial=serial)
        results.append(res)
        keyevent(KEY_BACK, serial)
        time.sleep(1)

    # Details via history navigation
    marker = "FULL_APP_TEST:media_details"
    adb_shell(f"log -t FULL_APP_TEST '{marker}'", serial=serial)
    clear_logcat(serial)
    open_history_details(serial)
    act = resumed_activity(serial)
    fatals = fatal_errors(serial=serial)
    results.append(StepResult(
        name="Media details",
        passed="MediaDetailsActivity" in act and not fatals,
        activity=act,
        fatal=fatals,
    ))
    screenshot("media_details", serial=serial)
    keyevent(KEY_BACK, serial)
    time.sleep(1)

    # Dashboard interactions: open history hero from main (2nd hero card)
    start_activity(".ui.features.dashboard.MainActivity", serial=serial)
    time.sleep(3)
    for _ in range(2):
        keyevent(KEY_DPAD_DOWN, serial)
        time.sleep(0.3)
    for _ in range(2):
        keyevent(KEY_DPAD_RIGHT, serial)
        time.sleep(0.3)
    keyevent(KEY_DPAD_CENTER, serial)
    time.sleep(5)
    act = resumed_activity(serial)
    fatals = fatal_errors(serial=serial)
    results.append(StepResult(
        name="Dashboard → History hero",
        passed="HistoryGridActivity" in act and not fatals,
        activity=act,
        fatal=fatals,
    ))
    screenshot("dashboard_history_hero", serial=serial)
    keyevent(KEY_BACK, serial)
    time.sleep(1)

    # Chats -> first chat -> messages
    start_activity(".ui.features.chats.ChatsGridActivity", serial=serial)
    time.sleep(5)
    keyevent(KEY_DPAD_DOWN, serial)
    time.sleep(0.4)
    keyevent(KEY_DPAD_CENTER, serial)
    time.sleep(5)
    act = resumed_activity(serial)
    fatals = fatal_errors(serial=serial)
    results.append(StepResult(
        name="Chats → Messages",
        passed="MessageGridActivity" in act and not fatals,
        activity=act,
        fatal=fatals,
    ))
    screenshot("chats_to_messages", serial=serial)
    keyevent(KEY_BACK, serial)

    return results


def main() -> int:
    if not ADB.exists():
        print("adb not found", file=sys.stderr)
        return 1

    devices = adb("devices")
    if SER not in devices:
        print(f"Device {SER} not connected. Connected:\n{devices}", file=sys.stderr)
        return 1

    print(f"Testing GrammPlayer on {SER}")
    print(f"Screenshots -> {OUT}\n")

    all_results: list[StepResult] = []

    print("=== Phase 1: Cold start + auth ===")
    all_results.extend(cold_start_flow(SER))

    print("=== Phase 2: Screen smoke tests ===")
    all_results.extend(screen_tests(SER))

    passed = sum(1 for r in all_results if r.passed)
    total = len(all_results)

    print("\n=== RESULTS ===")
    for r in all_results:
        status = "PASS" if r.passed else "FAIL"
        print(f"[{status}] {r.name}")
        print(f"        activity={r.activity}")
        if r.notes:
            print(f"        expected~={r.notes}")
        if r.fatal:
            for f in r.fatal:
                print(f"        FATAL: {f}")

    print(f"\n{passed}/{total} steps passed")
    summary = {
        "device": SER,
        "passed": passed,
        "total": total,
        "results": [
            {"name": r.name, "passed": r.passed, "activity": r.activity, "fatal": r.fatal}
            for r in all_results
        ],
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "results.json").write_text(json.dumps(summary, indent=2))
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())