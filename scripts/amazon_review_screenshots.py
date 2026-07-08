#!/usr/bin/env python3
"""Capture Amazon Appstore review screenshots at 1920x1080."""

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
OUT = Path(__file__).resolve().parents[1] / "amazon-review-screenshots"
APK = Path(__file__).resolve().parents[1] / "app/build/outputs/apk/debug/tgPlayer_v1.0_(1)_release.apk"
SEED_WAIT_SEC = 90

KEY_BACK = 4
KEY_DPAD_DOWN = 20
KEY_DPAD_RIGHT = 22
KEY_DPAD_CENTER = 23


def adb(*args: str, serial: str = SER) -> str:
    return subprocess.run(
        [str(ADB), "-s", serial, *args],
        capture_output=True,
        text=True,
    ).stdout.strip()


def shell(cmd: str, serial: str = SER) -> str:
    return adb("shell", cmd, serial=serial)


def keyevent(code: int, serial: str = SER) -> None:
    shell(f"input keyevent {code}", serial=serial)


def tap(x: int, y: int, serial: str = SER) -> None:
    shell(f"input tap {x} {y}", serial=serial)


def screenshot(name: str, serial: str = SER) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.png"
    with path.open("wb") as f:
        subprocess.run(
            [str(ADB), "-s", serial, "exec-out", "screencap", "-p"],
            stdout=f,
            check=False,
        )
    print(f"  saved {path.name}", flush=True)
    return path


def resumed_activity(serial: str = SER) -> str:
    out = shell("dumpsys activity activities", serial=serial)
    for line in out.splitlines():
        if "topResumedActivity" in line:
            m = re.search(r"com\.aes\.grammplayer/[^\s}]+", line)
            if m:
                return m.group(0).split("/")[-1]
    return "unknown"


def wait_activity(expected: str, timeout: float = 25.0, serial: str = SER) -> bool:
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


def login_review_user(serial: str = SER) -> None:
    start(".ui.features.authentication.LoginActivity", serial=serial)
    time.sleep(2)
    screenshot("01_login_phone", serial=serial)

    tap(360, 420, serial)
    shell("input text 1", serial=serial)
    tap(620, 420, serial)
    shell("input text 00", serial=serial)
    tap(960, 520, serial)
    time.sleep(3)
    screenshot("02_login_code", serial=serial)

    tap(640, 500, serial)
    shell("input text 12345", serial=serial)
    tap(960, 580, serial)
    wait_activity("MainActivity", timeout=30, serial=serial)
    time.sleep(2)


def ensure_auth(fresh: bool, serial: str = SER) -> None:
    if fresh:
        print(f"Waiting {SEED_WAIT_SEC}s for database seed...", flush=True)
        shell(f"pm clear {PKG}", serial=serial)
        time.sleep(2)
        start(".ui.features.onboarding.OnboardingActivity", serial=serial)
        time.sleep(SEED_WAIT_SEC)

    act = resumed_activity(serial)
    if "MainActivity" in act:
        print("Already authenticated", flush=True)
        return

    if "OnboardingActivity" in act:
        screenshot("00_onboarding", serial=serial)
        tap(960, 620, serial)
        time.sleep(2)

    act = resumed_activity(serial)
    if "TermsActivity" in act:
        screenshot("00_terms", serial=serial)
        tap(640, 560, serial)
        time.sleep(0.5)
        tap(640, 640, serial)
        time.sleep(2)

    login_review_user(serial)


def capture_dashboard(serial: str = SER) -> None:
    start(".ui.features.dashboard.MainActivity", serial=serial)
    time.sleep(3)
    screenshot("03_dashboard", serial=serial)
    keyevent(KEY_DPAD_DOWN, serial)
    time.sleep(0.5)
    screenshot("04_dashboard_chats_focus", serial=serial)
    keyevent(KEY_DPAD_RIGHT, serial)
    time.sleep(0.5)
    screenshot("05_dashboard_history_focus", serial=serial)


