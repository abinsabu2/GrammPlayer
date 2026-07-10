#!/usr/bin/env python3
"""Record a distributable demo video of the GrammPlayer login flow on Android TV."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
PKG = "com.aes.grammplayer"
SER = "emulator-5554"
ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "store-assets" / "demo-videos"
REMOTE_VIDEO = "/sdcard/grammplayer_login_demo.mp4"
LOCAL_VIDEO = OUT_DIR / "grammplayer_login_demo.mp4"
SEED_WAIT_SEC = 45

KEY_DPAD_DOWN = 20
KEY_DPAD_CENTER = 23


def adb(*args: str, serial: str = SER) -> str:
    return subprocess.run(
        [str(ADB), "-s", serial, *args],
        capture_output=True,
        text=True,
    ).stdout.strip()


def shell(cmd: str, serial: str = SER) -> str:
    return adb("shell", cmd, serial=serial)


def tap(x: int, y: int, serial: str = SER) -> None:
    shell(f"input tap {x} {y}", serial=serial)


def keyevent(code: int, serial: str = SER) -> None:
    shell(f"input keyevent {code}", serial=serial)


def resumed_activity(serial: str = SER) -> str:
    out = shell("dumpsys activity activities", serial=serial)
    for line in out.splitlines():
        if "topResumedActivity" in line:
            match = re.search(r"com\.aes\.grammplayer/[^\s}]+", line)
            if match:
                return match.group(0).split("/")[-1]
    return "unknown"


def wait_activity(expected: str, timeout: float = 30.0, serial: str = SER) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if expected in resumed_activity(serial):
            return True
        time.sleep(0.5)
    return False


def start(component: str, extras: str = "", serial: str = SER) -> None:
    cmd = f"am start -W -n {PKG}/{component}"
    if extras:
        cmd += f" {extras}"
    shell(cmd, serial=serial)


def start_recording(serial: str = SER) -> None:
    shell(f"rm -f {REMOTE_VIDEO}", serial=serial)
    adb(
        "shell",
        "screenrecord",
        "--time-limit",
        "120",
        "--bit-rate",
        "8000000",
        "--size",
        "1920x1080",
        REMOTE_VIDEO,
        serial=serial,
    )


def stop_recording(serial: str = SER) -> None:
    shell("pkill -2 screenrecord", serial=serial)
    time.sleep(2)


def pull_video(serial: str = SER) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    adb("pull", REMOTE_VIDEO, str(LOCAL_VIDEO), serial=serial)
    shell(f"rm -f {REMOTE_VIDEO}", serial=serial)


def run_login_demo(serial: str = SER) -> None:
    print("Preparing fresh install for demo...", flush=True)
    shell(f"pm clear {PKG}", serial=serial)
    time.sleep(2)

    print(f"Waiting {SEED_WAIT_SEC}s for database seed...", flush=True)
    start(".ui.features.onboarding.OnboardingActivity", serial=serial)
    time.sleep(SEED_WAIT_SEC)

    print("Starting screen recording...", flush=True)
    recorder = subprocess.Popen(
        [
            str(ADB),
            "-s",
            serial,
            "shell",
            "screenrecord",
            "--time-limit",
            "120",
            "--bit-rate",
            "8000000",
            "--size",
            "1920x1080",
            REMOTE_VIDEO,
        ]
    )
    time.sleep(1)

    print("Onboarding...", flush=True)
    tap(960, 620, serial)
    time.sleep(3)

    print("Terms...", flush=True)
    if "TermsActivity" in resumed_activity(serial):
        tap(640, 560, serial)
        time.sleep(0.8)
        tap(640, 640, serial)
        time.sleep(3)

    print("Login phone...", flush=True)
    wait_activity("LoginActivity", serial=serial)
    time.sleep(2)
    tap(360, 420, serial)
    shell("input text 1", serial=serial)
    time.sleep(0.8)
    tap(620, 420, serial)
    shell("input text 00", serial=serial)
    time.sleep(1.2)
    tap(960, 520, serial)
    time.sleep(4)

    print("Login code...", flush=True)
    tap(640, 500, serial)
    shell("input text 12345", serial=serial)
    time.sleep(1.2)
    tap(960, 580, serial)
    wait_activity("MainActivity", timeout=35, serial=serial)
    time.sleep(4)

    print("Dashboard tour...", flush=True)
    keyevent(KEY_DPAD_DOWN, serial)
    time.sleep(1.5)
    keyevent(KEY_DPAD_CENTER, serial)
    time.sleep(6)
    keyevent(4, serial)  # BACK
    time.sleep(2)

    print("Stopping recording...", flush=True)
    shell("pkill -2 screenrecord", serial=serial)
    recorder.wait(timeout=10)
    time.sleep(2)


def write_manifest() -> None:
    manifest = {
        "title": "GrammPlayer Login Demo",
        "file": LOCAL_VIDEO.name,
        "resolution": "1920x1080",
        "review_login": {
            "country_code": "1",
            "phone": "00",
            "auth_code": "12345",
            "full_phone": "+100",
        },
        "flow": [
            "Onboarding welcome",
            "Terms acceptance",
            "Phone entry (+1 00)",
            "Verification code (12345)",
            "Dashboard with Chats and History",
            "Open Chats preview",
        ],
    }
    (OUT_DIR / "grammplayer_login_demo.json").write_text(
        json.dumps(manifest, indent=2) + "\n"
    )


def main() -> int:
    if not ADB.exists():
        print("adb not found", file=sys.stderr)
        return 1
    if SER not in adb("devices"):
        print(f"{SER} not connected", file=sys.stderr)
        return 1

    run_login_demo()
    pull_video()
    write_manifest()

    if not LOCAL_VIDEO.exists() or LOCAL_VIDEO.stat().st_size < 10_000:
        print("Recording failed or output too small", file=sys.stderr)
        return 1

    size_mb = LOCAL_VIDEO.stat().st_size / (1024 * 1024)
    print(f"\nDemo video saved -> {LOCAL_VIDEO} ({size_mb:.1f} MB)", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())