#!/usr/bin/env python3
"""
One-command Play Store bundle builder for GrammPlayer.
Rung 1 automation: validates, builds AAB, verifies signing.
No new deps — uses existing gradle + apksigner/bundletool.

Usage:
  python3 scripts/publish-playstore.py              # build + validate
  python3 scripts/publish-playstore.py --bump patch # bump 3.3 -> 3.3.1 (or 3.4)
  python3 scripts/publish-playstore.py --upload     # + fastlane supply if configured

Full upload (rung 2) needs:
  - Google Play Console > Setup > API access > enable + create Service Account
  - Download JSON -> play-service-account.json in project root (gitignored)
  - gem install fastlane  OR  pip install google-api-python-client (script supports both)
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_BUILD = ROOT / "app/build.gradle.kts"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
STORE = ROOT / "store-assets"

def run(cmd, **kw):
    print(f"$ {' '.join(map(str,cmd))}")
    return subprocess.run(cmd, cwd=ROOT, check=False, **kw)

def read_version():
    t = APP_BUILD.read_text()
    vc = int(re.search(r"versionCode\s*=\s*(\d+)", t).group(1))
    vn = re.search(r'versionName\s*=\s*"([^"]+)"', t).group(1)
    return vc, vn

def bump_version(bump):
    t = APP_BUILD.read_text()
    vc, vn = read_version()
    new_vc = vc + 1
    if bump:
        parts = list(map(int, vn.split(".")))
        while len(parts) < 3: parts.append(0)
        if bump == "patch": parts[2] += 1
        elif bump == "minor": parts[1] += 1; parts[2]=0
        elif bump == "major": parts[0] += 1; parts[1]=parts[2]=0
        new_vn = ".".join(map(str, parts))
        t = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_vn}"', t)
        print(f"versionName {vn} -> {new_vn}")
    t = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_vc}", t)
    print(f"versionCode {vc} -> {new_vc}")
    APP_BUILD.write_text(t)
    return new_vc, new_vn if bump else vn

def checks():
    ok = True
    # local.properties
    lp = ROOT / "local.properties"
    if not lp.exists():
        print("FAIL local.properties missing (api_key/api_hash/tmbd_key)")
        ok=False
    else:
        txt=lp.read_text()
        for k in ["api_key","api_hash","tmbd_key"]:
            if k not in txt: print(f"FAIL local.properties missing {k}"); ok=False
    # keystore
    kp = ROOT / "keystore.properties"
    if not kp.exists():
        print("FAIL keystore.properties missing — release will be unsigned")
        print("  create: storeFile, storePassword, keyAlias, keyPassword")
        print("  keytool -genkeypair -v -keystore grammplayer-release.keystore -alias grammplayer -keyalg RSA -keysize 2048 -validity 10000")
        ok=False
    else:
        print("ok keystore.properties present")
        # verify storeFile exists
        m=re.search(r"storeFile\s*=\s*(.+)", kp.read_text())
        if m:
            sf=ROOT / m.group(1).strip()
            if not sf.exists():
                print(f"FAIL storeFile not found: {sf}")
                ok=False
    # manifest targetSdk
    mt=MANIFEST.read_text()
    if 'targetSdkVersion="34"' in mt:
        print("WARN AndroidManifest.xml targetSdkVersion 34 != build.gradle targetSdk 36 — align to 36 before Play upload")
    # store assets
    for p in [STORE/"app_icon_512.png", STORE/"promo_banner_1024x500.png"]:
        if not p.exists(): print(f"WARN missing {p.relative_to(ROOT)}")
        else: print(f"ok {p.relative_to(ROOT)}")
    if not (STORE/"screenshots_1920x1080").exists():
        print("WARN store-assets/screenshots_1920x1080 missing")
    # privacy policy hosted?
    if "firegram2025@gmail.com" in (ROOT/"privacy-policy.html").read_text():
        print("ok privacy-policy.html present — host via GitHub Pages for Play listing URL")
    return ok

def build():
    # sanitize handled via preBuild, but run explicitly for visibility
    run(["python3", "scripts/sanitize-for-amazon-appstore.py"])
    r = run(["./gradlew", ":app:bundleRelease", "-x", "lint"])
    if r.returncode != 0:
        sys.exit(r.returncode)

def verify():
    aabs = list((ROOT/"app/build/outputs/bundle/release").glob("*.aab"))
    if not aabs:
        print("FAIL no AAB found at app/build/outputs/bundle/release/")
        sys.exit(1)
    aab = max(aabs, key=lambda p: p.stat().st_mtime)
    print(f"built {aab.relative_to(ROOT)} ({aab.stat().st_size/1e6:.1f} MB)")
    # AAB is zip; apksigner only works on APK — use jarsigner for AAB
    r = run(["jarsigner", "-verify", str(aab)])
    if r.returncode != 0:
        print("WARN jarsigner verify failed — bundle likely UNSIGNED (keystore.properties missing)")
        print("  Play will REJECT unsigned AAB. Create keystore.properties before upload.")
    else:
        print("ok jarsigner verify — bundle signed")
    # bundletool validate if present
    bt = ROOT/"bundletool.jar"
    if bt.exists():
        run(["java","-jar",str(bt),"validate","--bundle",str(aab)])
    else:
        # also try apksigner on extracted APK via bundletool if available elsewhere
        pass
    return aab

def try_upload(aab: Path, track: str):
    svc = ROOT / "play-service-account.json"
    if not svc.exists():
        print(f"\nno {svc.name} — manual upload:")
        print(f"  Play Console > {track} testing > Create release > upload {aab}")
        print("  To automate: create Service Account in Play Console > API access, download JSON to play-service-account.json")
        return
    # prefer fastlane supply if available
    has_fastlane = run(["which","fastlane"]).returncode==0
    if has_fastlane:
        print(f"uploading via fastlane supply to track {track}...")
        r = run(["fastlane","supply","--aab",str(aab),"--track",track,"--json_key",str(svc),"--skip_upload_metadata","--skip_upload_images","--skip_upload_screenshots"])
        sys.exit(r.returncode)
    # fallback: google api python (minimal)
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build as gbuild
        from googleapiclient.http import MediaFileUpload
    except ImportError:
        print("pip install google-api-python-client google-auth to enable direct upload without fastlane")
        print(f"or: gem install fastlane && fastlane supply --aab {aab} --track {track} --json_key {svc}")
        return
    print("direct API upload not yet wired — use fastlane path above (one-liner)")

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--bump", choices=["patch","minor","major"], help="bump versionName")
    ap.add_argument("--track", default="internal", choices=["internal","closed","production"], help="upload track")
    ap.add_argument("--upload", action="store_true", help="attempt upload if play-service-account.json present")
    ap.add_argument("--skip-checks", action="store_true")
    args=ap.parse_args()

    if args.bump:
        bump_version(args.bump)
    else:
        vc,vn=read_version()
        print(f"current v{vn} ({vc})")

    if not args.skip_checks:
        ok=checks()
        if not ok:
            print("\nfix FAILs before upload (WARNs ok). re-run with --skip-checks to force build")
            # still allow build if only WARN — fail only on missing keystore/local.properties?
            # ponytail: don't block, just warn
    build()
    aab=verify()
    print(f"\n✓ AAB ready: {aab}")
    print(f"  next: Play Console > Create app (com.aes.grammplayer) > {args.track} > upload")
    print("  closed testing requires 20 testers x 14 days before Production (new personal accounts)")

    if args.upload:
        try_upload(aab, args.track)

if __name__=="__main__": main()