def capture_chats(serial: str = SER) -> None:
    start(".ui.features.chats.ChatsGridActivity", serial=serial)
    time.sleep(14)
    keyevent(KEY_DPAD_DOWN, serial)
    time.sleep(0.5)
    screenshot("06_chats_grid", serial=serial)
    keyevent(KEY_DPAD_DOWN, serial)
    time.sleep(0.5)
    screenshot("07_chats_grid_focus", serial=serial)


def capture_messages_and_details(serial: str = SER) -> None:
    start(
        ".ui.features.messages.MessageGridActivity",
        '--el chat_id 1 --es chat_title "Movies Channel"',
        serial=serial,
    )
    time.sleep(16)
    screenshot("08_messages_grid", serial=serial)
    tap(320, 340, serial)
    time.sleep(0.5)
    screenshot("09_messages_focus", serial=serial)
    keyevent(KEY_DPAD_CENTER, serial)
    wait_activity("MediaDetailsActivity", timeout=20, serial=serial)
    time.sleep(12)
    screenshot("10_media_details", serial=serial)


def capture_download_and_play(serial: str = SER) -> None:
    if "MediaDetailsActivity" not in resumed_activity(serial):
        capture_messages_and_details(serial)

    tap(1500, 980, serial)
    time.sleep(0.5)
    keyevent(KEY_DPAD_CENTER, serial)
    time.sleep(3)
    screenshot("11_download_started", serial=serial)

    deadline = time.time() + 120
    while time.time() < deadline:
        time.sleep(8)
        screenshot("12_download_progress", serial=serial)
        shell("uiautomator dump /sdcard/window_dump.xml", serial=serial)
        xml = shell("cat /sdcard/window_dump.xml", serial=serial)
        if "action_play" in xml:
            break

    time.sleep(2)
    screenshot("13_ready_to_play", serial=serial)
    tap(1180, 980, serial)
    time.sleep(0.5)
    keyevent(KEY_DPAD_CENTER, serial)
    wait_activity("InAppPlaybackActivity", timeout=20, serial=serial)
    time.sleep(5)
    screenshot("14_fullscreen_playback", serial=serial)
    keyevent(KEY_BACK, serial)
    time.sleep(2)


def capture_history(serial: str = SER) -> None:
    start(".ui.features.history.HistoryGridActivity", serial=serial)
    time.sleep(8)
    screenshot("15_history_grid", serial=serial)


def build_final_folder() -> None:
    final = OUT / "for-amazon"
    final.mkdir(exist_ok=True)
    picks = {
        "01_home_dashboard.png": "03_dashboard.png",
        "02_browse_chats.png": "06_chats_grid.png",
        "03_media_library.png": "08_messages_grid.png",
        "04_title_details.png": "10_media_details.png",
        "05_ready_to_play.png": "13_ready_to_play.png",
        "06_download_in_progress.png": "11_download_started.png",
        "07_login.png": "02_login_code.png",
        "08_watch_history.png": "15_history_grid.png",
    }
    for dest, src in picks.items():
        src_path = OUT / src
        if src_path.exists():
            (final / dest).write_bytes(src_path.read_bytes())


def main() -> int:
    fresh = "--fresh" in sys.argv
    if not ADB.exists():
        print("adb not found", file=sys.stderr)
        return 1
    if SER not in adb("devices"):
        print(f"{SER} not connected", file=sys.stderr)
        return 1

    if APK.exists():
        print(f"Installing {APK.name}...", flush=True)
        adb("install", "-r", str(APK), serial=SER)
        time.sleep(2)

    print(f"Capturing -> {OUT}", flush=True)
    ensure_auth(fresh=fresh, serial=SER)
    capture_dashboard(SER)
    capture_chats(SER)
    capture_messages_and_details(SER)
    capture_download_and_play(SER)
    capture_history(SER)
    build_final_folder()

    final = OUT / "for-amazon"
    shots = sorted(final.glob("*.png"))
    manifest = {
        "device": SER,
        "resolution": "1920x1080",
        "review_login": {"country_code": "+1", "phone": "00", "code": "12345"},
        "screenshots": [p.name for p in shots],
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2))
    print(f"\nAmazon-ready set ({len(shots)} images) -> {final}", flush=True)
    for p in shots:
        print(f"  {p.name}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())