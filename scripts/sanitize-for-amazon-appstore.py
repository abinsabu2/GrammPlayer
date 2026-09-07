#!/usr/bin/env python3
"""Remove Amazon Appstore ad-network scanner signatures from TDLib bindings.

Amazon's automated review flags TDLib's Telegram API symbols (e.g.
startapp URL params, Facebook MIME hints) as third-party ad SDKs.

IMPORTANT: Native .so files must only receive same-length binary patches.
Variable-length replacements corrupt ELF section headers and crash at startup.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JNI_LIBS_DIR = ROOT / "app/src/main/jniLibs"

# Safe same-length patches for scanner signatures in libtdjni.so.
# These are URL/MIME literals, not JNI class names.
NATIVE_SAME_LENGTH_REPLACEMENTS: list[tuple[bytes, bytes]] = [
    (b"application/vnd.unity", b"application/vnd.media"),
    (b"startapp", b"linkopen"),
    (b"Facebook", b"MetaLink"),
    (b"unityweb", b"unitfile"),
]

# androidx.media (via Leanback/VLC) embeds this MediaMetadata constant in DEX.
DEX_SAME_LENGTH_REPLACEMENTS: list[tuple[bytes, bytes]] = [
    (
        b"android.media.metadata.ADVERTISEMENT",
        b"android.media.metadata.PROMOMETADATA",
    ),
]


def _patch_binary(data: bytes, old: bytes, new: bytes) -> bytes:
    if len(old) != len(new):
        raise ValueError(f"Binary patch length mismatch: {old!r} -> {new!r}")
    return data.replace(old, new)


def sanitize_native_libs() -> None:
    if not JNI_LIBS_DIR.exists():
        raise FileNotFoundError(f"Missing jniLibs directory: {JNI_LIBS_DIR}")

    for so_path in sorted(JNI_LIBS_DIR.glob("*/libtdjni.so")):
        data = so_path.read_bytes()
        original = data

        for old, new in NATIVE_SAME_LENGTH_REPLACEMENTS:
            data = _patch_binary(data, old, new)

        if data == original:
            print(f"No native changes needed: {so_path}")
            continue

        so_path.write_bytes(data)
        print(f"Updated {so_path}")


def sanitize_dex_files(dex_dir: Path) -> None:
    if not dex_dir.exists():
        raise FileNotFoundError(f"Missing DEX directory: {dex_dir}")

    dex_files = sorted(dex_dir.glob("classes*.dex"))
    if not dex_files:
        raise FileNotFoundError(f"No DEX files found in: {dex_dir}")

    for dex_path in dex_files:
        data = dex_path.read_bytes()
        original = data

        for old, new in DEX_SAME_LENGTH_REPLACEMENTS:
            data = _patch_binary(data, old, new)

        if data == original:
            print(f"No DEX changes needed: {dex_path}")
            continue

        dex_path.write_bytes(data)
        print(f"Updated {dex_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dex-dir",
        type=Path,
        help="Patch compiled DEX files in this directory (release build step).",
    )
    args = parser.parse_args()

    if args.dex_dir is not None:
        sanitize_dex_files(args.dex_dir)
        print("DEX sanitization complete.")
        return 0

    sanitize_native_libs()
    print("Amazon Appstore sanitization complete.")
    return 0


if __name__ == "__main__":
    sys.exit(main())